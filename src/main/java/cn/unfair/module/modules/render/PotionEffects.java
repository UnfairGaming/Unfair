package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class PotionEffects extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float BASE_FONT_SIZE = 16.0F;
    private static final String MINECRAFT_FONT = "Minecraft";
    private static final float MIN_WIDGET_WIDTH = 32.0F;

    public final ModeProperty font = new ModeProperty("Font", 0, getFontModes());
    public final FloatProperty scale = new FloatProperty("Scale", 1.0F, 0.5F, 1.5F);
    public final BooleanProperty showName = new BooleanProperty("Show Name", true);
    public final BooleanProperty blink = new BooleanProperty("Blink", true);
    public final IntProperty blinkSeconds = new IntProperty("Blink Seconds", 10, 2, 20, this.blink::getValue);
    public final PercentProperty background = new PercentProperty("Background", 50);
    public final ColorProperty nameColor = new ColorProperty("Name Color", Color.WHITE.getRGB());
    public final ColorProperty durationColor = new ColorProperty("Duration Color", Color.WHITE.getRGB());
    private float widgetWidth = MIN_WIDGET_WIDTH;
    private int ticks;

    public PotionEffects() {
        super("PotionEffects", false, true);
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
        return this.isEnabled() && mc.thePlayer != null;
    }

    public boolean shouldRenderWidgetEffects() {
        return this.shouldRenderWidget() && this.background.getValue() > 0 && !getEffects().isEmpty();
    }

    public void tickBlink() {
        this.ticks++;
        if (this.ticks > 20) {
            this.ticks = 0;
        }
    }

    public float[] getWidgetSize() {
        float[] contentSize = this.getContentSize();
        this.widgetWidth = Math.max(this.widgetWidth, contentSize[0]);
        return new float[]{this.widgetWidth, contentSize[1]};
    }

    public float[] getContentSize() {
        List<PotionEffect> effects = getEffects();
        float scale = this.scale.getValue();
        if (effects.isEmpty()) {
            return new float[]{32.0F * scale, this.getRowHeight()};
        }
        int maxWidth = 0;
        for (PotionEffect effect : effects) {
            String name = getEffectName(effect);
            String duration = Potion.getDurationString(effect);
            int width = (int) (20.0F * scale + Math.max(
                    this.showName.getValue() ? this.getStringWidth(name) : 0,
                    this.getStringWidth(duration)
            ));
            maxWidth = Math.max(maxWidth, width);
        }
        return new float[]{Math.max(32.0F * scale, maxWidth), effects.size() * this.getRowHeight()};
    }

    public float getRowHeight() {
        float scale = this.scale.getValue();
        float fontHeight = this.getFontHeight();
        float baseHeight = this.showName.getValue() ? fontHeight * 2.0F + 3.0F * scale : fontHeight + 6.0F * scale;
        return Math.max(22.0F * scale, baseHeight);
    }

    public void renderWidget(float x, float y) {
        renderWidget(x, y, false, 0);
    }

    public void renderWidgetMask(float x, float y, int color) {
        renderWidget(x, y, true, color);
    }

    private void renderWidget(float x, float y, boolean mask, int color) {
        List<PotionEffect> effects = getEffects();
        if (effects.isEmpty()) {
            return;
        }
        float scale = this.scale.getValue();
        float[] size = getContentSize();
        float left = x - 3.0F * scale;
        float top = y - 3.0F * scale;
        float right = x + size[0] + 3.0F * scale;
        float bottom = y + size[1] + 3.0F * scale;
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        Float radius = hud.roundRadius.getValue() * hud.scale.getValue();
        if (mask) {
            RenderUtil.drawRoundedRectangle(left, top, right, bottom, radius, color);
            return;
        }
        if (this.background.getValue() > 0) {
            RenderUtil.drawRoundedRectangle(left, top, right, bottom, radius, new Color(0, 0, 0, this.background.getValue()).getRGB());
        }
        float rowY = y;
        for (PotionEffect effect : effects) {
            renderEffect(effect, x, rowY, scale);
            rowY += this.getRowHeight();
        }
    }

    private void renderEffect(PotionEffect effect, float x, float y, float scale) {
        float iconSize = 18.0F * scale;
        float iconY = y + (this.getRowHeight() - iconSize) / 2.0F;
        RenderUtil.renderPotionEffect(effect, (int) x, (int) iconY, scale);
        float textX = x + 20.0F * scale;
        if (this.showName.getValue()) {
            float textY = y + this.getTextBlockOffset(scale);
            this.drawString(getEffectName(effect), textX, textY, this.nameColor.getValue(), scale);
            this.drawString(getDurationText(effect), textX, textY + this.getFontHeight() + scale, this.durationColor.getValue(), scale);
        } else {
            this.drawString(getDurationText(effect), textX, y + (this.getRowHeight() - this.getFontHeight()) / 2.0F, this.durationColor.getValue(), scale);
        }
    }

    private float getTextBlockOffset(float scale) {
        float textHeight = this.getFontHeight() * 2.0F + scale;
        return Math.max(0.0F, (this.getRowHeight() - textHeight) / 2.0F);
    }

    private boolean useMinecraftFont() {
        return this.font.getValue() == 0;
    }

    private FontRenderer getCustomFont(float scale) {
        int fontIndex = this.font.getValue() - 1;
        Fonts[] fonts = Fonts.values();
        if (fontIndex < 0 || fontIndex >= fonts.length) {
            return null;
        }
        return fonts[fontIndex].get(BASE_FONT_SIZE * scale);
    }

    private int getStringWidth(String text) {
        float scale = this.scale.getValue();
        if (this.useMinecraftFont()) {
            return (int) (mc.fontRendererObj.getStringWidth(text) * scale);
        }
        FontRenderer fontRenderer = this.getCustomFont(scale);
        return fontRenderer == null ? (int) (mc.fontRendererObj.getStringWidth(text) * scale) : fontRenderer.getStringWidth(text);
    }

    private float getFontHeight() {
        float scale = this.scale.getValue();
        if (this.useMinecraftFont()) {
            return mc.fontRendererObj.FONT_HEIGHT * scale;
        }
        FontRenderer fontRenderer = this.getCustomFont(scale);
        return fontRenderer == null ? mc.fontRendererObj.FONT_HEIGHT * scale : fontRenderer.getHeight();
    }

    private void drawString(String text, float x, float y, int color, float scale) {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        Boolean shouldShadow = hud.shadow.getValue();
        if (this.useMinecraftFont()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x, y, 0.0F);
            GlStateManager.scale(scale, scale, 1.0F);
            if (shouldShadow) {
                mc.fontRendererObj.drawStringWithShadow(text, 0.0F, 0.0F, color);
            } else {
                mc.fontRendererObj.drawString(text, 0.0F, 0.0F, color, false);
            }
            GlStateManager.popMatrix();
            return;
        }
        FontRenderer fontRenderer = this.getCustomFont(scale);
        if (fontRenderer == null) {
            mc.fontRendererObj.drawString(text, x, y, color, shouldShadow);
        } else if (shouldShadow) {
            fontRenderer.drawStringWithShadow(text, x, y, color);
        } else {
            fontRenderer.drawString(text, x, y, color);
        }
    }

    private String getDurationText(PotionEffect effect) {
        if (this.blink.getValue() && effect.getDuration() <= this.blinkSeconds.getValue() * 20 && this.ticks > 10) {
            return "";
        }
        return Potion.getDurationString(effect);
    }

    private List<PotionEffect> getEffects() {
        Collection<PotionEffect> active = mc.thePlayer == null ? List.of() : mc.thePlayer.getActivePotionEffects();
        List<PotionEffect> effects = new ArrayList<>(active);
        effects.sort(Comparator.comparingInt(PotionEffect::getDuration));
        return effects;
    }

    private String getEffectName(PotionEffect effect) {
        return I18n.format(effect.getEffectName()) + getLevelName(effect.getAmplifier());
    }

    private String getLevelName(int level) {
        return switch (level) {
            case 1 -> " II";
            case 2 -> " III";
            case 3 -> " IV";
            case 4 -> " V";
            case 5 -> " VI";
            case 6 -> " VII";
            case 7 -> " VIII";
            case 8 -> " IX";
            case 9 -> " X";
            default -> level > 9 ? " " + (level + 1) : "";
        };
    }
}