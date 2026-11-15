package dev.budswatch.protocol;

import java.io.ByteArrayOutputStream;

/**
 * One protocol frame (big endian):
 * FE DC BA | flags | opcode | len(2) | body(len) | EF
 * flags: bit7 command/response, bit6 response requested, bits0-2 target app id.
 * command body: [sn] [xmOpcode if opcode==1] [params]; response body: [status] [sn or xmOpcode] [data].
 */
public final class Packet {
    public static final int HEAD0 = 0xFE, HEAD1 = 0xDC, HEAD2 = 0xBA, TAIL = 0xEF;
    public static final int TARGET_EARPHONE_APP = 4;

    public boolean command;
    public boolean needResponse;
    public int target = TARGET_EARPHONE_APP;
    public int opcode;
    public int sn;
    public int status;
    public int xmOpcode;
    public byte[] payload = new byte[0];

    public static Packet command(int opcode, int sn, boolean needResponse, byte[] params) {
        Packet p = new Packet();
        p.command = true;
        p.opcode = opcode;
        p.sn = sn & 0xFF;
        p.needResponse = needResponse;
        p.payload = params == null ? new byte[0] : params;
        return p;
    }

    public static Packet response(int opcode, int sn, int status, byte[] data) {
        Packet p = new Packet();
        p.command = false;
        p.opcode = opcode;
        p.sn = sn & 0xFF;
        p.status = status;
        p.payload = data == null ? new byte[0] : data;
        return p;
    }

    public byte[] encode() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (command) {
            body.write(sn);
            if (opcode == 1) body.write(xmOpcode);
        } else {
            body.write(status);
            body.write(opcode == 1 ? xmOpcode : sn);
        }
        body.write(payload, 0, payload.length);
        byte[] b = body.toByteArray();
        int flags = (command ? 0x80 : 0) | (needResponse ? 0x40 : 0) | (target & 0x07);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(HEAD0); out.write(HEAD1); out.write(HEAD2);
        out.write(flags);
        out.write(opcode);
        out.write((b.length >> 8) & 0xFF);
        out.write(b.length & 0xFF);
        out.write(b, 0, b.length);
        out.write(TAIL);
        return out.toByteArray();
    }

    /** @param f bytes starting at the flags byte, exactly 4 + len bytes. */
    static Packet decodeFrame(byte[] f) {
        if (f == null || f.length < 4) return null;
        Packet p = new Packet();
        int flags = f[0] & 0xFF;
        p.command = (flags & 0x80) != 0;
        p.needResponse = (flags & 0x40) != 0;
        p.target = flags & 0x07;
        p.opcode = f[1] & 0xFF;
        int len = ((f[2] & 0xFF) << 8) | (f[3] & 0xFF);
        if (len == 0) return p;
        int start;
        if (!p.command) {
            if (f.length < 6) return null;
            p.status = f[4] & 0xFF;
            if (p.opcode == 1) p.xmOpcode = f[5] & 0xFF; else p.sn = f[5] & 0xFF;
            start = 6;
        } else {
            p.sn = f[4] & 0xFF;
            if (p.opcode == 1) {
                if (f.length < 6) return null;
                p.xmOpcode = f[5] & 0xFF;
                start = 6;
            } else {
                start = 5;
            }
        }
        int n = len - (start - 4);
        if (n < 0 || start + n > f.length) return null;
        p.payload = new byte[n];
        System.arraycopy(f, start, p.payload, 0, n);
        return p;
    }

    @Override public String toString() {
        return (command ? "CMD" : "RSP") + "{op=" + opcode + " sn=" + sn + " st=" + status
                + " data=" + Hex.of(payload, 48) + "}";
    }
}
