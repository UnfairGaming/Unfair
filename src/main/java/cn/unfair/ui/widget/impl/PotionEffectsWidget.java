package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.PotionEffects;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import cn.unfair.util.RenderUtil;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class PotionEffectsWidget extends Widget {
    private PotionEffects potionEffects;
    private boolean positioned;
    private float lastScreenW;

    public PotionEffectsWidget() {
        super("PotionEffects", WidgetAlign.RIGHT | WidgetAlign.TOP);
        this.x = 0.99F;
        this.y = 0.28F;
    }

    @Override
    public boolean shouldRender() {
        PotionEffects module = this.getPotionEffects();
        return module != null && module.shouldRenderWidget();
    }

    @Override
    public void render(float partialTicks) {
        PotionEffects module = this.getPotionEffects();
        if (module == null) return;
        module.tickBlink();
        module.renderWidget(this.renderX, this.renderY);
    }

    @Override
    public boolean shouldRenderBlurMask() {
        PotionEffects module = this.getPotionEffects();
        return module != null && module.shouldRenderWidgetEffects();
    }

    @Override
    public void renderBlurMask(float partialTicks) {
        PotionEffects module = this.getPotionEffects();
        if (module == null) return;
        module.renderWidgetMask(this.renderX, this.renderY, 0xFF000000);
    }

    @Override
    public boolean shouldRenderBloomMask() {
        return this.shouldRenderBlurMask();
    }

    @Override
    public void renderBloomMask(float partialTicks) {
        PotionEffects module = this.getPotionEffects();
        if (module == null) return;
        module.renderWidgetMask(this.renderX, this.renderY, 0xFFFFFFFF);
    }

    @Override
    public void updatePos(ScaledResolution sr) {
        float screenW = sr.getScaledWidth();
        float screenH = sr.getScaledHeight();
        float oldWidth = this.width;
        float oldRenderX = this.renderX;

        PotionEffects module = this.getPotionEffects();
        if (module != null) {
            float[] size = module.getWidgetSize();
            this.width = size[0];
            this.height = size[1];
        }

        if (this.positioned && !this.dragging && Math.abs(screenW - this.lastScreenW) < 0.5F && Math.abs(this.width - oldWidth) > 0.01F) {
            float fixedRenderX = clamp(oldRenderX, 0.0F, Math.max(0.0F, screenW - this.width));
            if ((this.align & WidgetAlign.RIGHT) != 0) {
                this.x = screenW <= 0.0F ? 0.0F : (fixedRenderX + this.width) / screenW;
            } else if ((this.align & WidgetAlign.CENTER) != 0) {
                this.x = screenW <= 0.0F ? 0.0F : (fixedRenderX + this.width / 2.0F) / screenW;
            }
        }

        float rx = this.x * screenW;
        float ry = this.y * screenH - this.getCenterOffset();

        if ((this.align & WidgetAlign.RIGHT) != 0) {
            rx -= this.width;
        } else if ((this.align & WidgetAlign.CENTER) != 0) {
            rx -= this.width / 2.0F;
        }

        this.renderX = clamp(rx, 0.0F, Math.max(0.0F, screenW - this.width));
        this.renderY = clamp(ry, 0.0F, Math.max(0.0F, screenH - this.height));
        this.positioned = true;
        this.lastScreenW = screenW;
    }

    @Override
    public void onChatGUI(ScaledResolution sr, int mouseX, int mouseY, boolean allowDrag) {
        boolean hovering = this.isHovered(mouseX, mouseY);
        if (hovering || this.dragging) {
            RenderUtil.enableRenderState();
            this.drawDragFrame(this.renderX, this.renderY, this.width, this.height, this.dragging);
            RenderUtil.disableRenderState();
        }

        if (hovering && Mouse.isButtonDown(0) && !this.dragging && allowDrag) {
            this.dragging = true;
            this.dragX = mouseX;
            this.dragY = mouseY;
        }

        boolean wasDragging = this.dragging;
        if (!Mouse.isButtonDown(0)) {
            this.dragging = false;
        }

        if (wasDragging && !this.dragging && Unfair.widgetConfig != null) {
            Unfair.widgetConfig.save();
        }

        if (this.dragging) {
            float newRenderX = this.renderX + mouseX - this.dragX;
            float newRenderY = this.renderY + mouseY - this.dragY;
            float screenW = sr.getScaledWidth();
            float screenH = sr.getScaledHeight();

            newRenderX = clamp(newRenderX, 0.0F, Math.max(0.0F, screenW - this.width));
            newRenderY = clamp(newRenderY, 0.0F, Math.max(0.0F, screenH - this.height));

            float nx = newRenderX;
            if ((this.align & WidgetAlign.RIGHT) != 0) {
                nx += this.width;
            } else if ((this.align & WidgetAlign.CENTER) != 0) {
                nx += this.width / 2.0F;
            }

            this.x = screenW <= 0.0F ? 0.0F : nx / screenW;
            this.y = screenH <= 0.0F ? 0.0F : (newRenderY + this.getCenterOffset()) / screenH;
            this.dragX = mouseX;
            this.dragY = mouseY;
        }
    }

    private PotionEffects getPotionEffects() {
        if (this.potionEffects == null && Unfair.moduleManager != null) {
            this.potionEffects = (PotionEffects) Unfair.moduleManager.getModule(PotionEffects.class);
        }
        return this.potionEffects;
    }

    private float getCenterOffset() {
        PotionEffects module = this.getPotionEffects();
        float rowHeight = module == null ? 22.0F : module.getRowHeight();
        return Math.max(0.0F, (this.height - rowHeight) / 2.0F);
    }
}
