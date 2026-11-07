package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bubble corner radius (100% client-side).
 *
 * Entry {@link #applyToCurrentScreen()} takes no arguments on purpose: the
 * patch injects a register-free static call, so it verifies in ANY matched
 * method shape. Everything else happens here at RUNTIME.
 *
 * Model: every rounded {@link GradientDrawable} surface in a chat screen gets
 * the configured corner radius. That covers bubbles, but also the composer and
 * rounded buttons, because nothing at runtime reliably says "this rounded
 * surface is a message bubble" — a heuristic that guesses would as often
 * distort the wrong view. A surface that is NOT already rounded is never
 * touched, so full-screen containers and plain rectangles are left alone.
 *
 * A view remembers the radius this hook applied to it, so the change is
 * idempotent across RecyclerView recycling and re-applies when the setting
 * changes (including back to square). Multi-corner drawables report a radius
 * of 0 and are skipped rather than flattened.
 *
 * CONFIG: Cenotaph Settings + the Epitaph category in Piko Settings
 * (EpitaphPref). Morphe patch on/off stays the master toggle.
 */
@SuppressWarnings("unused")
public class BubbleShapeHook {

    private static final int MAX_VIEWS = 3000;
    private static final long THROTTLE_MS = 1000;

    /** Tag slot remembering the radius applied to a view (recycled views keep it). */
    private static final int RADIUS_TAG = 0x7E9B0001;

    private static volatile long lastRun = 0;

    private BubbleShapeHook() {}

    public static void applyToCurrentScreen() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastRun < THROTTLE_MS) return;
            lastRun = now;

            Context appCtx = EpitaphApp.context();
            if (appCtx == null) return;
            if (!EpitaphPref.bubbleShapeEnabled(appCtx)) return;

            int radiusDp = EpitaphPref.bubbleRadiusDp(appCtx);
            if (radiusDp < 0) return; // never configured -> no-op

            float px;
            try {
                px = radiusDp * appCtx.getResources().getDisplayMetrics().density;
            } catch (Throwable t) {
                return;
            }

            for (Activity a : resumedActivities()) {
                try {
                    if (a == null || a.isFinishing()) continue;
                    View content = a.findViewById(android.R.id.content);
                    if (!(content instanceof ViewGroup)) continue;
                    // Chat-only: the same runtime thread-header marker the
                    // wallpaper and color-map hooks gate on.
                    if (!WallpaperHook.containsThreadHeader((ViewGroup) content)) continue;
                    // Scope to the message list when it can be found: the
                    // composer and the header carry rounded surfaces too, and
                    // nothing there is a bubble.
                    View list = Ids.find(content, Ids.MESSAGE_LIST);
                    reshape((ViewGroup) (list instanceof ViewGroup ? list : content), px);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // Never let a reshape pass take down the host.
        }
    }

    private static void reshape(ViewGroup root, float px) {
        List<View> stack = new ArrayList<View>();
        stack.add(root);
        int seen = 0;
        while (!stack.isEmpty() && seen < MAX_VIEWS) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) continue;
            seen++;
            try {
                reshapeView(v, px);
            } catch (Throwable ignored) {
                // Foreign drawables / odd states: skip this view, keep walking.
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                try {
                    for (int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void reshapeView(View v, float px) {
        Drawable bg = v.getBackground();
        if (!(bg instanceof GradientDrawable)) return;
        GradientDrawable gd = (GradientDrawable) bg;

        Float applied = null;
        try {
            Object tag = v.getTag(RADIUS_TAG);
            if (tag instanceof Float) applied = (Float) tag;
        } catch (Throwable ignored) {}

        // getCornerRadius() is API 24+ and returns 0 for per-corner drawables.
        float current;
        try {
            current = gd.getCornerRadius();
        } catch (Throwable t) {
            return; // cannot read it back -> leave the surface alone
        }

        if (applied == null && current <= 0f) return; // never round a sharp rectangle
        // Skip only if the view really is at the configured radius. Trusting the
        // tag alone would go stale when IG swaps the background under a
        // recycled view, leaving that bubble at its original shape.
        if (applied != null
                && Math.abs(applied.floatValue() - px) < 0.5f
                && Math.abs(current - px) < 0.5f) {
            return;
        }

        try {
            gd.setCornerRadius(px);
            v.setTag(RADIUS_TAG, Float.valueOf(px));
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
