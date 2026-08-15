package cn.unfair.util.player;

import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

/** Shared packet delay queue used by BackTrack modes. */
public final class BackTrackLagUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final long DEFAULT_TIMER_DELAY = 100L;
    private static final long BLINK_DELAY = 9999999L;
    private static final Queue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
    private static final TimerUtil enabledTimer = new TimerUtil();
    private static boolean enabled;
    private static long delayAmount;
    private static boolean post;

    // LiquidBounce-mode backtrack state
    private static final Queue<LbPacket> lbPackets = new ConcurrentLinkedQueue<>();
    private static final Queue<LbPosition> lbPositions = new ConcurrentLinkedQueue<>();
    private static EntityLivingBase lbTarget;
    private static Vec3 lbRealPosition;
    private static long lbDelay = 80L;
    private static int lbMinDelay = 80;
    private static int lbMaxDelay = 80;
    private static float lbDistanceMin = 2.0F;
    private static float lbDistanceMax = 3.0F;
    private static boolean lbEnabled;

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

    // ===== LiquidBounce-mode backtrack (full packet delay + distance-based release) =====

    public static void liquidbounceConfigure(boolean enabled, int minDelay, int maxDelay, float distanceMin, float distanceMax) {
        lbEnabled = enabled;
        lbMinDelay = minDelay;
        lbMaxDelay = maxDelay;
        lbDistanceMin = distanceMin;
        lbDistanceMax = distanceMax;
        if (!enabled) {
            liquidbounceClear();
            lbTarget = null;
            lbRealPosition = null;
        } else {
            rollLiquidbounceDelay();
        }
    }

    public static void liquidbounceSetTarget(EntityLivingBase target) {
        if (target != lbTarget) {
            liquidbounceClear();
            lbTarget = target;
            lbRealPosition = target == null
                    ? null
                    : new Vec3(target.serverPosX / 32.0D, target.serverPosY / 32.0D, target.serverPosZ / 32.0D);
            rollLiquidbounceDelay();
        }
    }

    public static EntityLivingBase getLiquidbounceTarget() {
        return lbTarget;
    }

    public static long getLiquidbounceDelay() {
        return lbDelay;
    }

    public static boolean isLiquidbounceBacktracking() {
        return lbEnabled && liquidbounceShouldBacktrack();
    }

    public static void liquidbounceOnPacket(PacketEvent event) {
        if (!lbEnabled || event.getType() != EventType.RECEIVE) {
            return;
        }

        Packet<?> packet = event.getPacket();

        if (mc.isSingleplayer()) {
            liquidbounceClear();
            return;
        }

        if (packet instanceof S02PacketChat) {
            return;
        }

        if (packet instanceof S06PacketUpdateHealth health && health.getHealth() <= 0.0F) {
            liquidbounceClear();
            return;
        }

        if (packet instanceof S13PacketDestroyEntities destroy && lbTarget != null) {
            for (int id : destroy.getEntityIDs()) {
                if (id == lbTarget.getEntityId()) {
                    liquidbounceClear();
                    lbTarget = null;
                    lbRealPosition = null;
                    return;
                }
            }
        }

        if (!liquidbounceShouldBacktrack()) {
            return;
        }

        if (lbTarget != null && lbRealPosition != null) {
            if (packet instanceof S14PacketEntity s14 && s14.getEntityId() == lbTarget.getEntityId()) {
                Vec3 pos = new Vec3(
                        lbRealPosition.xCoord + s14.func_149062_c() / 32.0D,
                        lbRealPosition.yCoord + s14.func_149061_d() / 32.0D,
                        lbRealPosition.zCoord + s14.func_149064_e() / 32.0D
                );
                lbRealPosition = pos;
                lbPositions.add(new LbPosition(pos, System.currentTimeMillis()));
            } else if (packet instanceof S18PacketEntityTeleport s18 && s18.getEntityId() == lbTarget.getEntityId()) {
                Vec3 pos = new Vec3(s18.getX() / 32.0D, s18.getY() / 32.0D, s18.getZ() / 32.0D);
                lbRealPosition = pos;
                lbPositions.add(new LbPosition(pos, System.currentTimeMillis()));
            }
        }

        event.setCancelled(true);
        lbPackets.add(new LbPacket(packet, System.currentTimeMillis()));
    }

    public static void liquidbounceOnTick() {
        if (!lbEnabled) {
            return;
        }

        if (mc.thePlayer == null || mc.theWorld == null || lbTarget == null || !liquidbounceShouldBacktrack()) {
            liquidbounceClear();
            return;
        }

        double distance = liquidbounceDistanceToEntityBox();
        if (distance >= lbDistanceMin && distance <= lbDistanceMax) {
            liquidbounceReleasePackets(lbDelay);
        } else {
            liquidbounceReleasePacketsRange();
        }
    }

    public static void liquidbounceClear() {
        LbPacket packet;
        while ((packet = lbPackets.poll()) != null) {
            PacketUtil.receivePacketNoEvent(packet.packet);
        }
        lbPositions.clear();
    }

    private static boolean liquidbounceShouldBacktrack() {
        return mc.thePlayer != null && mc.theWorld != null
                && lbTarget != null
                && !mc.thePlayer.isDead
                && lbTarget.isEntityAlive()
                && !(mc.currentScreen instanceof GuiGameOver)
                && mc.thePlayer.ticksExisted > 20;
    }

    private static void liquidbounceReleasePackets(long delay) {
        long now = System.currentTimeMillis();
        LbPacket packet;
        while ((packet = lbPackets.peek()) != null) {
            if (packet.time > now - delay) {
                break;
            }
            PacketUtil.receivePacketNoEvent(packet.packet);
            lbPackets.poll();
        }
        long cutoff = now - delay;
        lbPositions.removeIf(position -> position.time < cutoff);
    }

    private static void liquidbounceReleasePacketsRange() {
        long time = liquidbounceGetRangeTime();
        if (time == -1L) {
            liquidbounceClear();
            return;
        }
        LbPacket packet;
        while ((packet = lbPackets.peek()) != null) {
            if (packet.time > time) {
                break;
            }
            PacketUtil.receivePacketNoEvent(packet.packet);
            lbPackets.poll();
        }
        lbPositions.removeIf(position -> position.time < time);
    }

    private static long liquidbounceGetRangeTime() {
        if (lbTarget == null) {
            return -1L;
        }
        long found = -1L;
        for (LbPosition position : lbPositions) {
            double distance = liquidbounceDistanceAt(position.pos, lbTarget);
            if (distance >= lbDistanceMin && distance <= lbDistanceMax) {
                found = position.time;
                break;
            }
        }
        return found;
    }

    private static double liquidbounceDistanceToEntityBox() {
        return liquidbounceDistanceAt(new Vec3(lbTarget.posX, lbTarget.posY, lbTarget.posZ), lbTarget);
    }

    private static double liquidbounceDistanceAt(Vec3 position, EntityLivingBase target) {
        AxisAlignedBB box = target.getEntityBoundingBox().offset(
                position.xCoord - target.posX,
                position.yCoord - target.posY,
                position.zCoord - target.posZ
        );
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double x = MathHelper.clamp_double(eyes.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(eyes.zCoord, box.minZ, box.maxZ);
        return eyes.distanceTo(new Vec3(x, y, z));
    }

    private static void rollLiquidbounceDelay() {
        int min = Math.min(lbMinDelay, lbMaxDelay);
        int max = Math.max(lbMinDelay, lbMaxDelay);
        lbDelay = min == max ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
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

    private static final class LbPacket {
        final Packet<?> packet;
        final long time;

        LbPacket(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }
    }

    private static final class LbPosition {
        final Vec3 pos;
        final long time;

        LbPosition(Vec3 pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }
}
