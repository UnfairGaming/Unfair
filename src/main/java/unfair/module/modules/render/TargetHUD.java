package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.enums.ChatColors;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.PacketEvent;
import unfair.events.Render2DEvent;
import unfair.module.Module;
import unfair.module.modules.combat.KillAura;
import unfair.property.properties.*;
import unfair.util.ColorUtil;
import unfair.util.RenderUtil;
import unfair.util.TeamUtil;
import unfair.util.TimerUtil;
import unfair.util.shader.BlurUtils;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class TargetHUD extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat healthFormat = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.US));
    private static final DecimalFormat diffFormat = new DecimalFormat("+0.0;-0.0", new DecimalFormatSymbols(Locale.US));
    public final ModeProperty style = new ModeProperty("style", 0, new String[]{"UNFAIR", "RAVENBS-MODERN", "RAVENBS-LEGACY"});
    public final ModeProperty color = new ModeProperty("color", 0, new String[]{"DEFAULT", "HUD"});
    public final ModeProperty posX = new ModeProperty("position-x", 1, new String[]{"LEFT", "MIDDLE", "RIGHT"});
    public final ModeProperty posY = new ModeProperty("position-y", 1, new String[]{"TOP", "MIDDLE", "BOTTOM"});
    public final FloatProperty scale = new FloatProperty("scale", 1.0F, 0.5F, 1.5F);
    public final IntProperty offX = new IntProperty("offset-x", 0, -255, 255);
    public final IntProperty offY = new IntProperty("offset-y", 40, -255, 255);
    public final PercentProperty background = new PercentProperty("background", 25, () -> this.style.getValue() == 0);
    public final BooleanProperty head = new BooleanProperty("head", true, () -> this.style.getValue() == 0);
    public final BooleanProperty indicator = new BooleanProperty("indicator", true, () -> this.style.getValue() == 0);
    public final BooleanProperty outline = new BooleanProperty("outline", false, () -> this.style.getValue() == 0 || this.style.getValue() == 1);
    public final BooleanProperty animations = new BooleanProperty("animations", true, () -> this.style.getValue() == 0);
    public final BooleanProperty shadow = new BooleanProperty("shadow", true, () -> this.style.getValue() == 0);
    public final BooleanProperty kaOnly = new BooleanProperty("ka-only", true);
    public final BooleanProperty chatPreview = new BooleanProperty("chat-preview", false);
    private final TimerUtil lastAttackTimer = new TimerUtil();
    private final TimerUtil animTimer = new TimerUtil();
    private EntityLivingBase lastTarget = null;
    private EntityLivingBase target = null;
    private ResourceLocation headTexture = null;
    private float oldHealth = 0.0F;
    private float newHealth = 0.0F;
    private float maxHealth = 0.0F;
    private float lastHealthBar = 0.0F;
    private TimerUtil fadeTimer = null;
    private boolean fadingIn = false;
    private EntityLivingBase fadingEntity = null;

    public TargetHUD() {
        super("TargetHUD", false, true);
    }

    private EntityLivingBase resolveTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed() && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        } else if (!(Boolean) this.kaOnly.getValue()
                && !this.lastAttackTimer.hasTimeElapsed(1500L)
                && TeamUtil.isEntityLoaded(this.lastTarget)) {
            return this.lastTarget;
        } else {
            return this.chatPreview.getValue() && mc.currentScreen instanceof GuiChat ? mc.thePlayer : null;
        }
    }

    private ResourceLocation getSkin(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            NetworkPlayerInfo playerInfo = mc.getNetHandler().getPlayerInfo(entityLivingBase.getName());
            if (playerInfo != null) {
                return playerInfo.getLocationSkin();
            }
        }
        return null;
    }

    private Color getTargetColor(EntityLivingBase entityLivingBase) {
        if (entityLivingBase instanceof EntityPlayer) {
            if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                return Unfair.friendManager.getColor();
            }
            if (TeamUtil.isTarget((EntityPlayer) entityLivingBase)) {
                return Unfair.targetManager.getColor();
            }
        }
        switch (this.color.getValue()) {
            case 0:
                if (!(entityLivingBase instanceof EntityPlayer)) {
                    return new Color(-1);
                }
                return TeamUtil.getTeamColor((EntityPlayer) entityLivingBase, 1.0F);
            case 1:
                int rgb = ((HUD) Unfair.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis()).getRGB();
                return new Color(rgb);
            default:
                return new Color(-1);
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && mc.thePlayer != null) {
            EntityLivingBase entityLivingBase = this.target;
            this.target = this.resolveTarget();

            // Handle fade in/out logic
            if (this.target != null) {
                // Target exists
                if (entityLivingBase == null && fadeTimer == null) {
                    // Target just appeared - start fade in
                    fadeTimer = new TimerUtil();
                    fadeTimer.reset();
                    fadingIn = true;
                } else if (fadingIn && fadeTimer != null && fadeTimer.getElapsedTime() >= 400) {
                    // Fade in complete
                    fadeTimer = null;
                    fadingIn = false;
                }
            } else {
                // Target is null - check if we should fade out
                if (entityLivingBase != null && fadeTimer == null) {
                    fadeTimer = new TimerUtil();
                    fadeTimer.reset();
                    fadingIn = false;
                    fadingEntity = entityLivingBase; // Store the entity for fade out
                }
            }

            // Render if we have a target or we're fading out
            if (entityLivingBase != null || fadeTimer != null) {
                // Use this.target if available, otherwise use fadingEntity for fade out
                EntityLivingBase entity = this.target != null ? this.target : fadingEntity;
                float health = (mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount()) / 2.0F;
                float abs = entity.getAbsorptionAmount() / 2.0F;
                float heal = entity.getHealth() / 2.0F + abs;

                if (entity != this.target) {
                    this.headTexture = null;
                    this.animTimer.setTime();
                    this.oldHealth = heal;
                    this.newHealth = heal;
                }
                if (!this.animations.getValue() || this.animTimer.hasTimeElapsed(150L)) {
                    this.oldHealth = this.newHealth;
                    this.newHealth = heal;
                    this.maxHealth = entity.getMaxHealth() / 2.0F;
                    if (this.oldHealth != this.newHealth) {
                        this.animTimer.reset();
                    }
                }
                ResourceLocation resourceLocation = this.getSkin(entity);
                if (resourceLocation != null) {
                    this.headTexture = resourceLocation;
                }

                int styleMode = this.style.getValue();
                if (styleMode == 0) {
                    drawUnfairStyle(health, abs, heal);
                } else {
                    drawRavenBSStyle(styleMode - 1, entity, health, abs, heal);
                }
            }
        }
    }

    private void drawUnfairStyle(float health, float abs, float heal) {
        float elapsedTime = (float) Math.min(Math.max(this.animTimer.getElapsedTime(), 0L), 150L);
        float lerpedHealthRatio = Math.min(Math.max(RenderUtil.lerpFloat(this.newHealth, this.oldHealth, elapsedTime / 150.0F) / this.maxHealth, 0.0F), 1.0F);
        Color targetColor = this.getTargetColor(this.target);
        Color healthBarColor = this.color.getValue() == 0 ? ColorUtil.getHealthBlend(lerpedHealthRatio) : targetColor;
        float healthDeltaRatio = Math.min(Math.max((health - heal + 1.0F) / 2.0F, 0.0F), 1.0F);
        Color healthDeltaColor = ColorUtil.getHealthBlend(healthDeltaRatio);
        ScaledResolution scaledResolution = new ScaledResolution(mc);
        String targetNameText = ChatColors.formatColor(String.format("&r%s&r", TeamUtil.stripName(this.target)));
        int targetNameWidth = mc.fontRendererObj.getStringWidth(targetNameText);
        String healthText = ChatColors.formatColor(
                String.format("&r&f%s%s❤&r", healthFormat.format(heal), abs > 0.0F ? "&6" : "&c")
        );
        int healthTextWidth = mc.fontRendererObj.getStringWidth(healthText);
        String statusText = ChatColors.formatColor(String.format("&r&l%s&r", heal == health ? "D" : (heal < health ? "W" : "L")));
        int statusTextWidth = mc.fontRendererObj.getStringWidth(statusText);
        String healthDiffText = ChatColors.formatColor(
                String.format("&r%s&r", heal == health ? "0.0" : diffFormat.format(health - heal))
        );
        int healthDiffWidth = mc.fontRendererObj.getStringWidth(healthDiffText);
        float barContentWidth = Math.max(
                (float) targetNameWidth + (this.indicator.getValue() ? 2.0F + (float) statusTextWidth + 2.0F : 0.0F),
                (float) healthTextWidth + (this.indicator.getValue() ? 2.0F + (float) healthDiffWidth + 2.0F : 0.0F)
        );
        float headIconOffset = this.head.getValue() && this.headTexture != null ? 25.0F : 0.0F;
        float barTotalWidth = Math.max(headIconOffset + 70.0F, headIconOffset + 2.0F + barContentWidth + 2.0F);
        float posX = this.offX.getValue().floatValue() / this.scale.getValue();
        switch (this.posX.getValue()) {
            case 1:
                posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() / 2.0F - barTotalWidth / 2.0F;
                break;
            case 2:
                posX *= -1.0F;
                posX += (float) scaledResolution.getScaledWidth() / this.scale.getValue() - barTotalWidth;
        }
        float posY = this.offY.getValue().floatValue() / this.scale.getValue();
        switch (this.posY.getValue()) {
            case 1:
                posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() / 2.0F - 13.5F;
                break;
            case 2:
                posY *= -1.0F;
                posY += (float) scaledResolution.getScaledHeight() / this.scale.getValue() - 27.0F;
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale.getValue(), this.scale.getValue(), 0.0F);
        GlStateManager.translate(posX, posY, -450.0F);
        RenderUtil.enableRenderState();
        int backgroundColor = new Color(0.0F, 0.0F, 0.0F, (float) this.background.getValue() / 100.0F).getRGB();
        int outlineColor = this.outline.getValue() ? targetColor.getRGB() : new Color(0, 0, 0, 0).getRGB();
        RenderUtil.drawOutlineRect(0.0F, 0.0F, barTotalWidth, 27.0F, 1.5F, backgroundColor, outlineColor);
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, barTotalWidth - 2.0F, 25.0F, ColorUtil.darker(healthBarColor, 0.2F).getRGB());
        RenderUtil.drawRect(headIconOffset + 2.0F, 22.0F, headIconOffset + 2.0F + lerpedHealthRatio * (barTotalWidth - 2.0F - headIconOffset - 2.0F), 25.0F, healthBarColor.getRGB());
        RenderUtil.disableRenderState();
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        mc.fontRendererObj.drawString(targetNameText, headIconOffset + 2.0F, 2.0F, -1, this.shadow.getValue());
        mc.fontRendererObj.drawString(healthText, headIconOffset + 2.0F, 12.0F, -1, this.shadow.getValue());
        if (this.indicator.getValue()) {
            mc.fontRendererObj.drawString(statusText, barTotalWidth - 2.0F - (float) statusTextWidth, 2.0F, healthDeltaColor.getRGB(), this.shadow.getValue());
            mc.fontRendererObj.drawString(healthDiffText, barTotalWidth - 2.0F - (float) healthDiffWidth, 12.0F, ColorUtil.darker(healthDeltaColor, 0.8F).getRGB(), this.shadow.getValue());
        }
        if (this.head.getValue() && this.headTexture != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(this.headTexture);
            Gui.drawScaledCustomSizeModalRect(2, 2, 8.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            Gui.drawScaledCustomSizeModalRect(2, 2, 40.0F, 8.0F, 8, 8, 23, 23, 64.0F, 64.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F);
        }
        GlStateManager.disableBlend();
        GlStateManager.enableDepth();
        GlStateManager.popMatrix();
    }

    private void drawRavenBSStyle(int mode, EntityLivingBase entity, float health, float abs, float heal) {
        String playerInfo = entity.getDisplayName().getFormattedText();
        double healthRatio = entity.getHealth() / entity.getMaxHealth();
        if (entity.isDead) {
            healthRatio = 0;
        }
        String healthStr = String.format("%.1f", heal);
        playerInfo += " §c" + healthStr;

        if (this.indicator.getValue()) {
            playerInfo += " " + ((healthRatio <= health / mc.thePlayer.getMaxHealth()) ? "§aW" : "§cL");
        }

        int alpha = 255;
        if (fadeTimer != null) {
            long elapsed = fadeTimer.getElapsedTime();
            if (elapsed < 400) {
                if (fadingIn) {
                    // Fade in: 0 -> 255
                    alpha = (int) ((elapsed / 400.0f) * 255);
                } else {
                    // Fade out: 255 -> 0
                    alpha = (int) (255 - (elapsed / 400.0f) * 255);
                }
            } else {
                alpha = fadingIn ? 255 : 0;
                if (!fadingIn) {
                    this.target = null;
                    fadeTimer = null;
                    fadingEntity = null;
                    return;
                }
            }
        }

        final ScaledResolution scaledResolution = new ScaledResolution(mc);
        final int padding = 8;
        final int targetStrWithPadding = mc.fontRendererObj.getStringWidth(playerInfo) + padding;
        final int x = (scaledResolution.getScaledWidth() / 2 - targetStrWithPadding / 2) + offX.getValue();
        final int y = (scaledResolution.getScaledHeight() / 2 + 15) + offY.getValue();
        final int n6 = x - padding;
        final int n7 = y - padding;
        final int n8 = x + targetStrWithPadding;
        final int n9 = y + (mc.fontRendererObj.FONT_HEIGHT + 5) - 6 + padding;

        final int maxAlphaOutline = Math.min(alpha, 110);
        final int maxAlphaBackground = Math.min(alpha, 210);

        // Get gradient colors from HUD
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        int gradientLeft = hud.getColor(System.currentTimeMillis()).getRGB();
        int gradientRight = hud.getColor(System.currentTimeMillis() + 500).getRGB();
        int[] gradientColors = new int[]{gradientLeft, gradientRight};

        switch (mode) {
            case 0: // Modern - with blur like ClickGUI (exact RavenBS implementation)
                float bloomRadius = (fadeTimer == null) ? 2f : (2f * alpha / 255f);
                float blurRadius = (fadeTimer == null) ? 3 : (3f * alpha / 255f);
                BlurUtils.prepareBloom();
                RoundedUtils.drawRound((float) n6, (float) n7, (float) (n8 - n6), (float) (n9 + 13 - n7), 8.0f, true, new Color(0, 0, 0, maxAlphaBackground));
                BlurUtils.bloomEnd(3, bloomRadius);
                BlurUtils.prepareBlur();
                RoundedUtils.drawRound((float) n6, (float) n7, (float) (n8 - n6), (float) (n9 + 13 - n7), 8.0f, true, new Color(RenderUtil.mergeAlpha(Color.black.getRGB(), maxAlphaOutline)));
                BlurUtils.blurEnd(2, blurRadius);
                break;
            case 1: // Legacy - no blur
                RenderUtil.drawRoundedGradientOutlinedRectangle((float) n6, (float) n7, (float) n8, (float) (n9 + 13), 10.0f,
                        RenderUtil.mergeAlpha(Color.black.getRGB(), maxAlphaOutline),
                        RenderUtil.mergeAlpha(gradientColors[0], alpha),
                        RenderUtil.mergeAlpha(gradientColors[1], alpha));
                break;
        }

        final int n13 = n6 + 6;
        final int n14 = n8 - 6;
        final int n15 = n9;

        // Bar background
        RenderUtil.drawRoundedRectangle((float) n13, (float) n15, (float) n14, (float) (n15 + 5), 4.0f,
                RenderUtil.mergeAlpha(Color.black.getRGB(), maxAlphaOutline));

        int mergedGradientLeft = RenderUtil.mergeAlpha(gradientColors[0], maxAlphaBackground);
        int mergedGradientRight = RenderUtil.mergeAlpha(gradientColors[1], maxAlphaBackground);

        float healthBar = (float) (n14 + (n13 - n14) * (1 - healthRatio));

        // Smooth health bar animation
        if (lastHealthBar != healthBar && lastHealthBar - n13 >= 3) {
            float diff = lastHealthBar - healthBar;
            if (diff > 0) {
                lastHealthBar = lastHealthBar - diff * 0.1f;
            } else {
                lastHealthBar = lastHealthBar + (-diff) * 0.1f;
            }
        } else {
            lastHealthBar = healthBar;
        }

        if (lastHealthBar > n14) {
            lastHealthBar = n14;
        }

        switch (mode) {
            case 0: // Modern
                RenderUtil.drawRoundedRectangle((float) n13, (float) n15, lastHealthBar, (float) (n15 + 5), 4.0f,
                        RenderUtil.darkenColor(mergedGradientRight, 25));
                RenderUtil.drawRoundedGradientRect((float) n13, (float) n15, healthBar, (float) (n15 + 5), 4.0f,
                        mergedGradientLeft, mergedGradientLeft, mergedGradientRight, mergedGradientRight);
                break;
            case 1: // Legacy
                RenderUtil.drawRoundedGradientRect((float) n13, (float) n15, lastHealthBar, (float) (n15 + 5), 4.0f,
                        mergedGradientLeft, mergedGradientLeft, mergedGradientRight, mergedGradientRight);
                break;
        }

        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_BLEND);
        mc.fontRendererObj.drawString(playerInfo, (float) x, (float) y,
                (new Color(220, 220, 220, 255).getRGB() & 0xFFFFFF) | Math.min(alpha + 15, 255) << 24, true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() != Action.ATTACK) {
                return;
            }
            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase) {
                if (entity instanceof EntityArmorStand) {
                    return;
                }
                this.lastAttackTimer.reset();
                this.lastTarget = (EntityLivingBase) entity;
            }
        }
    }
}
