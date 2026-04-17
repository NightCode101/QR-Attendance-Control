package cics.csup.qrattendancecontrol;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    private ConfigHelper configHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        ImageButton backButton = findViewById(R.id.aboutBackButton);
        TextView pageTitle = findViewById(R.id.aboutTitle);
        TextView creditsTitle = findViewById(R.id.aboutCreditsTitle);
        TextView versionTitle = findViewById(R.id.aboutVersionTitle);
        TextView lastUpdatedTitle = findViewById(R.id.aboutLastUpdatedTitle);
        TextView changelogTitle = findViewById(R.id.aboutChangelogTitle);
        TextView versionValue = findViewById(R.id.aboutVersionValue);
        TextView lastUpdatedValue = findViewById(R.id.aboutLastUpdatedValue);
        TextView creditsValue = findViewById(R.id.aboutCreditsValue);
        TextView changelogValue = findViewById(R.id.aboutChangelogValue);

        backButton.setOnClickListener(v -> finish());

        versionValue.setText(getAppVersionText());

        // Show local fallback first, then replace with Remote Config content when available.
        pageTitle.setText(getString(R.string.about_title));
        creditsTitle.setText(getString(R.string.about_credits_title));
        versionTitle.setText(getString(R.string.about_version_title));
        lastUpdatedTitle.setText(getString(R.string.about_last_updated_title));
        changelogTitle.setText(getString(R.string.about_changelog_title));
        creditsValue.setText(getString(R.string.about_credits_content));
        lastUpdatedValue.setText(getBuildLastUpdatedFallback());
        changelogValue.setText(getString(R.string.about_changelog_content));

        configHelper = new ConfigHelper();
        configHelper.fetchAndActivate(this, () -> {
            if (isFinishing()) {
                return;
            }

            String remotePageTitle = configHelper.getAboutTitle();
            if (!remotePageTitle.isEmpty()) {
                pageTitle.setText(remotePageTitle.replace("\\n", "\n"));
            }

            String remoteCreditsTitle = configHelper.getAboutCreditsTitle();
            if (!remoteCreditsTitle.isEmpty()) {
                creditsTitle.setText(remoteCreditsTitle.replace("\\n", "\n"));
            }

            String remoteVersionTitle = configHelper.getAboutVersionTitle();
            if (!remoteVersionTitle.isEmpty()) {
                versionTitle.setText(remoteVersionTitle.replace("\\n", "\n"));
            }

            String remoteLastUpdatedTitle = configHelper.getAboutLastUpdatedTitle();
            if (!remoteLastUpdatedTitle.isEmpty()) {
                lastUpdatedTitle.setText(remoteLastUpdatedTitle.replace("\\n", "\n"));
            }

            String remoteChangelogTitle = configHelper.getAboutChangelogTitle();
            if (!remoteChangelogTitle.isEmpty()) {
                changelogTitle.setText(remoteChangelogTitle.replace("\\n", "\n"));
            }

            String remoteCredits = configHelper.getAboutCredits();
            if (!remoteCredits.isEmpty()) {
                creditsValue.setText(remoteCredits.replace("\\n", "\n"));
            }

            String remoteLastUpdated = configHelper.getAboutLastUpdated();
            if (!remoteLastUpdated.isEmpty()) {
                lastUpdatedValue.setText(remoteLastUpdated.replace("\\n", "\n"));
            }

            String remoteChangelog = configHelper.getAboutChangelog();
            if (!remoteChangelog.isEmpty()) {
                changelogValue.setText(remoteChangelog.replace("\\n", "\n"));
            }
        });
    }

    private String getAppVersionText() {
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String versionName = packageInfo.versionName != null ? packageInfo.versionName : "-";
            long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
            return getString(R.string.about_version_format, versionName, versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            return getString(R.string.about_version_fallback);
        }
    }

    private String getBuildLastUpdatedFallback() {
        String versionName;
        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = packageInfo.versionName != null ? packageInfo.versionName : "-";
        } catch (PackageManager.NameNotFoundException e) {
            versionName = "-";
        }

        return getString(
                R.string.about_last_updated_fallback_format,
                versionName,
                getString(R.string.build_date)
        );
    }
}
