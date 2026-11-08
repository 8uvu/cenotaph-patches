package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Local chat font (100% client-side).
 *
 * Entry {@link #applyToCurrentScreen()} takes no arguments on purpose: the
 * patch injects a register-free static call, so it verifies in ANY matched
 * method shape. Everything else happens here at RUNTIME.
 *
 * Model: a typeface loaded from a local FILE (not a content:// URI — nothing
 * device-independent like {@code Typeface.createFromFile} accepts a document
 * URI) is applied to the text of chat screens. Bold text stays bold by using a
 * bold variant derived from the chosen typeface, rather than flattening every
 * label to one weight.
 *
 * A missing or unreadable font file is a silent no-op: the hook simply stops,
 * so a wrong path can never crash the app or blank out text. Both variants are
 * cached and re-derived only when the configured path changes.
 *
 * CONFIG: Cenotaph Settings + the Epitaph category in Piko Settings
 * (EpitaphPref). Morphe patch on/off stays the master toggle.
 */
@SuppressWarnings("unused")
public class ChatFontHook {

    private static final int MAX_VIEWS = 3000;
    private static final long THROTTLE_MS = 1500;

    private static volatile long lastRun = 0;
    private static volatile String cachedPath;
    /** {regular, bold} for {@link #cachedPath}. */
    private static volatile Typeface[] cachedFonts;

    private ChatFontHook() {}

    public static void applyToCurrentScreen() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastRun < THROTTLE_MS) return;
            lastRun = now;

            Context appCtx = EpitaphApp.context();
            if (appCtx == null) return;
            if (!EpitaphPref.chatFontEnabled(appCtx)) return;

            String path = EpitaphPref.chatFontPath(appCtx);
            if (path == null || path.trim().isEmpty()) return; // not configured -> no-op

            Typeface[] pair = fontsFor(path);
            if (pair == null) return; // missing/unreadable file -> no-op

            boolean allScreens;
            try {
                allScreens = EpitaphPref.chatFontAllScreens(appCtx);
            } catch (Throwable t) {
                allScreens = false;
            }

            for (Activity a : EpitaphRuntime.screens()) {
                try {
                    if (a == null || a.isFinishing()) continue;
                    View content = a.findViewById(android.R.id.content);
                    if (!(content instanceof ViewGroup)) continue;
                    if (!allScreens && !WallpaperHook.containsThreadHeader((ViewGroup) content)) {
                        continue;
                    }
                    // "Chat font" means the messages, not the tab bar: scope to
                    // the message list when this build exposes it.
                    View list = allScreens ? null : Ids.find(content, Ids.MESSAGE_LIST);
                    applyFont((ViewGroup) (list instanceof ViewGroup ? list : content), pair);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // Never let a font pass take down the host.
        }
    }

    /** {regular, bold} for the configured file, cached until the path changes. */
    private static Typeface[] fontsFor(String path) {
        try {
            String p = path.trim();
            if (p.equals(cachedPath) && cachedFonts != null) return cachedFonts;

            File f = new File(p);
            if (!f.exists() || !f.isFile()) return null;

            Typeface regular = Typeface.createFromFile(f);
            if (regular == null) return null;

            Typeface bold;
            try {
                bold = Typeface.create(regular, Typeface.BOLD);
            } catch (Throwable t) {
                bold = null;
            }

            Typeface[] pair = new Typeface[] { regular, bold != null ? bold : regular };
            cachedPath = p;
            cachedFonts = pair;
            return pair;
        } catch (Throwable t) {
            return null; // bad file / unsupported format
        }
    }

    private static void applyFont(ViewGroup root, Typeface[] pair) {
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
                    Typeface cur = tv.getTypeface();
                    boolean wantBold = cur != null && cur.isBold();
                    Typeface target = wantBold ? pair[1] : pair[0];
                    // Reference compare is correct here: both variants come from
                    // the same cached pair, so re-applying is skipped cheaply.
                    if (cur != target) tv.setTypeface(target);
                }
            } catch (Throwable ignored) {
                // Skip this view, keep walking.
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                try {
                    for (int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
                } catch (Throwable ignored) {}
            }
        }
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
