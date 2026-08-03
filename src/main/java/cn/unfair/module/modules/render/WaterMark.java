package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.ColorUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float FONT_SIZE = 15.0F;
    private static final String MINECRAFT_FONT = "Minecraft";

    public final ModeProperty font = new ModeProperty("font", 0, getFontModes());
    public final PercentProperty background = new PercentProperty("background", 45);
    public final BooleanProperty round = new BooleanProperty("round", true, () -> this.background.getValue() > 0);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty showVersion = new BooleanProperty("version", true);
    public final BooleanProperty showFps = new BooleanProperty("fps", false);
    public final BooleanProperty showPing = new BooleanProperty("ping", false);

    public WaterMark() {
        super("WaterMark", true, true);
    }

    private static String[] getFontModes() {
        Fonts[] fonts = Fonts.values();
        String[] modes = new String[fonts.length + 1];
        modes[0] = MINECRAFT_FONT;
        for (int i = 0; i < fonts.length; i++) {
            modes[i + 1] = fonts[i].name();
        }
        return modes;
    }

    public boolean shouldRenderWidget() {
        return this.isEnabled() && mc.theWorld != null && mc.thePlayer != null && !mc.gameSettings.showDebugInfo;
    }

    public boolean shouldRenderWidgetEffects() {
        return this.shouldRenderWidget() && this.background.getValue() > 0;
    }

    public void renderWidget(float x, float y) {
        if (!this.shouldRenderWidget()) {
            return;
        }

        WatermarkBounds bounds = this.getBounds(x, y);
        long time = System.currentTimeMillis();
        int accent = HUD.getColor(time).getRGB();
        int fillColor = new Color(9, 11, 15, (int) (this.background.getValue().floatValue() / 100.0F * 220.0F)).getRGB();

        RenderUtil.enableRenderState();
        this.drawBackground(bounds, fillColor);
        RenderUtil.disableRenderState();

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        this.drawString("Unfair", bounds.left + 4.0F, bounds.top + 3.0F, accent, this.shadow.getValue());

        float cursor = bounds.left + 4.0F + this.getStringWidth("Unfair");
        if (this.showVersion.getValue()) {
            this.drawString(" " + Unfair.version, cursor, bounds.top + 3.0F, new Color(230, 232, 238, 235).getRGB(), this.shadow.getValue());
            cursor += this.getStringWidth(" " + Unfair.version);
        }

        for (String segment : this.getInfoSegments()) {
            this.drawSeparator(cursor + 3.0F, bounds.top + 3.0F, accent);
            cursor += 7.0F;
            this.drawString(segment, cursor, bounds.top + 3.0F, new Color(210, 214, 225, 230).getRGB(), this.shadow.getValue());
            cursor += this.getStringWidth(segment);
        }
        GlStateManager.disableBlend();
    }

    public void renderWidgetMask(float x, float y, int color) {
        WatermarkBounds bounds = this.getBounds(x, y);
        RenderUtil.enableRenderState();
        this.drawBackground(bounds, color);
        RenderUtil.disableRenderState();
    }

    private void drawBackground(WatermarkBounds bounds, int color) {
        if (this.round.getValue()) {
            RenderUtil.drawRoundedRectangle(bounds.left, bounds.top, bounds.right, bounds.bottom, 2.0F, color);
        } else {
            RenderUtil.drawRect(bounds.left, bounds.top, bounds.right, bounds.bottom, color);
        }
    }

    public float[] getWidgetSize() {
        WatermarkBounds bounds = this.getBounds(0.0F, 0.0F);
        return new float[]{bounds.width(), bounds.height()};
    }

    private WatermarkBounds getBounds(float left, float top) {
        float width = 8.0F + this.getStringWidth("Unfair");
        if (this.showVersion.getValue()) {
            width += this.getStringWidth(" " + Unfair.version);
        }
        for (String segment : this.getInfoSegments()) {
            width += 7.0F + this.getStringWidth(segment);
        }

        float height = Math.max(14.0F, this.getFontHeight() + 5.0F);
        return new WatermarkBounds(left, top, left + width, top + height);
    }

    private String[] getInfoSegments() {
        int count = 0;
        if (this.showFps.getValue()) {
            count++;
        }
        if (this.showPing.getValue()) {
            count++;
        }

        String[] segments = new String[count];
        int index = 0;
        if (this.showFps.getValue()) {
            segments[index++] = Minecraft.getDebugFPS() + " fps";
        }
        if (this.showPing.getValue()) {
            segments[index] = this.getPing() + " ms";
        }
        return segments;
    }

    private int getPing() {
        if (mc.getNetHandler() == null || mc.thePlayer == null) {
            return 0;
        }
        NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return playerInfo == null ? 0 : playerInfo.getResponseTime();
    }

    private boolean useMinecraftFont() {
        return this.font.getValue() == 0;
    }

    private FontRenderer getCustomFont() {
        int fontIndex = this.font.getValue() - 1;
        Fonts[] fonts = Fonts.values();
        if (fontIndex < 0 || fontIndex >= fonts.length) {
            return null;
        }
        return fonts[fontIndex].get(FONT_SIZE);
    }

    private int getStringWidth(String text) {
        if (this.useMinecraftFont()) {
            return mc.fontRendererObj.getStringWidth(text);
        }
        FontRenderer fontRenderer = this.getCustomFont();
        return fontRenderer == null ? mc.fontRendererObj.getStringWidth(text) : fontRenderer.getStringWidth(text);
    }

    private int getFontHeight() {
        if (this.useMinecraftFont()) {
            return mc.fontRendererObj.FONT_HEIGHT;
        }
        FontRenderer fontRenderer = this.getCustomFont();
        return fontRenderer == null ? mc.fontRendererObj.FONT_HEIGHT : fontRenderer.getHeight();
    }

    private void drawString(String text, float x, float y, int color, boolean shadow) {
        if (this.useMinecraftFont()) {
            mc.fontRendererObj.drawString(text, x, y, color, shadow);
            return;
        }
        FontRenderer fontRenderer = this.getCustomFont();
        if (fontRenderer == null) {
            mc.fontRendererObj.drawString(text, x, y, color, shadow);
        } else if (shadow) {
            fontRenderer.drawStringWithShadow(text, x, y, color);
        } else {
            fontRenderer.drawString(text, x, y, color);
        }
    }

    private void drawSeparator(float x, float y, int color) {
        RenderUtil.drawRect(x, y + 1.5F, x + 1.0F, y + this.getFontHeight() - 1.5F, ColorUtil.darker(new Color(color, true), 0.65F).getRGB());
    }

    private static class WatermarkBounds {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private WatermarkBounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        private float width() {
            return this.right - this.left;
        }

        private float height() {
            return this.bottom - this.top;
        }
    }
}
