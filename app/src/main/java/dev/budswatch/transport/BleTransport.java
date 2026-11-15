package dev.budswatch.transport;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;

import java.util.ArrayDeque;
import java.util.UUID;

/** GATT transport: service 0xAF00, write 0xAF05, notify 0xAF06. Plain Android APIs only. */
@SuppressLint("MissingPermission")
public final class BleTransport implements Transport {
    public static final UUID SERVICE = UUID.fromString("0000AF00-0000-1000-8000-00805F9B34FB");
    public static final UUID WRITE = UUID.fromString("0000AF05-0000-1000-8000-00805F9B34FB");
    public static final UUID NOTIFY = UUID.fromString("0000AF06-0000-1000-8000-00805F9B34FB");
    /** Variant of some models that needs a vendor challenge-response; not supported. */
    public static final UUID WRITE_AUTH = UUID.fromString("0000AF07-0000-1000-8000-00805F9B34FB");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final Context ctx;
    private final BluetoothDevice device;
    private final Listener listener;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic writeChar;
    private int chunk = 20;
    private final ArrayDeque<byte[]> queue = new ArrayDeque<>();
    private boolean writing, ready, closed;

    public BleTransport(Context ctx, BluetoothDevice device, Listener l) {
        this.ctx = ctx.getApplicationContext();
        this.device = device;
        this.listener = l;
    }

    @Override public void connect() {
        gatt = device.connectGatt(ctx, false, cb, BluetoothDevice.TRANSPORT_LE);
    }

    private final BluetoothGattCallback cb = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                fail("GATT disconnected (status " + status + ")");
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService s = g.getService(SERVICE);
            if (s == null) { fail("Earbud control service (AF00) not found"); return; }
            if (s.getCharacteristic(WRITE) == null || s.getCharacteristic(NOTIFY) == null) {
                fail(s.getCharacteristic(WRITE_AUTH) != null
                        ? "This model needs the authenticated channel, which is not supported"
                        : "Control characteristics not found");
                return;
            }
            writeChar = s.getCharacteristic(WRITE);
            BluetoothGattCharacteristic n = s.getCharacteristic(NOTIFY);
            g.setCharacteristicNotification(n, true);
            BluetoothGattDescriptor d = n.getDescriptor(CCCD);
            if (d == null) { fail("Notify descriptor missing"); return; }
            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            else { d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); g.writeDescriptor(d); }
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("Enabling notifications failed (" + status + ")"); return; }
            if (!g.requestMtu(512)) markReady();
        }

        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) chunk = Math.max(20, mtu - 3);
            markReady();
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c) {
            if (Build.VERSION.SDK_INT < 33 && NOTIFY.equals(c.getUuid())) listener.onData(c.getValue());
        }

        @Override public void onCharacteristicChanged(BluetoothGatt g, BluetoothGattCharacteristic c, byte[] value) {
            if (NOTIFY.equals(c.getUuid())) listener.onData(value);
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic c, int status) {
            synchronized (queue) { writing = false; }
            if (status != BluetoothGatt.GATT_SUCCESS) { fail("Write failed (" + status + ")"); return; }
            pump();
        }
    };

    private void markReady() {
        synchronized (queue) { if (ready) return; ready = true; }
        listener.onConnected("BLE");
        pump();
    }

    @Override public void write(byte[] data) {
        synchronized (queue) {
            for (int i = 0; i < data.length; i += chunk) {
                int n = Math.min(chunk, data.length - i);
                byte[] c = new byte[n];
                System.arraycopy(data, i, c, 0, n);
                queue.add(c);
            }
        }
        pump();
    }

    private void pump() {
        byte[] next;
        synchronized (queue) {
            if (!ready || closed || writing || queue.isEmpty() || gatt == null || writeChar == null) return;
            next = queue.poll();
            writing = true;
        }
        boolean ok;
        if (Build.VERSION.SDK_INT >= 33) {
            ok = gatt.writeCharacteristic(writeChar, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                    == BluetoothGatt.GATT_SUCCESS;
        } else {
            writeChar.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            writeChar.setValue(next);
            ok = gatt.writeCharacteristic(writeChar);
        }
        if (!ok) { synchronized (queue) { writing = false; } fail("Could not start write"); }
    }

    private void fail(String why) {
        boolean first;
        synchronized (queue) { first = !closed; closed = true; }
        if (gatt != null) { try { gatt.close(); } catch (Exception ignored) {} gatt = null; }
        if (first) listener.onDisconnected(why);
    }

    @Override public void close() {
        synchronized (queue) { if (closed) return; closed = true; queue.clear(); }
        if (gatt != null) { try { gatt.disconnect(); gatt.close(); } catch (Exception ignored) {} gatt = null; }
    }
}
