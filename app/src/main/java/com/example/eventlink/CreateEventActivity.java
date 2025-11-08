package com.example.eventlink;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import com.google.android.libraries.places.api.model.PhotoMetadata;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPhotoRequest;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreateEventActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String TAG = "CreateEventActivity";

    // UI
    private GoogleMap mMap;
    private EditText eventName, eventDescription;
    private AutoCompleteTextView searchLocation;
    private AutoCompleteTextView domainDropdown;   // ✅ NEW FIELD
    private Button btnCurrentLocation, btnSearchLocation, btnPost;
    private TextView selectedLocationText;

    // Location/Places
    private FusedLocationProviderClient fusedLocationClient;
    private PlacesClient placesClient;
    private AutocompleteSessionToken sessionToken;

    // Selected data
    private LatLng selectedLatLng;
    private String selectedAddress;
    private String selectedPlaceId;

    // Autocomplete adapter
    private ArrayAdapter<String> placesAdapter;
    private List<AutocompletePrediction> predictionList;

    // Permissions
    private ActivityResultLauncher<String> locationPermissionLauncher;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // Keys
    private static final String ANDROID_PLACES_API_KEY = "AIzaSyAelpVGzYRvaDksblYvfUcLilQ2N4ETODY";
    private static final String STATIC_MAPS_API_KEY = "AIzaSyCeW_59JNoFRUCJk1pi-uxLScHTCu68aNQ";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        if (!Places.isInitialized()) {
            Places.initialize(getApplicationContext(), ANDROID_PLACES_API_KEY);
        }
        placesClient = Places.createClient(this);

        eventName = findViewById(R.id.eventName);
        eventDescription = findViewById(R.id.eventDescription);
        searchLocation = findViewById(R.id.searchLocation);
        domainDropdown = findViewById(R.id.domainDropdown);   // ✅ NEW FIELD
        btnCurrentLocation = findViewById(R.id.btnCurrentLocation);
        btnSearchLocation = findViewById(R.id.btnSearchLocation);
        btnPost = findViewById(R.id.btnPost);
        selectedLocationText = findViewById(R.id.selectedLocationText);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        sessionToken = AutocompleteSessionToken.newInstance();

        setupPlacesAutocomplete();
        setupDomainDropdown();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) getCurrentLocation();
                    else Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
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

    private void setupDomainDropdown() {
        String[] domains = {
                "AI/ML",
                "Cybersecurity",
                "Web/App Dev",
                "IoT/Hardware",
                "Blockchain",
                "Cloud/DevOps"
        };

        ArrayAdapter<String> domainAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, domains);

        domainDropdown.setAdapter(domainAdapter);
        domainDropdown.setThreshold(1);
    }

    private void setupPlacesAutocomplete() {
        predictionList = new ArrayList<>();
        placesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>());
        searchLocation.setAdapter(placesAdapter);
        searchLocation.setThreshold(1);

        searchLocation.setOnItemClickListener((parent, view, position, id) -> {
            if (position < predictionList.size()) {
                selectedPlaceId = predictionList.get(position).getPlaceId();
                fetchPlaceDetails(selectedPlaceId);
            }
        });

        searchLocation.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (!query.isEmpty()) getPlacePredictions(query);
                else {
                    predictionList.clear();
                    placesAdapter.clear();
                    placesAdapter.notifyDataSetChanged();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    private void getPlacePredictions(String query) {
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .setCountries("IN")
                .build();

        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    predictionList.clear();
                    predictionList.addAll(response.getAutocompletePredictions());

                    List<String> suggestions = new ArrayList<>();
                    for (AutocompletePrediction p : predictionList) {
                        suggestions.add(p.getFullText(null).toString());
                    }

                    placesAdapter.clear();
                    placesAdapter.addAll(suggestions);
                    placesAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Log.e(TAG, "Autocomplete error: ", e));
    }

    private void fetchPlaceDetails(String placeId) {
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ID,
                Place.Field.NAME,
                Place.Field.ADDRESS,
                Place.Field.LAT_LNG,
                Place.Field.PHOTO_METADATAS
        );

        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, fields).build();

        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    selectedLatLng = place.getLatLng();
                    selectedAddress = place.getAddress();

                    if (selectedAddress != null) {
                        searchLocation.setText(selectedAddress);
                        selectedLocationText.setText("Selected: " + selectedAddress);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Failed to fetch place details", Toast.LENGTH_SHORT).show()
                );
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

    private void updateMapMarker(LatLng latLng) {
        if (mMap != null) {
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(latLng).title("Event Location"));
        }
    }

    private void searchAndUpdateMap() {
        if (selectedLatLng != null) {
            updateMapMarker(selectedLatLng);
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 16));
        } else {
            String query = searchLocation.getText().toString().trim();
            if (!query.isEmpty()) geocodeAndUpdateMap(query);
            else Toast.makeText(this, "Enter a location", Toast.LENGTH_SHORT).show();
        }
    }

    private void geocodeAndUpdateMap(String query) {
        new Thread(() -> {
            try {
                Geocoder g = new Geocoder(this, Locale.getDefault());
                List<Address> list = g.getFromLocationName(query, 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    LatLng latLng = new LatLng(a.getLatitude(), a.getLongitude());
                    runOnUiThread(() -> {
                        selectedLatLng = latLng;
                        selectedAddress = a.getAddressLine(0);
                        updateMapMarker(latLng);
                        selectedLocationText.setText("Selected: " + selectedAddress);
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16));
                    });
                }
            } catch (IOException ignored) {}
        }).start();
    }

    private void getCurrentLocation() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                selectedLatLng = new LatLng(loc.getLatitude(), loc.getLongitude());
                updateMapMarker(selectedLatLng);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(selectedLatLng, 16));
                getAddressFromLatLng(selectedLatLng);
            }
        });
    }

    private void getAddressFromLatLng(LatLng latLng) {
        new Thread(() -> {
            try {
                Geocoder g = new Geocoder(this, Locale.getDefault());
                List<Address> addr = g.getFromLocation(latLng.latitude, latLng.longitude, 1);
                if (addr != null && !addr.isEmpty()) {
                    runOnUiThread(() -> {
                        selectedAddress = addr.get(0).getAddressLine(0);
                        searchLocation.setText(selectedAddress);
                        selectedLocationText.setText("Selected: " + selectedAddress);
                    });
                }
            } catch (IOException ignored) {}
        }).start();
    }

    private interface PhotoCallback { void onPhoto(Bitmap bitmap); }
    private interface UploadCallback { void onUpload(String url); }

    private String buildStaticMapUrl(double lat, double lng) {
        return "https://maps.googleapis.com/maps/api/staticmap?" +
                "center=" + lat + "," + lng +
                "&zoom=16&size=640x400&markers=color:red|" + lat + "," + lng +
                "&key=" + STATIC_MAPS_API_KEY;
    }

    private void fetchPlacePhotoOrStatic(LatLng latLng, String placeId, PhotoCallback callback) {
        if (placeId == null) {
            downloadBitmapFromUrlAsync(buildStaticMapUrl(latLng.latitude, latLng.longitude), callback);
            return;
        }
        List<Place.Field> fields = Arrays.asList(Place.Field.PHOTO_METADATAS);
        FetchPlaceRequest request = FetchPlaceRequest.builder(placeId, fields).build();
        placesClient.fetchPlace(request)
                .addOnSuccessListener(placeResponse -> {
                    Place place = placeResponse.getPlace();
                    List<PhotoMetadata> metadata = place.getPhotoMetadatas();
                    if (metadata == null || metadata.isEmpty()) {
                        downloadBitmapFromUrlAsync(buildStaticMapUrl(latLng.latitude, latLng.longitude), callback);
                        return;
                    }
                    PhotoMetadata photo = metadata.get(0);
                    FetchPhotoRequest photoRequest = FetchPhotoRequest.builder(photo)
                            .setMaxWidth(800)
                            .setMaxHeight(800)
                            .build();
                    placesClient.fetchPhoto(photoRequest)
                            .addOnSuccessListener(photoResponse -> callback.onPhoto(photoResponse.getBitmap()))
                            .addOnFailureListener(e -> {
                                Log.w(TAG, "Photo fetch failed, using static map", e);
                                downloadBitmapFromUrlAsync(buildStaticMapUrl(latLng.latitude, latLng.longitude), callback);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Place fetch failed, using static map", e);
                    downloadBitmapFromUrlAsync(buildStaticMapUrl(latLng.latitude, latLng.longitude), callback);
                });
    }

    private void downloadBitmapFromUrlAsync(String url, PhotoCallback callback) {
        new Thread(() -> {
            Bitmap bmp = null;
            try {
                URL u = new URL(url);
                HttpURLConnection conn = (HttpURLConnection) u.openConnection();
                conn.setDoInput(true);
                conn.connect();
                InputStream is = conn.getInputStream();
                bmp = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Static map download failed", e);
            }
            Bitmap finalBmp = bmp;
            runOnUiThread(() -> callback.onPhoto(finalBmp));
        }).start();
    }

    private void uploadImageToStorage(Bitmap bitmap, String eventId, UploadCallback callback) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos);
        byte[] data = baos.toByteArray();

        StorageReference ref = storage.getReference().child("eventImages/" + eventId + ".jpg");
        ref.putBytes(data)
                .addOnSuccessListener(task -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> callback.onUpload(uri.toString())))
                .addOnFailureListener(e -> callback.onUpload(null));
    }

    // ---------------------- Create Event flow ----------------------
    private void createEvent() {
        String name = eventName.getText().toString().trim();
        String desc = eventDescription.getText().toString().trim();
        String domain = domainDropdown.getText().toString().trim();   // ✅ NEW

        if (name.isEmpty()) { Toast.makeText(this, "Enter event name", Toast.LENGTH_SHORT).show(); return; }
        if (desc.isEmpty()) { Toast.makeText(this, "Enter description", Toast.LENGTH_SHORT).show(); return; }

        // ✅ MANDATORY DOMAIN CHECK
        if (domain.isEmpty()) {
            Toast.makeText(this, "Please select a domain", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedLatLng == null) {
            Toast.makeText(this, "Pick a location", Toast.LENGTH_SHORT).show();
            return;
        }

        String eventId = db.collection("createdEvents").document().getId();
        Toast.makeText(this, "Creating event…", Toast.LENGTH_SHORT).show();

        fetchPlacePhotoOrStatic(selectedLatLng, selectedPlaceId, bitmap -> {
            if (bitmap == null) {
                Toast.makeText(this, "Could not get image", Toast.LENGTH_SHORT).show();
                return;
            }
            uploadImageToStorage(bitmap, eventId, imageUrl -> {
                if (imageUrl == null) {
                    Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show();
                    return;
                }
                saveEventToFirestore(eventId, name, desc, domain, selectedAddress,
                        selectedLatLng, selectedPlaceId, imageUrl);
            });
        });
    }

    private void saveEventToFirestore(String eventId, String name, String desc,
                                      String domain, String addr, LatLng latLng,
                                      String placeId, String imageUrl) {

        Map<String, Object> event = new HashMap<>();
        event.put("name", name);
        event.put("description", desc);
        event.put("domain", domain);  // ✅ NEW FIELD SAVED
        event.put("address", addr);
        event.put("latitude", latLng.latitude);
        event.put("longitude", latLng.longitude);
        event.put("placeId", placeId);
        event.put("imageUrl", imageUrl);
        event.put("createdAt", FieldValue.serverTimestamp());

        db.collection("createdEvents").document(eventId)
                .set(event)
                .addOnSuccessListener(a -> {
                    Toast.makeText(this, "✅ Event Created", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "❌ Failed to save event", Toast.LENGTH_SHORT).show()
                );
    }
}
