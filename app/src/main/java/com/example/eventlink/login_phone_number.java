package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

public class login_phone_number extends AppCompatActivity {
    EditText phoneInput;
    AppCompatButton sendOtpBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_phone_number);

        phoneInput = findViewById(R.id.phoneno);
        sendOtpBtn = findViewById(R.id.sendOTPButton);

        sendOtpBtn.setOnClickListener((v) -> {
            String phoneNumber = phoneInput.getText().toString().trim();

            if (phoneNumber.isEmpty()) {
                Toast.makeText(this, "Please enter a phone number", Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(login_phone_number.this, login_otp.class);
            intent.putExtra("phone", phoneNumber);
            startActivity(intent);
        });
    }
}
