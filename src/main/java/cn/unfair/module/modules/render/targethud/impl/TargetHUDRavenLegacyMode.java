package cn.unfair.module.modules.render.targethud.impl;

import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.module.modules.render.targethud.TargetHUDMode;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.RenderUtil;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class TargetHUDRavenLegacyMode extends TargetHUDMode {
    public final BooleanProperty indicator = new BooleanProperty("Indicator", true);

    public TargetHUDRavenLegacyMode() {
        super("RavenLegacy");
    }

    @Override
    public void render(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y) {
        String playerInfo = targetHUD.buildModernPlayerInfo(data.entity(), data.targetHealth(), data.playerHealth(), this.indicator.getValue());
        TargetHUD.TargetHudBounds bounds = targetHUD.getModernBounds(playerInfo, x, y);
        int alpha = targetHUD.getFadeAlpha();
        if (alpha <= 0) {
            return;
        }

        int[] gradientColors = targetHUD.getRavenGradientColors();
        int gradientLeft = gradientColors[0];
        int gradientRight = gradientColors[1];
        int trackAlpha = Math.min(alpha, 110);
        int barAlpha = Math.min(alpha, 210);
        double healthRatio = data.entity().isDead
                ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, TargetHUD.finiteHealth(data.targetHealth()) / Math.max(TargetHUD.finiteHealth(data.maxHealth()), 1.0F)));

        RenderUtil.drawRoundedGradientOutlinedRectangle(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), 10.0F,
                RenderUtil.mergeAlpha(Color.black.getRGB(), trackAlpha),
                RenderUtil.mergeAlpha(gradientLeft, alpha),
                RenderUtil.mergeAlpha(gradientRight, alpha));

        int barLeft = bounds.left() + 6;
        int barRight = bounds.right() - 6;
        int barTop = bounds.contentBottom();
        RenderUtil.drawRoundedRectangle(barLeft, barTop, barRight, barTop + 5.0F, 2.0F,
                RenderUtil.mergeAlpha(Color.black.getRGB(), trackAlpha));

        int mergedGradientLeft = RenderUtil.mergeAlpha(gradientLeft, barAlpha);
        int mergedGradientRight = RenderUtil.mergeAlpha(gradientRight, barAlpha);
        float healthBar = (float) (barRight + (barLeft - barRight) * (1.0D - healthRatio));
        float lastHealthBar = targetHUD.updateRavenHealthBar(healthBar, barLeft, barRight);
        RenderUtil.drawRoundedGradientRect(barLeft, barTop, lastHealthBar, barTop + 5.0F, 2.0F,
                mergedGradientLeft, mergedGradientLeft, mergedGradientRight, mergedGradientRight);
        this.renderText(playerInfo, bounds, alpha);
    }

    @Override
    public float[] getSize(TargetHUD targetHUD, TargetHUD.RenderData data) {
        if (data == null) {
            return new float[]{120.0F, 36.0F};
        }
        String playerInfo = targetHUD.buildModernPlayerInfo(data.entity(), data.targetHealth(), data.playerHealth(), this.indicator.getValue());
        TargetHUD.TargetHudBounds bounds = targetHUD.getModernBounds(playerInfo, 0.0F, 0.0F);
        return new float[]{bounds.width(), bounds.height()};
    }

    private void renderText(String playerInfo, TargetHUD.TargetHudBounds bounds, int alpha) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        TargetHUD.mc.fontRendererObj.drawString(playerInfo, bounds.textX(), bounds.textY(),
                (new Color(220, 220, 220, 255).getRGB() & 0xFFFFFF) | Math.min(alpha + 15, 255) << 24, true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }
}
