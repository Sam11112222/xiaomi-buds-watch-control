package dev.budswatch.ui;

import android.content.Context;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/** EQ presets, custom EQ, spatial audio, scene rendering, adaptive sound. */
public final class SoundPage extends ListScreen {
    private final Host host;

    public SoundPage(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        onChanged();
    }

    @Override public void onChanged() {
        Session s = host.session();
        if (s == null) return;
        rebuild(() -> build(s));
    }

    /** Spatial state is written to id 29 and reported back on id 30. */
    static int spatialBits(Session s) {
        ConfigItem c = s.config(Configs.SPATIAL_CONFIG);
        if (c == null) c = s.config(Configs.SPATIAL_AUDIO);
        return c == null ? 0 : c.u8(0, 0);
    }

    private void build(Session s) {
        title("音效");
        ConfigItem se = s.config(Configs.SOUND_EFFECT);
        int cur = se != null ? se.u8(0, 0) : -1;
        section("EQ 预设");
        for (int i = 0; i < Labels.SOUND_IDS.length; i++) {
            final int id = Labels.SOUND_IDS[i];
            if (id == 10) continue;   // custom curve has its own page
            option(Labels.SOUND_NAMES[i], id == cur, () -> s.set(Configs.u8(Configs.SOUND_EFFECT, id)));
        }
        row("自定义 EQ", cur == 10 ? "使用中" : "10 段均衡器", () -> host.push(new EqPage(ctx, host)))
                .setHighlighted(cur == 10);

        section("空间音频");
        int sp = spatialBits(s);
        boolean on = (sp & 1) != 0;
        int pref = (sp >> 1) & 3;
        boolean head = (sp & 8) != 0, surround = (sp & 16) != 0;
        toggle("空间音频", null, on, v -> s.set(Configs.spatial(v, pref, head, surround)));
        if (on) {
            toggle("头部追踪", null, head, v -> s.set(Configs.spatial(true, pref, v, surround)));
            toggle("虚拟环绕声", null, surround, v -> s.set(Configs.spatial(true, pref, head, v)));
        }
        if (s.has(Configs.SCENE_RENDERING)) {
            ConfigItem sc = s.config(Configs.SCENE_RENDERING);
            boolean scOn = sc.u8(0, 0) == 1;
            int mode = sc.u8(1, 1);
            section("沉浸音效（场景渲染）");
            toggle("沉浸音效", Labels.scene(mode), scOn, v -> s.set(Configs.scene(v, mode == 255 ? 1 : mode)));
            if (scOn) {
                for (int i = 0; i < Labels.SCENE_IDS.length; i++) {
                    final int id = Labels.SCENE_IDS[i];
                    option(Labels.SCENE_NAMES[i], id == mode, () -> s.set(Configs.scene(true, id)));
                }
            }
        }

        section("其他");
        if (s.has(Configs.ADAPTIVE_SENSE)) {
            boolean v0 = s.config(Configs.ADAPTIVE_SENSE).u8(0, 0) == 1;
            toggle("自适应听感", "根据耳道调节音色", v0, v -> s.set(Configs.toggle(Configs.ADAPTIVE_SENSE, v)));
        }
        if (s.has(Configs.ADAPTIVE_VOLUME)) {
            boolean v0 = s.config(Configs.ADAPTIVE_VOLUME).u8(0, 0) == 1;
            toggle("自适应音量", null, v0, v -> s.set(Configs.toggle(Configs.ADAPTIVE_VOLUME, v)));
        }
    }
}
