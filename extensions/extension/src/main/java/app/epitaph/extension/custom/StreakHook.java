package app.epitaph.extension.custom;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;

import java.util.Calendar;

/**
 * Client-only DM streak. Stores per-thread day streaks in this device's
 * SharedPreferences. Nothing is sent to a server and nothing changes on the
 * other person's side.
 *
 * Counts a day when a chat is opened on that day. Diagnostics: adb logcat -s CenotaphStreak
 */
public final class StreakHook {

    private static final String TAG = "CenotaphStreak";
    private static final String FILE = "cenotaph_streak";

    private StreakHook() {}

    /** Key used until per-chat keys are wired. The streak is per device for now. */
    private static final String SCREEN_KEY = "dm_screen";

    /** Called when a DM thread is opened, from the thread-root anchor. */
    public static void onThreadOpened(View root) {
        try {
            Context ctx = root == null ? null : root.getContext();
            if (ctx == null) return;
            recordDay(ctx, SCREEN_KEY, today());
        } catch (Throwable t) {
            Log.i(TAG, "onThreadOpened: " + t);
        }
    }

    /** Current streak for a thread, or 0 when it is broken or unknown. */
    public static int streak(Context ctx, String threadKey) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
            long last = p.getLong(threadKey + ".last", -1L);
            int count = p.getInt(threadKey + ".count", 0);
            long t = today();
            if (last == t || last == t - 1) return count;
            return 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void recordDay(Context ctx, String threadKey, long day) {
        SharedPreferences p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        long last = p.getLong(threadKey + ".last", -1L);
        if (last == day) return;
        int count = p.getInt(threadKey + ".count", 0);
        int next = (last == day - 1) ? count + 1 : 1;
        p.edit()
                .putLong(threadKey + ".last", day)
                .putInt(threadKey + ".count", next)
                .apply();
        Log.i(TAG, "streak " + threadKey + " = " + next);
    }

    /** Day number since the epoch in local time, so consecutive calendar days differ by 1. */
    private static long today() {
        Calendar c = Calendar.getInstance();
        long offset = c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET);
        return (c.getTimeInMillis() + offset) / 86_400_000L;
    }
}
