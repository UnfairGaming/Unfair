package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
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
    private static final float FONT_SIZE = 16.0F;
    private static final String MINECRAFT_FONT = "Minecraft";
    private static final float MIN_WIDGET_WIDTH = 32.0F;

    public final ModeProperty font = new ModeProperty("Font", 0, getFontModes());
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
        if (effects.isEmpty()) {
            return new float[]{32.0F, this.getRowHeight()};
        }
        int maxWidth = 0;
        for (PotionEffect effect : effects) {
            String name = getEffectName(effect);
            String duration = Potion.getDurationString(effect);
            int width = 20 + Math.max(
                    this.showName.getValue() ? this.getStringWidth(name) : 0,
                    this.getStringWidth(duration)
            );
            maxWidth = Math.max(maxWidth, width);
        }
        return new float[]{Math.max(32.0F, maxWidth), effects.size() * this.getRowHeight()};
    }

    public float getRowHeight() {
        return Math.max(22.0F, this.showName.getValue() ? this.getFontHeight() * 2.0F + 3.0F : this.getFontHeight() + 6.0F);
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
        float[] size = getContentSize();
        float left = x - 3.0F;
        float top = y - 3.0F;
        float right = x + size[0] + 3.0F;
        float bottom = y + size[1];
        if (mask) {
            RenderUtil.drawRoundedRectangle(left, top, right, bottom, 2.5F, color);
            return;
        }
        if (this.background.getValue() > 0) {
            RenderUtil.drawRoundedRectangle(left, top, right, bottom, 2.5F, new Color(0, 0, 0, this.background.getValue()).getRGB());
        }
        float rowY = y;
        for (PotionEffect effect : effects) {
            renderEffect(effect, x, rowY);
            rowY += this.getRowHeight();
        }
    }

    private void renderEffect(PotionEffect effect, float x, float y) {
        RenderUtil.renderPotionEffect(
                effect,
                (int) x,
                (int) (y + (this.getRowHeight() - 18.0F) / 2.0F)
        );
        float textX = x + 20.0F;
        if (this.showName.getValue()) {
            float textY = y + this.getTextBlockOffset();
            this.drawString(getEffectName(effect), textX, textY, this.nameColor.getValue(), true);
            this.drawString(getDurationText(effect), textX, textY + this.getFontHeight() + 1.0F, this.durationColor.getValue(), true);
        } else {
            this.drawString(getDurationText(effect), textX, y + (this.getRowHeight() - this.getFontHeight()) / 2.0F, this.durationColor.getValue(), true);
        }
    }

    private float getTextBlockOffset() {
        float textHeight = this.getFontHeight() * 2.0F + 1.0F;
        return Math.max(0.0F, (this.getRowHeight() - textHeight) / 2.0F);
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
