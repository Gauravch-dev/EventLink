package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity {

    private EditText email, password;
    private AppCompatButton btnLogin;
    private TextView forgotPassword;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        email = findViewById(R.id.editEmail);
        password = findViewById(R.id.editPassword);
        btnLogin = findViewById(R.id.btnLogin);
        forgotPassword = findViewById(R.id.textForgotPassword);

        mAuth = FirebaseAuth.getInstance();

        // Login button click
        btnLogin.setOnClickListener(view -> {
            String emailText = email.getText().toString().trim();
            String passwordText = password.getText().toString().trim();

            if (emailText.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
                Toast.makeText(this, "Enter a valid email", Toast.LENGTH_SHORT).show();
                return;
            }

            if (passwordText.isEmpty() || passwordText.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            // Firebase login
            mAuth.signInWithEmailAndPassword(emailText, passwordText)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                            if (user != null) {
                                String userId = user.getUid();
                                String userEmail = user.getEmail();

                                Intent intent = new Intent(MainActivity.this, InterestsActivity.class);
                                intent.putExtra("userId", userId);
                                intent.putExtra("userEmail", userEmail);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }
                        } else {
                            Toast.makeText(this, "Invalid credentials", Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        // Forgot password click
        forgotPassword.setOnClickListener(view -> {
            String emailText = email.getText().toString().trim();

            if (emailText.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(emailText).matches()) {
                Toast.makeText(this, "Enter your registered email first", Toast.LENGTH_SHORT).show();
                return;
            }

            mAuth.sendPasswordResetEmail(emailText)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Password reset email sent", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Email not registered", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }

    // ✅ Sign Up click handler
    public void onSignUpClick(android.view.View view) {
        Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
        startActivity(intent);
    }
}
