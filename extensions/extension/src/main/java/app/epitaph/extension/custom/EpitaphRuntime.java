package app.epitaph.extension.custom;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

/**
 * The single runtime entry point for the v1 bundle.
 *
 * Two injected call sites reach this class, and nothing else does:
 *
 *  - {@link #install()} — from Instagram's own Application (Runtime patch).
 *  - {@link #attachThreadView(View)} — from the DM thread's root-view handler
 *    (Thread anchor patch), the point where Instagram resolves its message list.
 *  - {@link #themeWallpaper(Bitmap)} — from the thread background setter
 *    (Chat wallpaper hook patch), with the bitmap the app is about to draw.
 *
 * Feature patches inject their own no-argument registration call
 * ({@link #enableWallpaper()}). The classes are merged into the APK either way,
 * so without that call a feature the user left unticked would still be live —
 * the flag is what makes unticking a patch mean something.
 *
 * Diagnostics: adb logcat -s CenotaphRuntime
 */
public final class EpitaphRuntime {

    private static final String TAG = "CenotaphRuntime";

    /** Printed with the install line so logcat identifies which bundle is live. */
    private static final String BUILD = "1.0.0";

    /** The thread screen's root view, handed over by the Thread anchor patch. */
    private static volatile java.lang.ref.WeakReference<View> threadRoot =
            new java.lang.ref.WeakReference<View>(null);

    /** Most recently resumed activity, for diagnostics and later patches. */
    private static volatile java.lang.ref.WeakReference<Activity> lastActivity =
            new java.lang.ref.WeakReference<Activity>(null);

    private static volatile boolean wallpaperEnabled;
    private static volatile boolean typingEnabled;
    private static volatile boolean adsEnabled;
    private static volatile boolean installed;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private EpitaphRuntime() {}

    // ------------------------------------------------------------------
    // Feature registration — called from the injected calls
    // ------------------------------------------------------------------

    public static boolean isEnabled(String feature) {
        if ("disable_typing_status".equals(feature)) return typingEnabled;
        if ("disable_ads".equals(feature)) return adsEnabled;
        return "wallpaper".equals(feature) && wallpaperEnabled;
    }

    public static void enableWallpaper() {
        wallpaperEnabled = true;
        log("feature enabled: wallpaper");
    }

    /** Registration call from the Disable typing status patch. */
    public static void enableDisableTypingStatus() {
        typingEnabled = true;
        log("feature enabled: disable typing status");
    }

    /** Registration call from the Disable ads patch. */
    public static void enableDisableAds() {
        adsEnabled = true;
        log("feature enabled: disable ads");
    }

    /**
     * Read by Instagram's typing-status handler. True only when the user has
     * turned the option on and the feature was registered by its patch.
     */
    public static boolean disableTypingStatus() {
        if (!typingEnabled) return false;
        Application app = EpitaphApp.context();
        return app != null && EpitaphPref.disableTypingStatus(app);
    }

    /** Read by the "Is ad pod" predicate. True hides the pod. */
    public static boolean disableAds() {
        if (!adsEnabled) return false;
        Application app = EpitaphApp.context();
        return app != null && EpitaphPref.disableAds(app);
    }

    // ------------------------------------------------------------------
    // Injected entry points
    // ------------------------------------------------------------------

    /**
     * Instagram's own thread-background setter calls this with the bitmap it is
     * about to hand to its ImageView. Returns the user's wallpaper, or the
     * bitmap unchanged when no wallpaper is configured.
     */
    public static Bitmap themeWallpaper(Bitmap original) {
        try {
            if (!wallpaperEnabled) return original;
            return WallpaperHook.overrideThreadBackground(original);
        } catch (Throwable t) {
            log("themeWallpaper: " + t);
            return original;
        }
    }

    /**
     * Handed the DM thread's root view the moment Instagram resolves its own
     * message list out of it.
     *
     * Only a pointer is stored, and nothing touches the tree from inside
     * Instagram's layout pass — that would re-enter it.
     */
    public static void attachThreadView(View root) {
        if (root == null) return;
        try {
            threadRoot = new java.lang.ref.WeakReference<View>(root);
            Activity host = activityOf(root.getContext());
            if (host != null) lastActivity = new java.lang.ref.WeakReference<Activity>(host);
            log("thread view attached: " + root.getClass().getName());
        } catch (Throwable t) {
            log("attachThreadView: " + t);
        }
    }

    /** Called from Instagram's Application (see the Runtime patch). */
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

        try {
            app.registerActivityLifecycleCallbacks(
                    new Application.ActivityLifecycleCallbacks() {
                        @Override
                        public void onActivityCreated(Activity activity, Bundle state) {}

                        @Override
                        public void onActivityStarted(Activity activity) {}

                        @Override
                        public void onActivityResumed(Activity activity) {
                            lastActivity = new java.lang.ref.WeakReference<Activity>(activity);
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

        log("installed on " + app.getClass().getName() + " (bundle " + BUILD + ")");
    }

    // ------------------------------------------------------------------
    // Accessors for later patches
    // ------------------------------------------------------------------

    /** The thread root the anchor last handed us, or null when it is gone. */
    public static View threadRoot() {
        try {
            return threadRoot.get();
        } catch (Throwable t) {
            return null;
        }
    }

    /** The screen the anchor last reported, or null when nothing is open. */
    public static Activity targetActivity() {
        try {
            return lastActivity.get();
        } catch (Throwable t) {
            return null;
        }
    }

    public static String build() {
        return BUILD;
    }

    private static Activity activityOf(Context context) {
        Context c = context;
        for (int i = 0; i < 8 && c != null; i++) {
            if (c instanceof Activity) return (Activity) c;
            if (!(c instanceof ContextWrapper)) return null;
            c = ((ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private static void log(String msg) {
        try {
            Log.i(TAG, msg);
        } catch (Throwable ignored) {}
    }
}
