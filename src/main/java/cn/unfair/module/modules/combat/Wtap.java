package cn.unfair.module.modules.combat;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.client.TimerUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.potion.Potion;

public class Wtap extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double REACH = 3.0D;
    private static final double NEAR = 2.3D;
    private static final double FAR = 3.8D;
    private static final double KB_SPEED = 0.45D;
    private static final double CLOSE_THRESHOLD = 0.05D;
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Normal", "Smart"});
    public final FloatProperty delay = new FloatProperty("Delay", 5.5F, 0.0F, 10.0F, this::isNormal);
    public final FloatProperty duration = new FloatProperty("Duration", 1.5F, 1.0F, 5.0F, this::isNormal);
    private final TimerUtil timer = new TimerUtil();
    private boolean active = false;
    private boolean stopForward = false;
    private long delayTicks = 0L;
    private long durationTicks = 0L;
    private Entity lastTarget = null;
    private double lastDistance = -1.0D;
    private double closingSpeed = 0.0D;

    public Wtap() {
        super("WTap", false);
    }

    private boolean isNormal() {
        return this.mode.getValue() == 0;
    }

    private boolean isSmart() {
        return this.mode.getValue() == 1;
    }

    private boolean canTrigger() {
        return !(mc.thePlayer.movementInput.moveForward < 0.8F)
                && !mc.thePlayer.isCollidedHorizontally
                && (!((float) mc.thePlayer.getFoodStats().getFoodLevel() <= 6.0F) || mc.thePlayer.capabilities.allowFlying) && (mc.thePlayer.isSprinting()
                || !mc.thePlayer.isUsingItem() && !mc.thePlayer.isPotionActive(Potion.blindness) && mc.gameSettings.keyBindSprint.isKeyDown());
    }

    private boolean hasValidTarget() {
        return this.lastTarget != null && !this.lastTarget.isDead
                && mc.theWorld != null && mc.theWorld.getEntityByID(this.lastTarget.getEntityId()) == this.lastTarget;
    }

    private boolean shouldResume() {
        return this.hasValidTarget()
                && RotationUtil.distanceToEntity(this.lastTarget) > REACH + 0.5D;
    }

    private int getPing() {
        if (mc.getNetHandler() == null || mc.thePlayer == null) {
            return 0;
        }
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        return info == null ? 0 : Math.max(0, info.getResponseTime());
    }

    // dist 为到箱距离, closing 为相对接近速度(格/tick, 正=靠近), 直接推算出急停时长(ms)
    private boolean triggerSmart() {
        if (!this.hasValidTarget()) {
            return false;
        }
        double dist = RotationUtil.distanceToEntity(this.lastTarget);
        double closing = this.closingSpeed;
        if (dist > FAR && closing <= 0.0D) {
            return false;
        }

        double latency = this.getPing() / 1000.0D;
        // 对方正在远离时服务端判定距离比视觉大, 按对方一帧位移收紧边缘判定
        double lagOffset = Math.max(0.0D, -closing) * 20.0D * latency;

        long delayMs;
        long durationMs;
        if (closing > CLOSE_THRESHOLD) {
            // 对方主动扑来: 立即截停, 对方撞进我方攻击距离, 原地打第二刀
            delayMs = 20L;
            double reachTicks = Math.max(0.0D, (REACH - dist) / closing);
            durationMs = (long) Math.min(Math.max(reachTicks * 50.0D, 40.0D), 140.0D);
        } else if (dist < NEAR) {
            // 贴脸: 击退回弹到攻击边缘, 提前一拍恢复追击使第二刀刚好到位
            delayMs = 30L;
            double backTicks = Math.max(0.0D, (REACH - dist - lagOffset) / KB_SPEED);
            durationMs = (long) Math.min(Math.max(backTicks * 50.0D - 50.0D, 60.0D), 200.0D);
        } else {
            // 边缘僵持: 轻点一下防止持续顶进对方范围
            delayMs = 40L;
            durationMs = 50L;
            if (closing < 0.0D) {
                durationMs += (long) Math.min(-closing * 20.0D * latency * 50.0D, 60.0D);
            }
        }

        this.delayTicks += delayMs;
        this.durationTicks += durationMs;
        return true;
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.active) {
            if (!this.stopForward && !this.canTrigger()) {
                this.active = false;
                while (this.delayTicks > 0L) {
                    this.delayTicks -= 50L;
                }
                while (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                }
            } else if (this.delayTicks > 0L) {
                this.delayTicks -= 50L;
            } else {
                if (this.durationTicks > 0L) {
                    this.durationTicks -= 50L;
                    this.stopForward = true;
                    mc.thePlayer.movementInput.moveForward = 0.0F;
                    if (this.isSmart() && this.shouldResume()) {
                        this.durationTicks = 0L;
                    }
                }
                if (this.durationTicks <= 0L) {
                    this.active = false;
                }
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && !event.isCancelled() && mc.thePlayer != null) {
            this.lastTarget = event.getTarget();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        if (!this.isSmart()) {
            this.lastDistance = -1.0D;
            this.closingSpeed = 0.0D;
            return;
        }
        if (!this.hasValidTarget()) {
            this.lastTarget = null;
            this.lastDistance = -1.0D;
            this.closingSpeed = 0.0D;
            return;
        }
        double dist = RotationUtil.distanceToEntity(this.lastTarget);
        if (this.lastDistance >= 0.0D) {
            this.closingSpeed = this.lastDistance - dist;
        }
        this.lastDistance = dist;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && event.getType() == EventType.SEND) {
            if (event.getPacket() instanceof C02PacketUseEntity packet
                    && packet.getAction() == Action.ATTACK
                    && !this.active
                    && this.timer.hasTimeElapsed(500L)
                    && mc.thePlayer.isSprinting()) {
                if (this.isSmart() && !this.hasValidTarget() && mc.theWorld != null) {
                    this.lastTarget = packet.getEntityFromWorld(mc.theWorld);
                    this.lastDistance = -1.0D;
                    this.closingSpeed = 0.0D;
                }
                this.timer.reset();
                this.active = true;
                this.stopForward = false;
                if (this.isSmart()) {
                    if (!this.triggerSmart()) {
                        this.active = false;
                    }
                } else {
                    this.delayTicks = this.delayTicks + (long) (50.0F * this.delay.getValue());
                    this.durationTicks = this.durationTicks + (long) (50.0F * this.duration.getValue());
                }
            }
        }
    }

    @Override
    public void onDisabled() {
        this.active = false;
        this.stopForward = false;
        this.delayTicks = 0L;
        this.durationTicks = 0L;
        this.lastTarget = null;
        this.lastDistance = -1.0D;
        this.closingSpeed = 0.0D;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
