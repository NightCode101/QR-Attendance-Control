package cics.csup.qrattendancecontrol;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.WindowCompat;

import com.google.android.material.snackbar.Snackbar;

public class AccessCodeActivity extends AppCompatActivity {

    private EditText accessCodeEditText;
    private Button activateButton;
    private TextView adminLoginLink;
    private ProgressBar progressBar;
    private AccessControlManager accessControlManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_access_code);

        accessControlManager = new AccessControlManager(this);

        accessCodeEditText = findViewById(R.id.accessCodeEditText);
        activateButton = findViewById(R.id.activateButton);
        adminLoginLink = findViewById(R.id.adminLoginLink);
        progressBar = findViewById(R.id.accessCodeProgress);

        if (accessControlManager.hasCachedAccess()) {
            openMainActivity();
            return;
        }

        activateButton.setOnClickListener(v -> activateAccessCode());
        adminLoginLink.setOnClickListener(v -> {
            startActivity(new Intent(AccessCodeActivity.this, LoginActivity.class));
        });
    }

    private void activateAccessCode() {
        String rawCode = accessCodeEditText.getText() != null ? accessCodeEditText.getText().toString() : "";
        if (TextUtils.isEmpty(rawCode.trim())) {
            showSnackbar("Please enter your access code.");
            return;
        }

        setLoading(true);
        accessControlManager.activateCode(rawCode, (success, message) -> runOnUiThread(() -> {
            setLoading(false);
            if (success) {
                showSnackbar("Access granted. Welcome!");
                openMainActivity();
                return;
            }
            showSnackbar(message);
        }));
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        activateButton.setEnabled(!isLoading);
        accessCodeEditText.setEnabled(!isLoading);
    }

    private void openMainActivity() {
        Intent intent = new Intent(AccessCodeActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showSnackbar(String message) {
        View rootView = findViewById(android.R.id.content);
        if (rootView == null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            return;
        }

        Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setBackgroundTint(getColor(R.color.md_theme_secondary))
                .setTextColor(getColor(R.color.white))
                .show();
    }
}

