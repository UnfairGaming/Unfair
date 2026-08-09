package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.PotionEffects;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import cn.unfair.util.RenderUtil;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public class PotionEffectsWidget extends Widget {
    private static final String POSITION_MODE = "positionMode";
    private static final String POSITION_MODE_LEFT_TOP = "left_top";
    private PotionEffects potionEffects;
    private boolean positioned;
    private boolean leftTopPosition;

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

        PotionEffects module = this.getPotionEffects();
        if (module != null) {
            float[] size = module.getWidgetSize();
            this.width = size[0];
            this.height = size[1];
        }

        float rx = this.x * screenW;
        float ry = this.y * screenH - this.getCenterOffset();

        if (!this.leftTopPosition && (this.align & WidgetAlign.RIGHT) != 0) {
            rx -= this.width;
        } else if ((this.align & WidgetAlign.CENTER) != 0) {
            rx -= this.width / 2.0F;
        }

        if (!this.positioned && !this.leftTopPosition) {
            rx = clamp(rx, 0.0F, Math.max(0.0F, screenW - this.width));
            this.x = screenW <= 0.0F ? 0.0F : rx / screenW;
            this.leftTopPosition = true;
        }

        this.renderX = clamp(rx, 0.0F, Math.max(0.0F, screenW - this.width));
        this.renderY = clamp(ry, 0.0F, Math.max(0.0F, screenH - this.height));
        this.positioned = true;
    }

    @Override
    public void loadConfig(JsonObject object) {
        super.loadConfig(object);
        this.leftTopPosition = object.has(POSITION_MODE)
                && POSITION_MODE_LEFT_TOP.equalsIgnoreCase(object.get(POSITION_MODE).getAsString());
        this.positioned = false;
    }

    @Override
    public void saveConfig(JsonObject object) {
        object.addProperty("x", this.x);
        object.addProperty("y", this.y);
        object.addProperty(POSITION_MODE, POSITION_MODE_LEFT_TOP);
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

            this.x = screenW <= 0.0F ? 0.0F : newRenderX / screenW;
            this.y = screenH <= 0.0F ? 0.0F : (newRenderY + this.getCenterOffset()) / screenH;
            this.leftTopPosition = true;
            this.positioned = true;
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
