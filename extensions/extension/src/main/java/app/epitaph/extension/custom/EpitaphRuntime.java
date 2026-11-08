package app.epitaph.extension.custom;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * One runtime entry point for the whole bundle.
 *
 * A single injected call (see the `Cenotaph - Runtime` patch) lands in
 * Instagram's own {@link Application} class and registers activity lifecycle
 * callbacks here. Every resume then drives the hooks that are switched on.
 *
 * Feature patches inject their own no-argument registration call next to it
 * (`enableWallpaper`, `enableColorMap`, …). That is what makes leaving a patch
 * unticked in Morphe actually mean something: the classes are merged into the
 * APK either way, so without a flag every hook would run regardless of what the
 * user selected.
 *
 * Diagnostics: adb logcat -s CenotaphRuntime
 */
public final class EpitaphRuntime {

    private static final String TAG = "CenotaphRuntime";

    /** Printed with the install line so logcat identifies which bundle is live. */
    private static final String BUILD = "2.13.1";

    /** Give the host one frame to settle before walking its view tree. */
    private static final long SETTLE_MS = 150L;

    /**
     * The thread screen's root view, set by the `Thread anchor` patch.
     *
     * This is a real anchor: Instagram hands us the view at the point it
     * resolves its own message list, so the hooks below run on the screen they
     * were written for instead of on whatever activity happened to resume.
     */
    private static volatile java.lang.ref.WeakReference<View> threadRoot =
            new java.lang.ref.WeakReference<View>(null);

    private static final Set<String> ENABLED =
            Collections.synchronizedSet(new HashSet<String>());

    private static volatile boolean installed;

    /** Most recently resumed activity, for diagnostics and hook dispatch. */
    private static volatile java.lang.ref.WeakReference<Activity> lastActivity =
            new java.lang.ref.WeakReference<Activity>(null);

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final Runnable DISPATCH =
            new Runnable() {
                @Override
                public void run() {
                    dispatch();
                }
            };

    private EpitaphRuntime() {}

    // ------------------------------------------------------------------
    // Feature registration — called from the injected calls
    // ------------------------------------------------------------------

    public static boolean isEnabled(String feature) {
        return ENABLED.contains(feature);
    }

    private static void on(String feature) {
        if (ENABLED.add(feature)) log("feature enabled: " + feature);
    }

    /** Wallpaper, chat tint and the Seen-by line share one surface. */
    public static void enableWallpaper() {
        on("wallpaper");
    }

    public static void enableColorMap() {
        on("colormap");
    }

    public static void enableBubbles() {
        on("bubbles");
    }

    public static void enableFont() {
        on("font");
    }

    public static void enableStreaks() {
        on("streaks");
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Called from Instagram's own thread-background setter (see ThreadBackgroundPatch).
     *
     * Returns the user's wallpaper when one is configured, or the bitmap
     * Instagram passed in, untouched. Never throws: a bad image must not take
     * the chat screen down with it.
     */
    public static android.graphics.Bitmap themeWallpaper(android.graphics.Bitmap original) {
        try {
            if (!isEnabled("wallpaper")) return original;
            return WallpaperHook.overrideThreadBackground(original);
        } catch (Throwable t) {
            log("themeWallpaper: " + t);
            return original;
        }
    }

    /**
     * Called from the DM thread's root-view handler (see ThreadSurfacePatch).
     *
     * Only a pointer is stored here: the hooks run on the next main-thread
     * dispatch, after the thread screen has finished its own layout. Touching
     * the tree from inside Instagram's layout pass would re-enter it.
     */
    public static void attachThreadView(View root) {
        if (root == null) return;
        try {
            threadRoot = new java.lang.ref.WeakReference<View>(root);
            Activity host = activityOf(root.getContext());
            if (host != null) lastActivity = new java.lang.ref.WeakReference<Activity>(host);
            log("thread view attached: " + root.getClass().getName());
            schedule();
        } catch (Throwable t) {
            log("attachThreadView: " + t);
        }
    }

    /** Unwraps a ContextWrapper chain to the Activity that owns it, or null. */
    private static Activity activityOf(Context context) {
        Context c = context;
        for (int i = 0; i < 8 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof ContextWrapper)) return null;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    /** The thread root the anchor last handed us, or null when it is gone. */
    public static View threadRoot() {
        try {
            return threadRoot.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Every activity a hook may need to touch: the anchored thread screen first,
     * then whatever ActivityThread reports as resumed.
     *
     * The first entry is the point of the `Thread anchor` patch. On a thread
     * screen a hook no longer has to guess which activity it is looking at, and
     * everything else keeps working the way it did.
     */
    public static java.util.List<Activity> screens() {
        java.util.List<Activity> out = new java.util.ArrayList<Activity>();
        Activity target = targetActivity();
        if (target != null) out.add(target);
        for (Activity a : resumedActivities()) {
            if (a != target) out.add(a);
        }
        return out;
    }

    /** The screen the thread anchor last reported, or null when nothing is open. */
    public static Activity targetActivity() {
        try {
            return lastActivity.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Non-paused activities from ActivityThread.mActivities (stable AOSP shape). */
    private static java.util.List<Activity> resumedActivities() {
        java.util.List<Activity> out = new java.util.ArrayList<Activity>();
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("currentActivityThread").invoke(null);
            java.lang.reflect.Field f = at.getDeclaredField("mActivities");
            f.setAccessible(true);
            Object map = f.get(thread);
            if (!(map instanceof java.util.Map)) return out;
            for (Object rec : ((java.util.Map<?, ?>) map).values()) {
                if (rec == null) continue;
                try {
                    java.lang.reflect.Field paused = rec.getClass().getDeclaredField("paused");
                    paused.setAccessible(true);
                    if (paused.getBoolean(rec)) continue;
                    java.lang.reflect.Field act = rec.getClass().getDeclaredField("activity");
                    act.setAccessible(true);
                    Object a = act.get(rec);
                    if (a instanceof Activity) out.add((Activity) a);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void install() {
        if (installed) return;
        installed = true;

        Application app = null;
        try {
            app = EpitaphApp.context();
        } catch (Throwable ignored) {}
        if (app == null) {
            // attachBaseContext runs before ActivityThread records the app.
            installed = false;
            log("install: no Application yet, retrying");
            MAIN.postDelayed(
                    new Runnable() {
                        @Override
                        public void run() {
                            install();
                        }
                    },
                    300L);
            return;
        }

        final Application host = app;
        try {
            host.registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityCreated(Activity activity, Bundle state) {}

                        @Override
                        public void onActivityStarted(Activity activity) {}

                        @Override
                        public void onActivityResumed(Activity activity) {
                            lastActivity = new java.lang.ref.WeakReference<Activity>(activity);
                            schedule();
                        }

                        @Override
                        public void onActivityPaused(Activity activity) {}

                        @Override
                        public void onActivityStopped(Activity activity) {}

                        @Override
                        public void onActivitySaveInstanceState(Activity activity, Bundle out) {}

                        @Override
                        public void onActivityDestroyed(Activity activity) {}
                    });
        } catch (Throwable t) {
            installed = false;
            log("install: registerActivityLifecycleCallbacks failed: " + t);
            return;
        }

        log("installed on " + describe(host) + " (bundle " + BUILD + ")");
        schedule();
    }

    /** Debounced: a burst of resumes (deep links, back stacks) costs one pass. */
    private static void schedule() {
        try {
            MAIN.removeCallbacks(DISPATCH);
            MAIN.postDelayed(DISPATCH, SETTLE_MS);
        } catch (Throwable ignored) {}
    }

    private static void dispatch() {
        if (isEnabled("wallpaper")) {
            run("wallpaper", new Runnable() {
                @Override
                public void run() {
                    WallpaperHook.applyToCurrentScreen();
                }
            });
            run("tint", new Runnable() {
                @Override
                public void run() {
                    TintHook.applyToCurrentScreen();
                }
            });
            run("privacy", new Runnable() {
                @Override
                public void run() {
                    PrivacyHook.applyToCurrentScreen();
                }
            });
        }
        if (isEnabled("colormap")) {
            run("colormap", new Runnable() {
                @Override
                public void run() {
                    ColorMapHook.applyToCurrentScreen();
                }
            });
        }
        if (isEnabled("bubbles")) {
            run("bubbles", new Runnable() {
                @Override
                public void run() {
                    BubbleShapeHook.applyToCurrentScreen();
                }
            });
        }
        if (isEnabled("font")) {
            run("font", new Runnable() {
                @Override
                public void run() {
                    ChatFontHook.applyToCurrentScreen();
                }
            });
        }
        if (isEnabled("streaks")) {
            run("streak header", new Runnable() {
                @Override
                public void run() {
                    StreakHook.pumpHeader();
                }
            });
            run("streaks", new Runnable() {
                @Override
                public void run() {
                    StreakHook.pumpInbox();
                }
            });
            run("streak summary", new Runnable() {
                @Override
                public void run() {
                    StreakHook.inboxSummary();
                }
            });
        }
        try {
            EpitaphDiag.report(lastActivity != null ? lastActivity.get() : null);
        } catch (Throwable t) {
            log("diag: " + t);
        }
    }

    /** One failing hook never blocks the others, and never reaches Instagram. */
    private static void run(String what, Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            log(what + ": " + t);
        }
    }

    private static String describe(Application app) {
        try {
            return app.getClass().getName();
        } catch (Throwable t) {
            return "?";
        }
    }

    private static void log(String msg) {
        try {
            android.util.Log.i(TAG, msg);
        } catch (Throwable ignored) {}
    }
}
