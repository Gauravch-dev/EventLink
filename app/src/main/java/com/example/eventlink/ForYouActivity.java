package com.example.eventlink;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.denzcoskun.imageslider.ImageSlider;
import com.denzcoskun.imageslider.constants.ScaleTypes;
import com.denzcoskun.imageslider.models.SlideModel;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class ForYouActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private FirebaseFirestore db;
    private String userId, userEmail;
    private ImageSlider imageSlider;
    private Button openChatBtn;
    private ListenerRegistration eventListener;
    private Toolbar toolbar;
    private ActionBarDrawerToggle toggle;

    private RecyclerView recyclerNearbyEvents;

    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_for_you);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        db = FirebaseFirestore.getInstance();

        userId = getIntent().getStringExtra("userId");
        userEmail = getIntent().getStringExtra("userEmail");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // -------------------------------------------------
        // TOOLBAR + DRAWER
        // -------------------------------------------------
        toolbar = findViewById(R.id.mainToolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle("EventLink");
        }

        toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );

        toggle.getDrawerArrowDrawable().setColor(getResources().getColor(android.R.color.black));
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        // -------------------------------------------------
        // DEFAULT HEADER TEXT
        // -------------------------------------------------
        View headerView = navigationView.getHeaderView(0);
        ((TextView) headerView.findViewById(R.id.nav_header_name)).setText("Loading...");
        ((TextView) headerView.findViewById(R.id.nav_header_email)).setText("Loading...");
        ((TextView) headerView.findViewById(R.id.nav_header_interests)).setText("Your Interests: -");

        // -------------------------------------------------
        // UPDATE INTERESTS BUTTON
        // -------------------------------------------------
        Button btnUpdate = findViewById(R.id.btnUpdateInterests);
        btnUpdate.setOnClickListener(v -> {
            Intent intent = new Intent(ForYouActivity.this, InterestsActivity.class);
            intent.putExtra("userId", userId);
            intent.putExtra("userEmail", userEmail);
            startActivity(intent);
        });

        // -------------------------------------------------
        // OPEN CHAT BUTTON
        // -------------------------------------------------
        openChatBtn = findViewById(R.id.openChatBtn);
        openChatBtn.setOnClickListener(v -> {
            Intent chatIntent = new Intent(ForYouActivity.this, ChatActivity.class);
            chatIntent.putExtra("userId", userId);
            chatIntent.putExtra("userEmail", userEmail);
            startActivity(chatIntent);
        });

        // ✅ Setup RecyclerView
        recyclerNearbyEvents = findViewById(R.id.recyclerNearbyEvents);
        recyclerNearbyEvents.setLayoutManager(new LinearLayoutManager(this));

        refreshUserInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUserInfo();
    }

    // -------------------------------------------------
    // ✅ FETCH USER INFO
    // -------------------------------------------------
    private void refreshUserInfo() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this, "User not found!", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    View header = navigationView.getHeaderView(0);
                    TextView navName = header.findViewById(R.id.nav_header_name);
                    TextView navEmail = header.findViewById(R.id.nav_header_email);
                    TextView navInterests = header.findViewById(R.id.nav_header_interests);

                    String fireName = documentSnapshot.getString("name");
                    String fireEmail = documentSnapshot.getString("email");
                    List<String> selectedInterests =
                            (List<String>) documentSnapshot.get("selectedInterests");

                    navName.setText(fireName != null ? fireName : "Unknown User");
                    navEmail.setText(fireEmail != null ? fireEmail : userEmail);

                    if (selectedInterests != null && !selectedInterests.isEmpty()) {
                        navInterests.setText("Your Interests: " + String.join(", ", selectedInterests));
                    } else {
                        navInterests.setText("Your Interests: Not selected");
                    }

                    attachLiveEventListener(selectedInterests);
                    loadNearbyEvents();

                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // -------------------------------------------------
    // ✅ EVENT SLIDER
    // -------------------------------------------------
    private void attachLiveEventListener(List<String> userInterests) {

        imageSlider = findViewById(R.id.imageSlider);
        if (imageSlider == null) return;

        if (eventListener != null) eventListener.remove();

        eventListener = db.collection("createdEvents")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener((snapshot, e) -> {

                    if (e != null) {
                        Toast.makeText(this, "Error loading events", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    List<SlideModel> slides = new ArrayList<>();
                    List<DocumentSnapshot> filteredDocs = new ArrayList<>();
                    HashSet<String> seen = new HashSet<>();

                    if (snapshot == null) return;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {

                        String name = doc.getString("name");
                        String domain = doc.getString("domain");
                        String imageUrl = doc.getString("imageUrl");
                        String date = doc.getString("date");

                        if (name == null || seen.contains(name)) continue;
                        seen.add(name);

                        boolean match = true;
                        if (userInterests != null && !userInterests.isEmpty()) {
                            match = false;
                            for (String interest : userInterests) {
                                if (domain != null &&
                                        domain.equalsIgnoreCase(interest)) {
                                    match = true;
                                    break;
                                }
                            }
                        }

                        if (!match) continue;

                        slides.add(new SlideModel(
                                imageUrl != null ? imageUrl :
                                        "https://placehold.co/1000x600?text=" + name,
                                name + " • " + domain + " • " + date,
                                ScaleTypes.CENTER_CROP
                        ));

                        filteredDocs.add(doc);
                    }

                    imageSlider.setImageList(slides, ScaleTypes.CENTER_CROP);

                    imageSlider.setItemClickListener(position -> {
                        if (position < 0 || position >= filteredDocs.size()) return;

                        DocumentSnapshot doc = filteredDocs.get(position);

                        Intent intent = new Intent(ForYouActivity.this, EventDescActivity.class);
                        intent.putExtra("eventName", doc.getString("name"));
                        intent.putExtra("eventCategory", doc.getString("domain"));
                        intent.putExtra("eventDate", doc.getString("date"));
                        intent.putExtra("eventImage", doc.getString("imageUrl"));
                        intent.putExtra("eventDescription", doc.getString("description"));
                        intent.putExtra("eventLocation", doc.getString("address"));
                        intent.putExtra("userId", userId);
                        intent.putExtra("userEmail", userEmail);

                        startActivity(intent);
                    });
                });
    }

    // -------------------------------------------------
    // ✅ LOAD NEARBY EVENTS (WITH DISTANCE)
    // -------------------------------------------------
    private void loadNearbyEvents() {

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {

                    if (location == null) {
                        Toast.makeText(this, "Unable to get location", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double userLat = location.getLatitude();
                    double userLng = location.getLongitude();

                    db.collection("createdEvents")
                            .get()
                            .addOnSuccessListener(snapshot -> {

                                List<DocumentSnapshot> nearbyEvents = new ArrayList<>();
                                List<Double> distancesList = new ArrayList<>(); // ✅ NEW

                                for (DocumentSnapshot doc : snapshot.getDocuments()) {

                                    Double evLat = doc.getDouble("latitude");
                                    Double evLng = doc.getDouble("longitude");

                                    Log.d("NEARBY_DEBUG",
                                            "Event: " + doc.getString("name") +
                                                    " | lat=" + evLat +
                                                    " | lng=" + evLng);

                                    if (evLat == null || evLng == null) continue;

                                    double distance = distanceKm(userLat, userLng, evLat, evLng);

                                    if (distance <= 2.0) {
                                        nearbyEvents.add(doc);
                                        distancesList.add(distance); // ✅ ADD DISTANCE
                                    }
                                }

                                EventAdapter adapter = new EventAdapter(
                                        ForYouActivity.this,
                                        nearbyEvents,
                                        distancesList, // ✅ PASS DISTANCE LIST
                                        userId,
                                        userEmail
                                );

                                recyclerNearbyEvents.setAdapter(adapter);
                            });

                });
    }

    // ✅ Haversine distance
    private double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) *
                        Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) *
                        Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
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
