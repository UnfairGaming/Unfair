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
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;

import static cn.unfair.module.modules.combat.Velocity.isInLiquidOrWeb;
import static cn.unfair.management.BadPacketManager.bad;

public class GrimReduceVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty maxAirTicks = new IntProperty("Max Air Ticks", 12, 4, 20);
    public final IntProperty reach = new IntProperty("Reach", 3, 2, 4);

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
        if (suspending) return;
        if (!isPlayerKnockback()) return;
        if (isBlockedState()) return;

        Stuck stuck = (Stuck) Unfair.moduleManager.modules.get(Stuck.class);
        if (stuck != null && stuck.isEnabled()) return;
        LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
        if (longJump != null && longJump.isEnabled() && longJump.canStartJump()) return;

        if (!mc.thePlayer.onGround) {
            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
            Unfair.delayManager.delayedPacket.offer(packet);
            event.setCancelled(true);
            suspending = true;
            suspendTicks = 0;
        } else {
            knockback = true;
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
            suspendTicks++;
            boolean timeout = suspendTicks >= maxAirTicks.getValue();
            if (mc.thePlayer.onGround || timeout) {
                boolean grounded = mc.thePlayer.onGround;
                Entity target = findTarget();
                boolean canReduce = grounded
                        && mc.thePlayer.isSprinting()
                        && isValidTarget(target)
                        && !isBlockedState()
                        && !bad();

                release();

                if (canReduce) {
                    doReduce(target);
                } else if (grounded && mc.thePlayer.isSprinting()) {
                    mc.thePlayer.setSprinting(false);
                }
            }
            return;
        }

        if (knockback) {
            knockback = false;
            if (bad() || isBlockedState()) return;
            if (!mc.thePlayer.isSprinting()) return;
            Entity target = findTarget();
            if (isValidTarget(target)) {
                doReduce(target);
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!isEnabled() || !suspending) return;
        mc.thePlayer.movementInput.moveForward = 1.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        Unfair.delayManager.discardDelayedPackets(DelayModules.VELOCITY);
        reset();
    }

    @Override
    public void onEnabled() {
        reset();
    }

    @Override
    public void onDisabled() {
        release();
        reset();
    }

    private void doReduce(Entity target) {
        if (!(target instanceof EntityPlayer) || isBlockedState()) return;
        AttackOrder.sendFixedPacketAttackAndSwing(target);
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
    }

    private void release() {
        if (Unfair.delayManager.getDelayModule() == DelayModules.VELOCITY) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
        }
        suspending = false;
        suspendTicks = 0;
    }

    private void reset() {
        suspending = false;
        suspendTicks = 0;
        knockback = false;
    }

    public boolean isSuspending() {
        return this.suspending;
    }

    private boolean isPlayerKnockback() {
        double radius = reach.getValue() + 2.0;
        double radiusSq = radius * radius;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || !player.isEntityAlive()) continue;
            if (mc.thePlayer.getDistanceSqToEntity(player) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlockedState() {
        return mc.thePlayer.isOnLadder() || isInLiquidOrWeb() || isOnFireBlock();
    }

    private boolean isOnFireBlock() {
        double x = mc.thePlayer.posX;
        double z = mc.thePlayer.posZ;
        return mc.theWorld.getBlockState(new BlockPos(x, mc.thePlayer.posY, z)).getBlock() == Blocks.fire
                || mc.theWorld.getBlockState(new BlockPos(x, mc.thePlayer.posY - 0.2, z)).getBlock() == Blocks.fire;
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
