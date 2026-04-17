package cics.csup.qrattendancecontrol;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ImageButton backButton = findViewById(R.id.aboutBackButton);
        TextView versionValue = findViewById(R.id.aboutVersionValue);
        TextView changelogValue = findViewById(R.id.aboutChangelogValue);

        backButton.setOnClickListener(v -> finish());

        String versionText = getString(
                R.string.about_version_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE
        );
        versionValue.setText(versionText);
        changelogValue.setText(getString(R.string.about_changelog_content));
    }
}

