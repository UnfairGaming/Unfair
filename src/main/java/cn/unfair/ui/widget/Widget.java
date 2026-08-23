package cn.unfair.ui.widget;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.util.render.RenderUtil;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

import java.awt.*;

public abstract class Widget {
    protected static final Minecraft mc = Minecraft.getMinecraft();

    public final String name;
    public float x;
    public float y;
    public float width;
    public float height;
    public float renderX;
    public float renderY;
    public boolean dragging;
    public int dragX;
    public int dragY;
    public int align;

    protected Widget(String name) {
        this(name, WidgetAlign.LEFT | WidgetAlign.TOP);
    }

    protected Widget(String name, int align) {
        this.name = name;
        this.align = align;
        this.width = 80.0F;
        this.height = 20.0F;
    }

    protected static float clamp(float value, float min, float max) {
        float lower = Math.min(min, max);
        float upper = Math.max(min, max);
        if (value < lower) {
            return lower;
        }
        return Math.min(value, upper);
    }

    public abstract boolean shouldRender();

    public abstract void render(float partialTicks);

    public boolean shouldRenderBlurMask() {
        return false;
    }

    public void renderBlurMask(float partialTicks) {
    }

    public boolean shouldRenderBloomMask() {
        return false;
    }

    public void renderBloomMask(float partialTicks) {
    }

    public void updatePos(ScaledResolution sr) {
        float screenW = sr.getScaledWidth();
        float screenH = sr.getScaledHeight();
        float rx = this.x * screenW;
        float ry = this.y * screenH;

        if ((this.align & WidgetAlign.RIGHT) != 0) {
            rx -= this.width;
        } else if ((this.align & WidgetAlign.CENTER) != 0) {
            rx -= this.width / 2.0F;
        }
        if ((this.align & WidgetAlign.BOTTOM) != 0) {
            ry -= this.height;
        } else if ((this.align & WidgetAlign.MIDDLE) != 0) {
            ry -= this.height / 2.0F;
        }

        rx = clamp(rx, 0.0F, Math.max(0.0F, screenW - this.width));
        ry = clamp(ry, 0.0F, Math.max(0.0F, screenH - this.height));
        this.renderX = rx;
        this.renderY = ry;
    }

    public void loadConfig(JsonObject object) {
        if (object.has("x")) {
            this.x = object.get("x").getAsFloat();
        }
        if (object.has("y")) {
            this.y = object.get("y").getAsFloat();
        }
    }

    public void saveConfig(JsonObject object) {
        object.addProperty("x", this.x);
        object.addProperty("y", this.y);
    }

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
            float ny = newRenderY;
            if ((this.align & WidgetAlign.RIGHT) != 0) {
                nx += this.width;
            } else if ((this.align & WidgetAlign.CENTER) != 0) {
                nx += this.width / 2.0F;
            }
            if ((this.align & WidgetAlign.BOTTOM) != 0) {
                ny += this.height;
            } else if ((this.align & WidgetAlign.MIDDLE) != 0) {
                ny += this.height / 2.0F;
            }

            this.x = screenW <= 0.0F ? 0.0F : nx / screenW;
            this.y = screenH <= 0.0F ? 0.0F : ny / screenH;
            this.dragX = mouseX;
            this.dragY = mouseY;
        }
    }

    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= this.renderX && mouseX <= this.renderX + this.width && mouseY >= this.renderY && mouseY <= this.renderY + this.height;
    }

    protected void drawDragFrame(float x, float y, float width, float height, boolean dragging) {
        float left = x - 3.0F;
        float top = y - 3.0F;
        float right = x + width + 3.0F;
        float bottom = y + height + 3.0F;
        int accent = HUD.getColor(System.currentTimeMillis()).getRGB();
        int lineAlpha = dragging ? 215 : 145;
        int fillAlpha = dragging ? 30 : 18;
        int cornerAlpha = dragging ? 245 : 190;
        int lineColor = RenderUtil.mergeAlpha(accent, lineAlpha);
        int fillColor = new Color(8, 10, 14, fillAlpha).getRGB();
        int cornerColor = RenderUtil.mergeAlpha(accent, cornerAlpha);
        float line = 1.0F;
        float corner = Math.min(10.0F, Math.min(width, height) / 3.0F);

        RenderUtil.drawRect(left + 1.0F, top + 1.0F, right - 1.0F, bottom - 1.0F, fillColor);
        RenderUtil.drawRect(left, top, right, top + line, lineColor);
        RenderUtil.drawRect(left, bottom - line, right, bottom, lineColor);
        RenderUtil.drawRect(left, top, left + line, bottom, lineColor);
        RenderUtil.drawRect(right - line, top, right, bottom, lineColor);

        RenderUtil.drawRect(left - 1.0F, top - 1.0F, left + corner, top + 1.0F, cornerColor);
        RenderUtil.drawRect(left - 1.0F, top - 1.0F, left + 1.0F, top + corner, cornerColor);
        RenderUtil.drawRect(right - corner, top - 1.0F, right + 1.0F, top + 1.0F, cornerColor);
        RenderUtil.drawRect(right - 1.0F, top - 1.0F, right + 1.0F, top + corner, cornerColor);
        RenderUtil.drawRect(left - 1.0F, bottom - 1.0F, left + corner, bottom + 1.0F, cornerColor);
        RenderUtil.drawRect(left - 1.0F, bottom - corner, left + 1.0F, bottom + 1.0F, cornerColor);
        RenderUtil.drawRect(right - corner, bottom - 1.0F, right + 1.0F, bottom + 1.0F, cornerColor);
        RenderUtil.drawRect(right - 1.0F, bottom - corner, right + 1.0F, bottom + 1.0F, cornerColor);
    }
}
