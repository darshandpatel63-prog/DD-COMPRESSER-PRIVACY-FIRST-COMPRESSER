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

    // Live ad unit IDs are used in both debug and release builds.
    static String bannerId() { return BANNER; }
    static String interstitialId() { return INTERSTITIAL; }
    static String rewardedId() { return REWARDED; }
    static String rewardedInterstitialId() { return REWARDED_INTERSTITIAL; }
    static String nativeId() { return NATIVE; }
    static String idMode() { return "LIVE"; }

    // ------------------------------------------------------------------
    // WHEN TO SHOW ADS
    // ------------------------------------------------------------------
    /** Banner: one at the top and one at the bottom of the screen (adaptive size). */
    static final boolean TOP_BANNER = true;
    static final boolean BOTTOM_BANNER = true;

    /** Interstitial: only after a batch where at least this many files were processed. */
    static final int INTERSTITIAL_MIN_FILES = 7;

    /** Rewarded: 30 minutes without interstitial/native ads; banner ads remain visible. */
    static final long AD_FREE_MS = 30L * 60L * 1000L;

    // ------------------------------------------------------------------
    // SAFETY
    // ------------------------------------------------------------------
    /**
     * Master kill-switch. false = the Ads SDK is never touched at all (no consent, no initialize, no ads),
     * but the library stays in the APK. Useful to find out whether a crash comes from running ads or
     * only from the library being present.
     */
    static final boolean ADS_ENABLED = true;

    /**
     * When an ad fails to load (for example "no fill" on a brand-new ad unit) it is tried again later, with
     * growing pauses: 30 s, 60 s, 120 s, 240 s, then every 5 min, at most RETRY_MAX_TRIES times per launch.
     * Without this a banner that failed once would stay hidden until the app is opened again.
     */
    static final long RETRY_FIRST_MS = 30L * 1000L;
    static final long RETRY_MAX_MS = 5L * 60L * 1000L;
    static final int RETRY_MAX_TRIES = 8;

    /** Ads wait this long after launch so the first screen is always drawn before any ads code runs. */
    static final long START_DELAY_MS = 1500L;

    /**
     * Show a short toast with the technical reason when the ads layer switches itself off.
     * Handy while testing on a phone. Set to false once the integration is proven stable.
     */
    static final boolean SHOW_DEBUG_NOTICES = true;

    /** The ads status toast appears this long after the last ad result (one summary instead of many toasts). */
    static final long STATUS_REPORT_DELAY_MS = 4000L;
}
