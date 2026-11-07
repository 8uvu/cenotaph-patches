package app.epitaph.extension.custom;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The one definition of every Cenotaph setting.
 *
 * Before this existed, the standalone Settings activity and the Piko settings
 * screen each carried a hand-written row list, and the two drifted: a row could
 * exist in one screen and not the other, and a value edited in one surface
 * stayed stale in the other. Both surfaces now render THIS list.
 *
 * Writes go to both stores: the bundle's own "epitaph_settings" file, which the
 * hooks read, and Piko's "piko_settings" file under "epitaph_<key>", which the
 * Piko rows and their Scaffolding read. That keeps the two surfaces in sync in
 * both directions without either side importing Piko.
 */
public final class CenotaphSettings {

    public static final int SWITCH = 0;
    public static final int TEXT = 1;
    public static final int NUMBER = 2;
    public static final int CHOICE = 3;
    public static final int ACTION = 4;

    public static final String ACTION_PICK = "action:pick";
    public static final String ACTION_RESET = "action:reset";

    private static final String PIKO_FILE = "piko_settings";
    private static final String PIKO_PREFIX = "epitaph_";

    /** One configurable row. Immutable; the value always comes from storage. */
    public static final class Row {
        public final String key;
        public final int kind;
        public final String title;
        public final String summary;
        public final String def;
        public final String hint;
        public final int min;
        public final int max;
        public final List<String> choices;

        private Row(String key, int kind, String title, String summary, String def,
                    String hint, int min, int max, List<String> choices) {
            this.key = key;
            this.kind = kind;
            this.title = title;
            this.summary = summary;
            this.def = def;
            this.hint = hint;
            this.min = min;
            this.max = max;
            this.choices = choices;
        }
    }

    public static final class Section {
        public final String title;
        public final List<Row> rows;

        private Section(String title, List<Row> rows) {
            this.title = title;
            this.rows = Collections.unmodifiableList(rows);
        }
    }

    private static Row toggle(String key, String title, String summary, boolean def) {
        return new Row(key, SWITCH, title, summary, def ? "1" : "0", "", 0, 0,
                Collections.<String>emptyList());
    }

    private static Row text(String key, String title, String summary, String def, String hint) {
        return new Row(key, TEXT, title, summary, def, hint, 0, 0,
                Collections.<String>emptyList());
    }

    private static Row number(String key, String title, String summary, String def,
                              int min, int max) {
        return new Row(key, NUMBER, title, summary, def, "", min, max,
                Collections.<String>emptyList());
    }

    private static Row choice(String key, String title, String summary, String def,
                             String... values) {
        List<String> list = new ArrayList<String>();
        Collections.addAll(list, values);
        return new Row(key, CHOICE, title, summary, def, "", 0, 0, list);
    }

    private static Row action(String key, String title, String summary) {
        return new Row(key, ACTION, title, summary, "", "", 0, 0,
                Collections.<String>emptyList());
    }

    private static List<Section> build() {
        List<Section> sections = new ArrayList<Section>();

        List<Row> wallpaper = new ArrayList<Row>();
        wallpaper.add(toggle("wallpaper_enabled", "Wallpaper in chats",
                "Background image behind DM threads", false));
        wallpaper.add(text("wallpaper_path", "Wallpaper image",
                "Picked image, or a path such as /sdcard/piko_wallpaper.jpg",
                "", "/sdcard/piko_wallpaper.jpg"));
        wallpaper.add(choice("wallpaper_fit", "Wallpaper fit",
                "crop fills the screen, fit shows the whole image", "crop", "crop", "fit"));
        wallpaper.add(number("wallpaper_dim", "Dim",
                "10-100, higher keeps more of the image", "85", 10, 100));
        wallpaper.add(number("wallpaper_blur", "Blur", "0-25, 0 = off", "0", 0, 25));
        wallpaper.add(text("wallpaper_pool", "Rotation pool",
                "One image per line — each chat keeps its own pick", "", ""));
        wallpaper.add(text("chat_tint", "Chat tint",
                "#RRGGBB behind chats when no wallpaper is set (blank = off)", "", "#101014"));
        wallpaper.add(toggle("hide_seen", "Hide seen receipts",
                "Hides the Seen by … line in the DM list and in threads", false));
        sections.add(new Section("Chat wallpaper", wallpaper));

        List<Row> colour = new ArrayList<Row>();
        colour.add(toggle("colormap_enabled", "Colour map",
                "Recolour the UI by hex pair, local only", false));
        colour.add(text("colormap_entries", "Colour pairs",
                "One #RRGGBB=>#RRGGBB pair per line", "", "#262626=>#2A1B3D"));
        colour.add(toggle("colormap_all", "Apply everywhere",
                "Apply outside chats too", false));
        sections.add(new Section("Colour map", colour));

        List<Row> bubbles = new ArrayList<Row>();
        bubbles.add(toggle("bubble_shape_enabled", "Bubble corners",
                "Corner radius for already-rounded chat surfaces", false));
        bubbles.add(number("bubble_radius", "Corner radius",
                "dp — 18 round, 0 square, 24 pill (blank = off)", "18", 0, 64));
        bubbles.add(toggle("chat_font_enabled", "Local chat font",
                "Apply a local font file to chat text", false));
        bubbles.add(text("chat_font_path", "Font file",
                "Real file path (.ttf/.otf), not a document URI", "",
                "/sdcard/Android/data/com.instagram.android/files/MyFont.ttf"));
        bubbles.add(toggle("chat_font_all", "Font everywhere",
                "Apply outside chats too", false));
        sections.add(new Section("Bubbles and font", bubbles));

        List<Row> conversation = new ArrayList<Row>();
        conversation.add(toggle("show_streak", "Streak badges",
                "Fire count in the DM list and on the thread header", true));
        conversation.add(toggle("show_theme_plus", "Theme + button",
                "Wallpaper picker inside the theme preview", true));
        sections.add(new Section("Conversation", conversation));

        List<Row> tools = new ArrayList<Row>();
        tools.add(action(ACTION_PICK, "Pick wallpaper image", "Choose from the gallery"));
        tools.add(action(ACTION_RESET, "Reset all Cenotaph settings",
                "Clears every value above"));
        sections.add(new Section("Tools", tools));

        return Collections.unmodifiableList(sections);
    }

    private static final List<Section> SECTIONS = build();

    private CenotaphSettings() {}

    public static List<Section> sections() {
        return SECTIONS;
    }

    /** Key the Piko widgets are created with, e.g. "epitaph_wallpaper_enabled". */
    public static String pikoKey(Row row) {
        return PIKO_PREFIX + row.key;
    }

    // ------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------

    public static boolean isOn(Context ctx, Row row) {
        return "1".equals(display(ctx, row));
    }

    public static String display(Context ctx, Row row) {
        try {
            String key = row.key;
            if ("wallpaper_enabled".equals(key)) return bool(EpitaphPref.wallpaperEnabled(ctx));
            if ("hide_seen".equals(key)) return bool(EpitaphPref.hideSeen(ctx));
            if ("colormap_enabled".equals(key)) return bool(EpitaphPref.colorMapEnabled(ctx));
            if ("colormap_all".equals(key)) return bool(EpitaphPref.colorMapAllScreens(ctx));
            if ("bubble_shape_enabled".equals(key)) return bool(EpitaphPref.bubbleShapeEnabled(ctx));
            if ("chat_font_enabled".equals(key)) return bool(EpitaphPref.chatFontEnabled(ctx));
            if ("chat_font_all".equals(key)) return bool(EpitaphPref.chatFontAllScreens(ctx));
            if ("show_streak".equals(key)) return bool(EpitaphPref.showStreakBadge(ctx));
            if ("show_theme_plus".equals(key)) return bool(EpitaphPref.showThemePlus(ctx));

            if ("wallpaper_path".equals(key)) return EpitaphPref.wallpaperPath(ctx);
            if ("wallpaper_fit".equals(key)) return EpitaphPref.wallpaperFit(ctx);
            if ("wallpaper_pool".equals(key)) return EpitaphPref.wallpaperPool(ctx);
            if ("chat_tint".equals(key)) return EpitaphPref.chatTint(ctx);
            if ("colormap_entries".equals(key)) return EpitaphPref.colorMapEntries(ctx);
            if ("chat_font_path".equals(key)) return EpitaphPref.chatFontPath(ctx);

            if ("wallpaper_dim".equals(key)) return String.valueOf(EpitaphPref.wallpaperDim(ctx));
            if ("wallpaper_blur".equals(key)) return String.valueOf(EpitaphPref.wallpaperBlur(ctx));
            if ("bubble_radius".equals(key)) {
                int dp = EpitaphPref.bubbleRadiusDp(ctx);
                return dp < 0 ? "" : String.valueOf(dp);
            }
            return "";
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * Applies a value from either surface and mirrors it into the other store.
     * Returns the stored value, already clamped/normalised, so a dialog can show
     * what actually got saved.
     */
    public static String set(Context ctx, Row row, String raw) {
        String stored = raw == null ? "" : raw.trim();
        try {
            switch (row.kind) {
                case SWITCH: {
                    boolean on = "1".equals(stored);
                    if (on) stored = "1";
                    else stored = "0";
                    setBool(ctx, row.key, on);
                    break;
                }
                case NUMBER: {
                    if (stored.isEmpty()) {
                        if ("bubble_radius".equals(row.key)) {
                            EpitaphPref.setBubbleRadiusDp(ctx, -1);
                            stored = "";
                        } else {
                            stored = row.def;
                            setNumber(ctx, row.key, Integer.parseInt(stored));
                        }
                    } else {
                        int v = Integer.parseInt(stored);
                        if (v < row.min) v = row.min;
                        if (v > row.max) v = row.max;
                        setNumber(ctx, row.key, v);
                        stored = String.valueOf(v);
                    }
                    break;
                }
                case CHOICE: {
                    boolean known = false;
                    for (String c : row.choices) {
                        if (c.equalsIgnoreCase(stored)) {
                            stored = c;
                            known = true;
                            break;
                        }
                    }
                    if (!known) stored = row.def;
                    setText(ctx, row.key, stored);
                    break;
                }
                case TEXT: {
                    setText(ctx, row.key, stored);
                    break;
                }
                default:
                    return "";
            }
        } catch (Throwable ignored) {
            // A malformed value never gets persisted half-written.
            return display(ctx, row);
        }
        mirror(ctx, row, stored);
        return stored;
    }

    /** Clears every Cenotaph key from both stores, leaving Piko's own settings alone. */
    public static void resetAll(Context ctx) {
        try {
            own(ctx).edit().clear().apply();
        } catch (Throwable ignored) {}
        try {
            SharedPreferences.Editor e = piko(ctx).edit();
            for (Section s : SECTIONS) {
                for (Row r : s.rows) {
                    if (r.kind == ACTION) continue;
                    e.remove(PIKO_PREFIX + r.key);
                }
            }
            e.apply();
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // Storage
    // ------------------------------------------------------------------

    private static SharedPreferences own(Context ctx) {
        return ctx.getApplicationContext()
                .getSharedPreferences("epitaph_settings", Context.MODE_PRIVATE);
    }

    private static SharedPreferences piko(Context ctx) {
        try {
            return ctx.getApplicationContext()
                    .getSharedPreferences(PIKO_FILE, Context.MODE_PRIVATE);
        } catch (Throwable t) {
            return own(ctx);
        }
    }

    private static void mirror(Context ctx, Row row, String stored) {
        try {
            SharedPreferences.Editor e = piko(ctx).edit();
            if (row.kind == SWITCH) e.putBoolean(PIKO_PREFIX + row.key, "1".equals(stored));
            else e.putString(PIKO_PREFIX + row.key, stored);
            e.apply();
        } catch (Throwable ignored) {}
    }

    private static String bool(boolean v) {
        return v ? "1" : "0";
    }

    private static void setBool(Context ctx, String key, boolean v) {
        if ("wallpaper_enabled".equals(key)) EpitaphPref.setWallpaperEnabled(ctx, v);
        else if ("hide_seen".equals(key)) EpitaphPref.setHideSeen(ctx, v);
        else if ("colormap_enabled".equals(key)) EpitaphPref.setColorMapEnabled(ctx, v);
        else if ("colormap_all".equals(key)) EpitaphPref.setColorMapAllScreens(ctx, v);
        else if ("bubble_shape_enabled".equals(key)) EpitaphPref.setBubbleShapeEnabled(ctx, v);
        else if ("chat_font_enabled".equals(key)) EpitaphPref.setChatFontEnabled(ctx, v);
        else if ("chat_font_all".equals(key)) EpitaphPref.setChatFontAllScreens(ctx, v);
        else if ("show_streak".equals(key)) EpitaphPref.setShowStreakBadge(ctx, v);
        else if ("show_theme_plus".equals(key)) EpitaphPref.setShowThemePlus(ctx, v);
    }

    private static void setNumber(Context ctx, String key, int v) {
        if ("wallpaper_dim".equals(key)) EpitaphPref.setWallpaperDim(ctx, v);
        else if ("wallpaper_blur".equals(key)) EpitaphPref.setWallpaperBlur(ctx, v);
        else if ("bubble_radius".equals(key)) EpitaphPref.setBubbleRadiusDp(ctx, v);
    }

    private static void setText(Context ctx, String key, String v) {
        if ("wallpaper_path".equals(key)) EpitaphPref.setWallpaperPath(ctx, v);
        else if ("wallpaper_fit".equals(key)) EpitaphPref.setWallpaperFit(ctx, v);
        else if ("wallpaper_pool".equals(key)) EpitaphPref.setWallpaperPool(ctx, v);
        else if ("chat_tint".equals(key)) EpitaphPref.setChatTint(ctx, v);
        else if ("colormap_entries".equals(key)) EpitaphPref.setColorMapEntries(ctx, v);
        else if ("chat_font_path".equals(key)) EpitaphPref.setChatFontPath(ctx, v);
    }
}
