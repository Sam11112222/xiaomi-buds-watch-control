package dev.budswatch.ui;

import android.content.Context;
import android.os.Vibrator;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/** Make one or both earbuds ring. Value of config 9 is [ringing][which]. */
public final class FindPage extends ListScreen {
    private final Host host;

    public FindPage(Context ctx, Host host) {
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
        title("查找耳机");
        ConfigItem f = s.config(Configs.FIND_DEVICE);
        boolean ringing = f != null && f.u8(0, 0) == 1;
        int which = f != null ? f.u8(1, 0) : 0;
        note("耳机会播放较大的提示音，请先从耳朵上取下");
        String[] names = {"左耳", "双耳", "右耳"};
        int[] ids = {Configs.FIND_LEFT, Configs.FIND_BOTH, Configs.FIND_RIGHT};
        int sel = -1;
        for (int i = 0; i < 3; i++) if (ringing && ids[i] == which) sel = i;
        buttons(names, sel, i -> {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(40);
            s.set(Configs.find(true, ids[i]));
        });
        if (ringing) {
            row("停止响铃", "正在响铃：" + (which == 1 ? "左耳" : which == 2 ? "右耳" : "双耳"),
                    () -> s.set(Configs.find(false, which == 0 ? Configs.FIND_BOTH : which)));
        }
        section("状态");
        note(HomePage.wearText(s));
    }
}
