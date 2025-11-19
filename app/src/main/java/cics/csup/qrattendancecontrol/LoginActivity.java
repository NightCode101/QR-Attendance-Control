package cics.csup.qrattendancecontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar; // ADDED: For Snackbar
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LoginActivity extends AppCompatActivity {

    private static final Set<String> ADMIN_UIDS = new HashSet<>(Arrays.asList(
            "KCKVGF5sJ7TfGWKAl0fRJziE4Ja2",
            "NFs38qPJAXXZFspS37nRhteROWn1"
    ));
    private static final String PREFS_NAME = "LoginPrefs";
    private EditText emailEditText, passwordEditText;
    private Button loginButton;
    private CheckBox rememberCheckBox;
    private FirebaseAuth mAuth;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // --- UI & Theme Setup (Kept for visual consistency) ---
        getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        getWindow().setStatusBarColor(Color.parseColor("#121212"));
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(0);
        // --- End UI Setup ---

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        rememberCheckBox = findViewById(R.id.rememberCheckBox);

        // 🔹 Check if user is already logged in from a previous session
        FirebaseUser currentUser = mAuth.getCurrentUser();
        boolean rememberMe = prefs.getBoolean("remember_me", false);

        if (currentUser != null && rememberMe) {
            if (ADMIN_UIDS.contains(currentUser.getUid())) {
                startActivity(new Intent(LoginActivity.this, AdminActivity.class));
                finish();
                return;
            } else {
                // CHANGED: Use Snackbar
                showSnackbar("Access denied. Not an admin.");
                mAuth.signOut();
            }
        }

        // 🔹 Load saved email/password for autofill
        if (prefs.getBoolean("remember_me", false)) {
            emailEditText.setText(prefs.getString("email", ""));
            passwordEditText.setText(prefs.getString("password", ""));
            rememberCheckBox.setChecked(true);
        }

        loginButton.setOnClickListener(v -> {
            loginUser();
        });
    }

    private void loginUser() {
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            // CHANGED: Use Snackbar
            showSnackbar("Enter email and password");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        SharedPreferences.Editor editor = prefs.edit();

                        if (rememberCheckBox.isChecked()) {
                            editor.putString("email", email);
                            editor.putString("password", password);
                            editor.putBoolean("remember_me", true);
                        } else {
                            editor.remove("email");
                            editor.remove("password");
                            editor.putBoolean("remember_me", false);
                        }
                        editor.apply();

                        if (ADMIN_UIDS.contains(user.getUid())) {
                            Intent intent = new Intent(LoginActivity.this, AdminActivity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            // CHANGED: Use Snackbar
                            showSnackbar("Access denied. Not an admin.");
                            mAuth.signOut();
                        }
                    }
                })
                .addOnFailureListener(e ->
                        // CHANGED: Use Snackbar
                        showSnackbar("Login failed: " + e.getMessage())
                );
    }

    /**
     * Displays a non-stacking Snackbar message.
     */
    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView != null) {
            Snackbar.make(rootView, message, Snackbar.LENGTH_LONG).show();
        } else {
            // Fallback
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}