// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.widget.RemoteViews;
import java.util.Calendar;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * HTFU home-screen widget: plan, today's workout and its exercises, streak,
 * and a Start button that opens the app on today's workout.
 * 2.07: adds the exercise list line ("detail") and follows the colour scheme
 * the page sends ("accent", "onAccent"). Older pages that send neither still work.
 */
public class TodayWidget extends AppWidgetProvider {
    static final String ACTION_MIDNIGHT = "app.htfu.WIDGET_MIDNIGHT";
    static final String ACTION_START = "app.htfu.START";
    static final String KEY = "summary";
    static final String PREFS = "htfu-widget";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_MIDNIGHT.equals(intent == null ? null : intent.getAction())) {
            AppWidgetManager m = AppWidgetManager.getInstance(context);
            onUpdate(context, m, m.getAppWidgetIds(new ComponentName(context, TodayWidget.class)));
            scheduleMidnight(context);
        }
    }

    static void updateAll(Context context) {
        AppWidgetManager m = AppWidgetManager.getInstance(context);
        for (int id : m.getAppWidgetIds(new ComponentName(context, TodayWidget.class))) update(context, m, id);
    }

    static int color(JSONObject o, String key, int fallback) {
        if (o == null) return fallback;
        String s = o.optString(key, "");
        if (s == null || s.length() < 4) return fallback;
        try { return Color.parseColor(s); } catch (Exception e) { return fallback; }
    }

    static void update(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String raw = prefs.getString(KEY, "");
        String plan = "HTFU";
        String session = "";
        String detail = "";
        boolean dark = true;
        boolean workoutDay = false;
        int streak = 0;
        JSONObject o = null;
        try {
            if (raw != null && raw.length() > 2) {
                o = new JSONObject(raw);
                plan = o.optString("plan", plan);
                session = o.optString("session", "");
                detail = o.optString("detail", "");
                dark = o.optBoolean("dark", true);
                workoutDay = isWorkoutDay(o);
                streak = streakOf(o);
            }
        } catch (Exception e) {
            o = null;
        }
        String line = workoutDay ? (session.isEmpty() ? "Workout day" : session) : "Rest day";
        // 2.16: themed colours from the page (bg/ink/sub); Classic and older pages send none, so the old colours stay
        int bg = color(o, "bg", dark ? 0xFF14120E : 0xFFF6F1E8);
        int ink = color(o, "ink", dark ? 0xFFF4EFE6 : 0xFF1C1915);
        int sub = color(o, "sub", dark ? 0xFFD7D0C4 : 0xFF3A342C);
        int accentText = color(o, dark ? "accentTextDark" : "accentTextLight", dark ? 0xFFE07A3D : 0xFFB45309);
        int accent = color(o, "accent", 0xFFE85D04);
        int onAccent = color(o, "onAccent", 0xFF1A0E05);

        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_today);
        v.setTextViewText(R.id.widget_plan, plan);
        v.setTextViewText(R.id.widget_detail, line);
        v.setTextViewText(R.id.widget_list, workoutDay ? detail : "");
        v.setTextViewText(R.id.widget_streak, "Streak " + streak);
        v.setInt(R.id.widget_root, "setBackgroundColor", bg);
        v.setTextColor(R.id.widget_plan, ink);
        v.setTextColor(R.id.widget_detail, sub);
        v.setTextColor(R.id.widget_list, sub);
        v.setTextColor(R.id.widget_streak, accentText);
        v.setInt(R.id.widget_start, "setBackgroundColor", accent);
        v.setTextColor(R.id.widget_start, onAccent);
        v.setTextViewText(R.id.widget_start, workoutDay ? "Start workout" : "Open HTFU");

        Intent start = new Intent(context, MainActivity.class);
        start.setAction(ACTION_START);
        start.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 9, start, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.widget_start, pi);
        v.setOnClickPendingIntent(R.id.widget_root, pi);
        manager.updateAppWidget(widgetId, v);
    }

    static boolean isWorkoutDay(JSONObject o) {
        return isScheduled(o, Calendar.getInstance());
    }

    static int streakOf(JSONObject o) {
        try {
            JSONArray arr = o.optJSONArray("logDates");
            HashSet<String> seen = new HashSet<>();
            if (arr != null) for (int i = 0; i < arr.length(); i++) seen.add(arr.optString(i, ""));
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (isScheduled(o, c) && !seen.contains(ymd(c))) c.add(Calendar.DAY_OF_YEAR, -1);
            int n = 0;
            for (int i = 0; i < 400; i++) {
                if (!isScheduled(o, c)) { c.add(Calendar.DAY_OF_YEAR, -1); continue; }
                if (!seen.contains(ymd(c))) break;
                n++;
                c.add(Calendar.DAY_OF_YEAR, -1);
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    static boolean isScheduled(JSONObject o, Calendar when) {
        Calendar day = (Calendar) when.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        String key = ymd(day);
        JSONArray skip = o.optJSONArray("skipDays");
        if (skip != null) for (int i = 0; i < skip.length(); i++) if (key.equals(skip.optString(i, ""))) return false;
        JSONArray extra = o.optJSONArray("extraDays");
        if (extra != null) for (int i = 0; i < extra.length(); i++) if (key.equals(extra.optString(i, ""))) return true;
        if ("shift".equals(o.optString("mode", "week"))) {
            JSONArray shift = o.optJSONArray("shift");
            if (shift == null || shift.length() < 1) return false;
            String startStr = o.optString("shiftStart", "2026-01-01");
            try {
                String[] p = (startStr.length() >= 8 ? startStr : "2026-01-01").split("-");
                Calendar start = (Calendar) day.clone();
                start.set(Integer.parseInt(p[0]), Integer.parseInt(p[1]) - 1, Integer.parseInt(p[2]), 0, 0, 0);
                start.set(Calendar.MILLISECOND, 0);
                long diff = Math.round((day.getTimeInMillis() - start.getTimeInMillis()) / 8.64E7d);
                long len = shift.length();
                JSONObject d = shift.optJSONObject((int) (((diff % len) + len) % len));
                return d != null && d.optBoolean("workout", false);
            } catch (Exception e) {
                return false;
            }
        }
        JSONArray days = o.optJSONArray("days");
        if (days == null) return false;
        int dow = day.get(Calendar.DAY_OF_WEEK) - 1;
        for (int i = 0; i < days.length(); i++) if (days.optInt(i, -1) == dow) return true;
        return false;
    }

    static String ymd(Calendar c) {
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static void scheduleMidnight(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(context, TodayWidget.class);
            i.setAction(ACTION_MIDNIGHT);
            PendingIntent pi = PendingIntent.getBroadcast(context, 11, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, 1);
            c.set(Calendar.HOUR_OF_DAY, 0);
            c.set(Calendar.MINUTE, 1);
            c.set(Calendar.SECOND, 0);
            am.setAndAllowWhileIdle(AlarmManager.RTC, c.getTimeInMillis(), pi);
        } catch (Exception e) {
        }
    }
}
