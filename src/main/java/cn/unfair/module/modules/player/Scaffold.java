package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.*;
import cn.unfair.util.player.FallingPlayer;
import cn.unfair.util.player.SimulatedPlayer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.BlockWorkbench;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scaffold extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Telly", "Normal", "GodBridge"});
    public final BooleanProperty alwaysUpdateRot = new BooleanProperty("Always Update Rotation", false);
    public final IntProperty placeTick = new IntProperty("Place Tick", 1, 1, 5, () -> this.mode.getValue() == 0);
    public final IntProperty rotTick = new IntProperty("Rotation Tick", 1, 1, 5, () -> this.mode.getValue() == 0);
    public final BooleanProperty itemSpoof = new BooleanProperty("Spoof Item", true);
    public final BooleanProperty noSwing = new BooleanProperty("No Swing", false);
    public final BooleanProperty noUptelly = new BooleanProperty("No Up Telly", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty smoothed = new BooleanProperty("Smoothed", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty fixRotation = new BooleanProperty("Fix Rotation", true);
    public final BooleanProperty randomSlow = new BooleanProperty("Slow Up Telly", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty abuseRotation = new BooleanProperty("Abuse Rotation", true);
    public final ModeProperty blockSlotMode = new ModeProperty("Block Slot Mode", 0, new String[]{"Farthest", "Most Blocks"});
    public final ModeProperty jumpMode = new ModeProperty("Jump Mode", 1, new String[]{"Parkour", "Normal", "None"}, () -> this.mode.getValue() == 0);
    public final BooleanProperty godBridgeJump = new BooleanProperty("GodBridge Auto Jump", true, () -> this.mode.getValue() == 2);
    public final IntProperty godBridgeJumpMin = new IntProperty("GodBridge Jump Min", 4, 1, 8,
            () -> this.mode.getValue() == 2 && !godBridgeJump.getValue());
    public final IntProperty godBridgeJumpMax = new IntProperty("GodBridge Jump Max", 4, 1, 8,
            () -> this.mode.getValue() == 2 && !godBridgeJump.getValue());
    public final ModeProperty godBridgeRotations = new ModeProperty("GodBridge Rotations", 1,
            new String[]{"Off", "Normal", "Stabilized", "ReverseYaw", "GodBridge"},
            () -> this.mode.getValue() == 2);
    public final BooleanProperty godBridgeApplyServerSide = new BooleanProperty("GodBridge Apply Server Side", true,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final BooleanProperty godBridgeKeepRotation = new BooleanProperty("GodBridge Keep Rotation", true,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0 && godBridgeApplyServerSide.getValue());
    public final IntProperty godBridgeResetTicks = new IntProperty("GodBridge Reset Ticks", 1, 1, 20,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0 && godBridgeApplyServerSide.getValue());
    public final BooleanProperty godBridgeLegitimize = new BooleanProperty("GodBridge Legitimize", false,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final FloatProperty godBridgeHorizontalSpeedMin = new FloatProperty("GodBridge Horizontal Speed Min", 180.0F, 1.0F, 180.0F,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final FloatProperty godBridgeHorizontalSpeedMax = new FloatProperty("GodBridge Horizontal Speed Max", 180.0F, 1.0F, 180.0F,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final FloatProperty godBridgeVerticalSpeedMin = new FloatProperty("GodBridge Vertical Speed Min", 180.0F, 1.0F, 180.0F,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final FloatProperty godBridgeVerticalSpeedMax = new FloatProperty("GodBridge Vertical Speed Max", 180.0F, 1.0F, 180.0F,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final FloatProperty godBridgeAngleResetDifference = new FloatProperty("GodBridge Angle Reset Difference", 5.0F, 0.0F, 180.0F,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0 && godBridgeApplyServerSide.getValue());
    public final FloatProperty godBridgeMinRotationDifference = new FloatProperty("GodBridge Min Rotation Difference", 2.0F, 0.0F, 4.0F,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final ModeProperty godBridgeMinRotationTiming = new ModeProperty("GodBridge Min Rotation Timing", 0,
            new String[]{"OnStart", "OnSlowDown", "Always"},
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final BooleanProperty godBridgeWaitForRotations = new BooleanProperty("GodBridge Wait For Rotations", false,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final BooleanProperty godBridgeOptimizedPitch = new BooleanProperty("GodBridge Optimized Pitch", false,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0);
    public final FloatProperty godBridgePitch = new FloatProperty("GodBridge Pitch", 73.5F, 0.0F, 90.0F,
            () -> this.mode.getValue() == 2 && godBridgeRotations.getValue() != 0 && !godBridgeOptimizedPitch.getValue());
    public final BooleanProperty godBridgeAllowClutching = new BooleanProperty("GodBridge Allow Clutching", true,
            () -> this.mode.getValue() == 2);
    public final IntProperty godBridgeHorizontalClutch = new IntProperty("GodBridge Horizontal Clutch", 3, 1, 5,
            () -> this.mode.getValue() == 2 && godBridgeAllowClutching.getValue());
    public final IntProperty godBridgeVerticalClutch = new IntProperty("GodBridge Vertical Clutch", 2, 1, 3,
            () -> this.mode.getValue() == 2 && godBridgeAllowClutching.getValue());
    public final FloatProperty godBridgeSpeedModifier = new FloatProperty("GodBridge Speed Modifier", 1.0F, 0.0F, 2.0F,
            () -> this.mode.getValue() == 2);
    public final BooleanProperty godBridgeTrackCps = new BooleanProperty("GodBridge Track CPS", false,
            () -> this.mode.getValue() == 2);
    public final BooleanProperty godBridgeExtraClicks = new BooleanProperty("GodBridge Extra Clicks", false, () -> this.mode.getValue() == 2);
    public final BooleanProperty godBridgeDoubleClick = new BooleanProperty("GodBridge Double Click", false,
            () -> this.mode.getValue() == 2 && godBridgeExtraClicks.getValue());
    public final IntProperty godBridgeExtraClickMinCps = new IntProperty("GodBridge Extra Min CPS", 3, 0, 50,
            () -> this.mode.getValue() == 2 && godBridgeExtraClicks.getValue());
    public final IntProperty godBridgeExtraClickMaxCps = new IntProperty("GodBridge Extra Max CPS", 7, 0, 50,
            () -> this.mode.getValue() == 2 && godBridgeExtraClicks.getValue());
    public final ModeProperty godBridgePlacementAttempt = new ModeProperty("GodBridge Placement Attempt", 0,
            new String[]{"Fail", "Independent"}, () -> this.mode.getValue() == 2 && godBridgeExtraClicks.getValue());
    public final FloatProperty safeDistance = new FloatProperty("Clutch Safe Distance", 4.5F, 1.0F, 5.0F);
    public final BooleanProperty mark = new BooleanProperty("Mark", true);
    private final BooleanProperty duplicateRotPlace = new BooleanProperty("Duplicate Rot Place", true);
    private final BooleanProperty interactItem = new BooleanProperty("Interact Item Before Place", false);
    public final BooleanProperty blockCount = new BooleanProperty("Block Count", true);
    public final ModeProperty blockCountStyle = new ModeProperty("Block Count Style", 0, new String[]{"Retro", "Old"});
    public final IntProperty blockCountOffset = new IntProperty("Block Count Y Offset", 0, 0, 200);

    private static final List<Block> invalidBlocks = Arrays.asList(
            net.minecraft.init.Blocks.enchanting_table, net.minecraft.init.Blocks.chest, net.minecraft.init.Blocks.ender_chest,
            net.minecraft.init.Blocks.trapped_chest, net.minecraft.init.Blocks.anvil, net.minecraft.init.Blocks.sand,
            net.minecraft.init.Blocks.web, net.minecraft.init.Blocks.torch, net.minecraft.init.Blocks.crafting_table,
            net.minecraft.init.Blocks.furnace, net.minecraft.init.Blocks.dispenser, net.minecraft.init.Blocks.stone_pressure_plate,
            net.minecraft.init.Blocks.noteblock, net.minecraft.init.Blocks.dropper, net.minecraft.init.Blocks.tnt,
            net.minecraft.init.Blocks.redstone_torch, net.minecraft.init.Blocks.daylight_detector
    );

    private SlotData slot;
    private SlotData blockSlot;
    private int oldSlot;
    private int startHotbarCount = 1;
    private boolean canPlace;
    private BlockData blockData;
    private double posY;
    private BlockPos lastPlacePosition = null;
    private Rotation lastRotation;
    private Rotation rot;
    private GodBridgePlaceRotation godBridgePlaceRotation;
    private Rotation godBridgeTargetRotation;
    private Rotation godBridgeLimitedRotation;
    private boolean godBridgeResettingRotation = false;
    private int godBridgeRotationTicks = 0;
    private float godBridgeLastYawStep = 0.0F;
    private float godBridgeLastPitchStep = 0.0F;
    private int bridgePlaceCount = 0;
    private int godBridgeBlocksToJump = 4;
    private boolean bridgeJumping = false;
    private boolean godBridgeOnRightSide = false;
    private float godBridgeRawForward = 0.0F;
    private float godBridgeRawStrafe = 0.0F;
    private float godBridgeModifiedForward = 0.0F;
    private float godBridgeModifiedStrafe = 0.0F;
    private long godBridgeExtraClickLast = 0L;
    private int godBridgeExtraClickDelay = 0;
    private int godBridgeQueuedExtraClicks = 0;
    private final Deque<Long> godBridgeRightClicks = new ArrayDeque<>();
    private int ups = 0;
    private int onGroundTicks = 0;
    private int offGroundTicks = 0;

    public Scaffold() {
        super("Scaffold", false);
    }

    @Override
    public void onEnabled() {
        ups = 0;
        bridgePlaceCount = 0;
        godBridgeBlocksToJump = randomGodBridgeJumpInterval();
        bridgeJumping = false;
        godBridgePlaceRotation = null;
        godBridgeTargetRotation = null;
        godBridgeLimitedRotation = null;
        godBridgeResettingRotation = false;
        godBridgeRotationTicks = 0;
        godBridgeLastYawStep = 0.0F;
        godBridgeLastPitchStep = 0.0F;
        godBridgeOnRightSide = false;
        godBridgeRawForward = 0.0F;
        godBridgeRawStrafe = 0.0F;
        godBridgeModifiedForward = 0.0F;
        godBridgeModifiedStrafe = 0.0F;
        godBridgeExtraClickLast = 0L;
        godBridgeExtraClickDelay = randomGodBridgeClickDelay();
        godBridgeQueuedExtraClicks = 0;
        godBridgeRightClicks.clear();
        if (mc.thePlayer == null) {
            return;
        }
        lastRotation = new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        this.slot = new SlotData(mc.thePlayer.inventory.currentItem, false);
        this.oldSlot = mc.thePlayer.inventory.currentItem;
        this.blockSlot = null;
        startHotbarCount = Math.max(1, getBlockCountHotbar());
        blockData = null;
        canPlace = true;
        lastPlacePosition = null;
        rot = null;
        onGroundTicks = 0;
        offGroundTicks = 0;
    }

    @Override
    public void onDisabled() {
        bridgePlaceCount = 0;
        bridgeJumping = false;
        godBridgePlaceRotation = null;
        godBridgeTargetRotation = null;
        godBridgeLimitedRotation = null;
        godBridgeResettingRotation = false;
        godBridgeRotationTicks = 0;
        godBridgeLastYawStep = 0.0F;
        godBridgeLastPitchStep = 0.0F;
        godBridgeRawForward = 0.0F;
        godBridgeRawStrafe = 0.0F;
        godBridgeModifiedForward = 0.0F;
        godBridgeModifiedStrafe = 0.0F;
        godBridgeExtraClickLast = 0L;
        godBridgeExtraClickDelay = 0;
        godBridgeQueuedExtraClicks = 0;
        godBridgeRightClicks.clear();
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.inventory.currentItem = slot != null ? slot.slot() : oldSlot;
    }

    private boolean isValid(Item item) {
        return item instanceof ItemBlock
                && !invalidBlocks.contains(((ItemBlock) item).getBlock())
                && BlockUtil.isSolid(((ItemBlock) item).getBlock())
                && !BlockUtil.isInteractable(((ItemBlock) item).getBlock());
    }

    private boolean isFullBlock(ItemStack stack) {
        return stack != null && stack.stackSize > 0 && isValid(stack.getItem());
    }

    private int getHotbarBlockSlot() {
        if (blockSlotMode.getValue() == 1) {
            return getMostBlocksHotbarSlot();
        }
        int slot = -1;
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isFullBlock(stack)) {
                slot = i;
            }
        }
        return slot;
    }

    private int getMostBlocksHotbarSlot() {
        int selectedSlot = mc.thePlayer.inventory.currentItem;
        int bestSlot = -1;
        int bestCount = -1;
        ItemStack selectedStack = mc.thePlayer.inventory.getStackInSlot(selectedSlot);
        if (isFullBlock(selectedStack)) {
            bestSlot = selectedSlot;
            bestCount = selectedStack.stackSize;
        }
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isFullBlock(stack) && stack.stackSize > bestCount) {
                bestSlot = i;
                bestCount = stack.stackSize;
            }
        }
        return bestSlot;
    }

    private int getBlockCountHotbar() {
        if (mc.thePlayer == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isFullBlock(stack)) {
                count += stack.stackSize;
            }
        }
        ItemStack offhand = mc.thePlayer.inventory.viaforge$getOffhand();
        if (isFullBlock(offhand)) {
            count += offhand.stackSize;
        }
        return count;
    }

    private int getBlockCountColor(int count) {
        if (count < 16) {
            return new Color(255, 80, 80).getRGB();
        }
        if (count < 32) {
            return new Color(255, 220, 80).getRGB();
        }
        return Color.WHITE.getRGB();
    }

    private BlockData getBlockData(BlockPos pos) {
        BlockData data = getPos(pos);
        if (data == null) {
            BlockPos blockPos = getBlockPos();
            if (blockPos == null) {
                return null;
            }
            EnumFacing direction = getPlaceSide(blockPos);
            if (direction == null) {
                return null;
            }
            data = new BlockData(blockPos, direction);
        }
        if (BlockUtil.isReplaceable(data.blockPos().offset(data.facing()))) {
            return data;
        }
        return null;
    }

    private BlockData getPos(BlockPos pos) {
        if (isPosSolid(pos.add(-1, 0, 0))) {
            return new BlockData(pos.add(-1, 0, 0), EnumFacing.EAST);
        } else if (isPosSolid(pos.add(1, 0, 0))) {
            return new BlockData(pos.add(1, 0, 0), EnumFacing.WEST);
        } else if (isPosSolid(pos.add(0, 0, 1))) {
            return new BlockData(pos.add(0, 0, 1), EnumFacing.NORTH);
        } else if (isPosSolid(pos.add(0, 0, -1))) {
            return new BlockData(pos.add(0, 0, -1), EnumFacing.SOUTH);
        } else if (isPosSolid(pos.add(0, -1, 0))) {
            return new BlockData(pos.add(0, -1, 0), EnumFacing.UP);
        }
        return null;
    }

    private GodBridgePlaceRotation findGodBridgeTarget(BlockPos targetPos) {
        if (!BlockUtil.isReplaceable(targetPos)) {
            return null;
        }

        Rotation currentRotation = getGodBridgeClickRotation();
        GodBridgePlaceRotation best = null;
        for (EnumFacing side : EnumFacing.values()) {
            BlockPos supportPos = targetPos.offset(side);
            if (!canGodBridgeClick(supportPos)) {
                continue;
            }

            BlockData data = new BlockData(supportPos, side.getOpposite());
            Rotation rotation;
            MovingObjectPosition raycast = RayCastUtil.rayTrace(
                    currentRotation.yaw, currentRotation.pitch, mc.playerController.getBlockReachDistance(), 1.0F
            );
            if (raycast != null
                    && raycast.typeOfHit == MovingObjectType.BLOCK
                    && raycast.getBlockPos().equals(data.blockPos())
                    && raycast.sideHit == data.facing()) {
                rotation = currentRotation;
            } else {
                rotation = fixedSensitivity(getClosestToBlockFace(data, currentRotation.yaw, currentRotation.pitch));
                raycast = RayCastUtil.rayTrace(
                        rotation.yaw, rotation.pitch, mc.playerController.getBlockReachDistance(), 1.0F
                );
                if (raycast == null
                        || raycast.typeOfHit != MovingObjectType.BLOCK
                        || !raycast.getBlockPos().equals(data.blockPos())
                        || raycast.sideHit != data.facing()) {
                    continue;
                }
            }

            GodBridgePlaceRotation candidate = new GodBridgePlaceRotation(data, rotation, raycast.hitVec);
            if (best == null || getGodBridgeRotationDifference(
                    candidate.rotation(), currentRotation.yaw, currentRotation.pitch
            ) < getGodBridgeRotationDifference(best.rotation(), currentRotation.yaw, currentRotation.pitch)) {
                best = candidate;
            }
        }
        return best;
    }

    private GodBridgePlaceRotation searchGodBridgeTarget() {
        BlockPos blockPosition;
        if (mc.thePlayer.posY == Math.round(mc.thePlayer.posY) + 0.5D) {
            blockPosition = new BlockPos(mc.thePlayer);
        } else {
            blockPosition = new BlockPos(mc.thePlayer).down();
        }
        if (!BlockUtil.isReplaceable(blockPosition)) {
            return null;
        }

        GodBridgePlaceRotation direct = findGodBridgeTarget(blockPosition);
        if (direct != null) {
            return direct;
        }

        int horizontal = godBridgeAllowClutching.getValue() ? godBridgeHorizontalClutch.getValue() : 1;
        int vertical = godBridgeAllowClutching.getValue() ? godBridgeVerticalClutch.getValue() : 1;
        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.getAllInBox(
                blockPosition.add(-horizontal, 0, -horizontal),
                blockPosition.add(horizontal, -vertical, horizontal)
        )) {
            positions.add(pos);
        }
        Vec3 playerPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        positions.sort(Comparator.comparingDouble(pos -> new Vec3(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D
        ).squareDistanceTo(playerPosition)));

        for (BlockPos pos : positions) {
            if (canGodBridgeClick(pos)) {
                return null;
            }
            GodBridgePlaceRotation target = findGodBridgeTarget(pos);
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private void updateGodBridgePlaceTarget() {
        GodBridgePlaceRotation target = searchGodBridgeTarget();
        if (target != null) {
            godBridgePlaceRotation = target;
        }
        blockData = godBridgePlaceRotation != null ? godBridgePlaceRotation.blockData() : null;
        canPlace = true;
    }

    private BlockPos getBlockPos() {
        BlockPos playerPos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        ArrayList<BlockPos> positions = new ArrayList<>();
        for (Map.Entry<BlockPos, Block> block : searchBlocks(5).entrySet()) {
            if (isPosSolid(block.getKey())) {
                positions.add(block.getKey());
            }
        }
        positions.removeIf(pos -> pos.getY() >= playerPos.getY());
        if (positions.isEmpty()) {
            return null;
        }
        positions.sort(Comparator.comparingDouble(vec3 -> vec3.distanceSq(playerPos)));
        return positions.get(0);
    }

    private EnumFacing getPlaceSide(BlockPos blockPos) {
        List<BlockData> blockData = new ArrayList<>();
        BlockPos pos = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        if (isAirBlock(blockPos.east()) && !blockPos.east().equals(pos)) {
            blockData.add(new BlockData(blockPos.east(), EnumFacing.EAST));
        }
        if (isAirBlock(blockPos.north()) && !blockPos.north().equals(pos)) {
            blockData.add(new BlockData(blockPos.north(), EnumFacing.NORTH));
        }
        if (isAirBlock(blockPos.south()) && !blockPos.south().equals(pos)) {
            blockData.add(new BlockData(blockPos.south(), EnumFacing.SOUTH));
        }
        if (isAirBlock(blockPos.west()) && !blockPos.west().equals(pos)) {
            blockData.add(new BlockData(blockPos.west(), EnumFacing.WEST));
        }
        if (blockData.isEmpty()) {
            return null;
        }
        blockData.sort(Comparator.comparingDouble(vec3 -> vec3.blockPos().distanceSq(pos)));
        blockData.removeIf(bd -> !BlockUtil.isReplaceable(bd.blockPos().offset(bd.facing())));
        return blockData.get(0).facing();
    }

    private boolean isAirBlock(BlockPos blockPos) {
        return BlockUtil.isReplaceable(blockPos);
    }

    private Map<BlockPos, Block> searchBlocks(int radius) {
        Map<BlockPos, Block> blocks = new HashMap<>();
        if (mc.thePlayer == null) {
            return blocks;
        }
        for (int x = radius; x >= -radius + 1; x--) {
            for (int y = radius; y >= -radius + 1; y--) {
                for (int z = radius; z >= -radius + 1; z--) {
                    BlockPos blockPos = new BlockPos(
                            mc.thePlayer.getPosition().getX() + x,
                            mc.thePlayer.getPosition().getY() + y,
                            mc.thePlayer.getPosition().getZ() + z
                    );
                    Block block = mc.theWorld.getBlockState(blockPos).getBlock();
                    if (block != null) {
                        blocks.put(blockPos, block);
                    }
                }
            }
        }
        return blocks;
    }

    private boolean isPosSolid(BlockPos pos) {
        Block block = mc.theWorld.getBlockState(pos).getBlock();
        if (block instanceof net.minecraft.block.BlockTrapDoor
                || block instanceof net.minecraft.block.BlockDoor
                || block instanceof net.minecraft.block.BlockFenceGate) {
            return false;
        }
        return !BlockUtil.isReplaceable(pos) && BlockUtil.isSolid(block) && !BlockUtil.isInteractable(pos);
    }

    private boolean canGodBridgeClick(BlockPos pos) {
        if (!mc.theWorld.getWorldBorder().contains(pos)) {
            return false;
        }
        IBlockState state = mc.theWorld.getBlockState(pos);
        Block block = state.getBlock();
        if (!block.canCollideCheck(state, false)
                || block.getMaterial().isReplaceable()
                || block.hasTileEntity()
                || block.getCollisionBoundingBox(mc.theWorld, pos, state) == null
                || block instanceof BlockContainer
                || block instanceof BlockWorkbench) {
            return false;
        }
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityFallingBlock && entity.getPosition().equals(pos)) {
                return false;
            }
        }
        return true;
    }

    private static float smooth(float angle, float factor) {
        return angle * MathHelper.clamp_float(factor / 100.0F, 0.0F, 1.0F);
    }

    private Rotation getClosestToBlockFace(BlockData data, float yaw, float pitch) {
        if (data == null) {
            return null;
        }
        Vec3 face = getVec3(data);
        float[] rots = RotationUtil.getRotationsTo(
                face.xCoord - mc.thePlayer.posX,
                face.yCoord - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight(),
                face.zCoord - mc.thePlayer.posZ,
                yaw,
                pitch
        );
        return new Rotation(rots[0], rots[1]);
    }

    private Vec3 getVec3(BlockData data) {
        BlockPos pos = data.blockPos();
        EnumFacing face = data.facing();
        double x = pos.getX() + 0.5D + (double) face.getFrontOffsetX() * 0.5D;
        double y = pos.getY() + 0.5D + (double) face.getFrontOffsetY() * 0.5D;
        double z = pos.getZ() + 0.5D + (double) face.getFrontOffsetZ() * 0.5D;
        return new Vec3(x, y, z);
    }

    private static float yawDiffDirectly(float a, float b) {
        return MathHelper.wrapAngleTo180_float(a - b);
    }

    private static float normalizeYawDiff(float a, float b) {
        return Math.abs(MathHelper.wrapAngleTo180_float(a - b));
    }

    private float getServerYaw() {
        return RotationState.isActived() && RotationState.getPriority() == 3.0F
                ? RotationState.getSmoothedYaw()
                : mc.thePlayer.rotationYaw;
    }

    private float getServerPitch() {
        return RotationState.isActived() && RotationState.getPriority() == 3.0F
                ? RotationState.getRotationPitch()
                : mc.thePlayer.rotationPitch;
    }

    private Rotation getBRot() {
        if (mode.getValue() == 2) {
            return godBridgeRotation();
        }
        Rotation rotation = blockData != null
                ? getClosestToBlockFace(blockData, getServerYaw(), getServerPitch())
                : null;
        if (rotation == null) {
            if (normalizeYawDiff(mc.thePlayer.rotationYaw + 100f, getServerYaw()) < normalizeYawDiff(mc.thePlayer.rotationYaw - 100f, getServerYaw())) {
                rotation = new Rotation(mc.thePlayer.rotationYaw + 100f, getServerPitch());
            } else {
                rotation = new Rotation(mc.thePlayer.rotationYaw - 100f, getServerPitch());
            }
        }
        double diff = yawDiffDirectly(rotation.yaw, getServerYaw());
        if (mode.getValue() == 0) {
            if (mc.gameSettings.keyBindJump.isKeyDown() && noUptelly.getValue()) {
                return rotation;
            }
            if (mc.gameSettings.keyBindJump.isKeyDown() && randomSlow.getValue()) {
                ups++;
                if (ups % 2 == 0) {
                    return rotation;
                }
            }
            if (smoothed.getValue() && offGroundTicks < rotTick.getValue()) {
                if (onGroundTicks > 0) {
                    return new Rotation(mc.thePlayer.rotationYaw, 75.5f);
                } else {
                    float smoothFactor = offGroundTicks == 1 ? 80f : 50.0f;
                    smoothFactor -= (float) RandomUtil.nextDouble(0.001, 0.005);
                    rotation.yaw = getServerYaw() + smooth((float) diff, smoothFactor);
                }
            } else {
                if (offGroundTicks < rotTick.getValue()) {
                    return new Rotation(mc.thePlayer.rotationYaw, 85.0F + (float) Math.random());
                }
            }
        }
        if (lastRotation != null && blockData != null && didHitBlockFace(mc.thePlayer, lastRotation.yaw, lastRotation.pitch, blockData.blockPos(), blockData.facing())) {
            return lastRotation;
        }
        if (blockData != null && !alwaysUpdateRot.getValue() && offGroundTicks >= rotTick.getValue()) {
            if (!didHitBlockFace(mc.thePlayer, rotation.yaw, rotation.pitch, blockData.blockPos(), blockData.facing()) && offGroundTicks >= rotTick.getValue()) {
                lastRotation.yaw += (float) Math.random();
                return lastRotation;
            }
        }
        lastRotation = rotation;
        return rotation;
    }

    private Rotation godBridgeRotation() {
        return godBridgeLimitedRotation;
    }

    private boolean applyGodBridgeRotation(UpdateEvent event) {
        if (godBridgeRotations.getValue() == 0) {
            rot = null;
            return true;
        }

        rot = godBridgeRotation();
        if (rot == null) {
            return true;
        }

        if (godBridgeApplyServerSide.getValue()) {
            event.setRotation(rot.yaw, rot.pitch, 3);
            event.setPervRotation(rot.yaw, 3);
        } else {
            mc.thePlayer.rotationYaw = rot.yaw;
            mc.thePlayer.rotationPitch = rot.pitch;
        }
        return true;
    }

    private float getGodBridgeRotationDifference(Rotation target, float currentYaw, float currentPitch) {
        float yawDifference = MathHelper.wrapAngleTo180_float(target.yaw - currentYaw);
        float pitchDifference = target.pitch - currentPitch;
        return MathHelper.sqrt_float(yawDifference * yawDifference + pitchDifference * pitchDifference);
    }

    private void updateGodBridgeLimitedRotation() {
        if (godBridgeTargetRotation == null) {
            godBridgeLimitedRotation = null;
            return;
        }

        float currentYaw = getServerYaw();
        float currentPitch = getServerPitch();
        float yawDifference = MathHelper.wrapAngleTo180_float(godBridgeTargetRotation.yaw - currentYaw);
        float pitchDifference = godBridgeTargetRotation.pitch - currentPitch;
        float rotationDifference = MathHelper.sqrt_float(
                yawDifference * yawDifference + pitchDifference * pitchDifference
        );
        float horizontalSpeed = randomFloat(
                godBridgeHorizontalSpeedMin.getValue(), godBridgeHorizontalSpeedMax.getValue()
        );
        float verticalSpeed = randomFloat(
                godBridgeVerticalSpeedMin.getValue(), godBridgeVerticalSpeedMax.getValue()
        );
        float yawLimit = rotationDifference == 0.0F
                ? 0.0F
                : Math.abs(yawDifference / rotationDifference) * horizontalSpeed;
        float pitchLimit = rotationDifference == 0.0F
                ? 0.0F
                : Math.abs(pitchDifference / rotationDifference) * verticalSpeed;
        float yawStep = MathHelper.clamp_float(yawDifference, -yawLimit, yawLimit);
        float pitchStep = MathHelper.clamp_float(pitchDifference, -pitchLimit, pitchLimit);

        if (rotationDifference > 0.0F) {
            yawStep += randomFloat(-0.03F, 0.03F) * yawStep;
            pitchStep += randomFloat(-0.02F, 0.02F) * pitchStep;
        }

        float gcd = getGodBridgeGcd();
        float minDifference = godBridgeMinRotationDifference.getValue();
        float minYaw = fixedGodBridgeDelta(randomFloat(Math.min(minDifference, gcd), minDifference));
        float minPitch = fixedGodBridgeDelta(randomFloat(Math.min(minDifference, gcd), minDifference));
        yawStep = applyGodBridgeSlowDown(yawStep, minYaw, godBridgeLastYawStep);
        pitchStep = applyGodBridgeSlowDown(pitchStep, minPitch, godBridgeLastPitchStep);

        godBridgeLimitedRotation = fixedSensitivity(new Rotation(currentYaw + yawStep, currentPitch + pitchStep));
        godBridgeLastYawStep = MathHelper.wrapAngleTo180_float(godBridgeLimitedRotation.yaw - currentYaw);
        godBridgeLastPitchStep = godBridgeLimitedRotation.pitch - currentPitch;
    }

    private float applyGodBridgeSlowDown(float difference, float minimum, float previousDifference) {
        if (difference == 0.0F) {
            return 0.0F;
        }

        float differenceAbs = Math.abs(difference);
        boolean slowingDown = differenceAbs <= Math.abs(previousDifference);
        boolean stopAtMinimum = godBridgeMinRotationTiming.getValue() == 2
                || godBridgeMinRotationTiming.getValue() == 1 && slowingDown
                || godBridgeMinRotationTiming.getValue() == 0 && previousDifference == 0.0F;
        if (Math.abs(fixedGodBridgeDelta(difference)) <= minimum && stopAtMinimum) {
            return 0.0F;
        }
        if (!godBridgeLegitimize.getValue()) {
            return difference;
        }

        float interpolation;
        if (previousDifference == 0.0F) {
            float increase = 0.2F * MathHelper.clamp_float(differenceAbs / 50.0F, 0.0F, 1.0F);
            interpolation = randomFloat(0.1F + increase, 0.5F + increase);
        } else {
            interpolation = randomFloat(0.3F, 0.7F);
        }
        float result = previousDifference + (difference - previousDifference) * interpolation;
        return Math.abs(fixedGodBridgeDelta(result)) <= minimum && slowingDown ? difference : result;
    }

    private void resetGodBridgeRotationTarget() {
        if (!godBridgeApplyServerSide.getValue()) {
            godBridgeTargetRotation = null;
            godBridgeLimitedRotation = null;
            godBridgeResettingRotation = false;
            godBridgeLastYawStep = 0.0F;
            godBridgeLastPitchStep = 0.0F;
            return;
        }

        float currentYaw = getServerYaw();
        float currentPitch = getServerPitch();
        float resetYawDifference = Math.abs(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - currentYaw));
        float resetPitchDifference = Math.abs(mc.thePlayer.rotationPitch - currentPitch);
        boolean resetDifferenceIsDecreasing = resetYawDifference <= Math.abs(godBridgeLastYawStep)
                && resetPitchDifference <= Math.abs(godBridgeLastPitchStep);
        if (godBridgeResettingRotation
                && getGodBridgeRotationDifference(
                new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), currentYaw, currentPitch
        ) <= Math.abs(fixedGodBridgeDelta(godBridgeAngleResetDifference.getValue()))
                && (!godBridgeLegitimize.getValue() || resetDifferenceIsDecreasing)) {
            godBridgeTargetRotation = null;
            godBridgeLimitedRotation = null;
            godBridgeResettingRotation = false;
            godBridgeLastYawStep = 0.0F;
            godBridgeLastPitchStep = 0.0F;
            return;
        }

        godBridgeResettingRotation = true;
        float resetYaw = currentYaw + MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw - currentYaw);
        godBridgeTargetRotation = fixedSensitivity(new Rotation(resetYaw, mc.thePlayer.rotationPitch));
        updateGodBridgeLimitedRotation();
    }

    private void expireGodBridgeRotation() {
        if (godBridgeRotationTicks > 0) {
            godBridgeRotationTicks--;
            updateGodBridgeLimitedRotation();
            return;
        }
        if (godBridgeTargetRotation != null || godBridgeLimitedRotation != null) {
            resetGodBridgeRotationTarget();
        }
    }

    private void updateGodBridgeRotationTarget() {
        if (godBridgeRotations.getValue() == 0) {
            godBridgeTargetRotation = null;
            godBridgeLimitedRotation = null;
            godBridgeResettingRotation = false;
            godBridgeRotationTicks = 0;
            godBridgeLastYawStep = 0.0F;
            godBridgeLastPitchStep = 0.0F;
            return;
        }
        if (!isNearEdge(2.5F)) {
            expireGodBridgeRotation();
            return;
        }
        godBridgeResettingRotation = false;

        boolean moving = godBridgeRawForward != 0.0F || godBridgeRawStrafe != 0.0F;
        if (!moving) {
            Rotation placeRotation = godBridgePlaceRotation != null
                    ? godBridgePlaceRotation.rotation()
                    : null;
            if (placeRotation != null) {
                float axisMovement = (float) Math.floor(placeRotation.yaw / 90.0F) * 90.0F;
                godBridgeTargetRotation = fixedSensitivity(new Rotation(axisMovement + 45.0F, 75.0F));
                godBridgeRotationTicks = godBridgeResetTicks.getValue();
                updateGodBridgeLimitedRotation();
                return;
            }
            if (!godBridgeKeepRotation.getValue()) {
                expireGodBridgeRotation();
                return;
            }
        }

        float movingYaw = godBridgeApplyServerSide.getValue()
                ? snapGodBridgeYaw(getGodBridgeDirection() + 180.0F)
                : snapGodBridgeYaw(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw));
        boolean movingStraight = godBridgeApplyServerSide.getValue()
                ? movingYaw % 90.0F == 0.0F
                : (movingYaw == -135.0F || movingYaw == -45.0F || movingYaw == 45.0F || movingYaw == 135.0F)
                && godBridgeRawStrafe != 0.0F;

        Rotation rotation;
        if (movingStraight) {
            if (mc.thePlayer.onGround) {
                float yawRadians = movingYaw * 0.017453292F;
                godBridgeOnRightSide = MathHelper.floor_double(mc.thePlayer.posX + Math.cos(yawRadians) * 0.5D)
                        != MathHelper.floor_double(mc.thePlayer.posX)
                        || MathHelper.floor_double(mc.thePlayer.posZ + Math.sin(yawRadians) * 0.5D)
                        != MathHelper.floor_double(mc.thePlayer.posZ);

                EnumFacing facing = EnumFacing.fromAngle(movingYaw);
                BlockPos posInDirection = new BlockPos(
                        mc.thePlayer.posX + facing.getFrontOffsetX() * 0.6D,
                        mc.thePlayer.posY,
                        mc.thePlayer.posZ + facing.getFrontOffsetZ() * 0.6D
                );
                boolean leaningOffBlock = mc.theWorld.isAirBlock(mc.thePlayer.getPosition().down());
                boolean nextBlockIsAir = mc.theWorld.isAirBlock(posInDirection.down());
                if (leaningOffBlock && nextBlockIsAir) {
                    godBridgeOnRightSide = !godBridgeOnRightSide;
                }
            }

            float side = godBridgeApplyServerSide.getValue()
                    ? (godBridgeOnRightSide ? 45.0F : -45.0F)
                    : 0.0F;
            float pitch = godBridgeOptimizedPitch.getValue() ? 73.5F : godBridgePitch.getValue();
            rotation = new Rotation(movingYaw + side, pitch);
        } else {
            rotation = new Rotation(movingYaw, 75.6F);
        }

        godBridgeTargetRotation = fixedSensitivity(rotation);
        godBridgeRotationTicks = godBridgeApplyServerSide.getValue() ? godBridgeResetTicks.getValue() : 1;
        updateGodBridgeLimitedRotation();
    }

    private static float snapGodBridgeYaw(float yaw) {
        return (float) Math.rint(yaw / 45.0F) * 45.0F;
    }

    private float getGodBridgeDirection() {
        float yaw = mc.thePlayer.rotationYaw;
        float forward = 1.0F;
        float forwardInput = godBridgeRawForward;
        float strafeInput = godBridgeRawStrafe;
        if (forwardInput < 0) {
            yaw += 180.0F;
            forward = -0.5F;
        } else if (forwardInput > 0) {
            forward = 0.5F;
        }
        if (strafeInput < 0) {
            yaw += 90.0F * forward;
        } else if (strafeInput > 0) {
            yaw -= 90.0F * forward;
        }
        return yaw;
    }

    private boolean isNearEdge(float threshold) {
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos blockPos = new BlockPos(playerPos);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                BlockPos checkPos = blockPos.add(x, -1, z);
                if (mc.theWorld.isAirBlock(checkPos)) {
                    Vec3 center = new Vec3(checkPos.getX() + 0.5D, checkPos.getY(), checkPos.getZ() + 0.5D);
                    if (playerPos.distanceTo(center) <= threshold) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Rotation fixedSensitivity(Rotation rotation) {
        if (rotation == null) {
            return null;
        }
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = sensitivity * sensitivity * sensitivity * 1.2F;
        float baseYaw = RotationState.isActived() && RotationState.getPriority() == 3.0F
                ? RotationState.getSmoothedYaw()
                : mc.thePlayer.rotationYaw;
        float basePitch = RotationState.isActived() && RotationState.getPriority() == 3.0F
                ? RotationState.getRotationPitch()
                : mc.thePlayer.rotationPitch;
        float yaw = baseYaw + Math.round((rotation.yaw - baseYaw) / gcd) * gcd;
        float pitch = basePitch + Math.round((rotation.pitch - basePitch) / gcd) * gcd;
        return new Rotation(yaw, MathHelper.clamp_float(pitch, -90.0F, 90.0F));
    }

    private float getGodBridgeGcd() {
        float sensitivity = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return sensitivity * sensitivity * sensitivity * 1.2F;
    }

    private float fixedGodBridgeDelta(float delta) {
        float gcd = getGodBridgeGcd();
        return Math.round(delta / gcd) * gcd;
    }

    private static float randomFloat(float first, float second) {
        float minimum = Math.min(first, second);
        float maximum = Math.max(first, second);
        return minimum == maximum ? minimum : RandomUtil.nextFloat(minimum, maximum);
    }

    private int randomGodBridgeJumpInterval() {
        int minimum = Math.min(godBridgeJumpMin.getValue(), godBridgeJumpMax.getValue());
        int maximum = Math.max(godBridgeJumpMin.getValue(), godBridgeJumpMax.getValue());
        return minimum == maximum ? minimum : RandomUtil.nextInt(minimum, maximum + 1);
    }

    private int randomGodBridgeClickDelay() {
        int minCps = Math.min(godBridgeExtraClickMinCps.getValue(), godBridgeExtraClickMaxCps.getValue());
        int maxCps = Math.max(godBridgeExtraClickMinCps.getValue(), godBridgeExtraClickMaxCps.getValue());
        int minDelay = minCps == 0 ? 0 : 1000 / minCps;
        int maxDelay = maxCps == 0 ? 0 : 1000 / maxCps;
        return (int) Math.round(Math.random() * (minDelay - maxDelay) + maxDelay);
    }

    private Rotation getGodBridgeClickRotation() {
        if (godBridgeLimitedRotation != null) {
            return godBridgeLimitedRotation;
        }
        if (RotationState.isActived() && RotationState.getPriority() == 3.0F) {
            return new Rotation(RotationState.getSmoothedYaw(), RotationState.getRotationPitch());
        }
        return new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
    }

    private void registerGodBridgeRightClick() {
        if (!godBridgeTrackCps.getValue()) {
            return;
        }
        long now = System.currentTimeMillis();
        godBridgeRightClicks.addLast(now);
        while (!godBridgeRightClicks.isEmpty() && now - godBridgeRightClicks.peekFirst() > 1000L) {
            godBridgeRightClicks.removeFirst();
        }
    }

    public int getGodBridgeRightCps() {
        long now = System.currentTimeMillis();
        while (!godBridgeRightClicks.isEmpty() && now - godBridgeRightClicks.peekFirst() > 1000L) {
            godBridgeRightClicks.removeFirst();
        }
        return godBridgeRightClicks.size();
    }

    private boolean clickGodBridgeBlock(
            ItemStack stack, BlockPos clickPos, EnumFacing side, Vec3 hitVec, boolean placementAttempt
    ) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }

        int previousSize = stack.stackSize;
        boolean clicked = mc.playerController.onPlayerRightClick(
                mc.thePlayer, mc.theWorld, stack, clickPos, side, hitVec
        );
        registerGodBridgeRightClick();
        if (!clicked) {
            if (mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, stack)) {
                mc.entityRenderer.itemRenderer.resetEquippedProgress2();
            }
            return false;
        }

        lastPlacePosition = clickPos.offset(side);
        if (!placementAttempt && mc.thePlayer.onGround) {
            mc.thePlayer.motionX *= godBridgeSpeedModifier.getValue();
            mc.thePlayer.motionZ *= godBridgeSpeedModifier.getValue();
        }
        if (noSwing.getValue()) {
            mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        } else {
            mc.thePlayer.swingItem();
        }
        if (!godBridgeJump.getValue()) {
            bridgePlaceCount++;
        }
        if (stack.stackSize <= 0 && blockSlot != null && !blockSlot.offhand()) {
            mc.thePlayer.inventory.mainInventory[mc.thePlayer.inventory.currentItem] = null;
        } else if (stack.stackSize != previousSize || mc.playerController.isInCreativeMode()) {
            mc.entityRenderer.itemRenderer.resetEquippedProgress();
        }
        blockData = null;
        godBridgePlaceRotation = null;
        return true;
    }

    private boolean doGodBridgePlaceAttempt(MovingObjectPosition raycast) {
        if (raycast == null || raycast.typeOfHit != MovingObjectType.BLOCK || blockSlot == null || blockSlot.check()) {
            return false;
        }

        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (!isFullBlock(stack)) {
            return false;
        }
        ItemBlock itemBlock = (ItemBlock) stack.getItem();
        boolean canPlaceOnUpperFace = itemBlock.canPlaceBlockOnSide(
                mc.theWorld, raycast.getBlockPos(), EnumFacing.UP, mc.thePlayer, stack
        );
        boolean shouldPlace;
        if (godBridgePlacementAttempt.getValue() == 0) {
            shouldPlace = !itemBlock.canPlaceBlockOnSide(
                    mc.theWorld, raycast.getBlockPos(), raycast.sideHit, mc.thePlayer, stack
            );
        } else {
            int placementY = MathHelper.floor_double(mc.thePlayer.posY) - 1;
            shouldPlace = raycast.getBlockPos().getY() <= placementY
                    && !(raycast.getBlockPos().getY() == placementY
                    && canPlaceOnUpperFace
                    && raycast.sideHit == EnumFacing.UP);
        }
        return shouldPlace && clickGodBridgeBlock(
                stack, raycast.getBlockPos(), raycast.sideHit, raycast.hitVec, true
        );
    }

    private void runGodBridgeClick() {
        if (blockSlot == null || blockSlot.check()) {
            return;
        }

        Rotation clickRotation = getGodBridgeClickRotation();
        MovingObjectPosition raycast = RayCastUtil.rayTrace(
                clickRotation.yaw, clickRotation.pitch, mc.playerController.getBlockReachDistance(), 1.0F
        );
        boolean alreadyPlaced = false;
        if (godBridgeExtraClicks.getValue()) {
            int doubleClick = godBridgeDoubleClick.getValue() ? RandomUtil.nextInt(-1, 2) : 0;
            int clicks = godBridgeQueuedExtraClicks + doubleClick;
            for (int i = 0; i < clicks; i++) {
                godBridgeQueuedExtraClicks--;
                if (doGodBridgePlaceAttempt(raycast)) {
                    alreadyPlaced = true;
                }
            }
        }

        BlockData target = godBridgePlaceRotation != null ? godBridgePlaceRotation.blockData() : null;
        if (alreadyPlaced || target == null) {
            return;
        }
        if (godBridgeRotations.getValue() == 0) {
            clickGodBridgeBlock(
                    mc.thePlayer.inventory.getCurrentItem(), target.blockPos(), target.facing(),
                    godBridgePlaceRotation.hitVec(), false
            );
            return;
        }
        if (raycast == null || raycast.typeOfHit != MovingObjectType.BLOCK) {
            return;
        }
        if (!raycast.getBlockPos().equals(target.blockPos()) || raycast.sideHit != target.facing()) {
            return;
        }
        clickGodBridgeBlock(
                mc.thePlayer.inventory.getCurrentItem(), raycast.getBlockPos(), raycast.sideHit, raycast.hitVec, false
        );
    }

    private static boolean didHitBlockFace(Entity player, float yaw, float pitch, BlockPos targetPos, EnumFacing expectedFace) {
        if (player == null || expectedFace == null) {
            return false;
        }
        MovingObjectPosition result = RayCastUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (result == null || result.typeOfHit != MovingObjectType.BLOCK) {
            return false;
        }
        return result.getBlockPos().equals(targetPos) && (result.sideHit == expectedFace);
    }

    private void place() {
        if (blockData == null) {
            return;
        }
        if (rot == null) {
            return;
        }
        if (mc.playerController == null) {
            return;
        }
        if (!canPlace) {
            return;
        }
        MovingObjectPosition mop = RayCastUtil.rayTrace(rot.yaw, rot.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK
                || !mop.getBlockPos().equals(blockData.blockPos()) || mop.sideHit != blockData.facing()) {
            return;
        }
        Vec3 hitVec = mop.hitVec;
        if (!this.blockSlot.offhand()) {
            mc.thePlayer.inventory.currentItem = this.blockSlot.slot();
        }
        if (interactItem.getValue()) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockData.blockPos(), blockData.facing(), hitVec)) {
            lastPlacePosition = blockData.blockPos().offset(blockData.facing());
            if (noSwing.getValue()) {
                PacketUtil.sendPacket(new C0APacketAnimation());
            } else {
                mc.thePlayer.swingItem();
            }
            if (mode.getValue() == 2 && !godBridgeJump.getValue()) {
                bridgePlaceCount++;
            }
        }
    }

    private void rotationAbuse(float step, float targetYaw) {
        if (rot == null) {
            return;
        }
        double change = yawDiffDirectly(rot.yaw, targetYaw);
        int times = (int) (Math.abs(change) / step);
        float currentYaw = rot.yaw;
        for (int i = 0; i < times; i++) {
            currentYaw += smooth((float) change, step);
            rot = new Rotation(currentYaw, rot.pitch);
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        }
        rot = new Rotation(targetYaw, rot.pitch);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (event.getType() == EventType.POST) {
            if (mode.getValue() == 2) {
                updateGodBridgeRotationTarget();
            }
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }

        if (mc.thePlayer.onGround) {
            onGroundTicks++;
            offGroundTicks = 0;
        } else {
            onGroundTicks = 0;
            offGroundTicks++;
        }

        boolean hasGodBridgeRotation = mode.getValue() != 2 || applyGodBridgeRotation(event);

        this.blockSlot = null;

        ItemStack offhand = mc.thePlayer.inventory.viaforge$getOffhand();
        if (isFullBlock(offhand)) {
            this.blockSlot = new SlotData(-1, true);
        }
        if (this.blockSlot == null && blockSlotMode.getValue() != 1) {
            if (isFullBlock(mc.thePlayer.getHeldItem())) {
                this.blockSlot = new SlotData(mc.thePlayer.inventory.currentItem, false);
            }
        }
        if (this.blockSlot == null) {
            int hotbarSlot = getHotbarBlockSlot();
            if (hotbarSlot != -1) {
                this.blockSlot = new SlotData(hotbarSlot, false);
            }
        }
        if (this.blockSlot == null || blockSlot.check()) {
            return;
        }

        if (mc.thePlayer.onGround) {
            bridgeJumping = false;
            posY = MathHelper.floor_double(mc.thePlayer.posY - 1);
        }
        if (mc.gameSettings.keyBindJump.isKeyDown() || bridgeJumping) {
            posY = mc.thePlayer.getPosition().getY() - 1;
        }

        if (mode.getValue() == 2) {
            updateGodBridgePlaceTarget();
        } else {
            BlockPos playerBlock = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY),
                    MathHelper.floor_double(mc.thePlayer.posZ)
            );
            BlockData possible = BlockUtil.isReplaceable(playerBlock)
                    ? getBlockData(new BlockPos(playerBlock.getX(), (int) posY, playerBlock.getZ()))
                    : null;
            if (possible != null) {
                blockData = possible;
            }

            canPlace = mode.getValue() != 0 || offGroundTicks >= placeTick.getValue();

            FallingPlayer fallingPlayer = new FallingPlayer(mc.thePlayer);
            fallingPlayer.calculate(1);
            Vec3 nextEyePos = fallingPlayer.getEyePos();
            fallingPlayer.calculate(1);
            BlockData placement = getBlockData(new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    mc.thePlayer.getPosition().getY() - 1,
                    MathHelper.floor_double(mc.thePlayer.posZ)
            ));
            if (placement != null) {
                double distance = nextEyePos.distanceTo(new Vec3(
                        placement.blockPos().getX() + 0.5D,
                        placement.blockPos().getY() + 0.5D,
                        placement.blockPos().getZ() + 0.5D
                ));
                if (distance >= safeDistance.getValue() || placement.blockPos().getY() > fallingPlayer.getY()) {
                    canPlace = true;
                    blockData = placement;
                }
            }
            if (blockData != null) {
                AxisAlignedBB box = new AxisAlignedBB(
                        blockData.blockPos().getX(),
                        blockData.blockPos().getY() - 1,
                        blockData.blockPos().getZ(),
                        blockData.blockPos().getX() + 1,
                        blockData.blockPos().getY() + 1,
                        blockData.blockPos().getZ() + 1
                );
                if (blockData.blockPos().getY() > fallingPlayer.getY() && !box.isVecInside(new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ))) {
                    canPlace = true;
                    posY = mc.thePlayer.getPosition().getY() - 1;
                    blockData = getBlockData(new BlockPos(
                            MathHelper.floor_double(mc.thePlayer.posX),
                            MathHelper.floor_double(posY),
                            MathHelper.floor_double(mc.thePlayer.posZ)
                    ));
                }
            }
        }

        if (!this.blockSlot.offhand()) {
            mc.thePlayer.inventory.currentItem = this.blockSlot.slot();
        }

        if (mode.getValue() != 2) {
            rot = getBRot();
            if (rot == null) {
                return;
            }
            if (duplicateRotPlace.getValue()) {
                rot.pitch -= (float) RandomUtil.nextDouble(0.001, 0.003);
                rot.yaw -= (float) RandomUtil.nextDouble(0.0001, 0.0003);
                do {
                    rot.pitch -= (float) RandomUtil.nextDouble(0.001, 0.003);
                } while (rot.pitch > 90.0F);
                if (rot.pitch < -90.0F) {
                    rot.pitch = -90.0F;
                }
            }
            if (fixRotation.getValue()) {
                rot = new Rotation(rot.yaw, rot.pitch);
            }
            event.setRotation(rot.yaw, rot.pitch, 3);
            event.setPervRotation(rot.yaw, 3);
        } else if (!hasGodBridgeRotation) {
            return;
        }

        if (mode.getValue() != 2) {
            if (abuseRotation.getValue()) {
                rotationAbuse(30f, rot.yaw);
            }
            place();
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()
                || mode.getValue() != 2
                || event.type() != EventType.POST
                || mc.thePlayer == null
                || mc.theWorld == null) {
            return;
        }
        runGodBridgeClick();
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.blockSlot == null || blockSlot.check()) {
            return;
        }
        if (onGroundTicks > 0
                && !mc.gameSettings.keyBindJump.isKeyDown()
                && MoveUtil.isForwardPressed()
                && mode.getValue() == 0) {
            switch (jumpMode.getValue()) {
                case 0:
                    double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
                    double forwardX = -Math.sin(yaw);
                    double forwardZ = Math.cos(yaw);
                    BlockPos front1 = new BlockPos(
                            (int) (mc.thePlayer.posX + forwardX),
                            (int) (mc.thePlayer.posY - 0.1),
                            (int) (mc.thePlayer.posZ + forwardZ)
                    );
                    BlockPos front2 = new BlockPos(
                            (int) (mc.thePlayer.posX + forwardX * 2),
                            (int) (mc.thePlayer.posY - 0.1),
                            (int) (mc.thePlayer.posZ + forwardZ * 2)
                    );
                    if (BlockUtil.isReplaceable(front1) || BlockUtil.isReplaceable(front2)) {
                        mc.thePlayer.jump();
                    }
                    break;
                case 1:
                    mc.thePlayer.jump();
                    break;
                case 2:
                    break;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (mode.getValue() == 2) {
            godBridgeRawForward = mc.thePlayer.movementInput.moveForward;
            godBridgeRawStrafe = mc.thePlayer.movementInput.moveStrafe;
        }

        if (mode.getValue() == 2 && mc.thePlayer.onGround) {
            if (godBridgeRotations.getValue() != 0
                    && godBridgeWaitForRotations.getValue()
                    && godBridgeTargetRotation != null) {
                float currentYaw = RotationState.isActived() && RotationState.getPriority() == 3.0F
                        ? RotationState.getSmoothedYaw()
                        : mc.thePlayer.rotationYaw;
                float currentPitch = RotationState.isActived() && RotationState.getPriority() == 3.0F
                        ? RotationState.getRotationPitch()
                        : mc.thePlayer.rotationPitch;
                float gcd = getGodBridgeGcd();
                if (getGodBridgeRotationDifference(godBridgeTargetRotation, currentYaw, currentPitch) > gcd
                        && !mc.thePlayer.movementInput.sneak) {
                    mc.thePlayer.movementInput.sneak = true;
                    mc.thePlayer.movementInput.moveForward *= 0.3F;
                    mc.thePlayer.movementInput.moveStrafe *= 0.3F;
                }
            }

            MovementInput predictionInput = new MovementInput();
            predictionInput.moveForward = godBridgeModifiedForward;
            predictionInput.moveStrafe = godBridgeModifiedStrafe;
            SimulatedPlayer simulatedPlayer = SimulatedPlayer.fromClientPlayer(predictionInput, false, true);
            simulatedPlayer.rotationYaw = RotationState.isActived() && RotationState.getPriority() == 3.0F
                    ? RotationState.getSmoothedYaw()
                    : mc.thePlayer.rotationYaw;
            simulatedPlayer.tick();

            boolean shouldJump = godBridgeJump.getValue()
                    ? !simulatedPlayer.onGround
                    : bridgePlaceCount > godBridgeBlocksToJump;
            if (shouldJump) {
                mc.thePlayer.movementInput.jump = true;
                bridgeJumping = true;
                bridgePlaceCount = 0;
                godBridgeBlocksToJump = randomGodBridgeJumpInterval();
            }
        }

        boolean hasMovementInput = mode.getValue() == 2
                ? godBridgeRawForward != 0.0F || godBridgeRawStrafe != 0.0F
                : MoveUtil.isForwardPressed();
        if (RotationState.isActived()
                && RotationState.getPriority() == 3.0F
                && hasMovementInput
                && (mode.getValue() != 2
                || godBridgeRotations.getValue() != 0 && godBridgeApplyServerSide.getValue())) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }

        if (mode.getValue() == 2) {
            godBridgeModifiedForward = mc.thePlayer.movementInput.moveForward;
            godBridgeModifiedStrafe = mc.thePlayer.movementInput.moveStrafe;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()
                && mode.getValue() == 2
                && godBridgeExtraClicks.getValue()
                && mc.thePlayer != null
                && mc.theWorld != null
                && (godBridgeRawForward != 0.0F || godBridgeRawStrafe != 0.0F || MoveUtil.getSpeed() > 0.03D)) {
            Rotation clickRotation = getGodBridgeClickRotation();
            MovingObjectPosition raycast = RayCastUtil.rayTrace(
                    clickRotation.yaw, clickRotation.pitch, mc.playerController.getBlockReachDistance(), 1.0F
            );
            long now = System.currentTimeMillis();
            if (raycast != null
                    && raycast.typeOfHit == MovingObjectType.BLOCK
                    && now - godBridgeExtraClickLast >= godBridgeExtraClickDelay) {
                godBridgeExtraClickLast = now;
                godBridgeExtraClickDelay = randomGodBridgeClickDelay();
                godBridgeQueuedExtraClicks++;
            }
        }

        if (lastPlacePosition != null && mark.getValue() && this.isEnabled()) {
            AxisAlignedBB box = new AxisAlignedBB(
                    lastPlacePosition.getX() - mc.getRenderManager().getRenderPosX(),
                    lastPlacePosition.getY() - mc.getRenderManager().getRenderPosY(),
                    lastPlacePosition.getZ() - mc.getRenderManager().getRenderPosZ(),
                    lastPlacePosition.getX() + 1 - mc.getRenderManager().getRenderPosX(),
                    lastPlacePosition.getY() + 1 - mc.getRenderManager().getRenderPosY(),
                    lastPlacePosition.getZ() + 1 - mc.getRenderManager().getRenderPosZ()
            );
            RenderUtil.enableRenderState();
            RenderUtil.drawBoundingBox(box, 255, 255, 255, 150, 1.0F);
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || !blockCount.getValue()) {
            return;
        }
        int newCount = Math.max(0, getBlockCountHotbar());
        if (newCount > startHotbarCount) {
            startHotbarCount = newCount;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        float centerX = sr.getScaledWidth() / 2f;
        float centerY = sr.getScaledHeight() / 2f;
        float y = centerY + 15f + blockCountOffset.getValue();

        if (blockCountStyle.getValue() == 1) {
            String text = newCount + " Blocks";
            int x = Math.round(centerX - (mc.fontRendererObj.getStringWidth(text) / 2f));
            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            mc.fontRendererObj.drawStringWithShadow(text, x, y, getBlockCountColor(newCount));
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        } else {
            String text = newCount + " Blocks";
            int x = Math.round(centerX - (mc.fontRendererObj.getStringWidth(text) / 2f));
            GlStateManager.pushMatrix();
            GlStateManager.disableDepth();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            mc.fontRendererObj.drawStringWithShadow(text, x, y, getBlockCountColor(newCount));
            GlStateManager.disableBlend();
            GlStateManager.enableDepth();
            GlStateManager.popMatrix();
        }
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled() && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isEnabled() && !(mc.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer)) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled()) {
            this.oldSlot = event.setSlot(this.oldSlot);
            event.setCancelled(true);
        }
    }

    public int getSlot() {
        return this.oldSlot;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{mode.getModeString()};
    }

    public record BlockData(BlockPos blockPos, EnumFacing facing) {
    }

    private record GodBridgePlaceRotation(BlockData blockData, Rotation rotation, Vec3 hitVec) {
    }

    private record SlotData(int slot, boolean offhand) {
        public boolean check() {
            if (mc.thePlayer == null) {
                return true;
            }
            if (offhand) {
                ItemStack stack = mc.thePlayer.inventory.viaforge$getOffhand();
                return stack == null || !(stack.getItem() instanceof ItemBlock);
            }
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack == null || !(stack.getItem() instanceof ItemBlock);
        }
    }

    private static final class Rotation {
        float yaw;
        float pitch;

        Rotation(float yaw, float pitch) {
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
