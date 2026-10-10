package com.example.safezone;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

public class IncidentHistoryActivity extends AppCompatActivity {

    private LinearLayout historyContainer;
    private SharedPreferences preferences;
    private JSONArray incidents;
    private FirestoreManager firestoreManager;

    private static final String PREF_NAME = "SafeZoneIncidents";
    private static final String INCIDENT_KEY = "incidents";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incident_history);

        historyContainer = findViewById(R.id.historyContainer);
        TextView clearHistory = findViewById(R.id.btnClearHistory);

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        firestoreManager = new FirestoreManager();

        loadIncidents();

        clearHistory.setOnClickListener(v -> confirmClearHistory());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (preferences != null) {
            loadIncidents();
        }
    }

    private void loadIncidents() {
        try {
            String saved = preferences.getString(INCIDENT_KEY, "[]");
            incidents = new JSONArray(saved);
        } catch (Exception e) {
            incidents = new JSONArray();
        }
        displayIncidents();

        // Sync with Firestore
        if (firestoreManager != null) {
            firestoreManager.pullIncidentsFromCloud(this, new FirestoreManager.DataCallback<JSONArray>() {
                @Override
                public void onSuccess(JSONArray data) {
                    if (data != null && data.length() > 0) {
                        incidents = data;
                        displayIncidents();
                    }
                }

                @Override
                public void onFailure(String error) {
                    // Offline fallback - local incidents remain displayed
                }
            });
        }
    }

    private void displayIncidents() {
        historyContainer.removeAllViews();

        if (incidents.length() == 0) {
            LinearLayout emptyLayout = new LinearLayout(this);
            emptyLayout.setOrientation(LinearLayout.VERTICAL);
            emptyLayout.setGravity(Gravity.CENTER);
            emptyLayout.setPadding(40, 80, 40, 60);

            TextView emptyIcon = new TextView(this);
            emptyIcon.setText("🛡️");
            emptyIcon.setTextSize(48);
            emptyIcon.setGravity(Gravity.CENTER);

            TextView emptyTitle = new TextView(this);
            emptyTitle.setText("No Incidents Reported");
            emptyTitle.setTextSize(18);
            emptyTitle.setTextColor(Color.parseColor("#0B1F33"));
            emptyTitle.setTypeface(null, Typeface.BOLD);
            emptyTitle.setGravity(Gravity.CENTER);
            emptyTitle.setPadding(0, 16, 0, 8);

            TextView emptySub = new TextView(this);
            emptySub.setText("Your safety history is clear. All reported incidents will appear here.");
            emptySub.setTextSize(14);
            emptySub.setTextColor(Color.parseColor("#667085"));
            emptySub.setGravity(Gravity.CENTER);

            emptyLayout.addView(emptyIcon);
            emptyLayout.addView(emptyTitle);
            emptyLayout.addView(emptySub);

            historyContainer.addView(emptyLayout);
            return;
        }

        // Render from newest to oldest
        for (int i = incidents.length() - 1; i >= 0; i--) {
            final int index = i;
            try {
                JSONObject incident = incidents.getJSONObject(i);

                String type = incident.optString("type", "Incident");
                String description = incident.optString("description", "");
                String location = incident.optString("location", "Not specified");
                String date = incident.optString("date", "Unknown date");
                String imageUri = incident.optString("imageUri", "");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(32, 26, 32, 26);
                card.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_action_card));

                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                cardParams.setMargins(0, 0, 0, 20);
                card.setLayoutParams(cardParams);

                // Type Header
                TextView typeText = new TextView(this);
                typeText.setText("⚠️  " + type);
                typeText.setTextSize(18);
                typeText.setTextColor(Color.parseColor("#0B1F33"));
                typeText.setTypeface(null, Typeface.BOLD);

                // Date
                TextView dateText = new TextView(this);
                dateText.setText("🕒  " + date);
                dateText.setTextSize(13);
                dateText.setTextColor(Color.parseColor("#667085"));
                dateText.setPadding(0, 6, 0, 12);

                // Optional Attached Image
                if (!imageUri.isEmpty()) {
                    try {
                        ImageView imageView = new ImageView(this);
                        LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT, 350
                        );
                        imgParams.setMargins(0, 0, 0, 16);
                        imageView.setLayoutParams(imgParams);
                        imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        imageView.setImageURI(Uri.parse(imageUri));
                        card.addView(imageView);
                    } catch (Exception ignored) {}
                }

                // Description
                TextView descriptionText = new TextView(this);
                descriptionText.setText("Description:\n" + description);
                descriptionText.setTextSize(15);
                descriptionText.setTextColor(Color.parseColor("#344054"));
                descriptionText.setPadding(0, 0, 0, 10);

                // Location
                TextView locationText = new TextView(this);
                locationText.setText("📍  Location:\n" + location);
                locationText.setTextSize(14);
                locationText.setTextColor(Color.parseColor("#1D2939"));
                locationText.setPadding(0, 0, 0, 16);

                // Buttons layout
                LinearLayout buttonsLayout = new LinearLayout(this);
                buttonsLayout.setOrientation(LinearLayout.HORIZONTAL);

                TextView btnMap = createActionButton("🗺️ Map", "#F0F4F8", "#0B1F33");
                TextView btnEdit = createActionButton("✏️ Edit", "#F0F4F8", "#0B1F33");
                TextView btnDelete = createActionButton("🗑️ Delete", "#FFF1F0", "#B42318");

                buttonsLayout.addView(btnMap);
                buttonsLayout.addView(btnEdit);
                buttonsLayout.addView(btnDelete);

                card.addView(typeText);
                card.addView(dateText);
                card.addView(descriptionText);
                card.addView(locationText);
                card.addView(buttonsLayout);

                historyContainer.addView(card);

                btnMap.setOnClickListener(v -> openLocationInMap(location));
                btnEdit.setOnClickListener(v -> editIncident(index));
                btnDelete.setOnClickListener(v -> confirmDelete(index));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private TextView createActionButton(String text, String bgColorHex, String textColorHex) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.parseColor(textColorHex));
        button.setBackgroundColor(Color.parseColor(bgColorHex));
        button.setGravity(Gravity.CENTER);
        button.setPadding(12, 16, 12, 16);
        button.setTypeface(null, Typeface.BOLD);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
        );
        params.setMargins(6, 0, 6, 0);
        button.setLayoutParams(params);

        return button;
    }

    private void openLocationInMap(String loc) {
        if (loc.isEmpty() || loc.equalsIgnoreCase("Not specified")) {
            Toast.makeText(this, "No valid location specified", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(loc));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to launch map app", Toast.LENGTH_SHORT).show();
        }
    }

    private void editIncident(int index) {
        try {
            JSONObject incident = incidents.getJSONObject(index);

            EditText descriptionInput = new EditText(this);
            descriptionInput.setHint("Enter updated description");
            descriptionInput.setText(incident.optString("description", ""));
            descriptionInput.setPadding(40, 30, 40, 30);

            new AlertDialog.Builder(this)
                    .setTitle("Edit Incident Description")
                    .setView(descriptionInput)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        String newDesc = descriptionInput.getText().toString().trim();
                        if (!newDesc.isEmpty()) {
                            try {
                                incident.put("description", newDesc);
                                saveIncidents();
                                displayIncidents();
                                Toast.makeText(this, "Incident description updated", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(this, "Failed to update description", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open incident for editing", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete(int index) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Incident Record")
                .setMessage("Are you sure you want to permanently delete this reported incident from your history?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    try {
                        JSONObject toRemove = incidents.getJSONObject(index);
                        if (toRemove.has("id") && firestoreManager != null) {
                            firestoreManager.deleteIncidentFromCloud(toRemove.getString("id"), null);
                        }
                    } catch (Exception ignored) {}
                    incidents.remove(index);
                    saveIncidents();
                    displayIncidents();
                    Toast.makeText(this, "Incident removed from history", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void confirmClearHistory() {
        if (incidents.length() == 0) {
            Toast.makeText(this, "History is already empty", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Clear Entire History")
                .setMessage("Are you sure you want to remove all saved incidents from your local history?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear All", (dialog, which) -> {
                    incidents = new JSONArray();
                    saveIncidents();
                    displayIncidents();
                    Toast.makeText(this, "All incident history cleared", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void saveIncidents() {
        preferences.edit().putString(INCIDENT_KEY, incidents.toString()).apply();
    }
}