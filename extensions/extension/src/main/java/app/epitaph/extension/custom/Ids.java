package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import java.util.HashMap;
import java.util.Map;

/**
 * Instagram's own view lookup, by resource name.
 *
 * Instagram obfuscates class names but not resource ids: on 447 the DM thread
 * still exposes `message_list`, `thread_background_view`, `direct_thread_header`
 * and `row_thread_composer_voice` under exactly those names. Reading them here
 * is why the view features no longer need a patch-time anchor: an id survives
 * every rename of the classes around it, and it cannot "not match".
 *
 * Every id below was read out of a running 447 build
 * (TECNO LI9, Android 15) with `dumpsys activity top`, not guessed.
 *
 * Names are resolved through Resources once and cached — including the 0 that
 * means "not present in this build", so a missing id costs one lookup per
 * process, not one per frame.
 */
public final class Ids {

    // DM thread
    public static final String THREAD_HEADER = "direct_thread_header";
    public static final String THREAD_BACKGROUND = "thread_background_view";
    public static final String MESSAGE_LIST = "message_list";
    public static final String HEADER_AVATAR = "header_avatar";
    public static final String HEADER_TITLE = "header_title";
    public static final String HEADER_SUBTITLE = "header_subtitle";
    public static final String COMPOSER_ROOT = "row_thread_composer_container";
    public static final String COMPOSER_VOICE = "row_thread_composer_voice";
    public static final String COMPOSER_TEXT = "row_thread_composer_edittext";

    // DM inbox
    public static final String INBOX_BAR = "direct_inbox_action_bar";
    public static final String INBOX_LIST = "inbox_refreshable_thread_list_recyclerview";

    private static final String PKG = "com.instagram.android";

    private static final Map<String, Integer> CACHE = new HashMap<String, Integer>();

    private Ids() {}

    /** Resource id for an entry name, or 0 when this build does not have it. */
    public static int of(Context ctx, String entry) {
        if (ctx == null || entry == null || entry.isEmpty()) return 0;
        synchronized (CACHE) {
            Integer hit = CACHE.get(entry);
            if (hit != null) return hit;
        }
        int id = 0;
        try {
            android.content.res.Resources res = ctx.getResources();
            id = res.getIdentifier(entry, "id", PKG);
            if (id == 0) id = res.getIdentifier(entry, "id", ctx.getPackageName());
        } catch (Throwable ignored) {}
        synchronized (CACHE) {
            CACHE.put(entry, id);
        }
        return id;
    }

    /** The view with that resource name anywhere under {@code root}. */
    public static View find(View root, String entry) {
        if (root == null) return null;
        try {
            int id = of(root.getContext(), entry);
            if (id == 0) return null;
            return root.findViewById(id);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean has(View root, String entry) {
        return find(root, entry) != null;
    }

    /** Nearest Activity behind a view's context, or null. */
    public static Activity host(View v) {
        if (v == null) return null;
        try {
            Context c = v.getContext();
            for (int hops = 0; c != null && hops < 8; hops++) {
                if (c instanceof Activity) return (Activity) c;
                if (!(c instanceof ContextWrapper)) return null;
                c = ((ContextWrapper) c).getBaseContext();
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
