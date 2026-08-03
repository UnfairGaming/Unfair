package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.WaterMark;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public class WatermarkWidget extends Widget {
    public WatermarkWidget() {
        super("WaterMark", WidgetAlign.LEFT | WidgetAlign.TOP);
        this.x = 0.01F;
        this.y = 0.01F;
    }

    @Override
    public boolean shouldRender() {
        WaterMark waterMark = (WaterMark) Unfair.moduleManager.getModule(WaterMark.class);
        return waterMark != null && waterMark.shouldRenderWidget();
    }

    @Override
    public boolean shouldRenderBlurMask() {
        WaterMark waterMark = (WaterMark) Unfair.moduleManager.getModule(WaterMark.class);
        return waterMark != null && waterMark.shouldRenderWidgetEffects();
    }

    @Override
    public void renderBlurMask(float partialTicks) {
        WaterMark waterMark = (WaterMark) Unfair.moduleManager.getModule(WaterMark.class);
        if (waterMark == null) return;
        this.updateBounds(waterMark);
        waterMark.renderWidgetMask(this.renderX, this.renderY, 0xFF000000);
    }

    @Override
    public boolean shouldRenderBloomMask() {
        return this.shouldRenderBlurMask();
    }

    @Override
    public void renderBloomMask(float partialTicks) {
        WaterMark waterMark = (WaterMark) Unfair.moduleManager.getModule(WaterMark.class);
        if (waterMark == null) return;
        this.updateBounds(waterMark);
        waterMark.renderWidgetMask(this.renderX, this.renderY, 0xFFFFFFFF);
    }

    @Override
    public void render(float partialTicks) {
        WaterMark waterMark = (WaterMark) Unfair.moduleManager.getModule(WaterMark.class);
        if (waterMark == null) return;
        this.updateBounds(waterMark);
        waterMark.renderWidget(this.renderX, this.renderY);
    }

    private void updateBounds(WaterMark waterMark) {
        float[] size = waterMark.getWidgetSize();
        this.width = size[0];
        this.height = size[1];
        this.updatePos(new ScaledResolution(mc));
    }
}
