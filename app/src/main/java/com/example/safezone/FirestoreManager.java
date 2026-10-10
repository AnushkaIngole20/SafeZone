package com.example.safezone;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirestoreManager {

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;

    public interface SyncCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public interface DataCallback<T> {
        void onSuccess(T data);
        void onFailure(String error);
    }

    public FirestoreManager() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    private String getUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return (user != null) ? user.getUid() : null;
    }

    // =========================================================================
    // 1. PROFILE SYNCHRONIZATION
    // Path: users/{userId}/profile/info
    // =========================================================================

    public void syncProfileToCloud(String name, String email, String phone, String avatar, SyncCallback callback) {
        String uid = getUserId();
        if (uid == null) {
            if (callback != null) callback.onFailure("User not authenticated.");
            return;
        }

        Map<String, Object> profileData = new HashMap<>();
        profileData.put("name", name);
        profileData.put("email", email);
        profileData.put("phone", phone);
        profileData.put("avatar", avatar);
        profileData.put("updatedAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .collection("profile").document("info")
                .set(profileData)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getLocalizedMessage());
                });
    }

    // =========================================================================
    // 2. EMERGENCY CONTACTS SYNCHRONIZATION
    // Path: users/{userId}/emergencyContacts/list
    // =========================================================================

    public void syncContactsToCloud(JSONArray contactsArray, SyncCallback callback) {
        String uid = getUserId();
        if (uid == null) {
            if (callback != null) callback.onFailure("User not authenticated.");
            return;
        }

        List<Map<String, String>> contactsList = new ArrayList<>();
        for (int i = 0; i < contactsArray.length(); i++) {
            try {
                JSONObject obj = contactsArray.getJSONObject(i);
                Map<String, String> contactMap = new HashMap<>();
                contactMap.put("name", obj.optString("name", ""));
                contactMap.put("phone", obj.optString("phone", ""));
                contactsList.add(contactMap);
            } catch (Exception ignored) {}
        }

        Map<String, Object> data = new HashMap<>();
        data.put("contacts", contactsList);
        data.put("updatedAt", System.currentTimeMillis());

        db.collection("users").document(uid)
                .collection("emergencyContacts").document("list")
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getLocalizedMessage());
                });
    }

    public void pullContactsFromCloud(Context context, DataCallback<JSONArray> callback) {
        String uid = getUserId();
        if (uid == null) {
            if (callback != null) callback.onFailure("User not authenticated.");
            return;
        }

        db.collection("users").document(uid)
                .collection("emergencyContacts").document("list")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && snapshot.contains("contacts")) {
                        try {
                            List<Map<String, String>> list = (List<Map<String, String>>) snapshot.get("contacts");
                            JSONArray jsonArray = new JSONArray();
                            if (list != null) {
                                for (Map<String, String> m : list) {
                                    JSONObject obj = new JSONObject();
                                    obj.put("name", m.get("name"));
                                    obj.put("phone", m.get("phone"));
                                    jsonArray.put(obj);
                                }
                            }

                            // Cache in local preferences for offline resilience
                            context.getSharedPreferences("SafeZoneContacts", Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("contacts", jsonArray.toString())
                                    .apply();

                            if (callback != null) callback.onSuccess(jsonArray);
                        } catch (Exception e) {
                            if (callback != null) callback.onFailure(e.getLocalizedMessage());
                        }
                    } else {
                        if (callback != null) callback.onSuccess(new JSONArray());
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getLocalizedMessage());
                });
    }

    // =========================================================================
    // 3. INCIDENTS SYNCHRONIZATION
    // Path: users/{userId}/incidents/{incidentId}
    // =========================================================================

    public void addIncidentToCloud(JSONObject incidentJson, SyncCallback callback) {
        String uid = getUserId();
        if (uid == null) {
            if (callback != null) callback.onFailure("User not authenticated.");
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("type", incidentJson.optString("type", ""));
        map.put("description", incidentJson.optString("description", ""));
        map.put("location", incidentJson.optString("location", ""));
        map.put("date", incidentJson.optString("date", ""));
        map.put("timestamp", incidentJson.optLong("timestamp", System.currentTimeMillis()));
        map.put("imageUrl", incidentJson.optString("imageUrl", incidentJson.optString("imageUri", "")));

        db.collection("users").document(uid)
                .collection("incidents")
                .add(map)
                .addOnSuccessListener(documentReference -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getLocalizedMessage());
                });
    }

    public void pullIncidentsFromCloud(Context context, DataCallback<JSONArray> callback) {
        String uid = getUserId();
        if (uid == null) {
            if (callback != null) callback.onFailure("User not authenticated.");
            return;
        }

        db.collection("users").document(uid)
                .collection("incidents")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    try {
                        JSONArray cloudArray = new JSONArray();
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            JSONObject obj = new JSONObject();
                            obj.put("id", doc.getId());
                            obj.put("type", doc.getString("type"));
                            obj.put("description", doc.getString("description"));
                            obj.put("location", doc.getString("location"));
                            obj.put("date", doc.getString("date"));
                            obj.put("timestamp", doc.getLong("timestamp"));
                            obj.put("imageUri", doc.getString("imageUrl"));
                            cloudArray.put(obj);
                        }

                        // Sync to local cache
                        context.getSharedPreferences("SafeZoneIncidents", Context.MODE_PRIVATE)
                                .edit()
                                .putString("incidents", cloudArray.toString())
                                .apply();

                        if (callback != null) callback.onSuccess(cloudArray);
                    } catch (Exception e) {
                        if (callback != null) callback.onFailure(e.getLocalizedMessage());
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getLocalizedMessage());
                });
    }

    public void deleteIncidentFromCloud(String incidentId, SyncCallback callback) {
        String uid = getUserId();
        if (uid == null || incidentId == null || incidentId.isEmpty()) {
            if (callback != null) callback.onFailure("Invalid UID or Incident ID.");
            return;
        }

        db.collection("users").document(uid)
                .collection("incidents").document(incidentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e.getLocalizedMessage());
                });
    }
}
