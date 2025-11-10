package com.example.eventlink;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class login_otp extends AppCompatActivity {

    private String phoneNumber, eventId, eventName, userEmail;
    private Long timeoutSeconds = 60L;
    private String verificationCode;
    private PhoneAuthProvider.ForceResendingToken resendingToken;

    private EditText otpInput;
    private Button nextBtn;
    private TextView resendOtpTextView;

    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_otp);

        otpInput = findViewById(R.id.otpInput);
        nextBtn = findViewById(R.id.verifyButton);
        resendOtpTextView = findViewById(R.id.resend_otp_textview);

        phoneNumber = "+91" + getIntent().getStringExtra("phone");
        eventId = getIntent().getStringExtra("eventId");
        eventName = getIntent().getStringExtra("eventName");
        userEmail = getIntent().getStringExtra("userEmail");

        sendOtp(phoneNumber, false);

        nextBtn.setOnClickListener(v -> {
            String enteredOtp = otpInput.getText().toString();
            if (enteredOtp.isEmpty()) {
                Toast.makeText(this, "Enter OTP", Toast.LENGTH_SHORT).show();
                return;
            }

            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationCode, enteredOtp);
            linkOrSignInWithOtp(credential);
        });

        resendOtpTextView.setOnClickListener(v -> sendOtp(phoneNumber, true));
    }

    private void sendOtp(String phoneNumber, boolean isResend) {
        startResendTimer();

        PhoneAuthOptions.Builder builder = PhoneAuthOptions.newBuilder(mAuth)
                .setPhoneNumber(phoneNumber)
                .setTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .setActivity(this)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        linkOrSignInWithOtp(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        Toast.makeText(getApplicationContext(), "Verification failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onCodeSent(@NonNull String s, @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        verificationCode = s;
                        resendingToken = token;
                        Toast.makeText(getApplicationContext(), "OTP sent successfully", Toast.LENGTH_LONG).show();
                    }
                });

        if (isResend && resendingToken != null) {
            builder.setForceResendingToken(resendingToken);
        }

        PhoneAuthProvider.verifyPhoneNumber(builder.build());
    }

    private void linkOrSignInWithOtp(PhoneAuthCredential credential) {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // ✅ Link OTP with the existing logged-in user (same UID)
            currentUser.linkWithCredential(credential)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser linkedUser = task.getResult().getUser();
                            saveUserData(linkedUser.getUid());
                        } else {
                            // If already linked, just update data
                            if (task.getException() instanceof FirebaseAuthUserCollisionException) {
                                saveUserData(currentUser.getUid());
                            } else {
                                Toast.makeText(this, "Link failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        } else {
            // No user signed in, fallback to normal OTP sign-in
            mAuth.signInWithCredential(credential)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseUser newUser = mAuth.getCurrentUser();
                            saveUserData(newUser.getUid());
                        } else {
                            Toast.makeText(this, "OTP verification failed", Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void saveUserData(String userId) {
        DocumentReference userRef = db.collection("users").document(userId);
        DocumentReference eventRef = db.collection("events").document(eventId);

        Map<String, Object> userData = new HashMap<>();
        userData.put("phone", phoneNumber);
        userData.put("registeredEvents", FieldValue.arrayUnion(eventName));
        if (userEmail != null) userData.put("email", userEmail);

        userRef.set(userData, SetOptions.merge())
                .addOnSuccessListener(a -> updateEventAndRedirect(eventRef, userId))
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error saving user: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateEventAndRedirect(DocumentReference eventRef, String userId) {
        Map<String, Object> eventUpdate = new HashMap<>();
        eventUpdate.put("participants." + userId, true);

        eventRef.set(eventUpdate, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Registered for " + eventName, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(login_otp.this, ForYouActivity.class);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error updating event: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void startResendTimer() {
        resendOtpTextView.setEnabled(false);
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                timeoutSeconds--;
                runOnUiThread(() -> resendOtpTextView.setText("Resend OTP in " + timeoutSeconds + " seconds"));
                if (timeoutSeconds <= 0) {
                    timer.cancel();
                    runOnUiThread(() -> {
                        timeoutSeconds = 60L;
                        resendOtpTextView.setEnabled(true);
                        resendOtpTextView.setText("Resend OTP");
                    });
                }
            }
        }, 0, 1000);
    }
}
