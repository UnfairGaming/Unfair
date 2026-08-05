package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventManager;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.misc.BedNuker;
import cn.unfair.module.modules.movement.NoSlow;
import cn.unfair.module.modules.player.AutoBlockIn;
import cn.unfair.module.modules.player.AutoHeal;
import cn.unfair.module.modules.player.Scaffold;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.*;
import cn.unfair.util.*;
import cn.unfair.util.killaura.PointFinder;
import cn.unfair.util.player.ApsDelayGenerator;
import com.google.common.base.CaseFormat;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static AttackData target = null;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SINGLE", "SWITCH"});
    public final ModeProperty sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});
    public final ModeProperty autoBlock = new ModeProperty(
            "auto-block", 0, new String[]{"NONE", "VANILLA", "HYPIXEL", "LEGIT", "FAKE", "HYPIXEL_LAG"}
    );
    public final BooleanProperty autoBlockRequirePress = new BooleanProperty("auto-block-require-press", false);
    public final IntProperty autoBlockCPS = new IntProperty("auto-block-aps", 10, 1, 20);
    public final FloatProperty autoBlockRange = new FloatProperty("auto-block-range", 6.0F, 3.0F, 8.0F);
    public final FloatProperty swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 6.0F);
    public final FloatProperty attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
    public final IntProperty fov = new IntProperty("fov", 360, 30, 360);
    public final IntProperty minCPS = new IntProperty("min-aps", 14, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-aps", 14, 1, 20);
    public final IntProperty switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
    public final ModeProperty rotations = new ModeProperty("rotations", 2, new String[]{"NONE", "LEGIT", "SILENT", "LOCK_VIEW"});
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
    public final PercentProperty smoothing = new PercentProperty("smoothing", 0);
    public final IntProperty angleStep = new IntProperty("angle-step", 90, 30, 180);
    public final IntProperty aimSpeedYaw = new IntProperty("aim-speed-yaw", 60, 1, 180, () -> this.rotations.getValue() == 2);
    public final IntProperty aimSpeedPitch = new IntProperty("aim-speed-pitch", 60, 1, 180, () -> this.rotations.getValue() == 2);
    public final BooleanProperty advancedRotations = new BooleanProperty("advanced-rotations", false, () -> this.rotations.getValue() == 2 || this.rotations.getValue() == 3);
    public final BooleanProperty gcdFix = new BooleanProperty("gcd-fix", false, this.advancedRotations::getValue);
    public final FloatProperty hitboxQuality = new FloatProperty("hitbox-quality", 1.0F, 0.01F, 1.0F, this.advancedRotations::getValue);
    public final FloatProperty outOfRangeBuffer = new FloatProperty("out-of-range-buffer", 0.0F, -1.0F, 1.0F, this.advancedRotations::getValue);
    public final ModeProperty preferredBodyPart = new ModeProperty("preferred-body-part", 0, new String[]{"NONE", "HEAD", "TORSO", "FEET"}, this.advancedRotations::getValue);
    public final BooleanProperty blacklistHead = new BooleanProperty("blacklist-head", false, this.advancedRotations::getValue);
    public final BooleanProperty blacklistTorso = new BooleanProperty("blacklist-torso", false, this.advancedRotations::getValue);
    public final BooleanProperty blacklistFeet = new BooleanProperty("blacklist-feet", false, this.advancedRotations::getValue);
    public final BooleanProperty blacklistBadHitVec = new BooleanProperty("blacklist-bad-hitvec", false, this.advancedRotations::getValue);
    public final FloatProperty badHitVecBuffer = new FloatProperty("bad-hitvec-buffer", 0.5F, 0.01F, 2.0F, () -> this.advancedRotations.getValue() && this.blacklistBadHitVec.getValue());
    public final BooleanProperty blacklistHeuristic = new BooleanProperty("blacklist-heuristic", false, this.advancedRotations::getValue);
    public final FloatProperty heuristicBuffer = new FloatProperty("heuristic-buffer", 0.1F, 0.01F, 2.0F, () -> this.advancedRotations.getValue() && this.blacklistHeuristic.getValue());
    public final BooleanProperty dynamicTrim = new BooleanProperty("dynamic-trim", false, this.advancedRotations::getValue);
    public final FloatProperty yTrim = new FloatProperty("y-trim", 0.0F, 0.0F, 0.5F, () -> this.advancedRotations.getValue() && !this.dynamicTrim.getValue());
    public final FloatProperty xzRandAdd = new FloatProperty("xz-rand-add", 0.0F, 0.0F, 0.5F, () -> this.advancedRotations.getValue() && this.dynamicTrim.getValue());
    public final FloatProperty xzTrim = new FloatProperty("xz-trim", 0.0F, 0.0F, 0.5F, this.advancedRotations::getValue);
    public final BooleanProperty predictionEngine = new BooleanProperty("prediction-engine", false, this.advancedRotations::getValue);
    public final BooleanProperty simulateReactionTime = new BooleanProperty("simulate-reaction-time", false, () -> this.advancedRotations.getValue() && this.predictionEngine.getValue());
    public final IntProperty reactionTimeMin = new IntProperty("reaction-time-min", 0, 0, 20, () -> this.advancedRotations.getValue() && this.predictionEngine.getValue() && this.simulateReactionTime.getValue());
    public final IntProperty reactionTimeMax = new IntProperty("reaction-time-max", 10, 0, 20, () -> this.advancedRotations.getValue() && this.predictionEngine.getValue() && this.simulateReactionTime.getValue());
    public final FloatProperty thresholdToApplyReactionTime = new FloatProperty("threshold-to-apply-reaction-time", 0.1F, 0.01F, 1.0F, () -> this.advancedRotations.getValue() && this.predictionEngine.getValue() && this.simulateReactionTime.getValue());
    public final FloatProperty thresholdForDirectionConfidence = new FloatProperty("threshold-for-direction-confidence", 0.1F, 0.01F, 0.5F, () -> this.advancedRotations.getValue() && this.predictionEngine.getValue() && this.simulateReactionTime.getValue());
    public final FloatProperty extraPrediction = new FloatProperty("extra-reaction-prediction", 1.0F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.predictionEngine.getValue() && this.simulateReactionTime.getValue());
    public final BooleanProperty jitter = new BooleanProperty("jitter", false, this.advancedRotations::getValue);
    public final FloatProperty jitterMin = new FloatProperty("jitter-min", 0.5F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.jitter.getValue());
    public final FloatProperty jitterMax = new FloatProperty("jitter-max", 0.5F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.jitter.getValue());
    public final BooleanProperty interpolateJitterVec = new BooleanProperty("interpolate-jitter-vec", false, () -> this.advancedRotations.getValue() && this.jitter.getValue());
    public final ModeProperty lookVec = new ModeProperty("look-vec", 2, new String[]{"CLIENT", "SERVER", "NORMALISED"}, this.advancedRotations::getValue);
    public final ModeProperty offsetMode = new ModeProperty("offset-mode", 0, new String[]{"NONE", "GAUSSIAN", "NOISE", "ADVANCED"}, this.advancedRotations::getValue);
    public final ModeProperty advancedBase = new ModeProperty("advanced-base", 0, new String[]{"GAUSSIAN", "NOISE"}, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() == 3);
    public final IntProperty offsetChance = new IntProperty("offset-chance", 75, 1, 100, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() != 0);
    public final FloatProperty yawFactorMin = new FloatProperty("yaw-factor-min", 0.25F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() != 0);
    public final FloatProperty yawFactorMax = new FloatProperty("yaw-factor-max", 0.25F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() != 0);
    public final FloatProperty pitchFactorMin = new FloatProperty("pitch-factor-min", 0.25F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() != 0);
    public final FloatProperty pitchFactorMax = new FloatProperty("pitch-factor-max", 0.25F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() != 0);
    public final BooleanProperty interpolateVec = new BooleanProperty("interpolate-vec", false, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() != 0);
    public final FloatProperty offsetAmount = new FloatProperty("offset-amount", 0.5F, 0.01F, 1.0F, () -> this.advancedRotations.getValue() && this.interpolateVec.getValue() && this.offsetMode.getValue() != 0);
    public final FloatProperty tolerance = new FloatProperty("tolerance", 0.05F, 0.01F, 0.1F, () -> this.advancedRotations.getValue() && this.offsetMode.getValue() == 3);
    public final BooleanProperty pointStability = new BooleanProperty("point-stability", true, this.advancedRotations::getValue);
    public final FloatProperty pointStabilityAmount = new FloatProperty("point-stability-amount", 0.15F, 0.0F, 1.0F, () -> this.advancedRotations.getValue() && this.pointStability.getValue());
    public final BooleanProperty adaptiveAimSpeed = new BooleanProperty("adaptive-aim-speed", false, () -> this.advancedRotations.getValue() && this.rotations.getValue() == 2);
    public final FloatProperty adaptiveMinSpeed = new FloatProperty("adaptive-min-speed", 0.35F, 0.05F, 1.0F, () -> this.advancedRotations.getValue() && this.rotations.getValue() == 2 && this.adaptiveAimSpeed.getValue());
    public final BooleanProperty overshoot = new BooleanProperty("overshoot", false, this::canUseOvershoot);
    public final IntProperty overshootChance = new IntProperty("overshoot-chance", 100, 0, 100, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootSpeed = new FloatProperty("overshoot-speed", 1.0F, 0.05F, 2.0F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final IntProperty overshootDelay = new IntProperty("overshoot-delay", 2, 0, 20, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootStrength = new FloatProperty("overshoot-strength", 0.45F, 0.0F, 2.0F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootMaxAngle = new FloatProperty("overshoot-max-angle", 4.0F, 0.1F, 15.0F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootTriggerAngle = new FloatProperty("overshoot-trigger-angle", 18.0F, 1.0F, 60.0F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootDamping = new FloatProperty("overshoot-damping", 0.68F, 0.0F, 0.95F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootRecovery = new FloatProperty("overshoot-recovery", 0.35F, 0.05F, 1.0F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootPrediction = new FloatProperty("overshoot-prediction", 0.45F, 0.0F, 1.0F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final FloatProperty overshootRandomness = new FloatProperty("overshoot-randomness", 0.15F, 0.0F, 1.0F, () -> this.canUseOvershoot() && this.overshoot.getValue());
    public final BooleanProperty aimDot = new BooleanProperty("aim-dot", false, this.advancedRotations::getValue);
    public final ColorProperty aimDotColor = new ColorProperty("aim-dot-color", Color.WHITE.getRGB(), () -> this.advancedRotations.getValue() && this.aimDot.getValue());
    public final BooleanProperty throughWalls = new BooleanProperty("through-walls", true);
    public final BooleanProperty requirePress = new BooleanProperty("require-press", false);
    public final BooleanProperty allowMining = new BooleanProperty("allow-mining", false);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", false);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty inventoryCheck = new BooleanProperty("inventory-check", true);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty bosses = new BooleanProperty("bosses", false);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty golems = new BooleanProperty("golems", false);
    public final BooleanProperty silverfish = new BooleanProperty("silverfish", false);
    public final ModeProperty showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "3DBOX"});
    private final BooleanProperty c09Instead = new BooleanProperty("c09-instead", true, () -> this.autoBlock.getValue() == 5);
    private final TimerUtil timer = new TimerUtil();
    private final ApsDelayGenerator apsDelayGenerator = new ApsDelayGenerator();
    private final TimerUtil predictionUpdateTimer = new TimerUtil();
    private final ArrayList<Double> previousTargetMotions = new ArrayList<>();
    private final ArrayList<Long> jitterClicks = new ArrayList<>();
    public boolean attackDisabled = false;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    private int blockTick = 0;
    private float serverYaw;
    private float serverPitch;
    private Vec3 currentVec;
    private Vec3 lastAimVec;
    private Vec3 stableAimRatio;
    private EntityLivingBase stableAimTarget;
    private Vec3 offsetVec = zeroVec();
    private float[] normalisedRot;
    private double lastXOffset;
    private double lastYOffset;
    private double lastZOffset;
    private boolean shouldRandomize;
    private double finalXZTrim;
    private double xzRandShrinkThing;
    private float predictionTargetOffset;
    private EntityLivingBase predictionTrackedEntity;
    private AxisAlignedBB predictionLastTrackedAABB;
    private int predictionTicksExisted;
    private int currentReactionTime;
    private Vec3 predictionLastTrackedMoveDelta = zeroVec();
    private float tremorYaw;
    private float tremorPitch;
    private float targetJitterYaw;
    private float targetJitterPitch;
    private float clickImpulseYaw;
    private float clickImpulsePitch;
    private float jitterYaw;
    private float jitterPitch;
    private int lastJitterTick;
    private boolean easingOut;
    private EntityLivingBase overshootTrackedEntity;
    private float overshootYaw;
    private float overshootPitch;
    private float overshootLastTargetYaw;
    private float overshootLastTargetPitch;
    private float overshootLastErrorYaw;
    private float overshootLastErrorPitch;
    private float overshootNoiseYaw;
    private float overshootNoisePitch;
    private int overshootLastTick;
    private boolean overshootActive;
    private int overshootCooldownUntilTick;

    public KillAura() {
        super("KillAura", false);
    }

    private static Vec3 interpolate(Vec3 prev, Vec3 current, float partialTicks) {
        return new Vec3(
                prev.xCoord + (current.xCoord - prev.xCoord) * partialTicks,
                prev.yCoord + (current.yCoord - prev.yCoord) * partialTicks,
                prev.zCoord + (current.zCoord - prev.zCoord) * partialTicks
        );
    }

    private static AxisAlignedBB contract(AxisAlignedBB bb, double x, double y, double z) {
        return new AxisAlignedBB(bb.minX + x, bb.minY + y, bb.minZ + z, bb.maxX - x, bb.maxY - y, bb.maxZ - z);
    }

    private static Vec3[] vertices(AxisAlignedBB bb) {
        return new Vec3[]{
                new Vec3(bb.minX, bb.minY, bb.minZ), new Vec3(bb.minX, bb.minY, bb.maxZ),
                new Vec3(bb.minX, bb.maxY, bb.minZ), new Vec3(bb.minX, bb.maxY, bb.maxZ),
                new Vec3(bb.maxX, bb.minY, bb.minZ), new Vec3(bb.maxX, bb.minY, bb.maxZ),
                new Vec3(bb.maxX, bb.maxY, bb.minZ), new Vec3(bb.maxX, bb.maxY, bb.maxZ)
        };
    }

    private static Vec3 center(AxisAlignedBB bb) {
        return new Vec3((bb.minX + bb.maxX) / 2.0D, (bb.minY + bb.maxY) / 2.0D, (bb.minZ + bb.maxZ) / 2.0D);
    }

    private static Vec3 getMoveDelta(Entity entity) {
        return new Vec3(entity.posX - entity.prevPosX, entity.posY - entity.prevPosY, entity.posZ - entity.prevPosZ);
    }

    private static double getSpeedPosBased(Entity entity) {
        return Math.hypot(entity.posX - entity.prevPosX, entity.posZ - entity.prevPosZ);
    }

    private static Vec3 getVectorForRotation(float yaw, float pitch) {
        float yawRad = -yaw * 0.017453292F - (float) Math.PI;
        float pitchRad = -pitch * 0.017453292F;
        float cosYaw = MathHelper.cos(yawRad);
        float sinYaw = MathHelper.sin(yawRad);
        float cosPitch = -MathHelper.cos(pitchRad);
        float sinPitch = MathHelper.sin(pitchRad);
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    private static float[] rotationsTo(Vec3 target, Vec3 eyes) {
        double x = target.xCoord - eyes.xCoord;
        double y = target.yCoord - eyes.yCoord;
        double z = target.zCoord - eyes.zCoord;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        return new float[]{
                (float) (Math.atan2(z, x) * 180.0D / Math.PI) - 90.0F,
                (float) (-(Math.atan2(y, dist) * 180.0D / Math.PI))
        };
    }

    private static Vec3 add(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord + b.xCoord, a.yCoord + b.yCoord, a.zCoord + b.zCoord);
    }

    private static Vec3 subtract(Vec3 a, Vec3 b) {
        return new Vec3(a.xCoord - b.xCoord, a.yCoord - b.yCoord, a.zCoord - b.zCoord);
    }

    private static Vec3 multiply(Vec3 vec, double factor) {
        return new Vec3(vec.xCoord * factor, vec.yCoord * factor, vec.zCoord * factor);
    }

    private static Vec3 flat(Vec3 vec) {
        return new Vec3(vec.xCoord, 0.0D, vec.zCoord);
    }

    private static Vec3 zeroVec() {
        return new Vec3(0.0D, 0.0D, 0.0D);
    }

    private static double crossLength(Vec3 a, Vec3 b) {
        double x = a.yCoord * b.zCoord - a.zCoord * b.yCoord;
        double y = a.zCoord * b.xCoord - a.xCoord * b.zCoord;
        double z = a.xCoord * b.yCoord - a.yCoord * b.xCoord;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double x = x1 - x2;
        double y = y1 - y2;
        double z = z1 - z2;
        return Math.sqrt(x * x + y * y + z * z);
    }

    private static float interpolate(float current, float target, float amount) {
        return current + (target - current) * amount;
    }

    private static double randomBetween(float min, float max) {
        return min == max ? min : RandomUtil.nextDouble(Math.min(min, max), Math.max(min, max));
    }

    private long getAttackDelay() {
        return this.isBlocking ? this.apsDelayGenerator.nextDelay(this.autoBlockCPS.getValue(), this.autoBlockCPS.getValue()) : this.apsDelayGenerator.nextDelay(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            } else {
                this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                mc.thePlayer.swingItem();
                if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(target.getBox()))
                        && this.getAttackHitVec(yaw, pitch, this.attackRange.getValue()) == null) {
                    return false;
                } else {
                    AttackEvent event = new AttackEvent(target.getEntity());
                    EventManager.call(event);
                    mc.playerController.syncCurrentPlayItem();
                    PacketUtil.sendPacket(new C02PacketUseEntity(target.getEntity(), Action.ATTACK));
                    if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                        PlayerUtil.attackEntity(target.getEntity());
                    }
                    this.hitRegistered = true;
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void sendUseItem() {
        mc.playerController.syncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
    }

    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (target != null) {
            Vec3 hitVec = this.getAttackHitVec(yaw, pitch, 8.0);
            if (hitVec != null) {
                mc.playerController.syncCurrentPlayItem();
                PacketUtil.sendPacket(
                        new C02PacketUseEntity(
                                target.getEntity(),
                                new Vec3(hitVec.xCoord - target.getX(), hitVec.yCoord - target.getY(), hitVec.zCoord - target.getZ())
                        )
                );
                PacketUtil.sendPacket(new C02PacketUseEntity(target.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }

    private Vec3 getAttackHitVec(float yaw, float pitch, double distance) {
        if (target == null) {
            return null;
        }

        MovingObjectPosition hit = RotationUtil.rayTrace(target.getBox(), yaw, pitch, distance);
        return hit == null ? null : hit.hitVec;
    }

    private boolean canAttack() {
        if (this.attackDisabled) {
            return false;
        }
        if (this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (mc.playerController.getIsHittingBlock()) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) Unfair.moduleManager.modules.get(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedNuker bedNuker = (BedNuker) Unfair.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) Unfair.moduleManager.modules.get(AutoBlockIn.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (Unfair.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean canAutoBlock() {
        if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof EntityLivingBase
                                && this.isValidTarget((EntityLivingBase) entity)
                                && this.isInBlockRange((EntityLivingBase) entity)
                );
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && RotationUtil.rayTrace(entityLivingBase) != null) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return !TeamUtil.shouldBlockTarget((EntityPlayer) entityLivingBase);
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && !TeamUtil.shouldBlockTeamColor(entityLivingBase);
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && !TeamUtil.shouldBlockTeamColor(entityLivingBase);
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }

    private float[] interpolateRotation(float targetYaw, float targetPitch) {
        float maxDeltaYaw = this.aimSpeedYaw.getValue().floatValue();
        float maxDeltaPitch = this.aimSpeedPitch.getValue().floatValue();

        float deltaYaw = targetYaw - this.serverYaw;
        while (deltaYaw <= -180.0F) deltaYaw += 360.0F;
        while (deltaYaw > 180.0F) deltaYaw -= 360.0F;

        float deltaPitch = targetPitch - this.serverPitch;
        if (this.adaptiveAimSpeed.getValue() && this.advancedRotations.getValue()) {
            float angularError = Math.max(Math.abs(deltaYaw), Math.abs(deltaPitch));
            float speedFactor = this.getAdaptiveAimSpeedFactor(angularError);
            maxDeltaYaw *= speedFactor;
            maxDeltaPitch *= speedFactor;
        }

        if (Math.abs(deltaYaw) > maxDeltaYaw) {
            this.serverYaw += Math.signum(deltaYaw) * maxDeltaYaw;
        } else {
            this.serverYaw = targetYaw;
        }

        if (Math.abs(deltaPitch) > maxDeltaPitch) {
            this.serverPitch += Math.signum(deltaPitch) * maxDeltaPitch;
        } else {
            this.serverPitch = targetPitch;
        }

        this.serverPitch = Math.max(-90.0F, Math.min(90.0F, this.serverPitch));

        if (this.gcdFix.getValue() && this.advancedRotations.getValue()) {
            float[] fixed = this.applyGcdFix(this.serverYaw, this.serverPitch, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            this.serverYaw = fixed[0];
            this.serverPitch = fixed[1];
        }

        return new float[]{this.serverYaw, this.serverPitch};
    }

    private float getAdaptiveAimSpeedFactor(float angularError) {
        float min = this.adaptiveMinSpeed.getValue();
        float max = 1.35F;
        float normalizedError = MathHelper.clamp_float(angularError / 90.0F, 0.0F, 1.0F);
        float eased = normalizedError * normalizedError * (3.0F - 2.0F * normalizedError);
        float speedFactor = min + (max - min) * eased;
        if (target != null) {
            double distance = RotationUtil.distanceToBox(target.getBox());
            float closeRangeBoost = (float) MathHelper.clamp_double((this.attackRange.getValue() - distance) / this.attackRange.getValue(), 0.0D, 1.0D);
            speedFactor += closeRangeBoost * 0.15F;
        }
        return MathHelper.clamp_float(speedFactor, min, max);
    }

    private boolean isAtRotation(float yaw, float pitch) {
        return Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.serverYaw)) < 0.01F
                && Math.abs(pitch - this.serverPitch) < 0.01F;
    }

    private float[] applyGcdFix(float yaw, float pitch, float previousYaw, float previousPitch) {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = sensitivity * sensitivity * sensitivity * 1.2F;
        float yawDelta = yaw - previousYaw;
        float pitchDelta = pitch - previousPitch;

        yaw = previousYaw + yawDelta - yawDelta % gcd;
        pitch = previousPitch + pitchDelta - pitchDelta % gcd;
        pitch = MathHelper.clamp_float(pitch, -90.0F, 90.0F);

        return new float[]{yaw, pitch};
    }

    public EntityLivingBase getTarget() {
        return target != null ? target.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean shouldAutoBlock() {
        if (this.isPlayerBlocking() && this.isBlocking) {
            return mc.thePlayer != null
                    && !mc.thePlayer.isInWater()
                    && !mc.thePlayer.isInLava()
                    && (this.autoBlock.getValue() == 2
                    || this.autoBlock.getValue() == 3
                    || this.autoBlock.getValue() == 4
                    || this.autoBlock.getValue() == 5);
        } else {
            return false;
        }
    }

    private boolean shouldKeepSilentRotation() {
        return target != null
                && this.rotations.getValue() == 2
                && (this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 5);
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return mc.thePlayer != null && (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOWEST) //DO NOT CHANGE else if you want to recode raytrace
    public void onUpdate(UpdateEvent event) {
        if (this.easingOut && event.getType() == EventType.PRE) {
            float targetYaw = event.getNewYaw();
            float targetPitch = event.getNewPitch();
            float[] reset = this.interpolateRotation(targetYaw, targetPitch);
            event.setRotation(reset[0], reset[1], 1);
            if (this.moveFix.getValue() != 0) {
                event.setPervRotation(reset[0], 1);
            }
            if (this.isAtRotation(targetYaw, targetPitch)) {
                this.easingOut = false;
            }
            return;
        }
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            this.shouldRandomize = RandomUtil.nextDouble(0.0D, 100.0D) <= this.offsetChance.getValue();
            this.xzRandShrinkThing = Math.random();
            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= 50L;
            }
            boolean attack = target != null && this.canAttack();
            boolean block = attack && this.canAutoBlock();
            if (!block) {
                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
            }
            if (attack) {
                boolean swap = false;
                boolean blocked = false;
                if (block) {
                    switch (this.autoBlock.getValue()) {
                        case 0:
                            if (PlayerUtil.isUsingItem()) {
                                this.isBlocking = true;
                                if (!this.isPlayerBlocking() && !Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    swap = true;
                                }
                            } else {
                                this.isBlocking = false;
                                if (this.isPlayerBlocking() && !Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    this.stopBlock();
                                }
                            }
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.fakeBlockState = false;
                            break;
                        case 1:
                            if (this.hasValidTarget()) {
                                if (!this.isPlayerBlocking() && !Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    swap = true;
                                }
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 2:
                            if (this.hasValidTarget()) {
                                if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            blocked = true;
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            attack = false;
                                            this.blockTick = 2;
                                            break;
                                        case 2:
                                            if (this.isPlayerBlocking()) {
                                                if (Unfair.moduleManager.modules.get(NoSlow.class).isEnabled()) {
                                                    int randomSlot = new Random().nextInt(9);
                                                    while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                                        randomSlot = new Random().nextInt(9);
                                                    }
                                                    Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                                    mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                                }
                                                this.stopBlock();
                                            }
                                            attack = false;
                                            this.blockTick = 0;
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 3:
                            if (this.hasValidTarget()) {
                                if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                                attack = false;
                                            }
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 4:
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = this.hasValidTarget();
                            if (PlayerUtil.isUsingItem()
                                    && !this.isPlayerBlocking()
                                    && !Unfair.playerStateManager.digging
                                    && !Unfair.playerStateManager.placing) {
                                swap = true;
                            }
                            break;
                        case 5:
                            if (this.hasValidTarget()) {
                                if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            blocked = true;
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                if (this.c09Instead.getValue()) {
                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 8 + 1));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                }
                                                this.stopBlock();
                                            }
                                            attack = false;
                                            this.blockTick = 2;
                                            break;
                                        case 2:
                                            Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                if (this.isBlocking) {
                                    this.stopBlock();
                                }
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                    }
                }
                boolean attacked = false;
                if (this.isBoxInSwingRange(target.getBox())) {
                    if (this.rotations.getValue() == 2 || this.rotations.getValue() == 3) {

                        float[] targetRotations;
                        float randomOffset = (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
                        float smoothFactor = (float) this.smoothing.getValue() / 100.0F;
                        targetRotations = this.advancedRotations.getValue()
                                ? this.getAdvancedRotations(event.getYaw(), event.getPitch(), randomOffset, smoothFactor)
                                : RotationUtil.getRotationsToBox(
                                target.getBox(),
                                event.getYaw(),
                                event.getPitch(),
                                randomOffset,
                                smoothFactor
                        );

                        float finalYaw, finalPitch;

                        if (this.rotations.getValue() == 2) {

                            finalYaw = targetRotations[0];
                            finalPitch = targetRotations[1];

                            float[] smoothed = interpolateRotation(finalYaw, finalPitch);
                            event.setRotation(smoothed[0], smoothed[1], 1);
                        } else {

                            finalYaw = targetRotations[0];
                            finalPitch = targetRotations[1];
                            if (this.gcdFix.getValue() && this.advancedRotations.getValue()) {
                                float[] fixed = this.applyGcdFix(finalYaw, finalPitch, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                                finalYaw = fixed[0];
                                finalPitch = fixed[1];
                            }
                            event.setRotation(finalYaw, finalPitch, 1);
                            Unfair.rotationManager.setRotation(finalYaw, finalPitch, 1, true);
                        }

                        if (this.moveFix.getValue() != 0 || this.rotations.getValue() == 3) {
                            event.setPervRotation(event.getNewYaw(), 1);
                        }
                    }
                    if (attack) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                } else if (this.rotations.getValue() == 2 && target != null && !this.shouldKeepSilentRotation()) {

                    float realYaw = mc.thePlayer.rotationYaw;
                    float realPitch = mc.thePlayer.rotationPitch;

                    float[] reset = interpolateRotation(realYaw, realPitch);
                    event.setRotation(reset[0], reset[1], 1);

                    if (this.moveFix.getValue() != 0) {
                        event.setPervRotation(reset[0], 1);
                    }
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        this.sendUseItem();
                    }
                }
                if (blocked) {
                    Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }
            }

            if (this.rotations.getValue() == 2 && !attack && !this.shouldKeepSilentRotation()) {
                float realYaw = mc.thePlayer.rotationYaw;
                float realPitch = mc.thePlayer.rotationPitch;

                float[] reset = interpolateRotation(realYaw, realPitch);
                event.setRotation(reset[0], reset[1], 1);

                if (this.moveFix.getValue() != 0) {
                    event.setPervRotation(reset[0], 1);
                }
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                target = null;
                this.resetAimVec();
                this.resetPredictionEngine();
                this.blockingState = false;
                this.isBlocking = false;
                return;
            }
            switch (event.type()) {
                case PRE:
                    if (target == null
                            || !this.isValidTarget(target.getEntity())
                            || !this.isBoxInAttackRange(target.getBox())
                            || !this.isBoxInSwingRange(target.getBox())
                            || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        this.timer.reset();
                        ArrayList<EntityLivingBase> targets = new ArrayList<>();
                        for (Entity entity : mc.theWorld.loadedEntityList) {
                            if (entity instanceof EntityLivingBase
                                    && this.isValidTarget((EntityLivingBase) entity)
                                    && this.isInRange((EntityLivingBase) entity)) {
                                targets.add((EntityLivingBase) entity);
                            }
                        }
                        if (targets.isEmpty()) {
                            target = null;
                            this.resetAimVec();
                            this.resetPredictionEngine();
                        } else {
                            if (targets.stream().anyMatch(this::isInSwingRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isInAttackRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isPlayerTarget)) {
                                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
                            }
                            targets.sort(
                                    (entityLivingBase1, entityLivingBase2) -> {
                                        int sortBase = 0;
                                        switch (this.sort.getValue()) {
                                            case 1:
                                                sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                                break;
                                            case 2:
                                                sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                                break;
                                            case 3:
                                                sortBase = Float.compare(
                                                        RotationUtil.angleToEntity(entityLivingBase1),
                                                        RotationUtil.angleToEntity(entityLivingBase2)
                                                );
                                        }
                                        return sortBase != 0
                                                ? sortBase
                                                : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                                    }
                            );
                            if (this.mode.getValue() == 1 && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                                this.switchTick = 0;
                            }
                            EntityLivingBase selected = targets.get(this.switchTick);
                            if (target == null || target.getEntity() != selected) {
                                this.resetAimVec();
                                this.resetPredictionEngine();
                            }
                            target = new AttackData(selected);
                        }
                    }
                    if (target != null) {
                        target = new AttackData(target.getEntity());
                    }
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                    }
            }
        }
    }

    private float[] getAdvancedRotations(float yaw, float pitch, float maxAngle, float smoothFactor) {
        EntityLivingBase entity = target.getEntity();
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        AxisAlignedBB bb = this.getAdvancedBox(entity);
        double speedShrinkFactor = Math.min(
                this.xzTrim.getValue(),
                Math.max(getSpeedPosBased(mc.thePlayer) * 0.5D, getSpeedPosBased(entity) * 0.5D)
        ) + this.xzRandShrinkThing * this.xzRandAdd.getValue();
        this.finalXZTrim = this.dynamicTrim.getValue() ? speedShrinkFactor : this.xzTrim.getValue();
        double finalYTrim = this.dynamicTrim.getValue() ? Math.abs(mc.thePlayer.motionY) : this.yTrim.getValue();
        bb = contract(bb, this.finalXZTrim, finalYTrim, this.finalXZTrim);

        if (mc.thePlayer.getEntityBoundingBox().intersectsWith(bb)) {
            AxisAlignedBB playerBox = mc.thePlayer.getEntityBoundingBox();
            double overlapX = Math.min(playerBox.maxX, bb.maxX) - Math.max(playerBox.minX, bb.minX);
            double overlapZ = Math.min(playerBox.maxZ, bb.maxZ) - Math.max(playerBox.minZ, bb.minZ);
            bb = contract(bb, overlapX / 2.0D, 0.0D, overlapZ / 2.0D);
        }
        PointFinder.findPoints(bb, Math.max(1, (int) (PointFinder.POINT_COUNT * this.hitboxQuality.getValue())));
        List<Vec3> hitboxPoints = PointFinder.hitboxPoints;
        double playerToTarget = distance(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, entity.prevPosX, entity.prevPosY, entity.prevPosZ)
                - distance(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, entity.posX, entity.posY, entity.posZ);
        double targetToPlayer = distance(entity.posX, entity.posY, entity.posZ, mc.thePlayer.prevPosX, mc.thePlayer.prevPosY, mc.thePlayer.prevPosZ)
                - distance(entity.posX, entity.posY, entity.posZ, mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        double pred = (playerToTarget + targetToPlayer) / 2.0D;
        boolean outOfRange = RotationUtil.distanceToBox(target.getBox()) > this.attackRange.getValue();

        boolean[] preferred = this.getPreferredHittable(bb, eyes, this.attackRange.getValue() + this.outOfRangeBuffer.getValue() + pred + this.finalXZTrim);
        AxisAlignedBB finalBb = bb;
        hitboxPoints.removeIf(point -> {
            boolean invalid = !this.isValidPoint(point, finalBb, pred, outOfRange, preferred[0], preferred[1], preferred[2]);
            if (invalid) {
                PointFinder.invalidHitboxPoints.add(point);
            }
            return invalid;
        });
        AxisAlignedBB finalBb1 = bb;
        PointFinder.allHitboxPoints.removeIf(point -> this.pointInBlacklistedPos(point, finalBb1, preferred[0], preferred[1], preferred[2]));

        Vec3 lookDir = this.getAdvancedLookVec(yaw, pitch);
        Vec3 vec;
        if (!hitboxPoints.isEmpty()) {
            Vec3 stablePoint = this.getStablePoint(entity, bb);
            hitboxPoints.sort(Comparator.comparingDouble(point -> this.scoreAimPoint(point, eyes, lookDir, stablePoint)));
            if (this.blacklistBadHitVec.getValue() || this.blacklistHeuristic.getValue()) {
                Vec3 badHitVec = hitboxPoints.stream().min(Comparator.comparingDouble(point -> point.distanceTo(eyes))).orElse(center(bb));
                if (this.blacklistHeuristic.getValue()) {
                    hitboxPoints.removeIf(point -> point.distanceTo(badHitVec) < this.heuristicBuffer.getValue()
                            || Math.abs(point.yCoord - badHitVec.yCoord) < this.heuristicBuffer.getValue() / 2.0F);
                }
                if (this.blacklistBadHitVec.getValue()) {
                    hitboxPoints.removeIf(point -> point.distanceTo(badHitVec) > this.badHitVecBuffer.getValue());
                }
                hitboxPoints.sort(Comparator.comparingDouble(point -> this.scoreAimPoint(point, eyes, lookDir, stablePoint)));
            }
            vec = hitboxPoints.isEmpty() ? this.getBackupVec(PointFinder.allHitboxPoints, eyes, lookDir, bb) : hitboxPoints.get(0);
        } else {
            vec = this.getBackupVec(PointFinder.allHitboxPoints, eyes, lookDir, bb);
        }
        this.updateStableAimPoint(entity, bb, vec);

        this.normalisedRot = rotationsTo(vec, eyes);
        float[] jitterOffset = this.jitter.getValue()
                ? this.calculateJitter(RandomUtil.nextFloat(this.jitterMin.getValue(), this.jitterMax.getValue()), this.interpolateJitterVec.getValue())
                : new float[]{0.0F, 0.0F};
        Vec3 targetVec = add(vec, this.getOffsetVec());
        boolean notVisible = !canSeePoint(targetVec) && canSeePoint(subtract(targetVec, this.offsetVec));
        if (notVisible) {
            targetVec = subtract(targetVec, this.offsetVec);
        }
        targetVec = new Vec3(
                MathHelper.clamp_double(targetVec.xCoord, bb.minX, bb.maxX),
                MathHelper.clamp_double(targetVec.yCoord, bb.minY, bb.maxY),
                MathHelper.clamp_double(targetVec.zCoord, bb.minZ, bb.maxZ)
        );
        if (this.predictionEngine.getValue()) {
            targetVec = add(targetVec, multiply(getMoveDelta(entity), Math.random() * 0.7D * this.extraPrediction.getValue()));
        }

        this.updateAimVec(targetVec);
        float[] rot = RotationUtil.getRotations(
                targetVec.xCoord - eyes.xCoord,
                targetVec.yCoord - eyes.yCoord,
                targetVec.zCoord - eyes.zCoord,
                yaw,
                pitch,
                maxAngle,
                smoothFactor
        );
        if (this.canUseOvershoot() && this.overshoot.getValue() && !notVisible) {
            rot = this.applyOvershoot(rot[0], rot[1], yaw, pitch, entity);
        } else {
            this.resetOvershoot();
        }
        if (!notVisible) {
            rot[0] += jitterOffset[0];
            rot[1] += jitterOffset[1];
        }
        this.shouldRandomize = false;
        return rot;
    }

    private boolean canUseOvershoot() {
        return this.advancedRotations.getValue() && this.rotations.getValue() == 2;
    }

    private float[] applyOvershoot(float targetYaw, float targetPitch, float baseYaw, float basePitch, EntityLivingBase entity) {
        float errorYaw = MathHelper.wrapAngleTo180_float(targetYaw - baseYaw);
        float errorPitch = targetPitch - basePitch;
        float angularError = (float) Math.sqrt(errorYaw * errorYaw + errorPitch * errorPitch);
        float trigger = Math.max(1.0F, this.overshootTriggerAngle.getValue());

        if (this.overshootTrackedEntity != entity) {
            this.overshootTrackedEntity = entity;
            this.overshootYaw = 0.0F;
            this.overshootPitch = 0.0F;
            this.overshootLastTargetYaw = targetYaw;
            this.overshootLastTargetPitch = targetPitch;
            this.overshootLastErrorYaw = errorYaw;
            this.overshootLastErrorPitch = errorPitch;
            this.overshootLastTick = mc.thePlayer.ticksExisted;
            this.overshootActive = false;
            this.overshootCooldownUntilTick = mc.thePlayer.ticksExisted + this.overshootDelay.getValue();
        }

        float arrival = MathHelper.clamp_float(1.0F - angularError / trigger, 0.0F, 1.0F);
        int tick = mc.thePlayer.ticksExisted;
        if (!this.overshootActive && arrival > 0.0F && tick >= this.overshootCooldownUntilTick) {
            if (RandomUtil.nextDouble(0.0D, 100.0D) <= this.overshootChance.getValue()) {
                this.overshootActive = true;
            } else {
                this.overshootCooldownUntilTick = tick + Math.max(1, this.overshootDelay.getValue());
            }
        }

        float velocityYaw = MathHelper.wrapAngleTo180_float(targetYaw - this.overshootLastTargetYaw);
        float velocityPitch = targetPitch - this.overshootLastTargetPitch;
        float targetAngularVelocity = (float) Math.sqrt(velocityYaw * velocityYaw + velocityPitch * velocityPitch);
        float velocityGain = MathHelper.clamp_float(targetAngularVelocity / 12.0F, 0.0F, 1.0F);
        float motionGain = 1.0F + (float) MathHelper.clamp_double(getSpeedPosBased(entity) * 3.0D, 0.0D, 0.45D);
        float amplitude = this.overshootMaxAngle.getValue()
                * this.overshootStrength.getValue()
                * arrival
                * (0.55F + 0.45F * velocityGain)
                * motionGain;

        if (tick != this.overshootLastTick) {
            float noise = amplitude * this.overshootRandomness.getValue() * 0.35F;
            this.overshootNoiseYaw = RandomUtil.nextFloat(-noise, noise);
            this.overshootNoisePitch = RandomUtil.nextFloat(-noise, noise) * 0.65F;
            this.overshootLastTick = tick;
        }

        float speed = this.overshootSpeed.getValue();
        float recoveryStep = MathHelper.clamp_float(this.overshootRecovery.getValue() * speed, 0.01F, 1.0F);
        if (!this.overshootActive || arrival <= 0.0F || amplitude <= 0.0F || angularError < 1.0E-3F) {
            this.overshootYaw = interpolate(this.overshootYaw, 0.0F, recoveryStep);
            this.overshootPitch = interpolate(this.overshootPitch, 0.0F, recoveryStep);
            if (this.overshootActive && (arrival <= 0.0F || angularError < 1.0E-3F || Math.abs(this.overshootYaw) + Math.abs(this.overshootPitch) < 0.05F)) {
                this.overshootActive = false;
                this.overshootCooldownUntilTick = tick + this.overshootDelay.getValue();
            }
            this.overshootLastTargetYaw = targetYaw;
            this.overshootLastTargetPitch = targetPitch;
            this.overshootLastErrorYaw = errorYaw;
            this.overshootLastErrorPitch = errorPitch;
            return new float[]{
                    targetYaw + this.overshootYaw,
                    MathHelper.clamp_float(targetPitch + this.overshootPitch, -90.0F, 90.0F)
            };
        }

        float prediction = this.overshootPrediction.getValue();
        float leadYaw = errorYaw + velocityYaw * prediction * 1.75F;
        float leadPitch = errorPitch + velocityPitch * prediction * 1.25F;
        float leadLength = (float) Math.sqrt(leadYaw * leadYaw + leadPitch * leadPitch);
        if (leadLength < 1.0E-3F) {
            leadYaw = errorYaw;
            leadPitch = errorPitch;
            leadLength = Math.max(angularError, 1.0E-3F);
        }

        float desiredYawOffset = leadYaw / leadLength * amplitude + this.overshootNoiseYaw;
        float desiredPitchOffset = leadPitch / leadLength * amplitude * 0.75F + this.overshootNoisePitch;
        float errorDot = errorYaw * this.overshootLastErrorYaw + errorPitch * this.overshootLastErrorPitch;
        if (errorDot < 0.0F && angularError < trigger) {
            desiredYawOffset -= this.overshootYaw * this.overshootRecovery.getValue();
            desiredPitchOffset -= this.overshootPitch * this.overshootRecovery.getValue();
        }

        float response = MathHelper.clamp_float((1.0F - this.overshootDamping.getValue()) * speed, 0.01F, 1.0F);
        this.overshootYaw = this.overshootYaw + (desiredYawOffset - this.overshootYaw) * response;
        this.overshootPitch = this.overshootPitch + (desiredPitchOffset - this.overshootPitch) * response;
        this.overshootYaw = MathHelper.clamp_float(this.overshootYaw, -this.overshootMaxAngle.getValue(), this.overshootMaxAngle.getValue());
        this.overshootPitch = MathHelper.clamp_float(this.overshootPitch, -this.overshootMaxAngle.getValue(), this.overshootMaxAngle.getValue());
        if (angularError < 0.25F && Math.abs(this.overshootYaw) + Math.abs(this.overshootPitch) < 0.15F) {
            this.overshootActive = false;
            this.overshootCooldownUntilTick = tick + this.overshootDelay.getValue();
        }
        this.overshootLastTargetYaw = targetYaw;
        this.overshootLastTargetPitch = targetPitch;
        this.overshootLastErrorYaw = errorYaw;
        this.overshootLastErrorPitch = errorPitch;

        return new float[]{
                targetYaw + this.overshootYaw,
                MathHelper.clamp_float(targetPitch + this.overshootPitch, -90.0F, 90.0F)
        };
    }

    private void resetOvershoot() {
        this.overshootTrackedEntity = null;
        this.overshootYaw = 0.0F;
        this.overshootPitch = 0.0F;
        this.overshootLastTargetYaw = 0.0F;
        this.overshootLastTargetPitch = 0.0F;
        this.overshootLastErrorYaw = 0.0F;
        this.overshootLastErrorPitch = 0.0F;
        this.overshootNoiseYaw = 0.0F;
        this.overshootNoisePitch = 0.0F;
        this.overshootLastTick = 0;
        this.overshootActive = false;
        this.overshootCooldownUntilTick = 0;
    }

    private AxisAlignedBB getAdvancedBox(EntityLivingBase entity) {
        AxisAlignedBB bb = target.getBox();
        if (!this.predictionEngine.getValue()) {
            return bb;
        }
        return this.simulatePredictions(entity, bb);
    }

    private AxisAlignedBB simulatePredictions(EntityLivingBase entity, AxisAlignedBB hitbox) {
        if (this.predictionTrackedEntity != entity) {
            this.resetPredictionEngine();
            this.predictionTrackedEntity = entity;
        }

        double dist = RotationUtil.distanceToBox(hitbox);
        boolean shouldPredict = false;
        double speed = getSpeedPosBased(entity);

        if (dist > this.attackRange.getValue()) {
            this.predictionTargetOffset = (float) Math.min(Math.max((dist - this.attackRange.getValue()) * 3.0D, 0.0D), 8.0D);
            shouldPredict = true;
        }

        if (speed > 0.4D) {
            double extra = Math.random() * RandomUtil.nextFloat(0.9F, 1.1F);
            float predictedOffset = (float) (-Math.min(Math.max(dist, 0.0D), 8.0D) + (ThreadLocalRandom.current().nextBoolean() ? extra : -extra));
            this.predictionTargetOffset = interpolate(this.predictionTargetOffset, predictedOffset, 0.05F);
            shouldPredict = true;
        }

        if (mc.thePlayer.getEntityBoundingBox().intersectsWith(hitbox)) {
            this.predictionTargetOffset = 3.0F;
            shouldPredict = true;
        }

        Vec3 moveDelta = getMoveDelta(entity);
        AxisAlignedBB trackedHitbox = this.simulateReactionTime.getValue() ? this.getReactionDelayedHitbox(hitbox, moveDelta) : hitbox;
        Vec3 prediction = multiply(flat(moveDelta), shouldPredict ? this.predictionTargetOffset : 0.0D);
        return trackedHitbox.offset(prediction.xCoord, prediction.yCoord, prediction.zCoord);
    }

    private AxisAlignedBB getReactionDelayedHitbox(AxisAlignedBB hitbox, Vec3 moveDelta) {
        if (this.predictionLastTrackedAABB == null) {
            this.predictionLastTrackedAABB = hitbox;
            this.predictionLastTrackedMoveDelta = moveDelta;
        }

        if (mc.thePlayer.ticksExisted != this.predictionTicksExisted) {
            this.previousTargetMotions.add(moveDelta.lengthVector());

            if (this.predictionUpdateTimer.hasTimeElapsed((this.currentReactionTime + 1L) * 50L)) {
                this.predictionLastTrackedAABB = hitbox;
                this.predictionLastTrackedMoveDelta = moveDelta;
                this.currentReactionTime = this.getReactionTimeFromMotion();
                this.predictionUpdateTimer.reset();
            } else if (this.getDirectionConfidence(moveDelta, this.predictionLastTrackedMoveDelta) >= this.thresholdForDirectionConfidence.getValue()) {
                this.predictionLastTrackedAABB = this.predictionLastTrackedAABB.offset(
                        this.predictionLastTrackedMoveDelta.xCoord,
                        this.predictionLastTrackedMoveDelta.yCoord,
                        this.predictionLastTrackedMoveDelta.zCoord
                );
                hitbox = this.predictionLastTrackedAABB;
            } else {
                this.predictionLastTrackedAABB = hitbox;
                this.predictionLastTrackedMoveDelta = moveDelta;
            }

            while (this.previousTargetMotions.size() > 20) {
                this.previousTargetMotions.remove(0);
            }
        }

        this.predictionTicksExisted = mc.thePlayer.ticksExisted;
        return hitbox;
    }

    private int getReactionTimeFromMotion() {
        if (this.previousTargetMotions.isEmpty()) {
            return this.currentReactionTime;
        }

        double averageMotion = 0.0D;
        for (Double motion : this.previousTargetMotions) {
            averageMotion += motion;
        }
        averageMotion /= this.previousTargetMotions.size();

        double motionPercentage = MathHelper.clamp_double(averageMotion * 2.0D, 0.0D, 1.0D);
        if (averageMotion < this.thresholdToApplyReactionTime.getValue()) {
            motionPercentage = 0.0D;
        }

        int min = Math.min(this.reactionTimeMin.getValue(), this.reactionTimeMax.getValue());
        int max = Math.max(this.reactionTimeMin.getValue(), this.reactionTimeMax.getValue());
        return (int) interpolate(min, max, (float) motionPercentage);
    }

    private double getDirectionConfidence(Vec3 current, Vec3 previous) {
        double currentLength = current.lengthVector();
        double previousLength = previous.lengthVector();
        if (currentLength < 1.0E-4D || previousLength < 1.0E-4D) {
            return 1.0D;
        }
        double dot = current.xCoord * previous.xCoord + current.yCoord * previous.yCoord + current.zCoord * previous.zCoord;
        return MathHelper.clamp_double((dot / (currentLength * previousLength) + 1.0D) / 2.0D, 0.0D, 1.0D);
    }

    private void resetPredictionEngine() {
        this.predictionTargetOffset = 0.0F;
        this.predictionTrackedEntity = null;
        this.predictionLastTrackedAABB = null;
        this.predictionTicksExisted = 0;
        this.currentReactionTime = 0;
        this.predictionLastTrackedMoveDelta = zeroVec();
        this.previousTargetMotions.clear();
        this.predictionUpdateTimer.setTime();
    }

    private Vec3 getAdvancedLookVec(float yaw, float pitch) {
        switch (this.lookVec.getValue()) {
            case 0:
                return getVectorForRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
            case 1:
                return getVectorForRotation(this.serverYaw, this.serverPitch);
            default:
                return this.normalisedRot == null ? mc.thePlayer.getLook(1.0F) : getVectorForRotation(this.normalisedRot[0], this.normalisedRot[1]);
        }
    }

    private Vec3 getBackupVec(List<Vec3> points, Vec3 eyes, Vec3 lookDir, AxisAlignedBB bb) {
        if (points.isEmpty()) {
            return center(bb);
        }
        points.sort(Comparator.comparingDouble(point -> crossLength(subtract(point, eyes), lookDir)));
        return points.get(0);
    }

    private double scoreAimPoint(Vec3 point, Vec3 eyes, Vec3 lookDir, Vec3 stablePoint) {
        double score = crossLength(subtract(point, eyes), lookDir);
        if (stablePoint != null) {
            score += point.distanceTo(stablePoint) * this.pointStabilityAmount.getValue();
        }
        return score;
    }

    private Vec3 getStablePoint(EntityLivingBase entity, AxisAlignedBB bb) {
        if (!this.pointStability.getValue() || this.stableAimRatio == null || this.stableAimTarget != entity) {
            return null;
        }
        return new Vec3(
                bb.minX + (bb.maxX - bb.minX) * this.stableAimRatio.xCoord,
                bb.minY + (bb.maxY - bb.minY) * this.stableAimRatio.yCoord,
                bb.minZ + (bb.maxZ - bb.minZ) * this.stableAimRatio.zCoord
        );
    }

    private void updateStableAimPoint(EntityLivingBase entity, AxisAlignedBB bb, Vec3 point) {
        if (!this.pointStability.getValue()) {
            this.resetStableAimPoint();
            return;
        }
        double width = Math.max(bb.maxX - bb.minX, 1.0E-4D);
        double height = Math.max(bb.maxY - bb.minY, 1.0E-4D);
        double depth = Math.max(bb.maxZ - bb.minZ, 1.0E-4D);
        this.stableAimTarget = entity;
        this.stableAimRatio = new Vec3(
                MathHelper.clamp_double((point.xCoord - bb.minX) / width, 0.0D, 1.0D),
                MathHelper.clamp_double((point.yCoord - bb.minY) / height, 0.0D, 1.0D),
                MathHelper.clamp_double((point.zCoord - bb.minZ) / depth, 0.0D, 1.0D)
        );
    }

    private void resetStableAimPoint() {
        this.stableAimRatio = null;
        this.stableAimTarget = null;
    }

    private boolean isValidPoint(Vec3 point, AxisAlignedBB bb, double pred, boolean outOfRange, boolean headHittable, boolean torsoHittable, boolean feetHittable) {
        return this.canSeePoint(point, bb, pred, outOfRange) && !this.pointInBlacklistedPos(point, bb, headHittable, torsoHittable, feetHittable);
    }

    private boolean pointInBlacklistedPos(Vec3 point, AxisAlignedBB bb, boolean headHittable, boolean torsoHittable, boolean feetHittable) {
        double height = bb.maxY - bb.minY;
        double headSize = height / 4.5D;
        double torsoSize = height / 2.75D;
        boolean head = point.yCoord <= bb.maxY && point.yCoord > bb.maxY - headSize;
        boolean torso = point.yCoord <= bb.maxY - headSize && point.yCoord >= bb.minY + torsoSize;
        boolean feet = point.yCoord >= bb.minY && point.yCoord < bb.minY + torsoSize;
        if (headHittable && !head) return true;
        if (torsoHittable && !torso) return true;
        if (feetHittable && !feet) return true;
        if (this.blacklistHead.getValue() && head) return true;
        if (this.blacklistTorso.getValue() && torso) return true;
        if (this.blacklistFeet.getValue() && feet) return true;
        return this.blacklistHeuristic.getValue() && Math.abs(point.yCoord - center(bb).yCoord - 0.2D) < this.heuristicBuffer.getValue() / 2.0F;
    }

    private boolean canSeePoint(Vec3 point, AxisAlignedBB bb, double pred, boolean outOfRange) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0F);
        MovingObjectPosition blockHit = mc.theWorld.rayTraceBlocks(eyePos, point, false, false, false);
        MovingObjectPosition boxHit = bb.calculateIntercept(eyePos, point);
        if (boxHit != null && boxHit.hitVec.distanceTo(point) > 0.01D) {
            return false;
        }
        return blockHit == null && (eyePos.distanceTo(point) < this.attackRange.getValue() + this.outOfRangeBuffer.getValue() + pred + this.finalXZTrim || outOfRange);
    }

    private boolean canSeePoint(Vec3 point) {
        return mc.theWorld.rayTraceBlocks(mc.thePlayer.getPositionEyes(1.0F), point, false, false, false) == null;
    }

    private boolean[] getPreferredHittable(AxisAlignedBB bb, Vec3 eyes, double range) {
        boolean[] hittable = new boolean[3];
        AxisAlignedBB selected = null;
        double height = bb.maxY - bb.minY;
        double headSize = height / 4.5D;
        double torsoSize = height / 2.75D;
        switch (this.preferredBodyPart.getValue()) {
            case 1:
                selected = new AxisAlignedBB(bb.minX, bb.maxY - headSize, bb.minZ, bb.maxX, bb.maxY, bb.maxZ);
                break;
            case 2:
                selected = new AxisAlignedBB(bb.minX, bb.minY + torsoSize, bb.minZ, bb.maxX, bb.maxY - headSize, bb.maxZ);
                break;
            case 3:
                selected = new AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY + torsoSize, bb.maxZ);
                break;
            default:
                return hittable;
        }
        for (Vec3 vertex : vertices(selected)) {
            if (eyes.distanceTo(vertex) < range) {
                hittable[this.preferredBodyPart.getValue() - 1] = true;
                break;
            }
        }
        return hittable;
    }

    private Vec3 getOffsetVec() {
        double minXZ = -0.5D;
        double maxXZ = 0.5D;
        double minY = -0.5D;
        double maxY = 0.5D;
        double yawFactor = randomBetween(this.yawFactorMin.getValue(), this.yawFactorMax.getValue());
        double pitchFactor = randomBetween(this.pitchFactorMin.getValue(), this.pitchFactorMax.getValue());
        double meanXZ = (minXZ + maxXZ) / 2.0D;
        double stdDevXZ = (maxXZ - minXZ) / 4.0D;
        double meanY = (minY + maxY) / 2.0D;
        double stdDevY = (maxY - minY) / 4.0D;

        if (this.shouldRandomize) {
            switch (this.offsetMode.getValue()) {
                case 1:
                    this.updateOffset(
                            ThreadLocalRandom.current().nextGaussian() * stdDevXZ + meanXZ,
                            ThreadLocalRandom.current().nextGaussian() * stdDevY + meanY,
                            ThreadLocalRandom.current().nextGaussian() * stdDevXZ + meanXZ,
                            yawFactor,
                            pitchFactor
                    );
                    break;
                case 2:
                    this.updateOffset(RandomUtil.nextDouble(minXZ, maxXZ), RandomUtil.nextDouble(minY, maxY), RandomUtil.nextDouble(minXZ, maxXZ), yawFactor, pitchFactor);
                    break;
                case 3:
                    Vec3 lastOffset = new Vec3(this.lastXOffset, this.lastYOffset, this.lastZOffset);
                    if (this.offsetVec.distanceTo(lastOffset) < this.tolerance.getValue()) {
                        if (this.advancedBase.getValue() == 0) {
                            this.updateOffset(
                                    ThreadLocalRandom.current().nextGaussian() * stdDevXZ + meanXZ,
                                    ThreadLocalRandom.current().nextGaussian() * stdDevY + meanY,
                                    ThreadLocalRandom.current().nextGaussian() * stdDevXZ + meanXZ,
                                    yawFactor,
                                    pitchFactor
                            );
                        } else {
                            this.updateOffset(RandomUtil.nextDouble(minXZ, maxXZ), RandomUtil.nextDouble(minY, maxY), RandomUtil.nextDouble(minXZ, maxXZ), yawFactor, pitchFactor);
                        }
                    }
                    break;
                default:
                    this.offsetVec = zeroVec();
            }
        } else if (this.offsetMode.getValue() != 0) {
            this.offsetVec = interpolate(this.offsetVec, new Vec3(this.lastXOffset, this.lastYOffset, this.lastZOffset), this.interpolateVec.getValue() ? this.offsetAmount.getValue() : 1.0F);
        } else {
            this.offsetVec = zeroVec();
        }

        return this.offsetVec;
    }

    private void updateOffset(double xOffset, double yOffset, double zOffset, double yawFactor, double pitchFactor) {
        xOffset *= yawFactor;
        yOffset *= pitchFactor;
        zOffset *= yawFactor;
        this.offsetVec = interpolate(this.offsetVec, new Vec3(xOffset, yOffset, zOffset), this.interpolateVec.getValue() ? this.offsetAmount.getValue() : 1.0F);
        this.lastXOffset = xOffset;
        this.lastYOffset = yOffset;
        this.lastZOffset = zOffset;
    }

    private float[] calculateJitter(float strength, boolean interpolate) {
        long time = System.currentTimeMillis();
        this.jitterClicks.removeIf(click -> click + 1000L < time);
        if (this.jitterClicks.isEmpty()) {
            return new float[]{this.jitterYaw, this.jitterPitch};
        }

        boolean lastFrameClicked = false;
        for (Long clickTime : this.jitterClicks) {
            if (clickTime + 17L > time) {
                lastFrameClicked = true;
                break;
            }
        }
        if (lastFrameClicked) {
            this.clickImpulseYaw += RandomUtil.nextFloat(-strength, strength);
            this.clickImpulsePitch += RandomUtil.nextFloat(-strength, strength);
        }

        float cpsFactor = Math.min(this.jitterClicks.size() / 12.0F, 1.5F);
        float yawJitter = this.tremorYaw * 0.6F * cpsFactor + this.clickImpulseYaw;
        float pitchJitter = this.tremorPitch * 0.6F * cpsFactor + this.clickImpulsePitch;
        if (interpolate) {
            this.jitterYaw = interpolate(this.jitterYaw, yawJitter, 0.25F);
            this.jitterPitch = interpolate(this.jitterPitch, pitchJitter, 0.25F);
        } else {
            this.jitterYaw = yawJitter;
            this.jitterPitch = pitchJitter;
        }

        if (mc.thePlayer.ticksExisted != this.lastJitterTick) {
            if (Math.random() < 0.08D) {
                this.targetJitterYaw = RandomUtil.nextFloat(-0.15F, 0.15F);
                this.targetJitterPitch = RandomUtil.nextFloat(-0.15F, 0.15F);
            }
            this.tremorYaw += (this.targetJitterYaw - this.tremorYaw) * 0.12F;
            this.tremorPitch += (this.targetJitterPitch - this.tremorPitch) * 0.12F;
            this.clickImpulseYaw *= 0.75F;
            this.clickImpulsePitch *= 0.75F;
            this.lastJitterTick = mc.thePlayer.ticksExisted;
        }
        return new float[]{this.jitterYaw, this.jitterPitch};
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled()) {
            if (event.getType() == EventType.SEND && event.getPacket() instanceof C0APacketAnimation) {
                this.jitterClicks.add(System.currentTimeMillis());
            }
            if (event.getPacket() instanceof C07PacketPlayerDigging packet) {
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                this.blockingState = false;
                if (this.isBlocking) {
                    mc.thePlayer.stopUsingItem();
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled()) {
            if (this.moveFix.getValue() == 1
                    && this.rotations.getValue() != 3
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (this.shouldAutoBlock()) {
                mc.thePlayer.movementInput.jump = false;
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && target != null) {
            if (TeamUtil.isEntityLoaded(target.getEntity())
                    && this.isAttackAllowed()) {
                if (this.showTarget.getValue() == 1) {
                    Unfair.moduleManager.modules.get(HUD.class);
                    Color color = HUD.getColor(System.currentTimeMillis());
                    RenderUtil.enableRenderState();
                    RenderUtil.drawEntityBox(target.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
                    RenderUtil.disableRenderState();
                }
                if (this.advancedRotations.getValue() && this.aimDot.getValue() && this.currentVec != null) {
                    this.renderAimDot(event.partialTicks());
                }
            }
        }
    }

    private void renderAimDot(float partialTicks) {
        double size = 0.1D;
        Color color = new Color(this.aimDotColor.getValue());
        Vec3 aimVec = this.lastAimVec == null
                ? this.currentVec
                : interpolate(this.lastAimVec, this.currentVec, partialTicks);
        AxisAlignedBB dotBox = new AxisAlignedBB(
                aimVec.xCoord - size,
                aimVec.yCoord - size,
                aimVec.zCoord - size,
                aimVec.xCoord + size,
                aimVec.yCoord + size,
                aimVec.zCoord + size
        ).offset(
                -mc.getRenderManager().getRenderPosX(),
                -mc.getRenderManager().getRenderPosY(),
                -mc.getRenderManager().getRenderPosZ()
        );

        RenderUtil.enableRenderState();
        RenderUtil.drawFilledBox(dotBox, color.getRed(), color.getGreen(), color.getBlue());
        RenderUtil.disableRenderState();
    }

    private void updateAimVec(Vec3 targetVec) {
        this.lastAimVec = this.currentVec == null ? targetVec : this.currentVec;
        this.currentVec = targetVec;
    }

    private void resetAimVec() {
        this.currentVec = null;
        this.lastAimVec = null;
        this.resetStableAimPoint();
        this.resetOvershoot();
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean shouldEaseOut = !enabled
                && this.enabled
                && mc.thePlayer != null
                && this.rotations.getValue() == 2
                && !this.isAtRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (shouldEaseOut) {
            this.easingOut = true;
        }
        super.setEnabled(enabled);
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (this.isBlocking) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.easingOut = false;
        target = null;
        this.resetAimVec();
        this.resetPredictionEngine();
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.apsDelayGenerator.reset();
        this.blockTick = 0;

        this.serverYaw = mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0F;
        this.serverPitch = mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0.0F;
    }

    @Override
    public void onDisabled() {
        Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.blockingState = false;
        this.isBlocking = false;
        this.fakeBlockState = false;
        this.resetAimVec();
        this.resetPredictionEngine();
    }

    @Override
    public void verifyValue(String mode) {
        if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
            if (this.swingRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(mode)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else if (this.reactionTimeMin.getName().equals(mode)) {
                if (this.reactionTimeMin.getValue() > this.reactionTimeMax.getValue()) {
                    this.reactionTimeMax.setValue(this.reactionTimeMin.getValue());
                }
            } else if (this.reactionTimeMax.getName().equals(mode)) {
                if (this.reactionTimeMin.getValue() > this.reactionTimeMax.getValue()) {
                    this.reactionTimeMin.setValue(this.reactionTimeMax.getValue());
                }
            } else {
                if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        } else {
            boolean badCps = this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 3;
            if (badCps && this.autoBlockCPS.getValue() > 10) {
                this.autoBlockCPS.setValue(10);
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}
