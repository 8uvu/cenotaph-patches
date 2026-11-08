package app.epitaph.extension.custom;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The settings screen, described as data.
 *
 * Every row is read and written through this class, so the UI cannot drift from
 * the keys the runtime reads. v1 has exactly one feature, so it has exactly one
 * section: the screen is not allowed to advertise a feature with no hook behind
 * it, which is what the 2.x screen did with seven of them.
 */
public final class CenotaphSettings {

    public static final int SWITCH = 0;
    public static final int TEXT = 1;
    public static final int NUMBER = 2;
    public static final int CHOICE = 3;
    public static final int ACTION = 4;

    public static final String ACTION_PICK = "action:pick";
    public static final String ACTION_RESET = "action:reset";

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

        private Row(String key, int kind, String title, String summary,
                    String def, String hint, int min, int max, List<String> choices) {
            this.key = key;
            this.kind = kind;
            this.title = title;
            this.summary = summary;
            this.def = def;
            this.hint = hint;
            this.min = min;
            this.max = max;
            this.choices = choices == null ? Collections.<String>emptyList() : choices;
        }

        static Row toggle(String key, String title, String summary) {
            return new Row(key, SWITCH, title, summary, "0", null, 0, 0, null);
        }

        static Row number(String key, String title, String summary, int def, int min, int max) {
            return new Row(key, NUMBER, title, summary, String.valueOf(def), null, min, max, null);
        }

        static Row choice(String key, String title, String summary, String def, String... options) {
            return new Row(key, CHOICE, title, summary, def, null, 0, 0, Arrays.asList(options));
        }

        static Row action(String key, String title, String summary) {
            return new Row(key, ACTION, title, summary, "", null, 0, 0, null);
        }
    }

    public static final class Section {
        public final String title;
        public final List<Row> rows;

        Section(String title, List<Row> rows) {
            this.title = title;
            this.rows = rows;
        }
    }

    private CenotaphSettings() {}

    public static List<Section> sections() {
        List<Row> rows = new ArrayList<Row>();
        rows.add(Row.toggle(EpitaphPref.KEY_WALLPAPER_ENABLED, "Chat wallpaper",
                "Show your own image behind a chat. Drawn by Instagram itself."));
        rows.add(Row.action(ACTION_PICK, "Pick image", "Choose the image used behind chats."));
        rows.add(Row.choice(EpitaphPref.KEY_WALLPAPER_FIT, "Scale",
                "How the image fills the background.", "fill", "fill", "fit", "stretch"));
        rows.add(Row.number(EpitaphPref.KEY_WALLPAPER_DIM, "Dim",
                "Darken the image so text stays readable.", 0, 0, 80));
        rows.add(Row.number(EpitaphPref.KEY_WALLPAPER_BLUR, "Blur",
                "Soften the image. 0 turns it off.", 0, 0, 40));
        rows.add(Row.action(ACTION_RESET, "Reset", "Clear the wallpaper and every Cenotaph setting."));

        List<Section> sections = new ArrayList<Section>();
        sections.add(new Section("Chat wallpaper", rows));
        return sections;
    }

    public static boolean isOn(Context ctx, Row row) {
        return "1".equals(display(ctx, row));
    }

    public static String display(Context ctx, Row row) {
        if (EpitaphPref.KEY_WALLPAPER_ENABLED.equals(row.key)) {
            return EpitaphPref.wallpaperEnabled(ctx) ? "1" : "0";
        }
        if (EpitaphPref.KEY_WALLPAPER_PATH.equals(row.key)) {
            return EpitaphPref.wallpaperPath(ctx);
        }
        if (EpitaphPref.KEY_WALLPAPER_FIT.equals(row.key)) {
            return EpitaphPref.wallpaperFit(ctx);
        }
        if (EpitaphPref.KEY_WALLPAPER_DIM.equals(row.key)) {
            return String.valueOf(EpitaphPref.wallpaperDim(ctx));
        }
        if (EpitaphPref.KEY_WALLPAPER_BLUR.equals(row.key)) {
            return String.valueOf(EpitaphPref.wallpaperBlur(ctx));
        }
        if (EpitaphPref.KEY_WALLPAPER_POOL.equals(row.key)) {
            return EpitaphPref.wallpaperPool(ctx);
        }
        return row.def;
    }

    public static String set(Context ctx, Row row, String value) {
        if (EpitaphPref.KEY_WALLPAPER_ENABLED.equals(row.key)) {
            EpitaphPref.setWallpaperEnabled(ctx, "1".equals(value));
        } else if (EpitaphPref.KEY_WALLPAPER_PATH.equals(row.key)) {
            EpitaphPref.setWallpaperPath(ctx, value);
        } else if (EpitaphPref.KEY_WALLPAPER_FIT.equals(row.key)) {
            EpitaphPref.setWallpaperFit(ctx, value);
        } else if (EpitaphPref.KEY_WALLPAPER_DIM.equals(row.key)) {
            EpitaphPref.setWallpaperDim(ctx, clamp(value, row.min, row.max, 0));
        } else if (EpitaphPref.KEY_WALLPAPER_BLUR.equals(row.key)) {
            EpitaphPref.setWallpaperBlur(ctx, clamp(value, row.min, row.max, 0));
        } else if (EpitaphPref.KEY_WALLPAPER_POOL.equals(row.key)) {
            EpitaphPref.setWallpaperPool(ctx, value);
        }
        return display(ctx, row);
    }

    public static void resetAll(Context ctx) {
        EpitaphPref.resetAll(ctx);
    }

    private static int clamp(String value, int min, int max, int def) {
        try {
            int v = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Throwable t) {
            return def;
        }
    }
}
