// SPDX-License-Identifier: GPL-3.0-or-later
// HTFU - https://htfu.app/
package app.htfu;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

/** HTFU 2.15: speaks one scheduled voice cue (alarm set by VoiceCues.schedule). */
public final class VoiceCueReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null || !VoiceCues.ACTION.equals(intent.getAction())) return;
        String text = intent.getStringExtra("text");
        if (text == null || text.isEmpty()) return;
        final PendingResult pr = goAsync();
        final boolean[] finished = {false};
        final Runnable end = () -> { if (!finished[0]) { finished[0] = true; try { pr.finish(); } catch (Exception ignored) {} } };
        // keep the receiver (and so the process) alive until the phrase is spoken, at most 9 s
        new Handler(Looper.getMainLooper()).postDelayed(end, 9000);
        VoiceCues.speak(ctx, text, end);
    }
}
