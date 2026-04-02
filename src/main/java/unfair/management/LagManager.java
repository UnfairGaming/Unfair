package unfair.management;

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
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.PacketEvent;
import unfair.events.TickEvent;
import unfair.util.PacketUtil;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class LagManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final Deque<LagPacket> packetQueue;
    private int tickDelay;
    private boolean flushing;
    private Vec3 lastPosition;

    public LagManager() {
        this.packetQueue = new ConcurrentLinkedDeque<>();
        this.tickDelay = 0;
        this.flushing = false;
        this.lastPosition = new Vec3(0.0, 0.0, 0.0);
    }

    private void flushQueue() {
        if (mc.getNetHandler() == null) {
            this.packetQueue.clear();
        } else {
            for (this.flushing = true; !this.packetQueue.isEmpty(); this.packetQueue.poll()) {
                LagPacket lagPacket = this.packetQueue.peek();
                if (this.tickDelay > 0 && lagPacket.delay <= this.tickDelay) {
                    break;
                }
                PacketUtil.sendPacketNoEvent(lagPacket.packet);
                if (lagPacket.packet instanceof C03PacketPlayer) {
                    C03PacketPlayer c03 = (C03PacketPlayer) lagPacket.packet;
                    if (c03.isMoving()) {
                        this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
                    }
                }
            }
            this.flushing = false;
        }
    }

    private void incrementDelays() {
        this.packetQueue.forEach(z -> z.delay++);
    }

    public boolean handlePacket(Packet<?> packet) {
        this.flushQueue();
        if (packet instanceof C00PacketKeepAlive || packet instanceof C01PacketChatMessage) {
            return false;
        } else if ((long) this.tickDelay > 0L) {
            this.packetQueue.offer(new LagPacket(packet));
            return true;
        } else {
            if (packet instanceof C03PacketPlayer) {
                C03PacketPlayer c03 = (C03PacketPlayer) packet;
                if (c03.isMoving()) {
                    this.lastPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
                }
            }
            return false;
        }
    }

    public void setDelay(int delay) {
        this.tickDelay = delay;
    }

    public Vec3 getLastPosition() {
        return this.lastPosition;
    }

    public boolean isFlushing() {
        return this.flushing;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.POST) {
            if (mc.thePlayer.isDead) {
                this.setDelay(0);
            }
            this.incrementDelays();
            this.flushQueue();
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
        public int delay;

        public LagPacket(Packet<?> packet) {
            this.packet = packet;
            this.delay = 0;
        }
    }
}
