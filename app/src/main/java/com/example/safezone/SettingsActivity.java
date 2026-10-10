package com.example.safezone;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {

    private Switch darkMode;
    private Switch notifications;

    private SharedPreferences preferences;

    private static final String PREF_NAME = "SafeZoneSettings";
    private static final String KEY_DARK_MODE = "darkMode";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 301;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        darkMode = findViewById(R.id.switchDarkMode);
        notifications = findViewById(R.id.switchNotifications);

        TextView emergencyInfo = findViewById(R.id.btnEmergencyInfo);
        TextView privacy = findViewById(R.id.btnPrivacy);
        TextView about = findViewById(R.id.btnAbout);

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        loadSettings();

        darkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_DARK_MODE, isChecked).apply();

            AppCompatDelegate.setDefaultNightMode(
                    isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
            );

            Toast.makeText(
                    this,
                    isChecked ? "Dark Mode enabled" : "Dark Mode disabled",
                    Toast.LENGTH_SHORT
            ).show();
        });

        notifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            preferences.edit().putBoolean(KEY_NOTIFICATIONS, isChecked).apply();

            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(
                                this,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                NOTIFICATION_PERMISSION_REQUEST
                        );
                    }
                }
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_SHORT).show();
            }
        });

        emergencyInfo.setOnClickListener(v -> showEmergencyInfo());
        privacy.setOnClickListener(v -> showPrivacyInfo());
        about.setOnClickListener(v -> showAbout());
    }

    private void loadSettings() {
        boolean dark = preferences.getBoolean(KEY_DARK_MODE, false);
        boolean notification = preferences.getBoolean(KEY_NOTIFICATIONS, true);

        darkMode.setChecked(dark);
        notifications.setChecked(notification);
    }

    private void showEmergencyInfo() {
        new AlertDialog.Builder(this)
                .setTitle("🚨 Emergency Guidelines & Helplines")
                .setMessage(
                        "National Emergency Number: 112\n" +
                        "Police: 100\n" +
                        "Fire & Rescue: 101\n" +
                        "Ambulance / Medical: 108 / 102\n" +
                        "Women Helpline: 1090 / 1091\n" +
                        "Child Helpline: 1098\n\n" +
                        "Emergency Tips:\n" +
                        "• Keep your phone charged and GPS enabled.\n" +
                        "• Save at least 2 trusted emergency contacts.\n" +
                        "• Move to a secure, well-lit location before reporting."
                )
                .setPositiveButton("Understood", null)
                .show();
    }

    private void showPrivacyInfo() {
        new AlertDialog.Builder(this)
                .setTitle("🔒 Privacy Policy & Data Security")
                .setMessage(
                        "SAFEZONE respects your privacy and safety:\n\n" +
                        "1. Local Storage: Emergency contacts and incident history are stored securely on your local device.\n\n" +
                        "2. Location Access: Location is queried only when requesting your location, reporting incidents, or triggering an SOS alert.\n\n" +
                        "3. SMS Transmission: Emergency SMS messages are dispatched only to the contacts you explicitly configure.\n\n" +
                        "4. Account Data: User authentication is handled securely through Firebase Authentication."
                )
                .setPositiveButton("Close", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("About SAFEZONE")
                .setMessage(
                        "SAFEZONE\n" +
                        "Personal Emergency & Safety Assistant\n\n" +
                        "Version: 1.0\n" +
                        "Build: Production Debug\n\n" +
                        "Features:\n" +
                        "• Instant GPS SOS via SMS\n" +
                        "• Emergency Contacts Manager\n" +
                        "• Location Explorer & Maps\n" +
                        "• Incident Logger\n" +
                        "• AI Safety Assistant\n\n" +
                        "Stay prepared. Stay safe."
                )
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Notification permission denied in system settings", Toast.LENGTH_SHORT).show();
            }
        }
    }
}