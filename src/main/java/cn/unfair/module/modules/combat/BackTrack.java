package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.module.Module;
import cn.unfair.module.modules.movement.Stuck;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.*;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.util.client.ChatUtil;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.player.PacketUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.client.TimerUtil;
import cn.unfair.util.rotation.RotationUtil;
import cn.unfair.util.rotation.advanced.AdvancedRotationMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static Vec3 realPosition = zeroVec();
    public static Vec3 realLastPos = zeroVec();
    public static boolean shouldLag;
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Classic", "Rise", "B4"});
    public final BooleanProperty onlyWhenNeeded = new BooleanProperty("OnlyWhenNeeded", false, this::isClassic);
    public final FloatProperty attackRange = new FloatProperty("AttackRange", 3.0F, 0.1F, 8.0F, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final FloatProperty rangeStart = new FloatProperty("RangeStart", 3.0F, 1.0F, 8.0F, () -> this.isClassic() && !this.onlyWhenNeeded.getValue());
    public final FloatProperty rangeEnd = new FloatProperty("RangeEnd", 6.0F, 1.0F, 8.0F, () -> this.isClassic() && !this.onlyWhenNeeded.getValue());
    public final ModeProperty rangeBase = new ModeProperty("RangeBase", 0, new String[]{"MouseOver", "HurtTime", "Attack"}, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final BooleanProperty attackTickFix = new BooleanProperty("AttackTickFix", false, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final IntProperty predictionTicks = new IntProperty("PredictionTicks", 1, 0, 10, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final IntProperty hurtTimeToWork = new IntProperty("HurtTimeToWork", 3, 0, 10, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final BooleanProperty extraCheck = new BooleanProperty("ExtraCheck", true, this::isClassic);
    public final IntProperty ms = new IntProperty("DelayMs", 50, 0, 1000, () -> this.isClassic() || this.isB4());
    public final BooleanProperty extraMS = new BooleanProperty("ExtraMs", false, this::isClassic);
    public final IntProperty maxPingSpoof = new IntProperty("MaxPingSpoof", 1000, 50, 2000, this::isLegitReach);
    public final BooleanProperty renderRealLocation = new BooleanProperty("RenderRealLocation", true, this::isLegitReach);
    public final ModeProperty esp = new ModeProperty("RenderMode", 1, new String[]{"FakePlayer", "Box", "OnlineBox", "None"}, this::isClassic);
    public final ModeProperty boxColor = new ModeProperty("BoxColor", 1, new String[]{"Default", "Hud", "Custom"}, () -> (this.isClassic() && (this.esp.getValue() == 1 || this.esp.getValue() == 2)) || this.isB4());
    public final ColorProperty boxCustomColor = new ColorProperty("BoxCustomColor", new Color(0, 0, 0).getRGB(), () -> ((this.isClassic() && (this.esp.getValue() == 1 || this.esp.getValue() == 2)) || this.isB4()) && this.boxColor.getValue() == 2);
    public final FloatProperty outlineWidth = new FloatProperty("OutlineWidth", 1.0F, 0.0F, 5.0F, () -> this.isClassic() && this.esp.getValue() == 2);
    public final BooleanProperty players = new BooleanProperty("Players", true);
    public final BooleanProperty mobs = new BooleanProperty("Mobs", false);
    public final BooleanProperty animals = new BooleanProperty("Animals", false);
    public final FloatProperty b4RangeMin = new FloatProperty("DistanceMin", 3.0F, 1.0F, 8.0F, this::isB4);
    public final FloatProperty b4RangeMax = new FloatProperty("DistanceMax", 6.0F, 1.0F, 8.0F, this::isB4);
    public final FloatProperty cooldown = new FloatProperty("Cooldown", 0.0F, 0.0F, 10.0F, this::isB4);
    public final FloatProperty fov = new FloatProperty("FOV", 360.0F, 1.0F, 360.0F, this::isB4);
    public final BooleanProperty disableAdventure = new BooleanProperty("DisableAdventure", false, this::isB4);
    public final BooleanProperty disableWhileChasing = new BooleanProperty("DisableWhileChasing", false, this::isB4);
    public final BooleanProperty flushOnDamage = new BooleanProperty("FlushOnDamage", true, this::isB4);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("IgnoreTeammates", false, this::isB4);
    public final BooleanProperty showServerPosition = new BooleanProperty("ShowServerPosition", true, this::isB4);
    public final BooleanProperty onlyHittable = new BooleanProperty("OnlyHittable", false, this::isB4);
    public final BooleanProperty onlyMovement = new BooleanProperty("OnlyMovement", false, this::isB4);
    public final BooleanProperty weaponOnly = new BooleanProperty("WeaponOnly", true, this::isB4);
    private final TimerUtil attackTimer = new TimerUtil();
    private final TreeMap<Integer, List<Packet<?>>> b4Held = new TreeMap<>();
    private final Object b4Lock = new Object();
    private volatile int b4Tick;
    private volatile int b4DelayTicks;
    private volatile boolean b4Holding;
    private volatile boolean b4Ready;
    private volatile boolean b4VelocityDelay;
    private volatile boolean b4Stuck;
    private volatile EntityLivingBase b4Target;
    private volatile Vec3 b4ServerPos = zeroVec();
    private volatile Packet<?> b4Processing;
    private volatile long b4LastFlush;
    private volatile double b4MinRange;
    private volatile double b4MaxRangeSq;
    private volatile double b4ScanRangeSq;
    public boolean isBackTracking;
    private Vec3 lastRenderPos;
    private Vec3 currentRenderPos;
    private EntityLivingBase target;
    private EntityLivingBase lastTarget;
    private boolean dispatched;
    private boolean outOfRange;
    private boolean attacked;
    private boolean velocityDelayWasActive;
    private int activeMode;

    public BackTrack() {
        super("BackTrack", false);
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
        return RotationUtil.getClosestPointOnBox(mc.thePlayer.getPositionEyes(1.0F), AdvancedRotationMath.getHitbox(entity));
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

    private static Vec3 getPositionVector(Entity entity) {
        return new Vec3(entity.posX, entity.posY, entity.posZ);
    }

    private static Vec3 getServerPositionVector(Entity entity) {
        return new Vec3(entity.serverPosX / 32.0D, entity.serverPosY / 32.0D, entity.serverPosZ / 32.0D);
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

    private static AxisAlignedBB offset(AxisAlignedBB box, Vec3 vec) {
        return box.offset(vec.xCoord, vec.yCoord, vec.zCoord);
    }

    private static double distance(Entity entity, Vec3 vec) {
        double x = entity.posX - vec.xCoord;
        double y = entity.posY - vec.yCoord;
        double z = entity.posZ - vec.zCoord;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static Vec3 zeroVec() {
        return new Vec3(0.0D, 0.0D, 0.0D);
    }

    private boolean isClassic() {
        return this.mode.getValue() == 0;
    }

    private boolean isLegitReach() {
        return this.mode.getValue() == 1;
    }

    private boolean isB4() {
        return this.mode.getValue() == 2;
    }

    private int getDelayMs() {
        return Math.max(0, this.ms.getValue());
    }

    @Override
    public void onEnabled() {
        this.activeMode = this.mode.getValue();
        realPosition = zeroVec();
        realLastPos = zeroVec();
        this.lastRenderPos = null;
        this.currentRenderPos = null;
        this.lastTarget = null;
        this.isBackTracking = false;
        this.velocityDelayWasActive = false;
        synchronized (this.b4Lock) {
            this.b4Held.clear();
            this.b4Reset();
        }
    }

    @Override
    public void onDisabled() {
        Unfair.lagManager.setBackTrackState(false, 0);
        shouldLag = false;
        this.b4Flush();
        realPosition = null;
        realLastPos = null;
        this.lastRenderPos = null;
        this.currentRenderPos = null;
        this.target = null;
        this.lastTarget = null;
        this.isBackTracking = false;
        this.velocityDelayWasActive = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{(this.isClassic() || this.isB4() ? this.getDelayMs() : this.maxPingSpoof.getValue()) + "ms"};
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        this.checkModeChange();
        if (event.type() == EventType.POST) {
            EntityLivingBase active = this.isB4() ? this.b4Target : this.target;
            if (active != null && realPosition != null) {
                this.lastRenderPos = this.currentRenderPos;
                this.currentRenderPos = realPosition;
            } else {
                this.lastRenderPos = null;
                this.currentRenderPos = null;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST) {
            return;
        }

        this.checkModeChange();
        if (this.isClassic()) {
            this.runBackTrack();
        } else if (this.isB4()) {
            this.runB4();
        } else {
            this.runLegitReach();
        }
    }

    private void runLegitReach() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetTargetState();
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }

        if (mc.thePlayer.isDead || mc.currentScreen instanceof GuiGameOver) {
            this.stopLaggingForRespawn();
            return;
        }

        if (this.velocityDelayWasActive) {
            this.velocityDelayWasActive = false;
            if (this.target != null) {
                realPosition = getPositionVector(this.target);
                realLastPos = realPosition;
            }
        }
        if (this.isVelocityDelaying()) {
            this.pauseForVelocityDelay();
            return;
        }

        EntityLivingBase newTarget = this.getTarget(9.0D);
        if (newTarget == null) {
            this.resetTargetState();
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }

        if (newTarget != this.target || realPosition == null) {
            this.target = newTarget;
            this.lastTarget = newTarget;
            realPosition = getPositionVector(newTarget);
            realLastPos = realPosition;
            this.lastRenderPos = realPosition;
            this.currentRenderPos = realPosition;
        }

        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (!mc.thePlayer.isSwingInProgress && (killAura == null || !killAura.isEnabled())) {
            shouldLag = false;
            this.isBackTracking = false;
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }

        double realDistance = distance(mc.thePlayer, realPosition);
        double clientDistance = this.target.getDistanceToEntity(mc.thePlayer);
        shouldLag = realDistance > clientDistance && realDistance > 2.3D && realDistance < 5.9D;
        this.isBackTracking = shouldLag;

        Unfair.lagManager.setBackTrackState(false, 0);
    }

    private void runBackTrack() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.isBackTracking = false;
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }

        if (mc.thePlayer.isDead || mc.currentScreen instanceof GuiGameOver) {
            this.stopLaggingForRespawn();
            return;
        }

        if (this.velocityDelayWasActive) {
            this.velocityDelayWasActive = false;
            if (this.target != null) {
                realPosition = getServerPositionVector(this.target);
                realLastPos = realPosition;
            }
        }
        if (this.isVelocityDelaying()) {
            this.pauseForVelocityDelay();
            return;
        }

        if (this.ms.getValue() > (this.extraMS.getValue() ? 5000 : 1000)) {
            this.ms.setValue(this.extraMS.getValue() ? 5000 : 1000);
        }

        this.target = getTarget(8.0D);
        if (this.target == null) {
            this.lastTarget = null;
            this.isBackTracking = false;
            Unfair.lagManager.setBackTrackState(false, 0);
            return;
        }
        if (this.target != this.lastTarget || realPosition == null || realLastPos == null) {
            realPosition = getServerPositionVector(this.target);
            realLastPos = realPosition;
            this.lastRenderPos = realPosition;
            this.currentRenderPos = realPosition;
            this.lastTarget = this.target;
        }

        Vec3 pred = AdvancedRotationMath.multiply(subtract(getPositionVector(this.target), AdvancedRotationMath.getPrevPositionVector(this.target)), this.predictionTicks.getValue());
        double realDistance = getCustomDistanceToEntityBox(
                add(add(realPosition, 0.0D, this.target.getEyeHeight(), 0.0D), AdvancedRotationMath.multiply(AdvancedRotationMath.getMoveDeltaVector(this.target), this.predictionTicks.getValue())),
                mc.thePlayer
        );
        double clientDistance = getDistToTargetFromMouseOver(
                mc.thePlayer.getPositionEyes(1.0F),
                mc.thePlayer.getLook(1.0F),
                this.target,
                offset(offset(AdvancedRotationMath.getHitbox(this.target), inverse(getPositionVector(this.target))), add(getPositionVector(this.target), pred))
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
                    this.outOfRange = this.attacked && (!this.attackTickFix.getValue() || this.attackTimer.hasTimeElapsed(Math.min(this.getDelayMs(), 100)));
                    break;
            }
        }

        boolean distanceCheck = distance(mc.thePlayer, getPositionVector(this.target)) > distance(mc.thePlayer, AdvancedRotationMath.getPrevPositionVector(this.target));
        boolean extraCheck = distanceCheck || !this.extraCheck.getValue();
        boolean onlyNeeded = extraCheck
                && (realDistance > this.attackRange.getValue() || this.outOfRange)
                && realDistance < 4.5D
                && clientDistance <= 3.0D
                && this.target.hurtTime <= this.hurtTimeToWork.getValue();
        boolean on = extraCheck && realDistance > this.rangeStart.getValue() && realDistance < this.rangeEnd.getValue();

        shouldLag = this.onlyWhenNeeded.getValue() ? onlyNeeded : on;
        this.isBackTracking = shouldLag;

        this.attacked = false;
        Unfair.lagManager.setBackTrackState(this.isBackTracking, this.getDelayMs());
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || !this.isClassic() || event.getTarget() != this.target || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return;
        }

        this.attacked = true;
        if (!this.attackTimer.hasTimeElapsed(Math.min(this.getDelayMs(), 100))
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
            if (this.isB4()) {
                this.b4HandlePacket(packet, event);
                return;
            }
            if (this.target != null && realPosition != null) {
                realLastPos = realPosition;

                if (packet instanceof S14PacketEntity s14PacketEntity) {
                    if (s14PacketEntity.getEntityId() == this.target.getEntityId()) {
                        realPosition = realPosition.addVector(s14PacketEntity.func_149062_c() / 32.0D, s14PacketEntity.func_149061_d() / 32.0D, s14PacketEntity.func_149064_e() / 32.0D);
                    }
                } else if (packet instanceof S18PacketEntityTeleport s18PacketEntityTeleport) {
                    if (s18PacketEntityTeleport.getEntityId() == this.target.getEntityId()) {
                        realPosition = new Vec3(s18PacketEntityTeleport.getX() / 32.0D, s18PacketEntityTeleport.getY() / 32.0D, s18PacketEntityTeleport.getZ() / 32.0D);
                    }

                    if (mc.thePlayer != null && s18PacketEntityTeleport.getEntityId() == mc.thePlayer.getEntityId()) {
                        this.dispatched = false;
                        shouldLag = false;
                        this.isBackTracking = false;
                    }
                }
            }
            if (Unfair.lagManager.handleIncomingPacket(event.getPacket())) {
                event.setCancelled(true);
            }
        } else if (event.getType() == EventType.SEND) {
            if (this.isClassic()
                    && packet instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) packet).getAction().equals(C02PacketUseEntity.Action.ATTACK)
                    && this.attackTickFix.getValue()
                    && !this.dispatched
                    && this.onlyWhenNeeded.getValue()) {
                this.attackTimer.reset();
            }
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || (this.target == null && this.b4Target == null) || realPosition == null) {
            return;
        }

        if (this.isB4()) {
            if (!this.b4Holding || !this.showServerPosition.getValue()) {
                return;
            }
            AxisAlignedBB bb = this.getRenderBox(event.partialTicks(), this.b4Target);
            Color color = this.getBoxColor();
            RenderUtil.enableRenderState();
            RenderUtil.drawFilledBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 94);
            RenderUtil.disableRenderState();
            return;
        }
        if (this.isLegitReach()) {
            this.renderLegitReachPosition(event.partialTicks());
            return;
        }
        if (!shouldLag || (this.esp.getValue() != 1 && this.esp.getValue() != 2)) {
            return;
        }

        AxisAlignedBB bb = this.getRenderBox(event.partialTicks(), this.target);
        Color color = this.getBoxColor();
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, color.getRed(), color.getGreen(), color.getBlue());
        if (this.esp.getValue() == 2) {
            RenderUtil.drawBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 255, this.outlineWidth.getValue());
        }
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onRenderEntity(RenderEntityEvent event) {
        if (!this.isEnabled() || !this.isClassic() || this.target == null || this.esp.getValue() != 0 || !shouldLag) {
            return;
        }

        float partialTicks = mc.timer.renderPartialTicks;
        Vec3 renderPosition = this.getRenderPosition(partialTicks).addVector(
                -mc.getRenderManager().getRenderPosX(),
                -mc.getRenderManager().getRenderPosY(),
                -mc.getRenderManager().getRenderPosZ()
        );

        mc.getRenderManager().doRenderEntity(this.target, renderPosition.xCoord, renderPosition.yCoord, renderPosition.zCoord, this.target.rotationYawHead, partialTicks, true);
    }

    private void renderLegitReachPosition(float partialTicks) {
        if (!this.renderRealLocation.getValue()) {
            return;
        }
        Vec3 renderPos = this.getRenderPosition(partialTicks);
        if (renderPos == null) return;

        double expand = 0.14D;
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox()
                .offset(-mc.thePlayer.posX, -mc.thePlayer.posY, -mc.thePlayer.posZ)
                .offset(renderPos.xCoord, renderPos.yCoord, renderPos.zCoord)
                .expand(expand, expand, expand)
                .offset(
                        -mc.getRenderManager().getRenderPosX(),
                        -mc.getRenderManager().getRenderPosY(),
                        -mc.getRenderManager().getRenderPosZ()
                );
        Unfair.moduleManager.modules.get(HUD.class);
        Color color = HUD.getColor(System.currentTimeMillis());
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 50);
        RenderUtil.disableRenderState();
    }

    private Color getBoxColor() {
        EntityLivingBase target = this.isB4() ? this.b4Target : this.target;
        switch (this.boxColor.getValue()) {
            case 1:
                Unfair.moduleManager.modules.get(HUD.class);
                return HUD.getColor(System.currentTimeMillis());
            case 2:
                return new Color(this.boxCustomColor.getValue());
            default:
                return target instanceof EntityPlayer ? TeamUtil.getTeamColor((EntityPlayer) target, 1.0F) : new Color(0, 0, 0);
        }
    }

    private AxisAlignedBB getRenderBox(float partialTicks, EntityLivingBase target) {
        Vec3 position = this.getRenderPosition(partialTicks);
        float size = target.getCollisionBorderSize();
        return new AxisAlignedBB(
                position.xCoord - (double) target.width / 2.0D,
                position.yCoord,
                position.zCoord - (double) target.width / 2.0D,
                position.xCoord + (double) target.width / 2.0D,
                position.yCoord + (double) target.height,
                position.zCoord + (double) target.width / 2.0D
        )
                .expand(size, size, size)
                .offset(
                        -mc.getRenderManager().getRenderPosX(),
                        -mc.getRenderManager().getRenderPosY(),
                        -mc.getRenderManager().getRenderPosZ()
                );
    }

    private Vec3 getRenderPosition(float partialTicks) {
        if (this.currentRenderPos == null || this.lastRenderPos == null) {
            return this.currentRenderPos != null ? this.currentRenderPos : realPosition;
        }
        return new Vec3(
                RenderUtil.lerpDouble(this.currentRenderPos.xCoord, this.lastRenderPos.xCoord, partialTicks),
                RenderUtil.lerpDouble(this.currentRenderPos.yCoord, this.lastRenderPos.yCoord, partialTicks),
                RenderUtil.lerpDouble(this.currentRenderPos.zCoord, this.lastRenderPos.zCoord, partialTicks)
        );
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == mc.thePlayer || entity == mc.thePlayer.ridingEntity || entity instanceof EntityArmorStand || entity.isDead || entity.deathTime > 0) {
            return false;
        }
        if (entity instanceof EntityPlayer player) {
            if (!this.players.getValue() || TeamUtil.isFriend(player)) {
                return false;
            }
            return !TeamUtil.shouldBlockTarget(player);
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

    private void checkModeChange() {
        if (this.activeMode == this.mode.getValue()) {
            return;
        }

        Unfair.lagManager.setBackTrackState(false, 0);
        this.activeMode = this.mode.getValue();
        this.b4Flush();
        this.resetTargetState();
        realPosition = zeroVec();
        realLastPos = zeroVec();
        this.lastRenderPos = null;
        this.currentRenderPos = null;
    }

    private boolean isVelocityDelaying() {
        Module velocity = Unfair.moduleManager.getModule(Velocity.class);
        return velocity instanceof Velocity velocityModule && velocityModule.isDelayingVelocity();
    }

    private void pauseForVelocityDelay() {
        if (this.isBackTracking || shouldLag || !this.dispatched) {
            Unfair.lagManager.setBackTrackState(false, 0);
            this.dispatched = true;
        }
        shouldLag = false;
        this.isBackTracking = false;
        this.velocityDelayWasActive = true;
    }

    private void resetTargetState() {
        shouldLag = false;
        this.isBackTracking = false;
        this.target = null;
        this.lastTarget = null;
        this.lastRenderPos = null;
        this.currentRenderPos = null;
        this.outOfRange = false;
        this.attacked = false;
        this.velocityDelayWasActive = false;
    }

    private void stopLaggingForRespawn() {
        Unfair.lagManager.setBackTrackState(false, 0);
        this.dispatched = true;
        this.b4Flush();
        this.resetTargetState();
    }

    @Override
    public void verifyValue(String string) {
        if (string.equalsIgnoreCase("OnlyMovement") && this.isB4()) {
            this.b4Flush();
        } else if (this.b4RangeMin.getName().equalsIgnoreCase(string) && this.b4RangeMin.getValue() > this.b4RangeMax.getValue()) {
            this.b4RangeMax.setValue(this.b4RangeMin.getValue());
        } else if (this.b4RangeMax.getName().equalsIgnoreCase(string) && this.b4RangeMin.getValue() > this.b4RangeMax.getValue()) {
            this.b4RangeMin.setValue(this.b4RangeMax.getValue());
        }
    }

    private void runB4() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
            synchronized (this.b4Lock) {
                this.b4Held.clear();
                this.b4Reset();
            }
            return;
        }
        this.b4VelocityDelay = this.isVelocityDelaying();
        Stuck stuck = (Stuck) Unfair.moduleManager.getModule(Stuck.class);
        this.b4Stuck = stuck != null && stuck.isEnabled();
        double maxRange = this.b4RangeMax.getValue();
        this.b4MinRange = this.b4RangeMin.getValue();
        this.b4MaxRangeSq = (maxRange + 1.2) * (maxRange + 1.2);
        this.b4ScanRangeSq = (maxRange + 2.5) * (maxRange + 2.5);

        EntityLivingBase e = this.b4FindTarget();
        if (this.b4Holding) {
            if (e != this.b4Target || !this.b4TargetValid()) {
                this.b4Flush();
                return;
            }
            this.b4ReleaseDue();
        } else {
            if (e != null) {
                this.b4Target = e;
                this.b4UpdateServerPos();
                this.b4SyncRenderPos();
            }
            this.b4Ready = this.b4Target != null && this.b4TargetValid();
        }
    }

    private void b4HandlePacket(Packet<?> packet, PacketEvent event) {
        if (packet == this.b4Processing) {
            return;
        }
        try {
            if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) {
                if (this.b4Holding) {
                    synchronized (this.b4Lock) {
                        this.b4Held.clear();
                        this.b4Reset();
                    }
                }
                return;
            }
            boolean flush = false;
            if (this.b4Holding) {
                flush = packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect;
                if (!flush && this.flushOnDamage.getValue() && packet instanceof S12PacketEntityVelocity s12
                        && s12.getEntityID() == mc.thePlayer.getEntityId()) {
                    flush = true;
                }
            } else if (!this.b4Ready) {
                return;
            }
            if (flush) {
                this.b4Flush();
                return;
            }
            Vec3 pos = this.b4ServerPos;
            double x = pos.xCoord;
            double y = pos.yCoord;
            double z = pos.zCoord;
            boolean moves = false;
            boolean teleport = false;
            if (packet instanceof S14PacketEntity packetEntity) {
                if (packetEntity instanceof S14PacketEntity.S15PacketEntityRelMove
                        || packetEntity instanceof S14PacketEntity.S17PacketEntityLookMove) {
                    Entity entity = packetEntity.getEntity(mc.theWorld);
                    if (entity != null && this.b4Target != null && entity.getEntityId() == this.b4Target.getEntityId()) {
                        moves = true;
                        x += (double) packetEntity.func_149062_c() / 32.0;
                        y += (double) packetEntity.func_149061_d() / 32.0;
                        z += (double) packetEntity.func_149064_e() / 32.0;
                    }
                }
            } else if (packet instanceof S18PacketEntityTeleport s18) {
                if (this.b4Target != null && s18.getEntityId() == this.b4Target.getEntityId()) {
                    teleport = true;
                    moves = true;
                    x = (double) s18.getX() / 32.0;
                    y = (double) s18.getY() / 32.0;
                    z = (double) s18.getZ() / 32.0;
                }
            }
            if (moves) {
                flush = true;
                double distSq = mc.thePlayer.getDistanceSq(x, y, z);
                if (distSq <= this.b4MaxRangeSq) {
                    AxisAlignedBB box = this.b4Target.getEntityBoundingBox();
                    AxisAlignedBB newBox = box.offset(x - this.b4Target.posX, y - this.b4Target.posY, z - this.b4Target.posZ);
                    float[] rot = b4ToBox(this.b4Target, newBox);
                    if (!b4RayHits(this.b4Target, newBox, rot[0], rot[1], this.b4MinRange)) {
                        if (b4RayHits(this.b4Target, newBox, rot[0], rot[1], this.b4RangeMax.getValue())) {
                            AxisAlignedBB oldBox = box.offset(pos.xCoord - this.b4Target.posX, pos.yCoord - this.b4Target.posY, pos.zCoord - this.b4Target.posZ);
                            float[] oldRot = b4ToBox(this.b4Target, oldBox);
                            double newDist = b4RayDistanceSq(this.b4Target, newBox, rot[0], rot[1], this.b4RangeMax.getValue());
                            double oldDist = b4RayDistanceSq(this.b4Target, oldBox, oldRot[0], oldRot[1], this.b4RangeMax.getValue() + 2.0);
                            if (oldDist >= 0.0 && newDist > oldDist && (!teleport || newDist - oldDist < 64.0)) {
                                if (!this.b4Holding && this.b4CanStart()) {
                                    this.b4Start();
                                }
                                this.b4ServerPos = new Vec3(x, y, z);
                                this.b4SyncRenderPos();
                                flush = false;
                            }
                        }
                    }
                }
                if (this.b4Holding && flush) {
                    this.b4Flush();
                    return;
                }
            }
            if (this.b4Holding && !(this.onlyMovement.getValue() ? !moves : b4PassesThrough(packet))) {
                this.b4Hold(packet);
                event.setCancelled(true);
            }
        } catch (Throwable throwable) {
            ChatUtil.sendFormatted("&cbacktrack e3 " + throwable.getClass().getSimpleName());
        }
    }

    private static boolean b4PassesThrough(Packet<?> packet) {
        return packet instanceof S02PacketChat || packet instanceof S06PacketUpdateHealth
                || packet instanceof S14PacketEntity.S16PacketEntityLook || packet instanceof S19PacketEntityStatus
                || packet instanceof S22PacketMultiBlockChange || packet instanceof S23PacketBlockChange
                || packet instanceof S29PacketSoundEffect || packet instanceof S2APacketParticles
                || packet instanceof S3BPacketScoreboardObjective || packet instanceof S3CPacketUpdateScore
                || packet instanceof S3DPacketDisplayScoreboard || packet instanceof S3EPacketTeams;
    }

    private boolean b4CanStart() {
        if (this.b4Stuck || this.b4VelocityDelay) {
            return false;
        }
        return this.cooldown.getValue() <= 0.0F
                || Math.abs(System.currentTimeMillis() - this.b4LastFlush) > this.cooldown.getValue() * 1000.0 - 20.0;
    }

    private void b4Start() {
        this.b4DelayTicks = Math.max(1, this.getDelayMs() / 50);
        this.b4UpdateServerPos();
        this.b4SyncRenderPos();
        this.b4Holding = true;
    }

    private void b4UpdateServerPos() {
        EntityLivingBase e = this.b4Target;
        this.b4ServerPos = new Vec3((double) e.serverPosX / 32.0, (double) e.serverPosY / 32.0, (double) e.serverPosZ / 32.0);
    }

    private void b4SyncRenderPos() {
        realLastPos = realPosition;
        realPosition = this.b4ServerPos;
    }

    private void b4Hold(Packet<?> packet) {
        synchronized (this.b4Lock) {
            int n = this.b4Tick + this.b4DelayTicks;
            this.b4Held.computeIfAbsent(n, k -> new ArrayList<>()).add(packet);
        }
    }

    private void b4Flush() {
        ArrayList<Packet<?>> list = null;
        synchronized (this.b4Lock) {
            if (!this.b4Held.isEmpty()) {
                for (List<Packet<?>> packets : this.b4Held.values()) {
                    if (packets.isEmpty()) continue;
                    if (list == null) {
                        list = new ArrayList<>();
                    }
                    list.addAll(packets);
                    packets.clear();
                }
                this.b4Held.clear();
            }
            this.b4Reset();
        }
        if (list != null) {
            this.b4Process(list);
        }
    }

    private void b4ReleaseDue() {
        ArrayList<Packet<?>> list = null;
        synchronized (this.b4Lock) {
            if (!this.b4Held.isEmpty()) {
                Iterator<Map.Entry<Integer, List<Packet<?>>>> it = this.b4Held.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Integer, List<Packet<?>>> entry = it.next();
                    if (this.b4Tick < entry.getKey()) {
                        break;
                    }
                    List<Packet<?>> packets = entry.getValue();
                    if (!packets.isEmpty()) {
                        if (list == null) {
                            list = new ArrayList<>();
                        }
                        list.addAll(packets);
                        packets.clear();
                    }
                    it.remove();
                }
            }
            ++this.b4Tick;
        }
        if (list != null) {
            this.b4Process(list);
        }
    }

    private void b4Process(List<Packet<?>> list) {
        if (list.isEmpty()) {
            return;
        }
        for (Packet<?> packet : list) {
            try {
                this.b4Processing = packet;
                PacketEvent replay = new PacketEvent(EventType.RECEIVE, packet);
                EventManager.call(replay);
                if (!replay.isCancelled()) {
                    PacketUtil.receivePacketNoEvent(packet);
                }
            } catch (Throwable throwable) {
                ChatUtil.sendFormatted("&cbacktrack e4 " + packet.getClass().getSimpleName() + " " + throwable.getClass().getSimpleName());
            } finally {
                this.b4Processing = null;
            }
        }
    }

    private void b4Reset() {
        this.b4Ready = false;
        this.b4Holding = false;
        this.b4Target = null;
        this.b4Tick = 0;
        this.b4LastFlush = System.currentTimeMillis();
    }

    private boolean b4TargetValid() {
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null || mc.isSingleplayer()) {
            return false;
        }
        EntityLivingBase target = this.b4Target;
        if (target == null || mc.theWorld.getEntityByID(target.getEntityId()) == null || target.deathTime != 0) {
            return false;
        }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof AugustusClickGui)) {
            return false;
        }
        if (mc.thePlayer.capabilities.allowFlying || this.disableAdventure.getValue() && !mc.thePlayer.capabilities.allowEdit) {
            return false;
        }
        return !this.weaponOnly.getValue() || holdingWeapon();
    }

    private boolean b4Valid(EntityLivingBase e) {
        if (this.onlyHittable.getValue() && e.hurtTime > 0) {
            return false;
        }
        return !this.disableWhileChasing.getValue() || !b4AwayFromPlayer(e);
    }

    private EntityLivingBase b4FindTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            EntityLivingBase e = killAura.getTarget();
            if (this.b4Valid(e)) {
                return e;
            }
        }
        AimAssist aimAssist = (AimAssist) Unfair.moduleManager.getModule(AimAssist.class);
        if (aimAssist != null && aimAssist.getCurrentTarget() != null) {
            EntityPlayer e = aimAssist.getCurrentTarget();
            if (this.b4Valid(e)) {
                return e;
            }
        }
        float half = this.fov.getValue() / 2.0F;
        double best = 999.0;
        EntityPlayer found = null;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.deathTime != 0) continue;
            if (!this.b4Valid(p)) continue;
            if (TeamUtil.isBot(p)) continue;
            double d = mc.thePlayer.getDistanceSqToEntity(p);
            if (d > this.b4ScanRangeSq) continue;
            double yaw = Math.abs(RotationUtil.getYawBetween(mc.thePlayer.posX, mc.thePlayer.posZ, p.posX, p.posZ));
            if (yaw > half) continue;
            if (TeamUtil.isFriend(p)) continue;
            if (this.ignoreTeammates.getValue() && TeamUtil.isSameTeam(p)) continue;
            if (best != 999.0 && !(yaw < best)) continue;
            best = yaw;
            found = p;
        }
        return found;
    }

    private static float[] b4ToBox(EntityLivingBase entity, AxisAlignedBB box) {
        float f = entity.getCollisionBorderSize();
        AxisAlignedBB expanded = box.expand(f, f, f);
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0f);
        if (expanded.isVecInside(eyes)) {
            return new float[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
        }
        double cx = (expanded.minX + expanded.maxX) / 2.0;
        double cy = expanded.minY + (expanded.maxY - expanded.minY) * 0.825;
        double cz = (expanded.minZ + expanded.maxZ) / 2.0;
        return RotationUtil.getRotations(cx, cy, cz, eyes.xCoord, eyes.yCoord, eyes.zCoord);
    }

    private static boolean b4RayHits(EntityLivingBase entity, AxisAlignedBB box, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith()) {
            return false;
        }
        Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 dir = mc.thePlayer.getVectorForRotation(pitch, yaw);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        float f = entity.getCollisionBorderSize();
        box = box.expand(f, f, f);
        return box.calculateIntercept(from, to) != null;
    }

    private static double b4RayDistanceSq(EntityLivingBase entity, AxisAlignedBB box, float yaw, float pitch, double range) {
        if (entity == null || !entity.canBeCollidedWith()) {
            return -1.0;
        }
        Vec3 from = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 dir = mc.thePlayer.getVectorForRotation(pitch, yaw);
        Vec3 to = from.addVector(dir.xCoord * range, dir.yCoord * range, dir.zCoord * range);
        float f = entity.getCollisionBorderSize();
        box = box.expand(f, f, f);
        MovingObjectPosition hit = box.calculateIntercept(from, to);
        if (hit == null) {
            return -1.0;
        }
        return from.squareDistanceTo(hit.hitVec);
    }

    private static boolean b4AwayFromPlayer(EntityLivingBase entity) {
        double d = mc.thePlayer.getDistanceSqToEntity(entity);
        return d > 1.0 && !b4FacingPlayer(entity);
    }

    private static boolean b4FacingPlayer(EntityLivingBase entity) {
        float f = b4YawBetween(entity.posX, entity.posZ, mc.thePlayer.posX, mc.thePlayer.posZ);
        double d = MathHelper.wrapAngleTo180_double((double) (f - entity.rotationYaw));
        return Math.abs(d) <= 90.0;
    }

    private static float b4YawBetween(double fromX, double fromZ, double toX, double toZ) {
        return (float) (-Math.toDegrees(Math.atan2(toX - fromX, toZ - fromZ)));
    }

    private static boolean holdingWeapon() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack == null) {
            return false;
        }
        return ItemUtil.isSword(stack) || ItemUtil.isTool(stack, "axe");
    }
}