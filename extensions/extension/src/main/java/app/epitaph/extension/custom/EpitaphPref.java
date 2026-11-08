package app.epitaph.extension.custom;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Cenotaph v1 preferences.
 *
 * Deliberately small: one file, flat keys, no Piko coupling. The 2.x tree stored
 * a preference for every feature it hoped to have; v1 only stores keys for
 * features that are actually hooked into Instagram, so the settings screen
 * cannot offer something with nothing behind it.
 */
public final class EpitaphPref {

    private static final String FILE = "cenotaph";

    public static final String KEY_WALLPAPER_ENABLED = "wallpaper_enabled";
    public static final String KEY_WALLPAPER_PATH = "wallpaper_path";
    public static final String KEY_WALLPAPER_FIT = "wallpaper_fit";
    public static final String KEY_WALLPAPER_DIM = "wallpaper_dim";
    public static final String KEY_WALLPAPER_BLUR = "wallpaper_blur";
    public static final String KEY_WALLPAPER_POOL = "wallpaper_pool";
    public static final String KEY_THREAD_PREFIX = "wallpaper_thread_";

    private EpitaphPref() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean wallpaperEnabled(Context ctx) {
        try {
            return prefs(ctx).getBoolean(KEY_WALLPAPER_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setWallpaperEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_WALLPAPER_ENABLED, on).apply();
    }

    public static String wallpaperPath(Context ctx) {
        return prefs(ctx).getString(KEY_WALLPAPER_PATH, "");
    }

    public static void setWallpaperPath(Context ctx, String path) {
        prefs(ctx).edit().putString(KEY_WALLPAPER_PATH, path == null ? "" : path).apply();
    }

    /** Per-chat override: one image for one thread. */
    public static String wallpaperForThread(Context ctx, String threadId) {
        if (threadId == null || threadId.isEmpty()) return "";
        return prefs(ctx).getString(KEY_THREAD_PREFIX + threadId, "");
    }

    public static void setWallpaperForThread(Context ctx, String threadId, String path) {
        if (threadId == null || threadId.isEmpty()) return;
        SharedPreferences.Editor e = prefs(ctx).edit();
        if (path == null || path.isEmpty()) {
            e.remove(KEY_THREAD_PREFIX + threadId);
        } else {
            e.putString(KEY_THREAD_PREFIX + threadId, path);
        }
        e.apply();
    }

    /** fill (default), fit or stretch. */
    public static String wallpaperFit(Context ctx) {
        return prefs(ctx).getString(KEY_WALLPAPER_FIT, "fill");
    }

    public static void setWallpaperFit(Context ctx, String fit) {
        prefs(ctx).edit().putString(KEY_WALLPAPER_FIT, fit == null ? "fill" : fit).apply();
    }

    /** 0-80 percent black overlay, so text stays readable on a bright image. */
    public static int wallpaperDim(Context ctx) {
        return prefs(ctx).getInt(KEY_WALLPAPER_DIM, 0);
    }

    public static void setWallpaperDim(Context ctx, int value) {
        prefs(ctx).edit().putInt(KEY_WALLPAPER_DIM, value).apply();
    }

    /** 0-40 blur radius, 0 = off. */
    public static int wallpaperBlur(Context ctx) {
        return prefs(ctx).getInt(KEY_WALLPAPER_BLUR, 0);
    }

    public static void setWallpaperBlur(Context ctx, int value) {
        prefs(ctx).edit().putInt(KEY_WALLPAPER_BLUR, value).apply();
    }

    /** Newline separated image list; one is picked per chat. */
    public static String wallpaperPool(Context ctx) {
        return prefs(ctx).getString(KEY_WALLPAPER_POOL, "");
    }

    public static void setWallpaperPool(Context ctx, String pool) {
        prefs(ctx).edit().putString(KEY_WALLPAPER_POOL, pool == null ? "" : pool).apply();
    }

    public static void resetAll(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }
}
