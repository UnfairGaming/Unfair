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
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.module.modules.movement.Stuck;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import static cn.unfair.management.BadPacketManager.bad;
import static cn.unfair.util.player.PlayerUtil.isInLiquidOrWeb;

public class GrimReduceVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty maxAirTicks = new IntProperty("max-air-ticks", 12, 4, 20);
    public final IntProperty reach = new IntProperty("reach", 3, 2, 4);

    private boolean suspending;
    private int suspendTicks;
    private boolean knockback;

    public GrimReduceVelocity() {
        super("GrimReduce");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) return;
        if (!(event.getPacket() instanceof S12PacketEntityVelocity packet)) return;
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;
        if (suspending || !canStartReduce()) return;

        if (mc.thePlayer.onGround) {
            knockback = true;
        } else {
            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
            Unfair.delayManager.delayedPacket.offer(packet);
            event.setCancelled(true);
            suspending = true;
            suspendTicks = 0;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            reset();
            return;
        }
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        if (suspending) {
            if (++suspendTicks >= maxAirTicks.getValue() || mc.thePlayer.onGround) {
                Entity target = findTarget();
                boolean canReduce = mc.thePlayer.onGround
                        && mc.thePlayer.isSprinting()
                        && isValidTarget(target)
                        && !bad();

                release();

                if (canReduce) {
                    doReduce(target);
                } else if (mc.thePlayer.onGround && mc.thePlayer.isSprinting()
                        && (target == null || target instanceof EntityPlayer)) {
                    mc.thePlayer.setSprinting(false);
                }
            }
        } else if (knockback) {
            knockback = false;
            if (bad() || isInLiquidOrWeb() || !mc.thePlayer.isSprinting()) return;
            reduceIfPossible(findTarget());
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || !isEnabled() || !suspending) return;
        mc.thePlayer.movementInput.moveForward = 1.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    @Override
    public void onEnabled() {
        reset();
    }

    @Override
    public void onDisabled() {
        release();
    }

    private boolean canStartReduce() {
        if (isInLiquidOrWeb()) return false;

        Stuck stuck = (Stuck) Unfair.moduleManager.modules.get(Stuck.class);
        if (stuck != null && stuck.isEnabled()) return false;

        LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
        return longJump == null || !longJump.isEnabled() || !longJump.canStartJump();
    }

    private void reduceIfPossible(Entity target) {
        if (isValidTarget(target)) {
            doReduce(target);
        }
    }

    private void doReduce(Entity target) {
        AttackOrder.sendFixedPacketAttackAndSwing(target);
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
    }

    private void release() {
        if (Unfair.delayManager.getDelayModule() == DelayModules.VELOCITY) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
        }
        reset();
    }

    private void reset() {
        suspending = false;
        suspendTicks = 0;
        knockback = false;
    }

    private Entity findTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }
        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(
                new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch),
                reach.getValue().floatValue());
        if (result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                && result.entityHit instanceof EntityPlayer) {
            return result.entityHit;
        }
        return null;
    }

    private boolean isValidTarget(Entity entity) {
        return entity instanceof EntityPlayer
                && entity.isEntityAlive()
                && entity != mc.thePlayer;
    }
}