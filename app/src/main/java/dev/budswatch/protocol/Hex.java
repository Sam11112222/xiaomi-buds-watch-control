package dev.budswatch.protocol;

public final class Hex {
    private Hex() {}
    public static String of(byte[] b, int max) {
        if (b == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length && i < max; i++) sb.append(String.format("%02X", b[i] & 0xFF));
        if (b.length > max) sb.append("..");
        return sb.toString();
    }
    public static byte[] parse(String s) {
        s = s.replaceAll("[^0-9A-Fa-f]", "");
        byte[] r = new byte[s.length() / 2];
        for (int i = 0; i < r.length; i++) r[i] = (byte) Integer.parseInt(s.substring(2 * i, 2 * i + 2), 16);
        return r;
    }
}
