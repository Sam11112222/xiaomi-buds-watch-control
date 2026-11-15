package dev.budswatch.protocol;

/** Request payload builders. */
public final class Commands {
    private Commands() {}

    private static byte[] mask(int m) {
        return new byte[]{(byte) (m >>> 24), (byte) (m >>> 16), (byte) (m >>> 8), (byte) m};
    }

    public static byte[] getTargetInfo() { return mask(-1); }
    public static byte[] getRunInfo() { return mask(-1); }

    public static byte[] getConfigs(int... ids) {
        byte[] b = new byte[ids.length * 2];
        for (int i = 0; i < ids.length; i++) { b[2 * i] = (byte) (ids[i] >> 8); b[2 * i + 1] = (byte) ids[i]; }
        return b;
    }

    public static byte[] setConfigs(ConfigItem... items) { return ConfigItem.encodeAll(items); }

    /** ANC mode switch, as the official app does it: SET_TARGET_INFO with vendor record [02][04][mode]. */
    public static byte[] setAncMode(int mode) { return new byte[]{2, 4, (byte) mode}; }

    /** Current time in seconds, big endian (config 40, sent by the official app on every connect). */
    public static ConfigItem deviceTime(long epochMillis) {
        int t = (int) (epochMillis / 1000L);
        return new ConfigItem(Configs.DEVICE_TIME, (byte) (t >>> 24), (byte) (t >>> 16), (byte) (t >>> 8), (byte) t);
    }
}
