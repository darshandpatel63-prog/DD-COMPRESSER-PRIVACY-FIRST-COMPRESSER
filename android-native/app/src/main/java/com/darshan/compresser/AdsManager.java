package com.darshan.compresser;

import android.app.Activity;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Color;
import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/** Optional ads layer. Failures here must never affect file compression. */
final class AdsManager {
    private final Activity activity;
    private final LinearLayout topSlot, bottomSlot;
    private final Runnable onReward;
    private ConsentInformation consent;
    private AdView topBanner, bottomBanner;
    private InterstitialAd interstitial;
    private RewardedAd rewarded;
    private long adFreeUntil;

    private static final String APP_BANNER = "ca-app-pub-9557757991977213/8928348733";
    private static final String APP_INTERSTITIAL = "ca-app-pub-9557757991977213/9510802662";
    private static final String APP_REWARDED = "ca-app-pub-9557757991977213/1993993468";
    @SuppressWarnings("unused")
    private static final String APP_REWARDED_INTERSTITIAL = "ca-app-pub-9557757991977213/5180675414";
    @SuppressWarnings("unused")
    private static final String APP_NATIVE = "ca-app-pub-9557757991977213/5013329464";

    AdsManager(Activity activity, LinearLayout topSlot, LinearLayout bottomSlot, Runnable onReward) {
        this.activity = activity;
        this.topSlot = topSlot;
        this.bottomSlot = bottomSlot;
        this.onReward = onReward;
    }

    private String unit(String live, String test) { return BuildConfig.DEBUG ? test : live; }
    boolean isAdFree() { return System.currentTimeMillis() < adFreeUntil; }

    void start() {
        try {
            consent = UserMessagingPlatform.getConsentInformation(activity);
            ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
            consent.requestConsentInfoUpdate(activity, params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity, error -> {
                    if (consent.canRequestAds()) initialize();
                }),
                error -> { if (consent.canRequestAds()) initialize(); });
            if (consent.canRequestAds()) initialize();
        } catch (Exception ignored) { }
    }

    private void initialize() {
        try {
            MobileAds.initialize(activity, status -> activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed() || isAdFree()) return;
                loadBanner(true);
                loadBanner(false);
                loadInterstitial();
                loadRewarded();
            }));
        } catch (Exception ignored) { }
    }

    private void loadBanner(boolean top) {
        try {
            if (isAdFree() || activity.isFinishing() || activity.isDestroyed()) return;
            LinearLayout slot = top ? topSlot : bottomSlot;
            AdView old = top ? topBanner : bottomBanner;
            if (old != null) old.destroy();
            slot.removeAllViews();
            AdView view = new AdView(activity);
            view.setAdUnitId(unit(APP_BANNER, "ca-app-pub-3940256099942544/9214589741"));
            int width = Math.max(1, (int)(activity.getResources().getDisplayMetrics().widthPixels /
                    activity.getResources().getDisplayMetrics().density) - 32);
            view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, width));
            slot.setGravity(Gravity.CENTER);
            slot.addView(view);
            if (top) topBanner = view; else bottomBanner = view;
            view.loadAd(new AdRequest.Builder().build());
        } catch (Exception ignored) { }
    }

    private void loadInterstitial() {
        try {
            if (isAdFree() || activity.isFinishing() || activity.isDestroyed()) return;
            InterstitialAd.load(activity, unit(APP_INTERSTITIAL, "ca-app-pub-3940256099942544/1033173712"),
                new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(InterstitialAd ad) { interstitial = ad; }
                    @Override public void onAdFailedToLoad(LoadAdError error) { interstitial = null; }
                });
        } catch (Exception ignored) { interstitial = null; }
    }

    void showInterstitialAtBatchEnd(int count) {
        if (count < 7 || isAdFree() || interstitial == null || activity.isFinishing() || activity.isDestroyed()) return;
        InterstitialAd ad = interstitial;
        interstitial = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { loadInterstitial(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { loadInterstitial(); }
        });
        try { ad.show(activity); } catch (Exception ignored) { loadInterstitial(); }
    }

    private void loadRewarded() {
        try {
            if (isAdFree() || activity.isFinishing() || activity.isDestroyed()) return;
            RewardedAd.load(activity, unit(APP_REWARDED, "ca-app-pub-3940256099942544/5224354917"),
                new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(RewardedAd ad) { rewarded = ad; }
                    @Override public void onAdFailedToLoad(LoadAdError error) { rewarded = null; }
                });
        } catch (Exception ignored) { rewarded = null; }
    }

    void showRewarded() {
        if (isAdFree()) return;
        if (rewarded == null) { loadRewarded(); return; }
        RewardedAd ad = rewarded;
        rewarded = null;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() { loadRewarded(); }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) { loadRewarded(); }
        });
        try {
            ad.show(activity, reward -> {
                adFreeUntil = System.currentTimeMillis() + 30L * 60L * 1000L;
                activity.runOnUiThread(() -> {
                    removeBanners();
                    if (onReward != null) onReward.run();
                });
            });
        } catch (Exception ignored) { loadRewarded(); }
    }

    private void removeBanners() {
        if (topBanner != null) { topSlot.removeView(topBanner); topBanner.destroy(); topBanner = null; }
        if (bottomBanner != null) { bottomSlot.removeView(bottomBanner); bottomBanner.destroy(); bottomBanner = null; }
    }

    void destroy() {
        removeBanners();
        interstitial = null;
        rewarded = null;
    }
}
