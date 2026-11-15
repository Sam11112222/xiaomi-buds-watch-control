package dev.budswatch.remote;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.view.View;

import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/**
 * One-tap launcher entry "降噪切换". ColorOS Watch only shows OPPO's own cards on the card screen, but its
 * quick-app row accepts normal launcher entries, so this activity is what the user pins there.
 * Tap: noise cancelling <-> transparency (from off it goes to noise cancelling), a round confirmation, done.
 */
public final class QuickToggleActivity extends Activity implements BudsRemote.Listener {
    private final Handler main = new Handler(Looper.getMainLooper());
    private int target;
    private String text = "连接中…";
    private boolean done;
    private ResultView view;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        view = new ResultView(this);
        setContentView(view);
        int cur = BudsRemote.Cache.get(this).mode;
        Session s = Session.current();
        if (s != null && s.state == Session.State.READY) cur = s.ancMode();
        target = cur == Configs.ANC_NOISE ? Configs.ANC_TRANSPARENT : Configs.ANC_NOISE;
        BudsRemote.addListener(this);
        BudsRemote.setMode(this, target, ok -> {
            if (ok) finishWith(target == Configs.ANC_NOISE ? "降噪" : "通透", true);
            else finishWith("切换失败", false);
        });
        main.postDelayed(() -> finishWith("未连接耳机", false), 12000);
    }

    @Override public void onRemoteStateChanged(Context ctx) {
        if (done) return;
        int link = BudsRemote.link();
        if (link == BudsRemote.LINK_FAILED) { finishWith("未连接耳机", false); return; }
        text = link == BudsRemote.LINK_READY ? "切换中…" : "连接中…";
        view.invalidate();
    }

    private void finishWith(String t, boolean ok) {
        if (done) return;
        done = true;
        text = t;
        view.ok = ok;
        view.invalidate();
        if (ok) {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(30);
        }
        main.postDelayed(this::finish, ok ? 900 : 1800);
    }

    @Override protected void onDestroy() {
        BudsRemote.removeListener(this);
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private final class ResultView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        boolean ok;

        ResultView(Context c) { super(c); setBackgroundColor(0xFF000000); }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), cx = w / 2, cy = h / 2, rad = Math.min(w, h) * 0.22f;
            boolean anc = target == Configs.ANC_NOISE;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(rad * 0.16f);
            p.setStrokeCap(Paint.Cap.ROUND);
            r.set(cx - rad, cy - rad * 1.25f - rad, cx + rad, cy - rad * 1.25f + rad);
            p.setColor(done && !ok ? 0xFF555A63 : 0xFF5FB2FF);
            if (anc) {
                c.drawArc(r, 0, 360, false, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(r.centerX(), r.centerY(), rad * 0.5f, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(r.centerX(), r.centerY(), rad * 0.3f, p);
                p.setStyle(Paint.Style.STROKE);
                for (int k = 1; k <= 2; k++) {
                    RectF a = new RectF(r.centerX() - rad * 0.55f * k - rad * 0.1f, r.centerY() - rad * 0.55f * k,
                            r.centerX() + rad * 0.55f * k + rad * 0.1f, r.centerY() + rad * 0.55f * k);
                    c.drawArc(a, 135, 90, false, p);
                    c.drawArc(a, -45, 90, false, p);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);
            p.setColor(0xFFFFFFFF);
            p.setFakeBoldText(true);
            p.setTextSize(h * 0.085f);
            c.drawText(done ? text : (anc ? "降噪" : "通透"), cx, cy + h * 0.13f, p);
            p.setFakeBoldText(false);
            p.setColor(0xFF9AA0A8);
            p.setTextSize(h * 0.05f);
            c.drawText(done ? (ok ? "已开启" : "请先让耳机连接手表") : text, cx, cy + h * 0.23f, p);
        }
    }
}
