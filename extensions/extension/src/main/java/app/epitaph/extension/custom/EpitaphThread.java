package app.epitaph.extension.custom;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * Which DM thread is open, without needing Piko's extension classes.
 *
 * Every per-thread feature (wallpaper override, streak counting, badge
 * learning) needs this, and the previous implementation got it from a single
 * reflective read of Piko's `SavedMessagesHook.sCurrentThreadId`. In Piko 3.10
 * that field is gone — thread identity moved to `noteThreadUsers(...)` — so the
 * lookup quietly returned null and every per-thread feature fell back to global
 * behaviour while still reporting success.
 *
 * Three sources, in order of quality:
 *
 *  1. Piko's tracker, if a fused build still exposes one. Tried across the
 *     class/field names Piko has used; absent fields are not an error.
 *  2. The thread fragment's arguments. The Direct thread lives in a fragment
 *     whose arguments carry the thread key, so this yields Instagram's own id.
 *  3. The header title text. Always available on a real thread screen and
 *     stable per conversation, which is enough as a preference key — it is why
 *     per-thread wallpapers and streaks keep working even if 1 and 2 miss.
 *
 * Values from (3) are prefixed "n:" so they can never be confused with a real
 * thread id, which matters when an old preference is read back.
 */
public final class EpitaphThread {

    private static final String NAME_PREFIX = "n:";

    private static final String[] PIKO_CLASSES = {
        "app.morphe.extension.instagram.patches.dm.SavedMessagesHook",
        "app.morphe.extension.instagram.patches.dm.SavedMessages",
        "app.crimera.extension.instagram.patches.dm.SavedMessagesHook",
    };

    private static final String[] PIKO_FIELDS = {
        "sCurrentThreadId",
        "currentThreadId",
    };

    private EpitaphThread() {}

    /** Best known identity for the thread on screen, or null off-thread. */
    public static String currentThreadId(android.content.Context ctx) {
        String fromPiko = fromPiko();
        if (fromPiko != null) return fromPiko;
        Activity a = null;
        try {
            if (ctx instanceof Activity) a = (Activity) ctx;
            else if (ctx != null) {
                android.content.Context c = ctx;
                for (int hops = 0; c instanceof android.content.ContextWrapper && hops < 8; hops++) {
                    android.content.Context base =
                            ((android.content.ContextWrapper) c).getBaseContext();
                    if (base instanceof Activity) {
                        a = (Activity) base;
                        break;
                    }
                    c = base;
                }
            }
        } catch (Throwable ignored) {}
        return currentThreadId(a);
    }

    /** Same, from an explicit host activity (preferred where one is at hand). */
    public static String currentThreadId(Activity a) {
        if (a == null) return null;
        String fromPiko = fromPiko();
        if (fromPiko != null) return fromPiko;
        String fromArgs = fromFragmentArgs(a);
        if (fromArgs != null) return fromArgs;
        return fromHeader(a);
    }

    /** Legacy entry point: no host available, so header/args cannot be read. */
    public static String currentThreadId() {
        return fromPiko();
    }

    // ------------------------------------------------------------------
    // 1. Piko's tracker (optional, may not exist)
    // ------------------------------------------------------------------

    private static String fromPiko() {
        for (String className : PIKO_CLASSES) {
            try {
                Class<?> hook = Class.forName(className);
                for (String field : PIKO_FIELDS) {
                    try {
                        java.lang.reflect.Field f = hook.getDeclaredField(field);
                        f.setAccessible(true);
                        Object v = f.get(null);
                        String id = clean(v);
                        if (id != null) return id;
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String clean(Object v) {
        try {
            if (v == null) return null;
            String s = String.valueOf(v).trim();
            return s.isEmpty() ? null : s;
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 2. Fragment arguments
    // ------------------------------------------------------------------

    private static String fromFragmentArgs(Activity a) {
        // Support-library FragmentManager is reached reflectively: the extension
        // is compiled without AndroidX, and a fused build may not have it either.
        Object fm = null;
        for (String getter : new String[]{"getSupportFragmentManager", "getFragmentManager"}) {
            try {
                fm = a.getClass().getMethod(getter).invoke(a);
                if (fm != null) break;
            } catch (Throwable ignored) {}
        }
        return stringsFromFragments(fm, 0);
    }

    private static String stringsFromFragments(Object fm, int depth) {
        if (fm == null || depth > 4) return null;
        java.util.List<?> fragments = null;
        try {
            Object v = fm.getClass().getMethod("getFragments").invoke(fm);
            if (v instanceof java.util.List) fragments = (java.util.List<?>) v;
        } catch (Throwable ignored) {}
        if (fragments == null) return null;
        for (Object f : fragments) {
            if (f == null) continue;
            String hit = fromArguments(f);
            if (hit != null) return hit;
            try {
                Object child = f.getClass().getMethod("getChildFragmentManager").invoke(f);
                hit = stringsFromFragments(child, depth + 1);
                if (hit != null) return hit;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String fromArguments(Object fragment) {
        try {
            Object args = fragment.getClass().getMethod("getArguments").invoke(fragment);
            if (!(args instanceof Bundle)) return null;
            Bundle b = (Bundle) args;
            for (String key : b.keySet()) {
                if (key == null) continue;
                String lower = key.toLowerCase(java.util.Locale.US);
                if (!lower.contains("thread")) continue;
                Object v;
                try {
                    v = b.get(key);
                } catch (Throwable t) {
                    continue;
                }
                String direct = clean(v);
                if (direct != null && !direct.contains("@")) return direct;
                // The value is usually a DirectThreadKey: ask it for the id.
                for (String getter : new String[]{"getThreadId", "getThreadV2Id", "getId"}) {
                    try {
                        Object id = v.getClass().getMethod(getter).invoke(v);
                        String s = clean(id);
                        if (s != null) return s;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------------
    // 3. Header title
    // ------------------------------------------------------------------

    private static String fromHeader(Activity a) {
        try {
            View content = a.findViewById(android.R.id.content);
            if (content == null) return null;
            View title = Ids.find(content, Ids.HEADER_TITLE);
            if (!(title instanceof TextView)) return null;
            CharSequence cs = ((TextView) title).getText();
            if (cs == null) return null;
            String name = cs.toString().trim();
            if (name.isEmpty() || name.length() > 60) return null;
            return NAME_PREFIX + name.toLowerCase(java.util.Locale.US);
        } catch (Throwable t) {
            return null;
        }
    }
}
