package dev.budswatch.ui;

import android.content.Context;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/** Dual connection, auto answer, low latency, prompt volume, ear-tip fit test. */
public final class ConnectPage extends ListScreen {
    private final Host host;

    public ConnectPage(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        onChanged();
    }

    @Override public void onChanged() {
        Session s = host.session();
        if (s == null) return;
        rebuild(() -> build(s));
    }

    private boolean on(Session s, int id) {
        ConfigItem c = s.config(id);
        return c != null && c.u8(0, 0) == 1;
    }

    private void build(Session s) {
        title("连接与通话");
        toggle("双设备连接", "手表与手机同时连接", on(s, Configs.MULTIPOINT),
                v -> s.set(Configs.toggle(Configs.MULTIPOINT, v)));
        toggle("自动接听", "来电时戴上耳机自动接听", on(s, Configs.AUTO_ANSWER),
                v -> s.set(Configs.toggle(Configs.AUTO_ANSWER, v)));
        toggle("游戏低延迟", null, on(s, Configs.LOW_LATENCY),
                v -> s.set(Configs.toggle(Configs.LOW_LATENCY, v)));
        if (s.has(Configs.DUAL_SYNC)) {
            toggle("双连接设置同步", null, on(s, Configs.DUAL_SYNC),
                    v -> s.set(Configs.toggle(Configs.DUAL_SYNC, v)));
        }

        if (s.has(Configs.PROMPT_VOLUME)) {
            ConfigItem c = s.config(Configs.PROMPT_VOLUME);
            int cur = c.u8(0, 0);
            int max = c.value.length == 4 ? c.u8(2, 15) : 15;
            int min = c.value.length == 4 ? c.u8(3, 0) : 0;
            section("提示音音量");
            buttons(new String[]{"－", String.valueOf(cur), "＋"}, 1, i -> {
                if (i == 1) return;
                int nv = Math.max(min, Math.min(max, cur + (i == 0 ? -1 : 1)));
                if (nv != cur) s.set(Configs.u8(Configs.PROMPT_VOLUME, nv));
            });
        }

        section("工具");
        ConfigItem fit = s.config(Configs.EAR_FIT);
        String fitText = "检查耳塞是否贴合耳道";
        if (fit != null && fit.value.length >= 2) {
            fitText = "左 " + fitName(fit.u8(0, 0)) + " · 右 " + fitName(fit.u8(1, 0));
        }
        row("耳塞贴合度检测", fitText, () -> {
            android.widget.Toast.makeText(ctx, "请戴好双耳，保持安静…", android.widget.Toast.LENGTH_LONG).show();
            s.set(Configs.toggle(Configs.EAR_FIT, true));
        });
    }

    private static String fitName(int v) {
        return v == 1 ? "贴合良好" : v == 2 ? "不够贴合" : "未知";
    }
}
