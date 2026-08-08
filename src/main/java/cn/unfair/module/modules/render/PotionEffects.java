package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public class PotionEffects extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ResourceLocation INVENTORY_TEXTURE = new ResourceLocation("textures/gui/container/inventory.png");
    private static final float MIN_WIDGET_WIDTH = 120.0F;

    public final BooleanProperty showName = new BooleanProperty("show-name", true);
    public final BooleanProperty blink = new BooleanProperty("blink", true);
    public final IntProperty blinkSeconds = new IntProperty("blink-seconds", 10, 2, 20, this.blink::getValue);
    public final BooleanProperty background = new BooleanProperty("background", false);
    public final ColorProperty nameColor = new ColorProperty("name-color", Color.WHITE.getRGB());
    public final ColorProperty durationColor = new ColorProperty("duration-color", Color.WHITE.getRGB());
    private float widgetWidth = MIN_WIDGET_WIDTH;
    private int ticks;

    public PotionEffects() {
        super("PotionEffects", false, true);
    }

    public boolean shouldRenderWidget() {
        return this.isEnabled() && mc.thePlayer != null;
    }

    public void tickBlink() {
        this.ticks++;
        if (this.ticks > 20) {
            this.ticks = 0;
        }
    }

    public float[] getWidgetSize() {
        List<PotionEffect> effects = getEffects();
        if (effects.isEmpty()) {
            return new float[]{this.widgetWidth, 22.0F};
        }
        int maxWidth = 0;
        for (PotionEffect effect : effects) {
            String name = getEffectName(effect);
            String duration = Potion.getDurationString(effect);
            int width = 20 + Math.max(
                    this.showName.getValue() ? mc.fontRendererObj.getStringWidth(name) : 0,
                    mc.fontRendererObj.getStringWidth(duration)
            );
            maxWidth = Math.max(maxWidth, width);
        }
        this.widgetWidth = Math.max(this.widgetWidth, Math.max(MIN_WIDGET_WIDTH, maxWidth));
        return new float[]{this.widgetWidth, effects.size() * 22.0F};
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
        float[] size = getWidgetSize();
        if (mask) {
            RenderUtil.drawRect(x, y, x + size[0], y + size[1], color);
            return;
        }
        if (this.background.getValue()) {
            RenderUtil.drawRect(x - 3.0F, y - 3.0F, x + size[0] + 3.0F, y + size[1] + 3.0F, new Color(0, 0, 0, 90).getRGB());
        }
        float rowY = y;
        for (PotionEffect effect : effects) {
            renderEffect(effect, x, rowY);
            rowY += 22.0F;
        }
    }

    private void renderEffect(PotionEffect effect, float x, float y) {
        Potion potion = Potion.potionTypes[effect.getPotionID()];
        if (potion != null && potion.hasStatusIcon()) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(INVENTORY_TEXTURE);
            int icon = potion.getStatusIconIndex();
            Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, icon % 8 * 18, 198 + icon / 8 * 18, 18, 18, 256.0F, 256.0F);
        }
        float textX = x + 20.0F;
        if (this.showName.getValue()) {
            mc.fontRendererObj.drawStringWithShadow(getEffectName(effect), textX, y, this.nameColor.getValue());
            mc.fontRendererObj.drawStringWithShadow(getDurationText(effect), textX, y + 10.0F, this.durationColor.getValue());
        } else {
            mc.fontRendererObj.drawStringWithShadow(getDurationText(effect), textX, y + 5.0F, this.durationColor.getValue());
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
