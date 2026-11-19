// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;

/**
 * HTFU 2.07: rest countdown that keeps running when the app is in the
 * background or the screen is locked.
 *
 * - start() posts an ongoing notification whose chronometer counts down to
 *   the end of the rest, visible on the lock screen.
 * - It also sets an alarm at the end time. RestDoneReceiver then replaces the
 *   countdown with a "Rest over" alert (sound and vibration).
 * - cancel() removes both. The web page calls it on Skip, at the end of the
 *   countdown when the app is open, and when a workout ends or is discarded.
 */
public final class RestTimer {
    static final String CHANNEL_COUNT = "rest_countdown";
    static final String CHANNEL_DONE = "rest_done";
    static final String ACTION_DONE = "app.htfu.REST_DONE";
    static final int ID_COUNT = 4207;
    static final int ID_DONE = 4208;
    static final int REQ_ALARM = 4207;
    static final int REQ_OPEN = 4209;
    static final String PREFS = "htfu-rest";

    private RestTimer() {}

    static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_COUNT) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL_COUNT, "Rest countdown", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Shows the time left in your rest between sets");
            c.setShowBadge(false);
            c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CHANNEL_DONE) == null) {
            NotificationChannel d = new NotificationChannel(CHANNEL_DONE, "Rest over", NotificationManager.IMPORTANCE_HIGH);
            d.setDescription("Tells you when the rest is over and the next set is up");
            d.enableVibration(true);
            d.setVibrationPattern(new long[] {0, 300, 150, 300});
            d.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(d);
        }
    }

    static PendingIntent openApp(Context ctx) {
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(ctx, REQ_OPEN, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent alarmIntent(Context ctx) {
        Intent i = new Intent(ctx, RestDoneReceiver.class);
        i.setAction(ACTION_DONE);
        return PendingIntent.getBroadcast(ctx, REQ_ALARM, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressWarnings("deprecation")
    static Notification.Builder builder(Context ctx, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(ctx, channel);
        return new Notification.Builder(ctx);
    }

    static void start(Context ctx, long endAt, String title, String text) {
        try {
            ensureChannels(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(ID_DONE);
                Notification.Builder b = builder(ctx, CHANNEL_COUNT)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title == null || title.isEmpty() ? "Resting" : title)
                    .setContentText(text == null ? "" : text)
                    .setWhen(endAt)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setCategory(Notification.CATEGORY_STOPWATCH)
                    .setVisibility(Notification.VISIBILITY_PUBLIC)
                    .setContentIntent(openApp(ctx));
                if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
                if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(Math.max(1000L, endAt - System.currentTimeMillis() + 1000L));
                // 2.17: the workout notification already shows this countdown; post ours only without it
                if (SessionNotify.active(ctx)) nm.cancel(ID_COUNT); else nm.notify(ID_COUNT, b.build());
            }
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                PendingIntent pi = alarmIntent(ctx);
                am.cancel(pi);
                // 2.14: setAlarmClock fires exactly and is not throttled in Doze. Like every exact API on
                // Android 12+ it needs "Alarms & reminders" (SCHEDULE_EXACT_ALARM), so check first. Without it
                // the alarm is inexact; the page tells the user once and links to the setting.
                boolean exact = exactAllowed(ctx);
                boolean set = false;
                if (exact) {
                    try {
                        am.setAlarmClock(new AlarmManager.AlarmClockInfo(endAt, openApp(ctx)), pi);
                        set = true;
                    } catch (SecurityException se) {
                        android.util.Log.w("HTFU", "exact rest alarm refused, using inexact", se);
                    }
                }
                if (!set) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi);
            }
            ctx.getSharedPreferences(PREFS, 0).edit()
                .putLong("endAt", endAt)
                .putString("text", text == null ? "" : text)
                .apply();
        } catch (Exception e) {
            android.util.Log.e("HTFU", "rest start failed", e);
        }
    }

    static boolean exactAllowed(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT < 31) return true;
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        } catch (Exception e) {
            return false;
        }
    }

    /** 2.14: true when rest notifications can be shown (runtime permission on 13+, and not blocked in Settings). */
    static boolean notifyAllowed(Context ctx) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) return false;
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            return nm == null || nm.areNotificationsEnabled();
        } catch (Exception e) {
            return true;
        }
    }

    static void cancel(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(alarmIntent(ctx));
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) { nm.cancel(ID_COUNT); nm.cancel(ID_DONE); }
            ctx.getSharedPreferences(PREFS, 0).edit().remove("endAt").apply();
        } catch (Exception e) {
            android.util.Log.e("HTFU", "rest cancel failed", e);
        }
    }

    /** Called by RestDoneReceiver when the alarm fires. */
    static void done(Context ctx) {
        try {
            ensureChannels(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            nm.cancel(ID_COUNT);
            String text = ctx.getSharedPreferences(PREFS, 0).getString("text", "");
            Notification.Builder b = builder(ctx, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Rest over")
                .setContentText(text == null || text.isEmpty() ? "Time for your next set." : "Up next: " + text)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(openApp(ctx));
            if (Build.VERSION.SDK_INT < 26) {
                b.setDefaults(Notification.DEFAULT_ALL);
                b.setPriority(Notification.PRIORITY_HIGH);
            }
            if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(5 * 60 * 1000L);
            nm.notify(ID_DONE, b.build());
            ctx.getSharedPreferences(PREFS, 0).edit().remove("endAt").apply();
            SessionNotify.restOver(ctx); // 2.17: workout notification switches from countdown to the next set
        } catch (Exception e) {
            android.util.Log.e("HTFU", "rest done failed", e);
        }
    }

    /** JavaScript bridge, registered in MainActivity as "AndroidRest". */
    public static final class Bridge {
        private final Context ctx;
        public Bridge(Context ctx) { this.ctx = ctx.getApplicationContext(); }

        /** endAtMillis is a string so that large numbers pass safely from JavaScript. */
        @JavascriptInterface
        public void start(String endAtMillis, String title, String text) {
            long endAt;
            try { endAt = Long.parseLong(endAtMillis.trim()); } catch (Exception e) { return; }
            if (endAt <= System.currentTimeMillis()) { RestTimer.cancel(ctx); return; }
            RestTimer.start(ctx, endAt, title, text);
        }

        @JavascriptInterface
        public void cancel() { RestTimer.cancel(ctx); }

        @JavascriptInterface
        public boolean available() { return true; }

        /** 2.14: lets the page explain a late or silent rest alert instead of failing quietly. */
        @JavascriptInterface
        public boolean exactAllowed() { return RestTimer.exactAllowed(ctx); }

        @JavascriptInterface
        public boolean notifyAllowed() { return RestTimer.notifyAllowed(ctx); }
    }
}
