package dev.budswatch.ui;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.List;

import dev.budswatch.transport.Session;

/**
 * Single activity with plain framework views, laid out for a round Wear OS watch (OPPO Watch X2):
 * rotating crown scrolls / adjusts, swipe right goes back, pure black AMOLED background.
 */
public final class MainActivity extends Activity implements Host, Session.Callback {
    private static final int REQ_PERMS = 7;
    private static final String PREFS = "budswatch", KEY_LAST = "last_device";

    private final ArrayList<Page> stack = new ArrayList<>();
    private FrameLayout root;
    private SharedPreferences prefs;
    private BluetoothAdapter adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        setContentView(root);
        if (hasPermissions()) start(); else requestPermissions(requiredPermissions(), REQ_PERMS);
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 31) return new String[]{Manifest.permission.BLUETOOTH_CONNECT};
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    private boolean hasPermissions() {
        for (String p : requiredPermissions()) {
            if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_PERMS) return;
        if (hasPermissions()) start();
        else setRoot(new StatusPage(this, "需要蓝牙权限", "需要「附近设备」权限才能与耳机通信。")
                .action("授予权限", () -> requestPermissions(requiredPermissions(), REQ_PERMS)));
    }

    private void start() {
        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm == null ? null : bm.getAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            setRoot(new StatusPage(this, "蓝牙已关闭", "请在手表设置中打开蓝牙后重试。").action("重试", this::start));
            return;
        }
        Session cur = Session.current();
        if (cur != null && cur.state != Session.State.DISCONNECTED) {
            cur.addCallback(this);
            onState(cur.state, "");
            return;
        }
        String last = prefs.getString(KEY_LAST, null);
        if (last != null && BluetoothAdapter.checkBluetoothAddress(last)) {
            BluetoothDevice d = adapter.getRemoteDevice(last);
            if (d.getBondState() == BluetoothDevice.BOND_BONDED) { connect(d); return; }
        }
        List<BluetoothDevice> list = PickerPage.sortedBonded(adapter);
        int buds = 0;
        BluetoothDevice only = null;
        for (BluetoothDevice d : list) {
            android.util.Log.i("BudsMain", "bonded: " + d.getName() + " type=" + d.getType());
            if (PickerPage.looksLikeBuds(d)) { buds++; only = d; }
        }
        if (buds == 1) connect(only); else showPicker();
    }

    private void showPicker() { setRoot(new PickerPage(this, adapter, this::connect)); }

    private void connect(BluetoothDevice d) {
        android.util.Log.i("BudsMain", "connect " + d.getName());
        prefs.edit().putString(KEY_LAST, d.getAddress()).apply();
        Session s = Session.open(this, d);
        s.addCallback(this);
        String n = d.getName();
        setRoot(new StatusPage(this, "正在连接…", (n == null ? "" : n + "\n") + "请把耳机取出充电盒并靠近手表")
                .action("取消", this::disconnectAndPick));
    }

    @Override public Session session() { return Session.current(); }

    @Override public void push(Page p) { stack.add(p); show(); }

    @Override public void pop() { if (stack.size() > 1) { stack.remove(stack.size() - 1); show(); } }

    @Override public void disconnectAndPick() {
        Session s = Session.current();
        if (s != null) s.removeCallback(this);
        Session.closeCurrent();
        prefs.edit().remove(KEY_LAST).apply();
        if (adapter != null) showPicker();
    }

    @Override public void onState(Session.State state, String detail) {
        Session s = Session.current();
        if (s == null) return;
        switch (state) {
            case READY:
                if (stack.isEmpty() || !(stack.get(0) instanceof HomePage)) setRoot(new HomePage(this, this));
                break;
            case DISCONNECTED:
                String why = detail == null || detail.isEmpty() ? s.lastError : detail;
                setRoot(new StatusPage(this, "连接失败 / 已断开",
                        "请确认耳机已取出充电盒，并在手表蓝牙中处于「已连接」。\n"
                                + "若耳机正连着手机：请开启耳机的「双设备连接」，它才能同时连手机和手表。\n"
                                + (why == null ? "" : why))
                        .action("重新连接", () -> connect(s.device()))
                        .action("打开蓝牙设置", () -> {
                            try {
                                startActivity(new android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                            } catch (Exception ignored) {}
                        })
                        .action("选择其他耳机", this::disconnectAndPick));
                break;
            default:
                break;
        }
    }

    @Override public void onChanged() {
        if (!stack.isEmpty()) stack.get(stack.size() - 1).onChanged();
    }

    private void setRoot(Page p) { stack.clear(); stack.add(p); show(); }

    private void show() {
        root.removeAllViews();
        if (stack.isEmpty()) return;
        Page top = stack.get(stack.size() - 1);
        if (top.view().getParent() != null) ((FrameLayout) top.view().getParent()).removeView(top.view());
        root.addView(top.view(), new FrameLayout.LayoutParams(-1, -1));
        top.onChanged();
        top.view().requestFocus();
    }

    @Override public void onBackPressed() {
        if (stack.size() > 1) { stack.remove(stack.size() - 1); show(); }
        else super.onBackPressed();
    }

    @Override public boolean onGenericMotionEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_SCROLL && e.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)
                && !stack.isEmpty()) {
            float clockwise = -e.getAxisValue(MotionEvent.AXIS_SCROLL);
            return stack.get(stack.size() - 1).onRotary(clockwise);
        }
        return super.onGenericMotionEvent(e);
    }

    /**
     * Debug builds only: drive the session from ADB for on-device testing, e.g.
     * adb shell am start -n dev.budswatch.earbuds/dev.budswatch.ui.MainActivity --es bw_cmd "set 11 0102"
     * Commands: "anc N", "set ID HEX", "get ID[,ID]".
     */
    @Override protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        boolean debuggable = (getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        String cmd = intent == null ? null : intent.getStringExtra("bw_cmd");
        Session s = Session.current();
        if (debuggable && "pin".equals(cmd)) { HomePage.pinCard(this); return; }
        if (!debuggable || cmd == null || s == null) return;
        android.util.Log.i("BudsMain", "test cmd: " + cmd);
        String[] p = cmd.trim().split("\\s+");
        try {
            switch (p[0]) {
                case "anc": s.setAncMode(Integer.parseInt(p[1])); break;
                case "pin": HomePage.pinCard(this); break;
                case "set": s.set(new dev.budswatch.protocol.ConfigItem(Integer.parseInt(p[1]),
                        dev.budswatch.protocol.Hex.parse(p.length > 2 ? p[2] : ""))); break;
                case "get": {
                    String[] ids = p[1].split(",");
                    int[] v = new int[ids.length];
                    for (int i = 0; i < ids.length; i++) v[i] = Integer.parseInt(ids[i]);
                    s.fetchConfigs(v);
                    break;
                }
                default: break;
            }
        } catch (RuntimeException e) {
            android.util.Log.w("BudsMain", "bad test cmd", e);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        dev.budswatch.remote.BudsRemote.setAppVisible(this, true);
        Session s = Session.current();
        if (s != null && s.state == Session.State.READY) s.refresh();
        else if (adapter != null && hasPermissions() && (s == null || s.state == Session.State.DISCONNECTED)) {
            start();   // the link may have been dropped while the app was in the background
        }
    }

    @Override protected void onPause() {
        dev.budswatch.remote.BudsRemote.setAppVisible(this, false);
        super.onPause();
    }

    @Override protected void onDestroy() {
        Session s = Session.current();
        if (s != null) s.removeCallback(this);
        if (isFinishing()) Session.closeCurrent();
        super.onDestroy();
    }
}
