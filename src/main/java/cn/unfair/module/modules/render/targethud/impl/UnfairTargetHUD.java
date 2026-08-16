package cn.unfair.module.modules.render.targethud.impl;

import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.module.modules.render.targethud.TargetHUDMode;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.AndroidUtil;
import cn.unfair.util.AnimationUtil;
import cn.unfair.util.MathUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.util.MathHelper;

import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class UnfairTargetHUD extends TargetHUDMode {
    public final PercentProperty background = new PercentProperty("Background", 65);
    public final BooleanProperty animations = new BooleanProperty("Animations", true);

    public UnfairTargetHUD() {
        super("Unfair");
    }

    @Override
    public void render(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y) {
        float width = this.getSize(targetHUD, data)[0];
        float height = this.getSize(targetHUD, data)[1];
        int fadeAlpha = targetHUD.getFadeAlpha();
        if (fadeAlpha <= 0) {
            return;
        }
        float progress = fadeAlpha / 255.0F;
        float scale = AnimationUtil.popScale(progress);
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;

        float targetHealth = TargetHUD.finiteHealth(data.targetHealth());
        float playerHealth = TargetHUD.finiteHealth(data.playerHealth());
        float maxHealth = Math.max(TargetHUD.finiteHealth(data.maxHealth()), 1.0F);
        float absorption = TargetHUD.finiteHealth(data.absorption());
        float animatedHealth = this.getAnimatedHealth(targetHUD, targetHealth);
        float animatedMaxHealth = Math.max(TargetHUD.finiteHealth(targetHUD.maxHealth), maxHealth);
        float ratio = MathHelper.clamp_float(animatedHealth / animatedMaxHealth, 0.0F, 1.0F);
        float absorptionRatio = MathHelper.clamp_float(absorption / maxHealth, 0.0F, 1.0F);
        float space = width - 43.0F;
        int[] colors = targetHUD.getRavenGradientColors();
        float partialTicks = TargetHUD.mc.timer.renderPartialTicks;
        float hurtProgress = data.entity().hurtTime == 0
                ? 0.0F
                : MathHelper.clamp_float((data.entity().hurtTime - partialTicks) / 10.0F, 0.0F, 1.0F);

        if (this.background.getValue() > 0) {
            int backgroundAlpha = (int) (this.background.getValue() / 100.0F * fadeAlpha);
            RenderUtil.drawRoundedRectangle(
                    RenderUtil.scaleAround(x, centerX, scale),
                    RenderUtil.scaleAround(y, centerY, scale),
                    RenderUtil.scaleAround(x + width, centerX, scale),
                    RenderUtil.scaleAround(y + height, centerY, scale),
                    7.0F * scale,
                    new Color(15, 15, 18, backgroundAlpha).getRGB()
            );
        }
        RenderUtil.drawRoundedRectangle(
                RenderUtil.scaleAround(x + 38.5F, centerX, scale),
                RenderUtil.scaleAround(y + 28.0F, centerY, scale),
                RenderUtil.scaleAround(x + 38.5F + space, centerX, scale),
                RenderUtil.scaleAround(y + 32.0F, centerY, scale),
                2.0F * scale,
                new Color(0, 0, 0, (int) (150.0F * progress)).getRGB()
        );
        if (ratio > 0.01F) {
            RenderUtil.drawRoundedGradientRect(
                    RenderUtil.scaleAround(x + 38.5F, centerX, scale),
                    RenderUtil.scaleAround(y + 28.0F, centerY, scale),
                    RenderUtil.scaleAround(x + 38.5F + space * ratio, centerX, scale),
                    RenderUtil.scaleAround(y + 32.0F, centerY, scale),
                    2.0F * scale,
                    RenderUtil.mergeAlpha(colors[0], fadeAlpha), RenderUtil.mergeAlpha(colors[0], fadeAlpha),
                    RenderUtil.mergeAlpha(colors[1], fadeAlpha), RenderUtil.mergeAlpha(colors[1], fadeAlpha)
            );
        }
        if (absorptionRatio > 0.01F) {
            RenderUtil.drawRoundedGradientRect(
                    RenderUtil.scaleAround(x + 38.5F, centerX, scale),
                    RenderUtil.scaleAround(y + 28.0F, centerY, scale),
                    RenderUtil.scaleAround(x + 38.5F + space * absorptionRatio, centerX, scale),
                    RenderUtil.scaleAround(y + 32.0F, centerY, scale),
                    2.0F * scale,
                    new Color(255, 210, 55, fadeAlpha).getRGB(), new Color(255, 210, 55, fadeAlpha).getRGB(),
                    new Color(255, 235, 110, fadeAlpha).getRGB(), new Color(255, 235, 110, fadeAlpha).getRGB()
            );
        }

        float targetHp = animatedHealth;
        float playerHp = playerHealth;
        String health = this.floorToTwoPlaces(targetHp) + "HP";
        String diff = this.diffText(playerHp, targetHealth);
        FontRenderer nameFont = Fonts.interSemiBold.get(18.0F);
        FontRenderer infoFont = Fonts.interSemiBold.get(13.0F);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(centerX, centerY, 0.0F);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1.0F);
        net.minecraft.client.renderer.GlStateManager.translate(-centerX, -centerY, 0.0F);
        nameFont.drawStringWithShadow(data.entity().getName(), x + 37.0F, y + 5.0F, RenderUtil.mergeAlpha(Color.WHITE.getRGB(), fadeAlpha));
        infoFont.drawStringWithShadow(health, x + 37.0F, y + 17.0F, RenderUtil.mergeAlpha(Color.LIGHT_GRAY.getRGB(), fadeAlpha));
        infoFont.drawStringWithShadow(diff, x + 115.0F - infoFont.getStringWidth(diff), y + 17.0F, RenderUtil.mergeAlpha(Color.LIGHT_GRAY.getRGB(), fadeAlpha));
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        float headHurtScale = 1.0F - 0.15F * AnimationUtil.easeOutQuad(hurtProgress);
        int greenBlue = (int) (255.0F * (1.0F - 0.75F * hurtProgress));
        Color headColor = new Color(255, MathHelper.clamp_int(greenBlue, 0, 255), MathHelper.clamp_int(greenBlue, 0, 255), fadeAlpha);
        float baseHeadX = RenderUtil.scaleAround(x + 2.5F, centerX, scale);
        float baseHeadY = RenderUtil.scaleAround(y + 2.5F, centerY, scale);
        float baseHeadSize = 32.0F * scale;
        float headSize = baseHeadSize * headHurtScale;
        float headX = baseHeadX + (baseHeadSize - headSize) / 2.0F;
        float headY = baseHeadY + (baseHeadSize - headSize) / 2.0F;
        float headRadius = 5.0F * scale * headHurtScale;
        RenderUtil.drawRoundedRectangle(
                headX,
                headY,
                headX + headSize,
                headY + headSize,
                headRadius,
                playerHp >= targetHealth ? new Color(0, 0, 0, 0).getRGB() : new Color(255, 0, 0, (int) (85.0F * progress)).getRGB());
        if (AndroidUtil.isAndroid()) {
            RenderUtil.renderPlayerHead(data.entity(), headX, headY, headSize, headColor);
        } else {
            RenderUtil.renderRoundedPlayerHead(
                    data.entity(),
                    headX,
                    headY,
                    headSize,
                    headRadius,
                    headColor
            );
        }
    }

    @Override
    public float[] getSize(TargetHUD targetHUD, TargetHUD.RenderData data) {
        return new float[]{120.0F, 37.0F};
    }

    @Override
    public boolean shouldRenderEffects(TargetHUD targetHUD) {
        return this.background.getValue() > 0;
    }

    @Override
    public void renderMask(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y, int color) {
        float[] size = this.getSize(targetHUD, data);
        int fadeAlpha = targetHUD.getFadeAlpha();
        if (fadeAlpha <= 0) {
            return;
        }
        float scale = AnimationUtil.popScale(fadeAlpha / 255.0F);
        float centerX = x + size[0] / 2.0F;
        float centerY = y + size[1] / 2.0F;
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRectangle(
                RenderUtil.scaleAround(x, centerX, scale),
                RenderUtil.scaleAround(y, centerY, scale),
                RenderUtil.scaleAround(x + size[0], centerX, scale),
                RenderUtil.scaleAround(y + size[1], centerY, scale),
                7.0F * scale,
                RenderUtil.mergeAlpha(color, (color >> 24 & 255) * fadeAlpha / 255)
        );
        RenderUtil.disableRenderState();
    }

    private float getAnimatedHealth(TargetHUD targetHUD, float fallbackHealth) {
        boolean hasAnimationState = targetHUD.maxHealth > 0.0F || targetHUD.oldHealth != 0.0F || targetHUD.newHealth != 0.0F;
        if (!this.animations.getValue() || !hasAnimationState) {
            return fallbackHealth;
        }
        float elapsedTime = (float) Math.clamp(targetHUD.animTimer.getElapsedTime(), 0L, 150L);
        return TargetHUD.finiteHealth(MathUtil.interpolate(targetHUD.oldHealth, targetHUD.newHealth, elapsedTime / 150.0F));
    }

    @Override
    public boolean shouldAnimateHealth() {
        return this.animations.getValue();
    }

    private String diffText(float playerHealth, float targetHealth) {
        double diff = this.floorToTwoPlaces(TargetHUD.finiteOrDefault(playerHealth - targetHealth, 0.0F));
        if (diff > 0.0D) {
            return "+" + diff;
        }
        if (diff < 0.0D) {
            return String.valueOf(diff);
        }
        return "+0.0";
    }

    private double floorToTwoPlaces(float value) {
        return BigDecimal.valueOf(TargetHUD.finiteOrDefault(value, 0.0F)).setScale(2, RoundingMode.FLOOR).doubleValue();
    }
}
