package com.example.safezone;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

public class LoginActivity extends AppCompatActivity {

    private EditText email;
    private EditText password;
    private TextView loginBtn;
    private TextView registerBtn;
    private TextView forgotPasswordBtn;
    private ProgressBar progressBar;

    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();

        // Redirect if already authenticated
        if (auth.getCurrentUser() != null) {
            navigateToHome();
            return;
        }

        setContentView(R.layout.activity_login);

        email = findViewById(R.id.inputEmail);
        password = findViewById(R.id.inputPassword);
        loginBtn = findViewById(R.id.btnLogin);
        registerBtn = findViewById(R.id.btnRegister);
        forgotPasswordBtn = findViewById(R.id.btnForgotPassword);
        progressBar = findViewById(R.id.progressBar);

        loginBtn.setOnClickListener(v -> loginUser());

        registerBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        forgotPasswordBtn.setOnClickListener(v -> resetPassword());
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        loginBtn.setEnabled(!isLoading);
        loginBtn.setAlpha(isLoading ? 0.6f : 1.0f);
        registerBtn.setEnabled(!isLoading);
        forgotPasswordBtn.setEnabled(!isLoading);
    }

    private void loginUser() {
        String userEmail = email.getText().toString().trim();
        String userPassword = password.getText().toString().trim();

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

        if (userPassword.isEmpty()) {
            password.setError("Enter your password");
            password.requestFocus();
            return;
        }

        if (userPassword.length() < 6) {
            password.setError("Password must be at least 6 characters");
            password.requestFocus();
            return;
        }

        setLoading(true);

        auth.signInWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(this, task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(
                                this,
                                "Login successful",
                                Toast.LENGTH_SHORT
                        ).show();
                        navigateToHome();
                    } else {
                        handleLoginError(task.getException());
                    }
                });
    }

    private void handleLoginError(Exception exception) {
        String message;
        if (exception instanceof FirebaseAuthInvalidUserException) {
            message = "No account found with this email. Please register.";
        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            message = "Incorrect password or invalid email format.";
        } else if (exception instanceof FirebaseTooManyRequestsException) {
            message = "Too many failed attempts. Please try again later.";
        } else if (exception instanceof FirebaseNetworkException) {
            message = "Network error. Please check your internet connection.";
        } else if (exception != null && exception.getLocalizedMessage() != null) {
            message = exception.getLocalizedMessage();
        } else {
            message = "Login failed. Please check your credentials.";
        }

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void resetPassword() {
        String userEmail = email.getText().toString().trim();

        if (userEmail.isEmpty()) {
            email.setError("Enter your email first");
            email.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            email.setError("Enter a valid email address");
            email.requestFocus();
            return;
        }

        setLoading(true);

        auth.sendPasswordResetEmail(userEmail)
                .addOnCompleteListener(task -> {
                    setLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(
                                this,
                                "Password reset email sent. Check your inbox.",
                                Toast.LENGTH_LONG
                        ).show();
                    } else {
                        Exception ex = task.getException();
                        String msg = "Unable to send reset email";
                        if (ex instanceof FirebaseAuthInvalidUserException) {
                            msg = "No account registered with this email.";
                        } else if (ex instanceof FirebaseNetworkException) {
                            msg = "Network error. Check your internet connection.";
                        } else if (ex != null && ex.getLocalizedMessage() != null) {
                            msg = ex.getLocalizedMessage();
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void navigateToHome() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}