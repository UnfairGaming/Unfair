package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.management.BadPacketManager;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.Autoblock;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.module.modules.movement.Stuck;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.client.ChatUtil;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.potion.Potion;

public class HypixelVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty debug = new BooleanProperty("Debug", false);
    public final BooleanProperty jump = new BooleanProperty("Jump", true);
    public final BooleanProperty reduce = new BooleanProperty("Reduce", true);
    public final BooleanProperty delay = new BooleanProperty("Delay", true);
    private boolean delayFlag;
    private boolean knockback;
    private boolean jumpFlag;

    public HypixelVelocity() {
        super("Hypixel");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || !isEnabled()) return;
        if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
        if (!(event.getPacket() instanceof S12PacketEntityVelocity packet)
                || packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        if (isBlockedState() || canStartLongJump()) return;

        knockback = true;
        if (this.delay.getValue() && !delayFlag && !canDelay()) {
            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
            Unfair.delayManager.delayedPacket.offer(packet);
            event.setCancelled(true);
            delayFlag = true;
            if (debug.getValue()) {
                ChatUtil.dbg("Delay is activated");
            }
        }
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || !isEnabled()) return;
        if (!event.isCancelled()) {
            this.jumpFlag = this.jump.getValue() && event.getY() > 0.0;
        } else {
            knockback = false;
            return;
        }
        knockback = true;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            reset();
            return;
        }
        if (!isEnabled() || event.getType() != EventType.PRE) return;

        if (!this.delay.getValue() && this.delayFlag) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
            this.delayFlag = false;
        }

        if (delayFlag && shouldReleaseDelay()) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
            delayFlag = false;
            if (debug.getValue()) {
                ChatUtil.dbg("Delay is not activated");
            }
        }

        if (this.reduce.getValue() && knockback) {
            reduceVelocity();
        } else if (!this.reduce.getValue()) {
            knockback = false;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.jump.getValue() && this.jumpFlag) {
            this.jumpFlag = false;
            if (mc.thePlayer.onGround && mc.thePlayer.isSprinting() && !mc.thePlayer.isPotionActive(Potion.jump) && !Velocity.isInLiquidOrWeb()) {
                mc.thePlayer.movementInput.jump = true;
                if (debug.getValue()) {
                    ChatUtil.dbg("Jump");
                }
            }
        } else if (!this.jump.getValue()) {
            this.jumpFlag = false;
        }
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
        if (delayFlag && Unfair.delayManager != null
                && Unfair.delayManager.getDelayModule() == DelayModules.VELOCITY) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
        }
        reset();
    }

    public boolean isDelaying() {
        return delayFlag;
    }

    private boolean canDelay() {
        Autoblock autoblock = (Autoblock) Unfair.moduleManager.getModule(Autoblock.class);
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        return autoblock != null && autoblock.isActive()
                || mc.thePlayer.onGround
                && (killAura == null || !killAura.isEnabled() || !killAura.shouldAutoBlock());
    }

    private boolean shouldReleaseDelay() {
        return canDelay()
                || isBlockedState()
                || Unfair.delayManager.getDelay() >= 3;
    }

    private boolean canStartLongJump() {
        LongJump longJump = (LongJump) Unfair.moduleManager.getModule(LongJump.class);
        return longJump != null && longJump.isEnabled() && longJump.canStartJump();
    }

    private boolean isBlockedState() {
        Stuck stuck = (Stuck) Unfair.moduleManager.getModule(Stuck.class);
        return Velocity.isInLiquidOrWeb() || (stuck != null && stuck.isEnabled());
    }

    private void reduceVelocity() {
        if (BadPacketManager.bad() || isBlockedState()
                || !MoveUtil.isForwardPressed() || !mc.thePlayer.isSprinting()) {
            return;
        }

        Entity target = findTarget();
        if (!(target instanceof EntityPlayer)) {
            knockback = false;
            return;
        }

        AttackOrder.sendFixedPacketAttackAndSwing(target);
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
        knockback = false;
        if (debug.getValue()) {
            ChatUtil.dbg("Reduce 40%");
        }
    }

    private Entity findTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }

        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(
                new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 3.0F);
        if (result != null
                && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                && result.entityHit instanceof EntityPlayer) {
            return result.entityHit;
        }
        return null;
    }

    private void reset() {
        delayFlag = false;
        knockback = false;
        jumpFlag = false;
    }
}
