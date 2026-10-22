package com.darshan.compresser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;

import com.google.android.gms.ads.*;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.google.android.gms.ads.nativead.MediaView;
import com.google.android.gms.ads.appopen.AppOpenAd;
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback;
import com.google.android.ump.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class MainActivity extends Activity {
    private static final int PICK = 501;
    private static final String TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741";
    private static final String TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712";
    private static final String TEST_REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917";
    private static final String TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/5354046379";
    private static final String TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110";
    private static final String TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921";
    private static final int INTERSTITIAL_INTERVAL = 15;
    private static final int NATIVE_AD_INTERVAL = 3;
    private static final int LARGE_BATCH_MIN_FILES = 7;
    private static final int LARGE_BATCHES_FOR_REWARD = 4;
    private static final long AD_FREE_DURATION_MS = 30L * 60L * 1000L;
    private static final String AD_PREFS = "dd_compressor_ad_rewards";

    private ScrollView mainScroll;
    private FrameLayout adContainer;
    private Button adPrivacyButton;
    private ConsentInformation consentInformation;
    private AdView bannerAdView;
    private InterstitialAd interstitialAd;
    private RewardedAd rewardedAd;
    private RewardedInterstitialAd rewardedInterstitialAd;
    private AppOpenAd appOpenAd;
    private boolean adsSdkReady = false;
    private boolean adsEnabled = false;
    private final Runnable startAdsRunnable = this::startAdsSafely;
    private boolean loadingRewardedAd = false;
    private boolean loadingRewardedInterstitialAd = false;
    private boolean loadingNativeAd = false;
    private boolean loadingAppOpenAd = false;
    private boolean showingFullScreenAd = false;
    private boolean activityResumed = false;
    private boolean adFreeUiWasActive = false;
    private boolean suppressNextBackgroundCount = false;
    private boolean compressionRunning = false;
    private final Runnable adFreeExpiryRunnable = this::refreshAdFreeUi;
    private int completedCompressionCountForAds = 0;
    private int completedCompressionResultsForNativeAds = 0;
    private int completedLargeBatchCount = 0;
    private long adFreeUntilMs = 0L;
    private final ArrayList<View> nativeAdCards = new ArrayList<>();

    private final ArrayList<Item> items = new ArrayList<>();
    private LinearLayout list;
    private Button compressButton;
    private ProgressBar progress;
    private TextView status;
    private final ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16,18,20));
        getWindow().setNavigationBarColor(Color.rgb(16,18,20));
        adFreeUntilMs = getSharedPreferences(AD_PREFS, MODE_PRIVATE).getLong("ad_free_until", 0L);
        completedLargeBatchCount = getSharedPreferences(AD_PREFS, MODE_PRIVATE).getInt("large_batch_count", 0);
        if (isAdFreeActive()) scheduleAdFreeExpiry();
        adsEnabled = BuildConfig.ADMOB_ENABLED && !DDApp.adsBlocked(this);
        buildUi();
        handleIncoming(getIntent());
        showPreviousCrashIfAny();
        // Ads start only AFTER the UI is visible, and any ad failure can never kill the app.
        if (adsEnabled) main.postDelayed(startAdsRunnable, 1500);
    }

    /** Starts consent + ads in a protected way. A crash/failure here only disables ads for a while. */
    private void startAdsSafely() {
        if (!adsEnabled || isFinishing()) return;
        try {
            DDApp.markAdsStartup(this, true);
            initializeAdConsent();
        } catch (Throwable t) {
            disableAdsAfterFailure(t);
        }
    }

    private void disableAdsAfterFailure(Throwable t) {
        Log.e("DDCompressorAds", "Ads disabled after failure", t);
        adsEnabled = false;
        adsSdkReady = false;
        try { DDApp.blockAds(this, t); } catch (Throwable ignored) {}
        try {
            if (adContainer != null) adContainer.setVisibility(View.GONE);
            if (mainScroll != null) {
                mainScroll.setPadding(mainScroll.getPaddingLeft(), mainScroll.getPaddingTop(),
                        mainScroll.getPaddingRight(), 0);
            }
        } catch (Throwable ignored) {}
    }

    private void showPreviousCrashIfAny() {
        try {
            SharedPreferences sp = getSharedPreferences(DDApp.CRASH_PREFS, MODE_PRIVATE);
            if (!sp.getBoolean("unseen", false)) return;
            final String trace = sp.getString("last_trace", "");
            sp.edit().putBoolean("unseen", false).apply();
            if (trace == null || trace.isEmpty()) return;
            TextView tv = new TextView(this);
            tv.setText(trace);
            tv.setTextSize(10);
            tv.setTextIsSelectable(true);
            tv.setPadding(dp(16), dp(8), dp(16), dp(8));
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            new AlertDialog.Builder(this)
                    .setTitle("Previous crash report")
                    .setView(sv)
                    .setPositiveButton("Copy", (d, w) -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("DD Compressor crash", trace));
                        toast("Crash report copied");
                    })
                    .setNegativeButton("Close", null)
                    .show();
        } catch (Throwable ignored) {}
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        try {
            refreshAdFreeUi();
            maybeShowAppOpenAd();
        } catch (Throwable t) {
            disableAdsAfterFailure(t);
        }
    }

    @Override protected void onStop() {
        // Normal (non-crash) stop: the startup guard flag must not stay set.
        try { DDApp.markAdsStartup(this, false); } catch (Throwable ignored) {}
        super.onStop();
    }

    @Override protected void onPause() {
        activityResumed = false;
        super.onPause();
    }

    @Override public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (suppressNextBackgroundCount) {
            suppressNextBackgroundCount = false;
            return;
        }
        if (!showingFullScreenAd && !isAdFreeActive()) {
            int count = getSharedPreferences(AD_PREFS, MODE_PRIVATE).getInt("background_count", 0);
            getSharedPreferences(AD_PREFS, MODE_PRIVATE).edit()
                    .putInt("background_count", Math.min(4, count + 1)).apply();
        }
    }

    @Override protected void onDestroy() {
        main.removeCallbacks(startAdsRunnable);
        if (bannerAdView != null) {
            bannerAdView.destroy();
            bannerAdView = null;
        }
        for (View view : nativeAdCards) {
            Object tag = view.getTag();
            if (tag instanceof NativeAd) ((NativeAd) tag).destroy();
        }
        nativeAdCards.clear();
        super.onDestroy();
    }

    private String bannerAdUnitId() {
        return BuildConfig.ADMOB_TEST_MODE
                ? TEST_BANNER_AD_UNIT_ID
                : BuildConfig.ADMOB_BANNER_AD_UNIT_ID;
    }

    private String interstitialAdUnitId() {
        return BuildConfig.ADMOB_TEST_MODE
                ? TEST_INTERSTITIAL_AD_UNIT_ID
                : BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID;
    }

    private String rewardedAdUnitId() {
        return BuildConfig.ADMOB_TEST_MODE
                ? TEST_REWARDED_AD_UNIT_ID : BuildConfig.ADMOB_REWARDED_AD_UNIT_ID;
    }

    private String rewardedInterstitialAdUnitId() {
        return BuildConfig.ADMOB_TEST_MODE
                ? TEST_REWARDED_INTERSTITIAL_AD_UNIT_ID : BuildConfig.ADMOB_REWARDED_INTERSTITIAL_AD_UNIT_ID;
    }

    private String nativeAdUnitId() {
        return BuildConfig.ADMOB_TEST_MODE
                ? TEST_NATIVE_AD_UNIT_ID : BuildConfig.ADMOB_NATIVE_AD_UNIT_ID;
    }

    private String appOpenAdUnitId() {
        return BuildConfig.ADMOB_TEST_MODE
                ? TEST_APP_OPEN_AD_UNIT_ID : BuildConfig.ADMOB_APP_OPEN_AD_UNIT_ID;
    }

    private boolean isAdFreeActive() {
        return System.currentTimeMillis() < adFreeUntilMs;
    }

    private void grantAdFreePass(String reason) {
        adFreeUntilMs = Math.max(adFreeUntilMs, System.currentTimeMillis()) + AD_FREE_DURATION_MS;
        getSharedPreferences(AD_PREFS, MODE_PRIVATE).edit().putLong("ad_free_until", adFreeUntilMs).apply();
        scheduleAdFreeExpiry();
        refreshAdFreeUi();
        toast("30 minutes of Ad-Free Compression unlocked!");
        Log.i("DDCompressorAds", "Ad-free pass granted: " + reason);
    }

    private void scheduleAdFreeExpiry() {
        main.removeCallbacks(adFreeExpiryRunnable);
        long remaining = adFreeUntilMs - System.currentTimeMillis();
        if (remaining > 0) main.postDelayed(adFreeExpiryRunnable, remaining);
    }

    private void refreshAdFreeUi() {
        boolean adFree = isAdFreeActive();
        if (adFree) scheduleAdFreeExpiry();
        if (adContainer != null) {
            if (adFree) {
                adContainer.setVisibility(View.GONE);
            } else if (adFreeUiWasActive && bannerAdView != null
                    && bannerAdView.getParent() == adContainer) {
                // Reload the banner only when the ad-free pass ends, not on every resume.
                adContainer.setVisibility(View.GONE);
                bannerAdView.loadAd(new AdRequest.Builder().build());
            }
        }
        if (mainScroll != null) {
            mainScroll.setPadding(mainScroll.getPaddingLeft(), mainScroll.getPaddingTop(),
                    mainScroll.getPaddingRight(), adFree ? 0 : (bannerAdView != null ? dp(100) : 0));
        }
        for (View view : nativeAdCards) view.setVisibility(adFree ? View.GONE : View.VISIBLE);
        adFreeUiWasActive = adFree;
        if (!adFree && adsSdkReady && bannerAdView == null) loadBannerAd();
    }

    private void loadRewardedAd() {
        if (!adsSdkReady || loadingRewardedAd || rewardedAd != null || isFinishing()) return;
        loadingRewardedAd = true;
        RewardedAd.load(this, rewardedAdUnitId(), new AdRequest.Builder().build(),
                new RewardedAdLoadCallback() {
                    @Override public void onAdLoaded(RewardedAd ad) {
                        loadingRewardedAd = false;
                        rewardedAd = ad;
                    }
                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        loadingRewardedAd = false;
                        rewardedAd = null;
                        Log.w("DDCompressorAds", "Rewarded ad failed: " + error.getMessage());
                    }
                });
    }

    private void loadRewardedInterstitialAd() {
        if (!adsSdkReady || loadingRewardedInterstitialAd || rewardedInterstitialAd != null || isFinishing()) return;
        loadingRewardedInterstitialAd = true;
        RewardedInterstitialAd.load(this, rewardedInterstitialAdUnitId(), new AdRequest.Builder().build(),
                new RewardedInterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(RewardedInterstitialAd ad) {
                        loadingRewardedInterstitialAd = false;
                        rewardedInterstitialAd = ad;
                    }
                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        loadingRewardedInterstitialAd = false;
                        rewardedInterstitialAd = null;
                        Log.w("DDCompressorAds", "Rewarded interstitial failed: " + error.getMessage());
                    }
                });
    }

    private void loadAppOpenAd() {
        if (!adsSdkReady || loadingAppOpenAd || appOpenAd != null || isFinishing() || isAdFreeActive()) return;
        loadingAppOpenAd = true;
        AppOpenAd.load(this, appOpenAdUnitId(), new AdRequest.Builder().build(),
                new AppOpenAdLoadCallback() {
                    @Override public void onAdLoaded(AppOpenAd ad) {
                        loadingAppOpenAd = false;
                        appOpenAd = ad;
                        maybeShowAppOpenAd();
                    }
                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        loadingAppOpenAd = false;
                        appOpenAd = null;
                        Log.w("DDCompressorAds", "App open ad failed: " + error.getMessage());
                    }
                });
    }

    private void maybeShowAppOpenAd() {
        if (!adsSdkReady || isFinishing() || !activityResumed || isAdFreeActive() || showingFullScreenAd) return;
        int backgroundCount = getSharedPreferences(AD_PREFS, MODE_PRIVATE).getInt("background_count", 0);
        if (backgroundCount < 4) return;
        if (appOpenAd == null) {
            loadAppOpenAd();
            return;
        }
        AppOpenAd ad = appOpenAd;
        appOpenAd = null;
        showingFullScreenAd = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                showingFullScreenAd = false;
                getSharedPreferences(AD_PREFS, MODE_PRIVATE).edit().putInt("background_count", 0).apply();
                loadAppOpenAd();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                showingFullScreenAd = false;
                Log.w("DDCompressorAds", "App open show failed: " + error.getMessage());
                loadAppOpenAd();
            }
        });
        ad.show(this);
    }

    private void showRewardedAdForAdFree() {
        if (!adsEnabled) return;
        if (isAdFreeActive()) {
            toast("Ad-Free Compression is already active.");
            return;
        }
        if (rewardedAd == null) {
            loadRewardedAd();
            toast("Rewarded ad is loading. Please try again shortly.");
            return;
        }
        RewardedAd ad = rewardedAd;
        rewardedAd = null;
        final boolean[] earned = {false};
        showingFullScreenAd = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                showingFullScreenAd = false;
                if (earned[0]) grantAdFreePass("rewarded-ad");
                loadRewardedAd();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                showingFullScreenAd = false;
                Log.w("DDCompressorAds", "Rewarded show failed: " + error.getMessage());
                loadRewardedAd();
            }
        });
        ad.show(this, rewardItem -> earned[0] = true);
    }

    private void showRewardedInterstitialForBatch() {
        if (rewardedInterstitialAd == null) {
            loadRewardedInterstitialAd();
            toast("The required ad is loading. Try again shortly, or select 6 or fewer files.");
            return;
        }
        RewardedInterstitialAd ad = rewardedInterstitialAd;
        rewardedInterstitialAd = null;
        final boolean[] earned = {false};
        showingFullScreenAd = true;
        ad.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                showingFullScreenAd = false;
                loadRewardedInterstitialAd();
                if (earned[0]) startCompressionBatch(true);
                else toast("The reward was not earned. Select 6 or fewer files, or try again.");
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                showingFullScreenAd = false;
                Log.w("DDCompressorAds", "Rewarded interstitial show failed: " + error.getMessage());
                loadRewardedInterstitialAd();
                toast("Ad could not be shown. Please try again or select 6 or fewer files.");
            }
        });
        ad.show(this, rewardItem -> earned[0] = true);
    }

    private void loadNativeAdAfter(Item item) {
        if (!adsSdkReady || isAdFreeActive() || isFinishing()) return;
        AdLoader loader = new AdLoader.Builder(this, nativeAdUnitId())
                .forNativeAd(nativeAd -> {
                    if (isFinishing() || isAdFreeActive() || item.card == null) {
                        nativeAd.destroy();
                        return;
                    }
                    addNativeAdCardAfter(item, nativeAd);
                })
                .withAdListener(new AdListener() {
                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        Log.w("DDCompressorAds", "Native ad failed: " + error.getMessage());
                    }
                }).build();
        loader.loadAd(new AdRequest.Builder().build());
    }

    private void addNativeAdCardAfter(Item item, NativeAd nativeAd) {
        NativeAdView adView = new NativeAdView(this);
        LinearLayout adCard = card();
        adCard.addView(text("Ad", 9, Color.rgb(227,162,74)));
        LinearLayout adContent = new LinearLayout(this);
        adContent.setOrientation(LinearLayout.VERTICAL);
        TextView headline = text("", 13, Color.WHITE);
        headline.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        adContent.addView(headline);
        TextView body = text("", 10.5f, Color.rgb(154,161,168));
        adContent.addView(body, margin(0,3,0,0));
        TextView cta = text("", 11, Color.rgb(227,162,74));
        cta.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        adContent.addView(cta, margin(0,5,0,0));
        adView.setHeadlineView(headline);
        adView.setBodyView(body);
        adView.setCallToActionView(cta);
        adView.addView(adContent);
        headline.setText(nativeAd.getHeadline());
        if (nativeAd.getBody() != null) {
            body.setText(nativeAd.getBody());
            body.setVisibility(View.VISIBLE);
        } else body.setVisibility(View.GONE);
        if (nativeAd.getCallToAction() != null) {
            cta.setText(nativeAd.getCallToAction());
            cta.setVisibility(View.VISIBLE);
        } else cta.setVisibility(View.GONE);
        adView.setNativeAd(nativeAd);
        adCard.addView(adView);
        adCard.setTag(nativeAd);
        nativeAdCards.add(adCard);
        int index = list.indexOfChild(item.card);
        if (index >= 0) list.addView(adCard, index + 1, margin(0,0,0,8));
        else nativeAd.destroy();
    }

    private void initializeAdConsent() {
        // Keep Google consent/ad startup out of the launch path until ads are explicitly enabled.
        // A consent or ad-SDK issue must never block the local compressor UI.
        if (!adsEnabled) {
            Log.i("DDCompressorAds", "AdMob is disabled (build config or crash guard); continuing without ads.");
            return;
        }
        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consentInformation.requestConsentInfoUpdate(
                this,
                params,
                () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(this, formError -> {
                    if (formError != null) {
                        Log.w("DDCompressorAds", "Consent form: " + formError.getMessage());
                    }
                    updateAdPrivacyButton();
                    if (consentInformation.canRequestAds()) initializeMobileAdsSdk();
                    else DDApp.markAdsStartup(MainActivity.this, false);
                }),
                requestError -> {
                    Log.w("DDCompressorAds", "Consent info: " + requestError.getMessage());
                    updateAdPrivacyButton();
                    if (consentInformation.canRequestAds()) initializeMobileAdsSdk();
                    else DDApp.markAdsStartup(MainActivity.this, false);
                });
    }

    private void updateAdPrivacyButton() {
        runOnUiThread(() -> {
            if (adPrivacyButton == null || consentInformation == null) return;
            boolean required = consentInformation.getPrivacyOptionsRequirementStatus()
                    == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
            adPrivacyButton.setVisibility(required ? View.VISIBLE : View.GONE);
        });
    }

    private void showAdPrivacyOptions() {
        if (!adsEnabled) return;
        UserMessagingPlatform.showPrivacyOptionsForm(this, formError -> {
            if (formError != null) {
                Log.w("DDCompressorAds", "Privacy options form: " + formError.getMessage());
            }
            updateAdPrivacyButton();
            if (consentInformation != null && consentInformation.canRequestAds()) {
                initializeMobileAdsSdk();
            }
        });
    }

    private void initializeMobileAdsSdk() {
        if (!adsEnabled || adsSdkReady || isFinishing()) return;
        try {
            MobileAds.initialize(this, initializationStatus -> runOnUiThread(() -> {
                if (!adsEnabled || isFinishing()) return;
                try {
                    adsSdkReady = true;
                    DDApp.markAdsStartup(MainActivity.this, false);
                    loadBannerAd();
                    loadInterstitialAd();
                    loadRewardedAd();
                    loadRewardedInterstitialAd();
                    loadAppOpenAd();
                    refreshAdFreeUi();
                } catch (Throwable t) {
                    disableAdsAfterFailure(t);
                }
            }));
        } catch (Throwable t) {
            disableAdsAfterFailure(t);
        }
    }

    private void loadBannerAd() {
        if (!adsSdkReady || consentInformation == null || !consentInformation.canRequestAds()
                || adContainer == null || isFinishing()) return;

        if (bannerAdView != null) {
            bannerAdView.destroy();
            bannerAdView = null;
        }

        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this, widthDp);
        AdView view = new AdView(this);
        bannerAdView = view;
        view.setAdUnitId(bannerAdUnitId());
        view.setAdSize(adSize);
        adContainer.removeAllViews();
        adContainer.addView(view, new FrameLayout.LayoutParams(
                -1, adSize.getHeightInPixels(this), Gravity.CENTER));
        view.setAdListener(new AdListener() {
            @Override public void onAdLoaded() {
                runOnUiThread(() -> {
                    if (isFinishing() || adContainer == null) return;
                    if (isAdFreeActive()) {
                        adContainer.setVisibility(View.GONE);
                        return;
                    }
                    adContainer.setVisibility(View.VISIBLE);
                    if (mainScroll != null) {
                        mainScroll.setPadding(mainScroll.getPaddingLeft(),
                                mainScroll.getPaddingTop(), mainScroll.getPaddingRight(), dp(100));
                    }
                });
            }

            @Override public void onAdFailedToLoad(LoadAdError error) {
                Log.w("DDCompressorAds", "Banner failed: " + error.getMessage());
                runOnUiThread(() -> {
                    if (adContainer != null) adContainer.setVisibility(View.GONE);
                    if (mainScroll != null) {
                        mainScroll.setPadding(mainScroll.getPaddingLeft(), mainScroll.getPaddingTop(),
                                mainScroll.getPaddingRight(), 0);
                    }
                });
            }
        });
        view.loadAd(new AdRequest.Builder().build());
    }

    private void loadInterstitialAd() {
        if (!adsSdkReady || consentInformation == null || !consentInformation.canRequestAds()
                || isFinishing() || interstitialAd != null) return;

        InterstitialAd.load(this, interstitialAdUnitId(), new AdRequest.Builder().build(),
                new InterstitialAdLoadCallback() {
                    @Override public void onAdLoaded(InterstitialAd ad) {
                        interstitialAd = ad;
                    }

                    @Override public void onAdFailedToLoad(LoadAdError error) {
                        interstitialAd = null;
                        Log.w("DDCompressorAds", "Interstitial failed: " + error.getMessage());
                    }
                });
    }

    private void recordSuccessfulCompressionForAds(Item item) {
        if (!adsSdkReady || consentInformation == null || !consentInformation.canRequestAds()
                || isAdFreeActive()) return;
        completedCompressionCountForAds++;
        completedCompressionResultsForNativeAds++;
        if (completedCompressionResultsForNativeAds % NATIVE_AD_INTERVAL == 0) {
            loadNativeAdAfter(item);
        }
        if (completedCompressionCountForAds < INTERSTITIAL_INTERVAL) return;
        completedCompressionCountForAds = 0;
        if (interstitialAd == null) {
            loadInterstitialAd();
            return;
        }
        InterstitialAd adToShow = interstitialAd;
        interstitialAd = null;
        showingFullScreenAd = true;
        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override public void onAdDismissedFullScreenContent() {
                showingFullScreenAd = false;
                loadInterstitialAd();
            }
            @Override public void onAdFailedToShowFullScreenContent(AdError error) {
                showingFullScreenAd = false;
                Log.w("DDCompressorAds", "Interstitial show failed: " + error.getMessage());
                loadInterstitialAd();
            }
        });
        adToShow.show(MainActivity.this);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        mainScroll = scroll;
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(28), dp(16), dp(22));
        root.setBackgroundColor(Color.rgb(16,18,20));
        scroll.addView(root);

        LinearLayout bar = row();
        TextView brand = text("DD Compressor", 19, Color.WHITE);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView privacy = text("LOCAL • PRIVATE", 10, Color.rgb(98,184,165));
        privacy.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        bar.addView(privacy, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(bar);

        TextView title = text("Compress without uploading.", 27, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, margin(0, 12, 0, 0));

        TextView sub = text("Native Android processing. Your files stay on this phone.", 12.5f, Color.rgb(150,157,164));
        root.addView(sub, margin(0,4,0,16));

        Button pick = button("＋  Select files", Color.rgb(227,162,74), Color.rgb(42,28,8));
        pick.setOnClickListener(v -> openPicker());
        root.addView(pick, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView supported = text("Images  •  Video  •  Audio  •  PDF  •  Other files", 10, Color.rgb(112,120,128));
        supported.setGravity(Gravity.CENTER);
        root.addView(supported, margin(0,8,0,18));

        TextView selected = text("SELECTED FILES", 9.5f, Color.rgb(126,134,142));
        selected.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(selected, margin(2,0,0,7));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        compressButton = button("Compress selected files", Color.rgb(227,162,74), Color.rgb(42,28,8));
        compressButton.setEnabled(false);
        compressButton.setAlpha(.45f);
        compressButton.setOnClickListener(v -> compressAll());
        root.addView(compressButton, margin(0,12,0,0));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, margin(0,12,0,0));

        status = text("Choose an output format and target size for each file.", 11, Color.rgb(126,134,142));
        root.addView(status, margin(0,8,0,12));

        LinearLayout info = card();
        TextView infoT = text("QUALITY FIRST • LOCAL PROCESSING", 10, Color.rgb(227,162,74));
        infoT.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(infoT);
        info.addView(text("Each file has its own output format and target size. The native engine keeps the highest practical quality while trying to reach your selected target.", 10.5f, Color.rgb(154,161,168)));

        Button adFreeBtn = button("Watch an ad • 30 min Ad-Free", Color.rgb(227,162,74), Color.rgb(42,28,8));
        adFreeBtn.setVisibility(adsEnabled ? View.VISIBLE : View.GONE);
        adFreeBtn.setOnClickListener(v -> showRewardedAdForAdFree());
        info.addView(adFreeBtn, margin(0,10,0,0));

        Button privacyBtn = button("Privacy Policy", Color.rgb(32,37,42), Color.rgb(235,238,241));
        privacyBtn.setOnClickListener(v -> openUrl("https://dd-tech-labs-hub.vercel.app/products/dd-compressor/privacy.html"));
        info.addView(privacyBtn, margin(0,10,0,0));

        adPrivacyButton = button("Ad privacy options", Color.rgb(32,37,42), Color.rgb(235,238,241));
        adPrivacyButton.setVisibility(View.GONE);
        adPrivacyButton.setOnClickListener(v -> showAdPrivacyOptions());
        info.addView(adPrivacyButton, margin(0,7,0,0));

        Button hubBtn = button("D.D. Tech Labs Hub", Color.rgb(32,37,42), Color.rgb(235,238,241));
        hubBtn.setOnClickListener(v -> openUrl("https://dd-tech-labs-hub.vercel.app/"));
        info.addView(hubBtn, margin(0,7,0,0));
        root.addView(info, margin(0,0,0,14));

        TextView footer = text("D.D. Tech Labs  •  DD Compressor", 10, Color.rgb(100,108,116));
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);

        FrameLayout screen = new FrameLayout(this);
        screen.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        adContainer = new FrameLayout(this);
        adContainer.setBackgroundColor(Color.rgb(16,18,20));
        adContainer.setVisibility(View.GONE);
        FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        screen.addView(adContainer, adParams);
        setContentView(screen);
    }

    private void openPicker() {
        suppressNextBackgroundCount = true;
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK) suppressNextBackgroundCount = false;
        if (requestCode != PICK || resultCode != RESULT_OK || data == null) return;
        ClipData cd = data.getClipData();
        if (cd != null) {
            for (int i=0; i<cd.getItemCount(); i++) addUri(cd.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            addUri(data.getData());
        }
    }

    private void addUri(Uri uri) {
        for (Item i : items) if (i.uri.equals(uri)) return;
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}

        String name = FileUtils.displayName(this, uri);
        long size = FileUtils.size(this, uri);
        String mime = FileUtils.mime(this, uri, name);
        Item item = new Item(uri, name, size, mime);
        items.add(item);
        addCard(item);
        updateButton();
    }

    private void addCard(Item item) {
        LinearLayout card = card();

        TextView name = text(item.name, 13.5f, Color.WHITE);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(name);
        card.addView(text((item.size > 0 ? FileUtils.formatBytes(item.size) : "Unknown size") + "  •  " + item.category(),
                10.5f, Color.rgb(132,140,148)), margin(0,2,0,9));

        TextView formatLabel = text("OUTPUT FORMAT", 9, Color.rgb(126,134,142));
        formatLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(formatLabel);

        Spinner format = new Spinner(this);
        String[] options = item.formatOptions();
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, options);
        format.setAdapter(formatAdapter);
        format.setSelection(0);
        format.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) {}
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                item.outputFormat = options[position];
            }
        });
        card.addView(format, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView targetLabel = text("TARGET SIZE", 9, Color.rgb(126,134,142));
        targetLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(targetLabel, margin(0,7,0,0));

        LinearLayout sizeRow = row();
        EditText value = new EditText(this);
        value.setText("500");
        value.setTextColor(Color.WHITE);
        value.setTextSize(14);
        value.setSingleLine(true);
        value.setInputType(2|8192);
        Spinner unit = new Spinner(this);
        ArrayAdapter<String> units = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"KB","MB","GB"});
        unit.setAdapter(units);
        unit.setSelection(1);
        sizeRow.addView(value, new LinearLayout.LayoutParams(dp(110), dp(46)));
        sizeRow.addView(unit, new LinearLayout.LayoutParams(dp(92), dp(46)));
        sizeRow.addView(text("Highest quality practical within this target", 9.5f, Color.rgb(126,134,142)),
                new LinearLayout.LayoutParams(0, dp(46), 1));
        card.addView(sizeRow);

        Button remove = button("Remove", Color.rgb(32,37,42), Color.rgb(190,196,202));
        remove.setOnClickListener(v -> { items.remove(item); list.removeView(card); updateButton(); });
        card.addView(remove, margin(0,8,0,0));

        item.card = card;
        item.targetValue = value;
        item.targetUnit = unit;
        list.addView(card, margin(0,0,0,8));
    }

    private void updateButton() {
        compressButton.setEnabled(!items.isEmpty());
        compressButton.setAlpha(items.isEmpty() ? .45f : 1f);
    }

    private void compressAll() {
        if (items.isEmpty()) return;
        for (Item item : items) {
            item.targetBytes = parseTarget(item);
            if (item.targetBytes <= 0) { toast("Enter a valid target size for: " + item.name); return; }
        }
        if (items.size() >= LARGE_BATCH_MIN_FILES && !isAdFreeActive()) {
            showRewardedInterstitialForBatch();
            return;
        }
        startCompressionBatch(false);
    }

    private void startCompressionBatch(boolean earnedBatchAd) {
        if (items.isEmpty() || compressionRunning) return;
        compressionRunning = true;
        final ArrayList<Item> batchItems = new ArrayList<>(items);
        final int batchFileCount = batchItems.size();
        compressButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);

        executor.execute(() -> {
            int done = 0;
            int successfulFiles = 0;
            for (Item item : batchItems) {
                int index = ++done;
                main.post(() -> {
                    status.setText("Compressing " + index + " of " + items.size() + ": " + item.name);
                    progress.setProgress((index-1)*100/items.size());
                });
                try {
                    NativeResult result = NativeCompressor.compress(
                            this, item.uri, item.name, item.mime, item.size, item.targetBytes, item.outputFormat,
                            (pct,msg) -> main.post(() -> {
                                status.setText(msg);
                                progress.setProgress(Math.min(99, (int)((index-1)*100.0/items.size()+pct/items.size())));
                            }));
                    item.result = result;
                    successfulFiles++;
                    main.post(() -> showResult(item));
                } catch (Exception e) {
                    main.post(() -> toast("Failed: " + item.name + " — " + e.getMessage()));
                }
                main.post(() -> progress.setProgress(index*100/items.size()));
            }
            final int completedSuccessfully = successfulFiles;
            main.post(() -> {
                compressionRunning = false;
                compressButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                status.setText("Done. Saved files go directly to Downloads / DD Compressor.");
                if (batchFileCount >= LARGE_BATCH_MIN_FILES && completedSuccessfully >= LARGE_BATCH_MIN_FILES) {
                    completedLargeBatchCount++;
                    getSharedPreferences(AD_PREFS, MODE_PRIVATE).edit()
                            .putInt("large_batch_count", completedLargeBatchCount).apply();
                    if (completedLargeBatchCount >= LARGE_BATCHES_FOR_REWARD) {
                        completedLargeBatchCount = 0;
                        getSharedPreferences(AD_PREFS, MODE_PRIVATE).edit()
                                .putInt("large_batch_count", 0).apply();
                        grantAdFreePass("four-large-batches");
                    }
                }
            });
        });
    }

    private void showResult(Item item) {
        if (item.card == null || item.result == null) return;
        recordSuccessfulCompressionForAds(item);
        LinearLayout resultRow = row();
        TextView t = text(item.result.originalKept
                ? "Original kept • " + FileUtils.formatBytes(item.result.output.length())
                : FileUtils.formatBytes(item.result.output.length()) + " • " + item.result.detail,
                10.5f, item.result.originalKept ? Color.rgb(227,162,74) : Color.rgb(98,184,165));
        resultRow.addView(t, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button save = button("Save", Color.rgb(32,37,42), Color.WHITE);
        save.setOnClickListener(v -> saveDirect(item.result));
        Button share = button("Share", Color.rgb(32,37,42), Color.WHITE);
        share.setOnClickListener(v -> share(item.result));
        resultRow.addView(save, new LinearLayout.LayoutParams(dp(82),dp(44)));
        resultRow.addView(share, new LinearLayout.LayoutParams(dp(82),dp(44)));
        item.card.addView(resultRow, margin(0,10,0,0));
    }

    private void saveDirect(NativeResult result) {
        executor.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, result.outputName);
                values.put(MediaStore.Downloads.MIME_TYPE, result.mime);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/DD Compressor");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Android could not create the Downloads file.");
                try (InputStream in = new BufferedInputStream(new FileInputStream(result.output));
                     OutputStream out = new BufferedOutputStream(getContentResolver().openOutputStream(uri))) {
                    if (out == null) throw new IOException("Could not open Downloads file.");
                    byte[] b = new byte[128*1024];
                    int n;
                    while ((n = in.read(b)) >= 0) out.write(b,0,n);
                }
                main.post(() -> toast("Saved to Downloads / DD Compressor"));
            } catch (Exception e) {
                main.post(() -> toast("Could not save: " + e.getMessage()));
            }
        });
    }

    private void share(NativeResult result) {
        try {
            Uri uri = FileProvider.getUriForFile(this, "com.darshan.compresser.fileprovider", result.output);
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(result.mime);
            i.putExtra(Intent.EXTRA_STREAM, uri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "Share compressed file"));
        } catch (Exception e) { toast("Could not share: " + e.getMessage()); }
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (parcelable instanceof Uri) addUri((Uri) parcelable);
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<? extends Parcelable> sharedItems = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (sharedItems != null) for (Parcelable parcelable : sharedItems)
                if (parcelable instanceof Uri) addUri((Uri) parcelable);
        }
    }

    private long parseTarget(Item item) {
        try {
            double n = Double.parseDouble(item.targetValue.getText().toString().trim());
            int pos = item.targetUnit.getSelectedItemPosition();
            double mult = pos == 0 ? 1024 : pos == 1 ? 1024*1024 : 1024d*1024*1024;
            return (long)Math.max(1, n * mult);
        } catch (Exception e) { return -1; }
    }

    private void openUrl(String u) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u))); }
        catch (Exception e) { toast("Could not open link."); }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    private TextView text(String s,float size,int color) {
        TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); return t;
    }

    private Button button(String s,int bg,int fg) {
        Button b=new Button(this); b.setText(s); b.setTextSize(13); b.setTextColor(fg); b.setAllCaps(false);
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(bg); g.setCornerRadius(dp(12)); b.setBackground(g); b.setPadding(dp(8),0,dp(8),0); return b;
    }

    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}

    private LinearLayout card(){
        LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(12),dp(11),dp(12),dp(11));
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.rgb(24,27,31));g.setCornerRadius(dp(14));g.setStroke(dp(1),Color.rgb(43,48,54));
        l.setBackground(g);return l;
    }

    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;
    }

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    static final class Item {
        final Uri uri; final String name; final long size; final String mime;
        LinearLayout card; NativeResult result;
        EditText targetValue; Spinner targetUnit;
        String outputFormat = "Auto"; long targetBytes;
        Item(Uri u,String n,long s,String m){uri=u;name=n;size=s;mime=m;}
        String category(){
            if(mime.startsWith("image/"))return"Image";
            if(mime.startsWith("video/"))return"Video";
            if(mime.startsWith("audio/"))return"Audio";
            if("application/pdf".equals(mime))return"PDF";
            return"Other";
        }
        String[] formatOptions(){
            if(mime.startsWith("image/")) return new String[]{"Auto","JPEG","WebP","PNG"};
            if(mime.startsWith("video/")) return new String[]{"MP4"};
            if(mime.startsWith("audio/")) return new String[]{"M4A (AAC)"};
            if("application/pdf".equals(mime)) return new String[]{"PDF"};
            return new String[]{"GZIP (.gz)"};
        }
    }

    public static final class NativeResult {
        final File output; final String outputName; final String mime; final String detail; final boolean originalKept;
        NativeResult(File f,String n,String m,String d,boolean o){output=f;outputName=n;mime=m;detail=d;originalKept=o;}
    }
}
