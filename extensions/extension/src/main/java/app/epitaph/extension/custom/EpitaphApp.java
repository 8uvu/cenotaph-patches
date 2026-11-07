package app.epitaph.extension.custom;

import android.app.Application;

/**
 * Cached Application for hooks that receive no Context (e.g. rule readers).
 * Resolved via ActivityThread — stable AOSP API, no patch-time registers
 * involved.
 */
public final class EpitaphApp {

    private static volatile Application app;

    private EpitaphApp() {}

    public static Application context() {
        Application a = app;
        if (a != null) return a;
        synchronized (EpitaphApp.class) {
            a = app;
            if (a != null) return a;
            try {
                Class<?> at = Class.forName("android.app.ActivityThread");
                Object current = at.getMethod("currentActivityThread").invoke(null);
                Object application = at.getMethod("getApplication").invoke(current);
                if (application instanceof Application) {
                    app = (Application) application;
                    return app;
                }
            } catch (Throwable ignored) {}
            return null;
        }
    }
}
