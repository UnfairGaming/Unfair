package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public class TargetHUDWidget extends Widget {
    private TargetHUD activeTargetHUD;

    public TargetHUDWidget() {
        super("TargetHUD", WidgetAlign.CENTER | WidgetAlign.MIDDLE);
        this.x = 0.5F;
        this.y = 0.65F;
    }

    @Override
    public boolean shouldRender() {
        TargetHUD targetHUD = (TargetHUD) Unfair.moduleManager.getModule(TargetHUD.class);
        this.activeTargetHUD = targetHUD;
        return targetHUD != null && targetHUD.shouldRenderWidget();
    }

    @Override
    public boolean shouldRenderBlurMask() {
        TargetHUD targetHUD = (TargetHUD) Unfair.moduleManager.getModule(TargetHUD.class);
        this.activeTargetHUD = targetHUD;
        return targetHUD != null && targetHUD.shouldRenderWidgetEffects();
    }

    @Override
    public void renderBlurMask(float partialTicks) {
        TargetHUD targetHUD = (TargetHUD) Unfair.moduleManager.getModule(TargetHUD.class);
        if (targetHUD == null) return;
        this.updateBounds(targetHUD);
        targetHUD.renderWidgetMask(partialTicks, this.renderX, this.renderY, 0xFF000000);
    }

    @Override
    public boolean shouldRenderBloomMask() {
        return this.shouldRenderBlurMask();
    }

    @Override
    public void renderBloomMask(float partialTicks) {
        TargetHUD targetHUD = (TargetHUD) Unfair.moduleManager.getModule(TargetHUD.class);
        if (targetHUD == null) return;
        this.updateBounds(targetHUD);
        targetHUD.renderWidgetMask(partialTicks, this.renderX, this.renderY, 0xFFFFFFFF);
    }

    @Override
    public void render(float partialTicks) {
        TargetHUD targetHUD = (TargetHUD) Unfair.moduleManager.getModule(TargetHUD.class);
        if (targetHUD == null) return;
        this.updateBounds(targetHUD);
        targetHUD.renderWidget(partialTicks, this.renderX, this.renderY);
    }

    private void updateBounds(TargetHUD targetHUD) {
        this.activeTargetHUD = targetHUD;
        float[] size = targetHUD.getWidgetSize();
        this.width = size[0];
        this.height = size[1];
        this.updatePos(new ScaledResolution(mc));
    }

    @Override
    public void updatePos(ScaledResolution sr) {
        super.updatePos(sr);
        if (this.activeTargetHUD == null) {
            return;
        }
        float[] followPosition = this.activeTargetHUD.getFollowPosition(this.width, this.height);
        if (followPosition == null) {
            return;
        }
        this.renderX = clamp(followPosition[0], 0.0F, Math.max(0.0F, sr.getScaledWidth() - this.width));
        this.renderY = clamp(followPosition[1], 0.0F, Math.max(0.0F, sr.getScaledHeight() - this.height));
    }
}
