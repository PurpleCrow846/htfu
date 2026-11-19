// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

/**
 * HTFU 2.17: lock-screen workout notification.
 *
 * - The page calls update(json) whenever the active session changes (set or rest) and clear() when the
 *   session ends, is discarded, or a backup is restored.
 * - The notification shows the exercise, set X of Y, the target, and during a rest a live countdown
 *   (chronometer counting down). Actions: Set done, Skip rest (rest only), Open.
 * - Actions go to SessionActionReceiver (not exported). Each tap is queued in SharedPreferences with a
 *   unique id and the token of the set it was shown for. If the app is open, the page is told to read the
 *   queue at once; otherwise it reads it on the next open. The page applies an action only when its token
 *   still matches the current set, and remembers the last id it applied, so a tap is never lost or
 *   applied twice.
 * - No foreground service and no new permission (uses POST_NOTIFICATIONS, already requested).
 * - While this notification is showing, RestTimer keeps its alarm and its "Rest over" alert but does not
 *   post its own countdown, so there is only one countdown on the lock screen.
 */
public final class SessionNotify {
    static final String CHANNEL = "workout_session";
    static final String ACTION = "app.htfu.SESSION_ACTION";
    static final int ID = 4217;
    static final int REQ_DONE = 4218;
    static final int REQ_SKIP = 4219;
    static final int REQ_OPEN = 4220;
    static final String PREFS = "htfu-session";
    static final int MAX_QUEUE = 20;

    /** The running activity, if any; set by MainActivity. Used to hand an action to the open page at once. */
    static volatile WeakReference<MainActivity> live = new WeakReference<>(null);

    private SessionNotify() {}

    static SharedPreferences prefs(Context ctx) { return ctx.getSharedPreferences(PREFS, 0); }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel c = new NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW);
        c.setDescription("Current set and rest countdown on the lock screen, with Set done and Skip rest buttons");
        c.setShowBadge(false);
        c.setSound(null, null);
        c.enableVibration(false);
        c.enableLights(false);
        c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(c);
    }

    /** True while the page has a session notification up (RestTimer then skips its own countdown). */
    static boolean active(Context ctx) {
        try { return prefs(ctx).getString("payload", null) != null; } catch (Exception e) { return false; }
    }

    static synchronized void update(Context ctx, String json) {
        try {
            JSONObject o = new JSONObject(json);
            prefs(ctx).edit().putString("payload", o.toString()).remove("logged").apply();
            post(ctx, o, false);
            // one countdown only: drop RestTimer's own countdown if it is up (its alarm stays)
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(RestTimer.ID_COUNT);
        } catch (Exception e) {
            android.util.Log.e("HTFU", "session notify update failed", e);
        }
    }

    static synchronized void clear(Context ctx) {
        try {
            prefs(ctx).edit().remove("payload").remove("logged").apply();
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(ID);
        } catch (Exception e) {
            android.util.Log.e("HTFU", "session notify clear failed", e);
        }
    }

    @SuppressWarnings("deprecation")
    static Notification.Builder builder(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(ctx, CHANNEL);
        Notification.Builder b = new Notification.Builder(ctx);
        b.setPriority(Notification.PRIORITY_LOW);
        b.setDefaults(0);
        b.setSound(null);
        b.setVibrate(null);
        return b;
    }

    static PendingIntent actionIntent(Context ctx, String act, String token, int req) {
        Intent i = new Intent(ctx, SessionActionReceiver.class);
        i.setAction(ACTION);
        i.putExtra("act", act);
        i.putExtra("token", token == null ? "" : token);
        return PendingIntent.getBroadcast(ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openIntent(Context ctx) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, REQ_OPEN, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Payload keys (all optional except token): phase "work" | "rest", title, text, plan, endAt (ms, rest
     * end), canDone (boolean), token. "logged" = the user tapped Set done while the app was closed; show a
     * short "logged" state with only Open until the page takes over.
     */
    @SuppressWarnings("deprecation")
    static void post(Context ctx, JSONObject o, boolean logged) {
        ensureChannel(ctx);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        String token = o.optString("token", "");
        String title = o.optString("title", "Workout");
        String text = o.optString("text", "");
        String plan = o.optString("plan", "");
        long endAt = o.optLong("endAt", 0L);
        boolean rest = "rest".equals(o.optString("phase")) && endAt > System.currentTimeMillis();
        boolean canDone = o.optBoolean("canDone", false);
        Notification.Builder b = builder(ctx)
            .setSmallIcon(R.drawable.ic_stat)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(rest ? Notification.CATEGORY_STOPWATCH : Notification.CATEGORY_PROGRESS)
            .setContentIntent(openIntent(ctx));
        if (plan.length() > 0) b.setSubText(plan);
        if (logged) {
            b.setContentTitle("Set logged")
             .setContentText("Open HTFU for your next set.")
             .setShowWhen(false);
        } else {
            b.setContentTitle(rest ? "Rest · next: " + title : title).setContentText(text);
            if (rest) {
                b.setWhen(endAt).setShowWhen(true).setUsesChronometer(true);
                if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
            } else {
                b.setShowWhen(false);
            }
            if (canDone) b.addAction(new Notification.Action.Builder(R.drawable.ic_stat, "Set done", actionIntent(ctx, "done", token, REQ_DONE)).build());
            if (rest) b.addAction(new Notification.Action.Builder(R.drawable.ic_stat, "Skip rest", actionIntent(ctx, "skip", token, REQ_SKIP)).build());
        }
        b.addAction(new Notification.Action.Builder(R.drawable.ic_stat, "Open", openIntent(ctx)).build());
        nm.notify(ID, b.build());
    }

    /** Called by SessionActionReceiver for a notification button. */
    static synchronized void onAction(Context ctx, String act, String token) {
        try {
            if (!"done".equals(act) && !"skip".equals(act)) return;
            SharedPreferences p = prefs(ctx);
            String payload = p.getString("payload", null);
            if (payload == null) return; // session already ended
            JSONObject o = new JSONObject(payload);
            if (token == null || !token.equals(o.optString("token"))) return; // tap on an out-of-date notification
            if (p.getBoolean("logged", false)) return; // already logged this set; waiting for the page
            JSONArray q = new JSONArray(p.getString("queue", "[]"));
            for (int i = 0; i < q.length(); i++) {
                JSONObject x = q.optJSONObject(i);
                if (x != null && act.equals(x.optString("act")) && token.equals(x.optString("token"))) return; // double tap
            }
            long id = p.getLong("seq", 0L) + 1;
            JSONObject e = new JSONObject();
            e.put("id", id);
            e.put("act", act);
            e.put("token", token);
            e.put("ts", System.currentTimeMillis());
            JSONArray out = new JSONArray();
            int start = Math.max(0, q.length() - (MAX_QUEUE - 1));
            for (int i = start; i < q.length(); i++) out.put(q.get(i));
            out.put(e);
            SharedPreferences.Editor ed = p.edit().putLong("seq", id).putString("queue", out.toString());
            boolean wasRest = "rest".equals(o.optString("phase"));
            // the rest is over either way: stop its alarm (the page restarts it for the next rest)
            if (wasRest) RestTimer.cancel(ctx);
            MainActivity a = live.get();
            if (a != null && a.deliverSessionAction()) { ed.apply(); return; } // the open page applies it and updates us
            if ("skip".equals(act)) {
                o.put("phase", "work");
                o.put("endAt", 0L);
                ed.putString("payload", o.toString());
                ed.apply();
                post(ctx, o, false);
            } else {
                ed.putBoolean("logged", true);
                ed.apply();
                post(ctx, o, true);
            }
        } catch (Exception e) {
            android.util.Log.e("HTFU", "session action failed", e);
        }
    }

    /** RestTimer's alarm fired: turn the countdown into the "set" view (the Rest over alert is separate). */
    static synchronized void restOver(Context ctx) {
        try {
            SharedPreferences p = prefs(ctx);
            String payload = p.getString("payload", null);
            if (payload == null || p.getBoolean("logged", false)) return;
            JSONObject o = new JSONObject(payload);
            if (!"rest".equals(o.optString("phase"))) return;
            o.put("phase", "work");
            o.put("endAt", 0L);
            p.edit().putString("payload", o.toString()).apply();
            post(ctx, o, false);
        } catch (Exception e) {
            android.util.Log.e("HTFU", "session rest over failed", e);
        }
    }

    static synchronized String peek(Context ctx) {
        try { return prefs(ctx).getString("queue", "[]"); } catch (Exception e) { return "[]"; }
    }

    /** The page applied (or dropped) every queued action up to this id. */
    static synchronized void ack(Context ctx, long upTo) {
        try {
            SharedPreferences p = prefs(ctx);
            JSONArray q = new JSONArray(p.getString("queue", "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < q.length(); i++) {
                JSONObject x = q.optJSONObject(i);
                if (x != null && x.optLong("id") > upTo) out.put(x);
            }
            p.edit().putString("queue", out.toString()).apply();
        } catch (Exception e) {
            android.util.Log.e("HTFU", "session ack failed", e);
        }
    }

    /** JavaScript bridge, registered in MainActivity as "AndroidSession". */
    public static final class Bridge {
        private final Context ctx;
        public Bridge(Context ctx) { this.ctx = ctx.getApplicationContext(); }

        @JavascriptInterface public boolean available() { return true; }
        @JavascriptInterface public void update(String json) { SessionNotify.update(ctx, json); }
        @JavascriptInterface public void clear() { SessionNotify.clear(ctx); }
        @JavascriptInterface public String peek() { return SessionNotify.peek(ctx); }
        @JavascriptInterface public void ack(String upTo) {
            long n;
            try { n = Long.parseLong(upTo.trim()); } catch (Exception e) { return; }
            SessionNotify.ack(ctx, n);
        }
    }
}
