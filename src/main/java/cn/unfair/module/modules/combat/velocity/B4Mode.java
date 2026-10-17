package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.AimAssist;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.client.ServerUtil;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.network.Packet;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.util.AxisAlignedBB;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

public class B4Mode extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Field MOTION_X = findMotionField("motionX", "field_149415_b");
    private static final Field MOTION_Y = findMotionField("motionY", "field_149416_c");
    private static final Field MOTION_Z = findMotionField("motionZ", "field_149414_d");

    public final PercentProperty horizontal = new PercentProperty("Horizontal", 100);
    public final PercentProperty vertical = new PercentProperty("Vertical", 100);
    public final BooleanProperty disableInLobby = new BooleanProperty("Disable in lobby", true);
    public final BooleanProperty onlyWhileMoving = new BooleanProperty("Only while moving", false);
    public final BooleanProperty cancelBurning = new BooleanProperty("Cancel burning", false);
    public final BooleanProperty cancelExplosion = new BooleanProperty("Cancel explosion", false);
    public final BooleanProperty cancelGround = new BooleanProperty("Cancel ground", false);
    public final BooleanProperty cancelOffGround = new BooleanProperty("Cancel off ground", false);
    public final BooleanProperty cancelWhileFalling = new BooleanProperty("Cancel while falling", false);
    public final IntProperty cancelInterval = new IntProperty("Cancel interval", 1, 1, 5);
    public final FloatProperty damageBoost = new FloatProperty("Damage boost", 0.8F, 0.8F, 1.6F);
    public final BooleanProperty boostWithLmb = new BooleanProperty("Boost with LMB", false);
    public final IntProperty delay = new IntProperty("Delay", 0, 0, 2000);
    public final BooleanProperty delayIfLanding = new BooleanProperty("Delay if landing", false);
    public final BooleanProperty delayOnlyMidair = new BooleanProperty("Delay only midair", false);
    public final BooleanProperty delayOnlyTargeting = new BooleanProperty("Delay only targeting", false);
    public final BooleanProperty delaySimultaneously = new BooleanProperty("Delay simultaneously", false);
    public final BooleanProperty delayUntilGround = new BooleanProperty("Delay until ground", false);
    public final BooleanProperty disableAttackWhileDelay = new BooleanProperty("Disable attack while delay", false);

    private boolean delayFlag;
    private boolean startedMidair;
    private boolean bypassNextDelay;
    private int delayTicksAtStart;
    private int boostStage;
    private int cancelCount;
    private long explosionAt;
    private int fireballStage;
    private long fireballAt;

    public B4Mode() {
        super("B4Mode");
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (event.getType() == EventType.SEND) {
            this.handleFireballPlacement(event.getPacket());
            return;
        }
        if (event.getType() != EventType.RECEIVE) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof S12PacketEntityVelocity velocity && this.isOwnVelocity(velocity)) {
            boolean skipDelay = this.bypassNextDelay;
            this.bypassNextDelay = false;
            if (!skipDelay && this.shouldStartDelay()) {
                if (this.filter(packet)) {
                    this.startDelay(velocity);
                }
                event.setCancelled(true);
                return;
            }
        }

        if (!this.filter(packet)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            this.reset();
            return;
        }
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (this.fireballStage > 0 && System.currentTimeMillis() - this.fireballAt > 750L) {
            this.resetFireball();
        }

        if (this.boostStage > 0) {
            double speed = MoveUtil.getSpeed();
            if (this.boostStage == 1) {
                double boostedSpeed = Math.max(speed * this.damageBoost.getValue(), 0.35D);
                MoveUtil.setSpeed(boostedSpeed - Math.random() * 0.001D, MoveUtil.getMoveYaw());
            } else {
                MoveUtil.setSpeed(0.35D - Math.random() * 0.001D, MoveUtil.getMoveYaw());
            }
            this.boostStage = 0;
        }

        if (this.delayFlag) {
            int delayTicks = this.getDelayTicks();
            boolean changed = delayTicks != this.delayTicksAtStart;
            boolean landed = this.delayUntilGround.getValue() && this.startedMidair && mc.thePlayer.onGround;
            if (changed || delayTicks == 0 || landed || Unfair.delayManager.getDelay() >= delayTicks) {
                this.releaseDelay();
            }
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (this.isEnabled() && this.delayFlag && this.disableAttackWhileDelay.getValue()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.releaseDelay();
        this.reset();
    }

    @Override
    public void onEnabled() {
        this.reset();
    }

    @Override
    public void onDisabled() {
        this.releaseDelay();
        this.reset();
    }

    public boolean isDelaying() {
        return this.delayFlag;
    }

    public DelayedPacketAction handleDelayedPacket(Packet<?> packet) {
        if (!this.isEnabled() || !this.delayFlag) {
            return DelayedPacketAction.QUEUE;
        }

        if (packet instanceof S12PacketEntityVelocity velocity
                && this.isOwnVelocity(velocity)
                && !this.delaySimultaneously.getValue()) {
            this.bypassNextDelay = true;
            this.releaseDelay();
            return DelayedPacketAction.PROCESS_NOW;
        }

        return this.filter(packet) ? DelayedPacketAction.QUEUE : DelayedPacketAction.DROP;
    }

    private boolean shouldStartDelay() {
        int ticks = this.getDelayTicks();
        if (ticks == 0 || mc.isSingleplayer() || Unfair.delayManager.getDelayModule() != DelayModules.NONE) {
            return false;
        }
        if (this.delayOnlyMidair.getValue() && mc.thePlayer.onGround
                || this.delayOnlyTargeting.getValue() && !this.targetMatches()
                || this.delayIfLanding.getValue() && !this.landsWithin(ticks)) {
            return false;
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void startDelay(S12PacketEntityVelocity packet) {
        this.delayTicksAtStart = this.getDelayTicks();
        this.startedMidair = !mc.thePlayer.onGround;
        this.delayFlag = true;
        Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
        Unfair.delayManager.delayedPacket.offer((Packet<INetHandlerPlayClient>) packet);
    }

    private void releaseDelay() {
        if (this.delayFlag && Unfair.delayManager != null
                && Unfair.delayManager.getDelayModule() == DelayModules.VELOCITY) {
            Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
        }
        this.delayFlag = false;
        this.startedMidair = false;
        this.delayTicksAtStart = 0;
    }

    private boolean filter(Packet<?> packet) {
        if (!this.active()) {
            return true;
        }

        if (packet instanceof S12PacketEntityVelocity velocity) {
            if (!this.isOwnVelocity(velocity)) {
                return true;
            }
            if (this.fireballStage == 2) {
                this.resetFireball();
                return true;
            }

            LongJump longJump = (LongJump) Unfair.moduleManager.getModule(LongJump.class);
            if (longJump != null && longJump.isEnabled()) {
                return true;
            }

            if (this.explosionAt > 0L) {
                if (System.currentTimeMillis() - this.explosionAt < 10L) {
                    this.explosionAt = 0L;
                    return false;
                }
                this.explosionAt = 0L;
            }

            if (!mc.thePlayer.onGround) {
                if (this.damageBoost.getValue() > 0.8F
                        && (!this.boostWithLmb.getValue() || Mouse.isButtonDown(0))
                        && this.canBoost()) {
                    this.boostStage = 1;
                    return true;
                }
                if (this.cancelOffGround.getValue()) {
                    return false;
                }
                if (this.cancelWhileFalling.getValue()
                        && (mc.thePlayer.motionY < -0.55D
                        || mc.thePlayer.motionY < -0.08D && this.heightAboveGround() > 5.0D)) {
                    return false;
                }
            } else if (this.cancelGround.getValue()) {
                return false;
            }

            if (this.cancelBurning.getValue() && mc.thePlayer.isBurning()
                    && Math.abs(velocity.getMotionX()) < 2010 && Math.abs(velocity.getMotionZ()) < 2010) {
                return false;
            }
            if (this.cancelInterval.getValue() > 1 && !mc.thePlayer.onGround
                    && ++this.cancelCount >= this.cancelInterval.getValue()) {
                this.cancelCount = 0;
                return false;
            }
            return this.scale(velocity);
        }

        if (packet instanceof S27PacketExplosion explosion) {
            double distanceSq = mc.thePlayer.getDistanceSq(explosion.getX(), explosion.getY(), explosion.getZ());
            if (this.fireballStage == 1 && distanceSq <= 6.0D) {
                this.fireballAt = System.currentTimeMillis();
                this.fireballStage = 2;
                return true;
            }
            if (this.cancelExplosion.getValue() && distanceSq <= 25.0D) {
                this.explosionAt = System.currentTimeMillis();
                return false;
            }
            if (this.horizontal.getValue() == 0 || this.vertical.getValue() == 0) {
                return false;
            }
            explosion.motionX *= this.horizontal.getValue() / 100.0F;
            explosion.motionY *= this.vertical.getValue() / 100.0F;
            explosion.motionZ *= this.horizontal.getValue() / 100.0F;
        }
        return true;
    }

    private boolean scale(S12PacketEntityVelocity packet) {
        if (this.horizontal.getValue() == 0 && this.vertical.getValue() == 0) {
            return false;
        }
        double h = this.horizontal.getValue() / 100.0D;
        double v = this.vertical.getValue() / 100.0D;
        try {
            if (h != 1.0D) {
                MOTION_X.setInt(packet, (int) (MOTION_X.getInt(packet) * h));
                MOTION_Z.setInt(packet, (int) (MOTION_Z.getInt(packet) * h));
            }
            if (v != 1.0D) {
                MOTION_Y.setInt(packet, (int) (MOTION_Y.getInt(packet) * v));
            }
        } catch (IllegalAccessException ignored) {
            return true;
        }
        return true;
    }

    private boolean active() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        if (this.onlyWhileMoving.getValue() && !PlayerUtil.isMoving(mc.thePlayer)) {
            return false;
        }
        return !this.disableInLobby.getValue() || !this.isHypixelLobby();
    }

    private boolean isHypixelLobby() {
        return ServerUtil.isHypixel() && !ServerUtil.hasPlayerCountInfo();
    }

    private boolean isOwnVelocity(S12PacketEntityVelocity packet) {
        return packet.getEntityID() == mc.thePlayer.getEntityId()
                && packet.getMotionY() != -627 && packet.getMotionY() < 4500;
    }

    private boolean canBoost() {
        return !mc.thePlayer.onGround
                && PlayerUtil.isMoving(mc.thePlayer)
                && !mc.thePlayer.isBurning()
                && !PlayerUtil.isInLiquidOrWeb()
                && mc.currentScreen == null;
    }

    private boolean targetMatches() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return true;
        }
        AimAssist aimAssist = (AimAssist) Unfair.moduleManager.getModule(AimAssist.class);
        if (aimAssist != null && aimAssist.isEnabled() && aimAssist.getCurrentTarget() != null) {
            return true;
        }
        return mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null;
    }

    private boolean landsWithin(int ticks) {
        if (mc.thePlayer.onGround) {
            return false;
        }
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        double offsetY = 0.0D;
        double motionY = mc.thePlayer.motionY;
        for (int i = 0; i < ticks; i++) {
            offsetY += motionY;
            if (motionY < 0.0D
                    && !mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.offset(0.0D, offsetY, 0.0D)).isEmpty()) {
                return true;
            }
            motionY = (motionY - 0.08D) * 0.98D;
        }
        return false;
    }

    private double heightAboveGround() {
        AxisAlignedBB box = mc.thePlayer.getEntityBoundingBox();
        for (int distance = 1; distance <= 100; distance++) {
            if (!mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, box.offset(0.0D, -distance, 0.0D)).isEmpty()) {
                return distance - 1.0D;
            }
        }
        return -1.0D;
    }

    private void handleFireballPlacement(Packet<?> packet) {
        if (!(packet instanceof C08PacketPlayerBlockPlacement placement)
                || placement.getPlacedBlockDirection() == 255
                || placement.getStack() == null
                || placement.getStack().getItem() != Items.fire_charge) {
            return;
        }
        LongJump longJump = (LongJump) Unfair.moduleManager.getModule(LongJump.class);
        if (longJump != null && longJump.isEnabled()) {
            return;
        }
        this.fireballAt = System.currentTimeMillis();
        this.fireballStage = 1;
    }

    private void resetFireball() {
        this.fireballStage = 0;
        this.fireballAt = 0L;
    }

    private int getDelayTicks() {
        return this.delay.getValue() / 50;
    }

    private static Field findMotionField(String... names) {
        for (String name : names) {
            try {
                Field field = S12PacketEntityVelocity.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new IllegalStateException("Could not locate velocity packet motion field");
    }

    private void reset() {
        this.delayFlag = false;
        this.startedMidair = false;
        this.bypassNextDelay = false;
        this.delayTicksAtStart = 0;
        this.boostStage = 0;
        this.cancelCount = 0;
        this.explosionAt = 0L;
        this.resetFireball();
    }

    public enum DelayedPacketAction {
        QUEUE,
        DROP,
        PROCESS_NOW
    }
}
