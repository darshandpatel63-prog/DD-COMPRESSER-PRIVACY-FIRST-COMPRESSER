package com.darshan.compresser;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class MainActivity extends Activity {
    private static final int PICK = 501;

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
        buildUi();
        handleIncoming(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIncoming(intent);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
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

        Button privacyBtn = button("Privacy Policy", Color.rgb(32,37,42), Color.rgb(235,238,241));
        privacyBtn.setOnClickListener(v -> openUrl("https://dd-tech-labs-hub.vercel.app/products/dd-compressor/privacy.html"));
        info.addView(privacyBtn, margin(0,10,0,0));

        Button hubBtn = button("D.D. Tech Labs Hub", Color.rgb(32,37,42), Color.rgb(235,238,241));
        hubBtn.setOnClickListener(v -> openUrl("https://dd-tech-labs-hub.vercel.app/"));
        info.addView(hubBtn, margin(0,7,0,0));
        root.addView(info, margin(0,0,0,14));

        TextView footer = text("D.D. Tech Labs  •  DD Compressor", 10, Color.rgb(100,108,116));
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);
        setContentView(scroll);
    }

    private void openPicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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

        compressButton.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);

        executor.execute(() -> {
            int done = 0;
            for (Item item : new ArrayList<>(items)) {
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
                    main.post(() -> showResult(item));
                } catch (Exception e) {
                    main.post(() -> toast("Failed: " + item.name + " — " + e.getMessage()));
                }
                main.post(() -> progress.setProgress(index*100/items.size()));
            }
            main.post(() -> {
                compressButton.setEnabled(true);
                progress.setVisibility(View.GONE);
                status.setText("Done. Saved files go directly to Downloads / DD Compressor.");
            });
        });
    }

    private void showResult(Item item) {
        if (item.card == null || item.result == null) return;
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
