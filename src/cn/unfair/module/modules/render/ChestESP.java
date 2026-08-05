package cn.unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.Render3DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.RenderUtil;

import java.awt.*;
import java.util.stream.Collectors;

public class ChestESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ColorProperty color;
    public final PercentProperty opacity;
    public final BooleanProperty tracers;

    public ChestESP() {
        super("ChestESP", false, true);
        this.color = new ColorProperty("color", new Color(255, 170, 0).getRGB());
        this.opacity = new PercentProperty("opacity", 100);
        this.tracers = new BooleanProperty("tracers", false);
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled()) {
            RenderUtil.enableRenderState();
            for (TileEntity chest : mc.theWorld.loadedTileEntityList.stream().filter(tileEntity -> tileEntity instanceof TileEntityChest || tileEntity instanceof TileEntityEnderChest).collect(Collectors.toList())) {
                AxisAlignedBB aabb = new AxisAlignedBB(
                        (double) chest.getPos().getX() + 0.0625,
                        (double) chest.getPos().getY() + 0.0,
                        (double) chest.getPos().getZ() + 0.0625,
                        (double) chest.getPos().getX() + 0.9375,
                        (double) chest.getPos().getY() + 0.875,
                        (double) chest.getPos().getZ() + 0.9375
                )
                        .offset(
                                -mc.getRenderManager().getRenderPosX(),
                                -mc.getRenderManager().getRenderPosY(),
                                -mc.getRenderManager().getRenderPosZ()
                        );
                Color color = new Color(this.color.getValue());
                RenderUtil.drawBoundingBox(
                        aabb, color.getRed(), color.getGreen(), color.getBlue(), (int) ((float) this.opacity.getValue() / 100.0F * 255.0F), 1.5F
                );
                if (this.tracers.getValue()) {
                    Vec3 vec;
                    if (mc.gameSettings.thirdPersonView == 0) {
                        vec = new Vec3(0.0, 0.0, 1.0)
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
                        vec = new Vec3(0.0, 0.0, 0.0)
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
                                                        RenderUtil.lerpFloat(
                                                                mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, mc.timer.renderPartialTicks
                                                        )
                                                )
                                        )
                                );
                    }
                    vec = new Vec3(vec.xCoord, vec.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), vec.zCoord);
                    float opacity = (float) ((Tracers) Unfair.moduleManager.modules.get(Tracers.class)).opacity.getValue() / 100.0F;
                    RenderUtil.drawLine3D(
                            vec,
                            (double) chest.getPos().getX() + 0.5,
                            (double) chest.getPos().getY() + 0.5,
                            (double) chest.getPos().getZ() + 0.5,
                            (float) color.getRed() / 255.0F,
                            (float) color.getGreen() / 255.0F,
                            (float) color.getBlue() / 255.0F,
                            opacity,
                            1.5F
                    );
                }
            }
            RenderUtil.disableRenderState();
        }
    }
}
