package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

public class login_phone_number extends AppCompatActivity {

    private EditText phoneInput;
    private AppCompatButton sendOtpBtn;

    private String eventId;
    private String eventName;
    private String userId;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_phone_number);

        phoneInput = findViewById(R.id.phoneno);
        sendOtpBtn = findViewById(R.id.sendOTPButton);

        // ✅ Receive data from EventDescActivity
        eventId = getIntent().getStringExtra("eventId");
        eventName = getIntent().getStringExtra("eventName");
        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");

        sendOtpBtn.setOnClickListener(v -> {
            String phoneNumber = phoneInput.getText().toString().trim();

            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(login_phone_number.this, login_otp.class);
            intent.putExtra("phone", phoneNumber);
            intent.putExtra("eventId", eventId);
            intent.putExtra("eventName", eventName);
            intent.putExtra("userId", userId);
            intent.putExtra("userEmail", userEmail);

            startActivity(intent);
        });
    }
}
