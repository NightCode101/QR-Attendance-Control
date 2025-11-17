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

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class LoginActivity extends AppCompatActivity {

    // 1. IMPROVEMENT NOTE: Hardcoding UIDs is inflexible.
    // For a future update, consider moving this list to a document in
    // Firestore (e.g., /config/admins) to add/remove admins without
    // releasing a new app version.
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
        // --- UI & Theme Setup ---
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
            // User has an active session and wanted to be remembered
            if (ADMIN_UIDS.contains(currentUser.getUid())) {
                startActivity(new Intent(LoginActivity.this, AdminActivity.class));
                finish();
                return; // Skip the rest of onCreate
            } else {
                // This user is not an admin, log them out
                Toast.makeText(this, "Access denied. Not an admin.", Toast.LENGTH_LONG).show();
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
            Toast.makeText(this, "Enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        // 2. SECURITY NOTE: Storing passwords in SharedPreferences is
                        // a security risk. For this "Remember Me" autofill, it's a
                        // common trade-off, but never do this for sensitive data.

                        SharedPreferences.Editor editor = prefs.edit();
                        if (rememberCheckBox.isChecked()) {
                            editor.putString("email", email);
                            editor.putString("password", password);
                            editor.putBoolean("remember_me", true);
                        } else {
                            // Clear only the credentials, keep the "remember_me" false
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
                            Toast.makeText(this, "Access denied. Not an admin.", Toast.LENGTH_LONG).show();
                            mAuth.signOut();
                        }
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                );
    }
}