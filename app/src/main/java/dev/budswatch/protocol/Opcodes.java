package dev.budswatch.protocol;

/** Operation codes, verified against frames logged by the official app talking to REDMI Buds 8 Pro. */
public final class Opcodes {
    private Opcodes() {}
    public static final int GET_TARGET_INFO = 2;      // C4 02 .. mask FFFFFFFF
    public static final int EDR_STATUS = 7;           // pushed by earbuds
    public static final int SET_TARGET_INFO = 8;      // params [len][type][value]; type 4 = ANC mode
    public static final int GET_DEVICE_RUN_INFO = 9;
    public static final int DEVICE_STATUS = 14;       // pushed by earbuds: battery (type 0), ANC mode (type 4)
    public static final int SET_DEVICE_CONFIG = 0xF2;
    public static final int GET_DEVICE_CONFIG = 0xF3;
    public static final int NOTIFY_DEVICE_CONFIG = 0xF4;
}
