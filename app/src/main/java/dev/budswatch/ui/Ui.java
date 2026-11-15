package dev.budswatch.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/** Colours and small view helpers. AMOLED-friendly: pure black background, few lit pixels. */
final class Ui {
    private Ui() {}
    static final int BG = 0xFF000000, FG = 0xFFFFFFFF, DIM = 0xFF9AA0A8, ACCENT = 0xFF4FA8FF,
            ACCENT2 = 0xFF3DDC97, WARN = 0xFFFF6B5E, CARD = 0xFF1A1C20, TRACK = 0xFF2A2D33;

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView centered(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static GradientDrawable pill(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Typeface bold() { return Typeface.create(Typeface.DEFAULT, Typeface.BOLD); }

    static int dim(int color, float f) {
        return Color.argb(Color.alpha(color), (int) (Color.red(color) * f), (int) (Color.green(color) * f),
                (int) (Color.blue(color) * f));
    }
}
