package com.example.eventlink;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

    DrawerLayout drawerLayout;
    NavigationView navigationView;
    Toolbar toolbar;
    FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_for_you);

        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        String userEmail = getIntent().getStringExtra("userEmail");
        db = FirebaseFirestore.getInstance();

        if (userEmail != null) {
            db.collection("users").document(userEmail).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String userName = documentSnapshot.getString("name");
                            String email = documentSnapshot.getString("email");

                            View headerView = navigationView.getHeaderView(0);
                            TextView navName = headerView.findViewById(R.id.nav_header_name);
                            TextView navEmail = headerView.findViewById(R.id.nav_header_email);

                            navName.setText(userName);
                            navEmail.setText(email);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(this, "Error fetching user data: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }

        ImageSlider imageSlider = findViewById(R.id.imageSlider);
        ArrayList<SlideModel> slideModels = new ArrayList<>();
        slideModels.add(new SlideModel(R.drawable.blockchain, ScaleTypes.FIT));
        slideModels.add(new SlideModel(R.drawable.cybersec, ScaleTypes.FIT));
        slideModels.add(new SlideModel(R.drawable.ai_ml, ScaleTypes.FIT));
        slideModels.add(new SlideModel(R.drawable.iot, ScaleTypes.FIT));

        imageSlider.setImageList(slideModels, ScaleTypes.FIT);

        imageSlider.setItemClickListener(position -> {
            startActivity(new Intent(ForYouActivity.this, EventDescActivity.class));
        });
    }

    public void onCreateEventClick(View view) {
        Intent intent = new Intent(ForYouActivity.this, CreateEventActivity.class);
        startActivity(intent);
    }
}
