package cn.unfair.module.modules.combat;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.JumpEvent;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.rotation.RotationUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Basic", "OldPrediction", "Legit"});
    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0, this::isBasic);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false, this::isBasic);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false, this::isBasic);
    private int attackPending;
    private int velocityTicks = 1000;
    private int groundTicks;
    private int jumpCancelTicks;
    private boolean auraAttack;
    private Entity lastTarget;

    public KeepSprint() {
        super("KeepSprint", false);
    }

    public boolean shouldKeepSprint() {
        if (!this.isBasic()) {
            return true;
        }
        if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
            return false;
        } else {
            return !this.reachOnly.getValue() || mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
        }
    }

    public double getAttackSlowdown() {
        if (!this.isBasic()) {
            return 1.0D;
        }
        return 0.6D + 0.4D * (1.0D - this.slowdown.getValue().doubleValue() / 100.0D);
    }

    public long getOldPredictionAttackDelay() {
        return this.isEnabled() && this.isOldPrediction() && this.velocityTicks >= 7
                ? (mc.thePlayer.ticksExisted % 2 == 0 ? 500L : -1L)
                : Long.MIN_VALUE;
    }

    public void beginAuraAttack() {
        this.auraAttack = true;
    }

    public void endAuraAttack() {
        this.auraAttack = false;
    }

    public boolean prepareAttack() {
        return this.isEnabled()
                && this.isLegit()
                && this.velocityTicks >= 8
                && this.stopSprinting();
    }

    private boolean isBasic() {
        return this.mode.getValue() == 0;
    }

    private boolean isOldPrediction() {
        return this.mode.getValue() == 1;
    }

    private boolean isLegit() {
        return this.mode.getValue() == 2;
    }

    private boolean stopSprinting() {
        if (this.groundTicks == 1) {
            return true;
        }
        if (mc.thePlayer.isSprinting()) {
            mc.thePlayer.setSprinting(false);
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), false);
            this.jumpCancelTicks = 1;
            return true;
        }
        return false;
    }

    private boolean isTargetInRange() {
        return this.lastTarget != null
                && RotationUtil.distanceToEntity(this.lastTarget) <= 3.0D + MoveUtil.getSpeed();
    }

    private boolean usesAuraAutoBlockTiming() {
        KillAura killAura = (KillAura) cn.unfair.Unfair.moduleManager.modules.get(KillAura.class);
        return killAura.isHypixelLagAutoBlockActive();
    }

    private int getAuraAutoBlockTick() {
        return ((KillAura) cn.unfair.Unfair.moduleManager.modules.get(KillAura.class)).getAutoBlockTick();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (this.velocityTicks < Integer.MAX_VALUE) {
            this.velocityTicks++;
        }
        if (mc.thePlayer != null && mc.thePlayer.onGround) {
            if (this.groundTicks < Integer.MAX_VALUE) {
                this.groundTicks++;
            }
        } else {
            this.groundTicks = 0;
        }
        if (!this.isEnabled()) {
            return;
        }
        if (!this.isLegit()) {
            this.jumpCancelTicks = 0;
        } else if (this.jumpCancelTicks > 0) {
            this.jumpCancelTicks--;
        }
        if (this.attackPending > 0) {
            this.attackPending--;
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        this.lastTarget = event.getTarget();
        this.attackPending = Math.max(this.attackPending, 2);
        if (this.isOldPrediction() && this.velocityTicks >= 7) {
            if (!this.auraAttack) {
                AttackOrder.sendFixedPacketAttack(event.getTarget());
            }
            event.setCancelled(true);
        } else if (this.isLegit() && this.prepareAttack()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE
                && event.getPacket() instanceof S12PacketEntityVelocity velocity
                && mc.thePlayer != null
                && velocity.getEntityID() == mc.thePlayer.getEntityId()) {
            this.velocityTicks = 0;
        }
    }

    @EventTarget
    public void onJump(JumpEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.isLegit() && this.jumpCancelTicks > 0) {
            event.setCancelled(true);
        } else if (this.isOldPrediction()
                && this.attackPending > 0
                && this.isTargetInRange()
                && (!this.usesAuraAutoBlockTiming() && mc.thePlayer.ticksExisted % 2 == 0
                || this.usesAuraAutoBlockTiming() && this.getAuraAutoBlockTick() == 2)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled()
                && mc.thePlayer != null
                && this.isOldPrediction()
                && this.attackPending > 0
                && this.isTargetInRange()
                && this.velocityTicks >= 7
                && (!this.usesAuraAutoBlockTiming() && mc.thePlayer.ticksExisted % 2 == 0
                || this.usesAuraAutoBlockTiming() && this.getAuraAutoBlockTick() > 1)) {
            mc.thePlayer.setSprinting(false);
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.attackPending = 0;
        this.velocityTicks = 1000;
        this.groundTicks = 0;
        this.jumpCancelTicks = 0;
        this.auraAttack = false;
        this.lastTarget = null;
    }

    @Override
    public void onDisabled() {
        this.jumpCancelTicks = 0;
        this.auraAttack = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
