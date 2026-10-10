package com.example.safezone;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class ProfileActivity extends AppCompatActivity {

    private EditText name;
    private EditText email;
    private EditText phone;
    private TextView profileIcon;

    private SharedPreferences preferences;

    private static final String PREF_NAME = "SafeZoneProfile";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_AVATAR = "avatar";

    private final String[] AVATARS = {"👤", "🛡️", "👨", "👩", "🧑‍⚕️", "👮", "⚡", "🌟"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        profileIcon = findViewById(R.id.profileIcon);
        name = findViewById(R.id.profileName);
        email = findViewById(R.id.profileEmail);
        phone = findViewById(R.id.profilePhone);

        TextView save = findViewById(R.id.btnSaveProfile);
        TextView settings = findViewById(R.id.btnSettings);
        TextView logout = findViewById(R.id.btnLogout);

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        loadProfile();

        profileIcon.setOnClickListener(v -> showAvatarPicker());
        save.setOnClickListener(v -> saveProfile());
        settings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        logout.setOnClickListener(v -> showLogoutDialog());
    }

    private void showAvatarPicker() {
        new AlertDialog.Builder(this)
                .setTitle("Select Profile Avatar")
                .setItems(AVATARS, (dialog, which) -> {
                    String selectedAvatar = AVATARS[which];
                    profileIcon.setText(selectedAvatar);
                    preferences.edit().putString(KEY_AVATAR, selectedAvatar).apply();
                    Toast.makeText(this, "Avatar updated", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void loadProfile() {
        String savedName = preferences.getString(KEY_NAME, "");
        String savedEmail = preferences.getString(KEY_EMAIL, "");
        String savedPhone = preferences.getString(KEY_PHONE, "");
        String savedAvatar = preferences.getString(KEY_AVATAR, "👤");

        profileIcon.setText(savedAvatar);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            if (savedName.isEmpty() && currentUser.getDisplayName() != null) {
                savedName = currentUser.getDisplayName();
            }
            if (savedEmail.isEmpty() && currentUser.getEmail() != null) {
                savedEmail = currentUser.getEmail();
            }
        }

        name.setText(savedName);
        email.setText(savedEmail);
        phone.setText(savedPhone);

        // Keep email aligned with Firebase Auth account
        if (currentUser != null && currentUser.getEmail() != null) {
            email.setText(currentUser.getEmail());
            email.setFocusable(false);
            email.setClickable(false);
            email.setCursorVisible(false);
        }
    }

    private void saveProfile() {
        String userName = name.getText().toString().trim();
        String userEmail = email.getText().toString().trim();
        String userPhone = phone.getText().toString().trim();

        if (userName.isEmpty()) {
            name.setError("Enter your name");
            name.requestFocus();
            return;
        }

        if (userEmail.isEmpty()) {
            email.setError("Enter your email");
            email.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            email.setError("Enter a valid email address");
            email.requestFocus();
            return;
        }

        if (userPhone.isEmpty()) {
            phone.setError("Enter your phone number");
            phone.requestFocus();
            return;
        }

        if (userPhone.length() < 10) {
            phone.setError("Enter a valid 10-digit phone number");
            phone.requestFocus();
            return;
        }

        // Save locally
        preferences.edit()
                .putString(KEY_NAME, userName)
                .putString(KEY_EMAIL, userEmail)
                .putString(KEY_PHONE, userPhone)
                .apply();

        // Sync with Firebase User profile
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            UserProfileChangeRequest profileUpdate = new UserProfileChangeRequest.Builder()
                    .setDisplayName(userName)
                    .build();
            user.updateProfile(profileUpdate);
        }

        // Sync with Firestore Cloud document
        new FirestoreManager().syncProfileToCloud(
                userName,
                userEmail,
                userPhone,
                preferences.getString(KEY_AVATAR, "👤"),
                null
        );

        Toast.makeText(this, "Profile updated and synchronized successfully", Toast.LENGTH_SHORT).show();
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to log out of SAFEZONE?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Logout", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();

                    Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }
}