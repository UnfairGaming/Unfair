package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.enums.ChatColors;
import cn.unfair.event.EventTarget;
import cn.unfair.events.Render2DEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.stream.Collectors;

public class Tracers extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty colorMode = new ModeProperty("Color", 0, new String[]{"Default", "Teams", "Hud"});
    public final BooleanProperty drawLines = new BooleanProperty("Lines", true);
    public final BooleanProperty drawArrows = new BooleanProperty("Arrows", false);
    public final PercentProperty opacity = new PercentProperty("Opacity", 100);
    public final BooleanProperty showPlayers = new BooleanProperty("Players", true);
    public final BooleanProperty showFriends = new BooleanProperty("Friends", true);
    public final BooleanProperty showEnemies = new BooleanProperty("Enemies", true);
    public final BooleanProperty showBots = new BooleanProperty("Bots", false);

    public Tracers() {
        super("Tracers", false, true);
    }

    private boolean shouldRender(EntityPlayer entityPlayer) {
        if (entityPlayer.deathTime > 0) {
            return false;
        } else if (mc.getRenderViewEntity().getDistanceToEntity(entityPlayer) > 512.0F) {
            return false;
        } else if (entityPlayer != mc.thePlayer && entityPlayer != mc.getRenderViewEntity()) {
            if (TeamUtil.shouldBlockRenderTarget(entityPlayer)) {
                return false;
            } else if (!TeamUtil.isAntiBotEnabled() && TeamUtil.isBot(entityPlayer)) {
                return this.showBots.getValue();
            } else if (TeamUtil.isFriend(entityPlayer)) {
                return this.showFriends.getValue();
            } else {
                return TeamUtil.isTarget(entityPlayer) ? this.showEnemies.getValue() : this.showPlayers.getValue();
            }
        } else {
            return false;
        }
    }

    private Color getEntityColor(EntityPlayer entityPlayer, int alpha) {
        if (TeamUtil.isFriend(entityPlayer)) {
            Color color = Unfair.friendManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        } else if (TeamUtil.isTarget(entityPlayer)) {
            Color color = Unfair.targetManager.getColor();
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        } else {
            switch (this.colorMode.getValue()) {
                case 0:
                    return TeamUtil.getTeamColor(entityPlayer, RenderUtil.alphaToUnit(alpha));
                case 1:
                    int teamColor = TeamUtil.isSameTeam(entityPlayer) ? ChatColors.BLUE.toAwtColor() : ChatColors.RED.toAwtColor();
                    return new Color(RenderUtil.mergeAlpha(teamColor, alpha), true);
                case 2:
                    Unfair.moduleManager.modules.get(HUD.class);
                    int color = HUD.getColor(System.currentTimeMillis()).getRGB();
                    return new Color(RenderUtil.mergeAlpha(color, alpha), true);
                default:
                    return new Color(255, 255, 255, alpha);
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && this.drawLines.getValue()) {
            RenderUtil.enableRenderState();
            Vec3 position;
            if (mc.gameSettings.thirdPersonView == 0) {
                position = new Vec3(0.0, 0.0, 1.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationPitch,
                                                        mc.getRenderViewEntity().prevRotationPitch,
                                                        mc.timer.renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationYaw,
                                                        mc.getRenderViewEntity().prevRotationYaw,
                                                        mc.timer.renderPartialTicks
                                                )
                                        )
                                )
                        );
            } else {
                position = new Vec3(0.0, 0.0, 0.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, mc.timer.renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, mc.timer.renderPartialTicks)
                                        )
                                )
                        );
            }
            position = new Vec3(position.xCoord, position.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), position.zCoord);
            for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
                Color color = this.getEntityColor(player, RenderUtil.opacityToAlpha(this.opacity.getValue().floatValue()));
                double x = RenderUtil.lerpDouble(player.posX, player.lastTickPosX, event.partialTicks());
                double y = RenderUtil.lerpDouble(player.posY, player.lastTickPosY, event.partialTicks()) - (player.isSneaking() ? 0.125 : 0.0);
                double z = RenderUtil.lerpDouble(player.posZ, player.lastTickPosZ, event.partialTicks());
                RenderUtil.drawLine3D(
                        position,
                        x,
                        y + (double) player.getEyeHeight(),
                        z,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        this.opacity.getValue().floatValue(),
                        1.5F
                );
            }
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled() && this.drawArrows.getValue()) {
            for (EntityPlayer player : TeamUtil.getLoadedEntitiesSorted().stream().filter(entity -> entity instanceof EntityPlayer && this.shouldRender((EntityPlayer) entity)).map(EntityPlayer.class::cast).collect(Collectors.toList())) {
                float yawBetween = RotationUtil.getYawBetween(
                        RenderUtil.lerpDouble(mc.thePlayer.posX, mc.thePlayer.prevPosX, event.partialTicks()),
                        RenderUtil.lerpDouble(mc.thePlayer.posZ, mc.thePlayer.prevPosZ, event.partialTicks()),
                        RenderUtil.lerpDouble(player.posX, player.prevPosX, event.partialTicks()),
                        RenderUtil.lerpDouble(player.posZ, player.prevPosZ, event.partialTicks())
                );
                if (mc.gameSettings.thirdPersonView == 2) {
                    yawBetween += 180.0F;
                }
                float arrowDirX = (float) Math.sin(Math.toRadians(yawBetween));
                float arrowDirY = (float) Math.cos(Math.toRadians(yawBetween)) * -1.0F;
                int alpha = RenderUtil.opacityToAlpha(this.opacity.getValue().floatValue());
                yawBetween = Math.abs(MathHelper.wrapAngleTo180_float(yawBetween));
                if (yawBetween < 30.0F) {
                    alpha = 0;
                } else if (yawBetween < 60.0F) {
                    alpha = Math.round(alpha * (yawBetween - 30.0F) / 30.0F);
                }
                HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
                GlStateManager.pushMatrix();
                GlStateManager.scale(hud.scale.getValue(), hud.scale.getValue(), 0.0F);
                GlStateManager.translate(
                        (float) new ScaledResolution(mc).getScaledWidth() / 2.0F / hud.scale.getValue(),
                        (float) new ScaledResolution(mc).getScaledHeight() / 2.0F / hud.scale.getValue(),
                        0.0F
                );
                GlStateManager.pushMatrix();
                GlStateManager.translate(55.0F * arrowDirX + 1.0F, 55.0F * arrowDirY + 1.0F, -100.0F);
                RenderUtil.enableRenderState();
                RenderUtil.drawTriangle(
                        0.0F,
                        0.0F,
                        (float) (Math.atan2(arrowDirY, arrowDirX) + Math.PI),
                        10.0F,
                        this.getEntityColor(player, alpha).getRGB()
                );
                RenderUtil.disableRenderState();
                GlStateManager.popMatrix();
                GlStateManager.popMatrix();
            }
        }
    }
}
