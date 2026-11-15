package dev.budswatch.ui;

import android.content.Context;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/** Per-earbud gesture mapping plus the long-press noise cycle. */
public final class GesturesPage extends ListScreen {
    private final Host host;

    public GesturesPage(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        Session s = host.session();
        if (s != null) s.fetchConfigs(Configs.GESTURES, Configs.NOISE_CYCLE);
        onChanged();
    }

    @Override public void onChanged() {
        Session s = host.session();
        if (s == null) return;
        rebuild(() -> build(s));
    }

    private void build(Session s) {
        title("手势操作");
        int[][] g = Configs.parseGestures(s.config(Configs.GESTURES));
        boolean any = false;
        for (int t : Labels.PRESS_TYPES) {
            if (g[t] == null) continue;
            any = true;
            final int type = t;
            final int[] lr = g[t];
            section(Labels.press(t));
            row("左耳", Labels.action(lr[0]), () -> host.push(new ActionPicker(ctx, host, type, true)));
            row("右耳", Labels.action(lr[1]), () -> host.push(new ActionPicker(ctx, host, type, false)));
        }
        if (!any) note("正在读取手势设置…");

        ConfigItem cyc = s.config(Configs.NOISE_CYCLE);
        if (cyc != null && cyc.value.length >= 2) {
            section("长按切换的降噪模式");
            cycleRows(s, true, cyc.u8(0, 0));
            cycleRows(s, false, cyc.u8(1, 0));
            note("每只耳机至少保留两种模式");
        }
    }

    private void cycleRows(Session s, boolean left, int bits) {
        String side = left ? "左耳 · " : "右耳 · ";
        String[] names = {"通透", "降噪", "关闭"};
        int[] masks = {4, 2, 1};
        for (int i = 0; i < 3; i++) {
            final int m = masks[i];
            boolean on = (bits & m) != 0;
            toggle(side + names[i], null, on, v -> {
                int nb = v ? bits | m : bits & ~m;
                if (Integer.bitCount(nb) < 2) {
                    android.widget.Toast.makeText(ctx, "至少保留两种模式", android.widget.Toast.LENGTH_SHORT).show();
                    return;
                }
                s.set(Configs.noiseCycle(left, nb));
            });
        }
    }

    /** Choose the action for one press type on one side. */
    static final class ActionPicker extends ListScreen {
        private final Host host;
        private final int type;
        private final boolean left;

        ActionPicker(Context ctx, Host host, int type, boolean left) {
            super(ctx);
            this.host = host;
            this.type = type;
            this.left = left;
            onChanged();
        }

        @Override public void onChanged() {
            Session s = host.session();
            if (s == null) return;
            rebuild(() -> {
                title((left ? "左耳 " : "右耳 ") + Labels.press(type));
                int[][] g = Configs.parseGestures(s.config(Configs.GESTURES));
                int[] lr = g[type] != null ? g[type] : new int[]{8, 8};
                int cur = left ? lr[0] : lr[1];
                for (int i = 0; i < Labels.ACTION_IDS.length; i++) {
                    final int a = Labels.ACTION_IDS[i];
                    option(Labels.ACTION_NAMES[i], a == cur, () -> {
                        s.set(Configs.gesture(type, left ? a : lr[0], left ? lr[1] : a));
                        host.pop();
                    });
                }
                note("耳机不支持的组合会被拒绝并恢复原设置");
            });
        }
    }
}
