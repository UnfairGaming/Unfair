package cn.unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.Render2DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.RenderUtil;

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

        String text = "Unfair";

        float textWidth = (float) mc.fontRendererObj.getStringWidth(text);

        float padX = 6.0F;
        float padY = 4.0F;

        float startX = (float) rectLeft.getValue();
        float startY = (float) rectTop.getValue();

        float rectRight = startX + textWidth + (padX);
        float rectBottom = startY + (padY) + (mc.fontRendererObj.FONT_HEIGHT);

        float radius = 4.0f;

        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);

        int fillColor = 0x80000000;
        int hudColor = hud.getColor(System.currentTimeMillis()).getRGB();

        RenderUtil.drawRoundedGradientOutlinedRectangle(
                startX, startY, rectRight, rectBottom,
                radius, fillColor, hudColor, hudColor
        );

        mc.fontRendererObj.drawString(
                text,
                startX + padX / 2,
                startY + (padY / 2),
                hudColor,
                shadow.getValue()
        );
    }
}