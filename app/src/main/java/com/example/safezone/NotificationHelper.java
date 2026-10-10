package com.example.safezone;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationHelper {

    public static final String CHANNEL_ID = "SAFEZONE_SAFETY";
    public static final String CHANNEL_NAME = "SAFEZONE Safety Alerts & Tips";
    public static final String PREF_SETTINGS = "SafeZoneSettings";
    public static final String KEY_NOTIFICATIONS = "notifications";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Emergency alerts, safety reminders and preparedness notifications");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static boolean areNotificationsEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
        boolean appPrefEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true);
        if (!appPrefEnabled) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static boolean sendNotification(Context context, String title, String message, int notificationId) {
        // Respect user setting
        if (!areNotificationsEnabled(context)) {
            return false;
        }

        // Check Android 13+ runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        createNotificationChannel(context);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        try {
            notificationManager.notify(notificationId, builder.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
