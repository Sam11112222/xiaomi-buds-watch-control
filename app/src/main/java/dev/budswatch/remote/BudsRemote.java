package dev.budswatch.remote;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.transport.Session;

/**
 * Lets surfaces outside the app (the ColorOS card, the Wear OS tile) switch the noise mode.
 * Opens the shared {@link Session} on demand, runs the action once it is ready, caches the last known
 * state for instant drawing, and drops the link again after a short idle period unless the app is open.
 */
public final class BudsRemote {
    private BudsRemote() {}

    public static final String PREFS = "budswatch";
    private static final String KEY_LAST = "last_device";
    private static final long IDLE_CLOSE_MS = 30_000;

    /** Link state shown on the card / tile. */
    public static final int LINK_IDLE = 0, LINK_CONNECTING = 1, LINK_READY = 2, LINK_FAILED = 3;

    public interface Listener { void onRemoteStateChanged(Context ctx); }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<Listener> listeners = new ArrayList<>();
    private static final List<Runnable> pending = new ArrayList<>();
    private static Session.Callback hook;
    private static Session hooked;
    private static int link = LINK_IDLE;
    private static boolean appVisible;
    private static Context app;

    public static void addListener(Listener l) { if (!listeners.contains(l)) listeners.add(l); }

    public static int link() { return link; }

    /** Called by the activity so the link is not dropped while the user is in the app. */
    public static void setAppVisible(Context ctx, boolean visible) {
        app = ctx.getApplicationContext();
        appVisible = visible;
        if (visible) { main.removeCallbacks(idleClose); return; }
        Session s = Session.current();
        if (s != null) attach(s);   // also mirrors app-side changes onto the card
        scheduleIdleClose();
    }

    // ------------------------------------------------------------------ actions

    public static void setMode(Context ctx, int mode) { setMode(ctx, mode, null); }

    /** @param done receives true once the earbuds acknowledged, false on refusal (not called if never connected). */
    public static void setMode(Context ctx, int mode, java.util.function.Consumer<Boolean> done) {
        Cache.get(ctx).putMode(mode);           // optimistic: the button lights up immediately
        notifyListeners(ctx);
        whenReady(ctx, () -> Session.current().setAncMode(mode, done));
    }

    public static void removeListener(Listener l) { listeners.remove(l); }

    /** Connect (if needed) just to read the current state, e.g. when the card screen is shown. */
    public static void refresh(Context ctx) {
        whenReady(ctx, () -> {
            Session s = Session.current();
            s.fetchConfigs(Configs.NOISE_CONTROL, Configs.WEAR_STATE);
        });
    }

    private static void whenReady(Context ctx, Runnable action) {
        app = ctx.getApplicationContext();
        main.post(() -> {
            main.removeCallbacks(idleClose);
            Session s = Session.current();
            if (s != null && s.state == Session.State.READY) {
                attach(s);
                setLink(LINK_READY);
                action.run();
                scheduleIdleClose();
                return;
            }
            pending.add(action);
            if (s != null && s.state == Session.State.CONNECTING) { attach(s); return; }
            BluetoothDevice d = lastDevice(app);
            if (d == null) { pending.clear(); setLink(LINK_FAILED); return; }
            setLink(LINK_CONNECTING);
            attach(Session.open(app, d));
            main.postDelayed(connectTimeout, 15_000);
        });
    }

    private static final Runnable connectTimeout = () -> {
        if (link == LINK_CONNECTING) { pending.clear(); setLink(LINK_FAILED); }
    };

    private static final Runnable idleClose = () -> {
        if (appVisible) return;
        Session s = Session.current();
        if (s != null && hooked == s) {
            Session.closeCurrent();
            setLink(LINK_IDLE);
        }
    };

    private static void scheduleIdleClose() {
        main.removeCallbacks(idleClose);
        if (!appVisible) main.postDelayed(idleClose, IDLE_CLOSE_MS);
    }

    private static void attach(Session s) {
        if (hooked == s) return;
        if (hooked != null && hook != null) hooked.removeCallback(hook);
        hooked = s;
        hook = new Session.Callback() {
            @Override public void onState(Session.State st, String detail) {
                if (st == Session.State.READY) {
                    main.removeCallbacks(connectTimeout);
                    setLink(LINK_READY);
                    List<Runnable> todo = new ArrayList<>(pending);
                    pending.clear();
                    for (Runnable r : todo) r.run();
                    scheduleIdleClose();
                } else if (st == Session.State.DISCONNECTED) {
                    main.removeCallbacks(connectTimeout);
                    boolean wasWaiting = !pending.isEmpty();
                    pending.clear();
                    setLink(wasWaiting ? LINK_FAILED : LINK_IDLE);
                }
            }

            @Override public void onChanged() {
                if (app == null) return;
                Cache.get(app).capture(hooked);
                main.removeCallbacks(publish);
                main.postDelayed(publish, 250);
            }
        };
        s.addCallback(hook);
    }

    private static final Runnable publish = () -> { if (app != null) notifyListeners(app); };

    private static void setLink(int l) {
        link = l;
        if (app != null) notifyListeners(app);
    }

    private static void notifyListeners(Context ctx) {
        Context c = ctx.getApplicationContext();
        AncCardProvider.updateAll(c);
        AncTileService.requestRefresh(c);
        for (Listener l : new ArrayList<>(listeners)) l.onRemoteStateChanged(c);
    }

    @SuppressWarnings("MissingPermission")
    private static BluetoothDevice lastDevice(Context ctx) {
        try {
            BluetoothManager bm = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            if (a == null || !a.isEnabled()) return null;
            String addr = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST, null);
            if (addr != null && BluetoothAdapter.checkBluetoothAddress(addr)) return a.getRemoteDevice(addr);
            for (BluetoothDevice d : a.getBondedDevices()) {
                String n = d.getName();
                if (n != null && n.toLowerCase().contains("buds")) return d;
            }
        } catch (SecurityException ignored) {
        }
        return null;
    }

    // ------------------------------------------------------------------ cached state

    /** Last known state, persisted so the card can draw instantly without a connection. */
    public static final class Cache {
        public int mode = -1, left = -1, right = -1, box = -1;
        public String name = "";
        private final SharedPreferences p;

        private Cache(SharedPreferences p) {
            this.p = p;
            mode = p.getInt("w_mode", -1);
            left = p.getInt("w_left", -1);
            right = p.getInt("w_right", -1);
            box = p.getInt("w_case", -1);
            name = p.getString("w_name", "");
        }

        public static Cache get(Context ctx) {
            return new Cache(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE));
        }

        void putMode(int m) { mode = m; p.edit().putInt("w_mode", m).apply(); }

        void capture(Session s) {
            if (s == null) return;
            ConfigItem nc = s.config(Configs.NOISE_CONTROL);
            mode = nc != null ? nc.u8(0, 0) : s.info.ancStatus;
            left = s.info.batteryLeft;
            right = s.info.batteryRight;
            box = s.info.batteryCase;
            String n = s.device().getName();
            name = n != null ? n : "";
            p.edit().putInt("w_mode", mode).putInt("w_left", left).putInt("w_right", right)
                    .putInt("w_case", box).putString("w_name", name).apply();
        }
    }
}
