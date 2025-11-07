package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;

public class EventDescActivity extends AppCompatActivity {

    private String userId;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_desc);

        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");
    }

    public void onVerifyPhoneClick(View view) {
        String eventId = "event123";
        String eventName = "AI Workshop";

        Intent intent = new Intent(EventDescActivity.this, login_phone_number.class);
        intent.putExtra("userId", userId);
        intent.putExtra("userEmail", userEmail);
        intent.putExtra("eventId", eventId);
        intent.putExtra("eventName", eventName);
        startActivity(intent);
    }
}
