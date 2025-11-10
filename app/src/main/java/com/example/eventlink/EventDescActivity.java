package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class EventDescActivity extends AppCompatActivity {

    private String userId;
    private String userEmail;

    private String eventId;
    private String eventName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_desc);

        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");

        // ✅ Receive event data dynamically
        eventId = getIntent().getStringExtra("eventId");
        eventName = getIntent().getStringExtra("eventName");
        String eventDescription = getIntent().getStringExtra("eventDescription");
        String eventLocation = getIntent().getStringExtra("eventLocation");

        TextView title = findViewById(R.id.textTitle);
        TextView desc = findViewById(R.id.eventDescription);
        TextView location = findViewById(R.id.eventLocation);

        // ✅ Logs
        Log.d("EVENT_DATA", "Received id=" + eventId);
        Log.d("EVENT_DATA", "Received name=" + eventName);
        Log.d("EVENT_DATA", "Received desc=" + eventDescription);
        Log.d("EVENT_DATA", "Received location=" + eventLocation);

        // ✅ Display
        title.setText(eventName != null ? eventName : "Event");
        desc.setText(eventDescription != null ? eventDescription : "No description available");
        location.setText(eventLocation != null ? eventLocation : "Location not provided");
    }

    // ✅ PASS THE SAME DYNAMIC EVENT NAME + ID
    public void onVerifyPhoneClick(View view) {
        Intent intent = new Intent(EventDescActivity.this, login_phone_number.class);

        intent.putExtra("userId", userId);
        intent.putExtra("userEmail", userEmail);

        // ✅ Passing the LIVE dynamic values (NOT static!)
        intent.putExtra("eventId", eventId);
        intent.putExtra("eventName", eventName);

        startActivity(intent);
    }
}
