package dev.budswatch.protocol;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** A device setting record: [len][type hi][type lo][value...] with len = value.length + 2. */
public final class ConfigItem {
    public final int type;
    public final byte[] value;

    public ConfigItem(int type, byte... value) {
        this.type = type;
        this.value = value == null ? new byte[0] : value;
    }

    public int u8(int i, int dflt) { return i < value.length ? value[i] & 0xFF : dflt; }

    public byte[] encode() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(value.length + 2);
        o.write((type >> 8) & 0xFF);
        o.write(type & 0xFF);
        o.write(value, 0, value.length);
        return o.toByteArray();
    }

    public static byte[] encodeAll(ConfigItem... items) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (ConfigItem c : items) { byte[] b = c.encode(); o.write(b, 0, b.length); }
        return o.toByteArray();
    }

    public static List<ConfigItem> parseAll(byte[] d) {
        List<ConfigItem> out = new ArrayList<>();
        if (d == null) return out;
        int i = 0;
        while (i < d.length) {
            int len = d[i] & 0xFF;
            if (len == 0xFF) {
                if (i + 5 > d.length) break;
                int type = ((d[i + 1] & 0xFF) << 8) | (d[i + 2] & 0xFF);
                int n = ((d[i + 3] & 0xFF) << 8) | (d[i + 4] & 0xFF);
                int total = n + 5;
                if (type != 52 || i + total > d.length) break;
                byte[] v = new byte[n + 2];
                System.arraycopy(d, i + 3, v, 0, n + 2);
                out.add(new ConfigItem(type, v));
                i += total;
                continue;
            }
            int total = len + 1;
            if (len < 2 || i + total > d.length) break;
            int type = ((d[i + 1] & 0xFF) << 8) | (d[i + 2] & 0xFF);
            byte[] v = new byte[len - 2];
            System.arraycopy(d, i + 3, v, 0, v.length);
            out.add(new ConfigItem(type, v));
            i += total;
        }
        return out;
    }
}
