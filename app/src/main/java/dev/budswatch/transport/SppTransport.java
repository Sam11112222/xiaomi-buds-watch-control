package dev.budswatch.transport;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Classic Bluetooth RFCOMM channel. This is the channel the official app uses for REDMI Buds 8 Pro
 * ("BluetoothSpp: SPP_SEND"). Several service UUIDs are tried in the order the official SDK declares them.
 */
@SuppressLint("MissingPermission")
public final class SppTransport implements Transport {
    private static final String TAG = "BudsSpp";
    /** Same order as the official SDK's non-MIUI fallback; 0xFD2D is the one REDMI Buds 8 Pro accepts. */
    static final UUID[] UUIDS = {
            UUID.fromString("0000fd2d-0000-1000-8000-00805f9b34fb"),   // UUID_SPP_MIUI (0xFD2D)
            UUID.fromString("00001101-0000-1000-8000-008584d01810"),   // UUID_SPP_XIAOAI
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"),   // UUID_SPP
    };

    private final BluetoothDevice device;
    private final Listener listener;
    private volatile BluetoothSocket socket;
    private volatile OutputStream out;
    private volatile boolean closed;

    public SppTransport(BluetoothDevice device, Listener l) { this.device = device; this.listener = l; }

    @Override public void connect() {
        new Thread(this::run, "spp-reader").start();
    }

    private void run() {
        BluetoothSocket s = null;
        String lastError = "no service";
        for (UUID u : UUIDS) {
            if (closed) return;
            for (int attempt = 0; attempt < 2 && s == null; attempt++) {
                BluetoothSocket t = null;
                try {
                    t = device.createRfcommSocketToServiceRecord(u);
                    t.connect();
                    s = t;
                    Log.i(TAG, "connected via " + u);
                } catch (IOException e) {
                    lastError = e.getMessage();
                    Log.w(TAG, "connect " + u + " failed: " + e.getMessage());
                    if (t != null) try { t.close(); } catch (IOException ignored) {}
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                }
            }
            if (s != null) break;
        }
        if (s == null) {
            if (!closed) listener.onDisconnected("RFCOMM: " + lastError);
            return;
        }
        socket = s;
        try {
            out = s.getOutputStream();
            listener.onConnected("SPP");
            InputStream in = s.getInputStream();
            byte[] buf = new byte[2048];
            int n;
            while (!closed && (n = in.read(buf)) > 0) {
                byte[] c = new byte[n];
                System.arraycopy(buf, 0, c, 0, n);
                listener.onData(c);
            }
            if (!closed) listener.onDisconnected("连接已断开");
        } catch (IOException e) {
            if (!closed) listener.onDisconnected("RFCOMM: " + e.getMessage());
        } finally {
            closeQuietly();
        }
    }

    @Override public void write(byte[] data) {
        OutputStream o = out;
        if (o == null) return;
        try {
            synchronized (this) { o.write(data); o.flush(); }
        } catch (IOException e) {
            if (!closed) listener.onDisconnected("RFCOMM write: " + e.getMessage());
            closeQuietly();
        }
    }

    private void closeQuietly() {
        BluetoothSocket s = socket;
        socket = null; out = null;
        if (s != null) try { s.close(); } catch (IOException ignored) {}
    }

    @Override public void close() { closed = true; closeQuietly(); }
}
