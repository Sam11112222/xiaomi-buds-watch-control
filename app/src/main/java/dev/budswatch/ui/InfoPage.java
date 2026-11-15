package dev.budswatch.ui;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/** Device facts plus a protocol log for troubleshooting. */
public final class InfoPage extends ListScreen {
    private final Host host;

    public InfoPage(Context ctx, Host host) {
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
        title("设备信息");
        String bt = s.device().getName();
        row("蓝牙名称", bt == null ? "–" : bt, null);
        row("固件版本", nz(Configs.versions(s.config(Configs.VERSION)), s.info.version), null);
        ConfigItem sn = s.config(Configs.SN);
        row("序列号", sn == null ? "–" : new String(sn.value).trim(), null);
        row("VID / PID", s.info.vendorId < 0 ? "–"
                : String.format("0x%04X / 0x%04X", s.info.vendorId, s.info.productId), null);
        row("蓝牙地址", s.device().getAddress(), null);
        row("控制通道", s.via, null);
        row("刷新全部状态", "", s::refresh);

        section("已读取的配置项");
        List<Integer> ids = new ArrayList<>(s.configs.keySet());
        Collections.sort(ids);
        StringBuilder b = new StringBuilder();
        for (Integer id : ids) {
            b.append(id).append(" = ").append(Session.hex(s.configs.get(id).value)).append('\n');
        }
        note(b.length() == 0 ? "无" : b.toString().trim());

        section("通信日志（最近）");
        List<String> log = s.recentLog();
        StringBuilder l = new StringBuilder();
        for (int i = log.size() - 1; i >= 0 && i >= log.size() - 20; i--) l.append(log.get(i)).append('\n');
        note(l.toString().trim());
    }

    private static String nz(String a, String b) { return a != null ? a : (b != null ? b : "–"); }

    @SuppressWarnings("unused")
    private static void unused(Map<?, ?> m) {}
}
