package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local UI color map (100% client-side).
 *
 * Entry {@link #applyToCurrentScreen()} takes no arguments on purpose: the
 * patch injects a register-free static call, so it verifies in ANY matched
 * method shape. Everything else happens here at RUNTIME.
 *
 * Model: the user supplies `sourceRgb=>targetRgb` pairs (Cenotaph Settings).
 * On each throttled pass the hook walks the resumed screen's view hierarchy
 * and, for every view, rewrites the color it can actually read:
 *
 *   - TextView text color        -> {@code setTextColor}
 *   - ColorDrawable background   -> {@code setColor}
 *   - GradientDrawable (solid)   -> {@code setColor}
 *
 * Only colors the user explicitly names are touched, so a wrong or stale
 * pair is a silent no-op instead of a random repaint. Colors this hook
 * cannot read (ripples, state lists, 9-patches, multi-stop gradients) are
 * left alone rather than approximated with a tint. Matching is exact on RGB
 * (alpha is preserved), which keeps a broad mapping like #262626 from
 * swallowing neighbouring shades.
 *
 * Scope: DM thread screens by default (same runtime thread-header detection
 * the wallpaper uses); the "apply everywhere" switch widens it to whatever
 * screen is resumed. No server, API, or request is involved.
 *
 * CONFIG: Cenotaph Settings launcher activity + the Epitaph category in Piko
 * Settings (EpitaphPref). Morphe patch on/off stays the master toggle.
 */
@SuppressWarnings("unused")
public class ColorMapHook {

    /** Upper bound on views visited per pass — keeps a huge tree cheap. */
    private static final int MAX_VIEWS = 3000;

    /** The anchor fires on every list layout; recolor at most once a second. */
    private static final long THROTTLE_MS = 1000;

    private static final int[] NO_PAIRS = new int[0];

    private static volatile long lastRun = 0;

    private ColorMapHook() {}

    public static void applyToCurrentScreen() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastRun < THROTTLE_MS) return;
            lastRun = now;

            Context appCtx = EpitaphApp.context();
            if (appCtx == null) return;
            if (!EpitaphPref.colorMapEnabled(appCtx)) return;

            int[] pairs = parsePairs(EpitaphPref.colorMapEntries(appCtx));
            if (pairs.length == 0) return; // nothing configured -> no-op

            boolean allScreens;
            try {
                allScreens = EpitaphPref.colorMapAllScreens(appCtx);
            } catch (Throwable t) {
                allScreens = false;
            }

            List<Activity> resumed = resumedActivities();
            for (Activity a : resumed) {
                try {
                    if (a == null || a.isFinishing()) continue;
                    View content = a.findViewById(android.R.id.content);
                    if (!(content instanceof ViewGroup)) continue;
                    // Chat-only by default: only DM thread screens carry the
                    // thread header marker (reuses the wallpaper's detector).
                    if (!allScreens && !WallpaperHook.containsThreadHeader((ViewGroup) content)) {
                        continue;
                    }
                    recolor((ViewGroup) content, pairs);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // Never let a recolor pass take down the host.
        }
    }

    /**
     * Parses "source=>target" lines into a flat {src, dst, src, dst, ...}
     * RGB array. Unparsable lines are skipped, so a partially wrong config
     * still applies the pairs that are valid.
     */
    private static int[] parsePairs(String raw) {
        try {
            if (raw == null || raw.trim().isEmpty()) return NO_PAIRS;
            List<Integer> out = new ArrayList<Integer>();
            for (String line : raw.split("\\r?\\n")) {
                if (line == null) continue;
                String t = line.trim();
                int sep = t.indexOf("=>");
                if (sep <= 0) continue;
                Integer src = parseRgb(t.substring(0, sep));
                Integer dst = parseRgb(t.substring(sep + 2));
                if (src == null || dst == null) continue;
                // A pair that maps a color onto itself would be a no-op; skip
                // so the walk can short-circuit on "changed" instead.
                if (src.intValue() == dst.intValue()) continue;
                out.add(src);
                out.add(dst);
            }
            if (out.isEmpty()) return NO_PAIRS;
            int[] arr = new int[out.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = out.get(i).intValue();
            return arr;
        } catch (Throwable t) {
            return NO_PAIRS;
        }
    }

    /** "#RRGGBB" / "RRGGBB" / "#AARRGGBB" -> 0xRRGGBB, or null if invalid. */
    private static Integer parseRgb(String raw) {
        try {
            if (raw == null) return null;
            String s = raw.trim();
            if (s.startsWith("#")) s = s.substring(1);
            if (s.length() != 6 && s.length() != 8) return null;
            long v = Long.parseLong(s, 16);
            return Integer.valueOf((int) (v & 0xFFFFFFL));
        } catch (Throwable t) {
            return null; // not hex
        }
    }

    /** RGB lookup; returns the color unchanged (same int) when unmapped. */
    private static int mapColor(int color, int[] pairs) {
        int rgb = color & 0x00FFFFFF;
        for (int i = 0; i < pairs.length; i += 2) {
            if (pairs[i] == rgb) {
                // Preserve the original alpha so text/disabled states survive.
                return (color & 0xFF000000) | pairs[i + 1];
            }
        }
        return color;
    }

    private static void recolor(ViewGroup root, int[] pairs) {
        List<View> stack = new ArrayList<View>();
        stack.add(root);
        int seen = 0;
        while (!stack.isEmpty() && seen < MAX_VIEWS) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) continue;
            seen++;
            try {
                if (v instanceof TextView) {
                    TextView tv = (TextView) v;
                    int c = tv.getCurrentTextColor();
                    int m = mapColor(c, pairs);
                    if (m != c) tv.setTextColor(m);
                }
                applyBackground(v, pairs);
            } catch (Throwable ignored) {
                // Foreign drawables / weird states: skip this view, keep walking.
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                try {
                    for (int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
                } catch (Throwable ignored) {}
            }
        }
    }

    /**
     * Rewrites only colors we can actually read back. Anything else is left
     * untouched on purpose — guessing with a tint would repaint drawables the
     * user never named.
     */
    private static void applyBackground(View v, int[] pairs) {
        try {
            Drawable bg = v.getBackground();
            if (bg == null) return;
            if (bg instanceof ColorDrawable) {
                int c = ((ColorDrawable) bg).getColor();
                int m = mapColor(c, pairs);
                if (m != c) ((ColorDrawable) bg).setColor(m);
                return;
            }
            if (bg instanceof GradientDrawable) {
                // getColor() is API 24+ and returns null for multi-stop
                // gradients — both cases end up as a no-op, which is fine.
                ColorStateList cs = ((GradientDrawable) bg).getColor();
                if (cs == null) return;
                int c = cs.getDefaultColor();
                int m = mapColor(c, pairs);
                if (m != c) ((GradientDrawable) bg).setColor(m);
            }
        } catch (Throwable ignored) {}
    }

    /** All non-paused activities from ActivityThread.mActivities (stable AOSP). */
    private static List<Activity> resumedActivities() {
        List<Activity> out = new ArrayList<Activity>();
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object map = f.get(thread);
            if (!(map instanceof Map)) return out;
            for (Object rec : ((Map<?, ?>) map).values()) {
                if (rec == null) continue;
                try {
                    Field paused = rec.getClass().getDeclaredField("paused");
                    paused.setAccessible(true);
                    if (paused.getBoolean(rec)) continue;
                    Field act = rec.getClass().getDeclaredField("activity");
                    act.setAccessible(true);
                    Object a = act.get(rec);
                    if (a instanceof Activity) out.add((Activity) a);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }
}
