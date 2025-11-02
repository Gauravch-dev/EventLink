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

        // ✅ Receive user info from ForYouActivity
        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");
    }

    // ✅ When user clicks "Verify Phone" or "Register"
    public void onVerifyPhoneClick(View view) {
        // Example event info (in a real app, you'd fetch these from Firestore or intent extras)
        String eventId = "event123";
        String eventName = "AI Workshop";

        // ✅ Send all info to OTP screen
        Intent intent = new Intent(EventDescActivity.this, login_phone_number.class);
        intent.putExtra("userId", userId);
        intent.putExtra("userEmail", userEmail);
        intent.putExtra("eventId", eventId);
        intent.putExtra("eventName", eventName);
        startActivity(intent);
    }
}
