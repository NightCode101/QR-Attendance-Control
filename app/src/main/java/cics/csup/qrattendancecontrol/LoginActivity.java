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
    protected void onCreate(Bundle savedInstanceState) { // Use your app's valid theme here
        getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        getWindow().setStatusBarColor(Color.parseColor("#121212"));

        // Make status bar and nav bar icons light (only works on Android 6.0+)
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(0); // Clears flags like LIGHT_STATUS_BAR

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        loginButton = findViewById(R.id.loginButton);
        rememberCheckBox = findViewById(R.id.rememberCheckBox);

        // Load saved login
        String savedEmail = prefs.getString("email", null);
        String savedPass = prefs.getString("password", null);
        if (savedEmail != null && savedPass != null) {
            emailEditText.setText(savedEmail);
            passwordEditText.setText(savedPass);
            rememberCheckBox.setChecked(true);
        }

        loginButton.setOnClickListener(v -> {
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
                            if (rememberCheckBox.isChecked()) {
                                prefs.edit()
                                        .putString("email", email)
                                        .putString("password", password)
                                        .apply();
                            } else {
                                prefs.edit().clear().apply();
                            }

                            String target = getIntent().getStringExtra("target");
                            if ("admin".equals(target)) {
                                if (ADMIN_UIDS.contains(user.getUid())) {
                                    Intent intent = new Intent(LoginActivity.this, AdminActivity.class);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Toast.makeText(this, "Access denied. Not an admin.", Toast.LENGTH_LONG).show();
                                }
                            } else {
                                Toast.makeText(this, "Login successful.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
        });
    }
}
