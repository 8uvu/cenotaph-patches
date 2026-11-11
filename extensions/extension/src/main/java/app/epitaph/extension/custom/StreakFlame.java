package app.epitaph.extension.custom;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/**
 * Draws the streak flame in code: gray when inactive, orange when active.
 * No resource files, so nothing extra is merged into the APK.
 */
public final class StreakFlame extends Drawable {

    private final boolean active;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public StreakFlame(boolean active) {
        this.active = active;
        paint.setColor(active ? Color.rgb(255, 120, 20) : Color.rgb(140, 140, 140));
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        float w = b.width();
        float h = b.height();
        float x = b.left;
        float y = b.top;
        path.reset();
        path.moveTo(x + w * 0.5f, y);
        path.cubicTo(x + w * 0.95f, y + h * 0.35f, x + w * 0.85f, y + h * 0.7f, x + w * 0.5f, y + h);
        path.cubicTo(x + w * 0.15f, y + h * 0.7f, x + w * 0.05f, y + h * 0.35f, x + w * 0.5f, y);
        path.close();
        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
