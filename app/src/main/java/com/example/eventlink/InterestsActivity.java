package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import com.google.firebase.firestore.SetOptions;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class InterestsActivity extends AppCompatActivity {

    private String userId;
    private String userEmail;

    CheckBox cb1, cb2, cb3, cb4, cb5, cb6;
    MaterialCardView card1, card2, card3, card4, card5, card6;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_interests);

        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");

        if (userEmail == null) {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                userId = user.getUid();
                userEmail = user.getEmail();
            }
        }

        cb1 = findViewById(R.id.checkBoxOne);
        cb2 = findViewById(R.id.checkBoxTwo);
        cb3 = findViewById(R.id.checkBoxThree);
        cb4 = findViewById(R.id.checkBoxFour);
        cb5 = findViewById(R.id.checkBoxFive);
        cb6 = findViewById(R.id.checkBoxSix);

        card1 = findViewById(R.id.cardOne);
        card2 = findViewById(R.id.cardTwo);
        card3 = findViewById(R.id.cardThree);
        card4 = findViewById(R.id.cardFour);
        card5 = findViewById(R.id.cardFive);
        card6 = findViewById(R.id.cardSix);

        setupSelectableCard(cb1, card1);
        setupSelectableCard(cb2, card2);
        setupSelectableCard(cb3, card3);
        setupSelectableCard(cb4, card4);
        setupSelectableCard(cb5, card5);
        setupSelectableCard(cb6, card6);
    }

    private void setupSelectableCard(CheckBox cb, MaterialCardView card) {
        card.setOnClickListener(v -> cb.setChecked(!cb.isChecked()));

        cb.setOnCheckedChangeListener((btn, checked) -> {
            card.setStrokeColor(checked ? 0xFF8B5CF6 : 0xFFE0E0E0);
            card.setCardBackgroundColor(checked ? 0xFFF3F0FF : 0xFFFAFAFA);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem logout = menu.add("Logout");
        logout.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        logout.setIcon(android.R.drawable.ic_lock_power_off);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        return true;
    }

    public void onForyouClick(View view) {
        List<String> selectedInterests = new ArrayList<>();
        if (cb1.isChecked()) selectedInterests.add("AI/ML");
        if (cb2.isChecked()) selectedInterests.add("Cybersecurity");
        if (cb3.isChecked()) selectedInterests.add("Web/App Dev");
        if (cb4.isChecked()) selectedInterests.add("IoT/Hardware");
        if (cb5.isChecked()) selectedInterests.add("Blockchain");
        if (cb6.isChecked()) selectedInterests.add("Cloud/DevOps");

// Optional: require at least 1 interest
        if (selectedInterests.isEmpty()) {
            Toast.makeText(this, "Pick at least one interest", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("selectedInterests", selectedInterests);

// Use merge() so it creates or updates safely
        FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .set(userData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Interests updated!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(InterestsActivity.this, ForYouActivity.class);
                    intent.putExtra("userId", userId);
                    intent.putExtra("userEmail", userEmail);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );}

}
