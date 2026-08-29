package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.combat.KillAura.AttackData;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.player.PacketUtil;
import cn.unfair.util.player.PlayerUtil;
import cn.unfair.util.rotation.RotationUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.server.*;

import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;


public class GrimReduceVelocity extends SubModule {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty maxDelayTicks = new IntProperty("Max Delay Ticks", 6, 5, 100);
    public final IntProperty attack = new IntProperty("Attack", 4, 2, 6);

    private final Deque<Packet<?>> packetQueue = new ConcurrentLinkedDeque<>();
    private volatile boolean suspending = false;
    private volatile S12PacketEntityVelocity heldVelocity = null;
    private int delayTicks = 0;
    private int attacksRemaining = 0;
    private boolean attacking = false;
    private AttackData attackTarget = null;
    private volatile int teleportTicks = 0;
    private volatile boolean pendingHitStatus = false;
    private boolean attackPending = false;

    public GrimReduceVelocity() {
        super("GrimReduce");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            this.resetAll();
            return;
        }
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }

        Packet<?> packet = event.getPacket();

        if (packet instanceof S08PacketPlayerPosLook) {
            this.resetAll();
            this.teleportTicks = 10;
            return;
        }

        if (packet instanceof S19PacketEntityStatus status) {
            if (status.getEntity(mc.theWorld) == mc.thePlayer && status.getOpCode() == 2) {
                this.pendingHitStatus = true;
            }
            return;
        }

        if (packet instanceof S12PacketEntityVelocity motion) {
            if (motion.getEntityID() != mc.thePlayer.getEntityId()) {
                return;
            }

            if (this.teleportTicks > 0) {
                return;
            }

            boolean hitKnockback = this.pendingHitStatus;
            this.pendingHitStatus = false;

            if (!this.canProcess()) {
                this.resetAll();
                return;
            }

            if (hitKnockback && motion.getMotionY() > 0) {
                this.enterSuspension(motion);
                event.setCancelled(true);
            }
            return;
        }

        if (this.suspending) {
            if (this.isCriticalPacket(packet)) {
                if (packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn) {
                    this.resetAll();
                }
                return;
            }
            this.packetQueue.offer(packet);
            event.setCancelled(true);
        }
    }

    private void enterSuspension(S12PacketEntityVelocity motion) {
        if (this.suspending) {
            this.packetQueue.offer(motion);
            return;
        }
        this.suspending = true;
        this.delayTicks = 0;
        this.attacking = false;
        this.attacksRemaining = 0;
        this.attackTarget = null;
        this.heldVelocity = motion;
        this.packetQueue.clear();
        this.packetQueue.offer(motion);
    }

    private boolean isCriticalPacket(Packet<?> packet) {
        return packet instanceof S00PacketKeepAlive
                || packet instanceof S01PacketJoinGame
                || packet instanceof S07PacketRespawn
                || packet instanceof S08PacketPlayerPosLook
                || packet instanceof S40PacketDisconnect;
    }


    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.POST) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            this.resetAll();
            return;
        }
        if (this.teleportTicks > 0) {
            this.teleportTicks--;
        }
        if (!this.isEnabled() || (!this.suspending && !this.attacking)) {
            return;
        }
        if (mc.thePlayer.isDead || mc.thePlayer.ridingEntity != null || mc.currentScreen != null) {
            this.resetAll();
            return;
        }
        this.delayTicks++;

        if (this.suspending && this.delayTicks > this.maxDelayTicks.getValue()) {
            this.resetAll();
            return;
        }

        if (this.attacking) {
            if (this.attacksRemaining > 0) {
                this.attackPending = true;
            }
            return;
        }

        if (this.suspending && mc.thePlayer.onGround) {
            this.suspending = false;
            if (this.canStartAttackSequence()) {
                this.attacking = true;
                this.attacksRemaining = this.attack.getValue();
                this.attackTarget = KillAura.target;
            } else {
                this.release();
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (!this.isEnabled() || mc.thePlayer == null || !this.attackPending) {
            return;
        }
        this.attackPending = false;
        if (this.attacking && this.attacksRemaining > 0) {
            this.attacksRemaining--;
            this.doAttack();
        }
        if (this.attacksRemaining <= 0) {
            this.attacking = false;
            this.flushQueue();
        }
    }

    private boolean canStartAttackSequence() {
        if (Unfair.playerStateManager.digging || Unfair.playerStateManager.placing) {
            return false;
        }
        if (!mc.thePlayer.isSprinting()) {
            return false;
        }
        return this.hasValidTarget();
    }

    private boolean hasValidTarget() {
        AttackData current = KillAura.target;
        if (current == null || current.getEntity() == null) {
            return false;
        }
        EntityLivingBase entity = current.getEntity();
        if (entity.isDead || entity.deathTime > 0 || !mc.theWorld.loadedEntityList.contains(entity)) {
            return false;
        }
        double range = 6.0;
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null) {
            range = killAura.attackRange.getValue().doubleValue() + 1.5;
        }
        return RotationUtil.distanceToEntity(entity) <= range;
    }

    private void doAttack() {
        if (this.attackTarget == null) {
            return;
        }
        EntityLivingBase entity = this.attackTarget.getEntity();
        if (entity == null || entity.isDead || mc.thePlayer.getDistanceToEntity(entity) > 6.0F) {
            return;
        }

        boolean wasSprinting = mc.thePlayer.isSprinting();
        mc.thePlayer.setSprinting(false);
        mc.thePlayer.motionX *= 0.6;
        mc.thePlayer.motionZ *= 0.6;

        AttackOrder.sendFixedPacketAttack(entity);
        PlayerUtil.attackEntity(entity);
        if (wasSprinting) {
            mc.thePlayer.setSprinting(true);
        }
    }

    private void flushQueue() {
        this.suspending = false;
        this.attacking = false;
        this.attacksRemaining = 0;
        this.attackTarget = null;

        if (mc.getNetHandler() == null) {
            this.packetQueue.clear();
            this.heldVelocity = null;
            return;
        }
        Packet<?> packet;
        while ((packet = this.packetQueue.poll()) != null) {
            try {
                PacketUtil.receivePacket(packet);
            } catch (ThreadQuickExitException ignored) {
            }
        }
        this.heldVelocity = null;
    }

    private void release() {
        this.flushQueue();
    }

    private boolean canProcess() {
        if (mc.thePlayer.isDead || mc.thePlayer.ridingEntity != null) {
            return false;
        }
        if (mc.currentScreen != null) {
            return false;
        }
        if (Velocity.isInLiquidOrWeb()) {
            return false;
        }
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        return killAura != null && killAura.isEnabled();
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.resetAll();
    }

    @Override
    public void onEnabled() {
        this.resetAll();
    }

    @Override
    public void onDisabled() {
        this.resetAll();
    }

    private void resetAll() {
        this.suspending = false;
        this.attacking = false;
        this.attacksRemaining = 0;
        this.attackPending = false;
        this.delayTicks = 0;
        this.heldVelocity = null;
        this.attackTarget = null;
        this.pendingHitStatus = false;
        this.teleportTicks = 0;
        this.packetQueue.clear();
    }
}