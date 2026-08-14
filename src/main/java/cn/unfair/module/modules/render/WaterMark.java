package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
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

import java.awt.*;

public class WaterMark extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float FONT_SIZE = 16.0F;
    private static final String MINECRAFT_FONT = "Minecraft";
    private static final int VERSION_COLOR = 0xEBE6E8EE;
    private static final int INFO_COLOR = 0xE6D2D6E1;
    private static final int BACKGROUND_RGB = 9 << 16 | 11 << 8 | 15;

    public final ModeProperty font = new ModeProperty("Font", 0, getFontModes());
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("Background", 0);
    public final BooleanProperty shadow = new BooleanProperty("Shadow", true);
    public final BooleanProperty showVersion = new BooleanProperty("Version", true);
    public final BooleanProperty showFps = new BooleanProperty("Fps", false);
    public final BooleanProperty showPing = new BooleanProperty("Ping", false);
    private int cachedFont = -1;
    private boolean cachedShowVersion;
    private boolean cachedShowFps;
    private boolean cachedShowPing;
    private String cachedVersion = "";
    private long cachedInfoTime;
    private String[] cachedSegments = new String[0];
    private float cachedScale = -1.0F;
    private float cachedWidth;
    private float cachedHeight;

    public WaterMark() {
        super("WaterMark", true, true);
    }

    private static String[] getFontModes() {
        Fonts[] fonts = Fonts.values();
        String[] modes = new String[fonts.length + 1];
        modes[0] = MINECRAFT_FONT;
        for (int i = 0; i < fonts.length; i++) {
            String fontName = fonts[i].name();
            modes[i + 1] = Character.toUpperCase(fontName.charAt(0)) + fontName.substring(1);
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

        this.updateLayoutCache();
        float scaleValue = this.scale.getValue();
        long time = System.currentTimeMillis();
        int accent = HUD.getColor(time).getRGB();
        int backgroundAlpha = (int) (this.background.getValue().floatValue() / 100.0F * 220.0F);
        float paddingX = 4.0F * scaleValue;
        float paddingY = 3.0F * scaleValue;
        float separatorGap = 7.0F * scaleValue;

        if (backgroundAlpha > 0) {
            RenderUtil.enableRenderState();
            this.drawBackground(x, y, x + this.cachedWidth, y + this.cachedHeight, backgroundAlpha << 24 | BACKGROUND_RGB);
            RenderUtil.disableRenderState();
        }

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        this.drawString("Unfair", x + paddingX, y + paddingY, accent, this.shadow.getValue());

        float cursor = x + paddingX + this.getStringWidth("Unfair");
        if (this.showVersion.getValue()) {
            this.drawString(" " + Unfair.version, cursor, y + paddingY, VERSION_COLOR, this.shadow.getValue());
            cursor += this.getStringWidth(" " + Unfair.version);
        }

        for (String segment : this.cachedSegments) {
            this.drawSeparator(cursor + 3.0F * scaleValue, y + paddingY, accent);
            cursor += separatorGap;
            this.drawString(segment, cursor, y + paddingY, INFO_COLOR, this.shadow.getValue());
            cursor += this.getStringWidth(segment);
        }
        GlStateManager.disableBlend();
    }

    public void renderWidgetMask(float x, float y, int color) {
        if (!this.shouldRenderWidgetEffects()) {
            return;
        }
        this.updateLayoutCache();
        RenderUtil.enableRenderState();
        this.drawBackgroundMask(x, y, x + this.cachedWidth, y + this.cachedHeight, color);
        RenderUtil.disableRenderState();
    }

    private void drawBackground(float left, float top, float right, float bottom, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        RenderUtil.drawRoundedRectangle(left, top, right, bottom, 2.5F, color);
    }

    private void drawBackgroundMask(float left, float top, float right, float bottom, int color) {
        if (((color >> 24) & 0xFF) <= 0) {
            return;
        }
        RenderUtil.drawRoundedRectangle(left, top, right, bottom, 2.5F, color);
    }

    public float[] getWidgetSize() {
        this.updateLayoutCache();
        return new float[]{this.cachedWidth, this.cachedHeight};
    }

    private void updateLayoutCache() {
        long now = System.currentTimeMillis();
        float scaleValue = this.scale.getValue();
        boolean dynamicInfo = this.showFps.getValue() || this.showPing.getValue();
        boolean dirty = this.cachedFont != this.font.getValue()
                || this.cachedScale != scaleValue
                || this.cachedShowVersion != this.showVersion.getValue()
                || this.cachedShowFps != this.showFps.getValue()
                || this.cachedShowPing != this.showPing.getValue()
                || !this.cachedVersion.equals(Unfair.version)
                || (dynamicInfo && now - this.cachedInfoTime > 250L);
        if (!dirty && this.cachedWidth > 0.0F && this.cachedHeight > 0.0F) {
            return;
        }

        this.cachedFont = this.font.getValue();
        this.cachedShowVersion = this.showVersion.getValue();
        this.cachedShowFps = this.showFps.getValue();
        this.cachedShowPing = this.showPing.getValue();
        this.cachedVersion = Unfair.version;
        this.cachedInfoTime = now;
        this.cachedScale = scaleValue;
        this.cachedSegments = this.buildInfoSegments();

        float width = 8.0F * scaleValue + this.getStringWidth("Unfair");
        if (this.showVersion.getValue()) {
            width += this.getStringWidth(" " + Unfair.version);
        }
        for (String segment : this.cachedSegments) {
            width += 7.0F * scaleValue + this.getStringWidth(segment);
        }

        this.cachedWidth = width;
        this.cachedHeight = Math.max(14.0F * scaleValue, this.getFontHeight() + 5.0F * scaleValue);
    }

    private String[] buildInfoSegments() {
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
        return this.getCustomFont(this.scale.getValue());
    }

    private FontRenderer getCustomFont(float scaleValue) {
        int fontIndex = this.font.getValue() - 1;
        Fonts[] fonts = Fonts.values();
        if (fontIndex < 0 || fontIndex >= fonts.length) {
            return null;
        }
        return fonts[fontIndex].get(FONT_SIZE * scaleValue);
    }

    private int getStringWidth(String text) {
        float scaleValue = this.scale.getValue();
        if (this.useMinecraftFont()) {
            return Math.round(mc.fontRendererObj.getStringWidth(text) * scaleValue);
        }
        FontRenderer fontRenderer = this.getCustomFont(scaleValue);
        return fontRenderer == null ? Math.round(mc.fontRendererObj.getStringWidth(text) * scaleValue) : fontRenderer.getStringWidth(text);
    }

    private int getFontHeight() {
        float scaleValue = this.scale.getValue();
        if (this.useMinecraftFont()) {
            return Math.round(mc.fontRendererObj.FONT_HEIGHT * scaleValue);
        }
        FontRenderer fontRenderer = this.getCustomFont(scaleValue);
        return fontRenderer == null ? Math.round(mc.fontRendererObj.FONT_HEIGHT * scaleValue) : fontRenderer.getHeight();
    }

    private void drawString(String text, float x, float y, int color, boolean shadow) {
        float scaleValue = this.scale.getValue();
        if (this.useMinecraftFont()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scaleValue, scaleValue, 1.0F);
            mc.fontRendererObj.drawString(text, 0.0F, 0.0F, color, shadow);
            GlStateManager.popMatrix();
            return;
        }
        FontRenderer fontRenderer = this.getCustomFont(scaleValue);
        if (fontRenderer == null) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scaleValue, scaleValue, 1.0F);
            mc.fontRendererObj.drawString(text, 0.0F, 0.0F, color, shadow);
            GlStateManager.popMatrix();
        } else if (shadow) {
            fontRenderer.drawStringWithShadow(text, x, y, color);
        } else {
            fontRenderer.drawString(text, x, y, color);
        }
    }

    private void drawSeparator(float x, float y, int color) {
        float scaleValue = this.scale.getValue();
        RenderUtil.drawRect(
                x,
                y + 1.5F * scaleValue,
                x + Math.max(1.0F, scaleValue),
                y + this.getFontHeight() - 1.5F * scaleValue,
                ColorUtil.darker(new Color(color, true), 0.65F).getRGB()
        );
    }
}
