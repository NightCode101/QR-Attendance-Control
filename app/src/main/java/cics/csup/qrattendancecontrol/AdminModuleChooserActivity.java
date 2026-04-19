package cics.csup.qrattendancecontrol;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.gms.ads.AdView;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;

public class AdminModuleChooserActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "LoginPrefs";
    private AdView bannerAdView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_module_chooser);

        bannerAdView = findViewById(R.id.bannerAdView);

        MaterialButton attendanceButton = findViewById(R.id.openAttendanceButton);
        MaterialButton accessCodesButton = findViewById(R.id.openAccessCodesButton);
        MaterialButton logoutButton = findViewById(R.id.logoutButton);

        attendanceButton.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminActivity.class));
            finish();
        });

        accessCodesButton.setOnClickListener(v -> {
            startActivity(new Intent(this, AdminAccessCodeActivity.class));
            finish();
        });

        logoutButton.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean("remember_me", false).apply();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bannerAdView != null) {
            bannerAdView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (bannerAdView != null) {
            bannerAdView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bannerAdView != null) {
            bannerAdView.destroy();
        }
    }
}

