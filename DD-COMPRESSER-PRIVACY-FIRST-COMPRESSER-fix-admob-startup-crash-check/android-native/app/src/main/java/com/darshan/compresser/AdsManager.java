package com.darshan.compresser;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

/**
 * Optional ads layer (AdMob + Google UMP consent).
 *
 * Rules that keep the app safe (details: ADMOB_INTEGRATION.md):
 *  1. Nothing from the ads SDK runs before the first screen is drawn (AdsConfig.START_DELAY_MS).
 *  2. Every entry point catches Throwable. Ads may fail; compression and the UI must never notice.
 *  3. Order is always: consent (UMP) -> MobileAds.initialize on a worker thread -> load ads.
 *  4. Repeated failures pause ads automatically (AdsGuard) instead of crash-looping the app.
 *  5. Every ID and every "when to show" number lives in AdsConfig, not here.
 *
 * All fields are touched on the main thread only.
 */
final class AdsManager {
    private final Activity activity;
    private final Context appContext;
    private final LinearLayout topSlot, bottomSlot;
    private final Runnable onReward;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable adFreeExpired = this::onAdFreeExpired;

    private ConsentInformation consent;
    private AdView topBanner, bottomBanner;
    private InterstitialAd interstitial;
    private RewardedAd rewarded;

    private boolean enabled;          // AdsGuard allowed ads for this launch and nothing has failed yet
    private boolean destroyed;
    private boolean resumed;          // Activity is in the foreground (full-screen ads only show then)
    private boolean initStarted;
    private boolean sdkReady;
    private boolean interstitialLoading, rewardedLoading;
    private long adFreeUntil;         // SystemClock.elapsedRealtime() deadline, 0 = not ad-free

    AdsManager(Activity activity, LinearLayout topSlot, LinearLayout bottomSlot, Runnable onReward) {
        this.activity = activity;
        this.appContext = activity.getApplicationContext();
        this.topSlot = topSlot;
        this.bottomSlot = bottomSlot;
        this.onReward = onReward;
        // No ad yet => no empty gap on screen.
        topSlot.setVisibility(View.GONE);
        bottomSlot.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------
    // Public entry points (called from MainActivity)
    // ------------------------------------------------------------------

    /** Schedules the whole ads startup. Returns immediately; never throws. */
    void start() {
        runOnMain(() -> {
            try {
                if (!AdsConfig.ADS_ENABLED) return;
                enabled = AdsGuard.begin(appContext);
                String earlier = AdsGuard.takeNotice(appContext);
                if (earlier != null) notice("Ads were paused after a startup problem:\n" + earlier);
                if (!enabled) return;
                main.postDelayed(this::markStable, AdsGuard.STABLE_AFTER_MS);
                main.postDelayed(this::requestConsent, AdsConfig.START_DELAY_MS);
            } catch (Throwable t) {
                fail("start", t);
            }
        });
    }

    void pause() {
        runOnMain(() -> {
            resumed = false;
            try {
                if (topBanner != null) topBanner.pause();
                if (bottomBanner != null) bottomBanner.pause();
            } catch (Throwable ignored) { }
        });
    }

    void resume() {
        runOnMain(() -> {
            resumed = true;
            try {
                if (topBanner != null) topBanner.resume();
                if (bottomBanner != null) bottomBanner.resume();
            } catch (Throwable ignored) { }
        });
    }

    /**
     * Call when a compression batch finished. Shows an interstitial only for big batches, and only while
     * the user is actually looking at the app (compression can finish while the app is in the background).
     */
    void showInterstitialAtBatchEnd(int filesProcessed) {
        runOnMain(() -> {
            try {
                if (!usable() || !sdkReady || isAdFree() || !resumed) return;
                if (filesProcessed < AdsConfig.INTERSTITIAL_MIN_FILES) return;
                if (interstitial == null) { loadInterstitial(); return; }
                final InterstitialAd ad = interstitial;
                interstitial = null;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() { main.post(() -> loadInterstitial()); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError error) { main.post(() -> loadInterstitial()); }
                });
                ad.show(activity);
            } catch (Throwable t) {
                fail("interstitial show", t);
            }
        });
    }

    /** "Watch an ad" button: a finished rewarded ad = AdsConfig.AD_FREE_MS without banners/interstitials. */
    void showRewarded() {
        runOnMain(() -> {
            try {
                if (!usable() || !sdkReady) {
                    toast("Ads are not available right now. Please try again later.");
                    return;
                }
                if (isAdFree()) {
                    toast("Ad-free mode is already active.");
                    return;
                }
                if (rewarded == null) {
                    loadRewarded();
                    toast("The ad is still loading. Please try again in a few seconds.");
                    return;
                }
                final RewardedAd ad = rewarded;
                rewarded = null;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() { main.post(() -> loadRewarded()); }
                    @Override public void onAdFailedToShowFullScreenContent(AdError error) { main.post(() -> loadRewarded()); }
                });
                ad.show(activity, rewardItem -> main.post(this::grantAdFree));
            } catch (Throwable t) {
                fail("rewarded show", t);
            }
        });
    }

    void destroy() {
        runOnMain(() -> {
            destroyed = true;
            main.removeCallbacksAndMessages(null);
            try { removeBanners(); } catch (Throwable ignored) { }
            interstitial = null;
            rewarded = null;
            try { AdsGuard.closeWindow(appContext); } catch (Throwable ignored) { }
        });
    }

    // ------------------------------------------------------------------
    // Startup chain: consent -> initialize -> load
    // ------------------------------------------------------------------

    private void requestConsent() {
        if (!usable()) return;
        try {
            consent = UserMessagingPlatform.getConsentInformation(activity);
            consent.requestConsentInfoUpdate(
                    activity,
                    new ConsentRequestParameters.Builder().build(),
                    () -> main.post(this::showConsentFormIfRequired),
                    formError -> main.post(this::initializeIfAllowed));
            // Returning users already have a stored decision, so ads can start right away.
            initializeIfAllowed();
        } catch (Throwable t) {
            fail("consent", t);
        }
    }

    private void showConsentFormIfRequired() {
        if (!usable()) return;
        try {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity, formError -> main.post(this::initializeIfAllowed));
        } catch (Throwable t) {
            fail("consent form", t);
        }
    }

    private void initializeIfAllowed() {
        if (!usable() || initStarted) return;
        try {
            if (consent == null || !consent.canRequestAds()) return;
            initStarted = true;
            // Google recommends initializing off the main thread to avoid start-up stalls.
            Thread worker = new Thread(() -> {
                try {
                    MobileAds.initialize(appContext, status -> main.post(this::onSdkReady));
                } catch (Throwable error) {
                    main.post(() -> fail("MobileAds.initialize", error));
                }
            }, "dd-ads-init");
            worker.setDaemon(true);
            worker.start();
        } catch (Throwable t) {
            fail("initialize", t);
        }
    }

    private void onSdkReady() {
        if (!usable()) return;
        sdkReady = true;
        loadAll();
    }

    /** Loads every ad type that is missing. Does nothing while ad-free. */
    private void loadAll() {
        if (!usable() || !sdkReady || isAdFree()) return;
        if (AdsConfig.TOP_BANNER && topBanner == null) loadBanner(true);
        if (AdsConfig.BOTTOM_BANNER && bottomBanner == null) loadBanner(false);
        loadInterstitial();
        loadRewarded();
    }

    // ------------------------------------------------------------------
    // Banner
    // ------------------------------------------------------------------

    // getCurrentOrientationAnchoredAdaptiveBannerAdSize is deprecated since SDK 25.0.0 but still works.
    @SuppressWarnings("deprecation")
    private void loadBanner(boolean top) {
        if (!usable() || !sdkReady || isAdFree()) return;
        try {
            final LinearLayout slot = top ? topSlot : bottomSlot;
            AdView old = top ? topBanner : bottomBanner;
            if (old != null) { slot.removeView(old); old.destroy(); }
            slot.removeAllViews();

            final AdView view = new AdView(activity);
            view.setAdUnitId(AdsConfig.bannerId());
            // Screen width minus the 16dp + 16dp side padding of the main layout.
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int widthDp = Math.max(1, Math.round(dm.widthPixels / dm.density) - 32);
            view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            view.setAdListener(new AdListener() {
                @Override public void onAdLoaded() { slot.setVisibility(View.VISIBLE); }
                @Override public void onAdFailedToLoad(LoadAdError error) { slot.setVisibility(View.GONE); }
            });
            slot.setGravity(Gravity.CENTER);
            slot.setVisibility(View.VISIBLE);
            slot.addView(view);
            if (top) topBanner = view; else bottomBanner = view;
            view.loadAd(new AdRequest.Builder().build());
        } catch (Throwable t) {
            fail("banner", t);
        }
    }

    private void removeBanners() {
        if (topBanner != null) { topSlot.removeView(topBanner); topBanner.destroy(); topBanner = null; }
        if (bottomBanner != null) { bottomSlot.removeView(bottomBanner); bottomBanner.destroy(); bottomBanner = null; }
        topSlot.setVisibility(View.GONE);
        bottomSlot.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------
    // Interstitial + rewarded loading
    // ------------------------------------------------------------------

    private void loadInterstitial() {
        if (!usable() || !sdkReady || isAdFree() || interstitial != null || interstitialLoading) return;
        try {
            interstitialLoading = true;
            InterstitialAd.load(activity, AdsConfig.interstitialId(), new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override public void onAdLoaded(InterstitialAd ad) {
                            main.post(() -> { interstitialLoading = false; if (usable()) interstitial = ad; });
                        }
                        @Override public void onAdFailedToLoad(LoadAdError error) {
                            main.post(() -> { interstitialLoading = false; interstitial = null; });
                        }
                    });
        } catch (Throwable t) {
            interstitialLoading = false;
            fail("interstitial load", t);
        }
    }

    private void loadRewarded() {
        if (!usable() || !sdkReady || isAdFree() || rewarded != null || rewardedLoading) return;
        try {
            rewardedLoading = true;
            RewardedAd.load(activity, AdsConfig.rewardedId(), new AdRequest.Builder().build(),
                    new RewardedAdLoadCallback() {
                        @Override public void onAdLoaded(RewardedAd ad) {
                            main.post(() -> { rewardedLoading = false; if (usable()) rewarded = ad; });
                        }
                        @Override public void onAdFailedToLoad(LoadAdError error) {
                            main.post(() -> { rewardedLoading = false; rewarded = null; });
                        }
                    });
        } catch (Throwable t) {
            rewardedLoading = false;
            fail("rewarded load", t);
        }
    }

    // ------------------------------------------------------------------
    // Ad-free window (reward)
    // ------------------------------------------------------------------

    private boolean isAdFree() {
        return adFreeUntil != 0L && SystemClock.elapsedRealtime() < adFreeUntil;
    }

    private void grantAdFree() {
        if (destroyed) return;
        try {
            adFreeUntil = SystemClock.elapsedRealtime() + AdsConfig.AD_FREE_MS;
            main.removeCallbacks(adFreeExpired);
            main.postDelayed(adFreeExpired, AdsConfig.AD_FREE_MS);
            removeBanners();
            if (onReward != null) onReward.run();
        } catch (Throwable t) {
            fail("reward", t);
        }
    }

    private void onAdFreeExpired() {
        adFreeUntil = 0L;
        loadAll(); // banners come back, interstitial/rewarded are preloaded again
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private boolean usable() {
        return enabled && !destroyed && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void markStable() {
        try { AdsGuard.markStable(appContext); } catch (Throwable ignored) { }
    }

    private void runOnMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run(); else main.post(r);
    }

    /** Something unexpected happened in the ads layer: switch ads off for now, keep the app running. */
    private void fail(String where, Throwable t) {
        enabled = false;
        String reason = where + " -> " + AdsGuard.brief(t);
        try { AdsGuard.trip(appContext, reason); } catch (Throwable ignored) { }
        notice("Ads switched off (" + reason + ")");
        try { removeBanners(); } catch (Throwable ignored) { }
        interstitial = null;
        rewarded = null;
    }

    /** Technical message, only while AdsConfig.SHOW_DEBUG_NOTICES is true. */
    private void notice(String message) {
        if (AdsConfig.SHOW_DEBUG_NOTICES) toast(message);
    }

    private void toast(String message) {
        try { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) { }
    }
}
