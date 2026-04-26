package unfair.ui.clickgui.modern.components;

import unfair.property.properties.ColorProperty;
import unfair.util.RenderUtil;

import java.awt.*;

public class ColorPropertyComponent {

    public static void draw(ColorProperty prop, float propY, float controlX, float controlWidth) {
        float[] hsb = Color.RGBtoHSB((prop.getValue() >> 16) & 0xFF,
                (prop.getValue() >> 8) & 0xFF,
                prop.getValue() & 0xFF, null);
        float hue = hsb[0];

        for (int i = 0; i < controlWidth; i++) {
            int c = Color.HSBtoRGB(i / controlWidth, 1.0f, 1.0f);
            RenderUtil.drawRect(controlX + i, propY + 10, controlX + i + 1, propY + 18, new Color(c).getRGB());
        }
        RenderUtil.drawRect(controlX + (controlWidth * hue) - 1, propY + 8,
                controlX + (controlWidth * hue) + 1, propY + 20, Color.WHITE.getRGB());
    }
}