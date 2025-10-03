package com.example.eventlink;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class CreateEventActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private EditText eventName, eventDescription;
    private AutoCompleteTextView searchLocation;
    private Button btnCurrentLocation, btnSearchLocation, btnPost;
    private TextView selectedLocationText;
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private LatLng selectedLatLng;
    private String selectedAddress;
    private ArrayAdapter<String> placesAdapter;
    private List<AutocompletePrediction> predictionList;
    private AutocompleteSessionToken sessionToken;
    private ActivityResultLauncher<String> locationPermissionLauncher;
    private static final String TAG = "CreateEventActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), "AIzaSyAelpVGzYRvaDksblYvfUcLilQ2N4ETODY");
        }
        placesClient = Places.createClient(this);

        eventName = findViewById(R.id.eventName);
        eventDescription = findViewById(R.id.eventDescription);
        searchLocation = findViewById(R.id.searchLocation);
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation);
        btnSearchLocation = findViewById(R.id.btnSearchLocation);
        btnPost = findViewById(R.id.btnPost);
        selectedLocationText = findViewById(R.id.selectedLocationText);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        sessionToken = AutocompleteSessionToken.newInstance();
        setupPlacesAutocomplete();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        getCurrentLocation();
                    } else {
                        Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        btnCurrentLocation.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }
        });

        btnSearchLocation.setOnClickListener(v -> searchAndUpdateMap());
        btnPost.setOnClickListener(v -> createEvent());
    }

    private void setupPlacesAutocomplete() {
        predictionList = new ArrayList<>();
        placesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        searchLocation.setAdapter(placesAdapter);
        searchLocation.setThreshold(1);

        searchLocation.setOnItemClickListener((parent, view, position, id) -> {
            if (position < predictionList.size()) {
                AutocompletePrediction prediction = predictionList.get(position);
                fetchPlaceDetails(prediction.getPlaceId());
            }
        });

        searchLocation.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (!query.isEmpty()) {
                    getPlacePredictions(query);
                } else {
                    predictionList.clear();
                    placesAdapter.clear();
                    placesAdapter.notifyDataSetChanged();
                }
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void getPlacePredictions(String query) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .setCountries("IN")
                .build();

        placesClient.findAutocompletePredictions(request).addOnSuccessListener(response -> {
            predictionList.clear();
            predictionList.addAll(response.getAutocompletePredictions());

            List<String> suggestionsList = new ArrayList<>();
            for (AutocompletePrediction prediction : response.getAutocompletePredictions()) {
                suggestionsList.add(prediction.getFullText(null).toString());
            }

            placesAdapter.clear();
            placesAdapter.addAll(suggestionsList);
            placesAdapter.notifyDataSetChanged();

        }).addOnFailureListener(exception -> Log.e(TAG, "Place prediction error: " + exception.getMessage()));
    }

    private void fetchPlaceDetails(String placeId) {
        List<Place.Field> placeFields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.LAT_LNG,
                Place.Field.ADDRESS
        );

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, placeFields).build();

        placesClient.fetchPlace(request).addOnSuccessListener(response -> {
            Place place = response.getPlace();
            selectedLatLng = place.getLatLng();
            selectedAddress = place.getAddress();
            if (selectedAddress != null) {
                searchLocation.setText(selectedAddress);
                selectedLocationText.setText("Selected: " + selectedAddress);
                Toast.makeText(this, "Location selected. Click Search to view on map", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(exception -> Toast.makeText(this, "Error fetching place details", Toast.LENGTH_SHORT).show());
    }

    private void searchAndUpdateMap() {
        if (selectedLatLng != null) {
            updateMapMarker(selectedLatLng);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 16));
            Toast.makeText(this, "Map updated with selected location", Toast.LENGTH_SHORT).show();
        } else {
            String locationQuery = searchLocation.getText().toString().trim();
            if (!locationQuery.isEmpty()) geocodeAndUpdateMap(locationQuery);
            else Toast.makeText(this, "Please enter or select a location", Toast.LENGTH_SHORT).show();
        }
    }

    private void geocodeAndUpdateMap(String locationQuery) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(locationQuery, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                    runOnUiThread(() -> {
                        selectedLatLng = latLng;
                        selectedAddress = address.getAddressLine(0);
                        updateMapMarker(latLng);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
                        selectedLocationText.setText("Selected: " + selectedAddress);
                        Toast.makeText(this, "Location found!", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(this, "Location not found", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Error searching location", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        LatLng defaultLocation = new LatLng(19.0760, 72.8777);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12));
        mMap.setOnMapClickListener(latLng -> {
            selectedLatLng = latLng;
            updateMapMarker(latLng);
            getAddressFromLatLng(latLng);
        });
    }

    private void getCurrentLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
                selectedLatLng = latLng;
                updateMapMarker(latLng);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
                getAddressFromLatLng(latLng);
            } else {
                Toast.makeText(this, "Unable to fetch location", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateMapMarker(LatLng latLng) {
        if (mMap != null) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(latLng).title("Event Location"));
        }
    }

    private void getAddressFromLatLng(LatLng latLng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    String address = addresses.get(0).getAddressLine(0);
                    runOnUiThread(() -> {
                        selectedAddress = address;
                        searchLocation.setText(address);
                        selectedLocationText.setText("Selected: " + address);
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void createEvent() {
        String name = eventName.getText().toString().trim();
        String description = eventDescription.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Please enter event name", Toast.LENGTH_SHORT).show();
            return;
        }
        if (description.isEmpty()) {
            Toast.makeText(this, "Please enter event description", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedLatLng == null || selectedAddress == null) {
            Toast.makeText(this, "Please select a location", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Event created successfully!", Toast.LENGTH_LONG).show();
        finish();
    }
}
