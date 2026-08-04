package cn.unfair.module.modules.render.targethud.impl;

import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.mixin.IAccessorMinecraft;
import cn.unfair.module.modules.render.targethud.TargetHUDMode;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.util.MathHelper;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class TargetHUDNewMode extends TargetHUDMode {
    public final PercentProperty background = new PercentProperty("background", 65);

    public TargetHUDNewMode() {
        super("New");
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
        float scale = this.getPopScale(progress);
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;

        float ratio = MathHelper.clamp_float(data.targetHealth / Math.max(data.maxHealth, 1.0F), 0.0F, 1.0F);
        float absorptionRatio = Math.min(data.absorption / Math.max(data.maxHealth, 1.0F), 1.0F);
        float space = width - 43.0F;
        int[] colors = targetHUD.getRavenGradientColors();
        float partialTicks = ((IAccessorMinecraft) TargetHUD.mc).getTimer().renderPartialTicks;
        float hurtTime = data.entity.hurtTime == 0 ? 0.0F : (data.entity.hurtTime - partialTicks) * 0.3F;

        if (this.background.getValue() > 0) {
            int backgroundAlpha = (int) (this.background.getValue() / 100.0F * fadeAlpha);
            RenderUtil.drawRoundedRectangle(
                    this.scaleX(x, centerX, scale),
                    this.scaleY(y, centerY, scale),
                    this.scaleX(x + width, centerX, scale),
                    this.scaleY(y + height, centerY, scale),
                    this.scaleSize(7.0F, scale),
                    new Color(15, 15, 18, backgroundAlpha).getRGB()
            );
        }
        RenderUtil.drawRoundedRectangle(
                this.scaleX(x + 38.5F, centerX, scale),
                this.scaleY(y + 28.0F, centerY, scale),
                this.scaleX(x + 38.5F + space, centerX, scale),
                this.scaleY(y + 32.0F, centerY, scale),
                this.scaleSize(2.0F, scale),
                new Color(0, 0, 0, (int) (150.0F * progress)).getRGB()
        );
        if (ratio > 0.01F) {
            RenderUtil.drawRoundedGradientRect(
                    this.scaleX(x + 38.5F, centerX, scale),
                    this.scaleY(y + 28.0F, centerY, scale),
                    this.scaleX(x + 38.5F + space * ratio, centerX, scale),
                    this.scaleY(y + 32.0F, centerY, scale),
                    this.scaleSize(2.0F, scale),
                    RenderUtil.mergeAlpha(colors[0], fadeAlpha), RenderUtil.mergeAlpha(colors[0], fadeAlpha),
                    RenderUtil.mergeAlpha(colors[1], fadeAlpha), RenderUtil.mergeAlpha(colors[1], fadeAlpha)
            );
        }
        if (absorptionRatio > 0.01F) {
            RenderUtil.drawRoundedGradientRect(
                    this.scaleX(x + 38.5F, centerX, scale),
                    this.scaleY(y + 28.0F, centerY, scale),
                    this.scaleX(x + 38.5F + space * absorptionRatio, centerX, scale),
                    this.scaleY(y + 32.0F, centerY, scale),
                    this.scaleSize(2.0F, scale),
                    new Color(255, 210, 55, fadeAlpha).getRGB(), new Color(255, 210, 55, fadeAlpha).getRGB(),
                    new Color(255, 235, 110, fadeAlpha).getRGB(), new Color(255, 235, 110, fadeAlpha).getRGB()
            );
        }

        float targetHp = data.entity.getHealth() + data.entity.getAbsorptionAmount();
        float playerHp = TargetHUD.mc.thePlayer == null ? 0.0F : TargetHUD.mc.thePlayer.getHealth() + TargetHUD.mc.thePlayer.getAbsorptionAmount();
        String health = String.valueOf(BigDecimal.valueOf(targetHp).setScale(2, RoundingMode.FLOOR).doubleValue()) + "HP";
        String diff = this.diffText(playerHp, targetHp);
        FontRenderer nameFont = Fonts.interSemiBold.get(18.0F);
        FontRenderer infoFont = Fonts.interSemiBold.get(13.0F);
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        net.minecraft.client.renderer.GlStateManager.translate(centerX, centerY, 0.0F);
        net.minecraft.client.renderer.GlStateManager.scale(scale, scale, 1.0F);
        net.minecraft.client.renderer.GlStateManager.translate(-centerX, -centerY, 0.0F);
        nameFont.drawStringWithShadow(data.entity.getName(), x + 37.0F, y + 5.0F, RenderUtil.mergeAlpha(Color.WHITE.getRGB(), fadeAlpha));
        infoFont.drawStringWithShadow(health, x + 37.0F, y + 17.0F, RenderUtil.mergeAlpha(Color.LIGHT_GRAY.getRGB(), fadeAlpha));
        infoFont.drawStringWithShadow(diff, x + 115.0F - infoFont.getStringWidth(diff), y + 17.0F, RenderUtil.mergeAlpha(Color.LIGHT_GRAY.getRGB(), fadeAlpha));
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        Color headColor = new Color(255, (int) (255 - Math.max(0.0F, hurtTime) * 80.0F), (int) (255 - Math.max(0.0F, hurtTime) * 80.0F), fadeAlpha);
        RenderUtil.drawRoundedRectangle(
                this.scaleX(x + 2.55F, centerX, scale),
                this.scaleY(y + 2.55F, centerY, scale),
                this.scaleX(x + 34.65F, centerX, scale),
                this.scaleY(y + 34.65F, centerY, scale),
                this.scaleSize(5.0F, scale),
                playerHp >= targetHp ? new Color(0, 0, 0, 0).getRGB() : new Color(255, 0, 0, (int) (85.0F * progress)).getRGB());
        RenderUtil.renderRoundedPlayerHead(
                data.entity,
                this.scaleX(x + 2.5F, centerX, scale),
                this.scaleY(y + 2.5F, centerY, scale),
                this.scaleSize(32.0F, scale),
                this.scaleSize(5.0F, scale),
                headColor
        );
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
        float scale = this.getPopScale(fadeAlpha / 255.0F);
        float centerX = x + size[0] / 2.0F;
        float centerY = y + size[1] / 2.0F;
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRectangle(
                this.scaleX(x, centerX, scale),
                this.scaleY(y, centerY, scale),
                this.scaleX(x + size[0], centerX, scale),
                this.scaleY(y + size[1], centerY, scale),
                this.scaleSize(7.0F, scale),
                RenderUtil.mergeAlpha(color, (color >> 24 & 255) * fadeAlpha / 255)
        );
        RenderUtil.disableRenderState();
    }

    private float getPopScale(float progress) {
        return 0.82F + this.easeOutBack(progress) * 0.18F;
    }

    private float scaleX(float value, float centerX, float scale) {
        return centerX + (value - centerX) * scale;
    }

    private float scaleY(float value, float centerY, float scale) {
        return centerY + (value - centerY) * scale;
    }

    private float scaleSize(float value, float scale) {
        return value * scale;
    }

    private float easeOutBack(float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress)) - 1.0F;
        float c = 1.70158F;
        return t * t * ((c + 1.0F) * t + c) + 1.0F;
    }

    private String diffText(float playerHealth, float targetHealth) {
        double diff = BigDecimal.valueOf(playerHealth - targetHealth).setScale(2, RoundingMode.FLOOR).doubleValue();
        if (diff > 0.0D) {
            return "+" + diff;
        }
        if (diff < 0.0D) {
            return String.valueOf(diff);
        }
        return "+0.0";
    }
}
