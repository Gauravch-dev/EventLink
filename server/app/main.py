import os, re, json, pickle, numpy as np
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, field_validator
from typing import Optional, Dict, Any

# --- Gemini SDK is optional; server still runs without it
try:
    import google.generativeai as genai
except Exception:
    genai = None

# ======= Paths (edit if you put files elsewhere) =======
BASE_DIR   = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MODEL_PATH = os.getenv("INTENT_MODEL", os.path.join(BASE_DIR, "models", "intent_model_charwb_safe.pkl"))
VEC_PATH   = os.getenv("INTENT_VEC",   os.path.join(BASE_DIR, "models", "vectorizer_charwb_safe.pkl"))
THR_JSON   = os.getenv("INTENT_THRESHOLDS", os.path.join(BASE_DIR, "config", "intent_thresholds_prod.json"))

# ======= Threshold & clarify knobs =======
DEFAULT_TH = 0.30      # fallback if class not in JSON
NEAR_DELTA = 0.12      # if top1 - top2 < NEAR_DELTA -> clarify
LOW_CONF   = 0.35      # if top1_conf < LOW_CONF     -> clarify

INTENT_ATTRS: Dict[str, list[str]] = {
    "user_name":        ["user_name"],
    "user_email":       ["user_email"],
    "user_location":    ["user_location"],

    "event_name":       ["event_name"],
    "event_description":["event_description"],
    "event_category":   ["event_category"],
    "event_location":   ["event_location"],
}

# ======= Fixed prompt templates (short, grounded, no outside info) =======
PROMPTS: Dict[str, str] = {
    "user_name": """System: You are EventLink Assistant. Answer ONLY from the context. 
If the answer is missing, say "I don't have that info yet."

Task: Tell the user's name briefly.
Context:
- user_name: {user_name}

Answer in one short sentence.""",

    "user_email": """System: You are EventLink Assistant. Answer ONLY from the context.

Task: State the registered email.
Context:
- user_email: {user_email}

Answer in one short sentence.""",

    "user_location": """System: You are EventLink Assistant. Answer ONLY from the context.

Task: Tell the user's current/location value if present.
Context:
- user_location: {user_location}

Keep it to one short sentence.""",

    "event_name": """System: You are EventLink Assistant. Answer ONLY from the context.

Task: Tell the event's name.
Context:
- event_name: {event_name}

Answer in one short sentence.""",

    "event_description": """System: You are EventLink Assistant. Answer ONLY from the context.

Task: Summarize the event in 1–2 friendly sentences, no extra info.
Context:
- event_description: {event_description}""",

    "event_category": """System: You are EventLink Assistant. Answer ONLY from the context.

Task: Tell the event category/type.
Context:
- event_category: {event_category}

One short sentence.""",

    "event_location": """System: You are EventLink Assistant. Answer ONLY from the context.

Task: Tell where the event will be held.
Context:
- event_location: {event_location}

One short sentence.""",
}

# ======= Domain cues / guardrails =======
EVENT_WORDS    = {"event","seminar","workshop","session","conference"}
USER_LOC_CUES  = {"my location","where am i","where do i live","current location","my place"}
TIME_CUES      = {"when","time","date","schedule","start","begin","ends","ending"}
OFFDOMAIN_CUES = {"ronaldo","messi","elon","bitcoin","weather","recipe","cab","camera","translate","password","order"}

def _clean(t: str) -> str:
    return re.sub(r"[^\w\s]", "", str(t).lower().strip())

# ======= Load model/vectorizer/thresholds (frozen) =======
with open(THR_JSON, "r", encoding="utf-8") as f:
    PER_THR = json.load(f)

with open(MODEL_PATH, "rb") as f:
    MODEL = pickle.load(f)

with open(VEC_PATH, "rb") as f:
    VECTORIZER = pickle.load(f)

CLASSES = MODEL.classes_

def _choose_with_threshold(probs: np.ndarray):
    i = int(np.argmax(probs))
    intent = CLASSES[i]
    conf = float(probs[i])
    th = PER_THR.get(intent, DEFAULT_TH)
    if "out_of_scope" in CLASSES and conf < th:
        return "out_of_scope", conf
    return intent, conf

def _top2_strategy(probs: np.ndarray):
    order = np.argsort(-probs)
    i1, i2 = order[0], order[1]
    c1, c2 = float(probs[i1]), float(probs[i2])
    clarify = (c1 < LOW_CONF) or ((c1 - c2) < NEAR_DELTA)
    return CLASSES[i1], c1, CLASSES[i2], c2, clarify

def _guardrails(raw_text: str, result: dict) -> dict:
    t = raw_text.lower()
    # 1) off-domain names/tasks → out_of_scope (unless event/user clue present)
    if any(w in t for w in OFFDOMAIN_CUES) and not any(w in t for w in (EVENT_WORDS | USER_LOC_CUES)):
        result["final_intent"] = "out_of_scope"
        result["final_conf"] = max(result["final_conf"], 0.50)
        return result
    # 2) time questions → clarify (or later route to event_time)
    if any(w in t.split() for w in TIME_CUES) or "when is" in t:
        result["needs_clarify"] = True
        return result
    # 3) location disambiguation
    if any(phrase in t for phrase in USER_LOC_CUES):
        result["final_intent"] = "user_location"
        result["final_conf"] = max(result["final_conf"], 0.60)
        return result
    if any(w in t for w in EVENT_WORDS) and any(w in t for w in {"location","venue","place"}):
        result["final_intent"] = "event_location"
        result["final_conf"] = max(result["final_conf"], 0.60)
        return result
    return result

def _make_clarify_message(i1: str, i2: str) -> str:
    return f"I can help with **{i1.replace('_',' ')}** or **{i2.replace('_',' ')}** — which one did you mean?"

# ======= DB access (stub; replace later with Firestore/SQL) =======
def get_context_for_intent(intent: str, user_id: Optional[str], user_msg: str) -> Dict[str, Any]:
    required = INTENT_ATTRS.get(intent, [])
    ctx: Dict[str, Any] = {}

    demo_user = {
        "user_name": "Demo User",
        "user_email": "demo@example.com",
        "user_location": "Bengaluru",
    }
    demo_event = {
        "event_name": "AI Summit 2025",
        "event_description": "A workshop on modern ML systems.",
        "event_category": "Workshop",
        "event_location": "Mumbai Convention Center",
    }

    for field in required:
        if field.startswith("user_"):
            ctx[field] = demo_user.get(field)
        elif field.startswith("event_"):
            ctx[field] = demo_event.get(field)
    return ctx

# ======= Gemini helpers (auto-pick a working model) =======
VALID_MODEL_CANDIDATES = [
    # Fast, inexpensive
    "gemini-1.5-flash-latest",
    "gemini-1.5-flash-001",
    "gemini-1.5-flash",
    "gemini-1.5-flash-8b",
    # Higher quality (slower/costlier)
    "gemini-1.5-pro-latest",
    "gemini-1.5-pro-001",
    "gemini-1.5-pro",
]

def _pick_available_model() -> Optional[str]:
    """Return the first candidate that supports generateContent."""
    try:
        models = list(genai.list_models())
    except Exception:
        return None

    def supports_generate(m) -> bool:
        for attr in ("supported_generation_methods", "generation_methods"):
            methods = getattr(m, attr, None)
            if methods and ("generateContent" in methods or "generate_content" in methods):
                return True
        return False

    names = {m.name for m in models if supports_generate(m)}
    for candidate in VALID_MODEL_CANDIDATES:
        if candidate in names or f"models/{candidate}" in names:
            return candidate
    for m in models:
        if supports_generate(m):
            return m.name.split("/", 1)[-1]
    return None

# ======= Gemini wiring (initialized once if key present) =======
GEMINI_KEY = os.getenv("GOOGLE_API_KEY")
GEMINI_MODEL = None
GEMINI_MODEL_NAME = None

if genai and GEMINI_KEY:
    genai.configure(api_key=GEMINI_KEY)
    chosen = _pick_available_model() or "gemini-1.5-flash-latest"
    GEMINI_MODEL_NAME = chosen
    try:
        GEMINI_MODEL = genai.GenerativeModel(chosen)
    except Exception:
        for fallback in ["gemini-1.5-flash-001", "gemini-1.5-pro-001"]:
            try:
                GEMINI_MODEL = genai.GenerativeModel(fallback)
                GEMINI_MODEL_NAME = fallback
                break
            except Exception:
                continue

# ======= Build prompt & call Gemini (strict, grounded for /chat path) =======
def llm_answer(intent: str, user_msg: str, ctx: Dict[str, Any]) -> str:
    template = PROMPTS.get(intent)
    if not template or not ctx:
        return f"[{intent}] {ctx}" if ctx else f"[{intent}] No data available."

    fmt_ctx = {k: ("" if ctx.get(k) is None else str(ctx.get(k))) for k in INTENT_ATTRS.get(intent, [])}
    prompt = template.format(**fmt_ctx)

    if any(v == "" for v in fmt_ctx.values()):
        return f"[{intent}] {ctx}"  # missing data — deterministic fallback

    if not GEMINI_MODEL:
        return f"[{intent}] {ctx}"

    try:
        resp = GEMINI_MODEL.generate_content(prompt)
        text = (resp.text or "").strip()
        if not text:
            return f"[{intent}] {ctx}"
        sentences = [s.strip() for s in re.split(r'(?<=[.!?])\s+', text) if s.strip()]
        return " ".join(sentences[:2])
    except Exception:
        return f"[{intent}] {ctx}"

# ======= FastAPI app =======
app = FastAPI(title="EventLink Router")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],      # tighten later
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
def health():
    return {"ok": True, "model": str(type(MODEL).__name__), "classes": CLASSES.tolist()}

@app.get("/gemini_info")
def gemini_info():
    return {
        "configured": GEMINI_MODEL is not None,
        "model_name": GEMINI_MODEL_NAME,
    }

# ======= Pydantic models for /chat =======
class ChatIn(BaseModel):
    user_id: Optional[str] = None
    message: Optional[str] = None
    text: Optional[str] = None

    @field_validator("message", mode="before")
    @classmethod
    def prefer_message_or_text(cls, v, info):
        if v is None:
            data = info.data or {}
            return data.get("text")
        return v

    @field_validator("message")
    @classmethod
    def ensure_message_present(cls, v):
        if v is None or str(v).strip() == "":
            raise ValueError("Provide 'message' (or 'text').")
        return v

class ChatOut(BaseModel):
    intent: str
    confidence: float
    needs_clarify: bool
    top1_intent: str
    top1_confidence: float
    top2_intent: str
    top2_confidence: float
    answer: str

# ======= /chat: ML intent router (existing behaviour) =======
@app.post("/chat", response_model=ChatOut)
def chat(inp: ChatIn):
    user_message = inp.message
    x = VECTORIZER.transform([_clean(user_message)])
    probs = MODEL.predict_proba(x)[0]
    top1, c1, top2, c2, clarify = _top2_strategy(probs)
    final, fconf = _choose_with_threshold(probs)

    result = {
        "final_intent": final, "final_conf": float(fconf),
        "top1": {"intent": top1, "conf": float(c1)},
        "top2": {"intent": top2, "conf": float(c2)},
        "needs_clarify": clarify,
    }
    result = _guardrails(user_message, result)

    if result["final_intent"] == "out_of_scope" or result["needs_clarify"]:
        msg = _make_clarify_message(result["top1"]["intent"], result["top2"]["intent"])
        return ChatOut(
            intent="clarify",
            confidence=result["final_conf"],
            needs_clarify=True,
            top1_intent=result["top1"]["intent"], top1_confidence=result["top1"]["conf"],
            top2_intent=result["top2"]["intent"], top2_confidence=result["top2"]["conf"],
            answer=msg
        )

    ctx = get_context_for_intent(result["final_intent"], inp.user_id, user_message)
    ans = llm_answer(result["final_intent"], user_message, ctx)

    return ChatOut(
        intent=result["final_intent"],
        confidence=result["final_conf"],
        needs_clarify=False,
        top1_intent=result["top1"]["intent"], top1_confidence=result["top1"]["conf"],
        top2_intent=result["top2"]["intent"], top2_confidence=result["top2"]["conf"],
        answer=ans
    )

# ======= Minimal Gemini-only chat (no ML logic) =======
class GeminiIn(BaseModel):
    message: str
    system: Optional[str] = None  # optional system instruction

class GeminiOut(BaseModel):
    text: str

@app.post("/gemini_chat", response_model=GeminiOut)
def gemini_chat(inp: GeminiIn):
    # fallback if key not set or lib missing
    if GEMINI_MODEL is None:
        return GeminiOut(text="(Gemini is not configured on the server.)")

    parts = []
    if inp.system and inp.system.strip():
        parts.append(f"System: {inp.system.strip()}")
    parts.append(f"User: {inp.message.strip()}")

    try:
        resp = GEMINI_MODEL.generate_content("\n\n".join(parts))
        text = (resp.text or "").strip()
        if not text:
            text = "(No response returned.)"
        return GeminiOut(text=text)
    except Exception as e:
        return GeminiOut(text=f"(Error calling Gemini: {e})")
