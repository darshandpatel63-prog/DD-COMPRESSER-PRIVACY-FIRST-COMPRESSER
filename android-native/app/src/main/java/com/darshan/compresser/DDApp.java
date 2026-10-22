package com.darshan.compresser;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * DD Compressor crash safety net.
 *  - Saves the last crash (stack trace) so it can be shown on the next launch.
 *  - A crash on a background thread that comes from Google Ads / UMP is swallowed
 *    (the app keeps running) and ads are paused for 12 hours.
 *  - A crash on the main thread that comes from Google code pauses ads for 12 hours,
 *    so the NEXT launch always opens normally.
 */
public class DDApp extends Application {
    static final String CRASH_PREFS = "dd_compressor_crash";
    private static final long ADS_BLOCK_MS = 12L * 60L * 60L * 1000L; // 12 hours
    private static volatile boolean installed = false;

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        install(base);
    }

    /** Safe to call many times. Called from Application AND from MainActivity (in case manifest is old). */
    static synchronized void install(Context ctx) {
        if (installed || ctx == null) return;
        installed = true;
        final Context appCtx = ctx.getApplicationContext() != null ? ctx.getApplicationContext() : ctx;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            boolean swallow = false;
            try {
                String trace = traceOf(throwable);
                boolean fromGoogle = trace.contains("com.google.android.");
                boolean mainThread = thread == Looper.getMainLooper().getThread();
                SharedPreferences.Editor e = appCtx.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE).edit();
                e.putString("last_trace", "Thread: " + thread.getName() + "\n" + trace);
                e.putLong("last_time", System.currentTimeMillis());
                e.putBoolean("unseen", true);
                if (fromGoogle) {
                    e.putLong("ads_disabled_until", System.currentTimeMillis() + ADS_BLOCK_MS);
                    e.putBoolean("ads_init_pending", false);
                    // A Google Ads/UMP background thread died: keep the app alive.
                    swallow = !mainThread;
                }
                e.commit(); // synchronous: the process may die right after this
            } catch (Throwable ignored) {}
            if (swallow) return; // thread ends quietly, app keeps running without ads
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    private static String traceOf(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        String s = sw.toString();
        return s.length() > 6000 ? s.substring(0, 6000) : s;
    }

    /** True when ads must stay off (recent ads crash, or the last ads startup never finished). */
    static boolean adsBlocked(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            if (sp.getBoolean("ads_init_pending", false)) {
                // Previous launch died while ads were starting (e.g. native crash) -> pause ads.
                sp.edit()
                        .putBoolean("ads_init_pending", false)
                        .putLong("ads_disabled_until", now + ADS_BLOCK_MS)
                        .putString("last_trace", "Ads startup did not finish on the previous launch.\n"
                                + "The app probably crashed inside Google Ads / consent (UMP) startup.\n"
                                + "Ads are paused for 12 hours so the compressor keeps working.")
                        .putBoolean("unseen", true)
                        .commit();
                return true;
            }
            return now < sp.getLong("ads_disabled_until", 0L);
        } catch (Throwable t) {
            return true; // when in doubt, keep the app safe
        }
    }

    static void markAdsStartup(Context ctx, boolean pending) {
        try {
            ctx.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE)
                    .edit().putBoolean("ads_init_pending", pending).commit();
        } catch (Throwable ignored) {}
    }

    static void blockAds(Context ctx, Throwable t) {
        try {
            ctx.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("ads_init_pending", false)
                    .putLong("ads_disabled_until", System.currentTimeMillis() + ADS_BLOCK_MS)
                    .putString("last_trace", "Ads failure (caught):\n" + traceOf(t))
                    .putBoolean("unseen", true)
                    .commit();
        } catch (Throwable ignored) {}
    }
}
