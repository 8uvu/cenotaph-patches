package app.epitaph.extension.custom;

import android.app.Activity;
import android.view.View;

/**
 * One log line per screen saying what the runtime can actually see.
 *
 * This exists because every failure mode of this bundle looks identical on a
 * phone: nothing happens. A patch that never applied, a hook that ran on the
 * wrong screen, and a hook that found the screen but no view all produce the
 * same empty result — and the user has no way to tell them apart.
 *
 * With this, `adb logcat -s EpitaphDiag` answers it directly:
 *
 *   screen=ModalActivity thread=yes header=yes list=yes bg=yes composer=yes
 *
 * `thread=no` on a thread screen means the id lookup is wrong. `thread=yes`
 * with `bg=no` means this build has no `thread_background_view` and the
 * wallpaper is falling back to the ancestor paint. Nothing at all means the
 * runtime hook never landed.
 *
 * Local only: written to logcat, never sent anywhere.
 */
public final class EpitaphDiag {

    private static final String TAG = "CenotaphDiag";

    /** Screen we last reported, so a scroll does not log on every frame. */
    private static String lastKey = "";

    private EpitaphDiag() {}

    public static void report(Activity activity) {
        if (activity == null) return;
        try {
            View content = activity.findViewById(android.R.id.content);
            if (content == null) return;

            String screen = activity.getClass().getSimpleName();
            boolean thread = Ids.has(content, Ids.THREAD_HEADER);
            boolean header = thread;
            boolean list = Ids.has(content, Ids.MESSAGE_LIST);
            boolean bg = Ids.has(content, Ids.THREAD_BACKGROUND);
            boolean composer = Ids.has(content, Ids.COMPOSER_ROOT);
            boolean inbox = Ids.has(content, Ids.INBOX_LIST);

            String key = screen + "/" + thread + list + bg + composer + inbox;
            if (key.equals(lastKey)) return;
            lastKey = key;

            String line =
                    "screen=" + screen
                            + " thread=" + yn(thread)
                            + " header=" + yn(header)
                            + " list=" + yn(list)
                            + " bg=" + yn(bg)
                            + " composer=" + yn(composer)
                            + " inbox=" + yn(inbox)
                            + " wallpaper=" + yn(safeWallpaper())
                            + " streaks=" + yn(safeStreaks())
                            + " tint=" + yn(safeTint());
            log(line);
        } catch (Throwable ignored) {}
    }

    private static boolean safeWallpaper() {
        try {
            return EpitaphPref.wallpaperEnabled(EpitaphApp.context());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean safeStreaks() {
        try {
            return EpitaphPref.showStreakBadge(EpitaphApp.context());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean safeTint() {
        try {
            return EpitaphPref.chatTintColor(EpitaphApp.context()) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String yn(boolean v) {
        return v ? "yes" : "no";
    }

    private static void log(String msg) {
        try {
            android.util.Log.i(TAG, msg);
        } catch (Throwable ignored) {}
    }
}
