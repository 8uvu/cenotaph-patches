package app.epitaph.extension.custom;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Standalone Epitaph preferences. Plain SharedPreferences, zero Piko imports.
 * Backs the Cenotaph Settings launcher activity; hooks read through here so
 * Morphe on/off stays the master toggle and Settings holds the details.
 */
public final class EpitaphPref {

    private static final String FILE = "epitaph_settings";

    private EpitaphPref() {}

    private static SharedPreferences prefs(Context ctx) {
        Context app = ctx.getApplicationContext();
        return app.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * Piko Settings rows (injected by EpitaphPikoRowsPatch) persist into
     * Piko's own file, while the launcher activity writes ours. Getters
     * accept either store so both UIs stay in sync. No Piko imports —
     * plain file/keys by literal.
     */
    private static final String PIKO_FILE = "piko_settings";

    private static boolean pikoBoolean(Context ctx, String key, boolean def) {
        try {
            return ctx.getApplicationContext()
                    .getSharedPreferences(PIKO_FILE, Context.MODE_PRIVATE)
                    .getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    private static String pikoString(Context ctx, String key, String def) {
        try {
            String v = ctx.getApplicationContext()
                    .getSharedPreferences(PIKO_FILE, Context.MODE_PRIVATE)
                    .getString(key, def);
            return v != null ? v : def;
        } catch (Throwable t) {
            return def;
        }
    }

    // --- Wallpaper ---

    public static boolean wallpaperEnabled(Context ctx) {
        try {
            if (prefs(ctx).getBoolean("wallpaper_enabled", false)) return true;
            return pikoBoolean(ctx, "epitaph_wallpaper_enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setWallpaperEnabled(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("wallpaper_enabled", v).apply(); }
        catch (Throwable ignored) {}
    }

    public static String wallpaperPath(Context ctx) {
        try {
            String v = prefs(ctx).getString("wallpaper_path", "");
            if (v != null && !v.trim().isEmpty()) return v;
            String p = pikoString(ctx, "epitaph_wallpaper_path", "");
            if (p != null && !p.trim().isEmpty()) return p.trim();
            return "/sdcard/piko_wallpaper.jpg";
        } catch (Throwable t) {
            return "/sdcard/piko_wallpaper.jpg";
        }
    }

    public static void setWallpaperPath(Context ctx, String v) {
        try { prefs(ctx).edit().putString("wallpaper_path", v != null ? v : "").apply(); }
        catch (Throwable ignored) {}
    }

    // --- Wallpaper dim (10-100) ---

    public static int wallpaperDim(Context ctx) {
        try {
            int v = prefs(ctx).getInt("wallpaper_dim", -1);
            if (v < 0) v = pikoDim(ctx);
            if (v < 10) v = 10;
            if (v > 100) v = 100;
            return v;
        } catch (Throwable t) {
            return 85;
        }
    }

    private static int pikoDim(Context ctx) {
        try {
            String raw = ctx.getApplicationContext()
                    .getSharedPreferences(PIKO_FILE, Context.MODE_PRIVATE)
                    .getString("epitaph_wallpaper_dim", "");
            return Integer.parseInt(raw.trim());
        } catch (Throwable t) {
            return 85;
        }
    }

    public static void setWallpaperDim(Context ctx, int v) {
        try {
            if (v < 10) v = 10;
            if (v > 100) v = 100;
            prefs(ctx).edit().putInt("wallpaper_dim", v).apply();
        } catch (Throwable ignored) {}
    }

    /** "crop" (default) or "fit". */
    public static String wallpaperFit(Context ctx) {
        try {
            String v = prefs(ctx).getString("wallpaper_fit", "");
            if (v == null || v.trim().isEmpty()) v = pikoString(ctx, "epitaph_wallpaper_fit", "");
            v = v == null ? "" : v.trim().toLowerCase(java.util.Locale.US);
            return "fit".equals(v) ? "fit" : "crop";
        } catch (Throwable t) {
            return "crop";
        }
    }

    public static void setWallpaperFit(Context ctx, String v) {
        try { prefs(ctx).edit().putString("wallpaper_fit", v != null ? v.trim() : "crop").apply(); }
        catch (Throwable ignored) {}
    }

    // --- Hide seen receipts ---

    public static boolean hideSeen(Context ctx) {
        try {
            if (prefs(ctx).contains("hide_seen")) return prefs(ctx).getBoolean("hide_seen", false);
            return pikoBoolean(ctx, "epitaph_hide_seen", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setHideSeen(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("hide_seen", v).apply(); }
        catch (Throwable ignored) {}
    }

    // --- Chat tint (solid colour behind chats, no image needed) ---

    /** Hex colour like #101014, or "" when off. */
    public static String chatTint(Context ctx) {
        try {
            String v = prefs(ctx).getString("chat_tint", "");
            if (v != null && !v.trim().isEmpty()) return v.trim();
            String p = pikoString(ctx, "epitaph_chat_tint", "");
            return p != null ? p.trim() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setChatTint(Context ctx, String v) {
        try { prefs(ctx).edit().putString("chat_tint", v != null ? v.trim() : "").apply(); }
        catch (Throwable ignored) {}
    }

    /** Parsed tint, or null when unset/malformed. */
    public static Integer chatTintColor(Context ctx) {
        try {
            String raw = chatTint(ctx);
            if (raw.isEmpty()) return null;
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            if (hex.length() == 6) hex = "FF" + hex;
            if (hex.length() != 8) return null;
            return (int) Long.parseLong(hex, 16);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- Per-thread wallpapers (threadId=uri-or-path lines) ---

    public static String wallpaperThreads(Context ctx) {
        try {
            String v = prefs(ctx).getString("wallpaper_threads", "");
            if (v != null && !v.trim().isEmpty()) return v;
            return pikoString(ctx, "epitaph_wallpaper_threads", "");
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setWallpaperThreads(Context ctx, String v) {
        try { prefs(ctx).edit().putString("wallpaper_threads", v != null ? v : "").apply(); }
        catch (Throwable ignored) {}
    }

    /** URI/path override for one thread, or null. */
    public static String wallpaperForThread(Context ctx, String threadId) {
        try {
            if (threadId == null || threadId.isEmpty()) return null;
            String all = wallpaperThreads(ctx);
            if (all == null || all.isEmpty()) return null;
            for (String line : all.split("\\r?\\n")) {
                if (line == null) continue;
                int sep = line.indexOf('=');
                if (sep <= 0) continue;
                if (line.substring(0, sep).trim().equals(threadId)) {
                    String uri = line.substring(sep + 1).trim();
                    if (!uri.isEmpty()) return uri;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void setWallpaperForThread(Context ctx, String threadId, String uri) {
        try {
            if (threadId == null || threadId.isEmpty()) return;
            java.util.List<String> kept = new java.util.ArrayList<String>();
            String all = wallpaperThreads(ctx);
            if (all != null) {
                for (String line : all.split("\\r?\\n")) {
                    if (line == null) continue;
                    int sep = line.indexOf('=');
                    if (sep > 0 && line.substring(0, sep).trim().equals(threadId)) continue;
                    if (!line.trim().isEmpty()) kept.add(line);
                }
            }
            if (uri != null && !uri.trim().isEmpty()) kept.add(threadId + "=" + uri.trim());
            StringBuilder sb = new StringBuilder();
            for (String k : kept) sb.append(k).append('\n');
            setWallpaperThreads(ctx, sb.toString());
        } catch (Throwable ignored) {}
    }

    // --- Streaks (consecutive days a thread was opened) ---

    /** Stored as {"tid":{"last":"yyyy-MM-dd","n":3}}. */
    public static int recordStreakDay(Context ctx, String threadId) {
        try {
            if (threadId == null || threadId.isEmpty()) return 0;
            org.json.JSONObject all;
            try {
                all = new org.json.JSONObject(prefs(ctx).getString("streaks", "{}"));
            } catch (Throwable t) {
                all = new org.json.JSONObject();
            }
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.US).format(new java.util.Date());
            org.json.JSONObject e = all.optJSONObject(threadId);
            int n = 1;
            if (e != null) {
                String last = e.optString("last", "");
                if (last.equals(today)) return e.optInt("n", 1);
                n = isYesterday(last, today) ? e.optInt("n", 0) + 1 : 1;
            }
            org.json.JSONObject ne = new org.json.JSONObject();
            ne.put("last", today);
            ne.put("n", n);
            all.put(threadId, ne);
            prefs(ctx).edit().putString("streaks", all.toString()).apply();
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int getStreak(Context ctx, String threadId) {
        try {
            if (threadId == null || threadId.isEmpty()) return 0;
            org.json.JSONObject all = new org.json.JSONObject(
                    prefs(ctx).getString("streaks", "{}"));
            org.json.JSONObject e = all.optJSONObject(threadId);
            if (e == null) return 0;
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.US).format(new java.util.Date());
            String last = e.optString("last", "");
            if (last.equals(today) || isYesterday(last, today)) return e.optInt("n", 0);
            return 0; // gap — streak dead until reopened
        } catch (Throwable t) {
            return 0;
        }
    }

    private static boolean isYesterday(String last, String today) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.US);
            long diff = f.parse(today).getTime() - f.parse(last).getTime();
            return diff == 24L * 60 * 60 * 1000;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Display name captured from the thread header, stored as {threadId: name}.
     *
     * Needed because the inbox list only exposes the name text of each row —
     * nothing in the row tells us the thread id. Matching the name back to the
     * id is what lets a badge recorded inside a thread show up in the list.
     * Names are stored only for threads that were actually opened, so a thread
     * that has never been visited simply has no badge.
     */
    public static void setStreakName(Context ctx, String threadId, String name) {
        try {
            String key = threadId != null ? threadId.trim() : "";
            String norm = normName(name);
            if (key.isEmpty() || norm.isEmpty()) return;
            org.json.JSONObject all;
            try {
                all = new org.json.JSONObject(prefs(ctx).getString("streak_names", "{}"));
            } catch (Throwable t) {
                all = new org.json.JSONObject();
            }
            if (norm.equals(all.optString(key, ""))) return; // unchanged, skip write
            all.put(key, norm);
            prefs(ctx).edit().putString("streak_names", all.toString()).apply();
        } catch (Throwable ignored) {}
    }

    /** Days for a display name as shown in the DM list, 0 when unknown/expired. */
    public static int streakForName(Context ctx, String name) {
        try {
            String norm = normName(name);
            if (norm.isEmpty()) return 0;
            org.json.JSONObject names;
            try {
                names = new org.json.JSONObject(prefs(ctx).getString("streak_names", "{}"));
            } catch (Throwable t) {
                return 0;
            }
            java.util.Iterator<String> it = names.keys();
            while (it.hasNext()) {
                String tid = it.next();
                if (!norm.equals(names.optString(tid, ""))) continue;
                int n = getStreak(ctx, tid);
                if (n > 0) return n;
            }
            return 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * One-line summary of every stored streak for the inbox header, or null
     * when nothing is live. Counts conversations that are on a streak today or
     * yesterday (same window {@link #getStreak} uses), plus the best run ever
     * recorded locally.
     */
    public static String streakSummary(Context ctx) {
        try {
            org.json.JSONObject all =
                    new org.json.JSONObject(prefs(ctx).getString("streaks", "{}"));
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd",
                    java.util.Locale.US).format(new java.util.Date());
            int live = 0;
            int best = 0;
            java.util.Iterator<String> it = all.keys();
            while (it.hasNext()) {
                org.json.JSONObject e = all.optJSONObject(it.next());
                if (e == null) continue;
                int n = e.optInt("n", 0);
                if (n > best) best = n;
                String last = e.optString("last", "");
                if (last.equals(today) || isYesterday(last, today)) {
                    if (n > 0) live++;
                }
            }
            if (live == 0 && best == 0) return null;
            if (live == 0) return "\uD83D\uDD25 best " + best;
            return "\uD83D\uDD25 " + live + (live == 1 ? " chat" : " chats")
                    + " \u00b7 best " + best;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Case/space-insensitive form used for every name comparison. */
    private static String normName(String s) {
        try {
            if (s == null) return "";
            return s.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.US);
        } catch (Throwable t) {
            return "";
        }
    }

    // --- Visibility toggles (hide buttons you don't use) ---

    public static boolean showStreakBadge(Context ctx) {
        try {
            if (prefs(ctx).contains("show_streak")) return prefs(ctx).getBoolean("show_streak", true);
            return pikoBoolean(ctx, "epitaph_show_streak", true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setShowStreakBadge(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("show_streak", v).apply(); }
        catch (Throwable ignored) {}
    }

    public static boolean showThemePlus(Context ctx) {
        try {
            if (prefs(ctx).contains("show_theme_plus")) return prefs(ctx).getBoolean("show_theme_plus", true);
            return pikoBoolean(ctx, "epitaph_show_theme_plus", true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setShowThemePlus(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("show_theme_plus", v).apply(); }
        catch (Throwable ignored) {}
    }

    // --- Theme (4th patch) ---

    /** "stock", "black" or "purple". */
    public static String themeChoice(Context ctx) {
        try {
            String v = prefs(ctx).getString("theme_choice", "");
            if (v != null && !v.isEmpty()) return v;
            if (pikoBoolean(ctx, "epitaph_theme_purple", false)) return "purple";
            return "stock";
        } catch (Throwable t) {
            return "stock";
        }
    }

    public static void setThemeChoice(Context ctx, String v) {
        try { prefs(ctx).edit().putString("theme_choice", v != null ? v : "stock").apply(); }
        catch (Throwable ignored) {}
    }

    // --- Local color map ("source=>target" hex pairs, applied at runtime) ---

    public static boolean colorMapEnabled(Context ctx) {
        try {
            // Once the launcher activity has written our store it wins, so an
            // explicit "off" here is not overridden by a stale Piko row.
            if (prefs(ctx).contains("colormap_enabled")) return prefs(ctx).getBoolean("colormap_enabled", false);
            return pikoBoolean(ctx, "epitaph_colormap_enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setColorMapEnabled(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("colormap_enabled", v).apply(); }
        catch (Throwable ignored) {}
    }

    /** When false the map only touches DM thread screens. */
    public static boolean colorMapAllScreens(Context ctx) {
        try {
            if (prefs(ctx).contains("colormap_all")) return prefs(ctx).getBoolean("colormap_all", false);
            return pikoBoolean(ctx, "epitaph_colormap_all", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setColorMapAllScreens(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("colormap_all", v).apply(); }
        catch (Throwable ignored) {}
    }

    /** "#RRGGBB=>#RRGGBB" pairs, one per line. Empty means the map is off. */
    public static String colorMapEntries(Context ctx) {
        try {
            // Once our store holds the key it wins, so clearing the field here
            // is not silently undone by a leftover Piko value.
            if (prefs(ctx).contains("colormap_entries")) {
                String own = prefs(ctx).getString("colormap_entries", "");
                return own != null ? own : "";
            }
            return pikoString(ctx, "epitaph_colormap_entries", "");
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setColorMapEntries(Context ctx, String v) {
        try { prefs(ctx).edit().putString("colormap_entries", v != null ? v : "").apply(); }
        catch (Throwable ignored) {}
    }

    // --- Bubble corners (radius in dp; -1 means never configured) ---

    public static boolean bubbleShapeEnabled(Context ctx) {
        try {
            if (prefs(ctx).contains("bubble_shape_enabled")) return prefs(ctx).getBoolean("bubble_shape_enabled", false);
            return pikoBoolean(ctx, "epitaph_bubble_shape_enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setBubbleShapeEnabled(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("bubble_shape_enabled", v).apply(); }
        catch (Throwable ignored) {}
    }

    /** Corner radius in dp: 0 squares rounded surfaces, -1 disables the hook. */
    public static int bubbleRadiusDp(Context ctx) {
        try {
            // contains() first: an explicit "off" (-1) here must not be
            // overridden by the Piko row's default radius.
            if (prefs(ctx).contains("bubble_radius")) {
                return prefs(ctx).getInt("bubble_radius", -1);
            }
            String raw = pikoString(ctx, "epitaph_bubble_radius", "");
            if (raw == null || raw.trim().isEmpty()) return -1;
            int parsed = Integer.parseInt(raw.trim());
            return parsed >= 0 ? parsed : -1;
        } catch (Throwable t) {
            return -1;
        }
    }

    public static void setBubbleRadiusDp(Context ctx, int v) {
        try { prefs(ctx).edit().putInt("bubble_radius", v).apply(); }
        catch (Throwable ignored) {}
    }

    // --- Local chat font ---

    public static boolean chatFontEnabled(Context ctx) {
        try {
            if (prefs(ctx).contains("chat_font_enabled")) return prefs(ctx).getBoolean("chat_font_enabled", false);
            return pikoBoolean(ctx, "epitaph_chat_font_enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setChatFontEnabled(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("chat_font_enabled", v).apply(); }
        catch (Throwable ignored) {}
    }

    /** Real file path (not a content:// URI) of a .ttf/.otf on the device. */
    public static String chatFontPath(Context ctx) {
        try {
            if (prefs(ctx).contains("chat_font_path")) {
                String own = prefs(ctx).getString("chat_font_path", "");
                return own != null ? own.trim() : "";
            }
            return pikoString(ctx, "epitaph_chat_font_path", "");
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setChatFontPath(Context ctx, String v) {
        try { prefs(ctx).edit().putString("chat_font_path", v != null ? v.trim() : "").apply(); }
        catch (Throwable ignored) {}
    }

    /** When false the font applies to chat screens only. */
    public static boolean chatFontAllScreens(Context ctx) {
        try {
            if (prefs(ctx).contains("chat_font_all")) return prefs(ctx).getBoolean("chat_font_all", false);
            return pikoBoolean(ctx, "epitaph_chat_font_all", false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setChatFontAllScreens(Context ctx, boolean v) {
        try { prefs(ctx).edit().putBoolean("chat_font_all", v).apply(); }
        catch (Throwable ignored) {}
    }

    // --- Wallpaper blur + rotation pool ---

    /** 0 = off, 1-25 = strength (clamped). */
    public static int wallpaperBlur(Context ctx) {
        try {
            int v;
            if (prefs(ctx).contains("wallpaper_blur")) {
                v = prefs(ctx).getInt("wallpaper_blur", 0);
            } else {
                String raw = pikoString(ctx, "epitaph_wallpaper_blur", "");
                v = (raw == null || raw.trim().isEmpty()) ? 0 : Integer.parseInt(raw.trim());
            }
            if (v < 0) v = 0;
            if (v > 25) v = 25;
            return v;
        } catch (Throwable t) {
            return 0;
        }
    }

    public static void setWallpaperBlur(Context ctx, int v) {
        try {
            if (v < 0) v = 0;
            if (v > 25) v = 25;
            prefs(ctx).edit().putInt("wallpaper_blur", v).apply();
        } catch (Throwable ignored) {}
    }

    /** One image path/URI per line; the same chat always picks the same entry. */
    public static String wallpaperPool(Context ctx) {
        try {
            if (prefs(ctx).contains("wallpaper_pool")) {
                String own = prefs(ctx).getString("wallpaper_pool", "");
                return own != null ? own : "";
            }
            return pikoString(ctx, "epitaph_wallpaper_pool", "");
        } catch (Throwable t) {
            return "";
        }
    }

    public static void setWallpaperPool(Context ctx, String v) {
        try { prefs(ctx).edit().putString("wallpaper_pool", v != null ? v : "").apply(); }
        catch (Throwable ignored) {}
    }
}
