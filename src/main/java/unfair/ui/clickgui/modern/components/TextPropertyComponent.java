package unfair.ui.clickgui.modern.components;

import unfair.Unfair;
import unfair.property.properties.TextProperty;
import unfair.util.shader.RoundedUtils;
import org.lwjgl.opengl.GL11;
import unfair.util.RenderUtil;

import java.awt.*;

public class TextPropertyComponent {

    public static void draw(TextProperty prop, TextProperty focusedText, float propY, float controlX, float controlWidth) {
        boolean isFocused = (focusedText == prop);
        float textBgX = controlX;
        float textBgY = propY + 4;
        float textBgHeight = 20;
        RoundedUtils.drawRound(textBgX, textBgY, controlWidth, textBgHeight, 4.0f,
                isFocused ? new Color(255, 255, 255, 40) : new Color(255, 255, 255, 15));

        String txt = prop.getValue();
        if (isFocused && (System.currentTimeMillis() / 500) % 2 == 0) txt += "_";

        // 裁剪文字区域
        RenderUtil.scissor(controlX + 5, propY + 4, controlWidth - 10, 20);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int textFontSize = 18;
        float textTextHeight = Unfair.fontManager.getFont(textFontSize).getHeight();
        float textTextY = textBgY + (textBgHeight - textTextHeight) / 2f;
        Unfair.fontManager.getFont(textFontSize).drawString(txt, controlX + 5, textTextY, Color.WHITE.getRGB());
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}