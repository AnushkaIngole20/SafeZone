package com.example.safezone;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

public class EmergencyContactsActivity extends AppCompatActivity {

    private LinearLayout contactsContainer;
    private SharedPreferences preferences;
    private JSONArray contactsArray;
    private FirestoreManager firestoreManager;

    private static final String PREF_NAME = "SafeZoneContacts";
    private static final String CONTACTS_KEY = "contacts";
    private static final int CALL_PERMISSION = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        contactsContainer = findViewById(R.id.contactsContainer);
        TextView addContact = findViewById(R.id.btnAddContact);

        preferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        firestoreManager = new FirestoreManager();

        loadContacts();

        // Sync with Firestore in the background
        firestoreManager.pullContactsFromCloud(this, new FirestoreManager.DataCallback<JSONArray>() {
            @Override
            public void onSuccess(JSONArray data) {
                if (data != null && data.length() > 0) {
                    contactsArray = data;
                    displayContacts();
                }
            }

            @Override
            public void onFailure(String error) {
                // Preserves offline local contacts
            }
        });

        addContact.setOnClickListener(v -> showContactDialog(-1));
    }

    private void loadContacts() {
        try {
            String savedContacts = preferences.getString(CONTACTS_KEY, "[]");
            contactsArray = new JSONArray(savedContacts);
            displayContacts();
        } catch (Exception e) {
            contactsArray = new JSONArray();
        }
    }

    private void displayContacts() {

        contactsContainer.removeAllViews();

        if (contactsArray.length() == 0) {

            TextView empty = new TextView(this);
            empty.setText("No emergency contacts added yet.");
            empty.setTextSize(16);
            empty.setTextColor(0xFF667085);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(0, 40, 0, 20);

            contactsContainer.addView(empty);
            return;
        }

        for (int i = 0; i < contactsArray.length(); i++) {

            final int index = i;

            try {

                JSONObject contact = contactsArray.getJSONObject(i);

                String name = contact.getString("name");
                String phone = contact.getString("phone");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(32, 24, 32, 24);
                card.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_action_card));

                LinearLayout.LayoutParams cardParams =
                        new LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                        );

                cardParams.setMargins(0, 0, 0, 18);
                card.setLayoutParams(cardParams);

                TextView nameText = new TextView(this);
                nameText.setText(name);
                nameText.setTextSize(18);
                nameText.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary));
                nameText.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);

                TextView phoneText = new TextView(this);
                phoneText.setText(phone);
                phoneText.setTextSize(14);
                phoneText.setTextColor(0xFF64748B);
                phoneText.setPadding(0, 4, 0, 14);

                LinearLayout buttons = new LinearLayout(this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);

                TextView call = createButton("📞 Call");
                TextView edit = createButton("✏️ Edit");
                TextView delete = createButton("🗑️ Delete");

                buttons.addView(call);
                buttons.addView(edit);
                buttons.addView(delete);

                card.addView(nameText);
                card.addView(phoneText);
                card.addView(buttons);

                contactsContainer.addView(card);

                call.setOnClickListener(v -> makeCall(phone));

                edit.setOnClickListener(v -> showContactDialog(index));

                delete.setOnClickListener(v -> confirmDelete(index));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private TextView createButton(String text) {

        TextView button = new TextView(this);

        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.brand_primary));
        button.setGravity(android.view.Gravity.CENTER);
        button.setTypeface(null, android.graphics.Typeface.BOLD);
        button.setBackground(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_input_field));
        button.setPadding(12, 12, 12, 12);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1
                );

        params.setMargins(6, 0, 6, 0);

        button.setLayoutParams(params);

        return button;
    }

    private void showContactDialog(int editIndex) {

        View dialogView = getLayoutInflater()
                .inflate(R.layout.dialog_add_contact, null);

        EditText nameInput = dialogView.findViewById(R.id.inputName);
        EditText phoneInput = dialogView.findViewById(R.id.inputPhone);

        if (editIndex >= 0) {

            try {

                JSONObject contact =
                        contactsArray.getJSONObject(editIndex);

                nameInput.setText(contact.getString("name"));
                phoneInput.setText(contact.getString("phone"));

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editIndex >= 0 ? "Edit Contact" : "Add Contact")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null)
                .create();

        dialog.setOnShowListener(d -> {

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {

                        String name = nameInput.getText().toString().trim();
                        String phone = phoneInput.getText().toString().trim();

                        if (name.isEmpty()) {
                            nameInput.setError("Enter name");
                            return;
                        }

                        if (phone.isEmpty()) {
                            phoneInput.setError("Enter phone number");
                            return;
                        }

                        if (phone.length() < 10) {
                            phoneInput.setError("Enter a valid phone number");
                            return;
                        }

                        try {

                            JSONObject contact = new JSONObject();

                            contact.put("name", name);
                            contact.put("phone", phone);

                            if (editIndex >= 0) {
                                contactsArray.put(editIndex, contact);
                            } else {
                                contactsArray.put(contact);
                            }

                            saveContacts();
                            displayContacts();

                            dialog.dismiss();

                            Toast.makeText(
                                    this,
                                    "Contact saved",
                                    Toast.LENGTH_SHORT
                            ).show();

                        } catch (Exception e) {
                            Toast.makeText(
                                    this,
                                    "Unable to save contact",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    });
        });

        dialog.show();
    }

    private void saveContacts() {
        preferences.edit()
                .putString(CONTACTS_KEY, contactsArray.toString())
                .apply();

        if (firestoreManager != null) {
            firestoreManager.syncContactsToCloud(contactsArray, null);
        }
    }

    private void confirmDelete(int index) {

        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete this contact?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {

                    contactsArray.remove(index);
                    saveContacts();
                    displayContacts();

                    Toast.makeText(
                            this,
                            "Contact deleted",
                            Toast.LENGTH_SHORT
                    ).show();
                })
                .show();
    }

    private void makeCall(String phone) {

        Intent intent = new Intent(
                Intent.ACTION_CALL,
                Uri.parse("tel:" + phone)
        );

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CALL_PHONE
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CALL_PHONE},
                    CALL_PERMISSION
            );

            return;
        }

        startActivity(intent);
    }
}