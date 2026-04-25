package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.events.Render2DEvent;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.IntProperty;
import unfair.util.RenderUtil;
import unfair.font.impl.UFontRenderer; // 必须导入自定义字体类

public class WaterMark extends Module {
    public final IntProperty rectLeft = new IntProperty("RectLeft", 2, 0, 20);
    public final IntProperty rectTop = new IntProperty("RectTop", 2, 0, 20);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);

    public WaterMark() {
        super("WaterMark", true, true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;

        UFontRenderer fr = Unfair.fontManager.getFont(20);
        String text = "Unfair";

        float textWidth = (float) fr.getStringWidth(text);
        float textHeight = (float) fr.getHeight();

        float padX = 6.0F;
        float padY = 4.0F;

        float startX = (float) rectLeft.getValue();
        float startY = (float) rectTop.getValue();

        float rectRight = startX + textWidth + (padX);
        float rectBottom = startY + textHeight + (padY);

        float radius = 4.0f;

        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);

        int fillColor = 0x80000000;
        int hudColor = hud.getColor(System.currentTimeMillis()).getRGB();

        RenderUtil.drawRoundedGradientOutlinedRectangle(
                startX, startY, rectRight, rectBottom,
                radius, fillColor, hudColor, hudColor
        );

        fr.drawString(
                text,
                startX + padX / 2,
                startY,
                hudColor,
                shadow.getValue()
        );
    }
}