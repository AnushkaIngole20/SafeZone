package com.example.safezone;

import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportIncidentActivity extends AppCompatActivity {

    private EditText description;
    private EditText location;
    private Spinner incidentType;
    private ImageView imagePreview;
    private TextView btnAttachImage;
    private TextView btnUseLocation;
    private TextView btnSubmit;

    private String selectedImageUriString = "";

    private static final String PREF_NAME = "SafeZoneIncidents";
    private static final String INCIDENT_KEY = "incidents";

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_incident);

        incidentType = findViewById(R.id.incidentType);
        description = findViewById(R.id.incidentDescription);
        location = findViewById(R.id.incidentLocation);
        imagePreview = findViewById(R.id.imagePreview);
        btnAttachImage = findViewById(R.id.btnAttachImage);
        btnUseLocation = findViewById(R.id.btnUseLocation);
        btnSubmit = findViewById(R.id.btnSubmitIncident);

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        try {
                            final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
                            try {
                                getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            } catch (Exception ignored) {}

                            selectedImageUriString = uri.toString();
                            imagePreview.setImageURI(uri);
                            imagePreview.setVisibility(View.VISIBLE);
                            btnAttachImage.setText("✓ Photo Evidence Attached (Tap to change)");
                        } catch (Exception e) {
                            Toast.makeText(this, "Unable to load selected photo", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        String[] types = {
                "Select Incident Type",
                "Accident",
                "Harassment",
                "Theft",
                "Medical Emergency",
                "Fire",
                "Suspicious Activity",
                "Other"
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                types
        );

        incidentType.setAdapter(adapter);

        btnUseLocation.setOnClickListener(v -> fetchCurrentLocation());
        btnAttachImage.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        btnSubmit.setOnClickListener(v -> saveIncident());
    }

    private void fetchCurrentLocation() {
        if (!LocationHelper.hasLocationPermission(this)) {
            Toast.makeText(this, "Location permission required. Please enable it in Settings or SOS.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!LocationHelper.isLocationEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("GPS Disabled")
                    .setMessage("Please enable GPS in Android Settings to auto-fill your current location coordinates.")
                    .setPositiveButton("Settings", (dialog, which) -> {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        btnUseLocation.setText("⏳ Acquiring GPS coordinates...");
        btnUseLocation.setEnabled(false);

        LocationHelper.getCurrentLocation(this, new LocationHelper.LocationResultCallback() {
            @Override
            public void onSuccess(Location loc, String addressOrCoordinates) {
                btnUseLocation.setEnabled(true);
                btnUseLocation.setText("📍 Use Current Location");
                location.setText(addressOrCoordinates);
                Toast.makeText(ReportIncidentActivity.this, "Location added successfully", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorMessage) {
                btnUseLocation.setEnabled(true);
                btnUseLocation.setText("📍 Use Current Location");
                Toast.makeText(ReportIncidentActivity.this, "Could not acquire location: " + errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void saveIncident() {
        String type = incidentType.getSelectedItem().toString();
        String desc = description.getText().toString().trim();
        String loc = location.getText().toString().trim();

        if (type.equals("Select Incident Type")) {
            Toast.makeText(this, "Please select an incident type", Toast.LENGTH_SHORT).show();
            return;
        }

        if (desc.isEmpty()) {
            description.setError("Please enter a description of the incident");
            description.requestFocus();
            return;
        }

        if (desc.length() < 5) {
            description.setError("Description should be at least 5 characters");
            description.requestFocus();
            return;
        }

        if (loc.isEmpty()) {
            location.setError("Please enter or detect the location");
            location.requestFocus();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setAlpha(0.6f);

        try {
            JSONObject incident = new JSONObject();
            incident.put("type", type);
            incident.put("description", desc);
            incident.put("location", loc);

            String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(new Date());
            incident.put("date", date);
            incident.put("timestamp", System.currentTimeMillis());

            if (!selectedImageUriString.isEmpty()) {
                incident.put("imageUri", selectedImageUriString);

                btnSubmit.setText("Uploading Evidence...");
                new FirebaseStorageManager().uploadIncidentImage(
                        Uri.parse(selectedImageUriString),
                        new FirebaseStorageManager.UploadCallback() {
                            @Override
                            public void onProgress(int percent) {
                                btnSubmit.setText("Uploading Evidence (" + percent + "%)...");
                            }

                            @Override
                            public void onSuccess(String downloadUrl) {
                                try {
                                    incident.put("imageUrl", downloadUrl);
                                } catch (Exception ignored) {}
                                finishSavingIncident(incident);
                            }

                            @Override
                            public void onFailure(String error) {
                                // Fallback: save incident with local URI so user data is never lost
                                finishSavingIncident(incident);
                            }
                        }
                );
            } else {
                finishSavingIncident(incident);
            }

        } catch (Exception e) {
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1.0f);
            Toast.makeText(this, "Unable to save incident: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void finishSavingIncident(JSONObject incident) {
        try {
            SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            String saved = preferences.getString(INCIDENT_KEY, "[]");
            JSONArray incidents = new JSONArray(saved);

            incidents.put(incident);

            preferences.edit().putString(INCIDENT_KEY, incidents.toString()).apply();

            // Sync with Firestore Cloud
            new FirestoreManager().addIncidentToCloud(incident, null);

            Toast.makeText(this, "Incident reported and recorded successfully", Toast.LENGTH_LONG).show();
            finish();

        } catch (Exception e) {
            btnSubmit.setEnabled(true);
            btnSubmit.setAlpha(1.0f);
            Toast.makeText(this, "Error writing incident: " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}