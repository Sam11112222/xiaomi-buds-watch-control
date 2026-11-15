package dev.budswatch.protocol;

/** Parsers for the attribute lists returned by target-info, run-info and status pushes ([len][type][value...]). */
public final class DeviceInfo {
    public String name;
    public String version;
    public int vendorId = -1, productId = -1;
    public int batteryLeft = -1, batteryRight = -1, batteryCase = -1;
    public boolean chargingLeft, chargingRight, chargingCase;
    public int singleBattery = -1;
    public int ancStatus = -1;
    public String edrAddress;

    private interface Attr { void on(int type, byte[] v); }

    static void walk(byte[] d, Attr a) {
        if (d == null) return;
        int i = 0;
        while (i + 2 <= d.length) {
            int len = d[i] & 0xFF;
            if (len <= 1) return;
            int n = len - 1;
            if (i + 2 + n > d.length) return;
            byte[] v = new byte[n];
            System.arraycopy(d, i + 2, v, 0, n);
            a.on(d[i + 1] & 0xFF, v);
            i += len + 1;
        }
    }

    private void setBattery(byte[] v) {
        for (int idx = 0; idx < v.length && idx < 3; idx++) {
            int raw = v[idx] & 0xFF;
            if (raw == 0xFF) {
                if (idx == 2) batteryCase = -1;
                continue;
            }
            int pct = raw & 0x7F;
            boolean chg = (raw & 0x80) != 0;
            if (idx == 0) { batteryLeft = pct; chargingLeft = chg; }
            else if (idx == 1) { batteryRight = pct; chargingRight = chg; }
            else { batteryCase = pct; chargingCase = chg; }
        }
    }

    public static String versionString(byte[] v) {
        if (v.length < 2) return null;
        int a = v[0] & 0xFF, b = v[1] & 0xFF;
        return (a >> 4) + "." + (a & 15) + "." + (b >> 4) + "." + (b & 15);
    }

    public void applyTargetInfo(byte[] payload) {
        walk(payload, (t, v) -> {
            switch (t) {
                case 0: name = new String(v).trim(); break;
                case 1: version = versionString(v); break;
                case 2: if (v.length == 1) singleBattery = v[0] & 0x7F; break;
                case 3: if (v.length == 4) {
                    vendorId = ((v[0] & 0xFF) << 8) | (v[1] & 0xFF);
                    productId = ((v[2] & 0xFF) << 8) | (v[3] & 0xFF);
                } break;
                case 4: if (v.length >= 1) ancStatus = v[0] & 0xFF; break;
                case 7: setBattery(v); break;
                default: break;
            }
        });
    }

    public void applyStatusPush(byte[] payload) {
        walk(payload, (t, v) -> {
            if (t == 0) setBattery(v);
            else if (t == 4 && v.length > 0) ancStatus = v[0] & 0xFF;
        });
    }

    public void applyRunInfo(byte[] payload) {
        walk(payload, (t, v) -> {
            if (t == 0 && v.length == 6) {
                edrAddress = String.format("%02X:%02X:%02X:%02X:%02X:%02X", v[0], v[1], v[2], v[3], v[4], v[5]);
            }
        });
    }
}
