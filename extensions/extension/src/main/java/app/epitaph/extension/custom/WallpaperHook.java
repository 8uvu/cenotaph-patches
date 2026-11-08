package app.epitaph.extension.custom;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The wallpaper decoder.
 *
 * v1 does not walk the view tree. Instagram hands this class the bitmap it is
 * about to draw behind a chat (see the Chat wallpaper hook patch), and this
 * either returns the user's image or the original untouched.
 *
 * Everything here is best-effort and silent on failure on purpose: a missing
 * file or a huge image must leave the app's own background in place, never take
 * the chat screen down.
 *
 * Diagnostics: adb logcat -s CenotaphWallpaper
 */
public final class WallpaperHook {

    private static final String TAG = "CenotaphWallpaper";

    /** Instagram draws this into a full-screen ImageView; 2048 is plenty. */
    private static final int MAX_EDGE = 2048;

    /** Cheap blur: shrink, then let the ImageView scale it back up. */
    private static final int BLUR_DOWNSCALE = 12;

    private static final Random RNG = new Random();

    private WallpaperHook() {}

    /**
     * The image the thread background should show, or {@code original}.
     *
     * Called from Instagram's own background setter, which is why there is no
     * activity check here: the call can only happen on a screen that is drawing
     * a chat background.
     */
    public static Bitmap overrideThreadBackground(Bitmap original) {
        try {
            Context ctx = EpitaphApp.context();
            if (ctx == null) return original;
            if (!EpitaphPref.wallpaperEnabled(ctx)) return original;

            String selection = resolveSelection(ctx);
            if (selection == null || selection.trim().isEmpty()) return original;

            Bitmap ours = decodeSelection(ctx, selection);
            if (ours == null) return original;
            log("wallpaper applied: " + selection);
            return ours;
        } catch (Throwable t) {
            log("overrideThreadBackground: " + t);
            return original;
        }
    }

    /** Pool first (one image picked per chat), then the single image. */
    private static String resolveSelection(Context ctx) {
        String pool = EpitaphPref.wallpaperPool(ctx);
        if (pool != null && !pool.trim().isEmpty()) {
            List<String> items = new ArrayList<String>();
            for (String line : pool.split("\n")) {
                String s = line.trim();
                if (!s.isEmpty()) items.add(s);
            }
            if (!items.isEmpty()) return items.get(RNG.nextInt(items.size()));
        }
        return EpitaphPref.wallpaperPath(ctx);
    }

    private static Bitmap decodeSelection(Context ctx, String selection) {
        Bitmap src = decodeCapped(ctx, selection);
        if (src == null) return null;

        int blur = EpitaphPref.wallpaperBlur(ctx);
        Bitmap out = blur > 0 ? soften(src, blur) : src;

        int dim = EpitaphPref.wallpaperDim(ctx);
        if (dim > 0) out = withDim(out, dim);
        return out;
    }

    /** Content URIs and plain paths, decoded at no more than {@link #MAX_EDGE}. */
    private static Bitmap decodeCapped(Context ctx, String selection) {
        try {
            BitmapFactory.Options probe = new BitmapFactory.Options();
            probe.inJustDecodeBounds = true;

            String scheme = null;
            try {
                scheme = Uri.parse(selection).getScheme();
            } catch (Throwable ignored) {}

            if ("content".equals(scheme)) {
                InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(selection));
                if (in == null) return null;
                try {
                    BitmapFactory.decodeStream(in, null, probe);
                } finally {
                    in.close();
                }
                probe.inSampleSize = sampleSize(probe.outWidth, probe.outHeight);
                probe.inJustDecodeBounds = false;
                InputStream again = ctx.getContentResolver().openInputStream(Uri.parse(selection));
                if (again == null) return null;
                try {
                    return BitmapFactory.decodeStream(again, null, probe);
                } finally {
                    again.close();
                }
            }

            BitmapFactory.decodeFile(selection, probe);
            if (probe.outWidth <= 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize(probe.outWidth, probe.outHeight);
            return BitmapFactory.decodeFile(selection, opts);
        } catch (Throwable t) {
            log("decodeCapped: " + t);
            return null;
        }
    }

    private static int sampleSize(int width, int height) {
        int size = 1;
        int longest = Math.max(width, height);
        while (longest / size > MAX_EDGE) size *= 2;
        return size;
    }

    private static Bitmap soften(Bitmap src, int radius) {
        try {
            int divisor = Math.max(2, Math.min(radius, BLUR_DOWNSCALE * 3));
            int w = Math.max(1, src.getWidth() / (divisor / 2));
            int h = Math.max(1, src.getHeight() / (divisor / 2));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            return Bitmap.createScaledBitmap(small, src.getWidth(), src.getHeight(), true);
        } catch (Throwable t) {
            log("soften: " + t);
            return src;
        }
    }

    private static Bitmap withDim(Bitmap src, int percent) {
        try {
            Bitmap out = src.copy(Bitmap.Config.ARGB_8888, true);
            if (out == null) return src;
            Canvas canvas = new Canvas(out);
            Paint paint = new Paint();
            paint.setColor(Color.BLACK);
            paint.setAlpha(Math.min(255, percent * 255 / 100));
            canvas.drawRect(0, 0, out.getWidth(), out.getHeight(), paint);
            return out;
        } catch (Throwable t) {
            log("withDim: " + t);
            return src;
        }
    }

    private static void log(String msg) {
        try {
            Log.i(TAG, msg);
        } catch (Throwable ignored) {}
    }
}
