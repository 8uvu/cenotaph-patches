package app.epitaph.extension.custom;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

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
    private static final String BUILD = "2.12.0";

    /** Give the host one frame to settle before walking its view tree. */
    private static final long SETTLE_MS = 150L;

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
