package app.epitaph.extension.custom;

import android.content.Context;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Cenotaph's category inside Piko Settings, in the same shape Piko uses for its
 * own sections: one home row that opens a dedicated screen, and that screen
 * built from Piko's own widgets.
 *
 * The rows themselves come from {@link CenotaphSettings} — the same list the
 * standalone screen renders — so the two surfaces cannot drift, and a value
 * edited in either one is written to both stores.
 *
 * Why the fragment indirection: Piko's home is a list of one-row categories with
 * a chevron, and it builds each category's screen from a fragment name. A row
 * keyed {@code piko_frag_epitaph} navigates nowhere in Piko's own FragmentHook,
 * so {@link #openScreen} launches Piko's settings activity directly with the two
 * extras it already reads. The title survives because IgStr falls back to the
 * raw name for an unknown resource.
 *
 * Everything Piko-side is reached by reflection: this bundle keeps zero Piko
 * imports, and a Piko refactor degrades to "no Cenotaph row" rather than a
 * crash.
 */
public final class EpitaphPikoRows {

    /** Must start with {@code piko_frag_} or Piko's ButtonPref hides the chevron. */
    private static final String FRAGMENT_NAME = "piko_frag_epitaph";
    private static final String FRAGMENT_TITLE = "Cenotaph";

    private static final String KEY_NAME = "piko_frag_name";

    private static final String BUILDER_SUFFIX = "settings.preference.ScreenBuilder";
    private static final String BOOL_SETTING = "app.morphe.extension.crimera.settings.BooleanSetting";
    private static final String STR_SETTING = "app.morphe.extension.crimera.settings.StringSetting";
    private static final String ACTIVITY_HOOK = "app.morphe.extension.instagram.settings.ActivityHook";

    /** Builders/fragments already augmented (both are rebuilt on navigation). */
    private static final Set<Integer> done = new HashSet<Integer>();

    private EpitaphPikoRows() {}

    // ------------------------------------------------------------------
    // Piko home page
    // ------------------------------------------------------------------

    /** Adds the single "Cenotaph" row to Piko's home page. Called from the patch. */
    public static void injectHome(Object builder) {
        try {
            if (builder == null) return;
            if (!builder.getClass().getName().endsWith(BUILDER_SUFFIX)) return;
            if (!claim(builder)) return;

            Object helper = field(builder, "helper");
            Object screen = field(builder, "screen");
            if (helper == null || !(screen instanceof PreferenceScreen)) return;

            Preference row = button(helper, FRAGMENT_TITLE,
                    "Wallpaper, colour map, bubbles, font, streaks", FRAGMENT_NAME);
            if (row == null) return;

            // ButtonPref installs its own listener and abandons the click because
            // Piko does not know this fragment name. Replacing the listener keeps
            // Piko's chevron and pressed highlight.
            row.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    openScreen();
                    return true;
                }
            });

            ((PreferenceScreen) screen).addPreference(row);
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // Cenotaph screen
    // ------------------------------------------------------------------

    /**
     * Fills the Cenotaph screen, at the end of Piko's settings fragment
     * onCreate, i.e. after Piko has set an empty screen for a fragment name it
     * does not know.
     */
    public static void injectFragment(Object fragment) {
        try {
            if (fragment == null) return;

            Object argsObj = callNoArg(fragment, "getArguments");
            String name = (argsObj instanceof Bundle)
                    ? ((Bundle) argsObj).getString(KEY_NAME) : null;
            if (!FRAGMENT_NAME.equals(name)) return; // not our screen
            if (!claim(fragment)) return;

            Context ctx = (Context) callNoArg(fragment, "getActivity");
            Object manager = callNoArg(fragment, "getPreferenceManager");
            if (ctx == null || manager == null) return;

            PreferenceScreen screen = (PreferenceScreen) manager.getClass()
                    .getMethod("createPreferenceScreen", Context.class)
                    .invoke(manager, ctx);
            if (screen == null) return;

            Object helper = newHelper(ctx);
            if (helper == null) return;

            buildSections(screen, ctx, helper);

            fragment.getClass()
                    .getMethod("setPreferenceScreen", PreferenceScreen.class)
                    .invoke(fragment, screen);
        } catch (Throwable ignored) {}
    }

    /** Renders {@link CenotaphSettings} with Piko's widgets, section by section. */
    private static void buildSections(PreferenceScreen screen, Context ctx, Object helper) {
        boolean first = true;
        for (CenotaphSettings.Section section : CenotaphSettings.sections()) {
            PreferenceGroup group = category(screen, ctx, section.title, first);
            first = false;

            for (CenotaphSettings.Row row : section.rows) {
                String key = CenotaphSettings.pikoKey(row);
                switch (row.kind) {
                    case CenotaphSettings.SWITCH:
                        addSwitch(helper, group, row.title, row.summary, key,
                                "1".equals(row.def));
                        break;
                    case CenotaphSettings.NUMBER:
                        addTextLike(helper, group, "editTextNumPreference",
                                row.title, row.summary, key, row.def);
                        break;
                    case CenotaphSettings.TEXT:
                    case CenotaphSettings.CHOICE:
                        addTextLike(helper, group, "editTextPreference",
                                row.title, row.summary, key, row.def);
                        break;
                    default:
                        // Action rows (gallery picker, reset) need an Activity,
                        // so they live in the screen below.
                        break;
                }
            }
        }

        // The gallery picker and reset only exist in the launcher activity;
        // hand off to it from here. Built through Helper so the row is
        // a ButtonPref with Piko's chevron and pressed highlight.
        Preference open = button(helper, "Open Cenotaph Settings",
                "Gallery picker, reset", "piko_frag_epitaph_open");
        if (open == null) {
            open = new Preference(ctx);
            open.setTitle("Open Cenotaph Settings");
            open.setSummary("Gallery picker, reset");
        }
        open.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                try {
                    Context appCtx = EpitaphApp.context();
                    if (appCtx == null) return false;
                    android.content.Intent intent =
                            new android.content.Intent(appCtx, EpitaphSettingsActivity.class);
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    appCtx.startActivity(intent);
                    return true;
                } catch (Throwable t) {
                    return false;
                }
            }
        });
        screen.addPreference(open);
    }

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /**
     * Opens Piko's settings activity on the Cenotaph fragment. Piko's own
     * ActivityHook.startPikoActivity is public and already sets
     * FLAG_ACTIVITY_NEW_TASK, so it is preferred over hand-building an intent.
     */
    private static void openScreen() {
        try {
            Class<?> hook = Class.forName(ACTIVITY_HOOK);
            Method m = hook.getMethod("startPikoActivity", String.class, String.class);
            m.invoke(null, FRAGMENT_NAME, FRAGMENT_TITLE);
            return;
        } catch (Throwable ignored) {}

        try {
            Context ctx = EpitaphApp.context();
            if (ctx == null) return;
            Class<?> activity = Class.forName(
                    "app.morphe.extension.instagram.settings.SettingsActivity");
            android.content.Intent intent = new android.content.Intent(ctx, activity);
            intent.putExtra(KEY_NAME, FRAGMENT_NAME);
            intent.putExtra("piko_frag_title", FRAGMENT_TITLE);
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(intent);
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // Row builders (Piko widgets via reflection)
    // ------------------------------------------------------------------

    private static PreferenceGroup category(PreferenceScreen screen, Context ctx,
                                            String title, boolean first) {
        try {
            Class<?> c = Class.forName(
                    "app.morphe.extension.instagram.settings.preference.widgets.CategoryPref");
            Object cat = c.getConstructor(Context.class).newInstance(ctx);
            // Piko sets this for its own categories; without it the first
            // section gets the wrong top padding.
            c.getMethod("setFirstCategory", boolean.class).invoke(cat, first);
            c.getMethod("setTitle", CharSequence.class).invoke(cat, title);
            PreferenceGroup group = (PreferenceGroup) cat;
            screen.addPreference(group);
            return group;
        } catch (Throwable t1) {
            PreferenceCategory fallback = new PreferenceCategory(ctx);
            fallback.setTitle(title);
            screen.addPreference(fallback);
            return fallback;
        }
    }

    private static void addSwitch(Object helper, PreferenceGroup group, String title,
                                  String summary, String key, boolean def) {
        try {
            Object setting = newSetting(BOOL_SETTING, key, Boolean.valueOf(def));
            if (setting == null) return;
            Method m = helper.getClass().getMethod("switchPreference",
                    String.class, String.class, Class.forName(BOOL_SETTING));
            Object pref = m.invoke(helper, title, summary, setting);
            if (pref instanceof Preference) group.addPreference((Preference) pref);
        } catch (Throwable ignored) {}
    }

    private static void addTextLike(Object helper, PreferenceGroup group, String method,
                                    String title, String summary, String key, String def) {
        try {
            Object setting = newSetting(STR_SETTING, key, def);
            if (setting == null) return;
            Method m = helper.getClass().getMethod(method,
                    String.class, String.class, Class.forName(STR_SETTING));
            Object pref = m.invoke(helper, title, summary, setting);
            if (pref instanceof Preference) group.addPreference((Preference) pref);
        } catch (Throwable ignored) {}
    }

    private static Preference button(Object helper, String title, String summary, String key) {
        try {
            Method m = helper.getClass().getMethod("buttonPreference",
                    String.class, String.class, String.class);
            Object pref = m.invoke(helper, title, summary, key);
            if (pref instanceof Preference) return (Preference) pref;
        } catch (Throwable ignored) {}
        return null;
    }

    // ------------------------------------------------------------------
    // Reflection plumbing
    // ------------------------------------------------------------------

    private static Object newHelper(Context ctx) {
        try {
            Class<?> c = Class.forName(
                    "app.morphe.extension.instagram.settings.preference.Helper");
            return c.getConstructor(Context.class).newInstance(ctx);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object newSetting(String cls, String key, Object def) {
        try {
            Class<?> c = Class.forName(cls);
            Constructor<?> ctor = c.getConstructor(String.class,
                    def instanceof Boolean ? Boolean.class : String.class);
            return ctor.newInstance(key, def);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object callNoArg(Object o, String method) {
        try {
            return o.getClass().getMethod(method).invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object field(Object o, String name) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean claim(Object o) {
        if (o == null) return false;
        int id = System.identityHashCode(o);
        synchronized (done) {
            if (done.contains(id)) return false;
            done.add(id);
            if (done.size() > 64) done.clear();
            return true;
        }
    }
}
