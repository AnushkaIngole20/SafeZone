package com.example.safezone;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SOSActivity extends AppCompatActivity {

    private TextView locationStatus;
    private TextView sendSOSBtn;
    private ProgressBar progressBar;

    private static final int PERMISSION_REQUEST_CODE = 200;
    private static final String PREF_NAME = "SafeZoneContacts";
    private static final String CONTACTS_KEY = "contacts";

    private LocationManager locationManager;
    private LocationListener locationListener;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sosactivity);

        sendSOSBtn = findViewById(R.id.btnSendSOS);
        locationStatus = findViewById(R.id.locationStatus);
        progressBar = findViewById(R.id.sosProgressBar);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        timeoutHandler = new Handler(Looper.getMainLooper());

        sendSOSBtn.setOnClickListener(v -> showSOSConfirmation());
    }

    private void setLoading(boolean isLoading, String statusText) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (sendSOSBtn != null) {
            sendSOSBtn.setEnabled(!isLoading);
            sendSOSBtn.setAlpha(isLoading ? 0.6f : 1.0f);
        }
        if (locationStatus != null && statusText != null) {
            locationStatus.setText(statusText);
        }
    }

    private void showSOSConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("🚨 Send Emergency SOS?")
                .setMessage("SAFEZONE will acquire your current location and send an emergency SMS with a Google Maps link to your saved emergency contacts.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Send SOS", (dialog, which) -> checkPermissionsAndSendSOS())
                .show();
    }

    private void checkPermissionsAndSendSOS() {
        boolean hasFineLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean hasSms = ContextCompat.checkSelfPermission(
                this, Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED;

        if ((!hasFineLocation && !hasCoarseLocation) || !hasSms) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.SEND_SMS
                    },
                    PERMISSION_REQUEST_CODE
            );
        } else {
            startSOSProcess();
        }
    }

    private void startSOSProcess() {
        List<String> phoneNumbers = getSavedContactNumbers();

        if (phoneNumbers.isEmpty()) {
            setLoading(false, "Status: No emergency contacts saved");
            new AlertDialog.Builder(this)
                    .setTitle("No Emergency Contacts Found")
                    .setMessage("You have not added any emergency contacts yet. Please add at least one contact so SAFEZONE can send your SOS.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Add Contacts", (dialog, which) -> {
                        startActivity(new Intent(SOSActivity.this, EmergencyContactsActivity.class));
                    })
                    .show();
            return;
        }

        setLoading(true, "Acquiring current GPS location...");
        fetchCurrentLocationAndSendSMS(phoneNumbers);
    }

    private List<String> getSavedContactNumbers() {
        List<String> numbers = new ArrayList<>();
        SharedPreferences preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        String savedContacts = preferences.getString(CONTACTS_KEY, "[]");

        try {
            JSONArray jsonArray = new JSONArray(savedContacts);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject contact = jsonArray.getJSONObject(i);
                if (contact.has("phone")) {
                    String phone = contact.getString("phone").trim();
                    if (!phone.isEmpty()) {
                        numbers.add(phone);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return numbers;
    }

    private void fetchCurrentLocationAndSendSMS(List<String> phoneNumbers) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            setLoading(false, "Status: Location permission required");
            Toast.makeText(this, "Location permission required.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isGpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (!isGpsEnabled && !isNetworkEnabled) {
            setLoading(false, "Status: GPS disabled");
            new AlertDialog.Builder(this)
                    .setTitle("Location Services Disabled")
                    .setMessage("GPS is turned off. Your exact coordinates cannot be determined.\n\nWould you like to enable GPS or send the emergency SMS without location coordinates?")
                    .setPositiveButton("Turn On GPS", (dialog, which) -> {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    })
                    .setNeutralButton("Send Without GPS", (dialog, which) -> {
                        sendSOSWithoutLocation(phoneNumbers, "Location unavailable: GPS is turned off on device.");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        // Try getting best known recent location
        Location bestLocation = null;
        if (isGpsEnabled) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        }
        if (bestLocation == null && isNetworkEnabled) {
            bestLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
        if (bestLocation == null) {
            try {
                bestLocation = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER);
            } catch (Exception ignored) {}
        }

        // If a very fresh location (< 60 seconds) exists, use it immediately
        if (bestLocation != null && (System.currentTimeMillis() - bestLocation.getTime() < 60000)) {
            sendSOSMessageWithLocation(bestLocation, phoneNumbers);
            return;
        }

        final Location cachedLocation = bestLocation;

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                removeLocationUpdates();
                sendSOSMessageWithLocation(location, phoneNumbers);
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {}

            @Override
            public void onProviderDisabled(@NonNull String provider) {}
        };

        // Timeout fallback after 8 seconds
        timeoutRunnable = () -> {
            removeLocationUpdates();
            if (cachedLocation != null) {
                // Fallback to cached location if available
                sendSOSMessageWithLocation(cachedLocation, phoneNumbers);
            } else {
                // Send alert stating location timed out
                sendSOSWithoutLocation(phoneNumbers, "Location unavailable: GPS lock timed out.");
            }
        };

        try {
            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, locationListener);
            }
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, locationListener);
            }
            timeoutHandler.postDelayed(timeoutRunnable, 8000);
        } catch (SecurityException e) {
            removeLocationUpdates();
            setLoading(false, "Status: Location permission error");
            Toast.makeText(this, "Permission error while requesting location", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            removeLocationUpdates();
            if (cachedLocation != null) {
                sendSOSMessageWithLocation(cachedLocation, phoneNumbers);
            } else {
                sendSOSWithoutLocation(phoneNumbers, "Location unavailable: Provider error.");
            }
        }
    }

    private void removeLocationUpdates() {
        if (locationListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception e) {
                e.printStackTrace();
            }
            locationListener = null;
        }
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    private void sendSOSMessageWithLocation(Location location, List<String> phoneNumbers) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();

        String locationDetails = lat + ", " + lng;
        String message = "SAFEZONE SOS! I need help.\n\n" +
                "My current location:\n" +
                "https://maps.google.com/?q=" + lat + "," + lng;

        dispatchSms(phoneNumbers, message, locationDetails);
    }

    private void sendSOSWithoutLocation(List<String> phoneNumbers, String reason) {
        String message = "SAFEZONE SOS! I need help.\n\n" +
                "(" + reason + " Please contact or locate me immediately!)";

        dispatchSms(phoneNumbers, message, "Unavailable (" + reason + ")");
    }

    private void dispatchSms(List<String> phoneNumbers, String message, String locationLabel) {
        PackageManager pm = getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)) {
            setLoading(false, "Status: SMS not supported on this device");
            new AlertDialog.Builder(this)
                    .setTitle("SMS Not Supported")
                    .setMessage("This device does not have telephony / SMS capabilities.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        setLoading(true, "Sending emergency SMS to " + phoneNumbers.size() + " contact(s)...");

        int sentCount = 0;
        int failedCount = 0;

        try {
            SmsManager smsManager;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            for (String phone : phoneNumbers) {
                try {
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    if (parts.size() > 1) {
                        smsManager.sendMultipartTextMessage(phone, null, parts, null, null);
                    } else {
                        smsManager.sendTextMessage(phone, null, message, null, null);
                    }
                    sentCount++;
                } catch (Exception e) {
                    e.printStackTrace();
                    failedCount++;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            failedCount = phoneNumbers.size();
        }

        setLoading(false, "Location: " + locationLabel);

        if (sentCount == phoneNumbers.size() && sentCount > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("🚨 SOS Alert Dispatched")
                    .setMessage("Emergency SMS successfully sent to " + sentCount + " emergency contact(s).\n\nCoordinates:\n" + locationLabel)
                    .setPositiveButton("OK", null)
                    .show();
        } else if (sentCount > 0) {
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ Partial SOS Dispatch")
                    .setMessage("SOS was sent to " + sentCount + " contact(s), but failed for " + failedCount + " contact(s). Please verify their phone numbers.")
                    .setPositiveButton("OK", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("❌ Failed to Send SOS")
                    .setMessage("Could not send SMS to any emergency contacts. Please verify cellular signal, SIM card status, and SMS permissions.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;

            if (grantResults.length > 0) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
            } else {
                allGranted = false;
            }

            if (allGranted) {
                startSOSProcess();
            } else {
                setLoading(false, "Status: Permissions denied");

                boolean shouldShowRationale =
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);

                if (!shouldShowRationale) {
                    // User selected "Don't ask again"
                    new AlertDialog.Builder(this)
                            .setTitle("Permissions Required")
                            .setMessage("SMS and Location permissions are required to dispatch an emergency SOS alert. Please enable them in App Settings.")
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
                            "Location and SMS permissions are required to send an emergency SOS.",
                            Toast.LENGTH_LONG
                    ).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        removeLocationUpdates();
    }
}
