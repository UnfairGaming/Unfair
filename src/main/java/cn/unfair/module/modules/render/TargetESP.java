package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.mixin.IAccessorEntityRenderer;
import cn.unfair.mixin.IAccessorMinecraft;
import cn.unfair.mixin.IAccessorRenderManager;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ColorUtil;
import cn.unfair.util.AnimationUtil;
import cn.unfair.util.MathUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class TargetESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final ModeProperty mode = new ModeProperty("Mark Mode", 1, new String[]{"Points", "Ghost", "Ghost2", "Image", "Exhi", "Circle"});
    private final ModeProperty imageMode = new ModeProperty("Image Mode", 0, new String[]{"Rectangle", "QuadStapple", "TriangleStapple", "TriangleStipple", "Aim","Custom"}, () -> mode.getValue() == 3);
    private final BooleanProperty animation = new BooleanProperty("Animation", true, () -> mode.getValue() == 3 && imageMode.getValue() == 5);
    // 给👇这个删了你妈就死了 笑死我了
    private final BooleanProperty selectImage = new BooleanProperty("Select Image", false, () -> mode.getValue() == 3 && imageMode.getValue() == 5) {
        @Override
        public boolean setValue(Object value) {
            boolean result = super.setValue(value);
            if (result && (Boolean)value) {
                selectCustomImage();
                super.setValue(false);
            }
            return result;
        }
    };
    private final FloatProperty circleSpeed = new FloatProperty("CircleSpeed", 2.0F, 1.0F, 5.0F, () -> mode.getValue() == 5);
    private final BooleanProperty onlyPlayer = new BooleanProperty("OnlyPlayer", false);
    private final BooleanProperty showHurt = new BooleanProperty("ShowHurt", false, () -> mode.getValue() == 3);
    private ResourceLocation customImage = null;
    private long lastHurtTime = 0;
    private static final long HURT_DURATION = 500;

    private EntityLivingBase target;
    private final TimerUtil displayTimer = new TimerUtil();
    private long lastTime = System.currentTimeMillis();
    private long alphaStartTime = 0L;
    private boolean hasFullyFadedIn = false;
    private final ResourceLocation glowCircle = new ResourceLocation("minecraft", "unfair/targetesp/glow_circle.png");
    private final ResourceLocation rectangle = new ResourceLocation("minecraft", "unfair/targetesp/rectangle.png");
    private final ResourceLocation quadstapple = new ResourceLocation("minecraft", "unfair/targetesp/quadstapple.png");
    private final ResourceLocation trianglestapple = new ResourceLocation("minecraft", "unfair/targetesp/trianglestapple.png");
    private final ResourceLocation trianglestipple = new ResourceLocation("minecraft", "unfair/targetesp/trianglestipple.png");
    private final ResourceLocation aim = new ResourceLocation("minecraft", "unfair/targetesp/shenmi.png");
    public double prevCircleStep;
    public double circleStep;

    public TargetESP() {
        super("TargetESP", false);
    }

    private void selectCustomImage() {
        new Thread(() -> {
            FileDialog fileDialog = new FileDialog((Frame)null, "Select Custom Image", FileDialog.LOAD);
            fileDialog.setFile("*.png");
            fileDialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".png"));
            fileDialog.setVisible(true);

            String file = fileDialog.getFile();
            if (file != null) {
                String directory = fileDialog.getDirectory();
                File imageFile = new File(directory, file);
                try {
                    BufferedImage image = ImageIO.read(imageFile);
                    if (image != null) {
                        ResourceLocation newImage = new ResourceLocation("epilogue", "custom_target_" + System.currentTimeMillis());
                        mc.addScheduledTask(() -> {
                            mc.getTextureManager().loadTexture(newImage, new DynamicTexture(image));
                            customImage = newImage;
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, "Image Selector Thread").start();
    }

    private Color getInterfaceColor() {
        return new Color(HUD.getColor(System.currentTimeMillis()).getRGB());
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

    @EventTarget
    public void onPacket(PacketEvent event) {
        if(!this.isEnabled()) return;
        if (event.getType() == EventType.SEND && event.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity packet = (C02PacketUseEntity) event.getPacket();
            if (packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                Entity entity = packet.getEntityFromWorld(mc.theWorld);
                if (entity == target) {
                    lastHurtTime = System.currentTimeMillis();
                }
            }
            if (packet.getAction() != C02PacketUseEntity.Action.ATTACK) {
                return;
            }
            Entity entity = packet.getEntityFromWorld(mc.theWorld);
            if (entity instanceof EntityLivingBase &&
                    (!onlyPlayer.getValue() || entity instanceof EntityPlayer)) {
                EntityLivingBase newTarget = (EntityLivingBase) entity;
                if (target != newTarget) {
                    target = newTarget;
                    lastTime = System.currentTimeMillis();
                    alphaStartTime = AnimationUtil.start();
                    hasFullyFadedIn = false;
                }
                displayTimer.reset();
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event){
        if(!this.isEnabled()) return;
        if(target != null && displayTimer.hasTimeElapsed(1000)){
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
                return AnimationUtil.progress(alphaStartTime, 200.0F, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 0);
            } else {
                hasFullyFadedIn = true;
                return 1.0f;
            }
        } else {
            if (displayElapsed > 800) {
                return 1.0F - AnimationUtil.progress(System.currentTimeMillis() - (displayElapsed - 800L), 200.0F, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks, 0);
            } else {
                return 1.0f;
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if(!this.isEnabled()) return;
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

                Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), event.getPartialTicks());
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
                    int color = ColorUtil.applyOpacity(getInterfaceColor(), getAlpha()).getRGB();
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
                    int color = ColorUtil.applyOpacity(getInterfaceColor(), getAlpha()).getRGB();
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
                    int color = ColorUtil.applyOpacity(getInterfaceColor(), getAlpha()).getRGB();
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
                ((IAccessorEntityRenderer) mc.entityRenderer).callSetupCameraTransform(event.getPartialTicks(), 2);
                double x = target.prevPosX + (target.posX - target.prevPosX) * (double) event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
                double y = target.prevPosY + (target.posY - target.prevPosY) * (double) event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY();
                double z = target.prevPosZ + (target.posZ - target.prevPosZ) * (double) event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
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
                prevCircleStep = circleStep;
                circleStep += (double) this.circleSpeed.getValue() * ((IAccessorMinecraft) mc).getTimer().renderPartialTicks * 0.05;
                float eyeHeight = target.getEyeHeight();
                if (target.isSneaking()) {
                    eyeHeight -= 0.2F;
                }

                double cs = prevCircleStep + (circleStep - prevCircleStep) * (double) event.getPartialTicks();
                double prevSinAnim = Math.abs(1.0D + Math.sin(cs - 0.5D)) / 2.0D;
                double sinAnim = Math.abs(1.0D + Math.sin(cs)) / 2.0D;
                double x = target.lastTickPosX + (target.posX - target.lastTickPosX) * (double) event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX();
                double y = target.lastTickPosY + (target.posY - target.lastTickPosY) * (double) event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY() + prevSinAnim * (double) eyeHeight;
                double z = target.lastTickPosZ + (target.posZ - target.lastTickPosZ) * (double) event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ();
                double nextY = target.lastTickPosY + (target.posY - target.lastTickPosY) * (double) event.getPartialTicks() - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY() + sinAnim * (double) eyeHeight;
                GL11.glPushMatrix();
                GL11.glDisable(2884);
                GL11.glDisable(3553);
                GL11.glEnable(3042);
                GL11.glDisable(2929);
                GL11.glDisable(3008);
                GL11.glShadeModel(7425);
                GL11.glBegin(8);

                int i;
                Color color;
                for (i = 0; i <= 360; ++i) {
                    color = getInterfaceColor();
                    float alpha = getAlpha();
                    GL11.glColor4f((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, 0.6F * alpha);
                    GL11.glVertex3d(x + Math.cos(Math.toRadians(i)) * (double) target.width * 0.8D, nextY, z + Math.sin(Math.toRadians(i)) * (double) target.width * 0.8D);
                    GL11.glColor4f((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, 0.01F * alpha);
                    GL11.glVertex3d(x + Math.cos(Math.toRadians(i)) * (double) target.width * 0.8D, y, z + Math.sin(Math.toRadians(i)) * (double) target.width * 0.8D);
                }

                GL11.glEnd();
                GL11.glEnable(2848);
                GL11.glBegin(2);

                for (i = 0; i <= 360; ++i) {
                    color = getInterfaceColor();
                    float alpha = getAlpha();
                    GL11.glColor4f((float) color.getRed() / 255.0F, (float) color.getGreen() / 255.0F, (float) color.getBlue() / 255.0F, 0.8F * alpha);
                    GL11.glVertex3d(x + Math.cos(Math.toRadians(i)) * (double) target.width * 0.8D, nextY, z + Math.sin(Math.toRadians(i)) * (double) target.width * 0.8D);
                }

                GL11.glEnd();
                GL11.glDisable(2848);
                GL11.glEnable(3553);
                GL11.glEnable(3008);
                GL11.glEnable(2929);
                GL11.glShadeModel(7424);
                GL11.glDisable(3042);
                GL11.glEnable(2884);
                GL11.glPopMatrix();
            }
        }
    }

    private void ghost2(Render3DEvent event) {
        if (target == null) return;
        float partialTicks = event.getPartialTicks();
        Vec3 interpolated = MathUtil.interpolate(new Vec3(target.lastTickPosX, target.lastTickPosY, target.lastTickPosZ), target.getPositionVector(), partialTicks);
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
                RenderUtil.drawImage(glowCircle, 0f, 0f, -(float) ((0.16 + 0.06 * (1.0 - ((gi % 28) + (0.0)) / (double) 28)) * (0.86 + 0.24 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.6 + (gi % 28) * 0.45) + 0.14 * ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), -(float) ((0.16 + 0.06 * (1.0 - ((gi % 28) + (0.0)) / (double) 28)) * (0.86 + 0.24 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.6 + (gi % 28) * 0.45) + 0.14 * ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), ColorUtil.applyOpacity(getInterfaceColor(), getAlpha()).getRGB());
                GlStateManager.popMatrix();
            } else if (gi < (28) + 10) {
                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos(((gi - (28) + (0.0)) / (double) 10) * Math.PI * 2.0 - (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 0.95) * ((target.width * 0.86 + 0.20) + 0.10 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + gi - (28)) - (0.18 + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.25) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0)), (-(0.44 + 0.08 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.2)) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) - 0.06 * (((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5) * ((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5) * (3.0 - 2.0 * ((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) + Math.PI / 2.0) + 1.0) * 0.5))) - 0.5) * 2.0) + Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + (gi - (28)) * 0.70) * 0.10), Math.sin(((gi - (28) + (0.0)) / (double) 10) * Math.PI * 2.0 - (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 0.95) * ((target.width * 0.86 + 0.20) + 0.10 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + gi - (28)) - (0.18 + (0.22 + (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)) * 0.28) * 0.25) * ((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0)));
                GlStateManager.rotate((float) (((System.currentTimeMillis() - lastTime) / 1000.0) * 320.0 + (gi - (28)) * 30.0), 0, 0, 1);
                RenderUtil.drawImage(glowCircle, 0f, 0f, -(float) (0.17 + 0.06 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 3.0 + gi - (28))) + 0.05 * (1.0 - ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), -(float) (0.17 + 0.06 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 3.0 + gi - (28))) + 0.05 * (1.0 - ((((((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) < 0.68) ? (Math.pow((((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) / 0.68), 3.6) * 0.70) : (0.70 + (1.0 - 0.70) * (1.0 - Math.pow(1.0 - (((Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0)) + 1.0) * 0.5) - 0.68) / 0.32, 1.12)))) - 0.5) * 2.0) * 0.5) + 0.5))), ColorUtil.applyOpacity(getInterfaceColor(), getAlpha()).getRGB());
                GlStateManager.popMatrix();
            } else {
                GlStateManager.pushMatrix();
                GlStateManager.translate(Math.cos(((gi - (28) - 10 + (0.0)) / (double) 10) * Math.PI * 2.0 + (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.9) * ((target.width * 0.62 + 0.12) + 0.12 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.8 + gi - (28) - 10)), (Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.6 + gi - (28) - 10) * 0.16) + (Math.cos(((System.currentTimeMillis() - lastTime) / 1000.0) * 2.0 + (gi - (28) - 10) * 0.7) * 0.12), Math.sin(((gi - (28) - 10 + (0.0)) / (double) 10) * Math.PI * 2.0 + (((System.currentTimeMillis() - lastTime) / 1000.0) * (1.2 + 0.8 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.35)))) * 1.9) * ((target.width * 0.62 + 0.12) + 0.12 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 1.8 + gi - (28) - 10)));
                GlStateManager.rotate((float) (((System.currentTimeMillis() - lastTime) / 1000.0) * 420.0 + (gi - (28) - 10) * 40.0), 0, 0, 1);
                RenderUtil.drawImage(glowCircle, 0f, 0f, -(float) (0.09 + 0.05 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 4.2 + (gi - (28) - 10) * 1.3))), -(float) (0.09 + 0.05 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 4.2 + (gi - (28) - 10) * 1.3))), ColorUtil.applyOpacity(getInterfaceColor(), (float) (getAlpha() * (0.35 + 0.65 * (0.5 + 0.5 * Math.sin(((System.currentTimeMillis() - lastTime) / 1000.0) * 5.0 + (gi - (28) - 10) * 2.2))))).getRGB());
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
        if(!this.isEnabled()) return;
        int index = 3;
        if (mode.getValue() == 3 && target != null) {
            float dst = mc.thePlayer.getDistanceToEntity(target);
            float[] pos = targetESPSPos(target, event);
            if (pos != null) {
                drawTargetESP2D(pos[0], pos[1],
                        (1.0f - MathHelper.clamp_float(Math.abs(dst - 6.0f) / 60.0f, 0.0f, 0.75f)) * 1, index);
            }
        }
    }

    @EventTarget
    public void onShader2D(Shader2DEvent event) {
        if(!this.isEnabled()) return;
        if (event.getShaderType() == Shader2DEvent.ShaderType.GLOW) {
            int index = 3;
            if (mode.getValue() == 3 && imageMode.getValue() == 0 && target != null) {
                float dst = mc.thePlayer.getDistanceToEntity(target);
                float[] pos = targetESPSPos(target, null);
                if (pos != null) {
                    drawTargetESP2D(pos[0], pos[1],
                            (1.0f - MathHelper.clamp_float(Math.abs(dst - 6.0f) / 60.0f, 0.0f, 0.75f)) * 1, index);
                }
            }
        }
    }

    private void points(Render3DEvent event) {
        if (target != null) {
            double markerX = MathUtil.interporate(event.getPartialTicks(), target.lastTickPosX, target.posX);
            double markerY = MathUtil.interporate(event.getPartialTicks(), target.lastTickPosY, target.posY) + target.height / 1.6f;
            double markerZ = MathUtil.interporate(event.getPartialTicks(), target.lastTickPosZ, target.posZ);
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
                    int firstColor = ColorUtil.applyOpacity(getInterfaceColor(), getAlpha()).getRGB();
                    int secondColor = ColorUtil.applyOpacity(getInterfaceColor(), getAlpha()).getRGB();
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

        Color baseColor = getInterfaceColor();
        float hurtAlpha = getHurtAlpha();

        Color hurtColor = new Color(255, 0, 0, 185);
        Color baseWithAlpha = ColorUtil.applyOpacity(baseColor, 1.0f);
        Color hurtWithAlpha = ColorUtil.applyOpacity(hurtColor, hurtAlpha);

        int r = (int)(baseWithAlpha.getRed() * (1 - hurtAlpha) + hurtWithAlpha.getRed() * hurtAlpha);
        int g = (int)(baseWithAlpha.getGreen() * (1 - hurtAlpha) + hurtWithAlpha.getGreen() * hurtAlpha);
        int b = (int)(baseWithAlpha.getBlue() * (1 - hurtAlpha) + hurtWithAlpha.getBlue() * hurtAlpha);
        int a = (int)(baseWithAlpha.getAlpha() * (1 - hurtAlpha) + hurtWithAlpha.getAlpha() * hurtAlpha);

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
        GlStateManager.translate(x, y, 0);
        GlStateManager.rotate((float) rotate, 0, 0, 1);
        GlStateManager.translate(-x, -y, 0);
        GL11.glDisable(3008);
        GlStateManager.depthMask(false);
        GlStateManager.enableBlend();
        GlStateManager.shadeModel(7425);
        GlStateManager.tryBlendFuncSeparate(770, 1, 1, 0);
        Color HUDColor = getInterfaceColor();
        float alpha = getAlpha();
        GL11.glColor4f(HUDColor.getRed() / 255.0f, HUDColor.getGreen() / 255.0f, HUDColor.getBlue() / 255.0f, alpha);
        switch (imageMode.getValue()){
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
            case 5:
                if (customImage != null) {
                    RenderUtil.drawImage(customImage, renderX, renderY, x2, y2, color, color2, color3, color4);
                } else {
                    RenderUtil.drawImage(rectangle, renderX, renderY, x2, y2, color, color2, color3, color4);
                }
                break;
        }

        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.resetColor();
        GlStateManager.shadeModel(7424);
        GlStateManager.depthMask(true);
        GL11.glEnable(3008);
        GlStateManager.popMatrix();
    }

    private float[] targetESPSPos(EntityLivingBase entity, Render2DEvent event) {
        EntityRenderer entityRenderer = mc.entityRenderer;
        float partialTicks = event != null ? event.getPartialTicks() : ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;
        double x = MathUtil.interpolate(entity.prevPosX, entity.posX, partialTicks);
        double y = MathUtil.interpolate(entity.prevPosY, entity.posY, partialTicks) + entity.height * 0.4f;
        double z = MathUtil.interpolate(entity.prevPosZ, entity.posZ, partialTicks);
        double width = entity.width / 2.0f;
        double height = entity.height / 4.0f;
        AxisAlignedBB bb = new AxisAlignedBB(x - width, y - height, z - width, x + width, y + height, z + width);
        final double[][] vectors = {{bb.minX, bb.minY, bb.minZ},
                {bb.minX, bb.maxY, bb.minZ},
                {bb.minX, bb.maxY, bb.maxZ},
                {bb.minX, bb.minY, bb.maxZ},
                {bb.maxX, bb.minY, bb.minZ},
                {bb.maxX, bb.maxY, bb.minZ},
                {bb.maxX, bb.maxY, bb.maxZ},
                {bb.maxX, bb.minY, bb.maxZ}};
        ((IAccessorEntityRenderer) entityRenderer).callSetupCameraTransform(partialTicks, 0);
        float[] projection;
        final float[] position = new float[]{Float.MAX_VALUE, Float.MAX_VALUE, -1.0F, -1.0F};
        for (final double[] vec : vectors) {
            projection = RenderUtil.project2D((float) (vec[0] - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX()), (float) (vec[1] - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY()), (float) (vec[2] - ((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()), new ScaledResolution(mc).getScaleFactor());
            if (projection != null && projection[2] >= 0.0F && projection[2] < 1.0F) {
                position[0] = Math.min(projection[0], position[0]);
                position[1] = Math.min(projection[1], position[1]);
                position[2] = Math.max(projection[0], position[2]);
                position[3] = Math.max(projection[1], position[3]);
            }
        }
        entityRenderer.setupOverlayRendering();
        float centerX = (position[0] + position[2]) / 2.0f;
        float centerY = (position[1] + position[3]) / 2.0f;
        return new float[]{centerX, centerY};
    }
}
