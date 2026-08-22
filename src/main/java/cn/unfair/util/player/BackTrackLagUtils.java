package cn.unfair.util.player;

import cn.unfair.events.PacketEvent;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;


public final class BackTrackLagUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long DEFAULT_TIMER_DELAY = 100L;
    private static final long BLINK_DELAY = 9999999L;
    private static final Queue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
    private static final TimerUtil enabledTimer = new TimerUtil();
    private static boolean enabled;
    private static long delayAmount;
    private static boolean post;

    private BackTrackLagUtils() {
    }

    public static void onPacket(PacketEvent event, boolean outgoing) {
        if (!event.isCancelled() && enabled && shouldHandlePacket(event.getPacket())) {
            event.setCancelled(true);
            packets.add(new TimedPacket(event.getPacket(), outgoing));
        }
    }

    public static void onPreTick() {
        if (!post) {
            sendPackets();
        }
    }

    public static void onPostTick() {
        if (post) {
            sendPackets();
        }
    }

    public static void spoof(int amount, boolean regular, boolean velocity, boolean teleports, boolean players, boolean action, boolean movement) {
        enabledTimer.reset();
        PacketType.REGULAR.enabled = regular;
        PacketType.VELOCITY.enabled = velocity;
        PacketType.TELEPORTS.enabled = teleports;
        PacketType.PLAYERS.enabled = players;
        PacketType.ACTION.enabled = action;
        PacketType.MOVEMENT.enabled = movement;
        post = true;
        delayAmount = Math.max(0L, amount);
    }

    public static void dispatch() {
        if (!packets.isEmpty()) {
            boolean wasEnabled = enabled;
            enabled = false;
            TimedPacket packet;
            while ((packet = packets.poll()) != null) {
                queue(packet);
            }
            enabled = wasEnabled;
        }
    }

    public static void disable() {
        enabled = false;
        enabledTimer.setTime(enabledTimer.getElapsedTime() - BLINK_DELAY);
    }

    private static void sendPackets() {
        if (!(enabled = !enabledTimer.hasTimeElapsed(DEFAULT_TIMER_DELAY) && !(mc.currentScreen instanceof GuiDownloadTerrain))) {
            dispatch();
            return;
        }

        enabled = false;
        releaseTimedOutPackets();
        enabled = true;
    }

    private static void releaseTimedOutPackets() {
        long now = System.currentTimeMillis();
        TimedPacket packet;
        while ((packet = packets.peek()) != null) {
            if (packet.millis + delayAmount > now) {
                break;
            }
            queue(packet);
            packets.poll();
        }
    }

    private static boolean shouldHandlePacket(Packet<?> packet) {
        return Arrays.stream(PacketType.values()).anyMatch(type -> type.enabled && type.containsPacket(packet.getClass()));
    }

    private static void queue(TimedPacket timedPacket) {
        if (timedPacket.outgoing) {
            PacketUtil.sendPacketNoEvent(timedPacket.packet);
        } else {
            PacketUtil.receivePacketNoEvent(timedPacket.packet);
        }
    }

    private enum PacketType {
        REGULAR(new Class[]{C0FPacketConfirmTransaction.class, C00PacketKeepAlive.class, S1CPacketEntityMetadata.class}),
        VELOCITY(new Class[]{S12PacketEntityVelocity.class, S27PacketExplosion.class}),
        TELEPORTS(new Class[]{S08PacketPlayerPosLook.class, S39PacketPlayerAbilities.class, S09PacketHeldItemChange.class}),
        PLAYERS(new Class[]{S13PacketDestroyEntities.class, S14PacketEntity.class, S14PacketEntity.S16PacketEntityLook.class, S14PacketEntity.S15PacketEntityRelMove.class, S14PacketEntity.S17PacketEntityLookMove.class, S18PacketEntityTeleport.class, S20PacketEntityProperties.class, S19PacketEntityHeadLook.class}),
        ACTION(new Class[]{C02PacketUseEntity.class, C0DPacketCloseWindow.class, C0EPacketClickWindow.class, C0CPacketInput.class, C0BPacketEntityAction.class, C08PacketPlayerBlockPlacement.class, C07PacketPlayerDigging.class, C09PacketHeldItemChange.class, C13PacketPlayerAbilities.class, C15PacketClientSettings.class, C16PacketClientStatus.class, C17PacketCustomPayload.class, C18PacketSpectate.class, C19PacketResourcePackStatus.class, C0APacketAnimation.class}),
        MOVEMENT(new Class[]{C03PacketPlayer.class, C03PacketPlayer.C04PacketPlayerPosition.class, C03PacketPlayer.C05PacketPlayerLook.class, C03PacketPlayer.C06PacketPlayerPosLook.class});

        private final Class<?>[] packetClasses;
        private boolean enabled;

        PacketType(Class<?>[] packetClasses) {
            this.packetClasses = packetClasses;
        }

        private boolean containsPacket(Class<?> packetClass) {
            return Arrays.asList(this.packetClasses).contains(packetClass);
        }
    }

    private static final class TimedPacket {
        private final Packet<?> packet;
        private final boolean outgoing;
        private final long millis = System.currentTimeMillis();

        private TimedPacket(Packet<?> packet, boolean outgoing) {
            this.packet = packet;
            this.outgoing = outgoing;
        }
    }
}
