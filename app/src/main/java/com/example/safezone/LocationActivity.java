package com.example.safezone;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class LocationActivity extends AppCompatActivity {

    private TextView locationInfo;
    private TextView getLocationBtn;
    private TextView openMapsBtn;
    private ProgressBar progressBar;

    private static final int LOCATION_PERMISSION = 102;

    private double latitude = 0;
    private double longitude = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location);

        locationInfo = findViewById(R.id.locationInfo);
        getLocationBtn = findViewById(R.id.btnGetLocation);
        openMapsBtn = findViewById(R.id.btnOpenMaps);
        progressBar = findViewById(R.id.locationProgressBar);

        getLocationBtn.setOnClickListener(v -> checkLocationPermissionAndFetch());
        openMapsBtn.setOnClickListener(v -> openGoogleMaps());

        // Auto-fetch if permission is already granted
        if (LocationHelper.hasLocationPermission(this)) {
            fetchLocation();
        }
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (getLocationBtn != null) {
            getLocationBtn.setEnabled(!isLoading);
            getLocationBtn.setAlpha(isLoading ? 0.6f : 1.0f);
        }
    }

    private void checkLocationPermissionAndFetch() {
        if (!LocationHelper.hasLocationPermission(this)) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION
            );
        } else {
            fetchLocation();
        }
    }

    private void fetchLocation() {
        if (!LocationHelper.isLocationEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Location Disabled")
                    .setMessage("GPS is turned off. Please enable location services to determine your exact coordinates.")
                    .setPositiveButton("Enable Location", (dialog, which) -> {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        setLoading(true);
        locationInfo.setText("⏳ Acquiring current GPS location...\nPlease wait.");

        LocationHelper.getCurrentLocation(this, new LocationHelper.LocationResultCallback() {
            @Override
            public void onSuccess(Location location, String addressOrCoordinates) {
                setLoading(false);
                latitude = location.getLatitude();
                longitude = location.getLongitude();

                String accuracyText = location.hasAccuracy() ? String.format("%.1f meters", location.getAccuracy()) : "Unknown";

                StringBuilder sb = new StringBuilder();
                sb.append("📍 Current Location Details\n\n");
                sb.append("Latitude: ").append(latitude).append("\n");
                sb.append("Longitude: ").append(longitude).append("\n");
                sb.append("Accuracy: ").append(accuracyText).append("\n\n");
                sb.append("Approx. Address:\n").append(addressOrCoordinates);

                locationInfo.setText(sb.toString());
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                locationInfo.setText("❌ Location Unavailable\n\n" + errorMessage + "\n\nPlease try again with an open sky view.");
                Toast.makeText(LocationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openGoogleMaps() {
        if (latitude == 0 && longitude == 0) {
            Toast.makeText(
                    this,
                    "Get your location first",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Uri uri = Uri.parse(LocationHelper.getGoogleMapsUrl(latitude, longitude));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "No browser or map app found to open location", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchLocation();
            } else {
                setLoading(false);
                boolean rationale = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);
                if (!rationale) {
                    new AlertDialog.Builder(this)
                            .setTitle("Permission Needed")
                            .setMessage("Location permission is needed to show your coordinates. Please allow it in App Settings.")
                            .setPositiveButton("Open Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", getPackageName(), null);
                                intent.setData(uri);
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                } else {
                    Toast.makeText(
                            this,
                            "Location permission denied",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        }
    }
}