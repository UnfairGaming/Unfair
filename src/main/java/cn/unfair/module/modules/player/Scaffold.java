package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.misc.BedNuker;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.*;
import cn.unfair.util.player.DelayGenerator;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.potion.Potion;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double[] placeOffsets = new double[]{0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375, 0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875, 0.78125, 0.84375, 0.90625, 0.96875
    };
    public final ModeProperty rotationMode = new ModeProperty("rotations", 5, new String[]{"None", "Vanilla", "BackWards", "Strafe", "Test", "Prediction"});
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT"});
    public final ModeProperty sprintMode = new ModeProperty("sprint", 0, new String[]{"NONE", "VANILLA"});
    public final PercentProperty groundMotion = new PercentProperty("ground-motion", 100);
    public final PercentProperty airMotion = new PercentProperty("air-motion", 100);
    public final PercentProperty speedMotion = new PercentProperty("speed-motion", 100);
    public final ModeProperty tower = new ModeProperty("tower", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final ModeProperty keepY = new ModeProperty("keep-y", 0, new String[]{"NONE", "VANILLA", "EXTRA", "TELLY"});
    public final BooleanProperty predictionTower = new BooleanProperty("Prediction Tower", true);
    public final BooleanProperty keepYonPress = new BooleanProperty("keep-y-on-press", false, () -> this.keepY.getValue() != 0);
    public final BooleanProperty multiplace = new BooleanProperty("multi-place", true);
    public final BooleanProperty alwaysClick = new BooleanProperty("always-click", false);
    public final BooleanProperty raytraceCheck = new BooleanProperty("raytrace-check", false);
    public final IntProperty aimSpeedYaw = new IntProperty("aim-speed-yaw", 180, 1, 180, () -> this.rotationMode.getValue() != 0);
    public final IntProperty aimSpeedPitch = new IntProperty("aim-speed-pitch", 180, 1, 180, () -> this.rotationMode.getValue() != 0);
    public final BooleanProperty customClutchSpeed = new BooleanProperty("custom-clutch-speed", false, () -> this.rotationMode.getValue() != 0);
    public final BooleanProperty clutchSmooth = new BooleanProperty("clutch-smooth", true, () -> this.rotationMode.getValue() != 0 && this.customClutchSpeed.getValue());
    public final IntProperty clutchMinAimSpeed = new IntProperty("clutch-min-aim-speed", 180, 1, 180, () -> this.rotationMode.getValue() != 0 && this.customClutchSpeed.getValue());
    public final IntProperty clutchMaxAimSpeed = new IntProperty("clutch-max-aim-speed", 180, 1, 180, () -> this.rotationMode.getValue() != 0 && this.customClutchSpeed.getValue());
    public final IntProperty minCps = new IntProperty("min-cps", 8, 1, 30, this.alwaysClick::getValue);
    public final IntProperty maxCps = new IntProperty("max-cps", 12, 1, 30, this.alwaysClick::getValue);
    public final BooleanProperty safeWalk = new BooleanProperty("safe-walk", true);
    public final BooleanProperty sneak = new BooleanProperty("sneak", false);
    public final IntProperty sneakMinDelay = new IntProperty("sneak-min-delay", 2, 0, 10, this.sneak::getValue);
    public final IntProperty sneakMaxDelay = new IntProperty("sneak-max-delay", 3, 0, 10, this.sneak::getValue);
    public final BooleanProperty inventoryCheck = new BooleanProperty("inventory-check", true);
    public final BooleanProperty swing = new BooleanProperty("swing", true);
    public final BooleanProperty itemSpoof = new BooleanProperty("item-spoof", false);
    public final ModeProperty blockCounterMode = new ModeProperty("Block Counter Mode", 0, new String[]{"NONE", "Myau", "Exhibition"});
    private final float[] lastErrors = new float[20];
    private final TimerUtil clickTimer = new TimerUtil();
    private final DelayGenerator delayGenerator = new DelayGenerator();
    private int errorIndex = 0;
    private int rotationTick = 0;
    private int lastSlot = -1;
    private int blockCount = -1;
    private float yaw = -180.0F;
    private float pitch = 0.0F;
    private float serverYaw = 0.0F;
    private float serverPitch = 0.0F;
    private boolean canRotate = false;
    private int towerTick = 0;
    private int towerDelay = 0;
    private int sneakDelay = 0;
    private int stage = 0;
    private int startY = 256;
    private boolean shouldKeepY = false;
    private boolean towering = false;
    private float lastYaw = 0;
    private float lastYawChange = 0;
    private float lastPitchChange = 0;
    private EnumFacing targetFacing = null;
    private boolean easingOut = false;
    private long nextClickDelay = 0L;

    public Scaffold() {
        super("Scaffold", false);
    }

    private boolean isInventoryBlocked() {
        return this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer;
    }

    private void resetRuntimeState(boolean restoreSlot) {
        if (restoreSlot && mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
        this.blockCount = -1;
        this.rotationTick = 0;
        this.yaw = -180.0F;
        this.pitch = 0.0F;
        this.serverYaw = mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0F;
        this.serverPitch = mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0.0F;
        this.canRotate = false;
        this.towerTick = 0;
        this.towerDelay = 0;
        this.sneakDelay = 0;
        this.stage = 0;
        this.startY = mc.thePlayer != null ? MathHelper.floor_double(mc.thePlayer.posY) : 256;
        this.shouldKeepY = false;
        this.towering = false;
        this.lastYaw = 0.0F;
        this.lastYawChange = 0.0F;
        this.lastPitchChange = 0.0F;
        this.targetFacing = null;
        this.easingOut = false;
        this.errorIndex = 0;
        this.nextClickDelay = 0L;
        this.clickTimer.setTime();
        this.delayGenerator.reset();
        for (int i = 0; i < this.lastErrors.length; i++) {
            this.lastErrors[i] = 0.0F;
        }
    }

    private boolean shouldStopSprint() {
        if (this.isTowering()) {
            return false;
        } else {
            boolean stage = this.keepY.getValue() == 1 || this.keepY.getValue() == 2;
            return (!stage || this.stage <= 0) && this.sprintMode.getValue() == 0;
        }
    }

    private boolean canPlace() {
        BedNuker bedNuker = (BedNuker) Unfair.moduleManager.modules.get(BedNuker.class);
        if (bedNuker.isEnabled() && bedNuker.isReady()) {
            return false;
        } else {
            LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
            return !longJump.isEnabled() || !longJump.isAutoMode() || longJump.isJumping();
        }
    }

    private boolean canMoveSafely() {
        double[] offset = MoveUtil.predictMovement();
        double nextX = mc.thePlayer.posX + mc.thePlayer.motionX + offset[0];
        double nextZ = mc.thePlayer.posZ + mc.thePlayer.motionZ + offset[1];
        BlockPos nextBlockBelow = new BlockPos(
                MathHelper.floor_double(nextX),
                MathHelper.floor_double(mc.thePlayer.posY) - 1,
                MathHelper.floor_double(nextZ)
        );
        return BlockUtil.isReplaceable(nextBlockBelow)
                || PlayerUtil.canMove(mc.thePlayer.motionX + offset[0], mc.thePlayer.motionZ + offset[1]);
    }

    private boolean shouldUseClutchSpeed() {
        return this.customClutchSpeed.getValue()
                && this.isClutchPlacementState();
    }

    private boolean isClutchPlacementState() {
        return mc.thePlayer != null
                && !mc.thePlayer.onGround
                && (this.canMoveSafely() || BlockUtil.isReplaceable(this.getTargetPos()));
    }

    private boolean shouldSneak() {
        return ItemUtil.isHoldingBlock() && mc.thePlayer.onGround;
    }

    private EnumFacing getBestFacing(BlockPos blockPos1, BlockPos blockPos3) {
        double offset = 0.0;
        EnumFacing enumFacing = null;
        for (EnumFacing facing : EnumFacing.VALUES) {
            if (facing != EnumFacing.DOWN) {
                BlockPos pos = blockPos1.offset(facing);
                if (pos.getY() <= blockPos3.getY()) {
                    double distance = pos.distanceSqToCenter((double) blockPos3.getX() + 0.5, (double) blockPos3.getY() + 0.5, (double) blockPos3.getZ() + 0.5);
                    if (enumFacing == null || distance < offset || distance == offset && facing == EnumFacing.UP) {
                        offset = distance;
                        enumFacing = facing;
                    }
                }
            }
        }
        return enumFacing;
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && this.isInventoryBlocked()) {
            this.resetRuntimeState(true);
            return;
        }
        if (!this.sneak.getValue()) {
            this.sneakDelay = 0;
            return;
        }
        if (this.isEnabled() && event.type() == EventType.PRE) {
            if (this.sneakDelay > 0) {
                this.sneakDelay--;
            }
            if (this.sneakDelay == 0 && this.canMoveSafely()) {
                this.sneakDelay = RandomUtils.nextInt(this.sneakMinDelay.getValue(), this.sneakMaxDelay.getValue() + 1);
            }
        }
    }

    private BlockPos getTargetPos() {
        int startY = MathHelper.floor_double(mc.thePlayer.posY);
        return new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                (this.stage != 0 && !this.shouldKeepY ? Math.min(startY, this.startY) : startY) - 1,
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
    }

    private ArrayList<BlockData> getBlockDataCandidates() {
        BlockPos targetPos = this.getTargetPos();
        ArrayList<BlockData> candidates = new ArrayList<>();
        if (!BlockUtil.isReplaceable(targetPos)) {
            return candidates;
        }
        for (int x = -4; x <= 4; x++) {
            for (int y = -4; y <= 0; y++) {
                for (int z = -4; z <= 4; z++) {
                    BlockPos pos = targetPos.add(x, y, z);
                    if (!BlockUtil.isReplaceable(pos)
                            && !BlockUtil.isInteractable(pos)
                            && !(
                            mc.thePlayer.getDistance((double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5)
                                    > (double) mc.playerController.getBlockReachDistance()
                    )
                            && (this.stage == 0 || this.shouldKeepY || pos.getY() < this.startY)) {
                        for (EnumFacing facing : EnumFacing.VALUES) {
                            if (facing != EnumFacing.DOWN) {
                                BlockPos blockPos = pos.offset(facing);
                                if (BlockUtil.isReplaceable(blockPos)) {
                                    candidates.add(new BlockData(pos, facing));
                                }
                            }
                        }
                    }
                }
            }
        }
        candidates.sort(
                Comparator.comparingDouble(
                        o -> o.blockPos().offset(o.facing()).distanceSqToCenter((double) targetPos.getX() + 0.5, (double) targetPos.getY() + 0.5, (double) targetPos.getZ() + 0.5)
                )
        );
        return candidates;
    }

    private BlockData getBlockData() {
        BlockPos targetPos = this.getTargetPos();
        ArrayList<BlockData> candidates = this.getBlockDataCandidates();
        if (candidates.isEmpty()) {
            return null;
        }
        BlockPos blockPos = candidates.get(0).blockPos();
        EnumFacing facing = this.getBestFacing(blockPos, targetPos);
        return facing == null ? null : new BlockData(blockPos, facing);
    }

    private boolean isKeepYPlacementLocked() {
        return this.keepY.getValue() != 0 && this.stage > 0;
    }

    private boolean place(BlockPos blockPos, EnumFacing enumFacing, Vec3 vec3) {
        if (vec3 == null) {
            return false;
        }
        if (this.raytraceCheck.getValue() && !this.isValidPlacementTarget(blockPos, enumFacing)) {
            return false;
        }
        if (ItemUtil.isHoldingBlock() && this.blockCount > 0) {
            if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockPos, enumFacing, vec3)) {
                if (mc.playerController.getCurrentGameType() != GameType.CREATIVE) {
                    this.blockCount--;
                }
                if (this.swing.getValue()) {
                    mc.thePlayer.swingItem();
                } else {
                    PacketUtil.sendPacket(new C0APacketAnimation());
                }
                return true;
            }
        }
        return false;
    }

    private boolean isValidPlacementTarget(BlockPos blockPos, EnumFacing enumFacing) {
        if (blockPos == null || enumFacing == null) {
            return false;
        }
        if (BlockUtil.isReplaceable(blockPos) || BlockUtil.isInteractable(blockPos)) {
            return false;
        }
        if (this.isKeepYPlacementLocked()
                && blockPos.offset(enumFacing).getY() != this.getTargetPos().getY()) {
            return false;
        }
        return BlockUtil.isReplaceable(blockPos.offset(enumFacing));
    }

    private MovingObjectPosition getMatchingRaytrace(BlockPos blockPos, EnumFacing enumFacing, float yaw, float pitch) {
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK) {
            return null;
        }
        return mop.getBlockPos().equals(blockPos) && mop.sideHit == enumFacing ? mop : null;
    }

    private Vec3 getPlacementHitVec(BlockPos blockPos, EnumFacing enumFacing, float yaw, float pitch) {
        MovingObjectPosition mop = this.getMatchingRaytrace(blockPos, enumFacing, yaw, pitch);
        if (mop != null) {
            return mop.hitVec;
        }
        return this.raytraceCheck.getValue() ? null : BlockUtil.getHitVec(blockPos, enumFacing, yaw, pitch);
    }

    private float[] interpolateRotation(float targetYaw, float targetPitch) {
        float maxDeltaYaw = this.aimSpeedYaw.getValue().floatValue();
        float maxDeltaPitch = this.aimSpeedPitch.getValue().floatValue();
        if (this.shouldUseClutchSpeed()) {
            int min = this.clutchMinAimSpeed.getValue();
            int max = this.clutchMaxAimSpeed.getValue();
            float clutchSpeed = RandomUtils.nextInt(min, max + 1);
            maxDeltaYaw = clutchSpeed;
            maxDeltaPitch = clutchSpeed;
        }
        if (!mc.thePlayer.onGround && mc.gameSettings.keyBindJump.isKeyDown()) {
            maxDeltaYaw = Math.min(maxDeltaYaw, 45.0F);
        }
        float deltaYaw = MathHelper.wrapAngleTo180_float(targetYaw - this.serverYaw);
        if (Math.abs(deltaYaw) > maxDeltaYaw) {
            this.serverYaw = RotationUtil.quantizeAngle(this.serverYaw + Math.signum(deltaYaw) * maxDeltaYaw);
        } else {
            this.serverYaw = targetYaw;
        }
        float deltaPitch = targetPitch - this.serverPitch;
        if (Math.abs(deltaPitch) > maxDeltaPitch) {
            this.serverPitch = RotationUtil.quantizeAngle(this.serverPitch + Math.signum(deltaPitch) * maxDeltaPitch);
        } else {
            this.serverPitch = targetPitch;
        }
        this.serverPitch = MathHelper.clamp_float(this.serverPitch, -90.0F, 90.0F);
        return new float[]{this.serverYaw, this.serverPitch};
    }

    private boolean isAtRotation(float yaw, float pitch) {
        return Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.serverYaw)) < 0.01F
                && Math.abs(pitch - this.serverPitch) < 0.01F;
    }

    private boolean returnToMouseRotation(UpdateEvent event) {
        if (this.rotationMode.getValue() == 0) {
            return false;
        }

        float targetYaw = event.getNewYaw();
        float targetPitch = event.getNewPitch();
        if (this.isAtRotation(targetYaw, targetPitch)) {
            this.resetRuntimeState(false);
            return false;
        }

        float[] rotations = this.interpolateRotation(targetYaw, targetPitch);
        event.setRotation(rotations[0], rotations[1], 3);
        if (this.moveFix.getValue() == 1) {
            event.setPervRotation(rotations[0], 3);
        }
        return true;
    }

    private PlacementTarget findClosestPlacementTarget(float currentYaw, float currentPitch) {
        BlockPos targetPos = this.getTargetPos();
        int maxPlaceY = MathHelper.floor_double(mc.thePlayer.posY);
        if (this.isClutchPlacementState()) {
            PlacementTarget clutchTarget = this.findClosestClutchPlacementTarget(currentYaw, currentPitch, targetPos, maxPlaceY);
            if (clutchTarget != null) {
                return clutchTarget;
            }
        }

        PlacementTarget bestTarget = null;
        double bestScore = 0.0D;
        for (BlockData blockData : this.getBlockDataCandidates()) {
            if (!this.isValidPlacementTarget(blockData.blockPos(), blockData.facing())) {
                continue;
            }
            BlockPos placedPos = blockData.blockPos().offset(blockData.facing());
            if (placedPos.getY() > maxPlaceY) {
                continue;
            }
            double[] x = placeOffsets;
            double[] y = placeOffsets;
            double[] z = placeOffsets;
            double centerX = 0.5D;
            double centerY = 0.5D;
            double centerZ = 0.5D;
            switch (blockData.facing()) {
                case NORTH:
                    z = new double[]{0.0};
                    centerZ = 0.0D;
                    break;
                case EAST:
                    x = new double[]{1.0};
                    centerX = 1.0D;
                    break;
                case SOUTH:
                    z = new double[]{1.0};
                    centerZ = 1.0D;
                    break;
                case WEST:
                    x = new double[]{0.0};
                    centerX = 0.0D;
                    break;
                case DOWN:
                    y = new double[]{0.0};
                    centerY = 0.0D;
                    break;
                case UP:
                    y = new double[]{1.0};
                    centerY = 1.0D;
            }
            for (double dx : x) {
                for (double dy : y) {
                    for (double dz : z) {
                        double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                        double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                        double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                        float[] exactRotations = RotationUtil.getRotationsTo(relX, relY, relZ, currentYaw, currentPitch);
                        MovingObjectPosition mop = this.getMatchingRaytrace(blockData.blockPos(), blockData.facing(), exactRotations[0], exactRotations[1]);
                        if (mop != null) {
                            double yawScore = Math.abs(MathHelper.wrapAngleTo180_float(exactRotations[0] - currentYaw));
                            double pitchScore = Math.abs(exactRotations[1] - currentPitch);
                            double targetScore = placedPos.distanceSqToCenter(
                                    (double) targetPos.getX() + 0.5D,
                                    (double) targetPos.getY() + 0.5D,
                                    (double) targetPos.getZ() + 0.5D
                            );
                            double yScore = Math.abs(placedPos.getY() - targetPos.getY()) * 12.0D;
                            double faceCenterScore = Math.pow(dx - centerX, 2.0D)
                                    + Math.pow(dy - centerY, 2.0D)
                                    + Math.pow(dz - centerZ, 2.0D);
                            double reachScore = mc.thePlayer.getPositionEyes(1.0F).squareDistanceTo(mop.hitVec) * 0.05D;
                            double score = yawScore * 0.7D
                                    + pitchScore
                                    + targetScore * 10.0D
                                    + yScore
                                    + faceCenterScore * 3.0D
                                    + reachScore;
                            if (bestTarget == null || score < bestScore) {
                                bestTarget = new PlacementTarget(blockData, mop.hitVec, exactRotations[0], exactRotations[1]);
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }
        return bestTarget;
    }

    private PlacementTarget findClosestClutchPlacementTarget(float currentYaw, float currentPitch, BlockPos targetPos, int maxPlaceY) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        BlockData bestBlockData = null;
        Vec3 bestPoint = null;
        double bestScore = 0.0D;

        for (BlockData blockData : this.getBlockDataCandidates()) {
            if (!this.isValidPlacementTarget(blockData.blockPos(), blockData.facing())) {
                continue;
            }

            BlockPos placedPos = blockData.blockPos().offset(blockData.facing());
            if (placedPos.getY() > maxPlaceY) {
                continue;
            }

            Vec3 point = this.getClosestPointOnPlacementFace(blockData, eyes);
            double supportScore = eyes.squareDistanceTo(point);
            double targetScore = placedPos.distanceSqToCenter(
                    (double) targetPos.getX() + 0.5D,
                    (double) targetPos.getY() + 0.5D,
                    (double) targetPos.getZ() + 0.5D
            );
            double score = supportScore + targetScore * 0.25D;
            if (bestBlockData == null || score < bestScore) {
                bestBlockData = blockData;
                bestPoint = point;
                bestScore = score;
            }
        }

        if (bestBlockData == null || bestPoint == null) {
            return null;
        }

        float[] rotations = RotationUtil.getRotationsTo(
                bestPoint.xCoord - mc.thePlayer.posX,
                bestPoint.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight(),
                bestPoint.zCoord - mc.thePlayer.posZ,
                currentYaw,
                currentPitch
        );
        MovingObjectPosition mop = this.getMatchingRaytrace(bestBlockData.blockPos(), bestBlockData.facing(), rotations[0], rotations[1]);
        return mop == null ? null : new PlacementTarget(bestBlockData, mop.hitVec, rotations[0], rotations[1]);
    }

    private Vec3 getClosestPointOnPlacementFace(BlockData blockData, Vec3 point) {
        double minX = blockData.blockPos().getX();
        double minY = blockData.blockPos().getY();
        double minZ = blockData.blockPos().getZ();
        double maxX = minX + 1.0D;
        double maxY = minY + 1.0D;
        double maxZ = minZ + 1.0D;
        double inset = 0.03125D;

        double x = MathHelper.clamp_double(point.xCoord, minX + inset, maxX - inset);
        double y = MathHelper.clamp_double(point.yCoord, minY + inset, maxY - inset);
        double z = MathHelper.clamp_double(point.zCoord, minZ + inset, maxZ - inset);

        switch (blockData.facing()) {
            case DOWN:
                y = minY;
                break;
            case UP:
                y = maxY;
                break;
            case NORTH:
                z = minZ;
                break;
            case SOUTH:
                z = maxZ;
                break;
            case WEST:
                x = minX;
                break;
            case EAST:
                x = maxX;
                break;
        }

        return new Vec3(x, y, z);
    }

    private boolean canAlwaysClick() {
        return this.alwaysClick.getValue() && ItemUtil.isHoldingBlock() && this.blockCount > 0 && this.clickTimer.hasTimeElapsed(this.nextClickDelay);
    }

    private void resetClickTimer() {
        this.nextClickDelay = this.delayGenerator.nextDelay(this.minCps.getValue(), this.maxCps.getValue());
        this.clickTimer.reset();
    }

    private void sendAlwaysClick() {
        mc.playerController.syncCurrentPlayItem();
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getCurrentItem()));
    }

    private EnumFacing yawToFacing(float yaw) {
        if (yaw < -135.0F || yaw > 135.0F) {
            return EnumFacing.NORTH;
        } else if (yaw < -45.0F) {
            return EnumFacing.EAST;
        } else {
            return yaw < 45.0F ? EnumFacing.SOUTH : EnumFacing.WEST;
        }
    }

    private double distanceToEdge(EnumFacing enumFacing) {
        switch (enumFacing) {
            case NORTH:
                return mc.thePlayer.posZ - Math.floor(mc.thePlayer.posZ);
            case EAST:
                return Math.ceil(mc.thePlayer.posX) - mc.thePlayer.posX;
            case SOUTH:
                return Math.ceil(mc.thePlayer.posZ) - mc.thePlayer.posZ;
            case WEST:
            default:
                return mc.thePlayer.posX - Math.floor(mc.thePlayer.posX);
        }
    }

    private float getSpeed() {
        if (!mc.thePlayer.onGround) {
            return (float) this.airMotion.getValue() / 100.0F;
        } else {
            return MoveUtil.getSpeedLevel() > 0
                    ? (float) this.speedMotion.getValue() / 100.0F
                    : (float) this.groundMotion.getValue() / 100.0F;
        }
    }

    private double getRandomOffset() {
        return 0.2155 - RandomUtil.nextDouble(1.0E-4, 9.0E-4);
    }

    private float getCurrentYaw() {
        return MoveUtil.adjustYaw(
                mc.thePlayer.rotationYaw, (float) MoveUtil.getForwardValue(), (float) MoveUtil.getLeftValue()
        );
    }

    private boolean isDiagonal(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private boolean isTowering() {
        if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && !PlayerUtil.isAirAbove()) {
            boolean keepY = this.keepY.getValue() == 3;
            boolean tower = this.tower.getValue() == 3;
            return keepY && this.stage > 0 || tower && mc.gameSettings.keyBindJump.isKeyDown();
        } else {
            return false;
        }
    }

    public int getSlot() {
        return this.lastSlot;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if ((this.isEnabled() || this.easingOut) && event.getType() == EventType.PRE && this.isInventoryBlocked()) {
            this.resetRuntimeState(true);
            return;
        }

        if (this.easingOut && event.getType() == EventType.PRE) {
            this.returnToMouseRotation(event);
            return;
        }
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.rotationTick > 0) {
                this.rotationTick--;
            }
            if (predictionTower.getValue() && mc.thePlayer.motionY <= 0.0
                    && Math.sqrt(mc.thePlayer.motionX * mc.thePlayer.motionX + mc.thePlayer.motionZ * mc.thePlayer.motionZ) <= 0.02D
                    && mc.thePlayer.motionY >= -0.09
                    && !(Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode())
                    || Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode())
                    || Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode())
                    || Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()))
                    && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())) {
                mc.thePlayer.motionY = -0.38;
            }
            if (mc.thePlayer.onGround) {
                if (this.stage > 0) {
                    this.stage--;
                }
                if (this.stage < 0) {
                    this.stage++;
                }
                if (this.stage == 0
                        && this.keepY.getValue() != 0
                        && (!(Boolean) this.keepYonPress.getValue() || PlayerUtil.isUsingItem())
                        && !mc.gameSettings.keyBindJump.isKeyDown()) {
                    this.stage = 1;
                }
                this.startY = this.shouldKeepY ? this.startY : MathHelper.floor_double(mc.thePlayer.posY);
                this.shouldKeepY = false;
                this.towering = false;
            }
            if (this.canPlace()) {
                ItemStack stack = mc.thePlayer.getHeldItem();
                int count = ItemUtil.isBlock(stack) ? stack.stackSize : 0;
                this.blockCount = Math.min(this.blockCount, count);
                if (this.blockCount <= 0) {
                    int slot = mc.thePlayer.inventory.currentItem;
                    if (this.blockCount == 0) {
                        slot--;
                    }
                    for (int i = slot; i > slot - 9; i--) {
                        int hotbarSlot = (i % 9 + 9) % 9;
                        ItemStack candidate = mc.thePlayer.inventory.getStackInSlot(hotbarSlot);
                        if (ItemUtil.isBlock(candidate)) {
                            mc.thePlayer.inventory.currentItem = hotbarSlot;
                            this.blockCount = candidate.stackSize;
                            break;
                        }
                    }
                }
                float currentYaw = this.getCurrentYaw();
                float yawDiffTo180 = RotationUtil.wrapAngleDiff(currentYaw - 180.0F, event.getYaw());
                float diagonalYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F), event.getYaw());
                float strafeYaw = this.isDiagonal(currentYaw)
                        ? yawDiffTo180
                        : RotationUtil.wrapAngleDiff(currentYaw + 135.0F, event.getYaw());
                if (!this.canRotate) {
                    switch (this.rotationMode.getValue()) {
                        case 1:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(diagonalYaw);
                            }
                            break;
                        case 2:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            }
                            break;
                        case 3:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = RotationUtil.quantizeAngle(strafeYaw);
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            } else {
                                this.yaw = RotationUtil.quantizeAngle(strafeYaw);
                            }
                            break;
                        case 4:
                            if (this.yaw == -180.0F && this.pitch == 0.0F) {
                                this.yaw = (float) (RotationUtil.quantizeAngle(diagonalYaw) + RandomUtil.nextDouble(0.7d, 1.5d));
                                this.pitch = RotationUtil.quantizeAngle(85.0F);
                            }
                            break;
                        case 5:
                            BlockData currentBlockData = this.getBlockData();

                            if (currentBlockData != null) {
                                Vec3 targetVec = getVec3(currentBlockData);
                                float[] targetRots = RotationUtil.getRotationsTo(
                                        targetVec.xCoord - mc.thePlayer.posX,
                                        targetVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight(),
                                        targetVec.zCoord - mc.thePlayer.posZ,
                                        this.yaw,
                                        this.pitch
                                );
                                float targetYaw = targetRots[0];
                                float targetPitch = targetRots[1];
                                float predictedYaw = getPredictedYaw();
                                float currentYaw2 = this.yaw;
                                float currentPitch = this.pitch;
                                float yawToTarget = MathHelper.wrapAngleTo180_float(targetYaw - currentYaw2);
                                float pitchToTarget = targetPitch - currentPitch;
                                float absYawDiff = Math.abs(yawToTarget);
                                float distance = (float) mc.thePlayer.getDistance(
                                        currentBlockData.blockPos().getX() + 0.5,
                                        currentBlockData.blockPos().getY() + 0.5,
                                        currentBlockData.blockPos().getZ() + 0.5
                                );
                                float currentSpeed = getCurrentSpeed(distance);
                                float actualYawDiff = MathHelper.wrapAngleTo180_float(currentYaw - lastYaw);
                                float error = Math.abs(actualYawDiff - lastYawChange);
                                lastErrors[errorIndex] = error;
                                errorIndex = (errorIndex + 1) % 20;

                                float avgError = 0;
                                for (float e : lastErrors) {
                                    avgError += e;
                                }
                                avgError /= 20;

                                if (avgError > 5) {
                                    currentSpeed *= 0.8F;
                                } else if (avgError < 1) {
                                    currentSpeed *= 1.1F;
                                }

                                float yawChange;
                                if (absYawDiff > 90) {
                                    yawChange = Math.signum(yawToTarget) * currentSpeed * 1.2F;
                                } else if (absYawDiff > 30) {
                                    yawChange = Math.signum(yawToTarget) * currentSpeed * 0.8F;
                                } else {
                                    float fineSpeed = currentSpeed * 0.3F;
                                    yawChange = yawToTarget * 0.2F;
                                    yawChange = MathHelper.clamp_float(yawChange, -fineSpeed, fineSpeed);
                                }

                                float inertia = 0.3F;
                                yawChange = lastYawChange * inertia + yawChange * (1 - inertia);
                                lastYawChange = yawChange;
                                float pitchChange = Math.signum(pitchToTarget) * currentSpeed * 0.3F;
                                pitchChange = lastPitchChange * inertia + pitchChange * (1 - inertia);
                                lastPitchChange = pitchChange;

                                double ticks = 1.0;
                                double futureX = mc.thePlayer.posX + mc.thePlayer.motionX * ticks;
                                double futureY = mc.thePlayer.posY + mc.thePlayer.motionY * ticks;
                                double futureZ = mc.thePlayer.posZ + mc.thePlayer.motionZ * ticks;
                                BlockPos futureBlockPos = new BlockPos(
                                        MathHelper.floor_double(futureX),
                                        MathHelper.floor_double(futureY) - 1,
                                        MathHelper.floor_double(futureZ)
                                );

                                if (BlockUtil.isReplaceable(futureBlockPos)) {
                                    float[] futureRots = RotationUtil.getRotationsTo(
                                            futureBlockPos.getX() + 0.5 - mc.thePlayer.posX,
                                            futureBlockPos.getY() + 0.5 - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight(),
                                            futureBlockPos.getZ() + 0.5 - mc.thePlayer.posZ,
                                            this.yaw,
                                            this.pitch
                                    );
                                    yawToTarget = MathHelper.wrapAngleTo180_float(futureRots[0] - currentYaw2);
                                    pitchToTarget = futureRots[1] - currentPitch;
                                    yawChange += Math.signum(yawToTarget) * currentSpeed * 0.4F;
                                    pitchChange += Math.signum(pitchToTarget) * currentSpeed * 0.2F;
                                } else if (this.towering) {
                                    yawToTarget = MathHelper.wrapAngleTo180_float(predictedYaw - currentYaw2);
                                    yawChange += Math.signum(yawToTarget) * currentSpeed * 0.2F;
                                }

                                this.yaw = RotationUtil.quantizeAngle(currentYaw2 + yawChange);
                                this.pitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(currentPitch + pitchChange, 55.0F, 90.0F));
                                lastYaw = currentYaw;
                            }
                            break;
                    }
                }
                BlockData blockData = this.getBlockData();
                Vec3 hitVec = null;
                boolean canClick = !this.alwaysClick.getValue() || this.canAlwaysClick();
                boolean clicked = false;
                if (blockData != null) {
                    if (this.raytraceCheck.getValue()) {
                        PlacementTarget target = this.findClosestPlacementTarget(RotationUtil.wrapAngleDiff(this.yaw, event.getYaw()), this.pitch);
                        if (target != null) {
                            blockData = target.blockData;
                            this.yaw = target.yaw;
                            this.pitch = target.pitch;
                            hitVec = target.hitVec;
                            this.canRotate = true;
                        }
                    } else {
                        double[] x = placeOffsets;
                        double[] y = placeOffsets;
                        double[] z = placeOffsets;
                        switch (blockData.facing()) {
                            case NORTH:
                                z = new double[]{0.0};
                                break;
                            case EAST:
                                x = new double[]{1.0};
                                break;
                            case SOUTH:
                                z = new double[]{1.0};
                                break;
                            case WEST:
                                x = new double[]{0.0};
                                break;
                            case DOWN:
                                y = new double[]{0.0};
                                break;
                            case UP:
                                y = new double[]{1.0};
                        }
                        float bestYaw = -180.0F;
                        float bestPitch = 0.0F;
                        float bestDiff = 0.0F;
                        for (double dx : x) {
                            for (double dy : y) {
                                for (double dz : z) {
                                    double relX = (double) blockData.blockPos().getX() + dx - mc.thePlayer.posX;
                                    double relY = (double) blockData.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                    double relZ = (double) blockData.blockPos().getZ() + dz - mc.thePlayer.posZ;
                                    float baseYaw = RotationUtil.wrapAngleDiff(this.yaw, event.getYaw());
                                    float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, baseYaw, this.pitch);
                                    MovingObjectPosition mop = RotationUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                                    if (mop != null
                                            && mop.typeOfHit == MovingObjectType.BLOCK
                                            && mop.getBlockPos().equals(blockData.blockPos())
                                            && mop.sideHit == blockData.facing()) {
                                        float totalDiff = Math.abs(rotations[0] - baseYaw) + Math.abs(rotations[1] - this.pitch);
                                        if (bestYaw == -180.0F && bestPitch == 0.0F || totalDiff < bestDiff) {
                                            bestYaw = rotations[0];
                                            bestPitch = rotations[1];
                                            bestDiff = totalDiff;
                                            hitVec = mop.hitVec;
                                        }
                                    }
                                }
                            }
                        }
                        if (bestYaw != -180.0F || bestPitch != 0.0F) {
                            this.yaw = bestYaw;
                            this.pitch = bestPitch;
                            this.canRotate = true;
                        }
                    }
                }
                if ((!this.raytraceCheck.getValue() || hitVec == null)
                        && this.canRotate
                        && MoveUtil.isForwardPressed()
                        && Math.abs(MathHelper.wrapAngleTo180_float(yawDiffTo180 - this.yaw)) < 90.0F) {
                    switch (this.rotationMode.getValue()) {
                        case 2:
                            this.yaw = RotationUtil.quantizeAngle(yawDiffTo180);
                            break;
                        case 3:
                            this.yaw = RotationUtil.quantizeAngle(strafeYaw);
                            break;
                    }
                }
                if (this.rotationMode.getValue() != 0) {
                    float targetYaw = this.yaw;
                    float targetPitch = this.pitch;
                    if (this.towering && (mc.thePlayer.motionY > 0.0 || mc.thePlayer.posY > (double) (this.startY + 1))) {
                        float yawDiff = MathHelper.wrapAngleTo180_float(this.yaw - event.getYaw());
                        float tolerance = this.rotationTick >= 2 ? RandomUtil.nextFloat(115f, 120f) : RandomUtil.nextFloat(30f, 35f);
                        if (Math.abs(yawDiff) > tolerance) {
                            float clampedYaw = RotationUtil.clampAngle(yawDiff, tolerance);
                            targetYaw = RotationUtil.quantizeAngle(event.getYaw() + clampedYaw);
                            this.rotationTick = Math.max(this.rotationTick, 1);
                        }
                    }
                    if (this.isTowering()) {
                        float yawDelta = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - event.getYaw());
                        targetYaw = RotationUtil.quantizeAngle(event.getYaw() + yawDelta * RandomUtil.nextFloat(0.98F, 0.99F));
                        targetPitch = RotationUtil.quantizeAngle(RandomUtil.nextFloat(30.0F, 80.0F));
                        this.rotationTick = 3;
                        this.towering = true;
                    }
                    if (this.shouldUseClutchSpeed() && !this.clutchSmooth.getValue()) {
                        targetYaw = RotationUtil.quantizeAngle(targetYaw);
                        targetPitch = RotationUtil.quantizeAngle(MathHelper.clamp_float(targetPitch, -90.0F, 90.0F));
                        this.serverYaw = targetYaw;
                        this.serverPitch = targetPitch;
                    } else {
                        float[] rotations = this.interpolateRotation(targetYaw, targetPitch);
                        targetYaw = rotations[0];
                        targetPitch = rotations[1];
                    }
                    this.yaw = targetYaw;
                    this.pitch = targetPitch;
                    if (blockData != null) {
                        hitVec = this.getPlacementHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                    }
                    event.setRotation(targetYaw, targetPitch, 3);
                    if (this.moveFix.getValue() == 1) {
                        event.setPervRotation(targetYaw, 3);
                    }
                }
                if (blockData != null && hitVec != null && this.rotationTick <= 0 && canClick) {
                    Vec3 placementHitVec = this.raytraceCheck.getValue()
                            ? this.getPlacementHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch)
                            : hitVec;
                    if (placementHitVec == null) {
                        placementHitVec = this.getPlacementHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                    }
                    if (placementHitVec != null) {
                        clicked = this.place(blockData.blockPos(), blockData.facing(), placementHitVec) || clicked;
                    }
                    if (this.multiplace.getValue()) {
                        for (int i = 0; i < 3; i++) {
                            blockData = this.getBlockData();
                            if (blockData == null) {
                                break;
                            }
                            MovingObjectPosition mop = this.getMatchingRaytrace(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                            if (mop != null) {
                                clicked = this.place(blockData.blockPos(), blockData.facing(), mop.hitVec) || clicked;
                            } else {
                                if (this.raytraceCheck.getValue()) {
                                    break;
                                }
                                hitVec = BlockUtil.getClickVec(blockData.blockPos(), blockData.facing());
                                double dx = hitVec.xCoord - mc.thePlayer.posX;
                                double dy = hitVec.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                                double dz = hitVec.zCoord - mc.thePlayer.posZ;
                                float[] rotations = RotationUtil.getRotationsTo(dx, dy, dz, event.getYaw(), event.getPitch());
                                if (!(Math.abs(rotations[0] - this.yaw) < 120.0F) || !(Math.abs(rotations[1] - this.pitch) < 60.0F)) {
                                    break;
                                }
                                mop = this.getMatchingRaytrace(blockData.blockPos(), blockData.facing(), rotations[0], rotations[1]);
                                if (mop == null) {
                                    break;
                                }
                                clicked = this.place(blockData.blockPos(), blockData.facing(), mop.hitVec) || clicked;
                            }
                        }
                    }
                }
                if (this.targetFacing != null) {
                    if (this.rotationTick <= 0 && canClick) {
                        int playerBlockX = MathHelper.floor_double(mc.thePlayer.posX);
                        int playerBlockY = MathHelper.floor_double(mc.thePlayer.posY);
                        int playerBlockZ = MathHelper.floor_double(mc.thePlayer.posZ);
                        BlockPos belowPlayer = new BlockPos(playerBlockX, playerBlockY - 1, playerBlockZ);
                        hitVec = this.getPlacementHitVec(belowPlayer, this.targetFacing, this.yaw, this.pitch);
                        if (hitVec != null) {
                            clicked = this.place(belowPlayer, this.targetFacing, hitVec) || clicked;
                        }
                    }
                    this.targetFacing = null;
                } else if (this.keepY.getValue() == 2 && this.stage > 0 && !mc.thePlayer.onGround) {
                    int nextBlockY = MathHelper.floor_double(mc.thePlayer.posY + mc.thePlayer.motionY);
                    if (nextBlockY <= this.startY && mc.thePlayer.posY > (double) (this.startY + 1)) {
                        this.shouldKeepY = true;
                        blockData = this.getBlockData();
                        if (blockData != null && this.rotationTick <= 0 && canClick) {
                            hitVec = this.getPlacementHitVec(blockData.blockPos(), blockData.facing(), this.yaw, this.pitch);
                            if (hitVec != null) {
                                clicked = this.place(blockData.blockPos(), blockData.facing(), hitVec) || clicked;
                            }
                        }
                    }
                }
                if (this.alwaysClick.getValue() && canClick) {
                    if (!clicked) {
                        this.sendAlwaysClick();
                    }
                    this.resetClickTimer();
                }
            } else {
                this.returnToMouseRotation(event);
            }
        }
    }

    private float getCurrentSpeed(float distance) {
        float baseSpeed;
        if (this.towering) {
            baseSpeed = 40.0F;
        } else if (MoveUtil.getSpeedLevel() > 0) {
            baseSpeed = 35.0F;
        } else {
            baseSpeed = 25.0F;
        }
        float speedMultiplier = Math.min(1.2F, distance);
        float currentSpeed = baseSpeed * speedMultiplier;
        return Math.clamp(currentSpeed, 10.0F, 45.0F);
    }

    private float getPredictedYaw() {
        float currentMoveYaw = this.getCurrentYaw();
        if (this.isDiagonal(currentMoveYaw)) {
            return currentMoveYaw - 180.0F;
        }
        float sideMultiplier = (currentMoveYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F;
        return currentMoveYaw - 135.0F * sideMultiplier;
    }

    private Vec3 getVec3(BlockData data) {
        if (data == null) {
            return null;
        }

        BlockPos pos = data.blockPos();
        EnumFacing face = data.facing();
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;
        x += (double) face.getFrontOffsetX() * 0.5D;
        y += (double) face.getFrontOffsetY() * 0.5D;
        z += (double) face.getFrontOffsetZ() * 0.5D;
        return new Vec3(x, y, z);
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (this.isEnabled() && this.isInventoryBlocked()) {
            this.resetRuntimeState(true);
            return;
        }
        if (this.isEnabled()) {
            if (!mc.thePlayer.isCollidedHorizontally
                    && mc.thePlayer.hurtTime <= 5
                    && !mc.thePlayer.isPotionActive(Potion.jump)
                    && mc.gameSettings.keyBindJump.isKeyDown()
                    && ItemUtil.isHoldingBlock()) {
                int yState = (int) (mc.thePlayer.posY % 1.0 * 100.0);
                switch (this.tower.getValue()) {
                    case 1:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    this.towerTick = 2;
                                    mc.thePlayer.motionY = 0.42F;
                                    if (MoveUtil.isForwardPressed()) {
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                    } else {
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                    }
                                    return;
                                } else {
                                    this.towerTick = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = 0.75 - mc.thePlayer.posY % 1.0;
                                return;
                            case 3:
                                this.towerTick = 1;
                                mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                return;
                            default:
                                this.towerTick = 0;
                                return;
                        }
                    case 2:
                        switch (this.towerTick) {
                            case 0:
                                if (mc.thePlayer.onGround) {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = -0.0784000015258789;
                                }
                                return;
                            case 1:
                                if (yState == 0 && PlayerUtil.isAirBelow()) {
                                    this.startY = MathHelper.floor_double(mc.thePlayer.posY);
                                    if (!MoveUtil.isForwardPressed()) {
                                        this.towerDelay = 2;
                                        MoveUtil.setSpeed(0.0);
                                        event.setForward(0.0F);
                                        event.setStrafe(0.0F);
                                        EnumFacing facing = this.yawToFacing(MathHelper.wrapAngleTo180_float(this.yaw - 180.0F));
                                        double distance = this.distanceToEdge(facing);
                                        if (distance > 0.1) {
                                            if (mc.thePlayer.onGround) {
                                                Vec3i directionVec = facing.getDirectionVec();
                                                double offset = Math.min(this.getRandomOffset(), distance - 0.05);
                                                double jitter = RandomUtil.nextDouble(0.02, 0.03);
                                                AxisAlignedBB nextBox = mc.thePlayer
                                                        .getEntityBoundingBox()
                                                        .offset((double) directionVec.getX() * (offset - jitter), 0.0, (double) directionVec.getZ() * (offset - jitter));
                                                if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, nextBox).isEmpty()) {
                                                    mc.thePlayer.motionY = -0.0784000015258789;
                                                    mc.thePlayer
                                                            .setPosition(nextBox.minX + (nextBox.maxX - nextBox.minX) / 2.0, nextBox.minY, nextBox.minZ + (nextBox.maxZ - nextBox.minZ) / 2.0);
                                                }
                                                return;
                                            }
                                        } else {
                                            this.towerTick = 2;
                                            this.targetFacing = facing;
                                            mc.thePlayer.motionY = 0.42F;
                                        }
                                        return;
                                    } else {
                                        this.towerTick = 2;
                                        this.towerDelay++;
                                        mc.thePlayer.motionY = 0.42F;
                                        MoveUtil.setSpeed(MoveUtil.getSpeed(), MoveUtil.getMoveYaw());
                                        return;
                                    }
                                } else {
                                    this.towerTick = 0;
                                    this.towerDelay = 0;
                                    return;
                                }
                            case 2:
                                this.towerTick = 3;
                                mc.thePlayer.motionY = mc.thePlayer.motionY - RandomUtil.nextDouble(0.00101, 0.00109);
                                return;
                            case 3:
                                if (this.towerDelay >= 4) {
                                    this.towerTick = 4;
                                    this.towerDelay = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY = 1.0 - mc.thePlayer.posY % 1.0;
                                }
                                return;
                            case 4:
                                this.towerTick = 5;
                                return;
                            case 5:
                                if (!PlayerUtil.isAirBelow()) {
                                    this.towerTick = 0;
                                } else {
                                    this.towerTick = 1;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                    mc.thePlayer.motionY -= 0.08;
                                    mc.thePlayer.motionY *= 0.98F;
                                }
                                return;
                            default:
                                this.towerTick = 0;
                                this.towerDelay = 0;
                                return;
                        }
                    default:
                        this.towerTick = 0;
                        this.towerDelay = 0;
                }
            } else {
                this.towerTick = 0;
                this.towerDelay = 0;
            }
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked()) {
            if (this.moveFix.getValue() == 1
                    && RotationState.isActived()
                    && RotationState.getPriority() == 3.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (mc.thePlayer.onGround && this.stage > 0 && MoveUtil.isForwardPressed()) {
                mc.thePlayer.movementInput.jump = true;
            }
            if (this.sneak.getValue()
                    && mc.currentScreen == null
                    && !mc.thePlayer.movementInput.sneak
                    && this.shouldSneak()
                    && (this.sneakDelay > 0 || this.canMoveSafely())) {
                mc.thePlayer.movementInput.sneak = true;
                mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                mc.thePlayer.movementInput.moveForward *= 0.3F;
            }
        }
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked()) {
            float speed = this.getSpeed();
            if (speed != 1.0F) {
                if (mc.thePlayer.movementInput.moveForward != 0.0F && mc.thePlayer.movementInput.moveStrafe != 0.0F) {
                    mc.thePlayer.movementInput.moveForward = mc.thePlayer.movementInput.moveForward * (1.0F / (float) Math.sqrt(2.0));
                    mc.thePlayer.movementInput.moveStrafe = mc.thePlayer.movementInput.moveStrafe * (1.0F / (float) Math.sqrt(2.0));
                }
                mc.thePlayer.movementInput.moveForward *= speed;
                mc.thePlayer.movementInput.moveStrafe *= speed;
            }
            if (this.shouldStopSprint()) {
                mc.thePlayer.setSprinting(false);
            }
        }
    }

    @EventTarget
    public void onSafeWalk(SafeWalkEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked() && this.safeWalk.getValue()) {
            if (mc.thePlayer.onGround && mc.thePlayer.motionY <= 0.0 && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onRender(Render2DEvent event) {
        if (this.isEnabled()) {

            switch (blockCounterMode.getValue()) {
                case 0: {
                    break;
                }

                case 1: {
                    HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
                    float scale = hud.scale.getValue();
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scale, scale, 0.0F);
                    GlStateManager.disableDepth();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    mc.fontRendererObj
                            .drawString(
                                    String.format("%d block%s left", getBlockCount(), getBlockCount() != 1 ? "s" : ""),
                                    ((float) new ScaledResolution(mc).getScaledWidth() / 2.0F + (float) mc.fontRendererObj.FONT_HEIGHT * 1.5F) / scale,
                                    (float) new ScaledResolution(mc).getScaledHeight() / 2.0F / scale - (float) mc.fontRendererObj.FONT_HEIGHT / 2.0F + 1.0F,
                                    (getBlockCount() > 0 ? Color.WHITE.getRGB() : new Color(255, 85, 85).getRGB()) | -1090519040,
                                    hud.shadow.getValue()
                            );
                    GlStateManager.disableBlend();
                    GlStateManager.enableDepth();
                    GlStateManager.popMatrix();
                    break;
                }

                case 2: {
                    HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
                    float scale = hud.scale.getValue();
                    GlStateManager.pushMatrix();
                    GlStateManager.scale(scale, scale, 0.0F);
                    GlStateManager.disableDepth();
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    RenderUtil.drawOutlinedString(
                            Integer.toString(getBlockCount()),
                            ((new ScaledResolution(mc).getScaledWidth() - mc.fontRendererObj.FONT_HEIGHT * Integer.toString(getBlockCount()).codePointCount(0, Integer.toString(getBlockCount()).length()) * .5F) / 2.0F) / scale,
                            new ScaledResolution(mc).getScaledHeight() / 2.0F - 15F
                    );
                    GlStateManager.disableBlend();
                    GlStateManager.enableDepth();
                    GlStateManager.popMatrix();
                    break;
                }
            }
        }
    }

    public int getBlockCount() {
        int count = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.stackSize > 0) {
                Item item = stack.getItem();
                if (item instanceof ItemBlock) {
                    Block block = ((ItemBlock) item).getBlock();
                    if (!BlockUtil.isInteractable(block) && BlockUtil.isSolid(block)) {
                        count += stack.stackSize;
                    }
                }
            }
        }
        return count;
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked()) {
            this.lastSlot = event.setSlot(this.lastSlot);
            event.setCancelled(true);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean shouldEaseOut = !enabled
                && this.enabled
                && mc.thePlayer != null
                && this.rotationMode.getValue() != 0;
        if (shouldEaseOut) {
            this.easingOut = true;
        }
        super.setEnabled(enabled);
    }

    @Override
    public void onEnabled() {
        this.easingOut = false;
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        } else {
            this.lastSlot = -1;
        }
        this.resetRuntimeState(false);
        this.rotationTick = 3;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer != null && this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = this.lastSlot;
        }
        if (!this.easingOut) {
            this.resetRuntimeState(false);
        }
    }

    @Override
    public void verifyValue(String name) {
        switch (name) {
            case "sneak-min-delay":
                if (this.sneakMinDelay.getValue() > this.sneakMaxDelay.getValue()) {
                    this.sneakMaxDelay.setValue(this.sneakMinDelay.getValue());
                }
                break;
            case "sneak-max-delay":
                if (this.sneakMinDelay.getValue() > this.sneakMaxDelay.getValue()) {
                    this.sneakMinDelay.setValue(this.sneakMaxDelay.getValue());
                }
                break;
            case "min-cps":
                if (this.minCps.getValue() > this.maxCps.getValue()) {
                    this.maxCps.setValue(this.minCps.getValue());
                }
                break;
            case "max-cps":
                if (this.minCps.getValue() > this.maxCps.getValue()) {
                    this.minCps.setValue(this.maxCps.getValue());
                }
                break;
            case "clutch-min-aim-speed":
                if (this.clutchMinAimSpeed.getValue() > this.clutchMaxAimSpeed.getValue()) {
                    this.clutchMaxAimSpeed.setValue(this.clutchMinAimSpeed.getValue());
                }
                break;
            case "clutch-max-aim-speed":
                if (this.clutchMinAimSpeed.getValue() > this.clutchMaxAimSpeed.getValue()) {
                    this.clutchMinAimSpeed.setValue(this.clutchMaxAimSpeed.getValue());
                }
                break;
        }
    }

    public record BlockData(BlockPos blockPos, EnumFacing facing) {
    }

    private record PlacementTarget(BlockData blockData, Vec3 hitVec, float yaw, float pitch) {
    }
}
