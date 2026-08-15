package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.*;
import cn.unfair.util.player.BackTrackLagUtils;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.TimerUtil;
import cn.unfair.util.AnimationUtil;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.awt.*;
import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

public class BackTrack extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static Vec3 realPosition = zeroVec();
    public static Vec3 realLastPos = zeroVec();
    public static boolean shouldLag;
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Classic", "Rise", "LiquidBounce"}) {
        @Override
        public boolean read(JsonObject jsonObject) {
            String configuredMode = jsonObject.get(this.getName()).getAsString();
            if (configuredMode.equalsIgnoreCase("FAKE_PLAYER")
                    || configuredMode.equalsIgnoreCase("BOX")
                    || configuredMode.equalsIgnoreCase("NONE")) {
                BackTrack.this.esp.parseString(configuredMode);
                return this.setValue(0);
            }
            return super.read(jsonObject);
        }
    };
    public final BooleanProperty onlyWhenNeeded = new BooleanProperty("Only When Needed", false, this::isClassic);
    public final FloatProperty attackRange = new FloatProperty("Attack Range", 3.0F, 0.1F, 8.0F, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final FloatProperty rangeStart = new FloatProperty("Range Start", 3.0F, 1.0F, 8.0F, () -> this.isClassic() && !this.onlyWhenNeeded.getValue());
    public final FloatProperty rangeEnd = new FloatProperty("Range End", 6.0F, 1.0F, 8.0F, () -> this.isClassic() && !this.onlyWhenNeeded.getValue());
    public final ModeProperty rangeBase = new ModeProperty("Range Base", 0, new String[]{"MouseOver", "HurtTime", "Attack"}, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final BooleanProperty attackTickFix = new BooleanProperty("Attack Tick Fix", false, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final IntProperty predictionTicks = new IntProperty("Prediction Ticks", 1, 0, 10, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final IntProperty hurtTimeToWork = new IntProperty("Hurt Time To Work", 3, 0, 10, () -> this.isClassic() && this.onlyWhenNeeded.getValue());
    public final BooleanProperty extraCheck = new BooleanProperty("Extra Check", true, this::isClassic);
    public final IntProperty ms = new IntProperty("Delay Ms", 50, 0, 1000, this::isClassic);
    public final BooleanProperty extraMS = new BooleanProperty("Extra Ms", false, this::isClassic);
    public final IntProperty extraRand = new IntProperty("Extra Rand", 50, 0, 500, this::isClassic);
    public final IntProperty delayForNextLag = new IntProperty("Delay For Next Lag", 0, 0, 1000, this::isClassic);
    public final IntProperty maxPingSpoof = new IntProperty("Max Ping Spoof", 1000, 50, 2000, this::isLegitReach);
    public final BooleanProperty renderRealLocation = new BooleanProperty("Render Real Location", true, this::isLegitReach);
    public final ModeProperty esp = new ModeProperty("Render Mode", 1, new String[]{"FakePlayer", "Box", "None"}, this::isClassic);
    public final BooleanProperty players = new BooleanProperty("Players", true);
    public final BooleanProperty mobs = new BooleanProperty("Mobs", false);
    public final BooleanProperty animals = new BooleanProperty("Animals", false);
    public final ModeProperty boxColor = new ModeProperty("Box Color", 0, new String[]{"Default", "Hud", "Custom"}, () -> this.isClassic() && this.esp.getValue() == 1);
    public final ColorProperty boxCustomColor = new ColorProperty("Box Custom Color", new Color(0, 0, 0).getRGB(), () -> this.isClassic() && this.esp.getValue() == 1 && this.boxColor.getValue() == 2);
    public final FloatProperty outlineWidth = new FloatProperty("Outline Width", 1.0F, 0.1F, 5.0F, () -> this.isClassic() && this.esp.getValue() == 1);
    public final IntProperty liquidbounceMinDelay = new IntProperty("Min Delay", 80, 0, 2000, this::isLiquidBounce);
    public final IntProperty liquidbounceMaxDelay = new IntProperty("Max Delay", 80, 0, 2000, this::isLiquidBounce);
    public final FloatProperty liquidbounceDistanceMin = new FloatProperty("Distance Min", 2.0F, 0.0F, 6.0F, this::isLiquidBounce);
    public final FloatProperty liquidbounceDistanceMax = new FloatProperty("Distance Max", 3.0F, 0.0F, 6.0F, this::isLiquidBounce);
    private final TimerUtil relagTimer = new TimerUtil();
    private final TimerUtil attackTimer = new TimerUtil();
    private Vec3 animatedPosition;
    private long animatedFrameTime;
    public boolean isBackTracking;
    private EntityLivingBase target;
    private EntityLivingBase lastTarget;
    private Vec3 lastRenderPosition;
    private Vec3 currentRenderPosition;
    private boolean dispatched;
    private boolean outOfRange;
    private boolean attacked;
    private int nextRand;
    private int activeMode;

    public BackTrack() {
        super("BackTrack", false);
    }

    private boolean isClassic() {
        return this.mode.getValue() == 0;
    }

    private boolean isLegitReach() {
        return this.mode.getValue() == 1;
    }

    private boolean isLiquidBounce() {
        return this.mode.getValue() == 2;
    }

    private void configureLiquidBounce() {
        BackTrackLagUtils.liquidbounceConfigure(
                this.isEnabled() && this.isLiquidBounce(),
                this.liquidbounceMinDelay.getValue(),
                this.liquidbounceMaxDelay.getValue(),
                this.liquidbounceDistanceMin.getValue(),
                this.liquidbounceDistanceMax.getValue()
        );
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

    private int getDelayMs() {
        return Math.max(0, this.ms.getValue() + this.nextRand);
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

    @Override
    public void onEnabled() {
        this.activeMode = this.mode.getValue();
        realPosition = zeroVec();
        realLastPos = zeroVec();
        this.lastRenderPosition = null;
        this.currentRenderPosition = null;
        this.lastTarget = null;
        this.isBackTracking = false;
        this.configureLiquidBounce();
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
        this.isBackTracking = false;
        this.configureLiquidBounce();
    }

    @Override
    public String[] getSuffix() {
        if (this.isClassic()) {
            return new String[]{this.getDelayMs() + "ms"};
        } else if (this.isLiquidBounce()) {
            return new String[]{BackTrackLagUtils.getLiquidbounceDelay() + "ms"};
        } else {
            return new String[]{this.maxPingSpoof.getValue() + "ms"};
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        this.checkModeChange();
        if (event.type() == EventType.PRE) {
            BackTrackLagUtils.onPreTick();
        } else if (event.type() == EventType.POST && this.isClassic()) {
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

        this.checkModeChange();
        if (this.isClassic()) {
            this.runBackTrack();
        } else if (this.isLiquidBounce()) {
            BackTrackLagUtils.liquidbounceOnTick();
        } else {
            this.runLegitReach();
        }
    }

    private void runLegitReach() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.resetTargetState();
            BackTrackLagUtils.onPostTick();
            return;
        }

        if (mc.thePlayer.isDead || mc.currentScreen instanceof GuiGameOver) {
            this.stopLaggingForRespawn();
            BackTrackLagUtils.onPostTick();
            return;
        }

        EntityLivingBase newTarget = this.getTarget(9.0D);
        if (newTarget == null) {
            this.resetTargetState();
            BackTrackLagUtils.onPostTick();
            return;
        }

        if (newTarget != this.target || realPosition == null) {
            this.target = newTarget;
            this.lastTarget = newTarget;
            realPosition = getPositionVector(newTarget);
            realLastPos = realPosition;
            this.resetAnimation(realPosition);
        }

        KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
        if (!mc.thePlayer.isSwingInProgress && (killAura == null || !killAura.isEnabled())) {
            shouldLag = false;
            this.isBackTracking = false;
            BackTrackLagUtils.onPostTick();
            return;
        }

        double realDistance = distance(mc.thePlayer, realPosition);
        double clientDistance = this.target.getDistanceToEntity(mc.thePlayer);
        shouldLag = realDistance > clientDistance && realDistance > 2.3D && realDistance < 5.9D;
        this.isBackTracking = shouldLag;

        if (shouldLag) {
            BackTrackLagUtils.spoof(this.maxPingSpoof.getValue(), true, true, true, true, false, false);
            this.dispatched = false;
        } else if (!this.dispatched) {
            BackTrackLagUtils.disable();
            BackTrackLagUtils.dispatch();
            this.dispatched = true;
        }
        BackTrackLagUtils.onPostTick();
    }

    private void runBackTrack() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.isBackTracking = false;
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
            this.isBackTracking = false;
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
                    this.outOfRange = this.attacked && (!this.attackTickFix.getValue() || this.attackTimer.hasTimeElapsed(Math.min(this.getDelayMs(), 100)));
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
        this.isBackTracking = shouldLag;

        if (shouldLag) {
            if (this.relagTimer.hasTimeElapsed(this.delayForNextLag.getValue())) {
                BackTrackLagUtils.spoof(this.getDelayMs(), true, true, true, true, false, false);
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
        if (!this.isEnabled()) {
            return;
        }

        if (this.isLiquidBounce()) {
            if (event.getTarget() instanceof EntityLivingBase living && this.isValidTarget(living)) {
                BackTrackLagUtils.liquidbounceSetTarget(living);
            }
            return;
        }

        if (!this.isClassic() || event.getTarget() != this.target || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
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

        if (this.isLiquidBounce()) {
            BackTrackLagUtils.liquidbounceOnPacket(event);
            return;
        }

        if (event.getType() == EventType.RECEIVE) {
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
            BackTrackLagUtils.onPacket(event, false);
        } else if (event.getType() == EventType.SEND) {
            if (this.isClassic()
                    && packet instanceof C02PacketUseEntity
                    && ((C02PacketUseEntity) packet).getAction().equals(C02PacketUseEntity.Action.ATTACK)
                    && this.attackTickFix.getValue()
                    && !this.dispatched
                    && this.onlyWhenNeeded.getValue()) {
                this.attackTimer.reset();
            }
            BackTrackLagUtils.onPacket(event, true);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        if (this.isLiquidBounce()) {
            this.renderLiquidBounce();
            return;
        }

        if (this.target == null || realPosition == null) {
            return;
        }

        if (this.isLegitReach()) {
            this.renderLegitReachPosition();
            return;
        }
        if (!shouldLag || this.esp.getValue() != 1) {
            return;
        }

        AxisAlignedBB bb = this.getRenderBox(event.partialTicks());
        Color color = this.getBoxColor();
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.drawBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 255, this.outlineWidth.getValue());
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

    private void renderLegitReachPosition() {
        if (!this.renderRealLocation.getValue()) {
            return;
        }

        long now = AnimationUtil.start();
        if (this.animatedPosition == null) {
            this.resetAnimation(realPosition);
        }

        long deltaMillis = Math.min(50L, Math.max(0L, now - this.animatedFrameTime));
        this.animatedPosition = new Vec3(
                AnimationUtil.smooth(this.animatedPosition.xCoord, realPosition.xCoord, deltaMillis, 65.0D),
                AnimationUtil.smooth(this.animatedPosition.yCoord, realPosition.yCoord, deltaMillis, 65.0D),
                AnimationUtil.smooth(this.animatedPosition.zCoord, realPosition.zCoord, deltaMillis, 65.0D)
        );
        this.animatedFrameTime = now;

        double expand = 0.14D;
        AxisAlignedBB bb = mc.thePlayer.getEntityBoundingBox()
                .offset(-mc.thePlayer.posX, -mc.thePlayer.posY, -mc.thePlayer.posZ)
                .offset(this.animatedPosition.xCoord, this.animatedPosition.yCoord, this.animatedPosition.zCoord)
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

    private void renderLiquidBounce() {
        EntityLivingBase target = BackTrackLagUtils.getLiquidbounceTarget();
        if (target == null || !BackTrackLagUtils.isLiquidbounceBacktracking()) {
            return;
        }
        AxisAlignedBB bb = getHitbox(target).offset(
                -mc.getRenderManager().getRenderPosX(),
                -mc.getRenderManager().getRenderPosY(),
                -mc.getRenderManager().getRenderPosZ()
        );
        Color color = target instanceof EntityPlayer ? TeamUtil.getTeamColor((EntityPlayer) target, 1.0F) : new Color(0, 255, 0);
        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 50);
        RenderUtil.drawBoundingBox(bb, color.getRed(), color.getGreen(), color.getBlue(), 255, this.outlineWidth.getValue());
        RenderUtil.disableRenderState();
    }

    private Color getBoxColor() {
        switch (this.boxColor.getValue()) {
            case 1:
                Unfair.moduleManager.modules.get(HUD.class);
                return HUD.getColor(System.currentTimeMillis());
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
                        -mc.getRenderManager().getRenderPosX(),
                        -mc.getRenderManager().getRenderPosY(),
                        -mc.getRenderManager().getRenderPosZ()
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

        BackTrackLagUtils.disable();
        BackTrackLagUtils.dispatch();
        this.activeMode = this.mode.getValue();
        this.resetTargetState();
        realPosition = zeroVec();
        realLastPos = zeroVec();
        this.animatedPosition = null;
        this.animatedFrameTime = 0L;
        this.configureLiquidBounce();
    }

    private void resetTargetState() {
        shouldLag = false;
        this.isBackTracking = false;
        this.target = null;
        this.lastTarget = null;
        this.lastRenderPosition = null;
        this.currentRenderPosition = null;
        this.outOfRange = false;
        this.attacked = false;
    }

    private void resetAnimation(Vec3 position) {
        this.animatedPosition = position;
        this.animatedFrameTime = AnimationUtil.start();
    }

    private void stopLaggingForRespawn() {
        BackTrackLagUtils.disable();
        BackTrackLagUtils.dispatch();
        this.dispatched = true;
        this.resetTargetState();
    }

}
