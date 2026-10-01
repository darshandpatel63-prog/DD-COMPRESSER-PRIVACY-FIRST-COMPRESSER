package com.darshan.compresser;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK = 501;
    private static final int CREATE = 502;

    private final ArrayList<Item> items = new ArrayList<>();
    private LinearLayout list;
    private EditText targetValue;
    private Spinner targetUnit;
    private Button compressButton;
    private ProgressBar progress;
    private TextView status;
    private ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private Handler main = new Handler(Looper.getMainLooper());
    private File pendingOutput;
    private String pendingOutputName;

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
        root.setPadding(dp(16), dp(10), dp(16), dp(18));
        root.setBackgroundColor(Color.rgb(16,18,20));
        scroll.addView(root);

        LinearLayout bar = row();
        TextView brand = text("DD Compressor", 19, Color.WHITE); brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        TextView privacy = text("LOCAL • PRIVATE", 10, Color.rgb(98,184,165)); privacy.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        bar.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1));
        bar.addView(privacy, new LinearLayout.LayoutParams(-2, dp(48)));
        root.addView(bar);

        TextView title = text("Compress without uploading.", 27, Color.WHITE); title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title);
        TextView sub = text("Native Android processing. The web site is not used by this app.", 12.5f, Color.rgb(150,157,164));
        root.addView(sub, margin(0,4,0,14));

        Button pick = button("＋  Select files", Color.rgb(227,162,74), Color.rgb(42,28,8));
        pick.setOnClickListener(v -> openPicker());
        root.addView(pick, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView supported = text("Images  •  Video  •  Audio  •  PDF  •  Other files", 10, Color.rgb(112,120,128));
        supported.setGravity(Gravity.CENTER);
        root.addView(supported, margin(0,8,0,14));

        LinearLayout settings = card();
        TextView label = text("TARGET SIZE", 9.5f, Color.rgb(126,134,142)); label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        settings.addView(label);
        LinearLayout sizeRow = row();
        targetValue = new EditText(this); targetValue.setText("500"); targetValue.setTextColor(Color.WHITE); targetValue.setTextSize(14); targetValue.setSingleLine(true); targetValue.setInputType(2|8192);
        targetUnit = new Spinner(this);
        ArrayAdapter<String> units = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"KB","MB","GB"});
        targetUnit.setAdapter(units);
        sizeRow.addView(targetValue, new LinearLayout.LayoutParams(dp(110), dp(46)));
        sizeRow.addView(targetUnit, new LinearLayout.LayoutParams(dp(96), dp(46)));
        sizeRow.addView(text("Higher target = more quality • lower target = smaller file", 10, Color.rgb(126,134,142)), new LinearLayout.LayoutParams(0, dp(46), 1));
        settings.addView(sizeRow);
        root.addView(settings, margin(0,0,0,12));

        TextView selected = text("SELECTED FILES", 9.5f, Color.rgb(126,134,142)); selected.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(selected, margin(2,0,0,6));

        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list);

        compressButton = button("Compress selected files", Color.rgb(227,162,74), Color.rgb(42,28,8));
        compressButton.setEnabled(false); compressButton.setAlpha(.45f); compressButton.setOnClickListener(v -> compressAll());
        root.addView(compressButton, margin(0,12,0,0));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setVisibility(View.GONE);
        root.addView(progress, margin(0,12,0,0));
        status = text("Files are processed locally on this phone.", 11, Color.rgb(126,134,142));
        root.addView(status, margin(0,8,0,12));

        LinearLayout info = card();
        TextView infoT = text("QUALITY FIRST", 10, Color.rgb(227,162,74)); infoT.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        info.addView(infoT);
        info.addView(text("The native engine first tries to keep the largest sensible dimensions and highest feasible quality. It only reduces quality/resolution further when the requested target cannot be reached.", 10.5f, Color.rgb(154,161,168)));
        Button privacyBtn = button("Privacy policy", Color.rgb(32,37,42), Color.rgb(235,238,241));
        privacyBtn.setOnClickListener(v -> openUrl("https://dd-tech-labs-hub.vercel.app/product/dd-compressor"));
        info.addView(privacyBtn, margin(0,10,0,0));
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
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == PICK) {
            ClipData cd = data.getClipData();
            if (cd != null) for (int i=0;i<cd.getItemCount();i++) addUri(cd.getItemAt(i).getUri());
            else if (data.getData() != null) addUri(data.getData());
        } else if (requestCode == CREATE && pendingOutput != null) {
            Uri dest = data.getData();
            if (dest != null) executor.execute(() -> {
                try { copyFileToUri(pendingOutput, dest); main.post(() -> toast("Saved successfully.")); }
                catch(Exception e){ main.post(() -> toast("Could not save: "+e.getMessage())); }
            });
        }
    }

    private void addUri(Uri uri) {
        for (Item i : items) if (i.uri.equals(uri)) return;
        try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
        String name = FileUtils.displayName(this, uri);
        long size = FileUtils.size(this, uri);
        String mime = FileUtils.mime(this, uri, name);
        Item item = new Item(uri,name,size,mime);
        items.add(item);
        addCard(item);
        updateButton();
    }

    private void addCard(Item item) {
        LinearLayout card = card();
        TextView name = text(item.name, 13.5f, Color.WHITE); name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(name);
        card.addView(text((item.size>0?FileUtils.formatBytes(item.size):"Unknown size")+"  •  "+item.category(), 10.5f, Color.rgb(132,140,148)));
        Button remove = button("Remove", Color.rgb(32,37,42), Color.rgb(190,196,202));
        remove.setOnClickListener(v -> { items.remove(item); list.removeView(card); updateButton(); });
        card.addView(remove, margin(0,8,0,0));
        item.card=card;
        list.addView(card, margin(0,0,0,8));
    }

    private void updateButton() {
        compressButton.setEnabled(!items.isEmpty());
        compressButton.setAlpha(items.isEmpty() ? .45f : 1f);
    }

    private void compressAll() {
        if(items.isEmpty()) return;
        long target = parseTarget();
        if(target<=0){ toast("Enter a valid target size."); return; }
        compressButton.setEnabled(false); progress.setVisibility(View.VISIBLE); progress.setProgress(0);
        executor.execute(() -> {
            int done=0;
            for(Item item:new ArrayList<>(items)){
                int index=++done;
                main.post(() -> { status.setText("Compressing "+index+" of "+items.size()+": "+item.name); progress.setProgress((index-1)*100/items.size()); });
                try {
                    NativeResult result = NativeCompressor.compress(this,item.uri,item.name,item.mime,item.size,target,(pct,msg)->main.post(()->{status.setText(msg); progress.setProgress(Math.min(99,(int)((index-1)*100.0/items.size()+pct/items.size())));}));
                    item.result=result;
                    main.post(()->showResult(item));
                } catch(Exception e){ main.post(()->toast("Failed: "+item.name+" — "+e.getMessage())); }
                main.post(()->progress.setProgress(index*100/items.size()));
            }
            main.post(()->{ compressButton.setEnabled(true); progress.setVisibility(View.GONE); status.setText("Done. Outputs stay in app cache until you save or share them."); });
        });
    }

    private void showResult(Item item) {
        if(item.card==null) return;
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        TextView t = text(item.result.originalKept ? "Original kept" : (FileUtils.formatBytes(item.result.output.length())+" • "+item.result.detail), 10.5f, item.result.originalKept?Color.rgb(227,162,74):Color.rgb(98,184,165));
        row.addView(t,new LinearLayout.LayoutParams(0,dp(40),1));
        Button save=button("Save",Color.rgb(32,37,42),Color.WHITE); save.setOnClickListener(v->save(item.result));
        Button share=button("Share",Color.rgb(32,37,42),Color.WHITE); share.setOnClickListener(v->share(item.result));
        row.addView(save,new LinearLayout.LayoutParams(dp(82),dp(40))); row.addView(share,new LinearLayout.LayoutParams(dp(82),dp(40)));
        item.card.addView(row,margin(0,10,0,0));
    }

    private void save(NativeResult result) {
        pendingOutput=result.output; pendingOutputName=result.outputName;
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType(result.mime); i.putExtra(Intent.EXTRA_TITLE,result.outputName); startActivityForResult(i,CREATE);
    }

    private void share(NativeResult result) {
        try {
            Uri uri= FileProvider.getUriForFile(this,"com.darshan.compresser.fileprovider",result.output);
            Intent i=new Intent(Intent.ACTION_SEND); i.setType(result.mime); i.putExtra(Intent.EXTRA_STREAM,uri); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivity(Intent.createChooser(i,"Share compressed file"));
        } catch(Exception e){ toast("Could not share: "+e.getMessage()); }
    }

    private void handleIncoming(Intent intent) {
        if(intent==null) return;
        String action=intent.getAction();
        if(Intent.ACTION_SEND.equals(action)) {
            Parcelable parcelable = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (parcelable instanceof Uri) addUri((Uri) parcelable);
        } else if(Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<? extends Parcelable> sharedItems = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (sharedItems != null) {
                for (Parcelable parcelable : sharedItems) {
                    if (parcelable instanceof Uri) addUri((Uri) parcelable);
                }
            }
        }
    }

    private long parseTarget() {
        try {
            double n=Double.parseDouble(targetValue.getText().toString().trim());
            int pos=targetUnit.getSelectedItemPosition();
            double mult=pos==0?1024:pos==1?1024*1024:1024d*1024*1024;
            return (long)Math.max(1,n*mult);
        } catch(Exception e){ return -1; }
    }

    private void copyFileToUri(File source, Uri dest) throws IOException {
        try(InputStream in=new BufferedInputStream(new FileInputStream(source)); OutputStream out=new BufferedOutputStream(getContentResolver().openOutputStream(dest))) {
            if(out==null) throw new IOException("No writable destination.");
            byte[] b=new byte[128*1024]; int n; while((n=in.read(b))>=0) out.write(b,0,n);
        }
    }

    private void openUrl(String u){ try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception e){toast("Could not open link.");} }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}

    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private Button button(String s,int bg,int fg){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTextColor(fg);b.setAllCaps(false);android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(bg);g.setCornerRadius(dp(12));b.setBackground(g);b.setPadding(dp(8),0,dp(8),0);return b;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout card(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setPadding(dp(12),dp(11),dp(12),dp(11));android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(Color.rgb(24,27,31));g.setCornerRadius(dp(14));g.setStroke(dp(1),Color.rgb(43,48,54));l.setBackground(g);return l;}
    private LinearLayout.LayoutParams margin(int l,int t,int r,int b){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(dp(l),dp(t),dp(r),dp(b));return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    static final class Item {
        final Uri uri; final String name; final long size; final String mime; LinearLayout card; NativeResult result;
        Item(Uri u,String n,long s,String m){uri=u;name=n;size=s;mime=m;}
        String category(){if(mime.startsWith("image/"))return"Image";if(mime.startsWith("video/"))return"Video";if(mime.startsWith("audio/"))return"Audio";if("application/pdf".equals(mime))return"PDF";return"Other";}
    }

    public static final class NativeResult {
        final File output; final String outputName; final String mime; final String detail; final boolean originalKept;
        NativeResult(File f,String n,String m,String d,boolean o){output=f;outputName=n;mime=m;detail=d;originalKept=o;}
    }
}
