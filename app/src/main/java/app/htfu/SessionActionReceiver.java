// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** HTFU 2.17: Set done / Skip rest buttons on the workout notification. Not exported. */
public class SessionActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SessionNotify.ACTION.equals(intent.getAction())) return;
        SessionNotify.onAction(context, intent.getStringExtra("act"), intent.getStringExtra("token"));
    }
}
