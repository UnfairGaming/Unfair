package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.KnockbackEvent;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.module.modules.movement.Stuck;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.MoveUtil;
import cn.unfair.util.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.potion.Potion;

public class HypixelVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final int DELAY_TICKS = 3;
    private static final int START_BLINK_HURT_TIME = 2;
    private static final int FORCE_UNBLINK_TICK = 6;
    private static final double ATTACK_SLOWDOWN = 0.6D;

    private boolean allowNext = true;
    private boolean delayFlag = false;
    private boolean jumpFlag = false;
    private boolean knockback = false;
    private boolean blinkingVelocity = false;
    private boolean blinkScheduled = false;
    private int knockbackTimer = -1;
    private int blinkTicks = 0;

    public HypixelVelocity() {
        super("Hypixel");
    }

    private Velocity velocity() {
        return (Velocity) Unfair.moduleManager.getModule(Velocity.class);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        Velocity velocity = velocity();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (velocity == null || !isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) return;

        if (event.getPacket() instanceof S19PacketEntityStatus packet) {
            Entity entity = packet.getEntity(mc.theWorld);
            if (entity == mc.thePlayer && packet.getOpCode() == 2) {
                allowNext = false;
            }
            return;
        }

        if (!(event.getPacket() instanceof S12PacketEntityVelocity packet)) return;
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
        if (delayFlag
                || Velocity.isInLiquidOrWeb()
                || Unfair.moduleManager.getModule(Stuck.class).isEnabled()
                || (allowNext)
                || (longJump.isEnabled() && longJump.canStartJump())) {
            return;
        }

        if (!mc.thePlayer.onGround) {
            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
            Unfair.delayManager.delayedPacket.offer(packet);
            event.setCancelled(true);
            delayFlag = true;
        }
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!isEnabled() || event.isCancelled()) return;
        if (allowNext) return;

        allowNext = true;
        knockback = true;
        knockbackTimer = 0;
        blinkScheduled = false;

        if (mc.thePlayer.onGround && event.getY() > 0.0D) {
            jumpFlag = true;
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!isEnabled() || !jumpFlag) return;

        jumpFlag = false;
        if (mc.thePlayer.onGround
                && MoveUtil.isForwardPressed()
                && mc.thePlayer.isSprinting()
                && !mc.thePlayer.isPotionActive(Potion.jump)
                && !Velocity.isInLiquidOrWeb()) {
            mc.thePlayer.movementInput.jump = true;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            reset(false);
            return;
        }
        if (!isEnabled()) return;

        if (event.getType() == EventType.PRE) {
            handleBlink();
        } else if (event.getType() == EventType.POST) {
            handleDelayRelease();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    @Override
    public void onEnabled() {
        reset(false);
    }

    @Override
    public void onDisabled() {
        reset(true);
    }

    private void handleBlink() {
        if (knockbackTimer >= 0) {
            knockbackTimer++;
        }

        if (blinkingVelocity) {
            blinkTicks++;
            if (blinkTicks >= FORCE_UNBLINK_TICK) {
                releaseVelocityBlink();
            }
            return;
        }

        if (blinkScheduled) {
            if (knockbackTimer >= FORCE_UNBLINK_TICK) {
                blinkScheduled = false;
                knockback = false;
                knockbackTimer = -1;
                return;
            }

            startVelocityBlink();
        }

        if (knockback && mc.thePlayer.hurtTime <= START_BLINK_HURT_TIME) {
            startVelocityBlink();
            blinkScheduled = true;
        }
    }

    private void handleDelayRelease() {
        if (!delayFlag) return;
        if (Velocity.isInLiquidOrWeb()
                || Unfair.delayManager.getDelay() >= DELAY_TICKS
                || mc.thePlayer.onGround) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
            delayFlag = false;
            jumpFlag = true;
        }
    }

    private void startVelocityBlink() {
        if (Unfair.blinkManager.setBlinkState(true, BlinkModules.VELOCITY)) {
            blinkingVelocity = true;
            blinkScheduled = false;
            blinkTicks = 0;
        }
    }

    private void releaseVelocityBlink() {
        if (!blinkingVelocity) return;
        if (Unfair.blinkManager.getBlinkingModule() != BlinkModules.VELOCITY) {
            blinkingVelocity = false;
            blinkScheduled = false;
            knockback = false;
            knockbackTimer = -1;
            blinkTicks = 0;
            return;
        }

        Unfair.blinkManager.blinking = false;
        for (Packet<?> packet : Unfair.blinkManager.blinkedPackets) {
            if (packet instanceof C02PacketUseEntity) {
                mc.thePlayer.motionX *= ATTACK_SLOWDOWN;
                mc.thePlayer.motionZ *= ATTACK_SLOWDOWN;
                ChatUtil.dbg("reduce");
            }
            PacketUtil.sendPacketNoEvent(packet);
        }
        Unfair.blinkManager.blinkedPackets.clear();
        Unfair.blinkManager.blinkModule = BlinkModules.NONE;

        blinkingVelocity = false;
        blinkScheduled = false;
        knockback = false;
        knockbackTimer = -1;
        blinkTicks = 0;
    }

    private void reset(boolean flush) {
        boolean hadDelay = delayFlag && Unfair.delayManager.getDelayModule() == DelayModules.VELOCITY;
        boolean hadBlink = blinkingVelocity && Unfair.blinkManager.getBlinkingModule() == BlinkModules.VELOCITY;

        if (flush && hadDelay) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
        }

        if (flush && hadBlink) {
            releaseVelocityBlink();
        } else if (hadBlink) {
            Unfair.blinkManager.blinking = false;
            Unfair.blinkManager.blinkedPackets.clear();
            Unfair.blinkManager.blinkModule = BlinkModules.NONE;
        }

        allowNext = true;
        delayFlag = false;
        jumpFlag = false;
        knockback = false;
        blinkScheduled = false;
        knockbackTimer = -1;
        blinkTicks = 0;
        blinkingVelocity = false;
    }
}
