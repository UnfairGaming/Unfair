package cn.unfair.module.modules.movement;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.StrafeEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.KeyBindUtil;
import cn.unfair.util.MoveUtil;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.TeamUtil;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class Fly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "POLAR"});
    public final FloatProperty hSpeed = new FloatProperty("horizontal-speed", 1.0F, 0.0F, 100.0F);
    public final FloatProperty vSpeed = new FloatProperty("vertical-speed", 1.0F, 0.0F, 100.0F);
    private final Deque<Packet<?>> polarS32Packets = new ConcurrentLinkedDeque<>();
    private double verticalMotion = 0.0;
    private Entity polarBoat = null;
    private boolean polarAttackedBoat = false;
    private boolean polarMovementCheckDisabled = false;

    public Fly() {
        super("Fly", false);
    }

    private boolean shouldUseVanillaFly() {
        return this.mode.getValue() == 0 || this.polarMovementCheckDisabled;
    }

    private void resetPolar() {
        this.polarBoat = null;
        this.polarAttackedBoat = false;
        this.polarMovementCheckDisabled = false;
        this.polarS32Packets.clear();
    }

    private void flushPolarPackets() {
        Packet<?> packet;
        while ((packet = this.polarS32Packets.poll()) != null) {
            PacketUtil.receivePacketNoEvent(packet);
        }
    }

    private boolean isPolarBoatGone() {
        return this.polarBoat == null
                || this.polarBoat.isDead
                || mc.theWorld == null
                || !TeamUtil.isEntityLoaded(this.polarBoat);
    }

    private boolean isPolarDismountPacket(Packet<?> packet) {
        return this.mode.getValue() == 1
                && packet instanceof C0BPacketEntityAction
                && ((C0BPacketEntityAction) packet).getAction() == C0BPacketEntityAction.Action.START_SNEAKING
                && (mc.thePlayer.ridingEntity instanceof EntityBoat || this.polarBoat != null);
    }

    private void updatePolarState() {
        if (this.mode.getValue() != 1 || mc.thePlayer == null) {
            return;
        }

        if (!this.polarAttackedBoat && mc.thePlayer.ridingEntity instanceof EntityBoat) {
            this.polarBoat = mc.thePlayer.ridingEntity;
            PacketUtil.sendPacket(new C02PacketUseEntity(this.polarBoat, C02PacketUseEntity.Action.ATTACK));
            this.polarAttackedBoat = true;
        }

        if (this.polarAttackedBoat && !this.polarMovementCheckDisabled && this.isPolarBoatGone()) {
            this.polarMovementCheckDisabled = true;
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.shouldUseVanillaFly()) {
            if (mc.thePlayer.posY % 1.0 != 0.0) {
                mc.thePlayer.motionY = this.verticalMotion;
            }
            MoveUtil.setSpeed(0.0);
            event.setFriction((float) MoveUtil.getBaseMoveSpeed() * this.hSpeed.getValue());
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.updatePolarState();
            if (!this.shouldUseVanillaFly()) {
                return;
            }
            this.verticalMotion = 0.0;
            if (mc.currentScreen == null) {
                if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                    this.verticalMotion = this.verticalMotion + this.vSpeed.getValue().doubleValue() * 0.42F;
                }
                if (KeyBindUtil.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode())) {
                    this.verticalMotion = this.verticalMotion - this.vSpeed.getValue().doubleValue() * 0.42F;
                }
                KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && event.getType() == EventType.SEND && this.isPolarDismountPacket(event.getPacket())) {
            event.setCancelled(true);
            return;
        }

        if (this.isEnabled()
                && this.mode.getValue() == 1
                && this.polarAttackedBoat
                && event.getType() == EventType.RECEIVE
                && event.getPacket() instanceof S32PacketConfirmTransaction) {
            this.polarS32Packets.offer(event.getPacket());
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.resetPolar();
    }

    @Override
    public void onDisabled() {
        this.flushPolarPackets();
        this.resetPolar();
        if (mc.thePlayer != null) {
            mc.thePlayer.motionY = 0.0;
        }
        MoveUtil.setSpeed(0.0);
        KeyBindUtil.updateKeyState(mc.gameSettings.keyBindSneak.getKeyCode());
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
