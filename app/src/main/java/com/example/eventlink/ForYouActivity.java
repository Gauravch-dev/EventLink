package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;

import com.denzcoskun.imageslider.ImageSlider;
import com.denzcoskun.imageslider.constants.ScaleTypes;
import com.denzcoskun.imageslider.models.SlideModel;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;

public class ForYouActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private FirebaseFirestore db;
    private String userId;
    private String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_for_you);

        // ===== Setup Toolbar & Drawer =====
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // ===== Get user info from Intent =====
        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");

        db = FirebaseFirestore.getInstance();

        // ===== Load user data using UID =====
        if (userId != null) {
            db.collection("users").document(userId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String userName = documentSnapshot.getString("name");
                            String email = documentSnapshot.getString("email");

                            if (navigationView != null && navigationView.getHeaderCount() > 0) {
                                View headerView = navigationView.getHeaderView(0);
                                TextView navName = headerView.findViewById(R.id.nav_header_name);
                                TextView navEmail = headerView.findViewById(R.id.nav_header_email);
                                if (navName != null) navName.setText(userName);
                                if (navEmail != null) navEmail.setText(email);
                            }
                        } else {
                            Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error fetching user: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        } else {
            Toast.makeText(this, "User not logged in properly", Toast.LENGTH_SHORT).show();
        }

        // ===== Image Slider =====
        ImageSlider imageSlider = findViewById(R.id.imageSlider);
        if (imageSlider != null) {
            ArrayList<SlideModel> slideModels = new ArrayList<>();
            slideModels.add(new SlideModel(R.drawable.blockchain, ScaleTypes.FIT));
            slideModels.add(new SlideModel(R.drawable.cybersec, ScaleTypes.FIT));
            slideModels.add(new SlideModel(R.drawable.ai_ml, ScaleTypes.FIT));
            slideModels.add(new SlideModel(R.drawable.iot, ScaleTypes.FIT));
            imageSlider.setImageList(slideModels, ScaleTypes.FIT);

            // On image click -> go to EventDescActivity with UID and email
            imageSlider.setItemClickListener(position -> {
                Intent intent = new Intent(ForYouActivity.this, EventDescActivity.class);
                intent.putExtra("userId", userId);
                intent.putExtra("userEmail", userEmail);
                startActivity(intent);
            });
        }

        // ===== Open Chat button =====
        Button openChatBtn = findViewById(R.id.openChatBtn);
        if (openChatBtn != null) {
            openChatBtn.setOnClickListener(v -> {
                Intent chatIntent = new Intent(ForYouActivity.this, ChatActivity.class);
                chatIntent.putExtra("userId", userId);
                chatIntent.putExtra("userEmail", userEmail);
                startActivity(chatIntent);
            });
        } else {
            Toast.makeText(this, "Chat button not found in layout", Toast.LENGTH_SHORT).show();
        }
    }

    public void onCreateEventClick(View view) {
        Intent intent = new Intent(ForYouActivity.this, CreateEventActivity.class);
        intent.putExtra("userId", userId);
        intent.putExtra("userEmail", userEmail);
        startActivity(intent);
    }
}
