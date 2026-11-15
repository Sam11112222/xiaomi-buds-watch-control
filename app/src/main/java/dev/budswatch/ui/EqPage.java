package dev.budswatch.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/**
 * 10-band custom EQ drawn for a round screen. Tap a bar to select it (or drag it), turn the crown to change
 * its gain. Changes are sent ~0.4 s after the last adjustment.
 */
public final class EqPage implements Page {
    private static final int[] DEFAULT_FREQS = {62, 125, 250, 500, 1000, 2000, 4000, 8000, 12000, 16000};
    private final Host host;
    private final EqView view;
    private final List<int[]> bands = new ArrayList<>();
    private int bound = 6, selected = 0;
    private boolean dirty, edited;
    private final Runnable send = this::sendNow;

    public EqPage(Context ctx, Host host) {
        this.host = host;
        view = new EqView(ctx);
        load();
        Session s = host.session();
        if (s != null) s.fetchConfigs(Configs.CUSTOM_EQ);
    }

    private void load() {
        Session s = host.session();
        ConfigItem c = s == null ? null : s.config(Configs.CUSTOM_EQ);
        Configs.Eq e = Configs.parseEq(c);
        if (dirty || edited) return;
        bands.clear();
        String saved = view.getContext().getSharedPreferences("budswatch", Context.MODE_PRIVATE)
                .getString("eq_curve", null);
        boolean deviceFlat = true;
        for (int[] b : e.bands) if (b[1] != 0) deviceFlat = false;
        if (saved != null && deviceFlat) {
            // The earbuds report a flat curve after a custom write; show the curve the user last saved here.
            for (String part : saved.split(";")) {
                String[] fg = part.split(":");
                if (fg.length == 2) bands.add(new int[]{Integer.parseInt(fg[0]), Integer.parseInt(fg[1])});
            }
        } else if (e.bands.isEmpty()) {
            for (int f : DEFAULT_FREQS) bands.add(new int[]{f, 0});
        } else {
            bands.addAll(e.bands);
        }
        if (bands.isEmpty()) for (int f : DEFAULT_FREQS) bands.add(new int[]{f, 0});
        bound = Math.max(1, Math.min(12, e.bound));
        selected = Math.min(selected, bands.size() - 1);
    }

    @Override public View view() { return view; }

    @Override public void onChanged() { load(); view.invalidate(); }

    @Override public boolean onRotary(float delta) {
        if (Math.abs(delta) < 0.05f) return true;
        adjust(selected, delta > 0 ? 1 : -1);
        return true;
    }

    private void adjust(int i, int step) {
        int[] b = bands.get(i);
        int g = Math.max(-bound, Math.min(bound, b[1] + step));
        if (g == b[1]) return;
        b[1] = g;
        changed();
    }

    private void setGain(int i, int g) {
        g = Math.max(-bound, Math.min(bound, g));
        if (bands.get(i)[1] == g) return;
        bands.get(i)[1] = g;
        changed();
    }

    private final Runnable preview = this::previewNow;

    private void changed() {
        dirty = true;
        view.invalidate();
        view.removeCallbacks(preview);
        view.postDelayed(preview, 150);
        view.removeCallbacks(send);
        view.postDelayed(send, 1200);
    }

    private void previewNow() {
        Session s = host.session();
        if (s == null) return;
        List<int[]> copy = new ArrayList<>();
        for (int[] b : bands) copy.add(new int[]{b[0], b[1]});
        s.set(Configs.previewEq(copy));
    }

    private void sendNow() {
        Session s = host.session();
        if (s == null) return;
        List<int[]> copy = new ArrayList<>();
        for (int[] b : bands) copy.add(new int[]{b[0], b[1]});
        s.set(Configs.setCustomEq(copy));
        ConfigItem se = s.config(Configs.SOUND_EFFECT);
        if (se == null || se.u8(0, 0) != 10) s.set(Configs.u8(Configs.SOUND_EFFECT, 10));
        StringBuilder sb = new StringBuilder();
        for (int[] b : copy) sb.append(b[0]).append(':').append(b[1]).append(';');
        view.getContext().getSharedPreferences("budswatch", Context.MODE_PRIVATE).edit()
                .putString("eq_curve", sb.toString()).apply();
        dirty = false;
        edited = true;
    }

    private void reset() {
        for (int[] b : bands) b[1] = 0;
        changed();
    }

    private static String freq(int f) { return f >= 1000 ? (f % 1000 == 0 ? f / 1000 + "k" : (f / 100) / 10f + "k") : String.valueOf(f); }

    private final class EqView extends FrameLayout {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float plotL, plotR, plotT, plotB;

        EqView(Context c) {
            super(c);
            setWillNotDraw(false);
            setBackgroundColor(Ui.BG);
            p.setTextAlign(Paint.Align.CENTER);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            Context ctx = getContext();
            float w = getWidth(), h = getHeight();
            plotL = w * 0.12f; plotR = w * 0.88f; plotT = h * 0.27f; plotB = h * 0.70f;
            p.setColor(Ui.FG);
            p.setTextSize(Ui.dp(ctx, 15));
            p.setFakeBoldText(true);
            c.drawText("自定义 EQ", w / 2, h * 0.14f, p);
            p.setFakeBoldText(false);
            int[] sel = bands.get(selected);
            p.setColor(Ui.ACCENT);
            p.setTextSize(Ui.dp(ctx, 13));
            c.drawText(freq(sel[0]) + "Hz  " + (sel[1] > 0 ? "+" : "") + sel[1] + " dB", w / 2, h * 0.21f, p);

            float mid = (plotT + plotB) / 2;
            p.setColor(Ui.TRACK);
            p.setStrokeWidth(Ui.dp(ctx, 1));
            c.drawLine(plotL, mid, plotR, mid, p);
            int n = bands.size();
            float slot = (plotR - plotL) / n;
            float bw = slot * 0.55f;
            for (int i = 0; i < n; i++) {
                float cx = plotL + slot * (i + 0.5f);
                r.set(cx - bw / 2, plotT, cx + bw / 2, plotB);
                p.setColor(Ui.CARD);
                c.drawRoundRect(r, bw / 2, bw / 2, p);
                float y = mid - (bands.get(i)[1] / (float) bound) * (plotB - plotT) / 2;
                r.set(cx - bw / 2, Math.min(y, mid), cx + bw / 2, Math.max(y, mid));
                p.setColor(i == selected ? Ui.ACCENT : Ui.dim(Ui.ACCENT, 0.55f));
                c.drawRect(r, p);
                c.drawCircle(cx, y, bw * 0.62f, p);
                p.setColor(i == selected ? Ui.FG : Ui.DIM);
                p.setTextSize(Ui.dp(ctx, 8));
                c.drawText(freq(bands.get(i)[0]), cx, plotB + Ui.dp(ctx, 12), p);
            }
            p.setColor(Ui.DIM);
            p.setTextSize(Ui.dp(ctx, 10));
            c.drawText("点选频段 · 旋转表冠调节", w / 2, h * 0.80f, p);
            r.set(w * 0.36f, h * 0.83f, w * 0.64f, h * 0.91f);
            p.setColor(Ui.CARD);
            c.drawRoundRect(r, r.height() / 2, r.height() / 2, p);
            p.setColor(Ui.FG);
            p.setTextSize(Ui.dp(ctx, 12));
            c.drawText("重置", w / 2, r.centerY() + Ui.dp(ctx, 4), p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x = e.getX(), y = e.getY();
            float h = getHeight(), w = getWidth();
            if (e.getAction() == MotionEvent.ACTION_UP && y > h * 0.82f && y < h * 0.93f
                    && x > w * 0.3f && x < w * 0.7f) { reset(); return true; }
            if (x < plotL - 10 || x > plotR + 10 || y < plotT - 30 || y > plotB + 30) return true;
            int n = bands.size();
            int i = (int) ((x - plotL) / ((plotR - plotL) / n));
            i = Math.max(0, Math.min(n - 1, i));
            selected = i;
            if (e.getAction() == MotionEvent.ACTION_MOVE || e.getAction() == MotionEvent.ACTION_DOWN) {
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    float mid = (plotT + plotB) / 2;
                    setGain(i, Math.round(-(y - mid) / ((plotB - plotT) / 2) * bound));
                }
            }
            invalidate();
            return true;
        }
    }
}
