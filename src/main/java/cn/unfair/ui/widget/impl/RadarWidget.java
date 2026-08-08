package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.Radar;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public class RadarWidget extends Widget {
    private Radar radar;

    public RadarWidget() {
        super("Radar", WidgetAlign.RIGHT | WidgetAlign.TOP);
        this.x = 0.99F;
        this.y = 0.42F;
    }

    @Override
    public boolean shouldRender() {
        Radar module = this.getRadar();
        return module != null && module.shouldRenderWidget();
    }

    @Override
    public boolean shouldRenderBlurMask() {
        Radar module = this.getRadar();
        return module != null && module.shouldRenderWidgetEffects();
    }

    @Override
    public void renderBlurMask(float partialTicks) {
        Radar module = this.getRadar();
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
        Radar module = this.getRadar();
        if (module == null) return;
        this.updateBounds(module);
        module.renderWidgetMask(this.renderX, this.renderY, 0xFFFFFFFF);
    }

    @Override
    public void render(float partialTicks) {
        Radar module = this.getRadar();
        if (module == null) return;
        module.renderWidget(partialTicks, this.renderX, this.renderY);
    }

    @Override
    public void updatePos(ScaledResolution sr) {
        Radar module = this.getRadar();
        if (module != null) {
            this.updateBounds(module);
        }
        super.updatePos(sr);
    }

    private Radar getRadar() {
        if (this.radar == null && Unfair.moduleManager != null) {
            this.radar = (Radar) Unfair.moduleManager.getModule(Radar.class);
        }
        return this.radar;
    }

    private void updateBounds(Radar radar) {
        float[] size = radar.getWidgetSize();
        this.width = size[0];
        this.height = size[1];
    }
}
