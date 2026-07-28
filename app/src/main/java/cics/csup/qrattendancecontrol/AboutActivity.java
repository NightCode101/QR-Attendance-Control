package cics.csup.qrattendancecontrol;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

public class AboutActivity extends AppCompatActivity {

    private static final String DEBUG_TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110";

    private ConfigHelper configHelper;
    private FrameLayout nativeAdContainer;
    private NativeAd nativeAd;
    private View privacySection;
    private Button privacyButton;
    private ConsentInformation consentInformation;

    // UI Components
    private TextView devName, changelogValue, testersValue, versionValue, lastUpdatedValue;
    private ImageView devPhoto, changelogArrow, testersArrow;
    private View changelogCard, testersCard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);
        if (topAppBar != null) {
            setSupportActionBar(topAppBar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            topAppBar.setNavigationOnClickListener(v -> finish());
        }

        // Bind Views
        devName = findViewById(R.id.devName);
        devPhoto = findViewById(R.id.devPhoto);
        changelogValue = findViewById(R.id.changelogValue);
        testersValue = findViewById(R.id.testersValue);
        versionValue = findViewById(R.id.versionValue);
        lastUpdatedValue = findViewById(R.id.lastUpdatedValue);

        changelogCard = findViewById(R.id.changelogCard);
        testersCard = findViewById(R.id.testersCard);
        changelogArrow = findViewById(R.id.changelogArrow);
        testersArrow = findViewById(R.id.testersArrow);
        
        nativeAdContainer = findViewById(R.id.aboutNativeAdContainer);
        privacySection = findViewById(R.id.aboutPrivacySection);
        privacyButton = findViewById(R.id.aboutPrivacyButton);
        
        consentInformation = UserMessagingPlatform.getConsentInformation(this);

        privacyButton.setOnClickListener(v -> showPrivacyOptionsFormIfAvailable());
        updatePrivacyOptionsVisibility();

        setupExpandableSection(changelogCard, changelogValue, changelogArrow);
        setupExpandableSection(testersCard, testersValue, testersArrow);

        // Initial Data Binding (Local Fallbacks)
        if (topAppBar != null) topAppBar.setTitle(getString(R.string.about_title));
        devName.setText(getString(R.string.about_developer_name_default));
        changelogValue.setText(getString(R.string.about_changelog_default));
        testersValue.setText(getString(R.string.about_testers_default));
        versionValue.setText(getAppVersionText());
        lastUpdatedValue.setText(getBuildLastUpdatedFallback());

        // Load Developer Photo (Local Asset)
        devPhoto.setImageResource(R.drawable.dev);

        configHelper = new ConfigHelper();
        configHelper.fetchAndActivate(this, () -> {
            if (isFinishing()) return;

            // Page Title
            String remotePageTitle = configHelper.getAboutTitle();
            if (!remotePageTitle.isEmpty() && topAppBar != null) {
                topAppBar.setTitle(sanitize(remotePageTitle));
            }

            // Developer Name
            String remoteDevName = configHelper.getAboutDeveloperName();
            if (!remoteDevName.isEmpty()) devName.setText(sanitize(remoteDevName));

            // Changelog
            String remoteChangelog = configHelper.getAboutChangelog();
            if (!remoteChangelog.isEmpty()) changelogValue.setText(sanitize(remoteChangelog));

            // Testers
            String remoteTesters = configHelper.getAboutTesters();
            if (!remoteTesters.isEmpty()) testersValue.setText(sanitize(remoteTesters));

            // Last Updated
            String remoteLastUpdated = configHelper.getAboutLastUpdated();
            if (!remoteLastUpdated.isEmpty()) lastUpdatedValue.setText(sanitize(remoteLastUpdated));
        });

        loadNativeAdvancedAd();
    }

    private void setupExpandableSection(View card, TextView value, ImageView arrow) {
        card.setOnClickListener(v -> {
            boolean isExpanded = value.getMaxLines() == Integer.MAX_VALUE;
            if (isExpanded) {
                value.setMaxLines(3);
                arrow.setImageResource(R.drawable.ic_expand_more);
            } else {
                value.setMaxLines(Integer.MAX_VALUE);
                arrow.setImageResource(R.drawable.ic_expand_less);
            }
        });
    }

    private String sanitize(String raw) {
        if (raw == null) return "";
        return raw.replace("\\n", "\n").replace("\\r", "\r");
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePrivacyOptionsVisibility();
    }

    private void updatePrivacyOptionsVisibility() {
        if (privacySection == null || consentInformation == null) {
            return;
        }

        boolean shouldShow = consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
        privacySection.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    private void showPrivacyOptionsFormIfAvailable() {
        if (consentInformation == null) {
            Toast.makeText(this, R.string.privacy_options_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        if (consentInformation.getPrivacyOptionsRequirementStatus()
                != ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Toast.makeText(this, R.string.privacy_options_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        UserMessagingPlatform.showPrivacyOptionsForm(this, this::onPrivacyOptionsDismissed);
    }

    private void onPrivacyOptionsDismissed(FormError formError) {
        if (formError != null) {
            Toast.makeText(this, R.string.privacy_options_update_failed, Toast.LENGTH_SHORT).show();
        }
        updatePrivacyOptionsVisibility();
        loadNativeAdvancedAd();
    }

    private void loadNativeAdvancedAd() {
        if (nativeAdContainer == null) return;
        if (!UserMessagingPlatform.getConsentInformation(this).canRequestAds()) {
            nativeAdContainer.setVisibility(View.GONE);
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
                    if (nativeAd != null) nativeAd.destroy();
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
        } else bodyView.setVisibility(View.GONE);

        if (ad.getAdvertiser() != null) {
            advertiserView.setVisibility(View.VISIBLE);
            advertiserView.setText(ad.getAdvertiser());
        } else advertiserView.setVisibility(View.GONE);

        if (ad.getIcon() != null) {
            iconView.setVisibility(View.VISIBLE);
            iconView.setImageDrawable(ad.getIcon().getDrawable());
        } else iconView.setVisibility(View.GONE);

        if (ad.getCallToAction() != null) {
            ctaView.setVisibility(View.VISIBLE);
            ctaView.setText(ad.getCallToAction());
        } else ctaView.setVisibility(View.GONE);

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

        return getString(R.string.about_last_updated_fallback_format, versionName, getString(R.string.build_date));
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
