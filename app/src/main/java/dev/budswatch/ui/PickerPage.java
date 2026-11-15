package dev.budswatch.ui;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Choose the earbuds among devices paired with the watch; likely earbuds are listed first. */
@SuppressLint("MissingPermission")
public final class PickerPage extends ListScreen {
    private final BluetoothAdapter adapter;
    private final Consumer<BluetoothDevice> onPick;

    public PickerPage(Context ctx, BluetoothAdapter adapter, Consumer<BluetoothDevice> onPick) {
        super(ctx);
        this.adapter = adapter;
        this.onPick = onPick;
        onChanged();
    }

    static boolean looksLikeBuds(BluetoothDevice d) {
        String n = d.getName();
        if (n == null) return false;
        n = n.toLowerCase();
        return n.contains("buds") || n.contains("耳机") || n.contains("airdots") || n.contains("earphone")
                || n.contains("pods");
    }

    static List<BluetoothDevice> sortedBonded(BluetoothAdapter a) {
        List<BluetoothDevice> buds = new ArrayList<>(), other = new ArrayList<>();
        for (BluetoothDevice d : a.getBondedDevices()) (looksLikeBuds(d) ? buds : other).add(d);
        buds.addAll(other);
        return buds;
    }

    @Override public void onChanged() {
        rebuild(() -> {
            title("选择耳机");
            List<BluetoothDevice> list = sortedBonded(adapter);
            if (list.isEmpty()) {
                note("手表还没有配对任何蓝牙设备。请先在手表「设置 → 蓝牙」中配对耳机（打开充电盒并长按配对键）。");
            }
            for (BluetoothDevice d : list) {
                String n = d.getName();
                row(n == null ? d.getAddress() : n, looksLikeBuds(d) ? "已配对 · 耳机" : "已配对",
                        () -> onPick.accept(d));
            }
            row("打开蓝牙设置", "配对新耳机", () -> {
                try {
                    ctx.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Exception ignored) {}
            });
            row("刷新列表", "", this::onChanged);
        });
    }
}
