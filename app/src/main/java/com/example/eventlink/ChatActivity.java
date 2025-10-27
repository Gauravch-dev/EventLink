package com.example.eventlink;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private static final String MODEL = "gemini-2.0-flash";

    private EditText input;
    private Button sendBtn;
    private TextView chatLog;
    private ScrollView chatScroll;   // <— NEW

    private Call<GeminiResponse> inFlight;
    private IntentLocalClassifier classifier;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        input      = findViewById(R.id.inputText);
        sendBtn    = findViewById(R.id.sendBtn);
        chatLog    = findViewById(R.id.chatLog);
        chatScroll = findViewById(R.id.chatScroll); // <— NEW

        // Load classifier (offline, from assets)
        try {
            classifier = new IntentLocalClassifier(this);
        } catch (Exception e) {
            appendLine("Bot init warning: classifier not loaded (" + e.getMessage() + ")");
            classifier = null;
        }

        sendBtn.setOnClickListener(v -> trySend());
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeDone = actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE;
            boolean enter   = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
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
        if (apiKey == null || apiKey.isEmpty()) {
            appendLine("Bot: API key missing. Set GEMINI_API_KEY in local.properties.");
            return;
        }

        // 1) classify (if classifier loaded)
        String intent = null;
        if (classifier != null) {
            IntentLocalClassifier.Prediction r = classifier.predict(text);
            intent = r.top1;
            appendLine("[intent] " + intent + " (" + String.format("%.2f", r.top1Prob) + ")");
        }

        // 2) Build context from DB (stub for now)
        Map<String,String> ctxMap = DbProvider.fetchContextForIntent(this, "u1", intent == null ? "out_of_scope" : intent);
        String systemPrompt = DbProvider.buildPrompt(intent == null ? "out_of_scope" : intent, ctxMap);

        // 3) Call Gemini
        setSending(true);
        BotService api = RetrofitProvider.getApi();
        GeminiRequest body = GeminiRequest.of(text, systemPrompt);

        if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
        inFlight = api.generateContent(MODEL, apiKey, body);
        inFlight.enqueue(new Callback<GeminiResponse>() {
            @Override public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> res) {
                setSending(false);
                if (!res.isSuccessful()) {
                    appendLine("Bot (HTTP " + res.code() + "): " + res.message());
                    return;
                }
                String out = res.body() == null ? null : res.body().firstText();
                appendLine("Bot: " + (out == null || out.isEmpty() ? "(no response)" : out));
            }

            @Override public void onFailure(Call<GeminiResponse> call, Throwable t) {
                setSending(false);
                appendLine(call.isCanceled() ? "Bot: request cancelled" : "Bot (network): " + t.getMessage());
            }
        });
    }

    private void setSending(boolean sending) {
        sendBtn.setEnabled(!sending);
        input.setEnabled(!sending);
        sendBtn.setText(sending ? "Sending…" : "Send");
    }

    private void appendLine(String line) {
        String prev = chatLog.getText().toString();
        chatLog.setText(prev.isEmpty() ? line : prev + "\n\n" + line);
        scrollToBottom(); // <— NEW
    }

    private void scrollToBottom() {
        if (chatScroll != null) {
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    private void hideKeyboard(View v) {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
    }
}
