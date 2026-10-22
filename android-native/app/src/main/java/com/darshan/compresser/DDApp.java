package com.darshan.compresser;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Application class: crash safety net for DD Compressor.
 *  - Records the last crash (stack trace) so it can be shown on the next launch.
 *  - If a crash comes from Google Ads / UMP, ads are paused for a while so the
 *    compressor itself always opens.
 */
public class DDApp extends Application {
    static final String CRASH_PREFS = "dd_compressor_crash";
    private static final long ADS_BLOCK_MS = 12L * 60L * 60L * 1000L; // 12 hours

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        installCrashRecorder(base);
    }

    private static void installCrashRecorder(final Context ctx) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                String trace = sw.toString();
                if (trace.length() > 6000) trace = trace.substring(0, 6000);
                SharedPreferences.Editor e = ctx.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE).edit();
                e.putString("last_trace", "Thread: " + thread.getName() + "\n" + trace);
                e.putLong("last_time", System.currentTimeMillis());
                e.putBoolean("unseen", true);
                if (trace.contains("com.google.android.")) {
                    e.putLong("ads_disabled_until", System.currentTimeMillis() + ADS_BLOCK_MS);
                }
                e.commit(); // synchronous: the process is about to die
            } catch (Throwable ignored) {}
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
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
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            if (trace.length() > 6000) trace = trace.substring(0, 6000);
            ctx.getSharedPreferences(CRASH_PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("ads_init_pending", false)
                    .putLong("ads_disabled_until", System.currentTimeMillis() + ADS_BLOCK_MS)
                    .putString("last_trace", "Ads failure (caught):\n" + trace)
                    .putBoolean("unseen", true)
                    .commit();
        } catch (Throwable ignored) {}
    }
}
