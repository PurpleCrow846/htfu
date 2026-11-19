// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/* loaded from: classes.dex */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "HTFU";

    @Override // android.content.BroadcastReceiver
    public void onReceive(Context context, Intent intent) {
        String action;
        if (intent == null) {
            action = null;
        } else {
            try {
                action = intent.getAction();
            } catch (Exception e) {
                Log.e(TAG, "boot reschedule failed", e);
                return;
            }
        }
        if ("android.intent.action.BOOT_COMPLETED".equals(action) || "android.intent.action.TIME_SET".equals(action) || "android.intent.action.TIMEZONE_CHANGED".equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            ReminderScheduler.scheduleNext(context);
            TodayWidget.scheduleMidnight(context);
            TodayWidget.updateAll(context);
        }
    }
}
