package dev.budswatch.transport;

/** A byte pipe to the earbuds. Implementations call the listener from arbitrary threads. */
public interface Transport {
    interface Listener {
        void onConnected(String via);
        void onData(byte[] chunk);
        void onDisconnected(String reason);
    }
    void connect();
    void write(byte[] data);
    void close();
}
