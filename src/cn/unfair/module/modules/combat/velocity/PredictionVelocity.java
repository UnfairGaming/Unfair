package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.BackTrack;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.module.modules.movement.Stuck;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;

import static cn.unfair.util.ChatUtil.dbg;

public class PredictionVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 100;
    private static final int PREDICT_TICKS = 3;
    private static final int POST_TICKS = 2;

    private int predictTick = -1;
    private boolean predictSprinting;
    private boolean delaying;
    private int jumpResetTicks;
    private boolean rotating;
    private Entity target;

    public PredictionVelocity() {
        super("Prediction");
    }

    private Velocity velocity() {
        return (Velocity) Unfair.moduleManager.getModule(Velocity.class);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        Velocity velocity = velocity();
        if (mc.theWorld == null || mc.thePlayer == null) {
            resetPredict();
            return;
        }
        if (velocity == null || !isEnabled() || event.getType() != EventType.PRE) return;
        if (predictTick < 0) {
            rotating = false;
            return;
        }

        int tick = predictTick;
        predictTick++;
        rotating = true;

        if (tick < PREDICT_TICKS) {
            if (delaying) {
                if (tick == 0) {
                    mc.thePlayer.setSprinting(true);
                } else if (tick == 1) {
                    doReduce();
                } else if (tick == PREDICT_TICKS - 1) {
                    releaseDelay();
                }
            } else if (predictSprinting && tick == 0) {
                doReduce();
            }

            if (jumpResetTicks > 0) {
                doJumpReset();
                jumpResetTicks--;
            }
        }

        if (target != null) {
            float[] rots = getRotationsToEntity(target);
            event.setRotation(rots[0], rots[1], ROTATION_PRIORITY);
            event.setPervRotation(rots[0], ROTATION_PRIORITY);
        }

        if (tick >= PREDICT_TICKS + POST_TICKS - 1) {
            if (delaying) releaseDelay();
            resetPredict();
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!isEnabled() || !rotating) return;
        mc.thePlayer.movementInput.moveForward = 1.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.setSprinting(true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        Velocity velocity = velocity();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (velocity == null || !isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) return;
        if (event.getPacket() instanceof S12PacketEntityVelocity packet) {
            if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
                if (predictTick < 0
                        && !Velocity.isInLiquidOrWeb()
                        && !Unfair.moduleManager.getModule(Stuck.class).isEnabled()
                        && (!longJump.isEnabled() || !longJump.canStartJump())) {
                    Entity found = findTarget();
                    if (found != null) {
                        target = found;
                        boolean grounded = mc.thePlayer.onGround;
                        predictSprinting = mc.thePlayer.isSprinting();
                        jumpResetTicks = grounded ? 3 : 0;
                        BackTrack backTrack = (BackTrack) Unfair.moduleManager.modules.get(BackTrack.class);

                        if (!predictSprinting && !(backTrack.isEnabled() && backTrack.isBackTracking)) {
                            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
                            Unfair.delayManager.delayedPacket.offer(packet);
                            event.setCancelled(true);
                            delaying = true;
                            KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
                            if (killAura != null) killAura.attackDisabled = true;
                        }
                        predictTick = 0;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    @Override
    public void onEnabled() {
        dbg("Disable your JumpReset module when using prediction velocity.");
        resetPredict();
    }

    @Override
    public void onDisabled() {
        if (delaying) releaseDelay();
        resetPredict();
    }

    private Entity findTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }
        EntityPlayer nearest = null;
        double nearestDist = 3.0;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.deathTime > 0) continue;
            double dist = player.getDistanceToEntity(mc.thePlayer);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    private float[] getRotationsToEntity(Entity entity) {
        RotationUtil.RotationVec rv = RayCastUtil.calculateRotationToEntity(entity);
        return new float[]{rv.x, rv.y};
    }

    private void doReduce() {
        if (target == null) return;
        if (cn.unfair.util.BadPacketUtil.bad()) return;
        PacketUtil.sendPacket(new C0APacketAnimation());
        PacketUtil.sendPacket(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
    }

    private void doJumpReset() {
        if (mc.thePlayer.onGround && !Velocity.isInLiquidOrWeb() && !mc.thePlayer.isPotionActive(Potion.jump)) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    private void releaseDelay() {
        Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null) killAura.attackDisabled = false;
        delaying = false;
    }

    private void resetPredict() {
        predictTick = -1;
        target = null;
        jumpResetTicks = 0;
        delaying = false;
        rotating = false;
    }

}
