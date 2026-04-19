package cics.csup.qrattendancecontrol;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.FormError;
import com.google.android.ump.UserMessagingPlatform;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

public class AdMobApplication extends Application implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private static final String DEBUG_TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921";

    private final AppOpenAdManager appOpenAdManager = new AppOpenAdManager();
    private WeakReference<Activity> currentActivityRef;
    private ConsentInformation consentInformation;
    private final AtomicBoolean mobileAdsInitialized = new AtomicBoolean(false);
    private boolean canRequestAds = false;
    private boolean consentInfoUpdateRequested = false;
    private boolean consentFormFlowStarted = false;
    private boolean consentFormFlowFinished = false;

    @Override
    public void onCreate() {
        super.onCreate();
        consentInformation = UserMessagingPlatform.getConsentInformation(this);

        // Respect previously stored consent while waiting for fresh update from UMP.
        canRequestAds = consentInformation.canRequestAds();
        maybeInitializeMobileAds();

        registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        Activity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        appOpenAdManager.showAdIfAvailable(activity);
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        loadBannerIfPresent(activity);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
        requestConsentInfo(activity);
        maybeRunConsentForm(activity);
        maybeInitializeMobileAds();
        loadBannerIfPresent(activity);
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivityRef = new WeakReference<>(activity);
        AdView bannerAdView = activity.findViewById(R.id.bannerAdView);
        if (bannerAdView != null) {
            bannerAdView.resume();
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        AdView bannerAdView = activity.findViewById(R.id.bannerAdView);
        if (bannerAdView != null) {
            bannerAdView.pause();
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) { }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) { }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        Activity current = currentActivityRef != null ? currentActivityRef.get() : null;
        if (current == activity) {
            currentActivityRef = null;
        }

        AdView bannerAdView = activity.findViewById(R.id.bannerAdView);
        if (bannerAdView != null) {
            bannerAdView.destroy();
        }
    }

    private void loadBannerIfPresent(@NonNull Activity activity) {
        if (!canRequestAds || !mobileAdsInitialized.get()) {
            return;
        }

        AdView bannerAdView = activity.findViewById(R.id.bannerAdView);
        if (bannerAdView == null) {
            return;
        }
        if (Boolean.TRUE.equals(bannerAdView.getTag())) {
            return;
        }

        bannerAdView.setTag(Boolean.TRUE);
        bannerAdView.loadAd(new AdRequest.Builder().build());
    }

    private void requestConsentInfo(@NonNull Activity activity) {
        if (consentInfoUpdateRequested) {
            return;
        }
        consentInfoUpdateRequested = true;

        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();

        consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                () -> {
                    canRequestAds = consentInformation.canRequestAds();
                    maybeInitializeMobileAds();

                    Activity currentActivity = currentActivityRef != null ? currentActivityRef.get() : null;
                    if (currentActivity != null) {
                        maybeRunConsentForm(currentActivity);
                        loadBannerIfPresent(currentActivity);
                    }
                },
                requestConsentError -> {
                    canRequestAds = consentInformation.canRequestAds();
                    maybeInitializeMobileAds();
                }
        );
    }

    private void maybeRunConsentForm(@Nullable Activity activity) {
        if (activity == null || consentFormFlowStarted || consentFormFlowFinished) {
            return;
        }
        if (!consentInformation.isConsentFormAvailable()) {
            consentFormFlowFinished = true;
            return;
        }

        consentFormFlowStarted = true;
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, this::onConsentFormDismissed);
    }

    private void onConsentFormDismissed(@Nullable FormError formError) {
        consentFormFlowFinished = true;
        canRequestAds = consentInformation.canRequestAds();
        maybeInitializeMobileAds();

        Activity activity = currentActivityRef != null ? currentActivityRef.get() : null;
        if (activity != null) {
            loadBannerIfPresent(activity);
        }
    }

    private void maybeInitializeMobileAds() {
        if (!canRequestAds || !mobileAdsInitialized.compareAndSet(false, true)) {
            return;
        }

        MobileAds.initialize(this, initializationStatus -> {
            appOpenAdManager.loadAd();
        });
    }

    private class AppOpenAdManager {
        private AppOpenAd appOpenAd;
        private boolean isLoadingAd;
        private boolean isShowingAd;

        void loadAd() {
            if (!canRequestAds || !mobileAdsInitialized.get()) {
                return;
            }
            if (isLoadingAd || appOpenAd != null) {
                return;
            }
            isLoadingAd = true;

            String adUnitId = BuildConfig.DEBUG
                    ? DEBUG_TEST_APP_OPEN_AD_UNIT_ID
                    : getString(R.string.admob_app_open);

            AdRequest request = new AdRequest.Builder().build();
            AppOpenAd.load(
                    AdMobApplication.this,
                    adUnitId,
                    request,
                    new AppOpenAd.AppOpenAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull AppOpenAd ad) {
                            appOpenAd = ad;
                            isLoadingAd = false;
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            isLoadingAd = false;
                        }
                    }
            );
        }

        void showAdIfAvailable(@Nullable Activity activity) {
            if (activity == null || isShowingAd || !canRequestAds || !mobileAdsInitialized.get()) {
                return;
            }

            if (activity instanceof CustomScanActivity || activity instanceof RFIDScanActivity || activity instanceof GraphActivity) {
                return;
            }

            if (appOpenAd == null) {
                loadAd();
                return;
            }

            appOpenAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    isShowingAd = true;
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    appOpenAd = null;
                    isShowingAd = false;
                    loadAd();
                }

                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull com.google.android.gms.ads.AdError adError) {
                    appOpenAd = null;
                    isShowingAd = false;
                    loadAd();
                }
            });

            appOpenAd.show(activity);
        }
    }
}

