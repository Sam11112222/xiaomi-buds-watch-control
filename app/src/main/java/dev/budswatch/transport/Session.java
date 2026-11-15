package dev.budswatch.transport;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.budswatch.protocol.Commands;
import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.protocol.DeviceInfo;
import dev.budswatch.protocol.Hex;
import dev.budswatch.protocol.Opcodes;
import dev.budswatch.protocol.Packet;
import dev.budswatch.protocol.PacketStream;

/**
 * One live connection to the earbuds. Owns the transport, matches replies to requests by sequence number,
 * keeps the device state. All callbacks run on the main thread. A single process-wide instance is kept in
 * {@link #current()} so the UI can be recreated without dropping the link.
 */
public final class Session implements Transport.Listener {
    private static final String TAG = "BudsSession";
    private static final long TIMEOUT_MS = 2500;
    private static Session current;

    public static Session current() { return current; }

    public static Session open(Context ctx, BluetoothDevice d) {
        if (current != null) current.close();
        current = new Session(ctx, d);
        current.connect();
        return current;
    }

    public static void closeCurrent() {
        if (current != null) current.close();
        current = null;
    }

    public enum State { CONNECTING, READY, DISCONNECTED }

    public interface Callback {
        void onState(State state, String detail);
        void onChanged();
    }

    private interface Reply { void on(Packet reply /* null on timeout */); }

    private final Context ctx;
    private final BluetoothDevice device;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final PacketStream stream = new PacketStream();
    private final SparseArray<Reply> pending = new SparseArray<>();
    private final List<Callback> callbacks = new CopyOnWriteArrayList<>();
    private final ArrayDeque<String> log = new ArrayDeque<>();
    private Transport transport;
    private boolean triedBle;
    private int sppRetries;
    private int sn;

    public volatile State state = State.CONNECTING;
    public volatile String via = "";
    public volatile String lastError = "";
    public final DeviceInfo info = new DeviceInfo();
    public final Map<Integer, ConfigItem> configs = new HashMap<>();
    /** Ids the earbuds answered; the UI only offers features found here. */
    public final Set<Integer> supported = new HashSet<>();

    private Session(Context ctx, BluetoothDevice device) {
        this.ctx = ctx.getApplicationContext();
        this.device = device;
    }

    public BluetoothDevice device() { return device; }
    public void addCallback(Callback c) { if (!callbacks.contains(c)) callbacks.add(c); }
    public void removeCallback(Callback c) { callbacks.remove(c); }

    private void connect() { startTransport(new SppTransport(device, this)); }

    public void reconnect() {
        if (transport != null) transport.close();
        triedBle = false;
        sppRetries = 0;
        connect();
    }

    private void startTransport(Transport t) {
        transport = t;
        stream.reset();
        setState(State.CONNECTING, "");
        t.connect();
    }

    public void close() {
        Transport t = transport;
        transport = null;
        if (t != null) t.close();
        setState(State.DISCONNECTED, "");
    }

    // ---------------------------------------------------------------- transport events

    @Override public void onConnected(String v) {
        via = v;
        main.post(() -> { setState(State.READY, v); refresh(); });
    }

    @Override public void onDisconnected(String reason) {
        main.post(() -> {
            if (transport == null) return;               // closed by us
            if (state == State.CONNECTING && sppRetries < 2) {
                // The earbuds may still be bringing up their audio link; RFCOMM is refused until then.
                sppRetries++;
                lastError = reason;
                final Transport old = transport;
                main.postDelayed(() -> {
                    if (transport == old) startTransport(new SppTransport(device, this));
                }, 3000);
                return;
            }
            if (state == State.CONNECTING && !triedBle) {
                triedBle = true;
                lastError = reason;
                startTransport(new BleTransport(ctx, device, this));
                return;
            }
            synchronized (pending) {
                for (int i = 0; i < pending.size(); i++) pending.valueAt(i).on(null);
                pending.clear();
            }
            lastError = reason;
            setState(State.DISCONNECTED, reason);
        });
    }

    @Override public void onData(byte[] chunk) {
        final List<Packet> packets = stream.feed(chunk);
        if (packets.isEmpty()) return;
        main.post(() -> { for (Packet p : packets) handle(p); });
    }

    // ---------------------------------------------------------------- requests

    public void refresh() {
        request(Opcodes.GET_TARGET_INFO, Commands.getTargetInfo(), r -> {
            if (r != null && r.status == 0) { info.applyTargetInfo(r.payload); changed(); }
        });
        request(Opcodes.GET_DEVICE_RUN_INFO, Commands.getRunInfo(), r -> {
            if (r != null && r.status == 0) { info.applyRunInfo(r.payload); changed(); }
        });
        request(Opcodes.SET_DEVICE_CONFIG, Commands.setConfigs(Commands.deviceTime(System.currentTimeMillis())), null);
        for (int[] g : Configs.POLL_GROUPS) fetchConfigs(g);
    }

    public void fetchConfigs(int... ids) {
        request(Opcodes.GET_DEVICE_CONFIG, Commands.getConfigs(ids), r -> {
            if (r != null && r.status == 0) storeConfigs(ConfigItem.parseAll(r.payload));
        });
    }

    /** Write settings. The cache is updated at once and re-read when the earbuds refuse. */
    public void set(final ConfigItem... items) {
        final int[] ids = new int[items.length];
        for (int i = 0; i < items.length; i++) {
            ids[i] = items[i].type;
            int t = items[i].type;
            if (t != Configs.FIND_DEVICE && t != Configs.CUSTOM_EQ && t != Configs.NOISE_CYCLE
                    && t != Configs.GESTURES && t != Configs.EAR_FIT) configs.put(t, items[i]);
        }
        changed();
        request(Opcodes.SET_DEVICE_CONFIG, Commands.setConfigs(items), r -> {
            if (r == null || r.status != 0) toast("耳机未接受该设置");
            fetchConfigs(ids);
        });
    }

    /** Off / noise cancelling / transparency. */
    public void setAncMode(int mode) { setAncMode(mode, null); }

    /** @param done called on the main thread with true once the earbuds acknowledged the switch. */
    public void setAncMode(int mode, java.util.function.Consumer<Boolean> done) {
        info.ancStatus = mode;
        ConfigItem nc = configs.get(Configs.NOISE_CONTROL);
        if (nc != null && nc.value.length >= 2) configs.put(Configs.NOISE_CONTROL, Configs.noiseLevel(mode, nc.value[1]));
        changed();
        request(Opcodes.SET_TARGET_INFO, Commands.setAncMode(mode), r -> {
            boolean ok = r != null && r.status == 0;
            if (!ok && done == null) toast("切换失败");
            if (done != null) done.accept(ok);
            fetchConfigs(Configs.NOISE_CONTROL);
        });
    }

    public ConfigItem config(int id) { return configs.get(id); }
    public boolean has(int id) { return supported.contains(id); }

    public int ancMode() {
        ConfigItem c = configs.get(Configs.NOISE_CONTROL);
        if (c != null && c.value.length > 0) return c.u8(0, 0);
        return Math.max(info.ancStatus, 0);
    }

    private void storeConfigs(List<ConfigItem> list) {
        for (ConfigItem c : list) {
            configs.put(c.type, c);
            supported.add(c.type);
            if (c.type == Configs.NOISE_CONTROL && c.value.length > 0) info.ancStatus = c.value[0] & 0xFF;
        }
        changed();
    }

    private void request(int opcode, byte[] params, Reply reply) {
        final Transport t = transport;
        if (t == null || state != State.READY) { if (reply != null) main.post(() -> reply.on(null)); return; }
        final int mySn;
        synchronized (pending) {
            do { sn = (sn + 1) & 0xFF; } while (pending.indexOfKey(sn) >= 0);
            mySn = sn;
            if (reply != null) pending.put(mySn, reply);
        }
        Packet p = Packet.command(opcode, mySn, true, params);
        trace("→ " + p);
        t.write(p.encode());
        if (reply != null) main.postDelayed(() -> {
            Reply r;
            synchronized (pending) { r = pending.get(mySn); pending.remove(mySn); }
            if (r != null) r.on(null);
        }, TIMEOUT_MS);
    }

    private void handle(Packet p) {
        trace("← " + p);
        if (!p.command) {
            final Reply r;
            synchronized (pending) { r = pending.get(p.sn); pending.remove(p.sn); }
            if (r != null) r.on(p);
            return;
        }
        if (p.opcode == Opcodes.DEVICE_STATUS) {
            info.applyStatusPush(p.payload);
            changed();
        } else if (p.opcode == Opcodes.NOTIFY_DEVICE_CONFIG) {
            storeConfigs(ConfigItem.parseAll(p.payload));
        }
        if (p.needResponse && transport != null) {
            transport.write(Packet.response(p.opcode, p.sn, 0, null).encode());
        }
    }

    // ---------------------------------------------------------------- misc

    private void trace(String s) {
        Log.d(TAG, s);
        synchronized (log) {
            log.addLast(s);
            while (log.size() > 60) log.removeFirst();
        }
    }

    public List<String> recentLog() {
        synchronized (log) { return new ArrayList<>(log); }
    }

    private void toast(String s) {
        main.post(() -> android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show());
    }

    private void setState(final State s, final String detail) {
        state = s;
        main.post(() -> { for (Callback c : callbacks) c.onState(s, detail); });
    }

    private void changed() {
        main.post(() -> { for (Callback c : callbacks) c.onChanged(); });
    }

    public static String hex(byte[] b) { return Hex.of(b, 64); }
}
