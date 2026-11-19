// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** HTFU 2.07: fires at the end of a rest and posts the "Rest over" alert. */
public class RestDoneReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !RestTimer.ACTION_DONE.equals(intent.getAction())) return;
        RestTimer.done(context);
    }
}
