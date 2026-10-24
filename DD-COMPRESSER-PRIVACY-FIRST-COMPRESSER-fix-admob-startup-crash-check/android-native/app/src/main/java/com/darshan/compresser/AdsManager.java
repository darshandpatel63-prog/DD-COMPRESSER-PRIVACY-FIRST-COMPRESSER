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

    // What every ad type is doing right now: shown in the status toast and used for the retry back-off.
    private static final int TOP = 0, BOTTOM = 1, INTER = 2, REWARD = 3;
    private final String[] adStatus = {"waiting", "waiting", "waiting", "waiting"};
    private final boolean[] adOk = new boolean[4];
    private final int[] adFails = new int[4];       // consecutive failures per ad type
    private final Runnable reportTask = this::reportStatus;

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
                // If nothing has been reported after 20 s (consent or SDK stuck), say so once.
                if (AdsConfig.SHOW_DEBUG_NOTICES) main.postDelayed(reportTask, 20000L);
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
                adOk[INTER] = false;
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

    /** Long-press on the "Watch an ad" button: shows what every ad type is doing right now. */
    void showStatus() {
        runOnMain(() -> {
            try { if (AdsConfig.SHOW_DEBUG_NOTICES) toast(statusText()); } catch (Throwable ignored) { }
        });
    }

    /** "Watch an ad" button: a finished rewarded ad = AdsConfig.AD_FREE_MS without banners/interstitials. */
    void showRewarded() {
        runOnMain(() -> {
            try {
                if (!usable() || !sdkReady) {
                    toast("Ads are not available right now. Please try again later.");
                    notice(statusText());
                    return;
                }
                if (isAdFree()) {
                    toast("Ad-free mode is already active.");
                    return;
                }
                if (rewarded == null) {
                    loadRewarded();
                    toast("The ad is still loading. Please try again in a few seconds.");
                    notice("Rewarded: " + adStatus[REWARD]);
                    return;
                }
                final RewardedAd ad = rewarded;
                rewarded = null;
                adOk[REWARD] = false;
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

            adStatus[top ? TOP : BOTTOM] = "loading";
            adOk[top ? TOP : BOTTOM] = false;
            final AdView view = new AdView(activity);
            view.setAdUnitId(AdsConfig.bannerId());
            // Screen width minus the 16dp + 16dp side padding of the main layout.
            DisplayMetrics dm = activity.getResources().getDisplayMetrics();
            int widthDp = Math.max(1, Math.round(dm.widthPixels / dm.density) - 32);
            view.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp));
            view.setAdListener(new AdListener() {
                @Override public void onAdLoaded() {
                    if ((top ? topBanner : bottomBanner) != view) return;   // callback of a replaced banner
                    slot.setVisibility(View.VISIBLE);
                    adResult(top ? TOP : BOTTOM, true, "OK");
                }
                @Override public void onAdFailedToLoad(LoadAdError error) {
                    if ((top ? topBanner : bottomBanner) != view) return;
                    slot.setVisibility(View.GONE);
                    adResult(top ? TOP : BOTTOM, false, describe(error));
                }
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
            adStatus[INTER] = "loading";
            InterstitialAd.load(activity, AdsConfig.interstitialId(), new AdRequest.Builder().build(),
                    new InterstitialAdLoadCallback() {
                        @Override public void onAdLoaded(InterstitialAd ad) {
                            main.post(() -> {
                                interstitialLoading = false;
                                if (usable()) { interstitial = ad; adResult(INTER, true, "OK (ready)"); }
                            });
                        }
                        @Override public void onAdFailedToLoad(LoadAdError error) {
                            main.post(() -> {
                                interstitialLoading = false;
                                interstitial = null;
                                adResult(INTER, false, describe(error));
                            });
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
            adStatus[REWARD] = "loading";
            RewardedAd.load(activity, AdsConfig.rewardedId(), new AdRequest.Builder().build(),
                    new RewardedAdLoadCallback() {
                        @Override public void onAdLoaded(RewardedAd ad) {
                            main.post(() -> {
                                rewardedLoading = false;
                                if (usable()) { rewarded = ad; adResult(REWARD, true, "OK (ready)"); }
                            });
                        }
                        @Override public void onAdFailedToLoad(LoadAdError error) {
                            main.post(() -> {
                                rewardedLoading = false;
                                rewarded = null;
                                adResult(REWARD, false, describe(error));
                            });
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
    // Results, retry back-off and status text
    // ------------------------------------------------------------------

    /** Records how one ad type ended up (loaded / failed), plans a retry after a failure, plans the status toast. */
    private void adResult(int kind, boolean ok, String text) {
        final boolean changed = !text.equals(adStatus[kind]); // banners refresh every ~60 s: no toast for the same result
        adOk[kind] = ok;
        adStatus[kind] = text;
        if (ok) adFails[kind] = 0; else adFails[kind]++;
        if (!ok) scheduleRetry(kind);
        if (changed && AdsConfig.SHOW_DEBUG_NOTICES) {
            main.removeCallbacks(reportTask);
            main.postDelayed(reportTask, AdsConfig.STATUS_REPORT_DELAY_MS);
        }
    }

    /** A failed ad is tried again later (30 s, 60 s, 120 s, 240 s, then every 5 min; limited number of tries). */
    private void scheduleRetry(final int kind) {
        final int fails = adFails[kind];
        if (fails > AdsConfig.RETRY_MAX_TRIES || !usable()) return;
        long delay = Math.min(AdsConfig.RETRY_MAX_MS, AdsConfig.RETRY_FIRST_MS << Math.min(fails - 1, 10));
        main.postDelayed(() -> retry(kind), delay);
    }

    private void retry(final int kind) {
        try {
            if (!usable() || !sdkReady || isAdFree() || adOk[kind]) return;
            if (!resumed) { // do not request ads while the app is in the background
                main.postDelayed(() -> retry(kind), AdsConfig.RETRY_FIRST_MS);
                return;
            }
            if (kind == TOP) loadBanner(true);
            else if (kind == BOTTOM) loadBanner(false);
            else if (kind == INTER) loadInterstitial();
            else loadRewarded();
        } catch (Throwable t) {
            fail("retry", t);
        }
    }

    private void reportStatus() {
        try { if (!destroyed && AdsConfig.SHOW_DEBUG_NOTICES) toast(statusText()); } catch (Throwable ignored) { }
    }

    private String statusText() {
        boolean canRequest = false;
        try { canRequest = consent != null && consent.canRequestAds(); } catch (Throwable ignored) { }
        return "Ads [" + AdsConfig.idMode() + " ids]  on=" + enabled + "  consent=" + canRequest + "  sdk=" + sdkReady
                + (isAdFree() ? "  (ad-free now)" : "")
                + "\nTop banner: " + adStatus[TOP]
                + "\nBottom banner: " + adStatus[BOTTOM]
                + "\nInterstitial: " + adStatus[INTER]
                + "\nRewarded: " + adStatus[REWARD];
    }

    /** Short, readable reason for a failed load. Codes: 0 internal, 1 invalid request, 2 network, 3 no fill. */
    private static String describe(LoadAdError error) {
        try {
            int code = error.getCode();
            String name = code == 3 ? "NO_FILL" : code == 1 ? "INVALID_REQUEST" : code == 2 ? "NETWORK"
                    : code == 0 ? "INTERNAL" : "ERROR";
            String message = error.getMessage();
            if (message != null && message.length() > 60) message = message.substring(0, 60);
            return "FAILED " + code + " " + name + (message == null ? "" : " - " + message);
        } catch (Throwable t) {
            return "FAILED (unknown error)";
        }
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
