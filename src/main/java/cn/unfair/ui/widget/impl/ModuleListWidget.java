package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import cn.unfair.util.RenderUtil;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class ModuleListWidget extends Widget {
    private boolean isLeft = true;
    private boolean isTop = true;

    public ModuleListWidget() {
        super("HUD", WidgetAlign.LEFT | WidgetAlign.TOP);
        this.x = 0.01F;
        this.y = 0.01F;
    }

    @Override
    public boolean shouldRender() {
        HUD hud = (HUD) Unfair.moduleManager.getModule(HUD.class);
        return hud != null && hud.shouldRenderWidget();
    }

    @Override
    public boolean shouldRenderBlurMask() {
        HUD hud = (HUD) Unfair.moduleManager.getModule(HUD.class);
        return hud != null && hud.shouldRenderWidgetEffects();
    }

    @Override
    public void renderBlurMask(float partialTicks) {
        HUD hud = (HUD) Unfair.moduleManager.getModule(HUD.class);
        if (hud == null) return;
        this.updateBounds(hud);
        hud.renderWidgetMask(partialTicks, this.renderX, this.renderY, this.isLeft, this.isTop, 0xFF000000);
    }

    @Override
    public boolean shouldRenderBloomMask() {
        return this.shouldRenderBlurMask();
    }

    @Override
    public void renderBloomMask(float partialTicks) {
        HUD hud = (HUD) Unfair.moduleManager.getModule(HUD.class);
        if (hud == null) return;
        this.updateBounds(hud);
        hud.renderWidgetMask(partialTicks, this.renderX, this.renderY, this.isLeft, this.isTop, 0xFFFFFFFF);
    }

    @Override
    public void render(float partialTicks) {
        HUD hud = (HUD) Unfair.moduleManager.getModule(HUD.class);
        if (hud == null) return;
        this.updateBounds(hud);
        hud.renderWidget(partialTicks, this.renderX, this.renderY, this.isLeft, this.isTop);
    }

    @Override
    public void onChatGUI(ScaledResolution sr, int mouseX, int mouseY, boolean allowDrag) {
        HUD hud = (HUD) Unfair.moduleManager.getModule(HUD.class);
        if (hud == null) return;
        this.updateBounds(hud);
        boolean hovering = this.isHovered(mouseX, mouseY);

        if (hovering || this.dragging) {
            RenderUtil.enableRenderState();
            float outlineX = this.getOutlineX(sr);
            float outlineY = this.getOutlineY(sr);
            this.drawDragFrame(outlineX, outlineY, this.width, this.height, this.dragging);
            RenderUtil.disableRenderState();
        }

        if (hovering && Mouse.isButtonDown(0) && !this.dragging && allowDrag) {
            this.dragging = true;
            this.dragX = (int) (mouseX - this.getOutlineX(sr));
            this.dragY = (int) (mouseY - this.getOutlineY(sr));
        }

        boolean wasDragging = this.dragging;
        if (!Mouse.isButtonDown(0)) {
            this.dragging = false;
        }
        if (wasDragging && !this.dragging && Unfair.widgetConfig != null) {
            Unfair.widgetConfig.save();
        }

        if (this.dragging) {
            float screenW = sr.getScaledWidth();
            float screenH = sr.getScaledHeight();
            float outlineX = clamp(mouseX - this.dragX, 0.0F, Math.max(0.0F, screenW - this.width));
            float outlineY = clamp(mouseY - this.dragY, 0.0F, Math.max(0.0F, screenH - this.height));
            boolean alignLeft = outlineX + this.width / 2.0F < screenW / 2.0F;
            float anchorX = alignLeft ? outlineX : outlineX + this.width;
            this.x = screenW <= 0.0F ? 0.0F : anchorX / screenW;
            this.y = screenH <= 0.0F ? 0.0F : outlineY / screenH;
            this.isLeft = alignLeft;
            this.isTop = this.y < 0.5F;
            this.updateBounds(hud);
        }
    }

    @Override
    public boolean isHovered(int mouseX, int mouseY) {
        ScaledResolution sr = new ScaledResolution(mc);
        float outlineX = this.getOutlineX(sr);
        float outlineY = this.getOutlineY(sr);
        return mouseX >= outlineX && mouseX <= outlineX + this.width && mouseY >= outlineY && mouseY <= outlineY + this.height;
    }

    private void updateBounds(HUD hud) {
        this.isLeft = this.x < 0.5F;
        this.isTop = this.y < 0.5F;
        float[] size = hud.getWidgetSize();
        this.width = size[0];
        this.height = size[1];
        ScaledResolution sr = new ScaledResolution(mc);
        float outlineX = this.getOutlineX(sr, this.isLeft);
        float outlineY = this.getOutlineY(sr);
        this.renderX = this.isLeft ? outlineX : outlineX + this.width;
        this.renderY = this.isTop ? outlineY : outlineY + this.height;
    }

    private float getOutlineX(ScaledResolution sr) {
        return this.getOutlineX(sr, this.x < 0.5F);
    }

    private float getOutlineX(ScaledResolution sr, boolean alignLeft) {
        float screenW = sr.getScaledWidth();
        float anchorX = this.x * screenW;
        float outlineX = alignLeft ? anchorX : anchorX - this.width;
        return clamp(outlineX, 0.0F, Math.max(0.0F, screenW - this.width));
    }

    private float getOutlineY(ScaledResolution sr) {
        float outlineY = this.y * sr.getScaledHeight();
        return clamp(outlineY, 0.0F, Math.max(0.0F, sr.getScaledHeight() - this.height));
    }
}