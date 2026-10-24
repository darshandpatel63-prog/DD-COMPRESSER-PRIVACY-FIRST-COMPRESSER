package com.darshan.compresser;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Minimal crash screen designed to remain readable when the main UI has failed. */
public final class CrashActivity extends Activity {
    public static final String EXTRA_ERROR_TEXT = "dd_compressor_crash_text";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String suppliedReport = getIntent().getStringExtra(EXTRA_ERROR_TEXT);
        final String report = (suppliedReport == null || suppliedReport.trim().isEmpty())
                ? "No stack trace was provided." : suppliedReport;
        getWindow().getDecorView().setBackgroundColor(Color.rgb(18, 20, 24));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(22), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(18, 20, 24));

        TextView title = new TextView(this);
        title.setText("DD Compressor — Error Report");
        title.setTextColor(Color.rgb(255, 115, 105));
        title.setTextSize(21);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(this);
        hint.setText("The app encountered an unexpected error. Copy the full report below before restarting.");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(14);
        hint.setPadding(0, 0, 0, dp(12));
        root.addView(hint, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        TextView details = new TextView(this);
        details.setText(report);
        details.setTextIsSelectable(true);
        details.setTextColor(Color.rgb(235, 238, 242));
        details.setTextSize(12);
        details.setTypeface(android.graphics.Typeface.MONOSPACE);
        details.setPadding(dp(12), dp(12), dp(12), dp(12));
        details.setBackgroundColor(Color.rgb(31, 34, 40));
        scroll.addView(details, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button copy = new Button(this);
        copy.setText("Copy full error text");
        copy.setAllCaps(false);
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("DD Compressor crash report", report));
                Toast.makeText(this, "Full error report copied", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Clipboard is unavailable", Toast.LENGTH_LONG).show();
            }
        });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(12);
        root.addView(copy, buttonParams);

        TextView restart = new TextView(this);
        restart.setText("After copying, close this screen and reopen the app.");
        restart.setTextColor(Color.LTGRAY);
        restart.setGravity(Gravity.CENTER);
        restart.setPadding(0, dp(8), 0, 0);
        root.addView(restart, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root, new ViewGroup.LayoutParams(-1, -1));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
