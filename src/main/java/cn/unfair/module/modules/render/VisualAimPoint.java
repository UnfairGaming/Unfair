package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.event.types.EventType;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.rotation.AdvancedRotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;

public class VisualAimPoint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"DOT", "BOX"});
    public final BooleanProperty onlySilent = new BooleanProperty("only-silent", false);
    public final BooleanProperty notOnMiss = new BooleanProperty("not-on-miss", false);
    public final FloatProperty size = new FloatProperty("size", 0.1F, 0.05F, 0.2F);
    public final BooleanProperty customColor = new BooleanProperty("custom-color", false);
    public final ColorProperty color = new ColorProperty("color", 0xFFFFFF, this.customColor::getValue);
    private Vec3 pos;
    private Vec3 lastPos;
    private boolean miss = true;

    public VisualAimPoint() {
        super("VisualAimPoint", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (this.onlySilent.getValue() && !RotationState.isActived()) {
            return;
        }

        float yaw = RotationState.isActived() ? RotationState.getRotationYawHead() : mc.thePlayer.rotationYaw;
        float pitch = RotationState.isActived() ? RotationState.getRotationPitch() : mc.thePlayer.rotationPitch;
        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(new RotationUtil.RotationVec(yaw, pitch), getTraceDistance(), 0.0F);
        this.miss = result == null || result.typeOfHit == RayCastUtil.RayCastResult.Type.MISS || result.hitVec == null;

        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 look = mc.thePlayer.getLookCustom(yaw, pitch).normalize();
        Vec3 hitVec = this.miss ? eyePos.addVector(look.xCoord * getTraceDistance(), look.yCoord * getTraceDistance(), look.zCoord * getTraceDistance()) : result.hitVec;

        this.lastPos = this.pos;
        switch (this.mode.getValue()) {
            case 0:
                this.pos = hitVec;
                break;
            case 1:
                double distance = hitVec.distanceTo(eyePos);
                this.pos = eyePos.addVector(look.xCoord * distance, look.yCoord * distance, look.zCoord * distance);
                break;
        }
    }

    private double getTraceDistance() {
        return Math.max(6.0D, mc.playerController.getBlockReachDistance());
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.onlySilent.getValue() && !RotationState.isActived()) {
            return;
        }
        if (this.notOnMiss.getValue() && this.miss) {
            return;
        }
        if (this.pos == null || this.lastPos == null) {
            return;
        }

        Vec3 vec = AdvancedRotationMath.interpolate(this.lastPos, this.pos, event.partialTicks());
        double renderX = mc.thePlayer.prevPosX + (mc.thePlayer.posX - mc.thePlayer.prevPosX) * event.partialTicks();
        double renderY = mc.thePlayer.prevPosY + (mc.thePlayer.posY - mc.thePlayer.prevPosY) * event.partialTicks();
        double renderZ = mc.thePlayer.prevPosZ + (mc.thePlayer.posZ - mc.thePlayer.prevPosZ) * event.partialTicks();
        double d = this.size.getValue() / 2.0D;
        AxisAlignedBB box = new AxisAlignedBB(vec.xCoord - d, vec.yCoord - d, vec.zCoord - d, vec.xCoord + d, vec.yCoord + d, vec.zCoord + d)
                .offset(-renderX, -renderY, -renderZ);
        int argb = (0xCC << 24) | (this.customColor.getValue() ? this.color.getValue() : HUD.getColor(System.currentTimeMillis()).getRGB() & 0xFFFFFF);
        RenderUtil.drawAxisAlignedBB(box, this.mode.getValue() == 0, argb);
    }
}
