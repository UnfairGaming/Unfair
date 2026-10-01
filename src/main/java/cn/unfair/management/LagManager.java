package cn.unfair.management;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.util.player.PacketUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C01PacketChatMessage;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;
import net.minecraft.util.Vec3;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class LagManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final Deque<LagPacket> packetQueue;
    public final Deque<LagPacket> incomingQueue;
    private int lagRangeDelay;
    private boolean backTrackLagging;
    private int backTrackDelay;
    private boolean flushing;
    private Vec3 lastPosition;

    public LagManager() {
        this.packetQueue = new ConcurrentLinkedDeque<>();
        this.incomingQueue = new ConcurrentLinkedDeque<>();
        this.lagRangeDelay = 0;
        this.backTrackLagging = false;
        this.backTrackDelay = 0;
        this.flushing = false;
        this.lastPosition = new Vec3(0.0, 0.0, 0.0);
    }

    private boolean isInWorld() {
        return mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null;
    }

    private boolean isDue(LagPacket lagPacket, long now) {
        if (this.lagRangeDelay > 0 && now - lagPacket.millis < this.lagRangeDelay) {
            return false;
        }
        return !(this.backTrackLagging && this.backTrackDelay > 0 && now - lagPacket.millis < this.backTrackDelay);
    }

    private void flushQueue() {
        if (!this.isInWorld()) {
            this.packetQueue.clear();
        } else {
            this.flushing = true;
            long now = System.currentTimeMillis();
            LagPacket lagPacket;
            while ((lagPacket = this.packetQueue.peek()) != null) {
                if (!this.isDue(lagPacket, now)) {
                    break;
                }
                this.packetQueue.poll();
                PacketUtil.sendPacketNoEvent(lagPacket.packet);
                if (lagPacket.packet instanceof C03PacketPlayer c03) {
                    if (c03.isMoving()) {
                        this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
                    }
                }
            }
            this.flushing = false;
        }
    }

    private void flushIncomingQueue() {
        if (!this.isInWorld()) {
            this.incomingQueue.clear();
        } else {
            long now = System.currentTimeMillis();
            LagPacket lagPacket;
            while ((lagPacket = this.incomingQueue.peek()) != null) {
                if (this.backTrackLagging && this.backTrackDelay > 0 && now - lagPacket.millis < this.backTrackDelay) {
                    break;
                }
                this.incomingQueue.poll();
                PacketUtil.receivePacketNoEvent(lagPacket.packet);
            }
        }
    }

    private void reset() {
        this.setDelay(0);
        this.backTrackLagging = false;
        this.backTrackDelay = 0;
        this.packetQueue.clear();
        this.incomingQueue.clear();
        this.flushing = false;
    }

    public boolean handlePacket(Packet<?> packet) {
        if (!this.isInWorld()) {
            this.packetQueue.clear();
            return false;
        }
        this.flushQueue();
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        } else if (this.lagRangeDelay > 0 || (this.backTrackLagging && this.backTrackDelay > 0)) {
            this.packetQueue.offer(new LagPacket(packet));
            return true;
        } else {
            if (packet instanceof C03PacketPlayer c03) {
                if (c03.isMoving()) {
                    this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
                }
            }
            return false;
        }
    }

    public boolean handleIncomingPacket(Packet<?> packet) {
        if (!this.isInWorld()) {
            this.incomingQueue.clear();
            return false;
        }
        if (!this.backTrackLagging || this.backTrackDelay <= 0) {
            return false;
        }
        this.incomingQueue.offer(new LagPacket(packet, true));
        return true;
    }

    public void setDelay(int delay) {
        this.lagRangeDelay = Math.max(0, delay);
    }

    public void setBackTrackState(boolean lagging, int delayMs) {
        boolean wasLagging = this.backTrackLagging;
        this.backTrackLagging = lagging;
        this.backTrackDelay = Math.max(0, delayMs);
        if (wasLagging && !lagging) {
            this.flushQueue();
            this.flushIncomingQueue();
        }
    }

    public Vec3 getLastPosition() {
        return this.lastPosition;
    }

    public boolean isFlushing() {
        return this.flushing;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() == EventType.PRE) {
            if (!this.isInWorld()) {
                this.reset();
            } else {
                this.flushQueue();
                this.flushIncomingQueue();
            }
        } else if (event.type() == EventType.POST) {
            if (!this.isInWorld()) {
                this.reset();
                return;
            }
            if (mc.thePlayer.isDead) {
                this.reset();
            }
            this.flushQueue();
            this.flushIncomingQueue();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.setDelay(0);
        }
    }

    public static class LagPacket {
        public final Packet<?> packet;
        public final boolean incoming;
        public final long millis = System.currentTimeMillis();

        public LagPacket(Packet<?> packet) {
            this(packet, false);
        }

        public LagPacket(Packet<?> packet, boolean incoming) {
            this.packet = packet;
            this.incoming = incoming;
        }
    }
}
