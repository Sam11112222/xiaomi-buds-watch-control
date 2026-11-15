package dev.budswatch.protocol;

import java.util.ArrayList;
import java.util.List;

/** Re-assembles frames from an arbitrarily fragmented byte stream. */
public final class PacketStream {
    private byte[] buf = new byte[0];

    public synchronized List<Packet> feed(byte[] chunk) {
        List<Packet> out = new ArrayList<>();
        if (chunk != null && chunk.length > 0) {
            byte[] n = new byte[buf.length + chunk.length];
            System.arraycopy(buf, 0, n, 0, buf.length);
            System.arraycopy(chunk, 0, n, buf.length, chunk.length);
            buf = n;
        }
        int i = 0;
        while (true) {
            int h = -1;
            for (int k = i; k + 2 < buf.length; k++) {
                if ((buf[k] & 0xFF) == Packet.HEAD0 && (buf[k + 1] & 0xFF) == Packet.HEAD1
                        && (buf[k + 2] & 0xFF) == Packet.HEAD2) { h = k; break; }
            }
            if (h < 0) { i = Math.max(i, buf.length - 2); break; }
            int bodyStart = h + 3;
            if (bodyStart + 4 > buf.length) { i = h; break; }
            int len = ((buf[bodyStart + 2] & 0xFF) << 8) | (buf[bodyStart + 3] & 0xFF);
            int end = bodyStart + 4 + len;
            if (end + 1 > buf.length) { i = h; break; }
            if ((buf[end] & 0xFF) != Packet.TAIL) { i = h + 1; continue; }
            byte[] frame = new byte[4 + len];
            System.arraycopy(buf, bodyStart, frame, 0, frame.length);
            Packet p = Packet.decodeFrame(frame);
            if (p != null) out.add(p);
            i = end + 1;
        }
        if (i > 0) {
            byte[] r = new byte[buf.length - i];
            System.arraycopy(buf, i, r, 0, r.length);
            buf = r;
        }
        if (buf.length > 8192) buf = new byte[0];
        return out;
    }

    public synchronized void reset() { buf = new byte[0]; }
}
