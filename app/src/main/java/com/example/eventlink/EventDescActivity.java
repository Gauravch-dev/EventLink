package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

public class EventDescActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_desc);
    }

    public void onVerifyPhoneClick(View view){
        Intent intent=new Intent(EventDescActivity.this, login_phone_number.class);
        startActivity(intent);
    }
}
