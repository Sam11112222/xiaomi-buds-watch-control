package dev.budswatch.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * Setting ids (DeviceConfigIdConstantKt of the official app) and their value layouts.
 * Layouts are taken from the app's DeviceConfig* classes and checked against real frames
 * exchanged with REDMI Buds 8 Pro (vid 0x2717 / pid 0x50E3).
 */
public final class Configs {
    private Configs() {}

    public static final int GESTURES = 2, AUTO_ANSWER = 3, MULTIPOINT = 4, SOUND_EFFECT = 7, FIND_DEVICE = 9,
            NOISE_CYCLE = 10, NOISE_CONTROL = 11, WEAR_STATE = 12, SPATIAL_AUDIO = 29, SPATIAL_CONFIG = 30,
            AUTO_NOISE = 37, SN = 39, DEVICE_TIME = 40, ADAPTIVE_SENSE = 41, LOW_LATENCY = 47,
            SCENE_RENDERING = 54, CUSTOM_EQ = 55, PERSONAL_NOISE = 59, EAR_FIT = 60, VERSION = 71,
            ADAPTIVE_VOLUME = 72, RESTRICTED = 93, SMART_DENOISE = 102, COMMUTE = 103, DOLBY_MODE = 104,
            DOLBY_SUPPORTED = 118, DUAL_SYNC = 126, PROMPT_VOLUME = 161;

    /** Read on connect in small groups, the way the official app does (unknown ids are simply not answered). */
    public static final int[][] POLL_GROUPS = {
            {NOISE_CONTROL, WEAR_STATE}, {SMART_DENOISE, AUTO_NOISE, PERSONAL_NOISE}, {COMMUTE, RESTRICTED},
            {SOUND_EFFECT, ADAPTIVE_SENSE, SPATIAL_CONFIG}, {SPATIAL_AUDIO, DOLBY_MODE, SCENE_RENDERING},
            {CUSTOM_EQ}, {GESTURES, NOISE_CYCLE}, {MULTIPOINT, AUTO_ANSWER, LOW_LATENCY},
            {ADAPTIVE_VOLUME, DUAL_SYNC, PROMPT_VOLUME}, {VERSION, SN},
    };

    // ---- noise control (11): [state][level] ----
    public static final int ANC_OFF = 0, ANC_NOISE = 1, ANC_TRANSPARENT = 2;

    public static ConfigItem noiseLevel(int state, int level) {
        return new ConfigItem(NOISE_CONTROL, (byte) state, (byte) level);
    }

    public static ConfigItem toggle(int id, boolean on) { return new ConfigItem(id, (byte) (on ? 1 : 0)); }
    public static ConfigItem u8(int id, int v) { return new ConfigItem(id, (byte) v); }

    // ---- find (9): [on/off][which] ; which 1 left, 2 right, 3 both ----
    public static final int FIND_LEFT = 1, FIND_RIGHT = 2, FIND_BOTH = 3;
    public static ConfigItem find(boolean ring, int which) {
        return new ConfigItem(FIND_DEVICE, (byte) (ring ? 1 : 0), (byte) which);
    }

    // ---- wear / case state (12): bit3 left worn, bit2 right worn, bit1 left in case, bit0 right in case ----
    public static boolean leftWorn(int v) { return (v & 0x08) != 0; }
    public static boolean rightWorn(int v) { return (v & 0x04) != 0; }
    public static boolean leftInCase(int v) { return (v & 0x02) != 0; }
    public static boolean rightInCase(int v) { return (v & 0x01) != 0; }

    // ---- gestures (2): repeated [pressType][leftAction][rightAction] ----
    public static final int PRESS_DOUBLE = 1, PRESS_TRIPLE = 2, PRESS_LONG = 3, PRESS_SINGLE = 4, PRESS_SLIDE = 5;

    public static ConfigItem gesture(int pressType, int left, int right) {
        return new ConfigItem(GESTURES, (byte) pressType, (byte) left, (byte) right);
    }

    /** result[pressType] = {left, right}; null when the earbuds do not report that press type. */
    public static int[][] parseGestures(ConfigItem c) {
        int[][] r = new int[6][];
        if (c == null) return r;
        byte[] v = c.value;
        for (int i = 0; i + 2 < v.length; i += 3) {
            int t = v[i] & 0xFF;
            if (t >= 1 && t <= 5) r[t] = new int[]{v[i + 1] & 0xFF, v[i + 2] & 0xFF};
        }
        return r;
    }

    // ---- long-press noise cycle (10): [left bits][right bits], 0xFF = untouched side; bits 4 transp, 2 anc, 1 off ----
    public static ConfigItem noiseCycle(boolean left, int bits) {
        return left ? new ConfigItem(NOISE_CYCLE, (byte) bits, (byte) 0xFF)
                    : new ConfigItem(NOISE_CYCLE, (byte) 0xFF, (byte) bits);
    }

    // ---- spatial audio (29): bit0 on, bits1-2 preference, bit3 head tracking, bit4 virtual surround ----
    public static ConfigItem spatial(boolean on, int pref, boolean headTracking, boolean surround) {
        int b = (on ? 1 : 0) | ((pref & 3) << 1) | (headTracking ? 8 : 0) | (surround ? 16 : 0);
        return new ConfigItem(SPATIAL_AUDIO, (byte) b);
    }

    // ---- scene rendering (54): [on][mode] ; modes 1 standard 2 music 3 video 4 game 5 audiobook ----
    public static ConfigItem scene(boolean on, int mode) {
        return on ? new ConfigItem(SCENE_RENDERING, (byte) 1, (byte) mode) : new ConfigItem(SCENE_RENDERING, (byte) 0);
    }

    // ---- custom EQ (55) ----
    public static final class Eq {
        public int mode = -1, bound = 10;
        public final List<int[]> bands = new ArrayList<>();   // {freqHz, gainDb}
    }

    public static Eq parseEq(ConfigItem c) {
        Eq e = new Eq();
        if (c == null || c.value.length < 7 || (c.value[0] & 0xFF) != 1) return e;
        byte[] v = c.value;
        e.mode = v[1] & 0xFF;
        e.bound = (v[2] & 0xFF) == 0 ? 10 : v[2] & 0xFF;
        int skip = v[5] & 0xFF;
        int p = 6 + skip;
        if (p >= v.length) return e;
        int count = v[p++] & 0xFF;
        while (p + 2 < v.length && e.bands.size() < count) {
            int f = ((v[p] & 0xFF) << 8) | (v[p + 1] & 0xFF);
            int g = v[p + 2] & 0xFF;
            e.bands.add(new int[]{f, g < 128 ? g : 128 - g});
            p += 3;
        }
        return e;
    }

    /** Write all custom bands (operation 4 of DeviceConfigCustomEq). */
    public static ConfigItem setCustomEq(List<int[]> bands) {
        byte[] b = new byte[7 + bands.size() * 3];
        b[0] = 1; b[1] = 10; b[2] = 1; b[3] = 1; b[4] = 1; b[5] = 0; b[6] = (byte) bands.size();
        int p = 7;
        for (int[] band : bands) {
            int g = band[1] < 0 ? -band[1] + 128 : band[1];
            b[p++] = (byte) (band[0] >> 8); b[p++] = (byte) band[0]; b[p++] = (byte) g;
        }
        return new ConfigItem(CUSTOM_EQ, b);
    }

    /** Live audition of a curve without saving it (operation 8, used by the official app while dragging). */
    public static ConfigItem previewEq(List<int[]> bands) {
        byte[] b = new byte[4 + bands.size() * 3];
        b[0] = 5; b[1] = 1; b[2] = 1; b[3] = (byte) bands.size();
        int p = 4;
        for (int[] band : bands) {
            int g = band[1] < 0 ? -band[1] + 128 : band[1];
            b[p++] = (byte) (band[0] >> 8); b[p++] = (byte) band[0]; b[p++] = (byte) g;
        }
        return new ConfigItem(CUSTOM_EQ, b);
    }

    /** Select the custom curve / a preset through the EQ channel (operation 3). */
    public static ConfigItem selectEqMode(int mode) { return new ConfigItem(CUSTOM_EQ, (byte) 1, (byte) mode); }

    /** Firmware versions (71): [L hi][L lo][R hi][R lo], nibbles = a.b.c.d */
    public static String versions(ConfigItem c) {
        if (c == null || c.value.length < 2) return null;
        String l = DeviceInfo.versionString(new byte[]{c.value[0], c.value[1]});
        if (c.value.length < 4) return l;
        String r = DeviceInfo.versionString(new byte[]{c.value[2], c.value[3]});
        return l.equals(r) ? l : "L " + l + " / R " + r;
    }
}
