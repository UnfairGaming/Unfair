package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public class TargetHUDWidget extends Widget {
    public TargetHUDWidget() {
        super("TargetHUD", WidgetAlign.CENTER | WidgetAlign.MIDDLE);
        this.x = 0.5F;
        this.y = 0.65F;
    }

    @Override
    public boolean shouldRender() {
        TargetHUD targetHUD = (TargetHUD) Unfair.moduleManager.getModule(TargetHUD.class);
        return targetHUD != null && targetHUD.shouldRenderWidget();
    }

    @Override
    public boolean shouldRenderBlurMask() {
        TargetHUD targetHUD = (TargetHUD) Unfair.moduleManager.getModule(TargetHUD.class);
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
        float[] size = targetHUD.getWidgetSize();
        this.width = size[0];
        this.height = size[1];
        this.updatePos(new ScaledResolution(mc));
    }
}
