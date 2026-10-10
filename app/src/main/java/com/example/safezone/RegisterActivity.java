package com.example.safezone;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

public class RegisterActivity extends AppCompatActivity {

    private EditText name, email, phone, password, confirmPassword;
    private TextView registerBtn, loginBtn;
    private ProgressBar progressBar;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        name = findViewById(R.id.inputName);
        email = findViewById(R.id.inputEmail);
        phone = findViewById(R.id.inputPhone);
        password = findViewById(R.id.inputPassword);
        confirmPassword = findViewById(R.id.inputConfirmPassword);
        progressBar = findViewById(R.id.progressBar);

        registerBtn = findViewById(R.id.btnRegister);
        loginBtn = findViewById(R.id.btnLogin);

        auth = FirebaseAuth.getInstance();

        registerBtn.setOnClickListener(v -> registerUser());

        loginBtn.setOnClickListener(v -> finish());
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        registerBtn.setEnabled(!isLoading);
        registerBtn.setAlpha(isLoading ? 0.6f : 1.0f);
        loginBtn.setEnabled(!isLoading);
    }

    private void registerUser() {
        String userName = name.getText().toString().trim();
        String userEmail = email.getText().toString().trim();
        String userPhone = phone.getText().toString().trim();
        String userPassword = password.getText().toString().trim();
        String userConfirmPassword = confirmPassword.getText().toString().trim();

        if (TextUtils.isEmpty(userName)) {
            name.setError("Enter your name");
            name.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(userEmail)) {
            email.setError("Enter your email");
            email.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            email.setError("Enter a valid email address");
            email.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(userPhone)) {
            phone.setError("Enter phone number");
            phone.requestFocus();
            return;
        }

        if (userPhone.length() < 10) {
            phone.setError("Enter a valid 10-digit phone number");
            phone.requestFocus();
            return;
        }

        if (userPassword.length() < 6) {
            password.setError("Password must be at least 6 characters");
            password.requestFocus();
            return;
        }

        if (!userPassword.equals(userConfirmPassword)) {
            confirmPassword.setError("Passwords do not match");
            confirmPassword.requestFocus();
            return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            UserProfileChangeRequest profile =
                                    new UserProfileChangeRequest.Builder()
                                            .setDisplayName(userName)
                                            .build();
                            user.updateProfile(profile);
                        }

                        // Save local profile information
                        getSharedPreferences("SafeZoneProfile", MODE_PRIVATE)
                                .edit()
                                .putString("name", userName)
                                .putString("email", userEmail)
                                .putString("phone", userPhone)
                                .apply();

                        setLoading(false);

                        Toast.makeText(
                                this,
                                "Account created successfully!",
                                Toast.LENGTH_SHORT
                        ).show();

                        Intent intent = new Intent(
                                RegisterActivity.this,
                                MainActivity.class
                        );
                        intent.setFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );
                        startActivity(intent);
                        finish();

                    } else {
                        setLoading(false);
                        handleRegistrationError(task.getException());
                    }
                });
    }

    private void handleRegistrationError(Exception exception) {
        String message;
        if (exception instanceof FirebaseAuthUserCollisionException) {
            message = "This email is already registered. Please login instead.";
        } else if (exception instanceof FirebaseAuthWeakPasswordException) {
            message = "Password is too weak. Please choose a stronger password.";
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            message = "Invalid email format. Please check your email.";
        } else if (exception instanceof FirebaseNetworkException) {
            message = "Network error. Please check your internet connection.";
        } else if (exception != null && exception.getLocalizedMessage() != null) {
            message = exception.getLocalizedMessage();
        } else {
            message = "Registration failed. Please try again.";
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}