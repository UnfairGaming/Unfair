package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.WaterMark;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public class WatermarkWidget extends Widget {
    private WaterMark waterMark;

    public WatermarkWidget() {
        super("WaterMark", WidgetAlign.LEFT | WidgetAlign.TOP);
        this.x = 0.01F;
        this.y = 0.01F;
    }

    @Override
    public boolean shouldRender() {
        WaterMark module = this.getWaterMark();
        return module != null && module.shouldRenderWidget();
    }

    @Override
    public boolean shouldRenderBlurMask() {
        WaterMark module = this.getWaterMark();
        return module != null && module.shouldRenderWidgetEffects();
    }

    @Override
    public void renderBlurMask(float partialTicks) {
        WaterMark module = this.getWaterMark();
        if (module == null) return;
        this.updateBounds(module);
        module.renderWidgetMask(this.renderX, this.renderY, 0xFF000000);
    }

    @Override
    public boolean shouldRenderBloomMask() {
        return this.shouldRenderBlurMask();
    }

    @Override
    public void renderBloomMask(float partialTicks) {
        WaterMark module = this.getWaterMark();
        if (module == null) return;
        this.updateBounds(module);
        module.renderWidgetMask(this.renderX, this.renderY, 0xFFFFFFFF);
    }

    @Override
    public void render(float partialTicks) {
        WaterMark module = this.getWaterMark();
        if (module == null) return;
        this.updateBounds(module);
        module.renderWidget(this.renderX, this.renderY);
    }

    @Override
    public void updatePos(ScaledResolution sr) {
        WaterMark module = this.getWaterMark();
        if (module != null) {
            float[] size = module.getWidgetSize();
            this.width = size[0];
            this.height = size[1];
        }
        super.updatePos(sr);
    }

    private WaterMark getWaterMark() {
        if (this.waterMark == null && Unfair.moduleManager != null) {
            this.waterMark = (WaterMark) Unfair.moduleManager.getModule(WaterMark.class);
        }
        return this.waterMark;
    }

    private void updateBounds(WaterMark waterMark) {
        float[] size = waterMark.getWidgetSize();
        this.width = size[0];
        this.height = size[1];
    }
}
