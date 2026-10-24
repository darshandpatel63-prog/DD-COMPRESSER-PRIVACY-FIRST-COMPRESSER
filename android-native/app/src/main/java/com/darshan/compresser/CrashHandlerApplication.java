package com.darshan.compresser;

import android.app.Application;
import android.content.Intent;
import android.util.Log;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

/** Captures uncaught Java exceptions and opens a copyable on-screen report. */
public final class CrashHandlerApplication extends Application {
    private static final AtomicBoolean REPORTING = new AtomicBoolean(false);

    @Override public void onCreate() {
        super.onCreate();
        // Do not recursively relaunch the report screen if its isolated process fails.
        if ((getPackageName() + ":crash").equals(android.app.Application.getProcessName())) return;
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (!REPORTING.compareAndSet(false, true)) {
                Log.e("DDCompressorCrash", "Repeated crash while reporting", error);
                return;
            }
            StringWriter trace = new StringWriter();
            error.printStackTrace(new PrintWriter(trace));
            String report = "App: DD Compressor\nThread: " + thread.getName()
                    + "\n\n" + trace;
            try {
                Intent intent = new Intent(getApplicationContext(), CrashActivity.class);
                intent.putExtra(CrashActivity.EXTRA_ERROR_TEXT, report);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                getApplicationContext().startActivity(intent);
            } catch (Throwable reportError) {
                Log.e("DDCompressorCrash", "Unable to display crash report", reportError);
                Log.e("DDCompressorCrash", "Original uncaught exception", error);
            }
            // Intentionally do not invoke the default handler: it would immediately kill
            // the process and close the report screen. Restart the app after copying the trace.
        });
    }
}
