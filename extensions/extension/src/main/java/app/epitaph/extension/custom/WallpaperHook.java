package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Standalone wallpaper. No Piko dependency, no patch-time resource mapping.
 *
 * Entry {@link #applyToCurrentScreen()} takes no arguments on purpose: the
 * patch injects a register-free static call, so it verifies in ANY matched
 * method shape. All thread-screen detection happens here at RUNTIME:
 *
 * - IG activity class names are obfuscated, so class-name sniffing
 *   (Direct/Thread/Message) is unreliable. Instead we scan each resumed
 *   activity's view hierarchy for the thread header via
 *   getResourceEntryName(id) == "direct_thread_header" — the same stable
 *   anchor Piko's wallpaper uses, resolved at runtime so no patch-time
 *   resource-mapping dependency is needed.
 *
 * CONFIG: Cenotaph Settings launcher activity (EpitaphPref): enable switch +
 * image (document picker, persistable URI — scoped-storage safe). Morphe
 * patch on/off stays the master toggle. WALLPAPER_PATH remains the
 * out-of-box file fallback.
 */
@SuppressWarnings("unused")
public class WallpaperHook {

    public static final String WALLPAPER_PATH = "/sdcard/piko_wallpaper.jpg";

    private static final int WALLPAPER_VIEW_ID = 0xE91A9001;
    private static final String THREAD_HEADER_ENTRY = "direct_thread_header";
    private static final int MAX_SCAN_VIEWS = 400;

    private static volatile long lastScan = 0;
    /** Activities already wallpapered (identity refs; finished ones pruned). */
    private static final List<WeakReference<Activity>> appliedTo =
            new ArrayList<WeakReference<Activity>>();

    /**
     * Backgrounds we replaced on the thread containers, so turning the switch
     * off can put Instagram's own back instead of leaving ours stuck there.
     * Weak keys: the map must never keep a view (or a whole thread) alive.
     */
    private static final Map<View, Drawable> replacedChrome =
            new java.util.WeakHashMap<View, Drawable>();

    public static void applyToCurrentScreen() {
        try {
            long now = SystemClock.uptimeMillis();
            if (now - lastScan < 1500) return; // throttle: anchor fires on every list layout
            lastScan = now;
            log("scan start");

            // Wallpaper applies only when its own switch is on; the thread
            // screen is repainted on every scan while that switch is on.
            android.content.Context settingsCtx = EpitaphApp.context();
            if (settingsCtx == null) { log("BAIL no application context"); return; }
            boolean wallpaperOn;
            try {
                wallpaperOn = EpitaphPref.wallpaperEnabled(settingsCtx);
            } catch (Throwable t) {
                log("BAIL prefs threw: " + t);
                return; // any prefs failure = do nothing, never crash the host
            }
            try {
                log("switch=" + wallpaperOn + " path='" + EpitaphPref.wallpaperPath(settingsCtx) + "'");
            } catch (Throwable ignored) {}

            List<Activity> resumed = EpitaphRuntime.screens();
            if (resumed.isEmpty()) { log("BAIL no resumed activity"); return; }
            log("resumed activities=" + resumed.size());

            for (Activity a : resumed) {
                try {
                    if (a == null || a.isFinishing()) continue;
                    View content = a.findViewById(android.R.id.content);
                    if (!(content instanceof ViewGroup)) continue;
                    // Only DM thread screens carry the thread header marker.
                    if (!containsThreadHeader((ViewGroup) content)) {
                        log("skip " + a.getClass().getName() + ": no direct_thread_header found");
                        continue;
                    }
                    log("THREAD SCREEN: " + a.getClass().getName());
                    // Streak day: opening the thread counts. Identity comes from
                    // EpitaphThread, which works without Piko as well.
                    try {
                        String streakTid = EpitaphThread.currentThreadId(a);
                        if (streakTid != null) EpitaphPref.recordStreakDay(a, streakTid);
                    } catch (Throwable ignored) {}
                    // Conversation overlays: own gate inside pump, every visit
                    // re-checks (new rules apply on revisit, dupes suppressed).
                    if (!wallpaperOn) {
                        // Switch turned off while the thread is open: put back
                        // what was there and let the next enable repaint.
                        clearThreadBackgrounds(a, (ViewGroup) content);
                        unmarkApplied(a);
                        continue;
                    }
                    if (isApplied(a)) { log("already wallpapered, skipping"); continue; }
                    // Precedence: per-thread override (theme "+" flow) > a pick
                    // from the rotation pool for this chat > the single
                    // configured image.
                    String selection = WALLPAPER_PATH;
                    try {
                        String threadId = EpitaphThread.currentThreadId(a);
                        String perThread = threadId != null
                                ? EpitaphPref.wallpaperForThread(a, threadId) : null;
                        if (perThread != null && !perThread.isEmpty()) {
                            selection = perThread;
                        } else {
                            String fromPool = pickFromPool(EpitaphPref.wallpaperPool(a), threadId);
                            if (fromPool != null) {
                                selection = fromPool;
                            } else {
                                String configured = EpitaphPref.wallpaperPath(a);
                                if (configured != null && !configured.trim().isEmpty()) {
                                    selection = configured.trim();
                                }
                            }
                        }
                    } catch (Throwable t) {
                        continue;
                    }
                    log("chosen image: " + selection);
                    Bitmap bmp = decodeSelection(settingsCtx, selection);
                    if (bmp == null) {
                        log("BAIL could not decode: " + selection
                                + " (file missing, or a URI we have no permission for)");
                        continue;
                    }
                    log("decoded: " + bmp.getWidth() + "x" + bmp.getHeight());
                    // Blur after decode: the sample-capped bitmap is what gets
                    // softened, so the cost is bounded and the file is untouched.
                    try {
                        int blur = EpitaphPref.wallpaperBlur(a);
                        if (blur > 0) bmp = blurBitmap(bmp, blur);
                    } catch (Throwable ignored) {}
                    if (bmp == null) continue;
                    paintThreadBackgrounds(a, (ViewGroup) content, bmp);
                } catch (Throwable ignored) {}
            }
            pruneApplied();
        } catch (Throwable ignored) {}
    }

    /**
     * Local diagnostics only. Written to logcat under a fixed tag and never
     * sent anywhere; safe to leave in place. Watch with:
     * adb logcat -s EpitaphWallpaper
     */
    private static void log(String msg) {
        try { android.util.Log.i("CenotaphWallpaper", msg); } catch (Throwable ignored) {}
    }

    private static boolean isApplied(Activity a) {
        synchronized (appliedTo) {
            for (WeakReference<Activity> ref : appliedTo) {
                if (ref.get() == a) return true;
            }
            return false;
        }
    }

    private static void markApplied(Activity a) {
        synchronized (appliedTo) {
            for (WeakReference<Activity> ref : appliedTo) {
                if (ref.get() == a) return;
            }
            appliedTo.add(new WeakReference<Activity>(a));
        }
    }

    private static void unmarkApplied(Activity a) {
        synchronized (appliedTo) {
            for (int i = appliedTo.size() - 1; i >= 0; i--) {
                if (appliedTo.get(i).get() == a) appliedTo.remove(i);
            }
        }
    }

    private static void pruneApplied() {
        synchronized (appliedTo) {
            for (int i = appliedTo.size() - 1; i >= 0; i--) {
                Activity a = appliedTo.get(i).get();
                if (a == null || a.isFinishing()) appliedTo.remove(i);
            }
        }
    }

    /** True when any view in the hierarchy carries the thread-header id. */
    static boolean containsThreadHeader(ViewGroup root) {
        // Fast path: the id is stable on 447+, so one findViewById settles it.
        try {
            if (Ids.has(root, Ids.THREAD_HEADER)) return true;
        } catch (Throwable ignored) {}
        int seen = 0;
        List<View> stack = new ArrayList<View>();
        stack.add(root);
        while (!stack.isEmpty() && seen < MAX_SCAN_VIEWS) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) continue;
            seen++;
            try {
                int id = v.getId();
                if (id > 0 && id != WALLPAPER_VIEW_ID) {
                    String entry = v.getResources().getResourceEntryName(id);
                    if (THREAD_HEADER_ENTRY.equals(entry)) return true;
                }
            } catch (Throwable ignored) {
                // Foreign-package ids or missing names — keep scanning.
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
            }
        }
        return false;
    }

    /**
     * Paints the thread's own containers with the same screen-anchored image.
     *
     * This is the only place the wallpaper is installed. It replaces the flat
     * themed background Instagram puts on the thread's containers — the view
     * that actually holds the chat colour, which is why replacing it works
     * where inserting a view behind everything could not. Only the ancestor
     * chain of the message list (or of the thread header) is touched, so
     * nothing inside a message and no other screen is affected.
     */
    private static void paintThreadBackgrounds(
            Activity host, final ViewGroup content, final Bitmap bmp) {
        // The pump runs inside RecyclerView's layout dispatch, so touching
        // backgrounds here would request another layout mid-layout. Defer it.
        final WeakReference<Activity> ref = new WeakReference<Activity>(host);
        try {
            content.post(new Runnable() {
                @Override public void run() {
                    try {
                        Activity h = ref.get();
                        if (h == null || h.isFinishing()) return;
                        // The thread's own background surface first: it is the
                        // view Instagram paints the chat colour into, it sits
                        // behind the message list, and it is sized to the
                        // screen, so no ancestor chain has to be rewritten.
                        paintBackgroundSurface(content, bmp);
                        View anchor = findThreadAnchor(content);
                        int painted = 0;
                        View v = anchor;
                        while (v != null && painted < 24) {
                            Drawable current = v.getBackground();
                            if (!(current instanceof ScreenAnchoredDrawable)
                                    || !((ScreenAnchoredDrawable) current).isFor(bmp)) {
                                if (!replacedChrome.containsKey(v)) {
                                    replacedChrome.put(v, current);
                                }
                                v.setBackground(new ScreenAnchoredDrawable(bmp, v));
                            }
                            painted++;
                            if (v == content) break;
                            android.view.ViewParent parent = v.getParent();
                            if (!(parent instanceof View)) break;
                            v = (View) parent;
                        }
                        // Only claim the screen once the thread's own views were
                        // found. If this pump fired before the fragment was
                        // attached, the anchor is just the content frame: leave
                        // the screen unmarked so the next pump retries rather
                        // than declaring it done with almost nothing painted.
                        boolean foundThreadViews = anchor != null && anchor != content;
                        if (foundThreadViews) markApplied(h);
                        log("thread chrome painted: " + painted + " views up from "
                                + (anchor != null ? anchor.getClass().getName() : "null")
                                + (foundThreadViews ? "" : " (retrying: thread views not attached yet)"));
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    /**
     * Paint the thread's dedicated background ImageView (`thread_background_view`).
     *
     * Alpha comes from the dim preference: 100 shows the image as-is, lower
     * values fade it towards Instagram's own chat colour so text stays legible.
     */
    private static void paintBackgroundSurface(ViewGroup content, Bitmap bmp) {
        try {
            View v = Ids.find(content, Ids.THREAD_BACKGROUND);
            if (!(v instanceof ImageView)) {
                log("no thread_background_view on this build; using chrome paint");
                return;
            }
            ImageView iv = (ImageView) v;
            // Fit shows the whole picture (letterboxed on Instagram's colour);
            // crop fills the screen and loses the edges. User's choice.
            iv.setScaleType("fit".equals(EpitaphPref.wallpaperFit(iv.getContext()))
                    ? ImageView.ScaleType.FIT_CENTER
                    : ImageView.ScaleType.CENTER_CROP);
            iv.setImageBitmap(bmp);
            float alpha = 1f;
            try {
                int dim = EpitaphPref.wallpaperDim(iv.getContext());
                alpha = Math.max(0.1f, Math.min(1f, dim / 100f));
            } catch (Throwable ignored) {}
            iv.setAlpha(alpha);
            if (iv.getVisibility() != View.VISIBLE) iv.setVisibility(View.VISIBLE);
            log("background surface painted, alpha=" + alpha);
        } catch (Throwable t) {
            log("background surface failed: " + t);
        }
    }

    private static void clearBackgroundSurface(ViewGroup content) {
        try {
            View v = Ids.find(content, Ids.THREAD_BACKGROUND);
            if (!(v instanceof ImageView)) return;
            ImageView iv = (ImageView) v;
            iv.setImageDrawable(null);
            iv.setAlpha(1f);
            log("background surface cleared");
        } catch (Throwable ignored) {}
    }

    /** Undo the chrome paint, restoring the backgrounds Instagram had there. */
    private static void clearThreadBackgrounds(Activity host, final ViewGroup content) {
        final WeakReference<Activity> ref = new WeakReference<Activity>(host);
        try {
            content.post(new Runnable() {
                @Override public void run() {
                    try {
                        Activity h = ref.get();
                        if (h == null || h.isFinishing()) return;
                        clearBackgroundSurface(content);
                        View anchor = findThreadAnchor(content);
                        int cleared = 0;
                        View v = anchor;
                        while (v != null && cleared < 24) {
                            if (v.getBackground() instanceof ScreenAnchoredDrawable) {
                                v.setBackground(replacedChrome.remove(v));
                                cleared++;
                            }
                            if (v == content) break;
                            android.view.ViewParent parent = v.getParent();
                            if (!(parent instanceof View)) break;
                            v = (View) parent;
                        }
                        log("thread chrome cleared: " + cleared);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    /**
     * The view whose ancestor chain is the thread: the message list when it can
     * be found, otherwise the thread header. Both sit inside the same thread
     * containers, so either chain reaches the themed backgrounds that have to
     * be replaced. Resolved by class name so the extension needs no
     * RecyclerView dependency.
     */
    private static View findThreadAnchor(ViewGroup content) {
        View list = Ids.find(content, Ids.MESSAGE_LIST);
        if (list != null) return list;
        View background = Ids.find(content, Ids.THREAD_BACKGROUND);
        if (background != null) return background;
        View header = findHeader(content);
        list = findLargestRecyclerView(content);
        if (list != null) return list;
        return header != null ? header : content;
    }

    private static View findLargestRecyclerView(ViewGroup root) {
        View best = null;
        long bestArea = -1;
        int seen = 0;
        List<View> stack = new ArrayList<View>();
        stack.add(root);
        while (!stack.isEmpty() && seen < MAX_SCAN_VIEWS) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) continue;
            seen++;
            if (extendsClass(v.getClass(), "androidx.recyclerview.widget.RecyclerView")) {
                long area = (long) v.getWidth() * (long) v.getHeight();
                if (area > bestArea) {
                    bestArea = area;
                    best = v;
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
            }
        }
        return best;
    }

    private static boolean extendsClass(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            if (name.equals(k.getName())) return true;
        }
        return false;
    }

    private static View findHeader(ViewGroup root) {
        int seen = 0;
        List<View> stack = new ArrayList<View>();
        stack.add(root);
        while (!stack.isEmpty() && seen < MAX_SCAN_VIEWS) {
            View v = stack.remove(stack.size() - 1);
            if (v == null) continue;
            seen++;
            try {
                int id = v.getId();
                if (id > 0 && THREAD_HEADER_ENTRY.equals(
                        v.getResources().getResourceEntryName(id))) {
                    return v;
                }
            } catch (Throwable ignored) {}
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) stack.add(g.getChildAt(i));
            }
        }
        return null;
    }

    /**
     * Stable pick from the rotation pool: the same chat always gets the same
     * image, so the wallpaper does not reshuffle on every screen open. Without
     * a thread identity the first entry is used rather than a random one.
     */
    private static String pickFromPool(String pool, String threadId) {
        try {
            if (pool == null || pool.trim().isEmpty()) return null;
            List<String> entries = new ArrayList<String>();
            for (String line : pool.split("\\r?\\n")) {
                if (line == null) continue;
                String t = line.trim();
                if (!t.isEmpty()) entries.add(t);
            }
            if (entries.isEmpty()) return null;
            int idx = 0;
            if (threadId != null && !threadId.isEmpty()) {
                idx = Math.abs(threadId.hashCode()) % entries.size();
            }
            return entries.get(idx);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Downscale/upscale box blur. No RenderScript, no RenderEffect (API 31+)
     * and no dependencies, so it works on every supported API level. The
     * intermediate bitmaps are recycled, keeping peak memory near the source.
     * Any failure returns the unblurred image rather than crashing.
     */
    private static Bitmap blurBitmap(Bitmap src, int radius) {
        try {
            if (src == null || radius <= 0) return src;
            int w = src.getWidth();
            int h = src.getHeight();
            if (w <= 0 || h <= 0) return src;
            int scale = radius < 2 ? 2 : (radius > 24) ? 24 : radius;
            int sw = w / scale < 1 ? 1 : w / scale;
            int sh = h / scale < 1 ? 1 : h / scale;
            Bitmap small = Bitmap.createScaledBitmap(src, sw, sh, true);
            if (small == null) return src;
            Bitmap smaller = Bitmap.createScaledBitmap(small,
                    sw / 2 < 1 ? 1 : sw / 2, sh / 2 < 1 ? 1 : sh / 2, true);
            if (smaller == null) {
                if (small != src) small.recycle();
                return src;
            }
            Bitmap out = Bitmap.createScaledBitmap(smaller, w, h, true);
            // Only recycle copies, never the caller's bitmap.
            if (small != src) small.recycle();
            if (smaller != src) smaller.recycle();
            return out != null ? out : src;
        } catch (Throwable t) {
            return src;
        }
    }

    // --- Why there is no per-message hook here (2.6.5) ---
    //
    // An earlier build hooked the painter at LX/0HUH;->A00 and painted the
    // wallpaper into it. That was wrong: it is a BUBBLE painter, not a chat
    // background painter. Read from the 439.0.0.37.89 dexes, it is called from
    // GenericHscrollAdapter.onBindViewHolder once per message item, and its
    // boolean chooses between two variants of that item's own theme — the A03
    // spec (with an LX/03Wn child holding a colour int) or the A05 provider —
    // before applying the result with a corner-radius argument. Painting it put
    // the wallpaper inside every message bubble: the opposite of a background.
    //
    // Instagram does expose the real thread background as an ordinary
    // background drawable on the thread's containers, so the wallpaper is
    // installed from applyToCurrentScreen() instead. No bytecode anchor, no
    // per-version guesswork, and nothing inside a message is touched.

    /** Same precedence the activity scan uses: thread override > pool > path. */
    private static String resolveSelection(Context ctx) {
        try {
            String threadId = EpitaphThread.currentThreadId();
            String perThread = threadId != null ? EpitaphPref.wallpaperForThread(ctx, threadId) : null;
            if (perThread != null && !perThread.isEmpty()) return perThread;
            String fromPool = pickFromPool(EpitaphPref.wallpaperPool(ctx), threadId);
            if (fromPool != null) return fromPool;
            String configured = EpitaphPref.wallpaperPath(ctx);
            return configured != null ? configured.trim() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * One wallpaper, drawn by every view in the thread's container chain.
     *
     * The same image is installed on several nested views (list, thread root,
     * fragment container, content frame). A drawable that fit itself into its
     * own bounds would make each of them show a different crop of the picture —
     * visibly broken. This one ignores its bounds for placement and instead
     * maps into the ROOT VIEW's coordinate space, so every view in the chain
     * reveals the slice of the single cover-fitted image that belongs at that
     * screen position, and the chain composites into one undisturbed image.
     *
     * Bounds are still honoured for clipping, so no view paints outside itself.
     */
    private static final class ScreenAnchoredDrawable extends Drawable {
        private final Bitmap bitmap;
        private final WeakReference<View> host;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Rect src = new Rect();
        private final RectF dst = new RectF();
        private final int[] hostLoc = new int[2];
        private final int[] rootLoc = new int[2];

        ScreenAnchoredDrawable(Bitmap bitmap, View host) {
            this.bitmap = bitmap;
            this.host = new WeakReference<View>(host);
        }

        boolean isFor(Bitmap other) {
            return other != null && other == bitmap && !bitmap.isRecycled();
        }

        @Override
        public void draw(Canvas canvas) {
            Bitmap b = bitmap;
            View v = host.get();
            if (b == null || b.isRecycled() || v == null) return;
            if (b.getWidth() <= 0 || b.getHeight() <= 0) return;

            View root = v.getRootView();
            if (root == null) return;
            int rootW = root.getWidth();
            int rootH = root.getHeight();
            if (rootW <= 0 || rootH <= 0) return;

            try {
                v.getLocationOnScreen(hostLoc);
                // The root view is not always at screen 0,0 (status bar insets).
                root.getLocationOnScreen(rootLoc);
            } catch (Throwable t) {
                return;
            }

            // Cover-fit against the whole screen, not this view's bounds.
            float scale = Math.max((float) rootW / b.getWidth(),
                    (float) rootH / b.getHeight());
            float w = b.getWidth() * scale;
            float h = b.getHeight() * scale;

            src.set(0, 0, b.getWidth(), b.getHeight());
            dst.set(0, 0, w, h);

            int save = canvas.save();
            try {
                // Canvas origin here is this view's top-left; clip to it.
                canvas.clipRect(getBounds());
                // Shift into root coordinates, then to this image's top-left.
                canvas.translate(rootLoc[0] - hostLoc[0] + (rootW - w) / 2f,
                        rootLoc[1] - hostLoc[1] + (rootH - h) / 2f);
                canvas.drawBitmap(b, src, dst, paint);
            } finally {
                canvas.restoreToCount(save);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            paint.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
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

    /** Persisted document URI (scoped-storage safe) or plain file path fallback. */
    private static Bitmap decodeSelection(android.content.Context ctx, String selection) {
        try {
            if (selection == null) return null;
            if (!selection.startsWith("/")) {
                Uri uri = Uri.parse(selection);
                ContentResolver cr = ctx.getContentResolver();
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                InputStream probe = null;
                try {
                    probe = cr.openInputStream(uri);
                    if (probe == null) return null;
                    BitmapFactory.decodeStream(probe, null, o);
                } catch (Throwable t) {
                    return null;
                } finally {
                    try { if (probe != null) probe.close(); } catch (Throwable ignored) {}
                }
                if (o.outWidth <= 0) return null;
                int sample = 1;
                while ((o.outWidth / sample) > 1440 || (o.outHeight / sample) > 2560) sample *= 2;
                o.inJustDecodeBounds = false;
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.RGB_565;
                InputStream in = null;
                try {
                    in = cr.openInputStream(uri);
                    if (in == null) return null;
                    return BitmapFactory.decodeStream(in, null, o);
                } catch (Throwable t) {
                    return null;
                } finally {
                    try { if (in != null) in.close(); } catch (Throwable ignored) {}
                }
            }
            return decodeCapped(selection);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap decodeCapped(String path) {
        try {
            if (path == null) return null;
            java.io.File f = new java.io.File(path.trim());
            if (!f.exists()) return null;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while ((o.outWidth / sample) > 1440 || (o.outHeight / sample) > 2560) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            return null;
        }
    }
}
