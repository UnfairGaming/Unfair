package cn.unfair.ui.clickgui.modern.components;

import cn.unfair.Unfair;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.shader.RoundedUtils;

import java.awt.*;

public class ModePropertyComponent {

    public static void draw(ModeProperty prop, float cardX, float propY, float cardWidth) {
        String modeStr = prop.getModeString();
        int modeFontSize = 18;
        float strW = Unfair.fontManager.getFont(modeFontSize).getStringWidth(modeStr);
        float modeBgX = cardX + cardWidth - strW - 30;
        float modeBgY = propY + 4;
        float modeBgHeight = 20;
        RoundedUtils.drawRound(modeBgX, modeBgY, strW + 16, modeBgHeight, 6.0f, new Color(255, 255, 255, 20));
        float modeTextHeight = Unfair.fontManager.getFont(modeFontSize).getHeight();
        float modeTextY = modeBgY + (modeBgHeight - modeTextHeight) / 2f;
        Unfair.fontManager.getFont(modeFontSize).drawString(modeStr, cardX + cardWidth - strW - 22, modeTextY, Color.WHITE.getRGB());
    }
}