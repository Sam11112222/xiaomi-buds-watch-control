import java.util.List;

import dev.budswatch.protocol.Commands;
import dev.budswatch.protocol.ConfigItem;
import dev.budswatch.protocol.Configs;
import dev.budswatch.protocol.DeviceInfo;
import dev.budswatch.protocol.Hex;
import dev.budswatch.protocol.Opcodes;
import dev.budswatch.protocol.Packet;
import dev.budswatch.protocol.PacketStream;

/**
 * Checks the encoder/decoder against byte-exact frames that the official app exchanged with
 * REDMI Buds 8 Pro (taken from its bluetooth.main.log). Run with plain javac/java, no Android needed.
 */
public final class ProtocolSelfTest {
    private static int ok, bad;

    private static void eq(String what, String expected, String actual) {
        if (expected.equals(actual)) ok++;
        else { bad++; System.out.println("FAIL " + what + "\n  expected " + expected + "\n  actual   " + actual); }
    }

    private static String cmd(int op, int sn, byte[] params) {
        return Hex.of(Packet.command(op, sn, true, params).encode(), 999);
    }

    private static List<Packet> parse(String hex) { return new PacketStream().feed(Hex.parse(hex)); }

    public static void main(String[] a) {
        // ---- requests, byte-exact with the official app ----
        eq("get target info", "FEDCBAC402000500FFFFFFFFEF", cmd(Opcodes.GET_TARGET_INFO, 0, Commands.getTargetInfo()));
        eq("get run info", "FEDCBAC409000501FFFFFFFFEF", cmd(Opcodes.GET_DEVICE_RUN_INFO, 1, Commands.getRunInfo()));
        eq("set 118=1", "FEDCBAC4F200050403007601EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 4, Commands.setConfigs(Configs.u8(Configs.DOLBY_SUPPORTED, 1))));
        eq("smart denoise on", "FEDCBAC4F200052203006601EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x22, Commands.setConfigs(Configs.toggle(Configs.SMART_DENOISE, true))));
        eq("spatial on", "FEDCBAC4F200052903001D03EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x29, Commands.setConfigs(Configs.spatial(true, 1, false, false))));
        eq("sound effect 5", "FEDCBAC4F200052C03000705EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x2C, Commands.setConfigs(Configs.u8(Configs.SOUND_EFFECT, 5))));
        eq("auto answer on", "FEDCBAC4F200053403000301EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x34, Commands.setConfigs(Configs.toggle(Configs.AUTO_ANSWER, true))));
        eq("adaptive sense on", "FEDCBAC4F200054003002901EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x40, Commands.setConfigs(Configs.toggle(Configs.ADAPTIVE_SENSE, true))));
        eq("scene music", "FEDCBAC4F20006310400360102EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x31, Commands.setConfigs(Configs.scene(true, 2))));
        eq("find stop both", "FEDCBAC4F20006400400090003EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x40, Commands.setConfigs(Configs.find(false, Configs.FIND_BOTH))));
        eq("find left", "FEDCBAC4F20006430400090101EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x43, Commands.setConfigs(Configs.find(true, Configs.FIND_LEFT))));
        eq("find both", "FEDCBAC4F20006460400090103EF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 0x46, Commands.setConfigs(Configs.find(true, Configs.FIND_BOTH))));
        eq("device time", "FEDCBAC4F20008030600286ADC67DEEF",
                cmd(Opcodes.SET_DEVICE_CONFIG, 3, Commands.setConfigs(Commands.deviceTime(0x6ADC67DEL * 1000))));
        eq("anc mode", "FEDCBAC408000401020402EF", cmd(Opcodes.SET_TARGET_INFO, 1, Commands.setAncMode(2)));
        eq("ack status push", "FEDCBA040E00020003EF", Hex.of(Packet.response(14, 3, 0, null).encode(), 99));

        // ---- responses from the earbuds ----
        List<Packet> ps = parse("FEDCBA04020028" + "0000" + "100076656C61206F7320656172627564730501123612360202640503271750E3020401020500" + "EF");
        eq("target info frames", "1", String.valueOf(ps.size()));
        if (!ps.isEmpty()) {
            DeviceInfo di = new DeviceInfo();
            di.applyTargetInfo(ps.get(0).payload);
            eq("name", "vela os earbuds", di.name);
            eq("version", "1.2.3.6", di.version);
            eq("vid/pid", "2717/50E3", String.format("%04X/%04X", di.vendorId, di.productId));
            eq("anc from target info", "1", String.valueOf(di.ancStatus));
        }
        ps = parse("FEDCBAC40E000418020401EF");
        if (!ps.isEmpty()) {
            DeviceInfo di = new DeviceInfo();
            di.applyStatusPush(ps.get(0).payload);
            eq("status push anc", "1", String.valueOf(di.ancStatus));
        } else eq("status push frames", "1", "0");

        DeviceInfo bat = new DeviceInfo();
        bat.applyStatusPush(Hex.parse("04005F5AFF"));
        eq("battery", "95/90/-1", bat.batteryLeft + "/" + bat.batteryRight + "/" + bat.batteryCase);

        List<ConfigItem> items = ConfigItem.parseAll(Hex.parse(
                "110002040808010101020303030606050B0B" + "04000A0606"));
        eq("parse gestures+cycle", "2", String.valueOf(items.size()));
        int[][] g = Configs.parseGestures(items.get(0));
        eq("double tap", "1,1", g[Configs.PRESS_DOUBLE][0] + "," + g[Configs.PRESS_DOUBLE][1]);
        eq("long press", "6,6", g[Configs.PRESS_LONG][0] + "," + g[Configs.PRESS_LONG][1]);
        eq("slide", "11,11", g[Configs.PRESS_SLIDE][0] + "," + g[Configs.PRESS_SLIDE][1]);

        ConfigItem eqItem = new ConfigItem(Configs.CUSTOM_EQ, Hex.parse(
                "010A060600000A003E00007D0000FA0001F40003E80007D0000FA0001F40002EE0003E8000"));
        Configs.Eq e = Configs.parseEq(eqItem);
        eq("eq bands", "10", String.valueOf(e.bands.size()));
        eq("eq bound", "6", String.valueOf(e.bound));
        eq("eq last freq", "16000", e.bands.isEmpty() ? "-" : String.valueOf(e.bands.get(9)[0]));
        eq("eq gain encode", "03E883", Hex.of(Configs.setCustomEq(java.util.Arrays.asList(new int[]{1000, -3})).value, 99).substring(14));

        eq("versions", "1.2.3.6", Configs.versions(new ConfigItem(71, Hex.parse("12361236"))));
        eq("wear both", "true,true", Configs.leftWorn(0x0C) + "," + Configs.rightWorn(0x0C));
        eq("in case", "true,true", Configs.leftInCase(0x03) + "," + Configs.rightInCase(0x03));

        System.out.println(ok + " passed, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
