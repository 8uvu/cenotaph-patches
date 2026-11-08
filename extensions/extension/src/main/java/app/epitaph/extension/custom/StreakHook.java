package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Streak badges — a fire pill with the consecutive-days count.
 *
 * Two surfaces, one look:
 *  - the DM list (inbox), on each conversation's avatar;
 *  - the thread header, on the conversation's avatar.
 *
 * The badge is a {@link Drawable} added through {@link View#getOverlay()}, so
 * the host view tree is never modified: RecyclerView rows can be recycled and
 * rebound freely without leaving orphaned children behind, and touching a row
 * mid-layout cannot invalidate it.
 *
 * IDENTITY: the inbox row exposes a display name, not a thread id, while the
 * streak itself is recorded per thread id when a thread is opened. So opening
 * a thread stores id -> name here, and the list matches rows by that name.
 * A conversation that has never been opened has no badge — it also has no
 * recorded streak to show.
 *
 * Diagnostics: adb logcat -s EpitaphStreak
 */
public final class StreakHook {

    private static final String TAG = "CenotaphStreak";
    private static final int STREAK_ID = 0x57EA4001;

    /** Resource ids are resolved once; getIdentifier() is not cheap. */
    private static final Map<String, Integer> IDS = new HashMap<String, Integer>();

    /** Older builds exposed these row ids; 447 renders rows with obfuscated
     *  ids instead, so they are tried first and then the structural
     *  bestTitle/bestAvatar fallbacks take over. Verified absent on 447. */
    private static final String ID_AVATAR = "direct_action_row_avatar";
    private static final String ID_NAME = "direct_action_row_name";
    private static final String ID_INBOX_BAR = Ids.INBOX_BAR;

    private static volatile long lastPump = 0;
    private static volatile long lastHeader = 0;

    /** Views we put a badge on, so the switch going off can take them back. */
    private static final Map<View, Drawable> badged =
            new WeakHashMap<View, Drawable>();

    private StreakHook() {}

    // ------------------------------------------------------------------
    // Inbox (DM list)
    // ------------------------------------------------------------------

    /**
     * Called from a register-free injection on the RecyclerView bind path, so
     * it runs while the list scrolls and as rows appear. Throttled, and it
     * returns immediately on screens that are not the DM list.
     */
    public static void pumpInbox() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastPump < 800) return;
            lastPump = now;

            Context ctx = EpitaphApp.context();
            if (ctx == null) return;
            if (!EpitaphPref.showStreakBadge(ctx)) {
                clearAll();
                return;
            }

            for (Activity a : EpitaphRuntime.screens()) {
                if (a == null || a.isFinishing()) continue;
                View content = a.findViewById(android.R.id.content);
                if (!(content instanceof ViewGroup)) continue;

                // Gate: only the DM list carries this bar. Any other list of
                // rows (feed, explore, settings) is left untouched.
                if (findByEntryName((ViewGroup) content, ID_INBOX_BAR, 0) == null) continue;

                // The inbox list has a stable id on 447; the structural
                // fallback stays for builds that rename it.
                View byId = Ids.find(content, Ids.INBOX_LIST);
                ViewGroup list = byId instanceof ViewGroup
                        ? (ViewGroup) byId
                        : findThreadList((ViewGroup) content, 0);
                if (list == null) {
                    log("inbox found, no thread list yet");
                    continue;
                }

                int hit = 0;
                for (int i = 0; i < list.getChildCount(); i++) {
                    try {
                        if (decorateRow(list.getChildAt(i), ctx)) hit++;
                    } catch (Throwable ignored) {}
                }
                log("inbox rows=" + list.getChildCount() + " badged=" + hit);
            }
        } catch (Throwable ignored) {}
    }

    /** @return true when a badge was attached to this row. */
    private static boolean decorateRow(View row, Context ctx) {
        if (row == null) return false;

        View nameHit = findByEntryName(row, ID_NAME, 0);
        TextView nameView = nameHit instanceof TextView ? (TextView) nameHit : null;
        if (nameView == null) nameView = bestTitle(row);
        if (nameView == null) return false;

        CharSequence cs = nameView.getText();
        String name = cs != null ? cs.toString() : "";
        int days = EpitaphPref.streakForName(ctx, name);

        View avatar = findByEntryName(row, ID_AVATAR, 0);
        if (avatar == null) avatar = bestAvatar(row);
        if (avatar == null) return false;

        if (days <= 0) {
            clearBadge(avatar);
            return false;
        }
        attachBadge(avatar, days, ctx);
        log("row '" + shortName(name) + "' days=" + days
                + " avatar=" + avatar.getClass().getSimpleName());
        return true;
    }

    /**
     * Header badge, driven from {@link EpitaphRuntime} on every resume.
     *
     * This used to require a bytecode anchor that handed {@link #addStreakBadge}
     * a ViewGroup. The header has a stable id, so the runtime finds it instead:
     * the DM streaks patch now injects nothing at all.
     */
    public static void pumpHeader() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastHeader < 800) return;
            lastHeader = now;

            Context ctx = EpitaphApp.context();
            if (ctx == null) return;
            if (!EpitaphPref.showStreakBadge(ctx)) {
                clearAll();
                return;
            }

            for (Activity a : EpitaphRuntime.screens()) {
                if (a == null || a.isFinishing()) continue;
                View content = a.findViewById(android.R.id.content);
                if (content == null) continue;
                View header = Ids.find(content, Ids.THREAD_HEADER);
                if (!(header instanceof ViewGroup)) continue;
                addStreakBadge((ViewGroup) header);
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // Inbox header summary
    // ------------------------------------------------------------------

    private static final int SUMMARY_ID = 0x57EA4002;

    /**
     * One pill beside the "Messages" title: how many conversations are on a
     * live streak and the best run. Runs from the runtime dispatch, so there is
     * no per-row hook and no RecyclerView anchor involved — the summary stays
     * correct without touching a single conversation row.
     */
    public static void inboxSummary() {
        try {
            Context ctx = EpitaphApp.context();
            if (ctx == null) return;
            if (!EpitaphPref.showStreakBadge(ctx)) return;

            String label = EpitaphPref.streakSummary(ctx);

            for (Activity a : EpitaphRuntime.screens()) {
                if (a == null || a.isFinishing()) continue;
                View content = a.findViewById(android.R.id.content);
                if (!(content instanceof ViewGroup)) continue;
                ViewGroup root = (ViewGroup) content;

                // Same inbox gate the row badges use: the DM list carries this bar.
                if (findByEntryName(root, ID_INBOX_BAR, 0) == null) continue;

                TextView title = findText(root, "Messages");
                if (title == null) continue;
                if (!(title.getParent() instanceof ViewGroup)) continue;
                ViewGroup row = (ViewGroup) title.getParent();

                TextView pill = row.findViewById(SUMMARY_ID);
                if (label == null) {
                    if (pill != null) pill.setVisibility(View.GONE);
                    continue;
                }
                if (pill == null) {
                    pill = buildSummaryPill(ctx);
                    pill.setId(SUMMARY_ID);
                    int index = Math.min(row.indexOfChild(title) + 1, row.getChildCount());
                    try {
                        row.addView(pill, index, summaryParams(title));
                    } catch (Throwable t) {
                        try { row.addView(pill); } catch (Throwable ignored) { continue; }
                    }
                }
                pill.setText(label);
                pill.setVisibility(View.VISIBLE);
            }
        } catch (Throwable ignored) {}
    }

    private static TextView buildSummaryPill(Context ctx) {
        TextView pill = new TextView(ctx);
        pill.setTextSize(11);
        pill.setTextColor(0xFFFFC53D);
        pill.setTypeface(Typeface.DEFAULT_BOLD);
        pill.setGravity(android.view.Gravity.CENTER);
        int ph = dp(ctx, 7);
        pill.setPadding(ph, dp(ctx, 2), ph, dp(ctx, 2));
        pill.setBackground(capsule(ctx));
        return pill;
    }

    private static ViewGroup.LayoutParams summaryParams(View title) {
        Context ctx = title.getContext();
        ViewGroup.LayoutParams base = title.getLayoutParams();
        if (base instanceof android.widget.LinearLayout.LayoutParams) {
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = android.view.Gravity.CENTER_VERTICAL;
            lp.setMarginStart(dp(ctx, 8));
            return lp;
        }
        android.widget.FrameLayout.LayoutParams fp =
                new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        fp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START;
        fp.setMarginStart(dp(ctx, 8));
        return fp;
    }

    /** Exact-text TextView lookup; "Messages" is the inbox section title. */
    private static TextView findText(View root, String text) {
        if (root == null) return null;
        try {
            if (root instanceof TextView) {
                CharSequence cs = ((TextView) root).getText();
                if (cs != null && text.equals(cs.toString().trim())) return (TextView) root;
            }
            if (root instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) root;
                for (int i = 0; i < g.getChildCount(); i++) {
                    TextView hit = findText(g.getChildAt(i), text);
                    if (hit != null) return hit;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------------
    // Thread header
    // ------------------------------------------------------------------

    /**
     * Entry point from the thread-builder hook. Opening a thread is also the
     * moment we learn which display name belongs to this thread id, which is
     * what the inbox needs later.
     */
    public static void addStreakBadge(ViewGroup headerContainer) {
        try {
            if (headerContainer == null) return;
            Context ctx = headerContainer.getContext();

            if (!EpitaphPref.showStreakBadge(ctx)) {
                removePill(headerContainer);
                View avatar = bestAvatar(headerContainer);
                if (avatar != null) clearBadge(avatar);
                return;
            }

            String threadId = EpitaphThread.currentThreadId(ctx);
            View titleHit = Ids.find(headerContainer, Ids.HEADER_TITLE);
            TextView titleView = titleHit instanceof TextView
                    ? (TextView) titleHit
                    : bestTitle(headerContainer);
            String title = null;
            if (titleView != null && titleView.getText() != null) {
                title = titleView.getText().toString().trim();
            }

            // Learn id -> name while we are inside the conversation.
            if (threadId != null && title != null && !title.isEmpty()) {
                EpitaphPref.setStreakName(ctx, threadId, title);
            }

            int n = threadId != null ? EpitaphPref.getStreak(ctx, threadId) : 0;

            View avatar = Ids.find(headerContainer, Ids.HEADER_AVATAR);
            if (avatar == null) avatar = bestAvatar(headerContainer);
            if (avatar != null) {
                removePill(headerContainer);
                if (n > 0) attachBadge(avatar, n, ctx);
                else clearBadge(avatar);
                log("header '" + shortName(title) + "' days=" + n
                        + " avatar=" + avatar.getClass().getSimpleName());
                return;
            }

            // No avatar in the header: keep a compact pill next to the title.
            if (n <= 0) {
                removePill(headerContainer);
                return;
            }
            TextView old = (TextView) headerContainer.findViewById(STREAK_ID);
            if (old != null) {
                old.setText(fire(n));
                old.setVisibility(View.VISIBLE);
                return;
            }
            TextView pill = new TextView(ctx);
            pill.setId(STREAK_ID);
            pill.setText(fire(n));
            pill.setTextSize(12);
            pill.setTextColor(0xFFFFC53D);
            pill.setTypeface(Typeface.DEFAULT_BOLD);
            pill.setGravity(android.view.Gravity.CENTER);
            int ph = dp(ctx, 5);
            pill.setPadding(ph, dp(ctx, 1), ph, dp(ctx, 1));
            pill.setBackground(capsule(ctx));
            try {
                headerContainer.addView(pill, headerContainer.generateLayoutParams(null));
            } catch (Throwable t) {
                try { headerContainer.addView(pill); } catch (Throwable ignored) { return; }
            }
            log("header '" + shortName(title) + "' days=" + n + " pill (no avatar)");
        } catch (Throwable ignored) {}
    }

    private static void removePill(ViewGroup header) {
        try {
            View old = header.findViewById(STREAK_ID);
            if (old != null && old.getParent() instanceof ViewGroup) {
                ((ViewGroup) old.getParent()).removeView(old);
            }
        } catch (Throwable ignored) {}
    }

    private static Drawable capsule(Context ctx) {
        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setColor(0xD9111111);
        bg.setCornerRadius(dp(ctx, 10));
        bg.setStroke(Math.max(1, dp(ctx, 1)), 0x33FFC53D);
        return bg;
    }

    // ------------------------------------------------------------------
    // Badge plumbing
    // ------------------------------------------------------------------

    private static void attachBadge(View target, int days, Context ctx) {
        try {
            if (target == null) return;
            Drawable old = badged.get(target);
            if (old instanceof StreakBadge && ((StreakBadge) old).days == days) return;
            if (old != null) {
                try { target.getOverlay().remove(old); } catch (Throwable ignored) {}
            }
            StreakBadge badge = new StreakBadge(ctx, days);
            badged.put(target, badge);
            target.getOverlay().add(badge);
            target.invalidate();
        } catch (Throwable ignored) {}
    }

    private static void clearBadge(View v) {
        try {
            if (v == null) return;
            Drawable d = badged.remove(v);
            if (d != null) {
                v.getOverlay().remove(d);
                v.invalidate();
            }
        } catch (Throwable ignored) {}
    }

    private static void clearAll() {
        try {
            synchronized (badged) {
                for (Map.Entry<View, Drawable> e : badged.entrySet()) {
                    try {
                        View v = e.getKey();
                        if (v == null) continue;
                        v.getOverlay().remove(e.getValue());
                        v.invalidate();
                    } catch (Throwable ignored) {}
                }
                badged.clear();
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // Lookup
    // ------------------------------------------------------------------

    private static int idFor(View v, String entryName) {
        Integer cached = IDS.get(entryName);
        if (cached != null) return cached;
        int id = 0;
        try {
            Context ctx = v.getContext();
            id = ctx.getResources().getIdentifier(entryName, "id", ctx.getPackageName());
        } catch (Throwable ignored) {}
        IDS.put(entryName, id);
        return id;
    }

    /** View with this resource entry name, or null. */
    private static View findByEntryName(View root, String entryName, int depth) {
        if (root == null || depth > 40) return null;
        try {
            int id = idFor(root, entryName);
            if (id != 0) {
                View hit = root.findViewById(id);
                if (hit != null) return hit;
            }
        } catch (Throwable ignored) {}
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = findByEntryName(g.getChildAt(i), entryName, depth + 1);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /**
     * The vertical list with the most height. The inbox also contains a
     * horizontal notes/stories tray, which is filtered out by shape rather
     * than by name.
     */
    private static ViewGroup findThreadList(ViewGroup root, int depth) {
        if (root == null || depth > 40) return null;
        ViewGroup best = null;
        try {
            if (root.getClass().getName().endsWith("RecyclerView")
                    && root.getChildCount() > 0
                    && root.getHeight() > root.getWidth()) {
                best = root;
            }
            for (int i = 0; i < root.getChildCount(); i++) {
                View child = root.getChildAt(i);
                if (!(child instanceof ViewGroup)) continue;
                ViewGroup candidate = findThreadList((ViewGroup) child, depth + 1);
                if (candidate == null) continue;
                if (best == null || candidate.getHeight() > best.getHeight()) best = candidate;
            }
        } catch (Throwable ignored) {}
        return best;
    }

    /**
     * The name TextView of a row: the largest text that is not a timestamp,
     * a status line or the message preview.
     */
    private static TextView bestTitle(View root) {
        List<TextView> views = new ArrayList<TextView>();
        collectTextViews(root, views, new int[]{240});
        TextView best = null;
        float bestSize = -1f;
        for (TextView tv : views) {
            try {
                if (tv.getVisibility() != View.VISIBLE) continue;
                CharSequence cs = tv.getText();
                if (cs == null) continue;
                String s = cs.toString().trim();
                if (s.isEmpty() || s.length() > 40) continue;
                if (looksLikeMeta(s)) continue;
                float size = tv.getTextSize();
                if (size > bestSize) {
                    bestSize = size;
                    best = tv;
                }
            } catch (Throwable ignored) {}
        }
        return best;
    }

    private static void collectTextViews(View v, List<TextView> out, int[] budget) {
        if (v == null || budget[0]-- <= 0) return;
        try {
            if (v instanceof TextView) out.add((TextView) v);
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    collectTextViews(g.getChildAt(i), out, budget);
                }
            }
        } catch (Throwable ignored) {}
    }

    /** Timestamps, presence lines and short relative times are not names. */
    private static boolean looksLikeMeta(String s) {
        String t = s.trim();
        if (t.matches("(?i)^\\d{1,2}:\\d{2}(\\s*[ap]m)?$")) return true;
        if (t.matches("(?i)^\\d+\\s*[smhdw]$")) return true;
        if (t.matches("(?i)^(yesterday|today|just now|now|active now)$")) return true;
        if (t.matches("(?i)^(active|seen).{0,12}$")) return true;
        if (t.matches("(?i)^\\d{1,2}/\\d{1,2}/\\d{2,4}$")) return true;
        if (t.matches("^\\d+$")) return true;
        return false;
    }

    /** Leftmost roughly-square image, which is the conversation avatar. */
    private static View bestAvatar(View root) {
        List<ImageView> views = new ArrayList<ImageView>();
        collectImageViews(root, views, new int[]{240});
        ImageView best = null;
        int bestLeft = Integer.MAX_VALUE;
        for (ImageView iv : views) {
            try {
                if (iv.getVisibility() != View.VISIBLE) continue;
                // Header actions (back, call, video) are ImageButtons; they are
                // square and far left, so they must be excluded by type.
                if (iv instanceof android.widget.ImageButton) continue;
                int w = iv.getWidth();
                int h = iv.getHeight();
                if (w <= 0 || h <= 0) continue;
                if (Math.abs(w - h) > Math.max(4, w / 4)) continue; // square-ish
                if (w < dp(iv.getContext(), 28)) continue;
                int[] loc = new int[2];
                iv.getLocationInWindow(loc);
                if (loc[0] < bestLeft) {
                    bestLeft = loc[0];
                    best = iv;
                }
            } catch (Throwable ignored) {}
        }
        return best;
    }

    private static void collectImageViews(View v, List<ImageView> out, int[] budget) {
        if (v == null || budget[0]-- <= 0) return;
        try {
            if (v instanceof ImageView) out.add((ImageView) v);
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    collectImageViews(g.getChildAt(i), out, budget);
                }
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Fire pill drawn in the bottom-right corner of its host view. Reads its
     * own bounds, which the view overlay keeps in sync with the host size, so
     * it survives rows being measured at different heights.
     */
    private static final class StreakBadge extends Drawable {

        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String label;
        private final float density;
        final int days;

        StreakBadge(Context ctx, int days) {
            this.days = days;
            this.label = "\uD83D\uDD25 " + days;
            float d = 1f;
            try {
                d = ctx.getResources().getDisplayMetrics().density;
            } catch (Throwable ignored) {}
            this.density = d;
            fill.setColor(0xE6111111);
            edge.setStyle(Paint.Style.STROKE);
            edge.setStrokeWidth(Math.max(1f, d));
            edge.setColor(0x40FFC53D);
            text.setColor(0xFFFFC53D);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.LEFT);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect b = getBounds();
            if (b.width() <= 0 || b.height() <= 0) return;

            float h = Math.min(16f * density, b.height() * 0.34f);
            if (h < 8f * density) h = 8f * density;
            float pad = h * 0.30f;
            text.setTextSize(h * 0.62f);
            float tw = text.measureText(label);
            float w = tw + pad * 2f;

            float inset = Math.max(1f, density);
            float left = b.right - w - inset;
            float top = b.bottom - h - inset;
            if (left < b.left) left = b.left;
            if (top < b.top) top = b.top;

            RectF r = new RectF(left, top, left + w, top + h);
            float rad = h / 2f;
            canvas.drawRoundRect(r, rad, rad, fill);
            canvas.drawRoundRect(r, rad, rad, edge);

            Paint.FontMetrics fm = text.getFontMetrics();
            float baseline = r.centerY() - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(label, left + pad, baseline, text);
        }

        @Override
        public void setAlpha(int alpha) {
            fill.setAlpha(alpha);
            text.setAlpha(alpha);
            edge.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            fill.setColorFilter(cf);
            text.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return (int) (34f * density);
        }

        @Override
        public int getIntrinsicHeight() {
            return (int) (18f * density);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String fire(int n) {
        return "\uD83D\uDD25 " + n;
    }

    private static String shortName(String s) {
        if (s == null) return "null";
        String t = s.trim();
        return t.length() > 24 ? t.substring(0, 24) : t;
    }

    private static int dp(Context c, int dp) {
        try {
            return Math.round(dp * c.getResources().getDisplayMetrics().density);
        } catch (Throwable t) {
            return dp * 3;
        }
    }

    private static void log(String msg) {
        try { android.util.Log.i(TAG, msg); } catch (Throwable ignored) {}
    }

    /** All non-paused activities from ActivityThread.mActivities (stable AOSP). */
    private static List<Activity> resumedActivities() {
        List<Activity> out = new ArrayList<Activity>();
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object map = f.get(thread);
            if (!(map instanceof Map)) return out;
            for (Object rec : ((Map<?, ?>) map).values()) {
                if (rec == null) continue;
                try {
                    java.lang.reflect.Field paused = rec.getClass().getDeclaredField("paused");
                    paused.setAccessible(true);
                    if (paused.getBoolean(rec)) continue;
                    java.lang.reflect.Field act = rec.getClass().getDeclaredField("activity");
                    act.setAccessible(true);
                    Object a = act.get(rec);
                    if (a instanceof Activity) out.add((Activity) a);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }
}
