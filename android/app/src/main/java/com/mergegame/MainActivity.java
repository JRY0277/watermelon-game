package com.mergegame;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 合成大西瓜 · Android 壳
 * 游戏本体是 assets/game.html（WebView 加载），离线可玩。
 *
 * 带崩溃自报：万一启动就崩，下次打开会把错误原因显示出来（便于定位）。
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_PERM = 1002;
    private static final int MAX_PICK = 11;      // 游戏最多 11 级

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                try { writeCrash(e); } catch (Throwable ignore) { }
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(1);
            }
        });

        String last = readCrash();
        if (last != null) {
            showCrash(last);
            return;
        }

        try {
            startGame();
        } catch (Throwable t) {
            showError("启动失败", t);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void startGame() {
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);              // localStorage：最高分 / 图片 / 手感 / 本机榜
        s.setAllowContentAccess(true);             // 读取相册返回的图片
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        try {
            // 只有填了排行榜地址（file:// 页面去请求 http 接口）才需要，部分系统会拒绝，失败就算了
            s.setAllowUniversalAccessFromFileURLs(true);
        } catch (Throwable ignored) { }

        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
                filePathCallback = cb;
                if (openPicker()) return true;
                filePathCallback = null;
                return false;
            }
        });
        web.loadUrl("file:///android_asset/game.html");
    }

    // ---------- 选图 ----------

    private boolean openPicker() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                Intent i = new Intent("android.provider.action.PICK_IMAGES"); // 系统照片选择器，免权限
                i.setType("image/*");
                i.putExtra("android.provider.extra.PICK_IMAGES_MAX", MAX_PICK);
                startActivityForResult(i, REQ_FILE);
                return true;
            } catch (ActivityNotFoundException e) {
                // 这台机器没有照片选择器，走老办法
            }
        }
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERM);
            return true;
        }
        return launchGetContent();
    }

    private boolean launchGetContent() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(i, "选择图片"), REQ_FILE);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "没找到可以选图片的应用", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private void cancelPick() {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code == REQ_PERM) {
            boolean ok = res != null && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            if (ok) {
                if (!launchGetContent()) cancelPick();
            } else {
                Toast.makeText(this, "需要允许读取照片，才能换图片", Toast.LENGTH_SHORT).show();
                cancelPick();
            }
            return;
        }
        super.onRequestPermissionsResult(code, perms, res);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            ValueCallback<Uri[]> cb = filePathCallback;
            filePathCallback = null;
            if (cb == null) return;

            Uri[] uris = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    uris = new Uri[n];
                    for (int i = 0; i < n; i++) uris[i] = data.getClipData().getItemAt(i).getUri();
                } else if (data.getData() != null) {
                    uris = new Uri[]{data.getData()};
                }
            }
            cb.onReceiveValue(uris);
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // ---------- 崩溃自报 ----------

    private File crashFile() { return new File(getFilesDir(), "crash.txt"); }

    private void writeCrash(Throwable e) {
        try {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            FileOutputStream out = new FileOutputStream(crashFile(), false);
            out.write(sw.toString().getBytes("UTF-8"));
            out.close();
        } catch (Throwable ignore) { }
    }

    private String readCrash() {
        try {
            File f = crashFile();
            if (!f.exists()) return null;
            FileInputStream in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int read = 0, n;
            while ((n = in.read(buf, read, buf.length - read)) > 0) read += n;
            in.close();
            return new String(buf, 0, read, "UTF-8");
        } catch (Throwable t) {
            return null;
        }
    }

    private void clearCrash() {
        try { crashFile().delete(); } catch (Throwable ignore) { }
    }

    /** 上次崩溃：把原因显示出来（可长按复制），并提供继续进入游戏的按钮 */
    private void showCrash(String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 24, 24, 24);
        box.setBackgroundColor(0xFFFFFFFF);

        TextView title = new TextView(this);
        title.setText("⚠ 上次启动崩溃了\n下面是原因，截图发给开发者即可定位：");
        title.setTextSize(15);
        title.setTextColor(0xFFD93025);

        ScrollView sv = new ScrollView(this);
        TextView body = new TextView(this);
        body.setText(detail);
        body.setTextSize(11);
        body.setTextIsSelectable(true);
        body.setMovementMethod(new ScrollingMovementMethod());
        body.setPadding(0, 12, 0, 12);
        sv.addView(body);

        Button again = new Button(this);
        again.setText("仍然打开游戏");
        again.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clearCrash();
                try { startGame(); } catch (Throwable t) { showError("启动失败", t); }
            }
        });

        Button copy = new Button(this);
        copy.setText("清空错误信息");
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                clearCrash();
                recreate();
            }
        });

        box.addView(title);
        box.addView(sv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        box.addView(again);
        box.addView(copy);
        setContentView(box);
    }

    private void showError(String title, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 24, 24, 24);
        TextView tv = new TextView(this);
        tv.setText(title + "\n\n" + sw.toString());
        tv.setTextSize(12);
        tv.setTextIsSelectable(true);
        tv.setPadding(0, 0, 0, 16);
        box.addView(tv);
        setContentView(box);
    }
}
