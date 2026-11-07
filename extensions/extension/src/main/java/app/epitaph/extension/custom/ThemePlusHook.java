package app.epitaph.extension.custom;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * "+" entry inside Instagram's theme preview screen.
 *
 * Called with the fragment root view (signature-pinned View at patch time).
 * Overlays a "+" button once per screen; tap resolves the open thread
 * (Piko's tracker, reflection — null-safe) and opens Cenotaph Settings
 * straight into the image picker, saving the choice as that thread's
 * wallpaper override (or the global one when no thread is known).
 */
public final class ThemePlusHook {

    private static final int PLUS_BUTTON_ID = 0xE91A9003;

    private ThemePlusHook() {}

    public static void addPlus(View rootView) {
        try {
            if (rootView == null) return;
            try {
                android.content.Context prefCtx = EpitaphApp.context();
                if (prefCtx != null && !EpitaphPref.showThemePlus(prefCtx)) return;
            } catch (Throwable ignored) {}
            if (rootView.findViewById(PLUS_BUTTON_ID) != null) return;

            final Context ctx = rootView.getContext();
            final Activity activity = activityOf(ctx);

            final TextView plus = new TextView(ctx);
            plus.setId(PLUS_BUTTON_ID);
            plus.setText("+");
            plus.setTextSize(24);
            plus.setTextColor(Color.WHITE);
            plus.setGravity(Gravity.CENTER);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.parseColor("#7B1FA2"));
            bg.setCornerRadius(dp(ctx, 28));
            try { plus.setBackground(bg); } catch (Throwable ignored) {}
            int size = dp(ctx, 56);

            boolean attached = false;
            try {
                ViewGroup parent = rootView instanceof ViewGroup
                        ? (ViewGroup) rootView
                        : (ViewGroup) rootView.getParent();
                if (parent != null) {
                    try {
                        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                        lp.gravity = Gravity.END | Gravity.BOTTOM;
                        int m = dp(ctx, 24);
                        lp.setMargins(m, m, m, dp(ctx, 96));
                        parent.addView(plus, lp);
                    } catch (Throwable t) {
                        parent.addView(plus);
                    }
                    attached = true;
                }
            } catch (Throwable ignored) {}
            if (!attached) return;

            plus.setOnClickListener(v -> {
                try {
                    String threadId = EpitaphThread.currentThreadId();
                    Intent i = new Intent(ctx, EpitaphSettingsActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    if (threadId != null) i.putExtra("pick_for_thread", threadId);
                    i.putExtra("pick_wallpaper_now", true);
                    ctx.startActivity(i);
                    if (activity != null) {
                        android.widget.Toast.makeText(activity,
                                threadId != null
                                        ? "Pick wallpaper for this chat"
                                        : "Pick wallpaper (global — open thread unknown)",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static Activity activityOf(Context c) {
        Context cur = c;
        while (cur instanceof ContextWrapper) {
            if (cur instanceof Activity) return (Activity) cur;
            Context base = ((ContextWrapper) cur).getBaseContext();
            if (base == cur) return null;
            cur = base;
        }
        return cur instanceof Activity ? (Activity) cur : null;
    }

    private static int dp(Context c, int dp) {
        try {
            return Math.round(dp * c.getResources().getDisplayMetrics().density);
        } catch (Throwable t) {
            return dp * 3;
        }
    }
}
