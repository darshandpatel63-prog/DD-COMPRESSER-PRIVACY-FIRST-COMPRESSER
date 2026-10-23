package com.darshan.compresser;

/**
 * Single source of truth for every AdMob ID and every "when do we show an ad" rule.
 * Change IDs or rules HERE only; AdsManager just reads these values.
 *
 * Full explanation: ADMOB_INTEGRATION.md (project root).
 */
final class AdsConfig {
    private AdsConfig() {}

    // ------------------------------------------------------------------
    // LIVE AdMob IDs (release builds)
    // ------------------------------------------------------------------
    /** AdMob App ID. The real copy lives in AndroidManifest.xml (meta-data APPLICATION_ID). */
    static final String APP_ID = "ca-app-pub-9557757991977213~8750973505";

    static final String BANNER = "ca-app-pub-9557757991977213/8928348733";
    static final String INTERSTITIAL = "ca-app-pub-9557757991977213/9510802662";
    static final String REWARDED = "ca-app-pub-9557757991977213/1993993468";

    /** Created in AdMob, not used by the app yet. Kept here so the ID is never lost. */
    static final String REWARDED_INTERSTITIAL = "ca-app-pub-9557757991977213/5180675414";
    /** Created in AdMob, not used by the app yet. Kept here so the ID is never lost. */
    static final String NATIVE = "ca-app-pub-9557757991977213/5013329464";

    // ------------------------------------------------------------------
    // Google's official TEST ad units (debug builds only)
    // ------------------------------------------------------------------
    static final String TEST_BANNER = "ca-app-pub-3940256099942544/9214589741";
    static final String TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712";
    static final String TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917";

    /**
     * Set to true to make even a RELEASE build show Google's test ads (safe for testing on your own phone).
     * Never click your own live ads: it violates AdMob policy and can get the account disabled.
     * Keep false for the real store release.
     */
    static final boolean FORCE_TEST_ADS = false;

    private static boolean useTestAds() { return BuildConfig.DEBUG || FORCE_TEST_ADS; }
    static String bannerId() { return useTestAds() ? TEST_BANNER : BANNER; }
    static String interstitialId() { return useTestAds() ? TEST_INTERSTITIAL : INTERSTITIAL; }
    static String rewardedId() { return useTestAds() ? TEST_REWARDED : REWARDED; }

    // ------------------------------------------------------------------
    // WHEN TO SHOW ADS
    // ------------------------------------------------------------------
    /** Banner: one at the top and one at the bottom of the screen (adaptive size). */
    static final boolean TOP_BANNER = true;
    static final boolean BOTTOM_BANNER = true;

    /** Interstitial: only after a batch where at least this many files were processed. */
    static final int INTERSTITIAL_MIN_FILES = 7;

    /** Rewarded: the "Watch an ad" button gives this much ad-free time (banners + interstitials off). */
    static final long AD_FREE_MS = 30L * 60L * 1000L;

    // ------------------------------------------------------------------
    // SAFETY
    // ------------------------------------------------------------------
    /** Ads wait this long after launch so the first screen is always drawn before any ads code runs. */
    static final long START_DELAY_MS = 1500L;

    /**
     * Show a short toast with the technical reason when the ads layer switches itself off.
     * Handy while testing on a phone. Set to false once the integration is proven stable.
     */
    static final boolean SHOW_DEBUG_NOTICES = true;
}
