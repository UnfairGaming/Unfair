package unfair.ui.clickgui.modern.components;

import unfair.property.Property;
import unfair.property.properties.BooleanProperty;
import unfair.util.RenderUtil;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.util.Map;

public class BooleanPropertyComponent {

    public static void draw(BooleanProperty prop, float cardX, float propY, float cardWidth, Map<Property<?>, Float> animMap) {
        float toggleX = cardX + cardWidth - 40; // 与原代码一致
        boolean state = prop.getValue();
        float toggleWidth = 28, toggleHeight = 16;
        float animProgress = animMap.getOrDefault(prop, state ? 1.0f : 0.0f);
        animProgress = RenderUtil.lerpFloat(state ? 1.0f : 0.0f, animProgress, 0.25f);
        animMap.put(prop, animProgress);

        Color offColor = new Color(99, 99, 102, 255);
        Color onColor = new Color(52, 199, 89, 255);
        Color currentColor = RenderUtil.interpolateColorC(offColor, onColor, animProgress);
        RoundedUtils.drawRound(toggleX, propY + 6, toggleWidth, toggleHeight, toggleHeight / 2.0f, currentColor);

        float circleSize = 12;
        float circleX = toggleX + 2 + (animProgress * (toggleWidth - circleSize - 4));
        RoundedUtils.drawRound(circleX, propY + 8, circleSize, circleSize, circleSize / 2.0f, Color.WHITE);
    }
}