package com.example.safezone;

import android.net.Uri;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.UUID;

public class FirebaseStorageManager {

    private final FirebaseStorage storage;
    private final FirebaseAuth auth;

    public interface UploadCallback {
        void onProgress(int percent);
        void onSuccess(String downloadUrl);
        void onFailure(String error);
    }

    public FirebaseStorageManager() {
        this.storage = FirebaseStorage.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    public void uploadIncidentImage(Uri fileUri, UploadCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onFailure("User is not authenticated. Please log in.");
            return;
        }

        if (fileUri == null) {
            callback.onFailure("Invalid file URI.");
            return;
        }

        String fileName = UUID.randomUUID().toString() + ".jpg";
        StorageReference storageRef = storage.getReference()
                .child("users")
                .child(user.getUid())
                .child("incidents")
                .child(fileName);

        UploadTask uploadTask = storageRef.putFile(fileUri);

        uploadTask.addOnProgressListener(snapshot -> {
            long total = snapshot.getTotalByteCount();
            if (total > 0) {
                int progress = (int) ((100.0 * snapshot.getBytesTransferred()) / total);
                callback.onProgress(progress);
            }
        }).addOnSuccessListener(taskSnapshot -> {
            storageRef.getDownloadUrl()
                    .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                    .addOnFailureListener(e -> callback.onFailure("Failed to get download URL: " + e.getLocalizedMessage()));
        }).addOnFailureListener(e -> {
            callback.onFailure("Image upload failed: " + e.getLocalizedMessage());
        });
    }
}
