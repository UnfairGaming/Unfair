package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.RenderEntityEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.mixin.IAccessorMinecraft;
import cn.unfair.mixin.IAccessorRenderManager;
import cn.unfair.mixin.IAccessorS14PacketEntity;
import cn.unfair.mixin.IAccessorS18PacketEntityTeleport;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.ChatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0CPacketInput;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C13PacketPlayerAbilities;
import net.minecraft.network.play.client.C15PacketClientSettings;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.client.C18PacketSpectate;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S20PacketEntityProperties;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S39PacketPlayerAbilities;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty onlyWhenNeeded = new BooleanProperty("only-when-needed", false);
    public final FloatProperty attackRange = new FloatProperty("attack-range", 3.0F, 0.1F, 8.0F, this.onlyWhenNeeded::getValue);
    public final FloatProperty rangeStart = new FloatProperty("range-start", 3.0F, 1.0F, 8.0F, () -> !this.onlyWhenNeeded.getValue());
    public final FloatProperty rangeEnd = new FloatProperty("range-end", 6.0F, 1.0F, 8.0F, () -> !this.onlyWhenNeeded.getValue());
    public final ModeProperty rangeBase = new ModeProperty("range-base", 0, new String[]{"MOUSE_OVER", "HURT_TIME", "ATTACK"}, this.onlyWhenNeeded::getValue);
    public final BooleanProperty attackTickFix = new BooleanProperty("attack-tick-fix", false, this.onlyWhenNeeded::getValue);
    public final IntProperty predictionTicks = new IntProperty("prediction-ticks", 1, 0, 10, this.onlyWhenNeeded::getValue);
    public final IntProperty hurtTimeToWork = new IntProperty("hurt-time-to-work", 3, 0, 10, this.onlyWhenNeeded::getValue);
    public final BooleanProperty extraCheck = new BooleanProperty("extra-check", true);
    public final IntProperty ms = new IntProperty("delay-ms", 50, 0, 1000);
    public final BooleanProperty extraMS = new BooleanProperty("extra-ms", false);
    public final IntProperty extraRand = new IntProperty("extra-rand", 50, 0, 500);
    public final IntProperty delayForNextLag = new IntProperty("delay-for-next-lag", 0, 0, 1000);
    public final ModeProperty esp = new ModeProperty("mode", 1, new String[]{"FAKE_PLAYER", "BOX", "NONE"});
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty botCheck = new BooleanProperty("bot-check", true);
    public final BooleanProperty teams = new BooleanProperty("teams", true);
    public final ModeProperty boxColor = new ModeProperty("box-color", 0, new String[]{"DEFAULT", "HUD", "CUSTOM"}, () -> this.esp.getValue() == 1);
    public final ColorProperty boxCustomColor = new ColorProperty("box-custom-color", new Color(0, 0, 0).getRGB(), () -> this.esp.getValue() == 1 && this.boxColor.getValue() == 2);
    public final FloatProperty outlineWidth = new FloatProperty("outline-width", 1.0F, 0.1F, 5.0F, () -> this.esp.getValue() == 1);
    public final BooleanProperty debug = new BooleanProperty("debug", false);

    private EntityLivingBase target;
    private EntityLivingBase lastTarget;
    public static Vec3 realPosition = zeroVec();
    public static Vec3 realLastPos = zeroVec();
    private Vec3 lastRenderPosition;
    private Vec3 currentRenderPosition;
    public static boolean shouldLag;
    private boolean dispatched;
    private boolean outOfRange;
    private boolean attacked;
    private final DemiseTimer relagTimer = new DemiseTimer();
    private final DemiseTimer attackTimer = new DemiseTimer();
    private final DemiseTimer debugTimer = new DemiseTimer();
    private int nextRand;

    public BackTrack() {
        super("BackTrack", false);
    }

    @Override
    public void onEnabled() {
        realPosition = zeroVec();
        realLastPos = zeroVec();
        this.lastRenderPosition = null;
        this.currentRenderPosition = null;
        this.lastTarget = null;
    }

    @Override
    public void onDisabled() {
        BackTrackLagUtils.disable();
        BackTrackLagUtils.dispatch();
        shouldLag = false;
        realPosition = null;
        realLastPos = null;
        this.lastRenderPosition = null;
        this.currentRenderPosition = null;
        this.target = null;
        this.lastTarget = null;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.ms.getValue() + this.nextRand + " ms"};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (event.getType() == EventType.PRE) {
            BackTrackLagUtils.onPreTick();
        } else if (event.getType() == EventType.POST) {
            if (this.target != null && realPosition != null) {
                if (this.currentRenderPosition == null) {
                    this.lastRenderPosition = realPosition;
                } else {
                    this.lastRenderPosition = this.currentRenderPosition;
                }
                this.currentRenderPosition = realPosition;
            } else {
                this.lastRenderPosition = null;
                this.currentRenderPosition = null;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }

        this.runBackTrack();
    }

    private void runBackTrack() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            BackTrackLagUtils.onPostTick();
            return;
        }

        if (mc.thePlayer.isDead || mc.currentScreen instanceof GuiGameOver) {
            this.stopLaggingForRespawn();
            BackTrackLagUtils.onPostTick();
            return;
        }

        if (this.ms.getValue() > (this.extraMS.getValue() ? 5000 : 1000)) {
            this.ms.setValue(this.extraMS.getValue() ? 5000 : 1000);
        }

        this.target = getTarget(8.0D);
        if (this.target == null) {
            this.lastTarget = null;
            this.lastRenderPosition = null;
            this.currentRenderPosition = null;
            this.debugNoTarget();
            BackTrackLagUtils.onPostTick();
            return;
        }
        if (this.target != this.lastTarget || realPosition == null || realLastPos == null) {
            realPosition = getServerPositionVector(this.target);
            realLastPos = realPosition;
            this.lastRenderPosition = realPosition;
            this.currentRenderPosition = realPosition;
            this.lastTarget = this.target;
        }

        Vec3 pred = multiply(subtract(getPositionVector(this.target), getPrevPositionVector(this.target)), this.predictionTicks.getValue());
        double realDistance = getCustomDistanceToEntityBox(
                add(add(realPosition, 0.0D, this.target.getEyeHeight(), 0.0D), multiply(getMoveDeltaVector(this.target), this.predictionTicks.getValue())),
                mc.thePlayer
        );
        double realDistanceNoPred = getCustomDistanceToEntityBox(add(realPosition, 0.0D, this.target.getEyeHeight(), 0.0D), mc.thePlayer);
        double realDistanceToMouseOverNoPred = getDistToTargetFromMouseOver(
                mc.thePlayer.getPositionEyes(1.0F),
                mc.thePlayer.getLook(1.0F),
                this.target,
                offset(getHitbox(this.target), subtract(add(realPosition, 0.0D, this.target.getEyeHeight(), 0.0D), this.target.getPositionEyes(1.0F)))
        );
        double clientDistance = getDistToTargetFromMouseOver(
                mc.thePlayer.getPositionEyes(1.0F),
                mc.thePlayer.getLook(1.0F),
                this.target,
                offset(offset(getHitbox(this.target), inverse(getPositionVector(this.target))), add(getPositionVector(this.target), pred))
        );

        if (clientDistance > this.attackRange.getValue()) {
            this.outOfRange = true;
        }

        if (this.outOfRange) {
            switch (this.rangeBase.getValue()) {
                case 0:
                    this.outOfRange = realDistance < 3.0D;
                    break;
                case 1:
                    this.outOfRange = this.target.hurtTime == 10;
                    break;
                default:
                    this.outOfRange = this.attacked && (!this.attackTickFix.getValue() || this.attackTimer.hasTimeElapsed(Math.min(this.ms.getValue(), 100)));
                    break;
            }
        }

        boolean distanceCheck = distance(mc.thePlayer, getPositionVector(this.target)) > distance(mc.thePlayer, getPrevPositionVector(this.target));
        boolean extraCheck = distanceCheck || !this.extraCheck.getValue();
        boolean onlyNeeded = extraCheck
                && (realDistance > this.attackRange.getValue() || this.outOfRange)
                && realDistance < 4.5D
                && clientDistance <= 3.0D
                && this.target.hurtTime <= this.hurtTimeToWork.getValue();
        boolean on = extraCheck && realDistance > this.rangeStart.getValue() && realDistance < this.rangeEnd.getValue();

        shouldLag = this.onlyWhenNeeded.getValue() ? onlyNeeded : on;
        this.debugOnlyNeeded(
                extraCheck,
                realDistance,
                realDistanceNoPred,
                realDistanceToMouseOverNoPred,
                clientDistance,
                onlyNeeded,
                this.onlyWhenNeeded.getValue(),
                realDistance > this.attackRange.getValue() || this.outOfRange,
                realDistance < 4.5D,
                clientDistance <= 3.0D,
                this.target.hurtTime <= this.hurtTimeToWork.getValue()
        );

        if (shouldLag) {
            if (this.relagTimer.hasTimeElapsed(this.delayForNextLag.getValue())) {
                BackTrackLagUtils.spoof(this.ms.getValue() + this.nextRand, true, true, true, true, false, false);
                this.dispatched = false;
            }
        } else if (!this.dispatched) {
            BackTrackLagUtils.disable();
            BackTrackLagUtils.dispatch();
            this.relagTimer.reset();
            this.dispatched = true;
            this.nextRand = randomizeAround(this.extraRand.getValue());
        }

        this.attacked = false;
        BackTrackLagUtils.onPostTick();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || event.getTarget() != this.target || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }

        this.attacked = true;
        if (!this.attackTimer.hasTimeElapsed(Math.min(this.ms.getValue(), 100))
                && this.attackTickFix.getValue()
                && !this.dispatched
                && this.onlyWhenNeeded.getValue()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.PERFORM_RESPAWN) {
            this.stopLaggingForRespawn();
            return;
        }

        if (event.getType() == EventType.RECEIVE) {
            if (this.target != null) {
                realLastPos = realPosition;

                if (packet instanceof S14PacketEntity) {
                    S14PacketEntity s14PacketEntity = (S14PacketEntity) packet;
                    if (((IAccessorS14PacketEntity) s14PacketEntity).getEntityId() == this.target.getEntityId()) {
                        realPosition = realPosition.addVector(s14PacketEntity.func_149062_c() / 32.0D, s14PacketEntity.func_149061_d() / 32.0D, s14PacketEntity.func_149064_e() / 32.0D);
                    }
                } else if (packet instanceof S18PacketEntityTeleport) {
                    S18PacketEntityTeleport s18PacketEntityTeleport = (S18PacketEntityTeleport) packet;
                    if (((IAccessorS18PacketEntityTeleport) s18PacketEntityTeleport).getEntityId() == this.target.getEntityId()) {
                        realPosition = new Vec3(s18PacketEntityTeleport.getX() / 32.0D, s18PacketEntityTeleport.getY() / 32.0D, s18PacketEntityTeleport.getZ() / 32.0D);
                    }

                    if (mc.thePlayer != null && ((IAccessorS18PacketEntityTeleport) s18PacketEntityTeleport).getEntityId() == mc.thePlayer.getEntityId()) {
                        this.dispatched = false;
                        shouldLag = false;
                    }
                }
            }
            BackTrackLagUtils.onPacket(event, PacketDirection.INCOMING);
        } else if (event.getType() == EventType.SEND) {
            if (packet instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) packet).getAction().equals(C02PacketUseEntity.Action.ATTACK)
                    && this.attackTickFix.getValue()
                    && !this.dispatched
                    && this.onlyWhenNeeded.getValue()) {
                this.attackTimer.reset();
            }
            BackTrackLagUtils.onPacket(event, PacketDirection.OUTGOING);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || this.target == null || !shouldLag || this.esp.getValue() != 1) {
            return;
        }

        AxisAlignedBB bb = this.getRenderBox(event.getPartialTicks());
        Color color = this.getBoxColor();
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.drawBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 255, this.outlineWidth.getValue());
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onRenderEntity(RenderEntityEvent event) {
        if (!this.isEnabled() || this.target == null || this.esp.getValue() != 0 || !shouldLag) {
            return;
        }

        float partialTicks = ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;
        Vec3 renderPosition = this.getRenderPosition(partialTicks).addVector(
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
        );

        mc.getRenderManager().doRenderEntity(this.target, renderPosition.xCoord, renderPosition.yCoord, renderPosition.zCoord, this.target.rotationYawHead, partialTicks, true);
    }

    private Color getBoxColor() {
        switch (this.boxColor.getValue()) {
            case 1:
                return ((HUD) Unfair.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis());
            case 2:
                return new Color(this.boxCustomColor.getValue());
            default:
                return this.target instanceof EntityPlayer ? TeamUtil.getTeamColor((EntityPlayer) this.target, 1.0F) : new Color(0, 0, 0);
        }
    }

    private AxisAlignedBB getRenderBox(float partialTicks) {
        Vec3 position = this.getRenderPosition(partialTicks);
        float size = this.target.getCollisionBorderSize();
        return new AxisAlignedBB(
                position.xCoord - (double) this.target.width / 2.0D,
                position.yCoord,
                position.zCoord - (double) this.target.width / 2.0D,
                position.xCoord + (double) this.target.width / 2.0D,
                position.yCoord + (double) this.target.height,
                position.zCoord + (double) this.target.width / 2.0D
        )
                .expand(size, size, size)
                .offset(
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                );
    }

    private Vec3 getRenderPosition(float partialTicks) {
        if (this.currentRenderPosition == null || this.lastRenderPosition == null) {
            return realPosition;
        }
        return new Vec3(
                RenderUtil.lerpDouble(this.currentRenderPosition.xCoord, this.lastRenderPosition.xCoord, partialTicks),
                RenderUtil.lerpDouble(this.currentRenderPosition.yCoord, this.lastRenderPosition.yCoord, partialTicks),
                RenderUtil.lerpDouble(this.currentRenderPosition.zCoord, this.lastRenderPosition.zCoord, partialTicks)
        );
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity || entity instanceof EntityArmorStand || entity.isDead || entity.deathTime > 0) {
            return false;
        }
        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (!this.players.getValue() || TeamUtil.isFriend(player)) {
                return false;
            }
            return (!this.teams.getValue() || !TeamUtil.isSameTeam(player)) && (!this.botCheck.getValue() || !TeamUtil.isBot(player));
        }
        String entityPackage = entity.getClass().getName();
        if (entityPackage.contains(".monster.") || entityPackage.contains(".boss.")) {
            return this.mobs.getValue();
        }
        if (entityPackage.contains(".passive.")) {
            return this.animals.getValue();
        }
        return false;
    }

    private EntityLivingBase getTarget(double distance) {
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }

        return mc.theWorld.loadedEntityList.stream()
                .filter(EntityLivingBase.class::isInstance)
                .map(EntityLivingBase.class::cast)
                .filter(this::isValidTarget)
                .filter(entity -> getDistanceToEntityBox(entity) <= distance + 0.4D)
                .min(Comparator.comparingDouble(BackTrack::getDistanceToEntityBox))
                .orElse(null);
    }

    private static double getDistanceToEntityBox(Entity entity) {
        return getCustomDistanceToEntityBox(mc.thePlayer.getPositionEyes(1.0F), entity);
    }

    private static double getCustomDistanceToEntityBox(Vec3 eyes, Entity entity) {
        Vec3 pos = getBestHitVec(entity);
        double xDist = Math.abs(pos.xCoord - eyes.xCoord);
        double yDist = Math.abs(pos.yCoord - eyes.yCoord);
        double zDist = Math.abs(pos.zCoord - eyes.zCoord);
        return Math.sqrt(xDist * xDist + yDist * yDist + zDist * zDist);
    }

    private static Vec3 getBestHitVec(Entity entity) {
        return getClosestPointOnBox(mc.thePlayer.getPositionEyes(1.0F), getHitbox(entity));
    }

    private static Vec3 getClosestPointOnBox(Vec3 point, AxisAlignedBB box) {
        double x = MathHelper.clamp_double(point.xCoord, box.minX, box.maxX);
        double y = MathHelper.clamp_double(point.yCoord, box.minY, box.maxY);
        double z = MathHelper.clamp_double(point.zCoord, box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private static double getDistToTargetFromMouseOver(Vec3 eyes, Vec3 look, Entity target, AxisAlignedBB targetBB) {
        double blockReachDistance = 64.0D;
        Vec3 end = eyes.addVector(look.xCoord * blockReachDistance, look.yCoord * blockReachDistance, look.zCoord * blockReachDistance);
        Vec3 vec33 = null;
        MovingObjectPosition movingobjectposition = targetBB.calculateIntercept(eyes, end);

        if (targetBB.isVecInside(eyes)) {
            vec33 = movingobjectposition == null ? eyes : movingobjectposition.hitVec;
        } else if (movingobjectposition != null) {
            vec33 = movingobjectposition.hitVec;
        }

        return vec33 == null ? Double.MAX_VALUE : eyes.distanceTo(vec33);
    }

    private static AxisAlignedBB getHitbox(Entity entity) {
        float border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }

    private static Vec3 getPositionVector(Entity entity) {
        return new Vec3(entity.posX, entity.posY, entity.posZ);
    }

    private static Vec3 getServerPositionVector(Entity entity) {
        return new Vec3(entity.serverPosX / 32.0D, entity.serverPosY / 32.0D, entity.serverPosZ / 32.0D);
    }

    private static Vec3 getPrevPositionVector(Entity entity) {
        return new Vec3(entity.prevPosX, entity.prevPosY, entity.prevPosZ);
    }

    private static Vec3 getMoveDeltaVector(Entity entity) {
        return subtract(getPositionVector(entity), getPrevPositionVector(entity));
    }

    private static Vec3 add(Vec3 vec, double x, double y, double z) {
        return new Vec3(vec.xCoord + x, vec.yCoord + y, vec.zCoord + z);
    }

    private static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
    }

    private static Vec3 subtract(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord - b.xCoord, a.yCoord - b.yCoord, a.zCoord - b.zCoord);
    }

    private static Vec3 inverse(Vec3 vec) {
        return new Vec3(-vec.xCoord, -vec.yCoord, -vec.zCoord);
    }

    private static Vec3 multiply(Vec3 vec, double factor) {
        return new Vec3(vec.xCoord * factor, vec.yCoord * factor, vec.zCoord * factor);
    }

    private static AxisAlignedBB offset(AxisAlignedBB box, Vec3 vec) {
        return box.offset(vec.xCoord, vec.yCoord, vec.zCoord);
    }

    private static double distance(Entity entity, Vec3 vec) {
        double x = entity.posX - vec.xCoord;
        double y = entity.posY - vec.yCoord;
        double z = entity.posZ - vec.zCoord;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static int randomizeAround(int value) {
        return (int) randomizeDouble(-value, value);
    }

    private static double randomizeDouble(double min, double max) {
        if (min == max) {
            return min;
        }
        if (Math.abs(min) < 1.0D || Math.abs(max) < 1.0D) {
            return Math.random() * (max - min) + min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max + 1.0D);
    }

    private static Vec3 zeroVec() {
        return new Vec3(0.0D, 0.0D, 0.0D);
    }

    private void debugOnlyNeeded(boolean extraCheck, double realDistance, double realDistanceNoPred, double realMouseDistance, double clientDistance, boolean onlyNeeded,
                                 boolean onlyWhenNeeded,
                                 boolean realInRange, boolean maxRange, boolean clientInRange, boolean hurtTimeOk) {
        if (!this.debug.getValue() || !this.onlyWhenNeeded.getValue() || !this.debugTimer.hasTimeElapsed(500L)) {
            return;
        }

        this.debugTimer.reset();
        ChatUtil.sendFormatted(String.format(
                "&7BT dbg target=%s should=%s enabled=%s only=%s extra=%s rd=%.2f rdNo=%.2f rMouse=%.2f cMouse=%.2f atk=%.2f ht=%d/%d oor=%s why=%s%s%s%s%s lagQ=%d",
                this.target == null ? "null" : this.target.getName(),
                shouldLag,
                onlyWhenNeeded,
                onlyNeeded,
                extraCheck,
                realDistance,
                realDistanceNoPred,
                realMouseDistance,
                clientDistance,
                this.attackRange.getValue(),
                this.target == null ? -1 : this.target.hurtTime,
                this.hurtTimeToWork.getValue(),
                this.outOfRange,
                extraCheck ? "" : "E",
                realInRange ? "" : "R",
                maxRange ? "" : "X",
                clientInRange ? "" : "C",
                hurtTimeOk ? "" : "H",
                BackTrackLagUtils.size()
        ));
    }

    private void debugNoTarget() {
        if (!this.debug.getValue() || !this.onlyWhenNeeded.getValue() || !this.debugTimer.hasTimeElapsed(500L)) {
            return;
        }

        this.debugTimer.reset();
        ChatUtil.sendFormatted("&7BT dbg target=null should=false reason=no-target");
    }

    private void stopLaggingForRespawn() {
        BackTrackLagUtils.disable();
        BackTrackLagUtils.dispatch();
        shouldLag = false;
        this.dispatched = true;
        this.outOfRange = false;
        this.attacked = false;
        this.target = null;
        this.lastTarget = null;
    }

    private enum PacketDirection {
        INCOMING,
        OUTGOING
    }

    private static final class BackTrackLagUtils {
        private static final long DEFAULT_TIMER_DELAY = 100L;
        private static final long BLINK_DELAY = 9999999L;
        private static final Queue<TimedPacket> packets = new ConcurrentLinkedQueue<>();
        private static final DemiseTimer enabledTimer = new DemiseTimer();
        private static boolean enabled;
        private static long delayAmount;
        private static boolean post;

        private static void onPacket(PacketEvent event, PacketDirection direction) {
            if (!event.isCancelled() && enabled && shouldHandlePacket(event.getPacket())) {
                event.setCancelled(true);
                packets.add(new TimedPacket(event.getPacket(), direction));
            }
        }

        private static void onPreTick() {
            if (!post) {
                sendPackets();
            }
        }

        private static void onPostTick() {
            if (post) {
                sendPackets();
            }
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

        private static void spoof(int amount, boolean regular, boolean velocity, boolean teleports, boolean players, boolean action, boolean movement) {
            enabledTimer.reset();
            PacketType.REGULAR.enabled = regular;
            PacketType.VELOCITY.enabled = velocity;
            PacketType.TELEPORTS.enabled = teleports;
            PacketType.PLAYERS.enabled = players;
            PacketType.ACTION.enabled = action;
            PacketType.MOVEMENT.enabled = movement;
            post = true;
            delayAmount = amount;
        }

        private static void dispatch() {
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

        private static void disable() {
            enabled = false;
            enabledTimer.setTime(enabledTimer.getTime() - BLINK_DELAY);
        }

        private static boolean shouldHandlePacket(Packet<?> packet) {
            return Arrays.stream(PacketType.values()).anyMatch(type -> type.enabled && type.containsPacket(packet.getClass()));
        }

        private static int size() {
            return packets.size();
        }

        private static void queue(TimedPacket timedPacket) {
            if (timedPacket.direction == PacketDirection.OUTGOING) {
                PacketUtil.sendPacketNoEvent(timedPacket.packet);
            } else {
                PacketUtil.receivePacketNoEvent(timedPacket.packet);
            }
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
        private final PacketDirection direction;
        private final long millis;

        private TimedPacket(Packet<?> packet, PacketDirection direction) {
            this.packet = packet;
            this.direction = direction;
            this.millis = System.currentTimeMillis();
        }
    }

    private static final class DemiseTimer {
        private long lastMS = System.currentTimeMillis();

        private void reset() {
            this.lastMS = System.currentTimeMillis();
        }

        private boolean hasTimeElapsed(long time) {
            return System.currentTimeMillis() - this.lastMS > time;
        }

        private long getTime() {
            return System.currentTimeMillis() - this.lastMS;
        }

        private void setTime(long time) {
            this.lastMS = time;
        }
    }
}
