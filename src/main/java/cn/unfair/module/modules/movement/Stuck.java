package cn.unfair.module.modules.movement;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.module.Module;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.PacketUtil;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemSoup;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class Stuck extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Vanilla", "Heypixel"});

    public final IntProperty stuckTicks = new IntProperty("Stuck Ticks", 10, 1, 100);
    private double savedMotionX;
    private double savedMotionY;
    private double savedMotionZ;
    private int tick;
    private boolean using = false;

    private int stage = 0;
    private Packet heypixelPacket;
    private float lastYaw;
    private float lastPitch;
    private boolean tryDisable = false;
    private final Queue<Packet> heypixelPackets = new ConcurrentLinkedQueue<>();

    public Stuck() {
        super("Stuck", false, false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mc.thePlayer != null) {
            if (enabled) {
                super.setEnabled(true);
            } else if (this.mode.getModeString().equals("Heypixel")) {
                if (this.stage == 3) {
                    super.setEnabled(false);
                } else {
                    this.tryDisable = true;
                }
            } else {
                super.setEnabled(false);
            }
        }
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            if (this.mode.getModeString().equals("Heypixel")) {
                this.stage = 0;
                this.heypixelPacket = null;
                this.lastYaw = mc.thePlayer.rotationYaw;
                this.lastPitch = mc.thePlayer.rotationPitch;
                this.tryDisable = false;
            } else {
                tick = 0;
                using = true;
                savedMotionX = mc.thePlayer.motionX;
                savedMotionY = mc.thePlayer.motionY;
                savedMotionZ = mc.thePlayer.motionZ;
            }
        }
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && this.mode.getModeString().equals("Vanilla")) {
            using = false;
            Unfair.blinkManager.setBlinkState(false, BlinkModules.BLINK);
            mc.thePlayer.motionX = savedMotionX;
            mc.thePlayer.motionZ = savedMotionZ;
            mc.thePlayer.motionY = savedMotionY;
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
            mc.timer.timerSpeed = 1.0F;
        }
    }


    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;

        if (this.mode.getModeString().equals("Heypixel")) {
            handleHeypixelPacket(event);
        } else {
            handleVanillaPacket(event);
        }
    }

    private void handleHeypixelPacket(PacketEvent event) {
        if (event.getType() == EventType.SEND) {
            Packet<?> pkt = event.getPacket();
            if (pkt instanceof C03PacketPlayer) {
                event.setCancelled(true);
            } else if (pkt instanceof C00PacketKeepAlive || pkt instanceof C0FPacketConfirmTransaction) {
                this.heypixelPackets.offer(pkt);
                event.setCancelled(true);
            } else if (pkt instanceof C08PacketPlayerBlockPlacement || pkt instanceof C07PacketPlayerDigging) {
                this.heypixelPacket = pkt;
                this.stage = 1;
                event.setCancelled(true);
            }
        } else if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S08PacketPlayerPosLook) {
                while (!this.heypixelPackets.isEmpty()) {
                    PacketUtil.sendPacketNoEvent(this.heypixelPackets.poll());
                }
                this.stage = 3;
                this.setEnabled(false);
            }
        }
    }

    private void handleVanillaPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S12PacketEntityVelocity s12PacketEntityVelocity) {
                if (s12PacketEntityVelocity.getEntityID() == mc.thePlayer.getEntityId()) {
                    Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
                    tick = this.stuckTicks.getValue();
                    Unfair.delayManager.delayedPacket.offer(s12PacketEntityVelocity);
                    event.setCancelled(true);
                }
            }
        }
    }


    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;

        if (this.mode.getModeString().equals("Heypixel")) {
            handleHeypixelUpdate(event);
        } else {
            Unfair.blinkManager.setBlinkState(true, BlinkModules.BLINK);
            KeyBinding.unPressAllKeys();
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;
        }
    }

    private void handleHeypixelUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionZ = 0.0;
            mc.thePlayer.motionY = 0.0;

            if (this.stage == 1) {
                this.stage = 2;
                float rotYaw = mc.thePlayer.rotationYaw;
                float rotPitch = mc.thePlayer.rotationPitch;
                if (this.shouldRotate() && (this.lastYaw != rotYaw || this.lastPitch != rotPitch)) {
                    PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C05PacketPlayerLook(rotYaw, rotPitch, mc.thePlayer.onGround));
                    while (!this.heypixelPackets.isEmpty()) {
                        PacketUtil.sendPacketNoEvent(this.heypixelPackets.poll());
                    }
                    this.lastYaw = rotYaw;
                    this.lastPitch = rotPitch;
                }
                PacketUtil.sendPacketNoEvent(this.heypixelPacket);
            }

            if (this.tryDisable) {
                PacketUtil.sendPacketNoEvent(new C03PacketPlayer.C04PacketPlayerPosition(
                        mc.thePlayer.posX + 1337.0, mc.thePlayer.posY, mc.thePlayer.posZ + 1337.0, mc.thePlayer.onGround));
                while (!this.heypixelPackets.isEmpty()) {
                    PacketUtil.sendPacketNoEvent(this.heypixelPackets.poll());
                }
                this.tryDisable = false;
            }
        }
    }

    private boolean shouldRotate() {
        if (this.heypixelPacket instanceof C08PacketPlayerBlockPlacement) {
            net.minecraft.item.ItemStack item = mc.thePlayer.getHeldItem();
            return item != null && !(item.getItem() instanceof ItemSoup) && !(item.getItem() instanceof ItemBow);
        } else if (this.heypixelPacket instanceof C07PacketPlayerDigging) {
            C07PacketPlayerDigging playerDigging = (C07PacketPlayerDigging) this.heypixelPacket;
            return playerDigging.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM
                    && mc.thePlayer.getItemInUse() != null
                    && mc.thePlayer.getItemInUse().getItem() instanceof ItemBow;
        }
        return false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.mode.getModeString().equals("Heypixel")) return;

        if (using && event.type() == EventType.PRE) {
            int ticks = this.stuckTicks.getValue();
            if (tick == ticks) {
                this.setEnabled(false);
                using = true;
            }
            if (tick == ticks + 1) {
                this.setEnabled(true);
                tick = 0;
            }
            tick++;
        }
    }


    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()) return;

        mc.thePlayer.movementInput.moveForward = 0.0f;
        mc.thePlayer.movementInput.moveStrafe = 0.0f;
        mc.thePlayer.movementInput.jump = false;
        mc.thePlayer.movementInput.sneak = false;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled()) return;
        if (this.mode.getModeString().equals("Heypixel")) return;

        mc.thePlayer.motionX = 0.0;
        mc.thePlayer.motionY = 0.0;
        mc.thePlayer.motionZ = 0.0;
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled()) return;
        if (this.mode.getModeString().equals("Heypixel")) return;

        event.setForward(0.0f);
        event.setStrafe(0.0f);
    }
}
