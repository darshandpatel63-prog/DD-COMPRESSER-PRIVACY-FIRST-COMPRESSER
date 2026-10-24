package com.darshan.compresser;

import android.app.Application;
import android.content.Context;

/**
 * Runs before any content provider or library starts. Its only job is to install the crash reporter
 * so that even a crash during process start-up leaves a readable report (see CrashLog).
 */
public class DDApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try { CrashLog.install(this); } catch (Throwable ignored) { }
    }
}
