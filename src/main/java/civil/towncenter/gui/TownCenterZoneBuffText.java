package civil.towncenter.gui;

import net.minecraft.network.chat.Component;

/** Formats zone buff names with effect level for town center GUI. */
public final class TownCenterZoneBuffText {

    private static final String BUFF_LINE = "zone_buff.civil.tc.buff_line";

    private TownCenterZoneBuffText() {}

    public static Component line(String translationKey, int amplifier) {
        return Component.translatable(
                BUFF_LINE,
                Component.translatable(translationKey),
                Component.literal(effectLevelRoman(amplifier)));
    }

    private static String effectLevelRoman(int amplifier) {
        int level = amplifier + 1;
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }
}
