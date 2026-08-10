package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.module.Module;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import java.awt.*;

import static cn.unfair.util.MathUtil.interpolate;

public class TargetESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long HURT_DURATION = 500;
    private final ModeProperty colorMode = new ModeProperty("Color Mode", 0, new String[]{"HUD", "Custom", "Fade"});
    private final ColorProperty customColor = new ColorProperty("Custom Color", Color.WHITE.getRGB(), () -> colorMode.getValue() == 1);
    private final ColorProperty fadeColor1 = new ColorProperty("Fade Color 1", Color.WHITE.getRGB(), () -> colorMode.getValue() == 2);
    private final ColorProperty fadeColor2 = new ColorProperty("Fade Color 2", Color.WHITE.getRGB(), () -> colorMode.getValue() == 2);
    private final ModeProperty mode = new ModeProperty("Mark Mode", 1, new String[]{"Points", "Ghost", "Ghost2", "Image", "Exhi", "Circle"});
    private final ModeProperty imageMode = new ModeProperty("Image Mode", 0, new String[]{"Rectangle", "QuadStapple", "TriangleStapple", "TriangleStipple", "Aim"}, () -> mode.getValue() == 3);
    private final BooleanProperty animation = new BooleanProperty("Animation", true, () -> mode.getValue() == 3 && imageMode.getValue() == 5);
    private final BooleanProperty showHurt = new BooleanProperty("ShowHurt", false, () -> mode.getValue() == 3);
    private final TimerUtil displayTimer = new TimerUtil();
    private final ResourceLocation glowCircle = new ResourceLocation("minecraft", "unfair/targetesp/glow_circle.png");
    private final ResourceLocation rectangle = new ResourceLocation("minecraft", "unfair/targetesp/rectangle.png");
    private final ResourceLocation quadstapple = new ResourceLocation("minecraft", "unfair/targetesp/quadstapple.png");
    private final ResourceLocation trianglestapple = new ResourceLocation("minecraft", "unfair/targetesp/trianglestapple.png");
    private final ResourceLocation trianglestipple = new ResourceLocation("minecraft", "unfair/targetesp/trianglestipple.png");
    private final ResourceLocation aim = new ResourceLocation("minecraft", "unfair/targetesp/shenmi.png");
    public double prevCircleStep;
    public double circleStep;
    private long lastHurtTime = 0;
    private EntityLivingBase target;
    private long lastTime = System.currentTimeMillis();
    private long alphaStartTime = 0L;
    private boolean hasFullyFadedIn = false;

    public TargetESP() {
        super("TargetESP", false, true);
    }

    private Color getTargetColor() {
        return switch (colorMode.getValue()) {
            case 1 -> new Color(customColor.getValue());
            case 2 -> {
                float phase = (System.currentTimeMillis() % 2000L) / 1000.0F;
                float progress = phase <= 1.0F ? phase : 2.0F - phase;
                yield ColorUtil.interpolate(progress, new Color(fadeColor1.getValue()), new Color(fadeColor2.getValue()));
            }
            default -> HUD.getColor(System.currentTimeMillis());
        };
    }

    @Override
    public void onEnabled() {
        target = null;
        alphaStartTime = AnimationUtil.start();
        displayTimer.reset();
        lastTime = System.currentTimeMillis();
        hasFullyFadedIn = false;
        prevCircleStep = 0;
        circleStep = 0;
    }

    @Override
    public void onDisabled() {
        target = null;
        alphaStartTime = 0L;
    }

    private void setTarget(EntityLivingBase newTarget) {
        if (newTarget == null || newTarget == mc.thePlayer || !TeamUtil.isEntityLoaded(newTarget)) {
            return;
        }

        if (target != newTarget) {
            target = newTarget;
            lastTime = System.currentTimeMillis();
            alphaStartTime = AnimationUtil.start();
            hasFullyFadedIn = false;
        }
        displayTimer.reset();
    }

    private EntityLivingBase getKillAuraTarget() {
        if (Unfair.moduleManager == null) {
            return null;
        }

        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null
                && killAura.isEnabled()
                && killAura.isAttackAllowed()
                && TeamUtil.isEntityLoaded(killAura.getTarget())) {
            return killAura.getTarget();
        }
        return null;
    }

    private EntityLivingBase getMouseOverTarget() {
        if (mc.objectMouseOver == null
                || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || !(mc.objectMouseOver.entityHit instanceof EntityLivingBase entity)) {
            return null;
        }
        return entity;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity packet) {
            if (packet.getAction() != C02PacketUseEntity.Action.ATTACK) {
                return;
            }

            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase newTarget) {
                setTarget(newTarget);
                lastHurtTime = System.currentTimeMillis();
                return;
            }

            EntityLivingBase fallbackTarget = getKillAuraTarget();
            if (fallbackTarget == null) {
                fallbackTarget = getMouseOverTarget();
            }
            if (fallbackTarget != null) {
                setTarget(fallbackTarget);
                lastHurtTime = System.currentTimeMillis();
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        EntityLivingBase killAuraTarget = getKillAuraTarget();
        if (killAuraTarget != null) {
            setTarget(killAuraTarget);
            return;
        }

        if (target != null && (!TeamUtil.isEntityLoaded(target) || displayTimer.hasTimeElapsed(1000))) {
            hasFullyFadedIn = false;
            target = null;
        }
    }

    private float getHurtAlpha() {
        if (!showHurt.getValue()) return 0.0f;

        long timeSinceHurt = System.currentTimeMillis() - lastHurtTime;
        if (timeSinceHurt > HURT_DURATION) return 0.0f;

        float progress = (float) timeSinceHurt / HURT_DURATION;
        if (progress < 0.5f) {
            return progress * 2.0f;
        } else {
            return 2.0f - (progress * 2.0f);
        }
    }

    private float getAlpha() {
        if (target == null) return 0.0f;

        long displayElapsed = displayTimer.getElapsedTime();

        if (!hasFullyFadedIn) {
            if (!AnimationUtil.finished(alphaStartTime, 200.0F)) {
                return AnimationUtil.progress(alphaStartTime, 200.0F, mc.timer.renderPartialTicks, 0);
            } else {
                hasFullyFadedIn = true;
                return 1.0f;
            }
        } else {
            if (displayElapsed > 800) {
                return 1.0F - AnimationUtil.progress(System.currentTimeMillis() - (displayElapsed - 800L), 200.0F, mc.timer.renderPartialTicks, 0);
            } else {
                return 1.0f;
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) return;
        if (target != null) {
            if (mode.getValue() == 0)
                points(event);

            if (mode.getValue() == 1) {
                GlStateManager.pushMatrix();
                GlStateManager.disableLighting();
                GlStateManager.depthMask(false);
                GlStateManager.enableBlend();
                GlStateManager.shadeModel(7425);
                GlStateManager.disableCull();
                GlStateManager.disableAlpha();
                GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);
                double radius = 0.67;
                float speed = 45;
                float size = 0.4f;
                double distance = 19;
                int lenght = 20;

                Vec3 interpolated = interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.partialTicks());
                interpolated = new Vec3(interpolated.xCoord, interpolated.yCoord + 0.75f, interpolated.zCoord);

                RenderUtil.setupOrientationMatrix(interpolated.xCoord, interpolated.yCoord + 0.5f, interpolated.zCoord);

                float[] idk = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};

                GL11.glRotated(-idk[0], 0.0, 1.0, 0.0);
                GL11.glRotated(idk[1], 1.0, 0.0, 0.0);

                for (int i = 0; i < lenght; i++) {
                    double angle = 0.15f * (System.currentTimeMillis() - lastTime - (i * distance)) / (speed);
                    double s = Math.sin(angle) * radius;
                    double c = Math.cos(angle) * radius;
                    GlStateManager.translate(s, (c), -c);
                    GlStateManager.translate(-size / 2f, -size / 2f, 0);
                    GlStateManager.translate(size / 2f, size / 2f, 0);
                    int color = ColorUtil.applyOpacity(getTargetColor(), getAlpha()).getRGB();
                    RenderUtil.drawImage(glowCircle, 0f, 0f, -size, -size, color);
                    GlStateManager.translate(-size / 2f, -size / 2f, 0);
                    GlStateManager.translate(size / 2f, size / 2f, 0);
                    GlStateManager.translate(-(s), -(c), (c));
                }
                for (int i = 0; i < lenght; i++) {
                    double angle = 0.15f * (System.currentTimeMillis() - lastTime - (i * distance)) / (speed);
                    double s = Math.sin(angle) * radius;
                    double c = Math.cos(angle) * radius;
                    GlStateManager.translate(-s, s, -c);
                    GlStateManager.translate(-size / 2f, -size / 2f, 0);
                    GlStateManager.translate(size / 2f, size / 2f, 0);
                    int color = ColorUtil.applyOpacity(getTargetColor(), getAlpha()).getRGB();
                    RenderUtil.drawImage(glowCircle, 0f, 0f, -size, -size, color);
                    GlStateManager.translate(-size / 2f, -size / 2f, 0);
                    GlStateManager.translate(size / 2f, size / 2f, 0);
                    GlStateManager.translate((s), -(s), (c));
                }
                for (int i = 0; i < lenght; i++) {
                    double angle = 0.15f * (System.currentTimeMillis() - lastTime - (i * distance)) / (speed);
                    double s = Math.sin(angle) * radius;
                    double c = Math.cos(angle) * radius;
                    GlStateManager.translate(-(s), -(s), (c));
                    GlStateManager.translate(-size / 2f, -size / 2f, 0);
                    GlStateManager.translate(size / 2f, size / 2f, 0);
                    int color = ColorUtil.applyOpacity(getTargetColor(), getAlpha()).getRGB();
                    RenderUtil.drawImage(glowCircle, 0f, 0f, -size, -size, color);
                    GlStateManager.translate(-size / 2f, -size / 2f, 0);
                    GlStateManager.translate(size / 2f, size / 2f, 0);
                    GlStateManager.translate((s), (s), -(c));
                }
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.disableBlend();
                GlStateManager.enableCull();
                GlStateManager.enableAlpha();
                GlStateManager.depthMask(true);
                GlStateManager.popMatrix();
            }

            if (mode.getValue() == 2) {
                ghost2(event);
            }

            if (mode.getValue() == 4) {
                float alpha = getAlpha();
                int baseAlpha = (int) (75 * alpha);
                int color = this.target.hurtTime > 3 ? new Color(200, 255, 100, baseAlpha).getRGB() : this.target.hurtTime < 3 ? new Color(235, 40, 40, baseAlpha).getRGB() : new Color(255, 255, 255, baseAlpha).getRGB();
                GlStateManager.pushMatrix();
                GL11.glShadeModel(7425);
                GL11.glHint(3154, 4354);
                mc.entityRenderer.setupCameraTransform(event.partialTicks(), 2);
                double x = target.prevPosX + (target.posX - target.prevPosX) * (double) event.partialTicks() - mc.getRenderManager().getRenderPosX();
                double y = target.prevPosY + (target.posY - target.prevPosY) * (double) event.partialTicks() - mc.getRenderManager().getRenderPosY();
                double z = target.prevPosZ + (target.posZ - target.prevPosZ) * (double) event.partialTicks() - mc.getRenderManager().getRenderPosZ();
                double xMoved = target.posX - target.prevPosX;
                double yMoved = target.posY - target.prevPosY;
                double zMoved = target.posZ - target.prevPosZ;
                double motionX = 0.0;
                double motionY = 0.0;
                double motionZ = 0.0;
                GlStateManager.translate(x + (xMoved + motionX + (mc.thePlayer.motionX + 0.005)), y + (yMoved + motionY + (mc.thePlayer.motionY - 0.002)), z + (zMoved + motionZ + (mc.thePlayer.motionZ + 0.005)));
                AxisAlignedBB axisAlignedBB = target.getEntityBoundingBox();
                RenderUtil.drawAxisAlignedBB(new AxisAlignedBB(axisAlignedBB.minX - 0.1 - target.posX, axisAlignedBB.minY - 0.1 - target.posY, axisAlignedBB.minZ - 0.1 - target.posZ, axisAlignedBB.maxX + 0.1 - target.posX, axisAlignedBB.maxY + 0.2 - target.posY, axisAlignedBB.maxZ + 0.1 - target.posZ), true, color);
                GlStateManager.popMatrix();
            }

            if (mode.getValue() == 5) {
                double renderPosX = mc.getRenderManager().getRenderPosX();
                double renderPosY = mc.getRenderManager().getRenderPosY();
                double renderPosZ = mc.getRenderManager().getRenderPosZ();
                Vec3 interpolated = interpolate(
                        new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ),
                        target.getPositionVector(),
                        event.partialTicks()
                );

                double height = target.height;
                long time = System.currentTimeMillis();
                double rawAngle = time / 300.0;
                double offset = (Math.sin(rawAngle) + 1) / 2.0 * height;

                double thicknessScale = 1.0 - Math.abs(Math.sin(rawAngle));
                double minScale = 0.15;
                thicknessScale = minScale + (1.0 - minScale) * thicknessScale;

                double x = interpolated.xCoord - renderPosX;
                double y = interpolated.yCoord + offset - renderPosY;
                double z = interpolated.zCoord - renderPosZ;

                GlStateManager.pushMatrix();
                GlStateManager.translate(x, y, z);

                GlStateManager.disableTexture2D();
                GlStateManager.enableBlend();
                GlStateManager.disableAlpha();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.shadeModel(GL11.GL_SMOOTH);
                GlStateManager.disableCull();

                float radius = 0.6f;
                double baseThickness = 0.5f;
                double thickness = baseThickness * thicknessScale;
                double halfThick = thickness / 2.0;
                double bottomY = -halfThick;

                Tessellator tessellator = Tessellator.getInstance();
                WorldRenderer worldrenderer = tessellator.getWorldRenderer();
                int slices = 60;

                for (int i = 0; i < slices; i++) {
                    double angle1 = Math.toRadians((i / (double) slices) * 360.0);
                    double angle2 = Math.toRadians(((i + 1) / (double) slices) * 360.0);

                    double x1 = Math.sin(angle1) * radius;
                    double z1 = Math.cos(angle1) * radius;
                    double x2 = Math.sin(angle2) * radius;
                    double z2 = Math.cos(angle2) * radius;

                    Color col1 = getTargetColor();
                    Color col2 = getTargetColor();
                    float r1 = col1.getRed() / 255f;
                    float g1 = col1.getGreen() / 255f;
                    float b1 = col1.getBlue() / 255f;
                    float r2 = col2.getRed() / 255f;
                    float g2 = col2.getGreen() / 255f;
                    float b2 = col2.getBlue() / 255f;

                    float alphaTop, alphaBottom;
                    if (Math.cos(rawAngle) > 0) {
                        alphaBottom = 0.05f;
                        alphaTop = 0.7f;
                    } else {
                        alphaBottom = 0.7f;
                        alphaTop = 0.05f;
                    }

                    worldrenderer.begin(GL11.GL_TRIANGLE_STRIP, DefaultVertexFormats.POSITION_COLOR);
                    worldrenderer.pos(x1, bottomY, z1).color(r1, g1, b1, alphaBottom).endVertex();
                    worldrenderer.pos(x1, halfThick, z1).color(r1, g1, b1, alphaTop).endVertex();
                    worldrenderer.pos(x2, bottomY, z2).color(r2, g2, b2, alphaBottom).endVertex();
                    worldrenderer.pos(x2, halfThick, z2).color(r2, g2, b2, alphaTop).endVertex();
                    tessellator.draw();
                }
                GlStateManager.shadeModel(GL11.GL_FLAT);
                GlStateManager.enableAlpha();
                GlStateManager.enableCull();
                GlStateManager.disableBlend();
                GlStateManager.enableTexture2D();
                GlStateManager.popMatrix();
            }
        }
    }

    private void ghost2(Render3DEvent event) {
        if (target == null) return;
        float partialTicks = event.partialTicks();
        Vec3 interpolated = interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), partialTicks);
        interpolated = new Vec3(interpolated.xCoord, interpolated.yCoord + 0.9f, interpolated.zCoord);
        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.disableCull();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 1, 0, 1);
        RenderUtil.setupOrientationMatrix(interpolated.xCoord, interpolated.yCoord, interpolated.zCoord);
        float[] view = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};
        GL11.glRotated(-view[0], 0.0, 1.0, 0.0);
        GL11.glRotated(view[1], 1.0, 0.0, 0.0);

        for (int gi = 0; gi < ((28) + 10 + 10); gi++) {
            if (gi < (28)) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos((((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) + ((((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.25)) * ((target.width * 0.56) + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.78 + (0.26 + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.38) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0)) + Math.cos((((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) + ((((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.25) + Math.PI / 2.0) * (Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.7 + ((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) * 0.06), ((0.44 + 0.08 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.2)) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) + 0.06 * (((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5) * ((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5) * (3.0 - 2.0 * ((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5))) - 0.5) * 2.0)) + (Math.sin(((((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) + ((((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.25)) * 2.0 + ((System.currentTimeMillis() - lastTime) / 1000.0) * 2.2) * 0.05) + Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.2 + ((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) * (0.45 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.05)) * 0.35) * 0.12, Math.sin((((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) + ((((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.25)) * ((target.width * 0.56) + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.78 + (0.26 + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.38) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0)) + Math.sin((((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) + ((((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.25) + Math.PI / 2.0) * (Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.7 + ((gi % 28) + (0.0)) / (double) 28 * Math.PI * 2.0) * 0.06));
                GlStateManager.rotate((float) (((System.currentTimeMillis() - lastTime) / 1000.0) * 180.0 + (gi % 28) * 12.0), 0, 0, 1);
                RenderUtil.drawImage(glowCircle, 0f, 0f, -(float) ((0.16 + 0.06 * (1.0 - ((gi % 28) + (0.0)) / (double) 28)) * (0.86 + 0.24 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.6 + (gi % 28) * 0.45) + 0.14 * ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), -(float) ((0.16 + 0.06 * (1.0 - ((gi % 28) + (0.0)) / (double) 28)) * (0.86 + 0.24 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.6 + (gi % 28) * 0.45) + 0.14 * ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), ColorUtil.applyOpacity(getTargetColor(), getAlpha()).getRGB());
                GlStateManager.popMatrix();
            } else if (gi < (28) + 10) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos(((gi - (28) + (0.0)) / (double) 10) * Math.PI * 2.0 - (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 0.95) * ((target.width * 0.86 + 0.20) + 0.10 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + gi - (28)) - (0.18 + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.25) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0)), (-(0.44 + 0.08 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.2)) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) - 0.06 * (((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5) * ((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5) * (3.0 - 2.0 * ((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5))) - 0.5) * 2.0) + Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + (gi - (28)) * 0.70) * 0.10), Math.sin(((gi - (28) + (0.0)) / (double) 10) * Math.PI * 2.0 - (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 0.95) * ((target.width * 0.86 + 0.20) + 0.10 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + gi - (28)) - (0.18 + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.25) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0)));
                GlStateManager.rotate((float) (((System.currentTimeMillis() - lastTime) / 1000.0) * 320.0 + (gi - (28)) * 30.0), 0, 0, 1);
                RenderUtil.drawImage(glowCircle, 0f, 0f, -(float) (0.17 + 0.06 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 3.0 + gi - (28))) + 0.05 * (1.0 - ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), -(float) (0.17 + 0.06 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 3.0 + gi - (28))) + 0.05 * (1.0 - ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), ColorUtil.applyOpacity(getTargetColor(), getAlpha()).getRGB());
                GlStateManager.popMatrix();
            } else {
                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos(((gi - (28) - 10 + (0.0)) / (double) 10) * Math.PI * 2.0 + (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.9) * ((target.width * 0.62 + 0.12) + 0.12 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.8 + gi - (28) - 10)), (Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.6 + gi - (28) - 10) * 0.16) + (Math.cos(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + (gi - (28) - 10) * 0.7) * 0.12), Math.sin(((gi - (28) - 10 + (0.0)) / (double) 10) * Math.PI * 2.0 + (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.9) * ((target.width * 0.62 + 0.12) + 0.12 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.8 + gi - (28) - 10)));
                GlStateManager.rotate((float) (((System.currentTimeMillis() - lastTime) / 1000.0) * 420.0 + (gi - (28) - 10) * 40.0), 0, 0, 1);
                RenderUtil.drawImage(glowCircle, 0f, 0f, -(float) (0.09 + 0.05 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 4.2 + (gi - (28) - 10) * 1.3))), -(float) (0.09 + 0.05 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 4.2 + (gi - (28) - 10) * 1.3))), ColorUtil.applyOpacity(getTargetColor(), (float) (getAlpha() * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 5.0 + (gi - (28) - 10) * 2.2))))).getRGB());
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        int index = 3;
        if (mode.getValue() == 3 && target != null) {
            mc.entityRenderer.setupCameraTransform(event.partialTicks(), 0);
            ProjectionUtil.Projection projection = ProjectionUtil.projectEntity(target);
            mc.entityRenderer.setupOverlayRendering();
            if (projection != null) {
                drawTargetESP2D(projection.centerX(), projection.centerY(),
                        getImageScale(projection), index);
            }
        }
    }

    @EventTarget
    public void onShader2D(Shader2DEvent event) {
        if (!this.isEnabled()) return;
        if (event.shaderType() == Shader2DEvent.ShaderType.GLOW) {
            int index = 3;
            if (mode.getValue() == 3 && imageMode.getValue() == 0 && target != null) {
                ProjectionUtil.Projection projection = ProjectionUtil.projectEntity(target);
                if (projection != null) {
                    drawTargetESP2D(projection.centerX(), projection.centerY(),
                            getImageScale(projection), index);
                }
            }
        }
    }

    private float getImageScale(ProjectionUtil.Projection projection) {
        return MathHelper.clamp_float(projection.height() / 180.0F, 0.25F, 1.2F);
    }

    private void points(Render3DEvent event) {
        if (target != null) {
            double markerX = MathUtil.interporate(event.partialTicks(), target.lastTickPosX, target.posX);
            double markerY = MathUtil.interporate(event.partialTicks(), target.lastTickPosY, target.posY) + target.height / 1.6f;
            double markerZ = MathUtil.interporate(event.partialTicks(), target.lastTickPosZ, target.posZ);
            float time = (float) ((((System.currentTimeMillis() - lastTime) / 1500F)) + (Math.sin((((System.currentTimeMillis() - lastTime) / 1500F))) / 10f));
            float alpha = 0.5f * 1;
            float pl = 0;
            boolean fa = false;

            for (int iteration = 0; iteration < 3; iteration++) {
                for (float i = time * 360; i < time * 360 + 90; i += 2) {
                    float max = time * 360 + 90;
                    float dc = MathUtil.normalize(i, time * 360 - 45, max);
                    float rf = 0.6f;
                    double radians = Math.toRadians(i);
                    double plY = pl + Math.sin(radians * 1.2f) * 0.1f;
                    int firstColor = ColorUtil.applyOpacity(getTargetColor(), getAlpha()).getRGB();
                    int secondColor = ColorUtil.applyOpacity(getTargetColor(), getAlpha()).getRGB();
                    GlStateManager.pushMatrix();
                    RenderUtil.setupOrientationMatrix(markerX, markerY, markerZ);

                    float[] idk = new float[]{mc.getRenderManager().playerViewY, mc.getRenderManager().playerViewX};

                    GL11.glRotated(-idk[0], 0.0, 1.0, 0.0);
                    GL11.glRotated(idk[1], 1.0, 0.0, 0.0);

                    GlStateManager.depthMask(false);
                    float q = (!fa ? 0.25f : 0.15f) * (Math.max(fa ? 0.25f : 0.15f, fa ? dc : (1f + (0.4f - dc)) / 2f) + 0.45f);
                    float size = q * (2f + ((0.5f - alpha) * 2));
                    RenderUtil.drawImage(
                            glowCircle,
                            (float) (Math.cos(radians) * rf - size / 2f),
                            (float) (plY - 0.7),
                            size, size,
                            firstColor);
                    GL11.glEnable(GL11.GL_DEPTH_TEST);
                    GlStateManager.depthMask(true);
                    GlStateManager.popMatrix();
                }
                time *= -1.025f;
                fa = !fa;
                pl += 0.45f;
            }
        }
    }

    private void drawTargetESP2D(float x, float y, float scale, int index) {
        long millis = (System.currentTimeMillis() - lastTime) + index * 400L;
        boolean useAnimation = imageMode.getValue() == 5 ? animation.getValue() : true;
        double angle = useAnimation ? MathHelper.clamp_double((Math.sin(millis / 150.0) + 1.0) / 2.0 * 30.0, 0.0, 30.0) : 15.0;
        double scaled = useAnimation ? MathHelper.clamp_double((Math.sin(millis / 500.0) + 1.0) / 2.0, 0.8, 1.0) : 0.9;
        double rotate = useAnimation ? MathHelper.clamp_double((Math.sin(millis / 1000.0) + 1.0) / 2.0 * 360.0, 0.0, 360.0) : 0.0;
        rotate = (imageMode.getValue() == 1 ? 45 : 0) - (angle - 15.0) + rotate;

        Color baseColor = getTargetColor();
        float hurtAlpha = getHurtAlpha();

        Color hurtColor = new Color(255, 0, 0, 185);
        Color baseWithAlpha = ColorUtil.applyOpacity(baseColor, 1.0f);
        Color hurtWithAlpha = ColorUtil.applyOpacity(hurtColor, hurtAlpha);

        int r = (int) (baseWithAlpha.getRed() * (1 - hurtAlpha) + hurtWithAlpha.getRed() * hurtAlpha);
        int g = (int) (baseWithAlpha.getGreen() * (1 - hurtAlpha) + hurtWithAlpha.getGreen() * hurtAlpha);
        int b = (int) (baseWithAlpha.getBlue() * (1 - hurtAlpha) + hurtWithAlpha.getBlue() * hurtAlpha);
        int a = (int) (baseWithAlpha.getAlpha() * (1 - hurtAlpha) + hurtWithAlpha.getAlpha() * hurtAlpha);

        int color = new Color(r, g, b, a).getRGB();
        int color2 = color;
        int color3 = color;
        int color4 = color;

        rotate = 45 - (angle - 15.0) + rotate;
        float size = 128.0f * scale * (float) scaled;

        float renderX = x - size / 2.0f;
        float renderY = y - size / 2.0f;
        float x2 = renderX + size;
        float y2 = renderY + size;

        GlStateManager.pushMatrix();
        GlStateManager.pushAttrib();
        try {
            GlStateManager.translate(x, y, 0);
            GlStateManager.rotate((float) rotate, 0, 0, 1);
            GlStateManager.translate(-x, -y, 0);
            GlStateManager.disableAlpha();
            GlStateManager.depthMask(false);
            GlStateManager.enableBlend();
            GlStateManager.shadeModel(7425);
            GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);
            Color HUDColor = getTargetColor();
            float alpha = getAlpha();
            GlStateManager.color(
                    HUDColor.getRed() / 255.0f,
                    HUDColor.getGreen() / 255.0f,
                    HUDColor.getBlue() / 255.0f,
                    alpha
            );
            switch (imageMode.getValue()) {
                case 0:
                    RenderUtil.drawImage(rectangle, renderX, renderY, x2, y2, color, color2, color3, color4);
                    break;
                case 1:
                    RenderUtil.drawImage(quadstapple, renderX, renderY, x2, y2, color, color2, color3, color4);
                    break;
                case 2:
                    RenderUtil.drawImage(trianglestapple, renderX, renderY, x2, y2, color, color2, color3, color4);
                    break;
                case 3:
                    RenderUtil.drawImage(trianglestipple, renderX, renderY, x2, y2, color, color2, color3, color4);
                    break;
                case 4:
                    RenderUtil.drawImage(aim, renderX, renderY, x2, y2, color, color2, color3, color4);
                    break;
            }
        } finally {
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.resetColor();
            GlStateManager.shadeModel(7424);
            GlStateManager.depthMask(true);
            GlStateManager.enableAlpha();
            GlStateManager.popAttrib();
            GlStateManager.popMatrix();
            GL20.glUseProgram(0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(0);
            GlStateManager.resetColor();
            GlStateManager.enableTexture2D();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
            GlStateManager.disableBlend();
        }
    }

}
