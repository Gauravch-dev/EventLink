package com.example.eventlink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * ChatActivity (FINAL - FIXED)
 * ------------------------------------------------------------------
 * - Event Q&A (date/time/location/desc/category)
 * - User info Q&A (name/email/location)
 * - Real-time GPS fallback for "my location"
 * - Lists registered events
 * - Firestore context → Gemini for intent-based chats
 */
public class ChatActivity extends AppCompatActivity {

    private static final String MODEL = "gemini-2.0-flash";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

    private EditText input;
    private Button sendBtn;
    private TextView chatLog;
    private ScrollView chatScroll;

    private Call<GeminiResponse> inFlight;
    private IntentLocalClassifier classifier;
    private FusedLocationProviderClient fusedLocationClient;

    private boolean waitingForLocation = false;
    private String pendingUserText, pendingSystemPrompt, pendingApiKey;
    private BotService pendingApi;

    private Handler aiAlertHandler = new Handler(Looper.getMainLooper());
    private Runnable aiAlertRunnable;
    private static final long AI_ALERT_DELAY = 15000L;
    private String lastIntent = null;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private String userEmail;

    private enum LiteIntent {
        EVENT_DATE, EVENT_TIME, EVENT_LOCATION, EVENT_DESC, EVENT_CATEGORY, NONE
    }

    private LiteIntent quickClassify(String msg) {
        if (msg == null) return LiteIntent.NONE;
        msg = msg.toLowerCase(Locale.ROOT);
        if (msg.matches(".*\\b(what time|time|starts at|start at|begin|starting|clock|when does it start)\\b.*")) return LiteIntent.EVENT_TIME;
        if (msg.matches(".*\\b(when|date|day|schedule|which day|which date)\\b.*")) return LiteIntent.EVENT_DATE;
        if (msg.matches(".*\\b(where|venue|address|location|place|held|happen|happening)\\b.*")) return LiteIntent.EVENT_LOCATION;
        if (msg.matches(".*\\b(about|describe|details|summary|information|info|tell me about)\\b.*")) return LiteIntent.EVENT_DESC;
        if (msg.matches(".*\\b(category|type|domain|kind of event|what kind of)\\b.*")) return LiteIntent.EVENT_CATEGORY;
        return LiteIntent.NONE;
    }

    private String extractEventNameFromMessage(String msg) {
        if (msg == null) return null;
        msg = msg.toLowerCase(Locale.ROOT);
        msg = msg.replaceAll("\\b(when|what|which|time|date|day|schedule|is|for|the|event|show|tell|about|on|at|of|start|starts|will|happen|does|do|it|my|me|where|venue|address|location|place|held|category|type|domain|kind)\\b", " ");
        msg = msg.replaceAll("\\s+", " ").trim();
        return msg.isEmpty() ? null : msg;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        input = findViewById(R.id.inputText);
        sendBtn = findViewById(R.id.sendBtn);
        chatLog = findViewById(R.id.chatLog);
        chatScroll = findViewById(R.id.chatScroll);

        userEmail = getIntent().getStringExtra("userEmail");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        try {
            classifier = new IntentLocalClassifier(this, "intent_model_android.json");
        } catch (Exception e) {
            appendLine("Bot init warning: classifier not loaded (" + e.getMessage() + ")");
        }

        sendBtn.setOnClickListener(v -> trySend());
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeDone = actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE;
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (imeDone || enter) { trySend(); return true; }
            return false;
        });
    }

    private void trySend() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        appendLine("You: " + text);
        input.setText("");
        hideKeyboard(input);

        String apiKey = BuildConfig.GEMINI_API_KEY;
        if (TextUtils.isEmpty(apiKey)) { appendLine("Bot: API key missing."); return; }

        // Handle user location first
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("where am i") || lower.contains("my location") ||
                lower.contains("current location") || lower.contains("my address") ||
                lower.contains("where are we")) {
            handleUserLocationRequest(apiKey, text);
            return;
        }

        // Event field Q&A
        LiteIntent lite = quickClassify(text);
        if (lite != LiteIntent.NONE) {
            String eventQuery = extractEventNameFromMessage(text);
            if (TextUtils.isEmpty(eventQuery)) { appendLine("🤖 Please specify the event name."); return; }
            handleEventFieldQuery(lite, eventQuery);
            return;
        }

        // User info
        if (isUserDetailsQuery(text)) { handleUserDetailsQuery(text); return; }

        // Registered events
        if (isRegisteredEventsQuery(text)) {
            handleRegisteredEventsQuery();
            return;
        }

        // Intent classification
        String intentName = "out_of_scope";
        if (classifier != null) {
            IntentLocalClassifier.Prediction r = classifier.predict(text);

            // ✅ Confidence filtering
            if (r == null || TextUtils.isEmpty(r.top1) || r.top1Prob < 0.55) {
                intentName = "out_of_scope";
            } else {
                intentName = r.top1;
            }


            lastIntent = intentName;
            resetAIDelay();
        }


        // ✅ Out of scope handling
        if ("out_of_scope".equals(intentName)) {
            appendLine("🤖 I’m not sure about that yet — I can help you with your events, registrations, or profile info.");
            return;
        }

        // Normal AI flow
        fetchContextThenAskGemini(apiKey, intentName, text);
    }


    private boolean isUserDetailsQuery(String text) {
        if (text == null) return false;
        String s = text.toLowerCase(Locale.ROOT);
        return s.contains("my name") || s.contains("who am i") || s.contains("my email") ||
                s.contains("what's my name") || s.contains("my info") ||
                s.contains("my profile") || s.contains("my account") ||
                s.contains("my details");
    }

    // ✅ NEW: handle registered events list
    private void handleRegisteredEventsQuery() {
        setSending(true);
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) {
            appendLine("Bot: You are not logged in.");
            setSending(false);
            return;
        }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    setSending(false);
                    if (!doc.exists()) {
                        appendLine("Bot: I couldn’t find your profile.");
                        return;
                    }
                    List<String> regs = (List<String>) doc.get("registeredEvents");
                    if (regs == null || regs.isEmpty()) {
                        appendLine("📝 You haven’t registered for any events yet.");
                    } else {
                        StringBuilder sb = new StringBuilder("📅 You’re registered for:\n");
                        for (String ev : regs) sb.append("• ").append(ev).append("\n");
                        appendLine(sb.toString());
                    }
                })
                .addOnFailureListener(e -> {
                    appendLine("❌ Error fetching registered events: " + e.getMessage());
                    setSending(false);
                });
    }

    private void handleUserDetailsQuery(String userText) {
        setSending(true);
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) { appendLine("Bot: You are not logged in."); setSending(false); return; }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(userDoc -> {
                    setSending(false);
                    if (!userDoc.exists()) { appendLine("Bot: I couldn’t find your profile."); return; }

                    String name = userDoc.getString("name");
                    String email = userDoc.getString("email");
                    String loc = userDoc.getString("location");

                    StringBuilder sb = new StringBuilder("👤 Your account details:\n");
                    if (!TextUtils.isEmpty(name)) sb.append("• Name: ").append(name).append("\n");
                    if (!TextUtils.isEmpty(email)) sb.append("• Email: ").append(email).append("\n");
                    if (!TextUtils.isEmpty(loc)) sb.append("• Saved Location: ").append(loc);
                    appendLine(sb.toString());
                })
                .addOnFailureListener(e -> { appendLine("❌ Error fetching profile: " + e.getMessage()); setSending(false); });
    }

    private boolean isRegisteredEventsQuery(String text) {
        if (text == null) return false;
        String s = text.toLowerCase(Locale.ROOT);
        return s.contains("registered events") || s.contains("my registrations") ||
                s.contains("what did i register") || s.contains("my events") ||
                s.contains("what i've registered");
    }

    private void handleUserLocationRequest(String apiKey, String userText) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : null;
        if (uid == null) { appendLine("Bot: You are not logged in."); return; }

        db.collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String savedLoc = doc.getString("location");
                    if (!TextUtils.isEmpty(savedLoc)) appendLine("🏠 Saved Location: " + savedLoc);
                    appendLine("📍 Fetching real-time location...");
                    BotService api = RetrofitProvider.getApi();
                    setSending(true);
                    fetchUserAddressAndSend(userText, "You are EventLink assistant.", api, apiKey);
                })
                .addOnFailureListener(e -> {
                    appendLine("⚠️ Couldn't get saved location. Using real-time...");
                    BotService api = RetrofitProvider.getApi();
                    fetchUserAddressAndSend(userText, "You are EventLink assistant.", api, apiKey);
                });
    }

    private void fetchContextThenAskGemini(String apiKey, String intentName, String userText) {
        setSending(true);
        DbProvider.fetchContextForIntent(this, userEmail, intentName, ctxMap -> {
            String systemPrompt = DbProvider.buildPrompt(intentName, ctxMap);
            BotService api = RetrofitProvider.getApi();
            GeminiRequest body = GeminiRequest.of(userText, systemPrompt);
            if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
            inFlight = api.generateContent(MODEL, apiKey, body);
            inFlight.enqueue(new Callback<GeminiResponse>() {
                @Override public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> res) {
                    setSending(false);
                    if (!res.isSuccessful()) { appendLine("Bot (HTTP " + res.code() + "): " + res.message()); return; }
                    String out = res.body() == null ? null : res.body().firstText();
                    appendLine("Bot: " + (TextUtils.isEmpty(out) ? "(no response)" : out));
                }
                @Override public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
                    setSending(false);
                    appendLine(call.isCanceled() ? "Bot: request cancelled" : "Bot (network): " + t.getMessage());
                }
            });
        });
    }

    private void handleEventFieldQuery(LiteIntent lite, String eventQuery) {
        setSending(true);
        db.collection("createdEvents").whereEqualTo("name", eventQuery).limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (!snap.isEmpty()) { answerEventField(lite, snap.getDocuments().get(0)); setSending(false); }
                    else scanAndPickBest(eventQuery, lite);
                })
                .addOnFailureListener(e -> { appendLine("❌ Error fetching event: " + e.getMessage()); setSending(false); });
    }

    private void scanAndPickBest(String eventQuery, LiteIntent lite) {
        db.collection("createdEvents").orderBy("name", Query.Direction.ASCENDING)
                .limit(100).get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) { appendLine("⚠️ I couldn’t find that event."); setSending(false); return; }
                    String q = eventQuery.toLowerCase(Locale.ROOT);
                    DocumentSnapshot best = null; int bestScore = -1;
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        String name = d.getString("name"); if (name == null) continue;
                        int score = similarityScore(q, name.toLowerCase(Locale.ROOT));
                        if (score > bestScore) { bestScore = score; best = d; }
                    }
                    if (best != null) answerEventField(lite, best);
                    else appendLine("⚠️ I couldn’t find that event.");
                    setSending(false);
                })
                .addOnFailureListener(e -> { appendLine("❌ Error fetching event: " + e.getMessage()); setSending(false); });
    }

    private int similarityScore(String q, String name) {
        if (name.equals(q)) return 100;
        if (name.startsWith(q)) return 80;
        if (name.contains(q)) return 60;
        String[] qa = q.split("\\s+"), na = name.split("\\s+");
        int overlap = 0;
        for (String x : qa) for (String y : na) if (x.equals(y)) overlap++;
        return overlap * 10;
    }

    private void answerEventField(LiteIntent lite, DocumentSnapshot doc) {
        String name = doc.getString("name");
        String date = doc.getString("date");
        String time = doc.getString("time");
        String address = doc.getString("address");
        String desc = doc.getString("description");
        String category = doc.getString("domain");

        switch (lite) {
            case EVENT_DATE:
                appendLine(!TextUtils.isEmpty(date) ? "📅 \"" + name + "\" is on " + date + "." : "📅 No date set for \"" + name + "\"."); break;
            case EVENT_TIME:
                appendLine(!TextUtils.isEmpty(time) ? "⏰ \"" + name + "\" starts at " + time + "." : "⏰ No time set for \"" + name + "\"."); break;
            case EVENT_LOCATION:
                appendLine(!TextUtils.isEmpty(address) ? "📍 \"" + name + "\" will be at:\n" + address : "📍 Location not yet available for \"" + name + "\"."); break;
            case EVENT_DESC:
                appendLine(!TextUtils.isEmpty(desc) ? "📝 " + desc : "📝 No description for \"" + name + "\"."); break;
            case EVENT_CATEGORY:
                appendLine(!TextUtils.isEmpty(category) ? "🏷️ \"" + name + "\" is a " + category + " event." : "🏷️ No category info for \"" + name + "\"."); break;
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchUserAddressAndSend(String userText, String systemPrompt, BotService api, String apiKey) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            waitingForLocation = true;
            pendingUserText = userText; pendingSystemPrompt = systemPrompt;
            pendingApi = api; pendingApiKey = apiKey;
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            appendLine("Bot: Please grant location permission.");
            setSending(false);
            return;
        }

        LocationRequest req = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000).setFastestInterval(500).setNumUpdates(1);

        fusedLocationClient.requestLocationUpdates(req, new com.google.android.gms.location.LocationCallback() {
            @Override public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                fusedLocationClient.removeLocationUpdates(this);
                Location loc = locationResult.getLastLocation();
                if (loc == null) { appendLine("Bot: Unable to fetch location."); setSending(false); return; }
                String address = getAddressFromLocation(loc);
                appendLine("📍 Real-time Location: " + address);
                setSending(false);
            }
        }, getMainLooper());
    }

    private String getAddressFromLocation(Location loc) {
        Geocoder g = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addrs = g.getFromLocation(loc.getLatitude(), loc.getLongitude(), 1);
            if (addrs != null && !addrs.isEmpty()) {
                Address a = addrs.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= a.getMaxAddressLineIndex(); i++) {
                    sb.append(a.getAddressLine(i));
                    if (i < a.getMaxAddressLineIndex()) sb.append(", ");
                }
                return sb.toString();
            }
        } catch (IOException ignored) {}
        return "Address not found";
    }

    private void setSending(boolean sending) {
        sendBtn.setEnabled(!sending);
        input.setEnabled(!sending);
        sendBtn.setText(sending ? "Sending…" : "Send");
    }

    private void appendLine(String line) {
        String prev = chatLog.getText().toString();
        chatLog.setText(prev.isEmpty() ? line : prev + "\n\n" + line);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (chatScroll != null) chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void hideKeyboard(View v) {
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    @Override
    public void onRequestPermissionsResult(int reqCode, @NonNull String[] perms, @NonNull int[] results) {
        super.onRequestPermissionsResult(reqCode, perms, results);
        if (reqCode == LOCATION_PERMISSION_REQUEST_CODE && waitingForLocation) {
            waitingForLocation = false;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                fetchUserAddressAndSend(pendingUserText, pendingSystemPrompt, pendingApi, pendingApiKey);
            else { appendLine("Bot: Location permission denied."); setSending(false); }
        }
    }

    private void resetAIDelay() {
        aiAlertHandler.removeCallbacksAndMessages(null);
        aiAlertRunnable = () -> {
            if (lastIntent != null) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) return;
                AIAlerter.trigger(this, lastIntent);
                lastIntent = null;
            }
        };
        aiAlertHandler.postDelayed(aiAlertRunnable, AI_ALERT_DELAY);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
        aiAlertHandler.removeCallbacksAndMessages(null);
        if (lastIntent != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) return;
            AIAlerter.trigger(this, lastIntent);
            lastIntent = null;
        }
    }
}
