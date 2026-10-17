package cn.unfair.management;

import cn.unfair.Unfair;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.module.modules.combat.velocity.B4Mode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.login.client.C01PacketEncryptionResponse;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.network.status.client.C01PacketPing;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class DelayManager {
    public static Minecraft mc = Minecraft.getMinecraft();
    public DelayModules delayModule = DelayModules.NONE;
    public long delay = 0L;
    public Deque<Packet<INetHandlerPlayClient>> delayedPacket = new ConcurrentLinkedDeque<>();

    private boolean isInWorld() {
        return mc.thePlayer != null && mc.theWorld != null && mc.getNetHandler() != null;
    }

    public boolean shouldDelay(Packet<?> packet) {
        if (!this.isInWorld()) {
            this.delayedPacket.clear();
            this.delayModule = DelayModules.NONE;
            return false;
        }
        if (packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn) {
            this.discardDelayedPackets(this.delayModule);
            return false;
        }
        if (this.delayModule == DelayModules.NONE) {
            return false;
        } else if (packet instanceof S00PacketKeepAlive) {
            return false;
        } else {
            if (packet instanceof S19PacketEntityStatus s19) {
                Entity entity = s19.getEntity(mc.theWorld);
                if (entity != null && (!entity.equals(mc.thePlayer) || s19.getOpCode() != 2)) {
                    return false;
                }
            }
            if (this.delayModule == DelayModules.VELOCITY && Unfair.moduleManager != null) {
                Velocity velocity = (Velocity) Unfair.moduleManager.getModule(Velocity.class);
                SubModule subModule = velocity == null ? null : velocity.getCurrentSubModule();
                if (subModule instanceof B4Mode b4 && b4.isDelaying()) {
                    B4Mode.DelayedPacketAction action = b4.handleDelayedPacket(packet);
                    if (action == B4Mode.DelayedPacketAction.PROCESS_NOW) {
                        return false;
                    }
                    if (action == B4Mode.DelayedPacketAction.DROP) {
                        return true;
                    }
                }
            }
            this.queueDelayedPacket(packet);
            return true;
        }
    }

    @SuppressWarnings("unchecked")
    private void queueDelayedPacket(Packet<?> packet) {
        this.delayedPacket.offer((Packet<INetHandlerPlayClient>) packet);
    }

    public boolean setDelayState(boolean state, DelayModules delayModule) {
        if (state) {
            this.delay = 0;
            this.delayModule = delayModule;
        } else {
            this.delayModule = DelayModules.NONE;
            if (!this.isInWorld()) {
                this.delayedPacket.clear();
                return true;
            }
            if (this.delayedPacket.isEmpty()) {
                return true;
            }
            while (true) {
                Packet<INetHandlerPlayClient> packet = this.delayedPacket.poll();
                if (packet == null) {
                    this.delayedPacket.clear();
                    break;
                }
                packet.processPacket(Minecraft.getMinecraft().getNetHandler());
            }
        }
        return this.delayModule != DelayModules.NONE;
    }

    public boolean discardDelayedPackets(DelayModules delayModule) {
        if (this.delayModule != delayModule) {
            return false;
        }
        this.delayModule = DelayModules.NONE;
        this.delay = 0L;
        this.delayedPacket.clear();
        return true;
    }

    public DelayModules getDelayModule() {
        return this.delayModule;
    }

    public void delay(DelayModules modules) {
        this.delayModule = modules;
    }

    public long getDelay() {
        return this.delay;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getPacket() instanceof C00Handshake
                || event.getPacket() instanceof C00PacketLoginStart
                || event.getPacket() instanceof C00PacketServerQuery
                || event.getPacket() instanceof C01PacketPing
                || event.getPacket() instanceof C01PacketEncryptionResponse) {
            this.discardDelayedPackets(this.delayModule);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() == EventType.POST) {
            if (!this.isInWorld()) {
                this.delayModule = DelayModules.NONE;
                this.delay = 0L;
                this.delayedPacket.clear();
                return;
            }
            if (mc.thePlayer.isDead) {
                this.setDelayState(false, this.delayModule);
            }
            if (this.delayModule != DelayModules.NONE) {
                this.delay++;
            }
        }
    }
}
