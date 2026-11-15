package dev.budswatch.ui;

import android.content.Context;
import android.widget.LinearLayout;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/** Battery ring, one-tap noise mode, then the menu. */
public final class HomePage extends ListScreen {
    private final Host host;
    private final BatteryRingView ring;

    public HomePage(Context ctx, Host host) {
        super(ctx);
        this.host = host;
        ring = new BatteryRingView(ctx);
        onChanged();
    }

    @Override public void onChanged() {
        Session s = host.session();
        if (s == null) return;
        rebuild(() -> build(s));
    }

    private void build(Session s) {
        String name = s.info.name;
        if (name == null || name.isEmpty() || name.contains("vela")) {
            String n = s.device().getName();
            name = n != null ? n : "耳机";
        }
        ring.bind(s.info, name, wearText(s));
        if (ring.getParent() != null) ((LinearLayout) ring.getParent()).removeView(ring);
        column.addView(ring, new LinearLayout.LayoutParams(-1, -2));

        int mode = s.ancMode();
        section("降噪模式");
        buttons(new String[]{"关闭", "降噪", "通透"}, mode, s::setAncMode);
        ConfigItem nc = s.config(Configs.NOISE_CONTROL);
        String lvl = nc != null ? Labels.level(mode, nc.u8(1, 0)) : "";

        row("降噪设置", Labels.mode(mode) + (lvl.isEmpty() ? "" : " · " + lvl),
                () -> host.push(new NoisePage(ctx, host)));
        row("音效与空间音频", soundSummary(s), () -> host.push(new SoundPage(ctx, host)));
        row("手势操作", "单击 / 双击 / 长按 / 滑动", () -> host.push(new GesturesPage(ctx, host)));
        row("查找耳机", "让耳机播放提示音", () -> host.push(new FindPage(ctx, host)));
        row("连接与通话", "双设备连接 / 自动接听 / 低延迟", () -> host.push(new ConnectPage(ctx, host)));
        if (!cardAdded(ctx)) row("快捷切换入口", "在卡片屏一键切换降噪 / 通透", () -> {
            if (isOppoLauncher(ctx)) {
                host.push(new StatusPage(ctx, "添加快捷切换",
                        "OPPO 手表的卡片屏只允许系统卡片。\n\n已为你单独提供「降噪切换」入口：\n"
                                + "在卡片屏的快捷应用区添加它，点一下即可在降噪 / 通透之间切换。\n\n"
                                + "它也在应用列表里，可以直接打开。"));
            } else {
                pinCard(ctx);
            }
        });
        row("设备信息", s.via, () -> host.push(new InfoPage(ctx, host)));
        row("切换耳机", "", host::disconnectAndPick);
    }

    private static String soundSummary(Session s) {
        ConfigItem se = s.config(Configs.SOUND_EFFECT);
        StringBuilder b = new StringBuilder(se != null ? Labels.sound(se.u8(0, 0)) : "EQ 预设");
        ConfigItem sp = s.config(Configs.SPATIAL_AUDIO);
        ConfigItem sc = s.config(Configs.SPATIAL_CONFIG);
        int spv = sp != null ? sp.u8(0, 0) : (sc != null ? sc.u8(0, 0) : 0);
        if ((spv & 1) != 0) b.append(" · 空间音频");
        return b.toString();
    }

    static boolean isOppoLauncher(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo("com.heytap.wearable.launcher", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static boolean cardAdded(Context ctx) {
        int[] ids = android.appwidget.AppWidgetManager.getInstance(ctx).getAppWidgetIds(
                new android.content.ComponentName(ctx, dev.budswatch.remote.AncCardProvider.class));
        return ids != null && ids.length > 0;
    }

    /** Asks the launcher to add the card (ColorOS Watch shows its own confirm dialog). */
    static void pinCard(Context ctx) {
        android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(ctx);
        android.content.ComponentName cn = new android.content.ComponentName(ctx, dev.budswatch.remote.AncCardProvider.class);
        boolean ok = false;
        try {
            ok = m.isRequestPinAppWidgetSupported() && m.requestPinAppWidget(cn, null, null);
        } catch (RuntimeException ignored) {
        }
        if (!ok) {
            android.widget.Toast.makeText(ctx, "请在卡片屏的编辑页手动添加「耳机降噪」", android.widget.Toast.LENGTH_LONG).show();
        }
    }

    static String wearText(Session s) {
        ConfigItem w = s.config(Configs.WEAR_STATE);
        if (w == null) return Labels.mode(s.ancMode());
        int v = w.u8(0, 0);
        boolean l = Configs.leftWorn(v), r = Configs.rightWorn(v);
        if (l && r) return "双耳佩戴中";
        if (l) return "左耳佩戴中";
        if (r) return "右耳佩戴中";
        if (Configs.leftInCase(v) && Configs.rightInCase(v)) return "在充电盒中";
        return "未佩戴";
    }
}
