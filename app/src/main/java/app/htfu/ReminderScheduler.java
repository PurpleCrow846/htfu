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
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/* loaded from: classes.dex */
public class ReminderScheduler {
    static final String ACTION = "app.htfu.REMIND";
    static final String CHANNEL = "workouts";
    static final long GRACE_MS = 60000;
    static final String NOW_PREFIX = "Reminder going off now";
    static final String PREFS = "htfu";
    static final int REQUEST = 1001;
    static final int REQUEST_TEST = 1002;
    private static final String TAG = "HTFU";

    static long floorFor(SharedPreferences sharedPreferences) {
        long currentTimeMillis = System.currentTimeMillis();
        return Math.max(currentTimeMillis - GRACE_MS, sharedPreferences.getLong("firedSlot", 0L));
    }

    public static void save(Context context, boolean z, String str, int i, int i2) {
        SharedPreferences.Editor putBoolean = context.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", z);
        if (str == null) {
            str = "";
        }
        putBoolean.putString("days", str).putInt("hour", i).putInt("minute", i2).putString("mode", "week").apply();
        scheduleNext(context);
    }

    public static void saveShift(Context context, boolean z, String str, int i, String str2, int i2, int i3) {
        SharedPreferences.Editor putString = context.getSharedPreferences(PREFS, 0).edit().putBoolean("enabled", z).putString("mode", "shift");
        if (str == null) {
            str = "";
        }
        SharedPreferences.Editor putInt = putString.putString("shiftStart", str).putInt("shiftLen", Math.max(1, i));
        if (str2 == null) {
            str2 = "";
        }
        putInt.putString("shiftOn", str2).putInt("hour", i2).putInt("minute", i3).apply();
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService("alarm");
            if (alarmManager == null) {
                return;
            }
            PendingIntent pending = pending(context);
            alarmManager.cancel(pending);
            SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS, 0);
            if (!sharedPreferences.getBoolean("enabled", false)) {
                sharedPreferences.edit().putLong("armed", 0L).apply();
                return;
            }
            long nextWhen = nextWhen(sharedPreferences);
            sharedPreferences.edit().putLong("armed", Math.max(0L, nextWhen)).apply();
            if (nextWhen <= 0) {
                return;
            }
            fire(alarmManager, Math.max(nextWhen, System.currentTimeMillis() + 1000), pending);
        } catch (Throwable th) {
            Log.e(TAG, "scheduleNext failed", th);
        }
    }

    public static void scheduleIn(Context context, long j) {
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService("alarm");
            if (alarmManager == null) {
                return;
            }
            PendingIntent pending = pending(context, REQUEST_TEST);
            alarmManager.cancel(pending);
            fire(alarmManager, System.currentTimeMillis() + j, pending);
        } catch (Throwable th) {
            Log.e(TAG, "scheduleIn failed", th);
        }
    }

    static void fire(AlarmManager alarmManager, long j, PendingIntent pendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(0, j, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarmManager.setAndAllowWhileIdle(0, j, pendingIntent);
            } else {
                alarmManager.set(0, j, pendingIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "fire failed", e);
        }
    }

    public static String nextLabel(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS, 0);
        if (!sharedPreferences.getBoolean("enabled", false)) {
            return "Reminders are off.";
        }
        long nextWhen = nextWhen(sharedPreferences);
        if (nextWhen <= 0) {
            return "No workout day selected.";
        }
        String format = DateFormat.getDateTimeInstance(2, 3).format(new Date(nextWhen));
        return nextWhen <= System.currentTimeMillis() + 1500 ? "Reminder going off now (" + format + ")." : "Next reminder: " + format;
    }

    static PendingIntent pending(Context context) {
        return pending(context, REQUEST);
    }

    static PendingIntent pending(Context context, int i) {
        Intent intent = new Intent(context, (Class<?>) ReminderReceiver.class);
        intent.setAction(ACTION);
        intent.putExtra("kind", i == REQUEST ? "daily" : "test");
        return PendingIntent.getBroadcast(context, i, intent, 201326592);
    }

    static long nextWhen(SharedPreferences sharedPreferences) {
        long nextTrigger;
        int i = sharedPreferences.getInt("hour", 8);
        int i2 = sharedPreferences.getInt("minute", 0);
        long floorFor = floorFor(sharedPreferences);
        if ("shift".equals(sharedPreferences.getString("mode", "week"))) {
            nextTrigger = nextShift(sharedPreferences.getString("shiftStart", ""), sharedPreferences.getInt("shiftLen", 8), sharedPreferences.getString("shiftOn", ""), i, i2, floorFor);
        } else {
            nextTrigger = nextTrigger(sharedPreferences.getString("days", "1,3,5"), i, i2, floorFor);
        }
        long nextExtra = nextExtra(sharedPreferences.getString("extraDays", ""), i, i2, floorFor);
        return nextTrigger <= 0 ? nextExtra : nextExtra <= 0 ? nextTrigger : Math.min(nextTrigger, nextExtra);
    }

    static long nextExtra(String str, int i, int i2, long j) {
        if (str == null || str.isEmpty()) {
            return 0L;
        }
        long j2 = 0;
        for (String str2 : str.split(",")) {
            if (!str2.trim().isEmpty()) {
                try {
                    String[] split = str2.trim().split("-");
                    if (split.length >= 3) {
                        Calendar calendar = Calendar.getInstance();
                        calendar.set(1, Integer.parseInt(split[0]));
                        calendar.set(2, Integer.parseInt(split[1]) - 1);
                        calendar.set(5, Integer.parseInt(split[2]));
                        calendar.set(11, i);
                        calendar.set(12, i2);
                        calendar.set(13, 0);
                        calendar.set(14, 0);
                        long timeInMillis = calendar.getTimeInMillis();
                        if (timeInMillis > j && (j2 == 0 || timeInMillis < j2)) {
                            j2 = timeInMillis;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "bad extra day", e);
                }
            }
        }
        return j2;
    }

    static long nextShift(String str, int i, String str2, int i2, int i3, long j) {
        try {
            return nextShiftUnsafe(str, i, str2, i2, i3, j);
        } catch (Exception e) {
            Log.e(TAG, "nextShift failed", e);
            return 0L;
        }
    }

    static long nextShiftUnsafe(String str, int i, String str2, int i2, int i3, long j) {
        boolean z;
        if (str == null || str.length() < 8 || i < 1) {
            return 0L;
        }
        int max = Math.max(i, 31);
        boolean[] zArr = new boolean[max];
        if (str2 == null) {
            z = false;
        } else {
            z = false;
            for (String str3 : str2.split(",")) {
                if (!str3.trim().isEmpty()) {
                    try {
                        int parseInt = Integer.parseInt(str3.trim());
                        if (parseInt >= 0 && parseInt < max) {
                            zArr[parseInt] = true;
                            z = true;
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "bad shift day", e);
                    }
                }
            }
        }
        if (!z) {
            return 0L;
        }
        String[] split = str.split("-");
        Calendar calendar = Calendar.getInstance();
        calendar.set(1, Integer.parseInt(split[0]));
        calendar.set(2, Integer.parseInt(split[1]) - 1);
        calendar.set(5, Integer.parseInt(split[2]));
        calendar.set(11, 0);
        calendar.set(12, 0);
        calendar.set(13, 0);
        calendar.set(14, 0);
        Calendar calendar2 = Calendar.getInstance();
        calendar2.set(13, 0);
        calendar2.set(14, 0);
        calendar2.set(11, i2);
        calendar2.set(12, i3);
        for (int i4 = 0; i4 < i + 2; i4++) {
            Calendar calendar3 = (Calendar) calendar2.clone();
            calendar3.add(6, i4);
            if (zArr[((dayDiff(calendar, calendar3) % i) + i) % i] && calendar3.getTimeInMillis() > j) {
                return calendar3.getTimeInMillis();
            }
        }
        return 0L;
    }

    static int dayDiff(Calendar calendar, Calendar calendar2) {
        Calendar calendar3 = (Calendar) calendar.clone();
        Calendar calendar4 = (Calendar) calendar2.clone();
        int i = 0;
        calendar3.set(11, 0);
        calendar3.set(12, 0);
        calendar3.set(13, 0);
        calendar3.set(14, 0);
        calendar4.set(11, 0);
        calendar4.set(12, 0);
        calendar4.set(13, 0);
        calendar4.set(14, 0);
        if (calendar4.after(calendar3)) {
            while (calendar3.before(calendar4)) {
                calendar3.add(6, 1);
                i++;
            }
        } else {
            while (calendar4.before(calendar3)) {
                calendar4.add(6, 1);
                i--;
            }
        }
        return i;
    }

    static long nextTrigger(String str, int i, int i2, long j) {
        boolean[] zArr = new boolean[7];
        if (str != null) {
            for (String str2 : str.split(",")) {
                if (!str2.trim().isEmpty()) {
                    try {
                        int parseInt = Integer.parseInt(str2.trim());
                        if (parseInt >= 0 && parseInt <= 6) {
                            zArr[parseInt] = true;
                        }
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "bad day", e);
                    }
                }
            }
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(13, 0);
        calendar.set(14, 0);
        calendar.set(11, i);
        calendar.set(12, i2);
        for (int i3 = 0; i3 < 8; i3++) {
            Calendar calendar2 = (Calendar) calendar.clone();
            calendar2.add(6, i3);
            if (zArr[calendar2.get(7) - 1] && calendar2.getTimeInMillis() > j) {
                return calendar2.getTimeInMillis();
            }
        }
        return 0L;
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL, "Workout reminders", 4);
        notificationChannel.setDescription("Reminds you on the days you set in HTFU");
        notificationManager.createNotificationChannel(notificationChannel);
    }

    public static void showNow(Context context) {
        Notification.Builder builder;
        ensureChannel(context);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != 0) {
            return;
        }
        Intent intent = new Intent(context, (Class<?>) MainActivity.class);
        intent.setAction("app.htfu.OPEN_FROM_REMINDER");
        intent.setFlags(872415232); // 2.14: NEW_TASK | CLEAR_TOP | SINGLE_TOP, so a tap reuses the open page
        PendingIntent activity = PendingIntent.getActivity(context, 7, intent, 201326592);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService("notification");
        if (Build.VERSION.SDK_INT >= 26) {
            builder = new Notification.Builder(context, CHANNEL);
        } else {
            builder = new Notification.Builder(context);
        }
        notificationManager.notify(42, builder.setSmallIcon(R.drawable.ic_stat).setContentTitle("HTFU").setContentText("Test reminder. Workout day notifications are on.").setAutoCancel(true).setContentIntent(activity).build());
    }
}
