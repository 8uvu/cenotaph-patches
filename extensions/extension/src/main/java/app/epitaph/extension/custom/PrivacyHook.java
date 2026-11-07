package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hide "Seen by …" receipt lines, locally.
 *
 * Two places carry them: the DM list rows ("Seen by mesa.030 + 3") and the
 * conversation itself ("Seen by …" under your last message). Both are plain
 * TextViews, so this needs no ids and no anchors — it walks the two lists the
 * runtime already knows about and hides matching text.
 *
 * This does NOT stop Instagram from sending read receipts; it only stops the
 * app drawing the line. Anything else would be a lie.
 *
 * Diagnostics: adb logcat -s EpitaphPrivacy
 */
public final class PrivacyHook {

    private static final String TAG = "CenotaphPrivacy";
    private static final int MAX_VIEWS = 600;

    private static volatile long lastRun = 0;

    /** TextViews we hid, with their original visibility, so off restores them. */
    private static final Map<View, Integer> hidden = new WeakHashMap<View, Integer>();

    private PrivacyHook() {}

    public static void applyToCurrentScreen() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastRun < 1200) return;
            lastRun = now;

            Context ctx = EpitaphApp.context();
            if (ctx == null) return;
            boolean on = EpitaphPref.hideSeen(ctx);

            for (Activity a : resumed()) {
                if (a == null || a.isFinishing()) continue;
                View content = a.findViewById(android.R.id.content);
                if (!(content instanceof ViewGroup)) continue;
                ViewGroup root = (ViewGroup) content;

                List<ViewGroup> targets = new ArrayList<ViewGroup>();
                View inbox = Ids.find(root, Ids.INBOX_LIST);
                if (inbox instanceof ViewGroup) targets.add((ViewGroup) inbox);
                View list = Ids.find(root, Ids.MESSAGE_LIST);
                if (list instanceof ViewGroup) targets.add((ViewGroup) list);
                if (targets.isEmpty()) targets.add(root);

                int hit = 0;
                for (ViewGroup target : targets) {
                    hit += sweep(target, on);
                }
                if (hit > 0) log("hiding " + hit + " receipt line(s)");
            }
        } catch (Throwable ignored) {}
    }

    private static int sweep(ViewGroup target, boolean on) {
        int changed = 0;
        List<View> stack = new ArrayList<View>();
        stack.add(target);
        int seen = 0;
        while (!stack.isEmpty() && seen < MAX_VIEWS) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) continue;
            seen++;
            if (v instanceof TextView) {
                CharSequence cs = ((TextView) v).getText();
                if (cs != null && looksLikeReceipt(cs.toString())) {
                    Integer old = hidden.get(v);
                    if (on) {
                        if (v.getVisibility() != View.GONE) {
                            if (old == null) hidden.put(v, v.getVisibility());
                            v.setVisibility(View.GONE);
                            changed++;
                        }
                    } else if (old != null) {
                        v.setVisibility(old);
                        hidden.remove(v);
                        changed++;
                    }
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
            }
        }
        return changed;
    }

    /** "Seen by …" on its own, with an optional "+ N". Not "Seen 2h ago" in a header. */
    private static boolean looksLikeReceipt(String s) {
        try {
            String t = s.trim().toLowerCase(java.util.Locale.US);
            return t.startsWith("seen by ") && t.length() < 40;
        } catch (Throwable e) {
            return false;
        }
    }

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
                try {
                    java.lang.reflect.Field af = record.getClass().getDeclaredField("activity");
                    af.setAccessible(true);
                    Object a = af.get(record);
                    if (a instanceof Activity && !((Activity) a).isFinishing()) out.add((Activity) a);
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
