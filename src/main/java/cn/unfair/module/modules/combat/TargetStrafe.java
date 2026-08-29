package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.StrafeEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.movement.Fly;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.module.modules.movement.Speed;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.client.MathUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.player.PlayerUtil;
import cn.unfair.util.render.ColorUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import java.awt.*;
import java.util.ArrayList;

public class TargetStrafe extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty radius = new FloatProperty("Radius", 1.0F, 0.0F, 6.0F);
    public final IntProperty points = new IntProperty("Points", 6, 3, 24);
    public final BooleanProperty requirePress = new BooleanProperty("Require Press", true);
    public final BooleanProperty speedOnly = new BooleanProperty("Speed Only", true);
    public final ModeProperty showTarget = new ModeProperty("Show Target", 1, new String[]{"None", "Default", "Hud"});
    private EntityLivingBase target = null;
    private float targetYaw = Float.NaN;
    private int direction = 1;

    public TargetStrafe() {
        super("TargetStrafe", false);
    }

    private boolean canStrafe() {
        if (this.speedOnly.getValue()) {
            Speed speed = (Speed) Unfair.moduleManager.modules.get(Speed.class);
            Fly fly = (Fly) Unfair.moduleManager.modules.get(Fly.class);
            LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
            if (!speed.isEnabled() && !fly.isEnabled() && (!longJump.isEnabled() || !longJump.isJumping())) {
                return false;
            }
        }
        return !this.requirePress.getValue() || PlayerUtil.isJumping();
    }

    private EntityLivingBase getKillAuraTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura.isEnabled() && killAura.isAttackAllowed()) {
            EntityLivingBase entityLivingBase = killAura.getTarget();
            return !TeamUtil.isEntityLoaded(entityLivingBase) ? null : entityLivingBase;
        } else {
            return null;
        }
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
        switch (this.showTarget.getValue()) {
            case 1:
                if (!(entityLivingBase instanceof EntityPlayer)) {
                    return Color.WHITE;
                }
                return TeamUtil.getTeamColor((EntityPlayer) entityLivingBase, 1.0F);
            case 2:
                Unfair.moduleManager.modules.get(HUD.class);
                int color = HUD.getColor(System.currentTimeMillis()).getRGB();
                return new Color(color);
            default:
                return new Color(-1);
        }
    }

    private boolean isInWater(double x, double z) {
        return PlayerUtil.checkInWater(
                new AxisAlignedBB(x - 0.015, mc.thePlayer.posY, z - 0.015, x + 0.015, mc.thePlayer.posY + (double) mc.thePlayer.height, z + 0.015)
        );
    }

    public float getTargetYaw() {
        return this.targetYaw;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            boolean left = PlayerUtil.isMovingLeft();
            boolean right = PlayerUtil.isMovingRight();
            if (left ^ right) {
                this.direction = left ? 1 : -1;
            }
            if (!this.canStrafe()) {
                this.target = null;
                this.targetYaw = Float.NaN;
            } else {
                this.target = this.getKillAuraTarget();
                if (this.target == null) {
                    this.targetYaw = Float.NaN;
                } else {
                    ArrayList<Vec2d> vpositions = new ArrayList<>();
                    for (int i = 0; i < this.points.getValue(); i++) {
                        vpositions.add(
                                new Vec2d(
                                        (double) this.radius.getValue()
                                                * Math.cos((double) i * ((Math.PI * 2) / (double) this.points.getValue())),
                                        (double) this.radius.getValue()
                                                * Math.sin((double) i * ((Math.PI * 2) / (double) this.points.getValue()))
                                )
                        );
                    }
                    if (vpositions.isEmpty()) {
                        this.target = null;
                        this.targetYaw = Float.NaN;
                    } else {
                        double closestDistance = 0.0;
                        int closestIndex = -1;
                        for (int i = 0; i < vpositions.size(); i++) {
                            double distance = mc.thePlayer
                                    .getDistance(
                                            this.target.posX + (vpositions.get(i)).x(), mc.thePlayer.posY, this.target.posZ + (vpositions.get(i)).y()
                                    );
                            if (closestIndex == -1 || distance < closestDistance) {
                                closestDistance = distance;
                                closestIndex = i;
                            }
                        }
                        if (mc.thePlayer.isCollidedHorizontally) {
                            this.direction *= -1;
                        }
                        int nextIndex = closestIndex + this.direction;
                        nextIndex = MathUtil.wrapIndex(nextIndex, vpositions.size());
                        double nextX = this.target.posX + (vpositions.get(nextIndex)).x();
                        double nextZ = this.target.posZ + (vpositions.get(nextIndex)).y();
                        if (this.isInWater(nextX, nextZ)) {
                            this.direction *= -1;
                            nextIndex = closestIndex + this.direction;
                            nextIndex = MathUtil.wrapIndex(nextIndex, vpositions.size());
                            nextX = this.target.posX + (vpositions.get(nextIndex)).x();
                            nextZ = this.target.posZ + (vpositions.get(nextIndex)).y();
                        }
                        double deltaX = nextX - mc.thePlayer.posX;
                        double deltaZ = nextZ - mc.thePlayer.posZ;
                        float currentPitch = event.getPitch();
                        float currentYaw = event.getYaw();
                        double deltaY = 0.0;
                        this.targetYaw = RotationUtil.getRotationsTo(deltaX, deltaY, deltaZ, currentYaw, currentPitch)[0];
                        event.setPervRotation(this.targetYaw, 10);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled()) {
            if (!Float.isNaN(this.targetYaw) && MoveUtil.isForwardPressed()) {
                event.setStrafe(0.0F);
                event.setForward(1.0F);
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && TeamUtil.isEntityLoaded(this.target)) {
            if (this.showTarget.getValue() != 0) {
                Color color = this.getTargetColor(this.target);
                RenderUtil.enableRenderState();
                RenderUtil.drawEntityCircle(
                        this.target, this.radius.getValue(), this.points.getValue(), ColorUtil.darker(color, 0.2F).getRGB()
                );
                RenderUtil.drawEntityCircle(this.target, this.radius.getValue(), this.points.getValue(), color.getRGB());
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onDisabled() {
        this.target = null;
        this.targetYaw = Float.NaN;
    }

    public record Vec2d(double x, double y) {
    }
}
