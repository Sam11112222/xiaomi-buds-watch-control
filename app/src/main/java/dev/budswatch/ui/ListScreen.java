package dev.budswatch.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.function.Consumer;

/** Vertically scrolling list, padded so rows are not clipped by the round display; crown scrolls it. */
public abstract class ListScreen implements Page {
    protected final Context ctx;
    protected final ScrollView scroll;
    protected final LinearLayout column;

    protected ListScreen(Context ctx) {
        this.ctx = ctx;
        scroll = new ScrollView(ctx) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent e) {
                onTouch(e);
                return super.dispatchTouchEvent(e);
            }
        };
        scroll.setBackgroundColor(Ui.BG);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        column = new LinearLayout(ctx);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean round = ctx.getResources().getConfiguration().isScreenRound();
        int w = ctx.getResources().getDisplayMetrics().widthPixels;
        int h = ctx.getResources().getDisplayMetrics().heightPixels;
        int side = round ? (int) (w * 0.11f) : Ui.dp(ctx, 8);
        int top = round ? (int) (h * 0.15f) : Ui.dp(ctx, 12);
        int bottom = round ? (int) (h * 0.25f) : Ui.dp(ctx, 12);
        column.setPadding(side, top, side, bottom);
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));
    }

    @Override public View view() { return scroll; }

    @Override public boolean onRotary(float delta) {
        scroll.smoothScrollBy(0, Math.round(delta * Ui.dp(ctx, 48)));
        return true;
    }

    private boolean touching, built;
    private Runnable pendingBuild;
    private final Runnable flush = this::flushBuild;

    /**
     * Rebuild keeping the scroll position. Updates arrive often (battery, wear state), so rebuilds are
     * coalesced and held back while a finger is on the screen, otherwise they would cancel the scroll gesture.
     */
    protected void rebuild(Runnable build) {
        pendingBuild = build;
        if (!built) { built = true; flushBuild(); return; }
        if (touching) return;
        scroll.removeCallbacks(flush);
        scroll.postDelayed(flush, 120);
    }

    private void flushBuild() {
        Runnable b = pendingBuild;
        pendingBuild = null;
        if (b == null) return;
        int y = scroll.getScrollY();
        column.removeAllViews();
        b.run();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void onTouch(android.view.MotionEvent e) {
        int a = e.getActionMasked();
        if (a == android.view.MotionEvent.ACTION_DOWN) touching = true;
        else if (a == android.view.MotionEvent.ACTION_UP || a == android.view.MotionEvent.ACTION_CANCEL) {
            touching = false;
            if (pendingBuild != null) { scroll.removeCallbacks(flush); scroll.postDelayed(flush, 350); }
        }
    }

    protected void clear() { column.removeAllViews(); }

    protected TextView title(String s) {
        TextView t = Ui.centered(ctx, s, 16, Ui.FG);
        t.setTypeface(Ui.bold());
        t.setPadding(0, 0, 0, Ui.dp(ctx, 8));
        column.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    protected TextView section(String s) {
        TextView t = Ui.centered(ctx, s, 12, Ui.ACCENT);
        t.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 6));
        column.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    protected TextView note(String s) {
        TextView t = Ui.centered(ctx, s, 12, Ui.DIM);
        t.setPadding(Ui.dp(ctx, 6), Ui.dp(ctx, 4), Ui.dp(ctx, 6), Ui.dp(ctx, 8));
        column.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    /** @param action null makes the row non-interactive. */
    protected Row row(String name, String value, Runnable action) {
        Row r = new Row(ctx, name, value, action);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(ctx, 6);
        column.addView(r, lp);
        return r;
    }

    /** A selectable option; highlighted when selected. */
    protected Row option(String name, boolean selected, Runnable action) {
        Row r = row(name, null, action);
        r.setHighlighted(selected);
        return r;
    }

    /** On/off row. */
    protected Row toggle(String name, String detail, boolean on, Consumer<Boolean> change) {
        Row r = row(name, detail, null);
        r.setSwitch(on);
        r.setClickable(true);
        r.setOnClickListener(v -> change.accept(!on));
        return r;
    }

    /** Several equal buttons in one line (e.g. left / both / right). */
    protected LinearLayout buttons(String[] labels, int selected, Consumer<Integer> pick) {
        LinearLayout line = new LinearLayout(ctx);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView b = Ui.centered(ctx, labels[i], 13, i == selected ? Ui.BG : Ui.FG);
            b.setTypeface(Ui.bold());
            b.setBackground(Ui.pill(i == selected ? Ui.ACCENT : Ui.CARD, 22, ctx));
            b.setMinHeight(Ui.dp(ctx, 46));
            b.setOnClickListener(v -> pick.accept(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) lp.leftMargin = Ui.dp(ctx, 5);
            line.addView(b, lp);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(ctx, 6);
        column.addView(line, lp);
        return line;
    }

    protected static final class Row extends LinearLayout {
        private final TextView nameView, valueView;
        private TextView sw;

        Row(Context c, String name, String value, Runnable action) {
            super(c);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(Ui.dp(c, 52));
            setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
            setBackground(Ui.pill(Ui.CARD, 26, c));
            LinearLayout texts = new LinearLayout(c);
            texts.setOrientation(VERTICAL);
            nameView = Ui.text(c, name, 15, Ui.FG);
            valueView = Ui.text(c, value == null ? "" : value, 12, Ui.DIM);
            valueView.setPadding(0, Ui.dp(c, 3), 0, 0);
            texts.addView(nameView);
            texts.addView(valueView);
            addView(texts, new LayoutParams(0, -2, 1f));
            if (value == null || value.isEmpty()) valueView.setVisibility(GONE);
            if (action != null) {
                setClickable(true);
                setOnClickListener(v -> action.run());
            }
        }

        void set(String name, String value, int valueColor) {
            nameView.setText(name);
            valueView.setText(value == null ? "" : value);
            valueView.setTextColor(valueColor);
            valueView.setVisibility(value == null || value.isEmpty() ? GONE : VISIBLE);
        }

        void setSwitch(boolean on) {
            if (sw == null) {
                sw = Ui.centered(getContext(), "", 11, Ui.BG);
                sw.setTypeface(Ui.bold());
                addView(sw, new LayoutParams(Ui.dp(getContext(), 40), Ui.dp(getContext(), 24)));
            }
            sw.setText(on ? "开" : "关");
            sw.setTextColor(on ? Ui.BG : Ui.DIM);
            sw.setBackground(Ui.pill(on ? Ui.ACCENT2 : Ui.TRACK, 12, getContext()));
        }

        void setHighlighted(boolean on) {
            setBackground(Ui.pill(on ? Ui.dim(Ui.ACCENT, 0.45f) : Ui.CARD, 26, getContext()));
        }
    }
}
