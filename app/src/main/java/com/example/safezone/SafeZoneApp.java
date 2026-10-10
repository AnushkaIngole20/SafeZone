package com.example.safezone;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class SafeZoneApp extends Application {

    private static final String PREF_SETTINGS = "SafeZoneSettings";
    private static final String KEY_DARK_MODE = "darkMode";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1. Initialize Firebase
        FirebaseApp.initializeApp(this);

        // 2. Configure Firebase App Check
        // Uses Debug provider in development, Play Integrity in release
        try {
            FirebaseAppCheck appCheck = FirebaseAppCheck.getInstance();
            if (BuildConfig.DEBUG) {
                appCheck.installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                );
            } else {
                appCheck.installAppCheckProviderFactory(
                        PlayIntegrityAppCheckProviderFactory.getInstance()
                );
            }
        } catch (Exception e) {
            // Protect local testing if Play Integrity / debug token is not configured yet
            e.printStackTrace();
        }

        // 3. Restore user's preferred theme across the entire application
        SharedPreferences preferences = getSharedPreferences(PREF_SETTINGS, MODE_PRIVATE);
        boolean isDarkMode = preferences.getBoolean(KEY_DARK_MODE, false);

        AppCompatDelegate.setDefaultNightMode(
                isDarkMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        // 4. Initialize Notification Channels
        NotificationHelper.createNotificationChannel(this);
    }
}
