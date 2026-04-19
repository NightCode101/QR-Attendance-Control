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

import java.lang.ref.WeakReference;

public class AdMobApplication extends Application implements Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {

    private static final String DEBUG_TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921";

    private final AppOpenAdManager appOpenAdManager = new AppOpenAdManager();
    private WeakReference<Activity> currentActivityRef;

    @Override
    public void onCreate() {
        super.onCreate();
        MobileAds.initialize(this, initializationStatus -> {});
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

    private class AppOpenAdManager {
        private AppOpenAd appOpenAd;
        private boolean isLoadingAd;
        private boolean isShowingAd;

        void loadAd() {
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
            if (activity == null || isShowingAd) {
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

