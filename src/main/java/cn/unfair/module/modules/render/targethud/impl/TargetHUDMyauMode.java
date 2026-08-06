package cn.unfair.module.modules.render.targethud.impl;

import cn.unfair.Unfair;
import cn.unfair.enums.ChatColors;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.module.modules.render.targethud.TargetHUDMode;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.ColorUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TeamUtil;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class TargetHUDMyauMode extends TargetHUDMode {
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "HUD"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final PercentProperty background = new PercentProperty("background", 25);
    public final BooleanProperty head = new BooleanProperty("head", true);
    public final BooleanProperty indicator = new BooleanProperty("indicator", true);
    public final BooleanProperty outline = new BooleanProperty("outline", false);
    public final BooleanProperty animations = new BooleanProperty("animations", true);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true);

    public TargetHUDMyauMode() {
        super("Myau");
    }

    @Override
    public void render(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y) {
        float elapsedTime = (float) Math.clamp(targetHUD.animTimer.getElapsedTime(), 0L, 150L);
        float lerpedHealthRatio = Math.clamp(RenderUtil.lerpFloat(targetHUD.newHealth, targetHUD.oldHealth, elapsedTime / 150.0F) / targetHUD.maxHealth, 0.0F, 1.0F);
        Color targetColor = this.getTargetColor(data.entity());
        Color healthBarColor = this.color.getValue() == 0 ? ColorUtil.getHealthBlend(lerpedHealthRatio) : targetColor;
        float healthDeltaRatio = Math.clamp((data.playerHealth() - data.targetHealth() + 1.0F) / 2.0F, 0.0F, 1.0F);
        Color healthDeltaColor = ColorUtil.getHealthBlend(healthDeltaRatio);
        UnfairText text = this.buildText(targetHUD, data);
        float headIconOffset = this.getHeadIconOffset(targetHUD);
        float barTotalWidth = this.getBarWidth(targetHUD, text, headIconOffset);
        float posX = x / this.scale.getValue();
        float posY = y / this.scale.getValue();

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
        GlStateManager.translate(posX, posY, -450.0F);
        RenderUtil.enableRenderState();
        int backgroundColor = new Color(0.0F, 0.0F, 0.0F, this.background.getValue() / 100.0F).getRGB();
        int outlineColor = this.outline.getValue() ? targetColor.getRGB() : new Color(0, 0, 0, 0).getRGB();
        RenderUtil.drawOutlineRect(0.0F, 0.0F, barTotalWidth, 27.0F, 1.5F, backgroundColor, outlineColor);
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, barTotalWidth - 2.0F, 25.0F, ColorUtil.darker(healthBarColor, 0.2F).getRGB());
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, headIconOffset + 2.0F + lerpedHealthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F), 25.0F, healthBarColor.getRGB());
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        TargetHUD.mc.fontRendererObj.drawString(text.targetName, headIconOffset + 2.0F, 2.0F, -1, this.shadow.getValue());
        TargetHUD.mc.fontRendererObj.drawString(text.health, headIconOffset + 2.0F, 12.0F, -1, this.shadow.getValue());
        if (this.indicator.getValue()) {
            TargetHUD.mc.fontRendererObj.drawString(text.status, barTotalWidth - 2.0F - text.statusWidth, 2.0F, healthDeltaColor.getRGB(), this.shadow.getValue());
            TargetHUD.mc.fontRendererObj.drawString(text.healthDiff, barTotalWidth - 2.0F - text.healthDiffWidth, 12.0F, ColorUtil.darker(healthDeltaColor, 0.8F).getRGB(), this.shadow.getValue());
        }
        if (this.head.getValue() && targetHUD.headTexture != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            TargetHUD.mc.getTextureManager().bindTexture(targetHUD.headTexture);
            Gui.drawScaledCustomSizeModalRect(2, 2, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(2, 2, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
        }
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    @Override
    public float[] getSize(TargetHUD targetHUD, TargetHUD.RenderData data) {
        if (data == null) {
            return new float[]{120.0F, 36.0F};
        }
        UnfairText text = this.buildText(targetHUD, data);
        float headIconOffset = this.getHeadIconOffset(targetHUD);
        return new float[]{this.getBarWidth(targetHUD, text, headIconOffset) * this.scale.getValue(), 27.0F * this.scale.getValue()};
    }

    private float getHeadIconOffset(TargetHUD targetHUD) {
        return this.head.getValue() && targetHUD.headTexture != null ? 25.0F : 0.0F;
    }

    private float getBarWidth(TargetHUD targetHUD, UnfairText text, float headIconOffset) {
        float barContentWidth = Math.max(
                text.targetNameWidth + (this.indicator.getValue() ? 2.0F + text.statusWidth + 2.0F : 0.0F),
                text.healthWidth + (this.indicator.getValue() ? 2.0F + text.healthDiffWidth + 2.0F : 0.0F)
        );
        return Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);
    }

    private UnfairText buildText(TargetHUD targetHUD, TargetHUD.RenderData data) {
        String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(data.entity())));
        String healthText = ChatColors.formatColor(
                String.format("&r&f%s%sHP&r", TargetHUD.HEALTH_FORMAT.format(data.targetHealth()), data.absorption() > 0.0F ? "&6" : "&c")
        );
        String statusText = ChatColors.formatColor(String.format("&r&l%s&r", data.targetHealth() == data.playerHealth() ? "D" : (data.targetHealth() < data.playerHealth() ? "W" : "L")));
        String healthDiffText = ChatColors.formatColor(
                String.format("&r%s&r", data.targetHealth() == data.playerHealth() ? "0.0" : TargetHUD.DIFF_FORMAT.format(data.playerHealth() - data.targetHealth()))
        );
        return new UnfairText(targetNameText, healthText, statusText, healthDiffText);
    }

    @Override
    public boolean shouldAnimateHealth() {
        return this.animations.getValue();
    }

    private Color getTargetColor(net.minecraft.entity.EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof net.minecraft.entity.player.EntityPlayer player) {
            if (TeamUtil.isFriend(player)) {
                return Unfair.friendManager.getColor();
            }
            if (TeamUtil.isTarget(player)) {
                return Unfair.targetManager.getColor();
            }
        }
        switch (this.color.getValue()) {
            case 0:
                if (!(entityLivingBase instanceof net.minecraft.entity.player.EntityPlayer)) {
                    return new Color(-1);
                }
                return TeamUtil.getTeamColor((net.minecraft.entity.player.EntityPlayer) entityLivingBase, 1.0F);
            case 1:
                Unfair.moduleManager.modules.get(HUD.class);
                int rgb = HUD.getColor(System.currentTimeMillis()).getRGB();
                return new Color(rgb);
            default:
                return new Color(-1);
        }
    }

    private static class UnfairText {
        private final String targetName;
        private final String health;
        private final String status;
        private final String healthDiff;
        private final int targetNameWidth;
        private final int healthWidth;
        private final int statusWidth;
        private final int healthDiffWidth;

        private UnfairText(String targetName, String health, String status, String healthDiff) {
            this.targetName = targetName;
            this.health = health;
            this.status = status;
            this.healthDiff = healthDiff;
            this.targetNameWidth = TargetHUD.mc.fontRendererObj.getStringWidth(targetName);
            this.healthWidth = TargetHUD.mc.fontRendererObj.getStringWidth(health);
            this.statusWidth = TargetHUD.mc.fontRendererObj.getStringWidth(status);
            this.healthDiffWidth = TargetHUD.mc.fontRendererObj.getStringWidth(healthDiff);
        }
    }
}
