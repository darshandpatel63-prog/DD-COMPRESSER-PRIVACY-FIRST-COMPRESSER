package com.darshan.compresser;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Crash-loop breaker for the ads layer.
 *
 * Idea: right before ads start we write "startup window open" to disk. If the app is still alive
 * AdsGuard.STABLE_AFTER_MS later, the window is closed. If the process dies in between (uncaught
 * exception, native crash, kill) the flag is still set on the next launch, so we know ads were the
 * suspect and we pause them with a growing back-off (6h, 12h, 24h ... max 7 days).
 * Result: ads can never trap the user in a "splash screen, then instant close" loop.
 *
 * Everything here is wrapped by callers in try/catch(Throwable) and uses only plain SharedPreferences.
 */
final class AdsGuard {
    private AdsGuard() {}

    static final long STABLE_AFTER_MS = 15_000L;

    private static final String PREFS = "dd_ads_guard";
    private static final String K_OPEN = "window_open";
    private static final String K_FAILS = "fails";
    private static final String K_UNTIL = "paused_until";
    private static final String K_CRASH = "crash_text";
    private static final String K_NOTICE = "notice";

    private static final long BASE_PAUSE_MS = 6L * 60L * 60L * 1000L;
    private static final long MAX_PAUSE_MS = 7L * 24L * 60L * 60L * 1000L;

    private static volatile boolean windowOpen;
    private static boolean hookInstalled;

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Call once per launch before touching the ads SDK.
     * @return true if ads may start now, false if they are paused after an earlier problem.
     */
    static boolean begin(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long now = System.currentTimeMillis();

        if (p.getBoolean(K_OPEN, false)) {
            // The previous run never closed its startup window => it died while ads were starting.
            String why = p.getString(K_CRASH, null);
            if (why == null) why = "The app closed while ads were starting.";
            applyPause(p, why, now);
        }

        long until = p.getLong(K_UNTIL, 0L);
        if (until - now > MAX_PAUSE_MS) { // device clock was moved backwards; do not stay paused forever
            until = 0L;
            p.edit().remove(K_UNTIL).apply();
        }
        if (now < until) return false;

        p.edit().putBoolean(K_OPEN, true).commit();
        windowOpen = true;
        installHook(ctx.getApplicationContext());
        return true;
    }

    /** Ads survived the startup window: reset the failure counter. */
    static void markStable(Context ctx) {
        windowOpen = false;
        prefs(ctx).edit().putBoolean(K_OPEN, false).putInt(K_FAILS, 0).remove(K_UNTIL).remove(K_CRASH).apply();
    }

    /** Close the window without judging (normal Activity shutdown). */
    static void closeWindow(Context ctx) {
        windowOpen = false;
        prefs(ctx).edit().putBoolean(K_OPEN, false).apply();
    }

    /** A caught failure inside the ads layer: close the window and pause ads with back-off. */
    static void trip(Context ctx, String reason) {
        windowOpen = false;
        SharedPreferences p = prefs(ctx);
        applyPause(p, reason, System.currentTimeMillis());
        p.edit().remove(K_NOTICE).apply(); // the caller already shows this reason right now
    }

    /** One-time message left by an earlier crash (null if none). */
    static String takeNotice(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String notice = p.getString(K_NOTICE, null);
        if (notice != null) p.edit().remove(K_NOTICE).apply();
        return notice;
    }

    private static void applyPause(SharedPreferences p, String why, long now) {
        int fails = Math.min(p.getInt(K_FAILS, 0) + 1, 8);
        long pause = Math.min(BASE_PAUSE_MS << (fails - 1), MAX_PAUSE_MS);
        p.edit()
                .putBoolean(K_OPEN, false)
                .putInt(K_FAILS, fails)
                .putLong(K_UNTIL, now + pause)
                .putString(K_NOTICE, why)
                .remove(K_CRASH)
                .commit();
    }

    /** Remember why the process died, but only while the startup window is open. */
    private static void installHook(final Context app) {
        if (hookInstalled) return;
        hookInstalled = true;
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                if (windowOpen) {
                    prefs(app).edit().putString(K_CRASH, describeCrash(thread, error)).commit();
                }
            } catch (Throwable ignored) { }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    private static String describeCrash(Thread thread, Throwable error) {
        Throwable root = error;
        for (int i = 0; i < 8 && root.getCause() != null && root.getCause() != root; i++) root = root.getCause();
        StringBuilder sb = new StringBuilder(brief(error));
        if (root != error) sb.append(" <- ").append(brief(root));
        StackTraceElement[] frames = root.getStackTrace();
        for (int i = 0; i < Math.min(3, frames.length); i++) {
            String cls = frames[i].getClassName();
            sb.append("\n@ ").append(cls.substring(cls.lastIndexOf('.') + 1)).append('.').append(frames[i].getMethodName());
        }
        sb.append("\n[thread ").append(thread == null ? "?" : thread.getName()).append(']');
        return sb.length() > 420 ? sb.substring(0, 420) : sb.toString();
    }

    /** Short "ClassName: message" text, safe for a toast. */
    static String brief(Throwable t) {
        String m = t.getMessage();
        String s = t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
        return s.length() > 180 ? s.substring(0, 180) : s;
    }
}
