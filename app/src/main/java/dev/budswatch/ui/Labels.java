package dev.budswatch.ui;

import dev.budswatch.protocol.Configs;

/** Chinese names for protocol values (names follow the official app's string resources). */
final class Labels {
    private Labels() {}

    static String mode(int m) {
        switch (m) {
            case Configs.ANC_OFF: return "关闭";
            case Configs.ANC_NOISE: return "降噪";
            case Configs.ANC_TRANSPARENT: return "通透";
            default: return "未知";
        }
    }

    static final int[] NOISE_LEVEL_IDS = {0, 1, 2, 3, 4, 5};
    static final String[] NOISE_LEVELS = {"均衡降噪", "轻度降噪", "深度降噪", "自适应降噪", "抗风噪", "超深度降噪"};
    static final int[] TRANSP_LEVEL_IDS = {0, 1, 2, 3};
    static final String[] TRANSP_LEVELS = {"标准通透", "人声增强", "环境声增强", "人声增强+"};

    static String level(int mode, int level) {
        int[] ids = mode == Configs.ANC_NOISE ? NOISE_LEVEL_IDS : TRANSP_LEVEL_IDS;
        String[] names = mode == Configs.ANC_NOISE ? NOISE_LEVELS : TRANSP_LEVELS;
        if (mode == Configs.ANC_OFF) return "";
        for (int i = 0; i < ids.length; i++) if (ids[i] == level) return names[i];
        return "智能档位 (" + level + ")";
    }

    static final int[] COMMUTE_IDS = {0, 1, 2, 3};
    static final String[] COMMUTE_NAMES = {"关闭", "飞机", "地铁", "高铁"};

    static final int[] SOUND_IDS = {0, 1, 5, 6, 7, 13, 14, 15, 11, 12, 16, 17, 10};
    static final String[] SOUND_NAMES = {"均衡聆听", "人声增强", "低音增强", "高音增强", "音量增强", "舒缓低音",
            "哈曼调音", "哈曼大师调音", "经典听感", "传奇听感", "标准", "户外", "自定义"};

    static String sound(int id) {
        for (int i = 0; i < SOUND_IDS.length; i++) if (SOUND_IDS[i] == id) return SOUND_NAMES[i];
        return "预设 " + id;
    }

    static final int[] SCENE_IDS = {1, 2, 3, 4, 5};
    static final String[] SCENE_NAMES = {"标准", "音乐", "视频", "游戏", "有声书"};

    static String scene(int id) {
        for (int i = 0; i < SCENE_IDS.length; i++) if (SCENE_IDS[i] == id) return SCENE_NAMES[i];
        return "模式 " + id;
    }

    static final int[] PRESS_TYPES = {Configs.PRESS_SINGLE, Configs.PRESS_DOUBLE, Configs.PRESS_TRIPLE,
            Configs.PRESS_LONG, Configs.PRESS_SLIDE};
    static final String[] PRESS_NAMES = {"单击", "双击", "三击", "长按", "滑动"};

    static String press(int t) {
        for (int i = 0; i < PRESS_TYPES.length; i++) if (PRESS_TYPES[i] == t) return PRESS_NAMES[i];
        return "手势 " + t;
    }

    static final int[] ACTION_IDS = {1, 2, 3, 13, 4, 5, 11, 6, 7, 0, 9, 8};
    static final String[] ACTION_NAMES = {"播放/暂停", "上一曲", "下一曲", "上/下一曲", "音量+", "音量-",
            "调节音量", "降噪模式切换", "游戏模式", "语音助手", "拍照", "无操作"};

    static String action(int id) {
        for (int i = 0; i < ACTION_IDS.length; i++) if (ACTION_IDS[i] == id) return ACTION_NAMES[i];
        return "功能 " + id;
    }

    static String battery(int pct, boolean charging) {
        if (pct < 0) return "–";
        return pct + "%" + (charging ? " ⚡" : "");
    }
}
