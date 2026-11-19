// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import android.webkit.JavascriptInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTFU 2.15: spoken workout cues (Couch to 5K run/walk switches, halfway, done;
 * beep test levels) with the phone's own TextToSpeech engine. No INTERNET
 * permission: synthesis runs in the TTS engine, and an offline voice is preferred.
 *
 * Cues are scheduled the same way as the rest timer: one AlarmManager alarm per
 * cue (setAlarmClock when exact alarms are allowed, setAndAllowWhileIdle
 * otherwise). VoiceCueReceiver speaks the text, so cues fire with the screen off
 * and the app in the background. The page never speaks a scheduled cue itself
 * when this bridge exists, so nothing is said twice.
 */
public final class VoiceCues {
    static final String TAG = "HTFU-voice";
    static final String ACTION = "app.htfu.VOICE_CUE";
    static final String PREFS = "htfu-voice";
    static final int MAX_CUES = 64;

    private static TextToSpeech tts;
    private static boolean ready;
    private static boolean failed;
    private static final List<String[]> pending = new ArrayList<>();
    private static final Map<String, Runnable> doneCallbacks = new HashMap<>();
    private static int seq;
    private static final Handler main = new Handler(Looper.getMainLooper());

    private VoiceCues() {}

    static int base(String tag) {
        if ("beep".equals(tag)) return 5400;
        if ("c25".equals(tag)) return 5300;
        return 5500;
    }

    static String cleanTag(String tag) {
        return tag == null ? "x" : tag.replaceAll("[^a-z0-9]", "");
    }

    static synchronized void init(Context ctx) {
        if (tts != null || failed) return;
        final Context app = ctx.getApplicationContext();
        tts = new TextToSpeech(app, status -> {
            synchronized (VoiceCues.class) {
                if (status != TextToSpeech.SUCCESS || tts == null) {
                    failed = true;
                    Log.w(TAG, "TTS init failed: " + status);
                    for (String[] p : pending) runDone(p[1]);
                    pending.clear();
                    return;
                }
                try {
                    Locale loc = Locale.getDefault();
                    int r = tts.setLanguage(loc);
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US);
                    if (Build.VERSION.SDK_INT >= 21) {
                        Voice cur = tts.getVoice();
                        if (cur != null && cur.isNetworkConnectionRequired() && tts.getVoices() != null) {
                            for (Voice v : tts.getVoices()) {
                                if (!v.isNetworkConnectionRequired() && v.getLocale() != null
                                        && v.getLocale().getLanguage().equals(cur.getLocale().getLanguage())) { tts.setVoice(v); break; }
                            }
                        }
                        tts.setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
                    }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) { runDone(id); }
                        @Override public void onError(String id) { runDone(id); }
                    });
                } catch (Exception e) {
                    Log.w(TAG, "TTS setup", e);
                }
                ready = true;
                for (String[] p : pending) say(p[0], p[1]);
                pending.clear();
            }
        });
    }

    private static void runDone(String id) {
        Runnable r;
        synchronized (VoiceCues.class) { r = doneCallbacks.remove(id); }
        if (r != null) main.post(r);
    }

    private static void say(String text, String id) {
        try {
            Bundle b = new Bundle();
            b.putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false");
            int r = tts.speak(text, TextToSpeech.QUEUE_ADD, b, id);
            if (r != TextToSpeech.SUCCESS) runDone(id);
        } catch (Exception e) {
            Log.w(TAG, "speak", e);
            runDone(id);
        }
    }

    /** Speaks now. onDone (may be null) runs on the main thread when finished or failed. */
    static synchronized void speak(Context ctx, String text, Runnable onDone) {
        String id = "htfu" + (++seq);
        if (onDone != null) doneCallbacks.put(id, onDone);
        if (text == null || text.trim().isEmpty()) { runDone(id); return; }
        init(ctx);
        if (failed) { runDone(id); return; }
        if (!ready) { pending.add(new String[] {text, id}); return; }
        say(text, id);
    }

    static PendingIntent cueIntent(Context ctx, String tag, int i, String text, boolean create) {
        Intent it = new Intent(ctx, VoiceCueReceiver.class);
        it.setAction(ACTION);
        it.putExtra("tag", tag);
        it.putExtra("i", i);
        if (text != null) it.putExtra("text", text);
        int flags = PendingIntent.FLAG_IMMUTABLE | (create ? PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_NO_CREATE);
        return PendingIntent.getBroadcast(ctx, base(tag) + i, it, flags);
    }

    static void cancel(Context ctx, String tag) {
        tag = cleanTag(tag);
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, 0);
            int n = Math.min(MAX_CUES, sp.getInt("n_" + tag, MAX_CUES));
            for (int i = 0; i < n; i++) {
                PendingIntent pi = cueIntent(ctx, tag, i, null, false);
                if (pi != null) { if (am != null) am.cancel(pi); pi.cancel(); }
            }
            sp.edit().putInt("n_" + tag, 0).apply();
        } catch (Exception e) {
            Log.w(TAG, "cancel", e);
        }
    }

    static int schedule(Context ctx, String tag, String json) {
        tag = cleanTag(tag);
        cancel(ctx, tag);
        int count = 0;
        try {
            main.post(() -> init(ctx));
            JSONArray a = new JSONArray(json);
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return 0;
            boolean exact = RestTimer.exactAllowed(ctx);
            long now = System.currentTimeMillis();
            int n = Math.min(MAX_CUES, a.length());
            ctx.getSharedPreferences(PREFS, 0).edit().putInt("n_" + tag, n).apply();
            for (int i = 0; i < n; i++) {
                JSONObject o = a.getJSONObject(i);
                long at = o.optLong("at", 0);
                String text = o.optString("text", "");
                if (at < now - 2000 || text.isEmpty()) continue;
                PendingIntent pi = cueIntent(ctx, tag, i, text, true);
                boolean set = false;
                if (exact) {
                    try {
                        am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, RestTimer.openApp(ctx)), pi);
                        set = true;
                    } catch (SecurityException se) {
                        Log.w(TAG, "exact cue refused, using inexact", se);
                    }
                }
                if (!set) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                count++;
            }
        } catch (Exception e) {
            Log.w(TAG, "schedule", e);
        }
        return count;
    }

    static final class Bridge {
        private final Context ctx;
        Bridge(Context ctx) { this.ctx = ctx.getApplicationContext(); }

        @JavascriptInterface public void speak(String text) { main.post(() -> VoiceCues.speak(ctx, text, null)); }
        @JavascriptInterface public boolean available() { return !failed; }
        @JavascriptInterface public boolean exactAllowed() { return RestTimer.exactAllowed(ctx); }
        @JavascriptInterface public int schedule(String tag, String json) { return VoiceCues.schedule(ctx, tag, json); }
        @JavascriptInterface public void cancel(String tag) { VoiceCues.cancel(ctx, tag); }
    }
}
