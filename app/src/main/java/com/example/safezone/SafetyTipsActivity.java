package com.example.safezone;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SafetyTipsActivity extends AppCompatActivity {

    private static final int NOTIFICATION_PERMISSION_REQUEST = 401;

    private final String[] SAFETY_TIPS = {
            "Stay alert and aware of your surroundings, especially in crowded or unfamiliar areas.",
            "Keep your emergency contacts updated in SAFEZONE and test your SOS setup.",
            "Always share your travel plans and real-time location with a trusted friend or family member.",
            "In case of fire, avoid elevators, stay low under smoke, and proceed to the nearest emergency exit.",
            "Save national emergency numbers like 112 on speed dial for immediate assistance."
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_tips);

        TextView notification = findViewById(R.id.btnSafetyNotification);

        NotificationHelper.createNotificationChannel(this);

        notification.setOnClickListener(v -> checkPermissionAndSendTip());
    }

    private void checkPermissionAndSendTip() {
        if (!NotificationHelper.areNotificationsEnabled(this)) {
            new AlertDialog.Builder(this)
                    .setTitle("Notifications Disabled")
                    .setMessage("Notifications are turned off in SAFEZONE Settings. Would you like to enable them?")
                    .setPositiveButton("Settings", (dialog, which) -> {
                        startActivity(new Intent(SafetyTipsActivity.this, SettingsActivity.class));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST
                );
                return;
            }
        }

        sendRandomSafetyTip();
    }

    private void sendRandomSafetyTip() {
        int index = (int) (Math.random() * SAFETY_TIPS.length);
        String tip = SAFETY_TIPS[index];

        boolean dispatched = NotificationHelper.sendNotification(
                this,
                "🛡️ SAFEZONE Safety Tip",
                tip,
                101
        );

        if (dispatched) {
            Toast.makeText(this, "Safety tip notification dispatched!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Could not display notification. Check notification permissions.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                sendRandomSafetyTip();
            } else {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}