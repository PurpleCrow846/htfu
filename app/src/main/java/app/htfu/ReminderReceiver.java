// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

/* loaded from: classes.dex */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "HTFU";

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        String stringExtra = null;
        Notification.Builder builder;
        if (intent == null) {
            stringExtra = null;
        } else {
            try {
                stringExtra = intent.getStringExtra("kind");
            } catch (Throwable th) {
                Log.e(TAG, "reminder failed", th);
            }
        }
        if (!"test".equals(stringExtra)) {
            SharedPreferences sharedPreferences = context.getSharedPreferences("htfu", 0);
            long j = sharedPreferences.getLong("armed", 0L);
            if (j > 0 && j <= System.currentTimeMillis() + 5000) {
                sharedPreferences.edit().putLong("firedSlot", j).commit();
            }
        }
        ReminderScheduler.ensureChannel(context);
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == 0) {
            Intent intent2 = new Intent(context, (Class<?>) MainActivity.class);
            intent2.setAction("app.htfu.OPEN_FROM_REMINDER");
            intent2.setFlags(872415232); // 2.14: NEW_TASK | CLEAR_TOP | SINGLE_TOP, so a tap reuses the open page
            PendingIntent activity = PendingIntent.getActivity(context, 7, intent2, 201326592);
            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(context, "workouts");
            } else {
                builder = new Notification.Builder(context);
            }
            NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
            if (notificationManager != null) {
                notificationManager.notify(41, builder.setSmallIcon(R.drawable.ic_stat).setContentTitle("HTFU").setContentText("Workout day. Your session is waiting.").setAutoCancel(true).setContentIntent(activity).build());
            }
        }
        try {
            ReminderScheduler.scheduleNext(context);
        } catch (Throwable th2) {
            Log.e(TAG, "reminder reschedule failed", th2);
        }
    }
}
