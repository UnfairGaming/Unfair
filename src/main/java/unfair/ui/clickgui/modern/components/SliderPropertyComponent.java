package unfair.ui.clickgui.modern.components;

import unfair.Unfair;
import unfair.property.Property;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.property.properties.PercentProperty;
import unfair.util.RenderUtil;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.util.Map;

public class SliderPropertyComponent {

    public static void draw(Property<?> prop, float cardX, float propY, float cardWidth,
                            float controlX, float controlWidth,
                            Map<Property<?>, Float> sliderAnims,
                            Color colorAccent, Color colorDivider, Color colorTextSecondary) {
        float min = 0, max = 0, val = 0;
        if (prop instanceof IntProperty) {
            min = ((IntProperty) prop).getMinimum();
            max = ((IntProperty) prop).getMaximum();
            val = ((IntProperty) prop).getValue();
        } else if (prop instanceof FloatProperty) {
            min = ((FloatProperty) prop).getMinimum();
            max = ((FloatProperty) prop).getMaximum();
            val = ((FloatProperty) prop).getValue();
        } else if (prop instanceof PercentProperty) {
            min = ((PercentProperty) prop).getMinimum();
            max = ((PercentProperty) prop).getMaximum();
            val = ((PercentProperty) prop).getValue();
        }

        float percent = (val - min) / (max - min);
        float animPercent = sliderAnims.getOrDefault(prop, percent);
        animPercent = RenderUtil.lerpFloat(percent, animPercent, 0.3f);
        sliderAnims.put(prop, animPercent);

        // 滑轨背景
        RoundedUtils.drawRound(controlX, propY + 12, controlWidth, 4, 2.0f, colorDivider);
        // 填充
        RoundedUtils.drawRound(controlX, propY + 12, controlWidth * animPercent, 4, 2.0f, colorAccent);
        // 滑块
        RoundedUtils.drawRound(controlX + (controlWidth * animPercent) - 4, propY + 8, 10, 10, 5.0f, Color.WHITE);

        // 显示值
        String formatVal = prop.formatValue().replace("&", "§");
        int sliderValFontSize = 16;
        float strW = Unfair.fontManager.getFont(sliderValFontSize).getStringWidth(formatVal);
        float sliderValTextHeight = Unfair.fontManager.getFont(sliderValFontSize).getHeight();
        float sliderValTextY = propY + (28 - sliderValTextHeight) / 2f;
        Unfair.fontManager.getFont(sliderValFontSize).drawString(formatVal, controlX - strW - 10, sliderValTextY, colorTextSecondary.getRGB());
    }
}