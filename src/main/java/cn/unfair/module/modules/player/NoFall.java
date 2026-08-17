package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.StrafeEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.util.AxisAlignedBB;

public class NoFall extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Packet", "Blink", "NoGround", "Spoof", "Heypixel"});
    public final FloatProperty distance = new FloatProperty("Distance", 3.0F, 0.0F, 20.0F);
    public final IntProperty delay = new IntProperty("Delay", 0, 0, 10000);
    private final TimerUtil packetDelayTimer = new TimerUtil();
    private final TimerUtil scoreboardResetTimer = new TimerUtil();
    private boolean slowFalling = false;
    private boolean lastOnGround = false;
    private boolean heypixelLagged = false;
    private boolean heypixelShouldHandleFall = false;
    private boolean heypixelShouldSendLagPacket = false;
    private boolean heypixelShouldJump = false;

    public NoFall() {
        super("NoFall", false);
    }

    @Override
    public void onEnabled() {
        this.heypixelResetState();
    }

    private boolean heypixelShouldBlockJump() {
        return this.heypixelShouldHandleFall || this.heypixelShouldJump;
    }

    private void heypixelResetState() {
        this.heypixelLagged = false;
        this.heypixelShouldHandleFall = false;
        this.heypixelShouldSendLagPacket = false;
        this.heypixelShouldJump = false;
    }

    private boolean canTrigger() {
        return this.scoreboardResetTimer.hasTimeElapsed(3000) && this.packetDelayTimer.hasTimeElapsed(this.delay.getValue().longValue());
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE && event.getPacket() instanceof S08PacketPlayerPosLook) {
            if (this.mode.getValue() == 4) {
                if (this.heypixelShouldHandleFall) {
                    this.heypixelLagged = true;
                }
            } else {
                this.onDisabled();
            }
        } else if (this.isEnabled() && event.getType() == EventType.SEND && !event.isCancelled()) {
            if (event.getPacket() instanceof C03PacketPlayer packet) {
                switch (this.mode.getValue()) {
                    case 0:
                        if (this.slowFalling) {
                            this.slowFalling = false;
                            mc.timer.timerSpeed = 1.0F;
                        } else if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                this.slowFalling = true;
                                mc.timer.timerSpeed = 0.5F;
                            }
                        }
                        break;
                    case 1:
                        boolean allowed = !mc.thePlayer.isOnLadder() && !mc.thePlayer.capabilities.allowFlying && mc.thePlayer.hurtTime == 0;
                        if (Unfair.blinkManager.getBlinkingModule() != BlinkModules.NO_FALL) {
                            if (this.lastOnGround
                                    && !packet.isOnGround()
                                    && allowed
                                    && PlayerUtil.canFly(this.distance.getValue().intValue())
                                    && mc.thePlayer.motionY < 0.0) {
                                Unfair.blinkManager.setBlinkState(false, Unfair.blinkManager.getBlinkingModule());
                                Unfair.blinkManager.setBlinkState(true, BlinkModules.NO_FALL);
                            }
                        } else if (!allowed) {
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed player check!&r", Unfair.clientName, this.getName()));
                        } else if (PlayerUtil.checkInWater(mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0))) {
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            ChatUtil.sendFormatted(String.format("%s%s: &cFailed void check!&r", Unfair.clientName, this.getName()));
                        } else if (packet.isOnGround()) {
                            for (Packet<?> blinkedPacket : Unfair.blinkManager.blinkedPackets) {
                                if (blinkedPacket instanceof C03PacketPlayer) {
                                    blinkedPacket.setOnGround(true);
                                }
                            }
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
                            this.packetDelayTimer.reset();
                        }
                        this.lastOnGround = packet.isOnGround() && allowed && this.canTrigger();
                        break;
                    case 2:
                        packet.setOnGround(false);
                        break;
                    case 3:
                        if (!packet.isOnGround()) {
                            AxisAlignedBB aabb = mc.thePlayer.getEntityBoundingBox().expand(2.0, 0.0, 2.0);
                            if (PlayerUtil.canFly(this.distance.getValue())
                                    && !PlayerUtil.checkInWater(aabb)
                                    && this.canTrigger()) {
                                this.packetDelayTimer.reset();
                                packet.setOnGround(true);
                                mc.thePlayer.fallDistance = 0.0F;
                            }
                        }
                        break;
                    case 4:
                        if (mc.isSingleplayer()) {
                            break;
                        }
                        if (!this.heypixelShouldHandleFall
                                && mc.thePlayer.fallDistance > this.distance.getValue()
                                && !mc.thePlayer.onGround) {
                            this.heypixelShouldHandleFall = true;
                            this.heypixelLagged = false;
                            this.heypixelShouldSendLagPacket = false;
                        }
                        if (this.heypixelShouldHandleFall && mc.thePlayer.fallDistance < 3.0F) {
                            packet.setOnGround(false);
                            if (!this.heypixelShouldSendLagPacket) {
                                // Heypixel lag packet. 1.8.9 has no accept-teleportation packet,
                                // so only the fake position (X offset by -1000) is sent.
                                PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                                        mc.thePlayer.posX - 1000.0, mc.thePlayer.posY, mc.thePlayer.posZ, false));
                                this.heypixelShouldSendLagPacket = true;
                            }
                        }
                        if (this.heypixelShouldHandleFall && this.heypixelShouldSendLagPacket && !this.heypixelLagged) {
                            event.setCancelled(true);
                        }
                        break;
                }
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (event.type() == EventType.PRE) {
            if (ServerUtil.hasPlayerCountInfo()) {
                this.scoreboardResetTimer.reset();
            }
            if (this.mode.getValue() == 0 && this.slowFalling) {
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer(true));
                mc.thePlayer.fallDistance = 0.0F;
            }
        } else if (event.type() == EventType.POST && this.mode.getValue() == 4) {
            if (mc.isSingleplayer()) {
                return;
            }
            if (this.heypixelShouldBlockJump()) {
                mc.gameSettings.keyBindJump.pressed = false;
            }
            if (this.heypixelLagged && this.heypixelShouldHandleFall) {
                this.heypixelShouldJump = true;
                this.heypixelShouldHandleFall = false;
                this.heypixelLagged = false;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 4 && this.heypixelShouldBlockJump() && mc.thePlayer != null) {
            mc.thePlayer.movementInput.jump = false;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.mode.getValue() == 4 && mc.thePlayer != null
                && mc.thePlayer.onGround && this.heypixelShouldJump) {
            mc.thePlayer.jump();
            this.heypixelShouldJump = false;
        }
    }

    @Override
    public void onDisabled() {
        this.lastOnGround = false;
        Unfair.blinkManager.setBlinkState(false, BlinkModules.NO_FALL);
        this.heypixelResetState();
        if (this.slowFalling) {
            this.slowFalling = false;
            mc.timer.timerSpeed = 1.0F;
        }
    }

    @Override
    public void verifyValue(String mode) {
        if (this.isEnabled()) {
            this.onDisabled();
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
