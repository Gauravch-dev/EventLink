package com.example.eventlink;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.example.eventlink.R;
import com.example.eventlink.GeminiIn;
import com.example.eventlink.GeminiOut;
import com.example.eventlink.BotService;
import com.example.eventlink.RetrofitProvider;

public class ChatActivity extends AppCompatActivity {

    private EditText input;
    private Button sendBtn;
    private TextView chatLog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        input = findViewById(R.id.inputText);
        sendBtn = findViewById(R.id.sendBtn);
        chatLog = findViewById(R.id.chatLog);

        sendBtn.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            appendLine("You: " + text);
            callGemini(text);
            input.setText("");
        });
    }

    private void callGemini(String userText) {
        BotService api = RetrofitProvider.getApi();
        GeminiIn body = new GeminiIn(
                userText,
                "You are a friendly assistant. Keep replies under 2 sentences."
        );

        api.geminiChat(body).enqueue(new Callback<GeminiOut>() {
            @Override public void onResponse(Call<GeminiOut> call, Response<GeminiOut> res) {
                if (!res.isSuccessful() || res.body() == null) {
                    appendLine("Bot (error): " + res.code());
                    return;
                }
                appendLine("Bot: " + res.body().text);
            }

            @Override public void onFailure(Call<GeminiOut> call, Throwable t) {
                appendLine("Bot (network error): " + t.getMessage());
            }
        });
    }

    private void appendLine(String line) {
        String prev = chatLog.getText().toString();
        chatLog.setText(prev.isEmpty() ? line : prev + "\n\n" + line);
    }
}
