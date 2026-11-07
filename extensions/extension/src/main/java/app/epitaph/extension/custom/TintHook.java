package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Solid colour behind chats — no image file, no URI permission, no decoding.
 *
 * Instagram's thread screen has a dedicated background ImageView
 * (`thread_background_view`) sitting behind the message list. A wall of
 * {@link ColorDrawable} on it gives a themed chat with none of the cost of the
 * image path, and it is the same surface the wallpaper uses, so the two never
 * disagree about what should be visible.
 *
 * Precedence: an enabled wallpaper wins (it is the richer feature); the tint
 * applies when no image is in play. Switching either off puts Instagram's own
 * background back.
 *
 * Diagnostics: adb logcat -s EpitaphTint
 */
public final class TintHook {

    private static final String TAG = "CenotaphTint";

    private static volatile long lastRun = 0;

    /** Views we painted, with the alpha they had, so off restores them. */
    private static final Map<View, Float> painted = new WeakHashMap<View, Float>();

    private TintHook() {}

    public static void applyToCurrentScreen() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastRun < 1200) return;
            lastRun = now;

            Context ctx = EpitaphApp.context();
            if (ctx == null) return;

            Integer color = EpitaphPref.chatTintColor(ctx);
            boolean wallpaperOn = EpitaphPref.wallpaperEnabled(ctx);

            for (Activity a : resumed()) {
                try {
                    if (a == null || a.isFinishing()) continue;
                    View content = a.findViewById(android.R.id.content);
                    if (!(content instanceof android.view.ViewGroup)) continue;
                    if (!WallpaperHook.containsThreadHeader((android.view.ViewGroup) content)) {
                        continue;
                    }
                    View bg = Ids.find(content, Ids.THREAD_BACKGROUND);
                    if (!(bg instanceof ImageView)) continue;
                    ImageView iv = (ImageView) bg;

                    if (color == null || wallpaperOn) {
                        Float old = painted.remove(iv);
                        if (old != null) {
                            iv.setImageDrawable(null);
                            iv.setAlpha(old);
                            log("tint cleared");
                        }
                        continue;
                    }
                    if (!painted.containsKey(iv)) painted.put(iv, iv.getAlpha());
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    iv.setImageDrawable(new ColorDrawable(color));
                    iv.setAlpha(Math.max(0.1f, Math.min(1f,
                            EpitaphPref.wallpaperDim(ctx) / 100f)));
                    if (iv.getVisibility() != View.VISIBLE) iv.setVisibility(View.VISIBLE);
                    log("tint applied #" + Integer.toHexString(color));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    /** Resumed activities via ActivityThread — the same call the other hooks use. */
    private static List<Activity> resumed() {
        List<Activity> out = new ArrayList<Activity>();
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Map<?, ?> activities = (Map<?, ?>) f.get(thread);
            if (activities == null) return out;
            for (Object record : activities.values()) {
                if (record == null) continue;
                try {
                    java.lang.reflect.Field af =
                            record.getClass().getDeclaredField("activity");
                    af.setAccessible(true);
                    Object a = af.get(record);
                    if (!(a instanceof Activity)) continue;
                    Activity act = (Activity) a;
                    if (act.isFinishing()) continue;
                    Boolean resumed = null;
                    try {
                        java.lang.reflect.Field pf =
                                record.getClass().getDeclaredField("paused");
                        pf.setAccessible(true);
                        Object p = pf.get(record);
                        resumed = !(p instanceof Boolean) || !((Boolean) p);
                    } catch (Throwable ignored) {}
                    if (resumed == null || resumed) out.add(act);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void log(String msg) {
        try {
            android.util.Log.i(TAG, msg);
        } catch (Throwable ignored) {}
    }
}
