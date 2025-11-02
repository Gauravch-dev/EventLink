//package com.example.eventlink;
//
//import android.content.Context;
//import android.os.Bundle;
//import android.view.KeyEvent;
//import android.view.View;
//import android.view.inputmethod.EditorInfo;
//import android.view.inputmethod.InputMethodManager;
//import android.widget.Button;
//import android.widget.EditText;
//import android.widget.ScrollView;
//import android.widget.TextView;
//
//import androidx.annotation.Nullable;
//import androidx.appcompat.app.AppCompatActivity;
//
//import java.util.Map;
//
//import retrofit2.Call;
//import retrofit2.Callback;
//import retrofit2.Response;
//
//public class ChatActivity extends AppCompatActivity {
//
//    private static final String MODEL = "gemini-2.0-flash";
//
//    private EditText input;
//    private Button sendBtn;
//    private TextView chatLog;
//    private ScrollView chatScroll;   // <— NEW
//
//    private Call<GeminiResponse> inFlight;
//    private IntentLocalClassifier classifier;
//
//    @Override
//    protected void onCreate(@Nullable Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_chat);
//
//        input      = findViewById(R.id.inputText);
//        sendBtn    = findViewById(R.id.sendBtn);
//        chatLog    = findViewById(R.id.chatLog);
//        chatScroll = findViewById(R.id.chatScroll); // <— NEW
//
//        // Load classifier (offline, from assets)
//        try {
//            classifier = new IntentLocalClassifier(this);
//        } catch (Exception e) {
//            appendLine("Bot init warning: classifier not loaded (" + e.getMessage() + ")");
//            classifier = null;
//        }
//
//        sendBtn.setOnClickListener(v -> trySend());
//        input.setOnEditorActionListener((v, actionId, event) -> {
//            boolean imeDone = actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE;
//            boolean enter   = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
//            if (imeDone || enter) { trySend(); return true; }
//            return false;
//        });
//    }
//
//    private void trySend() {
//        String text = input.getText().toString().trim();
//        if (text.isEmpty()) return;
//
//        appendLine("You: " + text);
//        input.setText("");
//        hideKeyboard(input);
//
//        String apiKey = BuildConfig.GEMINI_API_KEY;
//        if (apiKey == null || apiKey.isEmpty()) {
//            appendLine("Bot: API key missing. Set GEMINI_API_KEY in local.properties.");
//            return;
//        }
//
//        // 1) classify (if classifier loaded)
//        String intent = null;
//        if (classifier != null) {
//            IntentLocalClassifier.Prediction r = classifier.predict(text);
//            intent = r.top1;
//            appendLine("[intent] " + intent + " (" + String.format("%.2f", r.top1Prob) + ")");
//        }
//
//        // 2) Build context from DB (stub for now)
//        Map<String,String> ctxMap = DbProvider.fetchContextForIntent(this, "u1", intent == null ? "out_of_scope" : intent);
//        String systemPrompt = DbProvider.buildPrompt(intent == null ? "out_of_scope" : intent, ctxMap);
//
//        // 3) Call Gemini
//        setSending(true);
//        BotService api = RetrofitProvider.getApi();
//        GeminiRequest body = GeminiRequest.of(text, systemPrompt);
//
//        if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
//        inFlight = api.generateContent(MODEL, apiKey, body);
//        inFlight.enqueue(new Callback<GeminiResponse>() {
//            @Override public void onResponse(Call<GeminiResponse> call, Response<GeminiResponse> res) {
//                setSending(false);
//                if (!res.isSuccessful()) {
//                    appendLine("Bot (HTTP " + res.code() + "): " + res.message());
//                    return;
//                }
//                String out = res.body() == null ? null : res.body().firstText();
//                appendLine("Bot: " + (out == null || out.isEmpty() ? "(no response)" : out));
//            }
//
//            @Override public void onFailure(Call<GeminiResponse> call, Throwable t) {
//                setSending(false);
//                appendLine(call.isCanceled() ? "Bot: request cancelled" : "Bot (network): " + t.getMessage());
//            }
//        });
//    }
//
//    private void setSending(boolean sending) {
//        sendBtn.setEnabled(!sending);
//        input.setEnabled(!sending);
//        sendBtn.setText(sending ? "Sending…" : "Send");
//    }
//
//    private void appendLine(String line) {
//        String prev = chatLog.getText().toString();
//        chatLog.setText(prev.isEmpty() ? line : prev + "\n\n" + line);
//        scrollToBottom(); // <— NEW
//    }
//
//    private void scrollToBottom() {
//        if (chatScroll != null) {
//            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
//        }
//    }
//
//    private void hideKeyboard(View v) {
//        try {
//            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
//        } catch (Exception ignored) {}
//    }
//
//    @Override protected void onDestroy() {
//        super.onDestroy();
//        if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
//    }
//}


package com.example.eventlink;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
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
import com.google.android.gms.tasks.CancellationTokenSource;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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
    private String pendingUserText = null;
    private String pendingSystemPrompt = null;
    private BotService pendingApi;
    private String pendingApiKey;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        input = findViewById(R.id.inputText);
        sendBtn = findViewById(R.id.sendBtn);
        chatLog = findViewById(R.id.chatLog);
        chatScroll = findViewById(R.id.chatScroll);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Load classifier
        try {
            classifier = new IntentLocalClassifier(this);
        } catch (Exception e) {
            appendLine("Bot init warning: classifier not loaded (" + e.getMessage() + ")");
            classifier = null;
        }

        sendBtn.setOnClickListener(v -> trySend());
        input.setOnEditorActionListener((v, actionId, event) -> {
            boolean imeDone = actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE;
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN;
            if (imeDone || enter) {
                trySend();
                return true;
            }
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

        String intent = null;
        if (classifier != null) {
            IntentLocalClassifier.Prediction r = classifier.predict(text);
            intent = r.top1;
            appendLine("[intent] " + intent + " (" + String.format("%.2f", r.top1Prob) + ")");
        }

        Map<String, String> ctxMap = DbProvider.fetchContextForIntent(this, "u1", intent == null ? "out_of_scope" : intent);
        String systemPrompt = DbProvider.buildPrompt(intent == null ? "out_of_scope" : intent, ctxMap);

        BotService api = RetrofitProvider.getApi();
        setSending(true);

        String lower = text.toLowerCase();
        if (lower.contains("where am i") || lower.contains("current location") ||
                lower.contains("my address") || lower.contains("where are we") || lower.contains("location")) {
            // Ask for location
            fetchUserAddressAndSend(text, systemPrompt, api, apiKey);
        } else {
            GeminiRequest body = GeminiRequest.of(text, systemPrompt);
            sendToGemini(api, apiKey, body);
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchUserAddressAndSend(String userText, String systemPrompt, BotService api, String apiKey) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            waitingForLocation = true;
            pendingUserText = userText;
            pendingSystemPrompt = systemPrompt;
            pendingApi = api;
            pendingApiKey = apiKey;

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            appendLine("Bot: Please grant location permission to fetch your address.");
            setSending(false);
            return;
        }

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(500)
                .setNumUpdates(1);

        fusedLocationClient.requestLocationUpdates(locationRequest, new com.google.android.gms.location.LocationCallback() {
            @Override
            public void onLocationResult(@NonNull com.google.android.gms.location.LocationResult locationResult) {
                fusedLocationClient.removeLocationUpdates(this);

                Location location = locationResult.getLastLocation();
                if (location == null) {
                    appendLine("Bot: Unable to fetch location.");
                    setSending(false);
                    return;
                }

                double lat = location.getLatitude();
                double lon = location.getLongitude();

                String address = getAddressFromLocation(location);

                String fullPrompt = systemPrompt +
                        "\n\nIMPORTANT INSTRUCTION:\n" +
                        "The user's **current real-world address** is:\n" +
                        address + "\n\n" +
                        "You must always use this location for any 'where am I', 'how far', or 'location-related' questions. " +
                        "Do NOT assume or guess another city (like Bengaluru). " +
                        "Always rely only on the above real address provided by the system.";


                GeminiRequest body = GeminiRequest.of(userText, fullPrompt);
                sendToGemini(api, apiKey, body);
            }
        }, getMainLooper());
    }



    private String getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                    sb.append(addr.getAddressLine(i));
                    if (i < addr.getMaxAddressLineIndex()) sb.append(", ");
                }
                return sb.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Address not found";
    }

    private void sendToGemini(BotService api, String apiKey, GeminiRequest body) {
        if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
        inFlight = api.generateContent(MODEL, apiKey, body);

        inFlight.enqueue(new Callback<GeminiResponse>() {
            @Override
            public void onResponse(@NonNull Call<GeminiResponse> call, @NonNull Response<GeminiResponse> res) {
                setSending(false);
                if (!res.isSuccessful()) {
                    appendLine("Bot (HTTP " + res.code() + "): " + res.message());
                    return;
                }
                String out = res.body() == null ? null : res.body().firstText();
                appendLine("Bot: " + (out == null || out.isEmpty() ? "(no response)" : out));
            }

            @Override
            public void onFailure(@NonNull Call<GeminiResponse> call, @NonNull Throwable t) {
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
        scrollToBottom();
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && waitingForLocation) {
            waitingForLocation = false;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Retry sending now that permission is granted
                fetchUserAddressAndSend(pendingUserText, pendingSystemPrompt, pendingApi, pendingApiKey);
            } else {
                appendLine("Bot: Location permission denied. Cannot fetch address.");
                setSending(false);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (inFlight != null && !inFlight.isCanceled()) inFlight.cancel();
    }
}
