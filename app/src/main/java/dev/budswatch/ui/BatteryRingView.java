package dev.budswatch.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import dev.budswatch.protocol.DeviceInfo;

/** Left bud / case / right bud battery as three arcs around the rim, status text in the middle. */
final class BatteryRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private DeviceInfo info;
    private String title = "", subtitle = "";

    private static final float L_START = 125, L_SWEEP = 110;
    private static final float R_START = -55, R_SWEEP = 110;
    private static final float C_START = 62, C_SWEEP = 56;

    BatteryRingView(Context c) {
        super(c);
        float sw = Ui.dp(c, 9);
        for (Paint p : new Paint[]{track, fill}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(Ui.TRACK);
        txt.setTextAlign(Paint.Align.CENTER);
    }

    void bind(DeviceInfo info, String title, String subtitle) {
        this.info = info;
        this.title = title;
        this.subtitle = subtitle;
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w);
    }

    @Override protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float pad = fill.getStrokeWidth();
        box.set(pad, pad, s - pad, s - pad);
        float cx = s / 2f, cy = s / 2f, r = box.width() / 2f;

        arc(c, L_START, L_SWEEP, info == null ? -1 : info.batteryLeft, info != null && info.chargingLeft, true);
        arc(c, R_START, R_SWEEP, info == null ? -1 : info.batteryRight, info != null && info.chargingRight, false);
        arc(c, C_START, C_SWEEP, info == null ? -1 : info.batteryCase, info != null && info.chargingCase, true);

        label(c, "左耳", info == null ? -1 : info.batteryLeft, cx - r * 0.5f, cy + r * 0.12f);
        label(c, "右耳", info == null ? -1 : info.batteryRight, cx + r * 0.5f, cy + r * 0.12f);
        label(c, "充电盒", info == null ? -1 : info.batteryCase, cx, cy + r * 0.58f);

        txt.setColor(Ui.FG);
        txt.setFakeBoldText(true);
        txt.setTextSize(Ui.dp(getContext(), 15));
        c.drawText(ellipsize(title, r * 1.3f), cx, cy - r * 0.38f, txt);
        txt.setFakeBoldText(false);
        txt.setColor(Ui.ACCENT);
        txt.setTextSize(Ui.dp(getContext(), 12));
        c.drawText(ellipsize(subtitle, r * 1.3f), cx, cy - r * 0.18f, txt);
    }

    private void arc(Canvas c, float start, float sweep, int pct, boolean charging, boolean fromStart) {
        c.drawArc(box, start, sweep, false, track);
        if (pct < 0) return;
        float f = sweep * Math.min(100, pct) / 100f;
        fill.setColor(charging ? Ui.ACCENT : (pct <= 15 ? Ui.WARN : Ui.FG));
        if (f > 0.5f) {
            if (fromStart) c.drawArc(box, start, f, false, fill);
            else c.drawArc(box, start + sweep - f, f, false, fill);
        }
    }

    private void label(Canvas c, String name, int pct, float x, float y) {
        Context ctx = getContext();
        txt.setFakeBoldText(false);
        txt.setColor(Ui.DIM);
        txt.setTextSize(Ui.dp(ctx, 11));
        c.drawText(name, x, y - Ui.dp(ctx, 4), txt);
        txt.setColor(Ui.FG);
        txt.setTextSize(Ui.dp(ctx, 14));
        c.drawText(pct < 0 ? "–" : pct + "%", x, y + Ui.dp(ctx, 12), txt);
    }

    private String ellipsize(String s, float max) {
        if (txt.measureText(s) <= max) return s;
        while (s.length() > 1 && txt.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
