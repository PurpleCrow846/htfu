// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public class MainActivity extends Activity {
    private static final String BACKUP_CACHE = "pending-backup.json";
    static final int BACKUP_CODE = 45;
    private static final int MAX_SHARE_BYTES = 2000000;
    private static final int NOTIFY_CODE = 44;
    static final int RESTORE_CODE = 46;
    // 2.12: progress photos. <input type=file> needs onShowFileChooser, or the WebView ignores the tap.
    static final int PHOTO_CODE = 47;
    static final int CAMERA_CODE = 48;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    // 2.14: a camera photo that came back after Android recreated the activity (no WebView callback left)
    private Uri pendingPhotoUri;
    private static final long MAX_PENDING_PHOTO = 15L * 1024 * 1024;
    private static final String TAG = "HTFU";
    private boolean handledThisLaunch;
    private Boolean lastExactAllowed;
    private boolean pageReady;
    String pendingBackupJson;
    private boolean pendingEnable;
    private Intent pendingIntent;
    private boolean pendingStartSession;
    private boolean permissionAsked;
    private RepSensor repSensor;
    private boolean sessionActive;
    private WebView web;
    private final Runnable refreshNext = new Runnable() {
        @Override // java.lang.Runnable
        public final void run() {
            MainActivity.this.lambda$new$0();
        }
    };
    private final LinkedHashSet<String> lastHandled = new LinkedHashSet<>();
    private String pendingDays = "1,3,5";
    private String pendingTime = "08:00";
    private String pendingMode = "week";
    private String pendingStart = "";
    private String pendingOn = "";
    private int pendingLen = 8;

    private boolean isColdLauncher(Intent intent) {
        if (intent == null || "app.htfu.OPEN_FROM_REMINDER".equals(intent.getAction()) || "app.htfu.START".equals(intent.getAction()) || !"android.intent.action.MAIN".equals(intent.getAction())) {
            return false;
        }
        return intent.hasCategory("android.intent.category.LAUNCHER");
    }

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        try {
            ReminderScheduler.ensureChannel(this);
            WebView webView = new WebView(this);
            this.web = webView;
            setContentView(webView);
            WebSettings settings = this.web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(false);
            settings.setMediaPlaybackRequiresUserGesture(false);
            this.web.setWebChromeClient(new AnonymousClass1());
            this.web.setWebViewClient(new WebViewClient() {
                // 2.09: external links open in the browser; the WebView never leaves the app page.
                @Override // android.webkit.WebViewClient
                public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                    return ExternalLinks.intercept(MainActivity.this, request.getUrl().toString());
                }

                @Override // android.webkit.WebViewClient
                @SuppressWarnings("deprecation")
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    return ExternalLinks.intercept(MainActivity.this, url);
                }

                @Override // android.webkit.WebViewClient
                public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                    if (url != null && !ExternalLinks.isAppPage(url)) {
                        view.stopLoading();
                        ExternalLinks.intercept(MainActivity.this, url);
                        view.loadUrl(ExternalLinks.APP_URL);
                        return;
                    }
                    super.onPageStarted(view, url, favicon);
                }

                @Override // android.webkit.WebViewClient
                public void onPageFinished(WebView webView2, String str) {
                    MainActivity.this.pageReady = true;
                    if (MainActivity.this.pendingIntent != null) {
                        Intent intent = MainActivity.this.pendingIntent;
                        MainActivity.this.pendingIntent = null;
                        MainActivity.this.handleIncoming(intent, false);
                    }
                    if (MainActivity.this.pendingStartSession) {
                        MainActivity.this.pendingStartSession = false;
                        MainActivity.this.startTodaySession();
                    }
                    MainActivity.this.deliverPendingPhoto();
                }
            });
            this.web.addJavascriptInterface(new ReminderBridge(), "AndroidReminders");
            this.repSensor = new RepSensor();
            this.web.addJavascriptInterface(new ExportBridge(), "AndroidExport");
            this.web.addJavascriptInterface(new ExternalLinks.Bridge(this), "AndroidLinks");
            this.web.addJavascriptInterface(new RestTimer.Bridge(this), "AndroidRest");
            this.web.addJavascriptInterface(new VoiceCues.Bridge(this), "AndroidVoice");
            this.web.addJavascriptInterface(new SessionNotify.Bridge(this), "AndroidSession");
            SessionNotify.live = new java.lang.ref.WeakReference<>(this);
            this.web.loadUrl("file:///android_asset/www/index.html" + (bundle == null && isColdLauncher(getIntent()) ? "?splash=1" : ""));
            loadHandled();
            if (bundle != null && bundle.getBoolean("handled", false)) {
                this.handledThisLaunch = true;
            }
            if (bundle != null) {
                String cam = bundle.getString("cameraUri");
                if (cam != null) this.cameraUri = Uri.parse(cam);
                String pend = bundle.getString("pendingPhotoUri");
                if (pend != null) this.pendingPhotoUri = Uri.parse(pend);
            }
            Intent intent = getIntent();
            if ("app.htfu.START".equals(intent != null ? intent.getAction() : null)) {
                this.pendingStartSession = true;
            } else if (!this.handledThisLaunch) {
                this.pendingIntent = intent;
            }
            restorePendingBackup();
            TodayWidget.scheduleMidnight(this);
        } catch (Exception e) {
            Log.e(TAG, "onCreate failed", e);
            Toast.makeText(this, "HTFU could not open.", 1).show();
        }
    }

    /* loaded from: classes.dex */
    class AnonymousClass1 extends WebChromeClient {
        AnonymousClass1() {
        }

        @Override // android.webkit.WebChromeClient
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
            return MainActivity.this.openFileChooser(callback, params);
        }

        @Override // android.webkit.WebChromeClient
        public boolean onJsAlert(WebView webView, String str, String str2, final JsResult jsResult) {
            if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) {
                jsResult.cancel();
                return true;
            }
            new AlertDialog.Builder(MainActivity.this).setTitle("HTFU").setMessage(str2).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    jsResult.confirm();
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override // android.content.DialogInterface.OnCancelListener
                public final void onCancel(DialogInterface dialogInterface) {
                    jsResult.cancel();
                }
            }).show();
            return true;
        }

        @Override // android.webkit.WebChromeClient
        public boolean onJsConfirm(WebView webView, String str, String str2, final JsResult jsResult) {
            if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) {
                jsResult.cancel();
                return true;
            }
            new AlertDialog.Builder(MainActivity.this).setTitle("HTFU").setMessage(str2).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    jsResult.confirm();
                }
            }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                @Override // android.content.DialogInterface.OnClickListener
                public final void onClick(DialogInterface dialogInterface, int i) {
                    jsResult.cancel();
                }
            }).setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override // android.content.DialogInterface.OnCancelListener
                public final void onCancel(DialogInterface dialogInterface) {
                    jsResult.cancel();
                }
            }).show();
            return true;
        }
    }

    @Override // android.app.Activity
    protected void onPause() {
        super.onPause();
        RepSensor repSensor = this.repSensor;
        if (repSensor != null) {
            repSensor.stop();
        }
        if (!this.sessionActive) {
            getWindow().clearFlags(128);
        }
    }

    @Override // android.app.Activity
    protected void onDestroy() {
        if (SessionNotify.live.get() == this) SessionNotify.live = new java.lang.ref.WeakReference<>(null);
        finishFileChooser(null);
        RepSensor repSensor = this.repSensor;
        if (repSensor != null) {
            repSensor.stop();
        }
        super.onDestroy();
    }

    @Override // android.app.Activity
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if ("app.htfu.START".equals(intent != null ? intent.getAction() : null)) {
            if (!this.pageReady) {
                this.pendingStartSession = true;
                return;
            } else {
                startTodaySession();
                return;
            }
        }
        if (!this.pageReady) {
            this.pendingIntent = intent;
        } else {
            handleIncoming(intent, false);
        }
    }

    private void loadHandled() {
        try {
            JSONArray jSONArray = new JSONArray(getSharedPreferences("htfu-import", 0).getString("lastHandled", "[]"));
            synchronized (this.lastHandled) {
                this.lastHandled.clear();
                for (int i = 0; i < jSONArray.length(); i++) {
                    String optString = jSONArray.optString(i, "");
                    if (!optString.isEmpty()) {
                        this.lastHandled.add(optString);
                    }
                }
            }
        } catch (Exception e) {
            synchronized (this.lastHandled) {
                this.lastHandled.clear();
            }
        }
    }

    public void saveHandled() {
        try {
            JSONArray jSONArray = new JSONArray();
            synchronized (this.lastHandled) {
                Iterator<String> it = this.lastHandled.iterator();
                while (it.hasNext()) {
                    jSONArray.put(it.next());
                }
            }
            getSharedPreferences("htfu-import", 0).edit().putString("lastHandled", jSONArray.toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "save handled failed", e);
        }
    }

    public void handleIncoming(Intent intent, final boolean z) {
        final Uri data;
        boolean contains;
        if (intent == null) {
            return;
        }
        if ("android.intent.action.SEND".equals(intent.getAction())) {
            data = (Uri) intent.getParcelableExtra("android.intent.extra.STREAM");
            if (data == null) {
                final String stringExtra = intent.getStringExtra("android.intent.extra.TEXT");
                if (stringExtra == null || stringExtra.isEmpty()) {
                    Toast.makeText(this, "Share an HTFU file.", 1).show();
                    setIntent(new Intent());
                    return;
                }
                if (utf8Length(stringExtra) > MAX_SHARE_BYTES) { // 2.14: bytes, same unit as the page and the file path
                    Toast.makeText(this, "File is over 2 MB. Use a smaller backup.", 1).show();
                    setIntent(new Intent());
                    return;
                }
                final String str = "text|" + stringExtra.hashCode();
                synchronized (this.lastHandled) {
                    contains = this.lastHandled.contains(str);
                }
                if (contains) {
                    Toast.makeText(this, "Already imported.", 0).show();
                    setIntent(new Intent());
                    return;
                } else {
                    runOnUiThread(new Runnable() {
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.lambda$handleIncoming$1(stringExtra, str);
                        }
                    });
                    setIntent(new Intent());
                    return;
                }
            }
        } else {
            data = "android.intent.action.VIEW".equals(intent.getAction()) ? intent.getData() : null;
        }
        if (data == null) {
            return;
        }
        setIntent(new Intent());
        new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$handleIncoming$6(data, z);
            }
        }).start();
    }

    public /* synthetic */ void lambda$handleIncoming$1(String str, String str2) {
        WebView webView = this.web;
        if (webView != null) {
            webView.evaluateJavascript("window.onSharedFile(" + JSONObject.quote(str) + "," + JSONObject.quote(str2) + ")", null);
        }
    }

    /* Reads a shared/opened backup file off the UI thread (rebuilt by hand from the 2.11 bytecode). */
    private void lambda$handleIncoming$6(final Uri uri, boolean force) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                return;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int total = 0;
            while (true) {
                int n = in.read(buf);
                if (n <= 0) {
                    break;
                }
                total += n;
                if (total > MAX_SHARE_BYTES) {
                    in.close();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            MainActivity.this.lambda$handleIncoming$2();
                        }
                    });
                    return;
                }
                out.write(buf, 0, n);
            }
            in.close();
            final String text = out.toString("UTF-8");
            String key = uri.toString() + "|" + String.valueOf(text.hashCode());
            boolean seen;
            synchronized (this.lastHandled) {
                seen = this.lastHandled.contains(key);
            }
            if (!force && seen) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.lambda$handleIncoming$3();
                    }
                });
                return;
            }
            final String k2 = force ? "" : key;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.lambda$handleIncoming$4(text, k2);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "share import failed", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.lambda$handleIncoming$5();
                }
            });
        }
    }

    public /* synthetic */ void lambda$handleIncoming$2() {
        Toast.makeText(this, "File is over 2 MB. Use a smaller backup.", 1).show();
    }

    public /* synthetic */ void lambda$handleIncoming$3() {
        Toast.makeText(this, "Already imported.", 0).show();
    }

    public /* synthetic */ void lambda$handleIncoming$4(String str, String str2) {
        WebView webView = this.web;
        if (webView != null) {
            webView.evaluateJavascript("window.onSharedFile(" + JSONObject.quote(str) + "," + JSONObject.quote(str2) + ")", null);
        }
    }

    public /* synthetic */ void lambda$handleIncoming$5() {
        Toast.makeText(this, "Could not read that file.", 1).show();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        Uri data;
        super.onActivityResult(i, i2, intent);
        if (i == PHOTO_CODE || i == CAMERA_CODE) {
            onPhotoResult(i, i2, intent);
            return;
        }
        if (i == BACKUP_CODE) {
            if (i2 != -1 || intent == null || intent.getData() == null) {
                clearPendingBackup();
                Toast.makeText(this, "Backup cancelled.", 0).show();
                return;
            } else {
                final Uri data2 = intent.getData();
                new Thread(new Runnable() {
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$onActivityResult$10(data2);
                    }
                }).start();
                return;
            }
        }
        if (i2 == -1 && intent != null && (data = intent.getData()) != null && i == RESTORE_CODE) {
            handleIncoming(new Intent("android.intent.action.VIEW").setData(data), true);
        }
    }

    public /* synthetic */ void lambda$onActivityResult$10(Uri uri) {
        try {
            File file = new File(getCacheDir(), BACKUP_CACHE);
            if (!file.exists()) {
                runOnUiThread(new Runnable() {
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$onActivityResult$7();
                    }
                });
                return;
            }
            FileInputStream fileInputStream = new FileInputStream(file);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[8192];
            while (true) {
                int read = fileInputStream.read(bArr);
                if (read <= 0) {
                    break;
                } else {
                    byteArrayOutputStream.write(bArr, 0, read);
                }
            }
            fileInputStream.close();
            OutputStream openOutputStream = getContentResolver().openOutputStream(uri);
            if (openOutputStream != null) {
                openOutputStream.write(byteArrayOutputStream.toByteArray());
                openOutputStream.close();
                file.delete();
                this.pendingBackupJson = null;
                runOnUiThread(new Runnable() {
                    @Override // java.lang.Runnable
                    public final void run() {
                        MainActivity.this.lambda$onActivityResult$8();
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "write backup failed", e);
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onActivityResult$9();
                }
            });
        }
    }

    public /* synthetic */ void lambda$onActivityResult$7() {
        Toast.makeText(this, "Backup data missing.", 0).show();
    }

    public /* synthetic */ void lambda$onActivityResult$8() {
        Toast.makeText(this, "Backup saved.", 0).show();
    }

    public /* synthetic */ void lambda$onActivityResult$9() {
        Toast.makeText(this, "Could not save backup.", 0).show();
    }

    private void restorePendingBackup() {
        new Thread(new Runnable() {
            @Override // java.lang.Runnable
            public final void run() {
                MainActivity.this.lambda$restorePendingBackup$12();
            }
        }).start();
    }

    public /* synthetic */ void lambda$restorePendingBackup$12() {
        try {
            File file = new File(getCacheDir(), BACKUP_CACHE);
            if (!file.exists()) {
                return;
            }
            FileInputStream fileInputStream = new FileInputStream(file);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] bArr = new byte[8192];
            while (true) {
                int read = fileInputStream.read(bArr);
                if (read <= 0) {
                    fileInputStream.close();
                    final String byteArrayOutputStream2 = byteArrayOutputStream.toString("UTF-8");
                    runOnUiThread(new Runnable() {
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.lambda$restorePendingBackup$11(byteArrayOutputStream2);
                        }
                    });
                    return;
                }
                byteArrayOutputStream.write(bArr, 0, read);
            }
        } catch (Exception e) {
            Log.e(TAG, "restore pending backup failed", e);
        }
    }

    public /* synthetic */ void lambda$restorePendingBackup$11(String str) {
        this.pendingBackupJson = str;
    }

    private void clearPendingBackup() {
        this.pendingBackupJson = null;
        try {
            File file = new File(getCacheDir(), BACKUP_CACHE);
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            Log.e(TAG, "clear pending backup failed", e);
        }
    }

    @Override // android.app.Activity
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putBoolean("handled", this.handledThisLaunch);
        // 2.14: survive "Don't keep activities" / low-memory kills while the camera app is open
        if (this.cameraUri != null) bundle.putString("cameraUri", this.cameraUri.toString());
        if (this.pendingPhotoUri != null) bundle.putString("pendingPhotoUri", this.pendingPhotoUri.toString());
    }

    public void startTodaySession() {
        WebView webView = this.web;
        if (webView == null) {
            return;
        }
        webView.evaluateJavascript("window.startFromWidget && window.startFromWidget()", null);
    }

    @Override // android.app.Activity
    public void onBackPressed() {
        WebView webView = this.web;
        if (webView != null && webView.getUrl() != null && !ExternalLinks.isAppPage(webView.getUrl())) {
            ExternalLinks.restoreAppPage(webView); // 2.09: never strand the user on a web page
            return;
        }
        if (webView == null) {
            super.onBackPressed();
        } else {
            webView.evaluateJavascript("(function(){ return window.handleBack ? window.handleBack() : false; })()", new ValueCallback() {
                @Override // android.webkit.ValueCallback
                public final void onReceiveValue(Object obj) {
                    MainActivity.this.lambda$onBackPressed$14((String) obj);
                }
            });
        }
    }

    public /* synthetic */ void lambda$onBackPressed$13() {
        super.onBackPressed();
    }

    public /* synthetic */ void lambda$onBackPressed$14(String str) {
        if (!"true".equals(str)) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$onBackPressed$13();
                }
            });
        }
    }

    @Override // android.app.Activity
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != NOTIFY_CODE) {
            return;
        }
        this.permissionAsked = false;
        try {
            boolean granted = results != null && results.length > 0 && results[0] == 0;
            if (this.pendingEnable) {
                setRemindersEnabled(granted, null, null);
                lambda$tellPage$15(granted);
            }
            lambda$new$0();
            tellNotify214(granted); // 2.14: the page shows a note if notifications are off
        } catch (Throwable th) {
            Log.e(TAG, "permission result failed", th);
        } finally {
            this.pendingEnable = false;
        }
    }

    static int[] parseTime(String str) {
        int i = 8;
        int i2 = 0;
        if (str != null) {
            try {
                if (str.contains(":")) {
                    String[] split = str.trim().split(":");
                    int parseInt = Integer.parseInt(split[0].trim());
                    int parseInt2 = (split.length <= 1 || split[1].trim().isEmpty()) ? 0 : Integer.parseInt(split[1].trim());
                    if (parseInt >= 0 && parseInt <= 23 && parseInt2 >= 0 && parseInt2 <= 59) {
                        i2 = parseInt2;
                        i = parseInt;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "bad time " + str, e);
            }
        }
        return new int[]{i, i2};
    }

    public void setRemindersEnabled(boolean z, String str, String str2) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences("htfu", 0);
            SharedPreferences.Editor putBoolean = sharedPreferences.edit().putBoolean("enabled", z);
            if (z && str != null && !"shift".equals(sharedPreferences.getString("mode", "week"))) {
                int[] parseTime = parseTime(str2);
                putBoolean.putString("days", str).putInt("hour", parseTime[0]).putInt("minute", parseTime[1]);
            }
            putBoolean.apply();
            ReminderScheduler.scheduleNext(this);
        } catch (Throwable th) {
            Log.e(TAG, "setRemindersEnabled failed", th);
        }
    }

    public void lambda$new$0() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$new$0();
                }
            });
            return;
        }
        try {
            if (this.web == null) {
                return;
            }
            String nextLabel = ReminderScheduler.nextLabel(this);
            this.web.evaluateJavascript("window.onNextReminder && window.onNextReminder(" + JSONObject.quote(nextLabel) + ")", null);
            this.web.removeCallbacks(this.refreshNext);
            if (nextLabel.startsWith("Reminder going off now")) {
                this.web.postDelayed(this.refreshNext, 3000L);
            }
        } catch (Throwable th) {
            Log.e(TAG, "postNextReminder failed", th);
        }
    }

    private void applySchedule(boolean z, String str, String str2) {
        int[] parseTime = parseTime(str2);
        ReminderScheduler.save(this, z, str, parseTime[0], parseTime[1]);
    }

    private void applyShift(boolean z, String str, int i, String str2, String str3) {
        int[] parseTime = parseTime(str3);
        ReminderScheduler.saveShift(this, z, str, i, str2, parseTime[0], parseTime[1]);
    }

    public void lambda$tellPage$15(final boolean z) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$tellPage$15(z);
                }
            });
            return;
        }
        try {
            WebView webView = this.web;
            if (webView == null) {
                return;
            }
            webView.evaluateJavascript("window.onNotifyPermission && window.onNotifyPermission(" + (z ? "true" : "false") + ")", null);
        } catch (Throwable th) {
            Log.e(TAG, "tellPage failed", th);
        }
    }

    /* loaded from: classes.dex */
    public class ExportBridge {
        private ExportBridge() {
        }

        public /* synthetic */ void lambda$shareLog$0(String str) {
            LogExporter.share(MainActivity.this, str);
        }

        @JavascriptInterface
        public void shareLog(final String str) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ExportBridge.this.lambda$shareLog$0(str);
                }
            });
        }

        public /* synthetic */ void lambda$shareJson$1(String str, String str2, String str3, String str4) {
            LogExporter.shareJson(MainActivity.this, str, str2, str3, str4);
        }

        @JavascriptInterface
        public void shareJson(final String str, final String str2, final String str3, final String str4) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ExportBridge.this.lambda$shareJson$1(str, str2, str3, str4);
                }
            });
        }

        public /* synthetic */ void lambda$backup$2(String str, String str2) {
            LogExporter.backup(MainActivity.this, str, str2);
        }

        @JavascriptInterface
        public void backup(final String str, final String str2) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ExportBridge.this.lambda$backup$2(str, str2);
                }
            });
        }

        @JavascriptInterface
        public void markImported(String str) {
            if (str == null || str.isEmpty()) {
                return;
            }
            synchronized (MainActivity.this.lastHandled) {
                MainActivity.this.lastHandled.remove(str);
                MainActivity.this.lastHandled.add(str);
                while (MainActivity.this.lastHandled.size() > 50) {
                    MainActivity.this.lastHandled.remove(MainActivity.this.lastHandled.iterator().next());
                }
            }
            MainActivity.this.handledThisLaunch = true;
            MainActivity.this.saveHandled();
        }

        @JavascriptInterface
        public void widgetSummary(String str) {
            try {
                SharedPreferences.Editor edit = MainActivity.this.getSharedPreferences("htfu-widget", 0).edit();
                if (str == null) {
                    str = "";
                }
                edit.putString("summary", str).apply();
                TodayWidget.updateAll(MainActivity.this);
                TodayWidget.scheduleMidnight(MainActivity.this);
            } catch (Exception e) {
                Log.e(MainActivity.TAG, "widget summary failed", e);
            }
        }

        @JavascriptInterface
        public void keepScreen(final boolean z) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ExportBridge.this.lambda$keepScreen$3(z);
                }
            });
        }

        public /* synthetic */ void lambda$keepScreen$3(boolean z) {
            MainActivity.this.sessionActive = z;
            if (z) {
                MainActivity.this.getWindow().addFlags(128);
            } else {
                MainActivity.this.getWindow().clearFlags(128);
            }
        }

        @JavascriptInterface
        public boolean systemDark() {
            try {
                return (MainActivity.this.getResources().getConfiguration().uiMode & 48) == 32;
            } catch (Throwable th) {
                return true;
            }
        }

        @JavascriptInterface
        public void pickRestore() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ExportBridge.this.lambda$pickRestore$4();
                }
            });
        }

        public /* synthetic */ void lambda$pickRestore$4() {
            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
            intent.addCategory("android.intent.category.OPENABLE");
            intent.setType("*/*");
            intent.putExtra("android.intent.extra.MIME_TYPES", new String[]{"application/json", "application/octet-stream", "text/plain"});
            MainActivity.this.startActivityForResult(intent, MainActivity.RESTORE_CODE);
        }

        @JavascriptInterface
        public boolean repSensorAvailable(String str) {
            return MainActivity.this.repSensor != null && MainActivity.this.repSensor.available(str);
        }

        public /* synthetic */ void lambda$startRepSensor$5(String str) {
            if (MainActivity.this.repSensor != null) {
                MainActivity.this.repSensor.start(str);
            }
        }

        @JavascriptInterface
        public void startRepSensor(final String str) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ExportBridge.this.lambda$startRepSensor$5(str);
                }
            });
        }

        public /* synthetic */ void lambda$stopRepSensor$6() {
            if (MainActivity.this.repSensor != null) {
                MainActivity.this.repSensor.stop();
            }
        }

        @JavascriptInterface
        public void stopRepSensor() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ExportBridge.this.lambda$stopRepSensor$6();
                }
            });
        }
    }

    /* loaded from: classes.dex */
    public class ReminderBridge {
        private ReminderBridge() {
        }

        @JavascriptInterface
        public void remember(String str, String str2, boolean z, String str3, String str4, int i, String str5) {
            String str6 = "shift";
            try {
                int[] parseTime = MainActivity.parseTime(str2);
                SharedPreferences.Editor putBoolean = MainActivity.this.getSharedPreferences("htfu", 0).edit().putBoolean("enabled", z);
                if (str == null) {
                    str = "";
                }
                SharedPreferences.Editor putString = putBoolean.putString("days", str);
                if (!"shift".equals(str3)) {
                    str6 = "week";
                }
                SharedPreferences.Editor putString2 = putString.putString("mode", str6);
                if (str4 == null) {
                    str4 = "";
                }
                SharedPreferences.Editor putInt = putString2.putString("shiftStart", str4).putInt("shiftLen", Math.max(1, i));
                if (str5 == null) {
                    str5 = "";
                }
                putInt.putString("shiftOn", str5).putInt("hour", parseTime[0]).putInt("minute", parseTime[1]).apply();
                ReminderScheduler.scheduleNext(MainActivity.this);
                MainActivity.this.lambda$new$0();
            } catch (Throwable th) {
                Log.e(MainActivity.TAG, "remember failed", th);
            }
        }

        @JavascriptInterface
        public void update(final String str, final String str2, final boolean z) {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ReminderBridge.this.lambda$update$0(z, str, str2);
                }
            });
        }

        public /* synthetic */ void lambda$update$0(boolean z, String str, String str2) {
            try {
                if (!z) {
                    MainActivity.this.setRemindersEnabled(false, null, null);
                    MainActivity.this.lambda$new$0();
                } else if (MainActivity.this.ensureNotifyPermission(true)) {
                    MainActivity.this.setRemindersEnabled(true, str, str2);
                    MainActivity.this.lambda$tellPage$15(true);
                    MainActivity.this.lambda$new$0();
                }
            } catch (Throwable th) {
                Log.e(MainActivity.TAG, "update failed", th);
            }
        }

        @JavascriptInterface
        public void reportNext() {
            MainActivity.this.lambda$new$0();
        }

        @JavascriptInterface
        public void testNow() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ReminderBridge.this.lambda$testNow$1();
                }
            });
        }

        public /* synthetic */ void lambda$testNow$1() {
            try {
                if (MainActivity.this.ensureNotifyPermission(false)) {
                    ReminderScheduler.showNow(MainActivity.this);
                }
            } catch (Throwable th) {
                Log.e(MainActivity.TAG, "testNow failed", th);
            }
        }

        @JavascriptInterface
        public void inOneMinute() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ReminderBridge.this.lambda$inOneMinute$2();
                }
            });
        }

        public /* synthetic */ void lambda$inOneMinute$2() {
            try {
                if (MainActivity.this.ensureNotifyPermission(false)) {
                    ReminderScheduler.scheduleIn(MainActivity.this, 60000L);
                }
            } catch (Throwable th) {
                Log.e(MainActivity.TAG, "inOneMinute failed", th);
            }
        }

        @JavascriptInterface
        public void openAlarmSettings() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.ReminderBridge.this.lambda$openAlarmSettings$3();
                }
            });
        }

        public /* synthetic */ void lambda$openAlarmSettings$3() {
            try {
                MainActivity.this.ensureExactAlarm();
            } catch (Throwable th) {
                Log.e(MainActivity.TAG, "openAlarmSettings failed", th);
            }
        }

        @JavascriptInterface
        public boolean exactAlarmsAllowed() {
            return MainActivity.this.canExactAlarm();
        }

        /** 2.14: "granted", "ask" (can show the system dialog) or "denied" (blocked; only Settings can change it). */
        @JavascriptInterface
        public String notifyState() {
            try {
                if (RestTimer.notifyAllowed(MainActivity.this)) return "granted";
                if (Build.VERSION.SDK_INT < 33) return "denied";
                boolean asked = MainActivity.this.getSharedPreferences("htfu", 0).getBoolean("notifyAsked214", false);
                if (!asked || MainActivity.this.shouldShowRequestPermissionRationale("android.permission.POST_NOTIFICATIONS")) return "ask";
                return "denied";
            } catch (Throwable t) {
                return "granted";
            }
        }

        /** 2.14: shows the system notification dialog once (rest timer); the answer comes back via onNotifyPermission214. */
        @JavascriptInterface
        public void askNotify() {
            try {
                MainActivity.this.getSharedPreferences("htfu", 0).edit().putBoolean("notifyAsked214", true).apply();
                if (MainActivity.this.ensureNotifyPermission(false)) MainActivity.this.tellNotify214(true);
            } catch (Throwable t) {
                Log.e(MainActivity.TAG, "askNotify failed", t);
            }
        }

        /** 2.14: opens this app's notification settings (used when the permission is blocked). */
        @JavascriptInterface
        public void openNotifySettings() {
            try {
                Intent i = new Intent("android.settings.APP_NOTIFICATION_SETTINGS");
                i.putExtra("android.provider.extra.APP_PACKAGE", MainActivity.this.getPackageName());
                MainActivity.this.startActivity(i);
            } catch (Throwable t) {
                try {
                    MainActivity.this.startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.parse("package:" + MainActivity.this.getPackageName())));
                } catch (Throwable t2) {
                    Log.e(MainActivity.TAG, "notification settings unavailable", t2);
                }
            }
        }

        @JavascriptInterface
        public void setExtraDays(String str) {
            try {
                SharedPreferences.Editor edit = MainActivity.this.getSharedPreferences("htfu", 0).edit();
                if (str == null) {
                    str = "";
                }
                edit.putString("extraDays", str).apply();
                ReminderScheduler.scheduleNext(MainActivity.this);
                MainActivity.this.lambda$new$0();
            } catch (Throwable th) {
                Log.e(MainActivity.TAG, "setExtraDays failed", th);
            }
        }
    }

    public boolean ensureNotifyPermission(boolean z) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
                if (z) {
                    this.pendingEnable = true;
                }
                if (!this.permissionAsked) {
                    this.permissionAsked = true;
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override // java.lang.Runnable
                        public final void run() {
                            MainActivity.this.lambda$ensureNotifyPermission$16();
                        }
                    }, 300L);
                }
                return false;
            }
            return true;
        } catch (Throwable th) {
            Log.e(TAG, "ensureNotifyPermission failed", th);
            return false;
        }
    }

    public /* synthetic */ void lambda$ensureNotifyPermission$16() {
        try {
            if (isFinishing()) {
                this.permissionAsked = false;
            } else {
                getSharedPreferences("htfu", 0).edit().putBoolean("notifyAsked214", true).apply();
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIFY_CODE);
            }
        } catch (Throwable th) {
            this.permissionAsked = false;
            Log.e(TAG, "request permission failed", th);
        }
    }

    public boolean canExactAlarm() {
        AlarmManager alarmManager;
        try {
            if (Build.VERSION.SDK_INT >= 31 && (alarmManager = (AlarmManager) getSystemService("alarm")) != null) {
                return alarmManager.canScheduleExactAlarms();
            }
            return true;
        } catch (Throwable th) {
            Log.e(TAG, "canExactAlarm failed", th);
            return true;
        }
    }

    public void lambda$tellExactAlarm$17(final boolean z) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.lambda$tellExactAlarm$17(z);
                }
            });
            return;
        }
        try {
            WebView webView = this.web;
            if (webView == null) {
                return;
            }
            webView.evaluateJavascript("window.onExactAlarm && window.onExactAlarm(" + (z ? "true" : "false") + ")", null);
        } catch (Throwable th) {
            Log.e(TAG, "tellExactAlarm failed", th);
        }
    }

    public void ensureExactAlarm() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.this.ensureExactAlarm();
                }
            });
            return;
        }
        try {
            if (canExactAlarm()) {
                Toast.makeText(this, "Exact alarms are already allowed for HTFU.", 0).show();
                lambda$tellExactAlarm$17(true);
                return;
            }
            Uri parse = Uri.parse("package:" + getPackageName());
            try {
                startActivity(new Intent("android.settings.REQUEST_SCHEDULE_EXACT_ALARM", parse));
            } catch (Throwable th) {
                Log.w(TAG, "exact alarm settings not available", th);
                try {
                    startActivity(new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", parse));
                    Toast.makeText(this, "Open \"Alarms & reminders\" and allow it for HTFU.", 1).show();
                } catch (Throwable th2) {
                    Log.w(TAG, "app settings not available", th2);
                    Toast.makeText(this, "Open Settings > Apps > HTFU > Alarms & reminders and allow it.", 1).show();
                }
            }
        } catch (Throwable th3) {
            Log.e(TAG, "open alarm settings failed", th3);
        }
    }

    /** 2.17: ask the page to read the notification action queue. Returns false when there is no page. */
    boolean deliverSessionAction() {
        final WebView w = this.web;
        if (w == null || isFinishing()) return false;
        runOnUiThread(() -> {
            try { w.evaluateJavascript("window.onSessionAction217 && window.onSessionAction217()", null); }
            catch (Exception e) { Log.e(TAG, "session action delivery failed", e); }
        });
        return true;
    }

    @Override // android.app.Activity
    protected void onResume() {
        super.onResume();
        try {
            boolean allowed = canExactAlarm();
            Boolean last = this.lastExactAllowed;
            if (last == null ? allowed : last.booleanValue() != allowed) {
                ReminderScheduler.scheduleNext(this);
            }
            this.lastExactAllowed = Boolean.valueOf(allowed);
            lambda$tellExactAlarm$17(allowed);
            if (this.pageReady) {
                lambda$new$0();
            }
        } catch (Throwable th) {
            Log.e(TAG, "onResume reminder check failed", th);
        }
        // 2.17: apply notification taps (Set done / Skip rest) made while the app was in the background
        if (this.pageReady) deliverSessionAction();
    }

    /* loaded from: classes.dex */
    public class RepSensor implements SensorEventListener {
        private long lastRep;
        private boolean motionArmed;
        private SensorManager sm;
        private double smooth;
        private boolean smoothInit;
        private boolean wasNear;

        private RepSensor() {
        }

        boolean available(String str) {
            int i;
            SensorManager sensorManager = (SensorManager) MainActivity.this.getSystemService("sensor");
            if (sensorManager == null) {
                return false;
            }
            if ("proximity".equals(str)) {
                i = 8;
            } else {
                i = "motion".equals(str) ? 1 : -1;
            }
            return i >= 0 && sensorManager.getDefaultSensor(i) != null;
        }

        void start(String str) {
            int i;
            stop();
            SensorManager sensorManager = (SensorManager) MainActivity.this.getSystemService("sensor");
            this.sm = sensorManager;
            if (sensorManager == null) {
                return;
            }
            if ("proximity".equals(str)) {
                i = 8;
            } else {
                i = "motion".equals(str) ? 1 : -1;
            }
            Sensor defaultSensor = i >= 0 ? this.sm.getDefaultSensor(i) : null;
            if (defaultSensor == null) {
                return;
            }
            this.wasNear = true;
            this.motionArmed = false;
            this.smoothInit = false;
            this.smooth = 0.0d;
            this.sm.registerListener(this, defaultSensor, 1);
        }

        void stop() {
            SensorManager sensorManager = this.sm;
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
                this.sm = null;
            }
        }

        @Override // android.hardware.SensorEventListener
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.sensor.getType() == 8) {
                boolean z = sensorEvent.values[0] < Math.min(sensorEvent.sensor.getMaximumRange(), 5.0f);
                if (z && !this.wasNear && System.currentTimeMillis() - this.lastRep >= 500) {
                    this.lastRep = System.currentTimeMillis();
                    fireRep();
                }
                this.wasNear = z;
                return;
            }
            if (sensorEvent.sensor.getType() == 1) {
                double sqrt = Math.sqrt((sensorEvent.values[0] * sensorEvent.values[0]) + (sensorEvent.values[1] * sensorEvent.values[1]) + (sensorEvent.values[2] * sensorEvent.values[2]));
                if (this.smoothInit) {
                    this.smooth = (this.smooth * 0.8d) + (sqrt * 0.2d);
                } else {
                    this.smooth = sqrt;
                    this.smoothInit = true;
                }
                double d = this.smooth;
                if (d >= 8.01d) {
                    if (this.motionArmed && d > 11.610000000000001d && System.currentTimeMillis() - this.lastRep >= 600) {
                        this.motionArmed = false;
                        this.lastRep = System.currentTimeMillis();
                        fireRep();
                        return;
                    }
                    return;
                }
                this.motionArmed = true;
            }
        }

        @Override // android.hardware.SensorEventListener
        public void onAccuracyChanged(Sensor sensor, int i) {
        }

        private void fireRep() {
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    MainActivity.RepSensor.this.lambda$fireRep$0();
                }
            });
        }

        public /* synthetic */ void lambda$fireRep$0() {
            if (MainActivity.this.web != null) {
                MainActivity.this.web.evaluateJavascript("window.onSensorRep&&window.onSensorRep()", null);
            }
        }
    }

    /* ---- 2.12 progress photos: choose from the phone or take one with the camera ---- */

    /** Always answers the pending WebView callback exactly once, so the next tap works (cancel = null). */
    void finishFileChooser(Uri[] result) {
        ValueCallback<Uri[]> cb = this.fileCallback;
        this.fileCallback = null;
        if (cb != null) {
            try {
                cb.onReceiveValue(result);
            } catch (Exception e) {
                Log.e(TAG, "file chooser callback failed", e);
            }
        }
    }

    boolean openFileChooser(ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params) {
        finishFileChooser(null);
        this.fileCallback = callback;
        boolean capture = false;
        try {
            capture = params != null && params.isCaptureEnabled();
        } catch (Exception e) {
            capture = false;
        }
        if (capture) {
            if (!startCamera()) {
                finishFileChooser(null);
            }
            return true;
        }
        if (!startPicker()) {
            Toast.makeText(this, "No app found to choose a photo.", 1).show();
            finishFileChooser(null);
        }
        return true;
    }

    private boolean startPicker() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                Intent pick = new Intent(MediaStore.ACTION_PICK_IMAGES);
                pick.setType("image/*");
                startActivityForResult(pick, PHOTO_CODE);
                return true;
            } catch (ActivityNotFoundException e) {
                Log.w(TAG, "photo picker missing, using GET_CONTENT");
            } catch (Exception e) {
                Log.w(TAG, "photo picker failed, using GET_CONTENT", e);
            }
        }
        try {
            Intent get = new Intent(Intent.ACTION_GET_CONTENT);
            get.addCategory(Intent.CATEGORY_OPENABLE);
            get.setType("image/*");
            startActivityForResult(Intent.createChooser(get, "Choose a photo"), PHOTO_CODE);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "no picker", e);
            return false;
        }
    }

    private boolean startCamera() {
        try {
            File dir = new File(getCacheDir(), "exports/photos");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new IllegalStateException("no photo dir");
            }
            File[] old = dir.listFiles();
            if (old != null) {
                for (File f : old) {
                    f.delete();
                }
            }
            String name = "cam-" + System.currentTimeMillis() + ".jpg";
            new File(dir, name).createNewFile();
            Uri out = Uri.parse("content://" + ExportProvider.AUTHORITY + "/photos/" + name);
            this.cameraUri = out;
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, out);
            cam.setClipData(ClipData.newRawUri("photo", out));
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(cam, CAMERA_CODE);
            return true;
        } catch (ActivityNotFoundException e) {
            this.cameraUri = null;
            Toast.makeText(this, "No camera app found. Use Choose photo instead.", 1).show();
            return false;
        } catch (SecurityException e) {
            this.cameraUri = null;
            Log.e(TAG, "camera blocked", e);
            Toast.makeText(this, "The camera could not be opened. Use Choose photo instead.", 1).show();
            return false;
        } catch (Exception e) {
            this.cameraUri = null;
            Log.e(TAG, "camera failed", e);
            Toast.makeText(this, "The camera could not be opened. Use Choose photo instead.", 1).show();
            return false;
        }
    }

    private void onPhotoResult(int requestCode, int resultCode, Intent intent) {
        Uri[] result = null;
        try {
            if (resultCode == -1) {
                if (requestCode == CAMERA_CODE) {
                    Uri out = this.cameraUri;
                    File f = out == null ? null : ExportProvider.photoFile(this, out);
                    if (f != null && f.isFile() && f.length() > 0) {
                        result = new Uri[]{out};
                    } else {
                        Toast.makeText(this, "The camera did not return a photo.", 0).show();
                    }
                } else if (intent != null) {
                    ClipData clip = intent.getClipData();
                    if (clip != null && clip.getItemCount() > 0 && clip.getItemAt(0).getUri() != null) {
                        result = new Uri[]{clip.getItemAt(0).getUri()};
                    } else if (intent.getData() != null) {
                        result = new Uri[]{intent.getData()};
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "photo result failed", e);
            result = null;
        }
        if (requestCode == CAMERA_CODE) {
            this.cameraUri = null;
        }
        if (this.fileCallback == null && requestCode == CAMERA_CODE && result != null) {
            // 2.14: the activity was recreated while the camera was open, so the WebView's callback is gone.
            // Keep the photo and hand it to the page when it has loaded.
            this.pendingPhotoUri = result[0];
            if (this.pageReady) deliverPendingPhoto();
            return;
        }
        finishFileChooser(result);
    }

    /** 2.14: sends a recovered camera photo to the page as a data URL (window.onPendingPhoto214). */
    void deliverPendingPhoto() {
        final Uri uri = this.pendingPhotoUri;
        if (uri == null || this.web == null) return;
        this.pendingPhotoUri = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File f = ExportProvider.photoFile(MainActivity.this, uri);
                    if (f == null || !f.isFile() || f.length() <= 0 || f.length() > MAX_PENDING_PHOTO) return;
                    byte[] bytes = new byte[(int) f.length()];
                    FileInputStream in = new FileInputStream(f);
                    try {
                        int off = 0;
                        while (off < bytes.length) {
                            int n = in.read(bytes, off, bytes.length - off);
                            if (n <= 0) break;
                            off += n;
                        }
                    } finally {
                        in.close();
                    }
                    final String url = "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (MainActivity.this.web != null) {
                                MainActivity.this.web.evaluateJavascript("window.onPendingPhoto214 && window.onPendingPhoto214(" + JSONObject.quote(url) + ")", null);
                            }
                        }
                    });
                } catch (Throwable t) {
                    Log.e(TAG, "pending photo failed", t);
                }
            }
        }).start();
    }

    /** 2.14: tells the page whether notifications are allowed after the permission dialog. */
    void tellNotify214(final boolean granted) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.tellNotify214(granted);
                }
            });
            return;
        }
        try {
            WebView webView = this.web;
            if (webView == null) {
                return;
            }
            webView.evaluateJavascript("window.onNotifyPermission214 && window.onNotifyPermission214(" + (granted ? "true" : "false") + ")", null);
        } catch (Throwable th) {
            Log.e(TAG, "tellNotify214 failed", th);
        }
    }

    /** UTF-8 byte length of a string (2.14: backup limits are bytes everywhere). */
    static int utf8Length(String str) {
        if (str == null) {
            return 0;
        }
        try {
            return str.getBytes("UTF-8").length;
        } catch (Exception e) {
            return str.length();
        }
    }
}
