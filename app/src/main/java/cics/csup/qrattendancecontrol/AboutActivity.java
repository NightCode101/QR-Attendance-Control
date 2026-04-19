package cics.csup.qrattendancecontrol;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;

public class AboutActivity extends AppCompatActivity {

    private static final String DEBUG_TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110";

    private ConfigHelper configHelper;
    private FrameLayout nativeAdContainer;
    private NativeAd nativeAd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView pageTitle = findViewById(R.id.aboutTitle);
        TextView creditsTitle = findViewById(R.id.aboutCreditsTitle);
        TextView versionTitle = findViewById(R.id.aboutVersionTitle);
        TextView lastUpdatedTitle = findViewById(R.id.aboutLastUpdatedTitle);
        TextView changelogTitle = findViewById(R.id.aboutChangelogTitle);
        TextView versionValue = findViewById(R.id.aboutVersionValue);
        TextView lastUpdatedValue = findViewById(R.id.aboutLastUpdatedValue);
        TextView creditsValue = findViewById(R.id.aboutCreditsValue);
        TextView changelogValue = findViewById(R.id.aboutChangelogValue);
        nativeAdContainer = findViewById(R.id.aboutNativeAdContainer);


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

        loadNativeAdvancedAd();
    }

    private void loadNativeAdvancedAd() {
        if (nativeAdContainer == null) {
            return;
        }

        String adUnitId = BuildConfig.DEBUG
                ? DEBUG_TEST_NATIVE_AD_UNIT_ID
                : getString(R.string.admob_native_advanced);

        AdLoader adLoader = new AdLoader.Builder(this, adUnitId)
                .forNativeAd(ad -> {
                    if (isFinishing() || isDestroyed()) {
                        ad.destroy();
                        return;
                    }

                    if (nativeAd != null) {
                        nativeAd.destroy();
                    }
                    nativeAd = ad;

                    NativeAdView adView = (NativeAdView) LayoutInflater.from(this)
                            .inflate(R.layout.item_native_advanced_ad, nativeAdContainer, false);
                    populateNativeAdView(ad, adView);

                    nativeAdContainer.removeAllViews();
                    nativeAdContainer.addView(adView);
                    nativeAdContainer.setVisibility(View.VISIBLE);
                })
                .withAdListener(new AdListener() {
                    @Override
                    public void onAdFailedToLoad(LoadAdError adError) {
                        nativeAdContainer.setVisibility(View.GONE);
                    }
                })
                .build();

        adLoader.loadAd(new AdRequest.Builder().build());
    }

    private void populateNativeAdView(NativeAd ad, NativeAdView adView) {
        TextView headlineView = adView.findViewById(R.id.nativeAdHeadline);
        TextView bodyView = adView.findViewById(R.id.nativeAdBody);
        TextView advertiserView = adView.findViewById(R.id.nativeAdAdvertiser);
        ImageView iconView = adView.findViewById(R.id.nativeAdIcon);
        Button ctaView = adView.findViewById(R.id.nativeAdCallToAction);
        MediaView mediaView = adView.findViewById(R.id.nativeAdMedia);

        adView.setHeadlineView(headlineView);
        adView.setBodyView(bodyView);
        adView.setAdvertiserView(advertiserView);
        adView.setIconView(iconView);
        adView.setCallToActionView(ctaView);
        adView.setMediaView(mediaView);

        headlineView.setText(ad.getHeadline());

        if (ad.getBody() != null) {
            bodyView.setVisibility(View.VISIBLE);
            bodyView.setText(ad.getBody());
        } else {
            bodyView.setVisibility(View.GONE);
        }

        if (ad.getAdvertiser() != null) {
            advertiserView.setVisibility(View.VISIBLE);
            advertiserView.setText(ad.getAdvertiser());
        } else {
            advertiserView.setVisibility(View.GONE);
        }

        if (ad.getIcon() != null) {
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageDrawable(ad.getIcon().getDrawable());
        } else {
            iconView.setVisibility(View.GONE);
        }

        if (ad.getCallToAction() != null) {
            ctaView.setVisibility(View.VISIBLE);
            ctaView.setText(ad.getCallToAction());
        } else {
            ctaView.setVisibility(View.GONE);
        }

        adView.setNativeAd(ad);
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

    @Override
    protected void onDestroy() {
        if (nativeAd != null) {
            nativeAd.destroy();
            nativeAd = null;
        }
        super.onDestroy();
    }
}
