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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * ForYouActivity (LIVE Firestore Edition)
 *  - Real-time updates via addSnapshotListener
 *  - Filters events by user's interests
 *  - Displays event name, domain, and date in slideshow
 */
public class ForYouActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;
    private FirebaseFirestore db;
    private String userId;
    private String userEmail;
    private ImageSlider imageSlider;
    private Button openChatBtn;

    private ListenerRegistration eventListener; // 🔁 Firestore real-time listener

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

        // ===== Get user info =====
        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");
        db = FirebaseFirestore.getInstance();

        // ===== Interests Button =====
        Button btnUpdate = findViewById(R.id.btnUpdateInterests);
        if (btnUpdate != null) {
            btnUpdate.setOnClickListener(v -> {
                Intent intent = new Intent(ForYouActivity.this, InterestsActivity.class);
                intent.putExtra("userId", userId);
                intent.putExtra("userEmail", userEmail);
                startActivity(intent);
            });
        }

        // ===== Chat Button =====
        openChatBtn = findViewById(R.id.openChatBtn);
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

        // ===== User Info =====
        if (userId != null) refreshUserInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUserInfo();
    }

    private void refreshUserInfo() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String userName = documentSnapshot.getString("name");
                        String email = documentSnapshot.getString("email");

                        if (navigationView != null && navigationView.getHeaderCount() > 0) {
                            View headerView = navigationView.getHeaderView(0);
                            TextView navName = headerView.findViewById(R.id.nav_header_name);
                            TextView navEmail = headerView.findViewById(R.id.nav_header_email);
                            TextView navInterests = headerView.findViewById(R.id.nav_header_interests);

                            if (navName != null) navName.setText(userName);
                            if (navEmail != null) navEmail.setText(email);

                            List<String> selectedInterests = (List<String>) documentSnapshot.get("selectedInterests");
                            if (selectedInterests != null && !selectedInterests.isEmpty()) {
                                if (navInterests != null)
                                    navInterests.setText("Your Interests: " + String.join(", ", selectedInterests));
                            } else if (navInterests != null) {
                                navInterests.setText("Your Interests: Not selected");
                            }

                            // load real-time Firestore events
                            attachLiveEventListener(selectedInterests);
                        }
                    } else {
                        Toast.makeText(this, "User data not found!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error fetching user: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    /** 🔥 Attach live Firestore listener for events */
    private void attachLiveEventListener(List<String> userInterests) {
        imageSlider = findViewById(R.id.imageSlider);
        if (imageSlider == null) return;

        // remove any old listener to avoid leaks
        if (eventListener != null) eventListener.remove();

        eventListener = db.collection("createdEvents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Toast.makeText(this, "Error loading events: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (snapshot == null || snapshot.isEmpty()) {
                        Toast.makeText(this, "No events found!", Toast.LENGTH_SHORT).show();
                        imageSlider.setImageList(new ArrayList<>(), ScaleTypes.CENTER_CROP);
                        return;
                    }

                    List<SlideModel> slides = new ArrayList<>();
                    HashSet<String> seen = new HashSet<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String name = doc.getString("name");
                        String domain = doc.getString("domain");
                        String imageUrl = doc.getString("imageUrl");
                        String date = doc.getString("date");

                        if (name == null || seen.contains(name)) continue;
                        seen.add(name);

                        // optional filter by interest
                        if (userInterests != null && !userInterests.isEmpty()) {
                            boolean match = false;
                            for (String interest : userInterests) {
                                if (domain != null && domain.equalsIgnoreCase(interest)) {
                                    match = true;
                                    break;
                                }
                            }
                            if (!match) continue;
                        }

                        String subtitle = (domain != null ? domain : "General") +
                                (date != null ? " • " + date : "");

                        slides.add(new SlideModel(
                                (imageUrl != null && !imageUrl.isEmpty())
                                        ? imageUrl
                                        : "https://placehold.co/1000x600?text=" + name.replace(" ", "+"),
                                name + " • " + subtitle,
                                ScaleTypes.CENTER_CROP
                        ));
                    }

                    imageSlider.setImageList(slides, ScaleTypes.CENTER_CROP);

                    // handle click -> event details
                    imageSlider.setItemClickListener(position -> {
                        if (position < 0 || position >= snapshot.size()) return;
                        DocumentSnapshot doc = snapshot.getDocuments().get(position);
                        Intent intent = new Intent(ForYouActivity.this, EventDescActivity.class);
                        intent.putExtra("eventName", doc.getString("name"));
                        intent.putExtra("eventCategory", doc.getString("domain"));
                        intent.putExtra("eventDate", doc.getString("date"));
                        intent.putExtra("eventImage", doc.getString("imageUrl"));
                        intent.putExtra("userId", userId);
                        intent.putExtra("userEmail", userEmail);
                        startActivity(intent);
                    });
                });
    }

    public void onCreateEventClick(View view) {
        Intent intent = new Intent(ForYouActivity.this, CreateEventActivity.class);
        intent.putExtra("userId", userId);
        intent.putExtra("userEmail", userEmail);
        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventListener != null) eventListener.remove();
    }
}
