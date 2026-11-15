package dev.budswatch.ui;

import android.content.Context;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/** Noise mode, level, smart / adaptive options and commute immersion. */
public final class NoisePage extends ListScreen {
    private final Host host;

    public NoisePage(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        onChanged();
    }

    @Override public void onChanged() {
        Session s = host.session();
        if (s == null) return;
        rebuild(() -> build(s));
    }

    private void build(Session s) {
        title("降噪设置");
        int mode = s.ancMode();
        buttons(new String[]{"关闭", "降噪", "通透"}, mode, s::setAncMode);

        ConfigItem nc = s.config(Configs.NOISE_CONTROL);
        int level = nc != null ? nc.u8(1, 0) : -1;
        if (mode == Configs.ANC_NOISE || mode == Configs.ANC_TRANSPARENT) {
            section(mode == Configs.ANC_NOISE ? "降噪强度" : "通透模式");
            int[] ids = mode == Configs.ANC_NOISE ? Labels.NOISE_LEVEL_IDS : Labels.TRANSP_LEVEL_IDS;
            String[] names = mode == Configs.ANC_NOISE ? Labels.NOISE_LEVELS : Labels.TRANSP_LEVELS;
            boolean known = false;
            for (int i = 0; i < ids.length; i++) {
                final int id = ids[i];
                known |= id == level;
                option(names[i], id == level, () -> s.set(Configs.noiseLevel(mode, id)));
            }
            if (!known && level >= 0) note("当前：" + Labels.level(mode, level) + "（由耳机自动调节）");
            note("型号不支持的档位会被耳机拒绝，并自动恢复为实际值");
        }

        section("智能功能");
        if (s.has(Configs.SMART_DENOISE)) {
            boolean on = s.config(Configs.SMART_DENOISE).u8(0, 0) == 1;
            toggle("智能动态降噪", "根据环境自动调节", on, v -> s.set(Configs.toggle(Configs.SMART_DENOISE, v)));
        }
        if (s.has(Configs.AUTO_NOISE)) {
            boolean on = s.config(Configs.AUTO_NOISE).u8(0, 0) == 1;
            toggle("自适应降噪", null, on, v -> s.set(Configs.toggle(Configs.AUTO_NOISE, v)));
        }
        if (s.has(Configs.PERSONAL_NOISE)) {
            boolean on = s.config(Configs.PERSONAL_NOISE).u8(0, 0) == 1;
            toggle("个性化降噪", null, on, v -> s.set(Configs.toggle(Configs.PERSONAL_NOISE, v)));
        }
        if (s.has(Configs.COMMUTE)) {
            int cur = s.config(Configs.COMMUTE).u8(0, 0);
            section("通勤沉浸");
            for (int i = 0; i < Labels.COMMUTE_IDS.length; i++) {
                final int id = Labels.COMMUTE_IDS[i];
                option(Labels.COMMUTE_NAMES[i], id == cur, () -> s.set(Configs.u8(Configs.COMMUTE, id)));
            }
        }
        if (!s.has(Configs.SMART_DENOISE) && !s.has(Configs.AUTO_NOISE) && !s.has(Configs.COMMUTE)) {
            note("正在读取耳机支持的功能…");
        }
    }
}
