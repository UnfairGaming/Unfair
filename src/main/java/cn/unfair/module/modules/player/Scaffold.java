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
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    public final FloatProperty godBridgePitch = new FloatProperty("GodBridge Pitch", 73.5F, 0.0F, 90.0F, () -> this.mode.getValue() == 2);
    public final BooleanProperty godBridgeJump = new BooleanProperty("GodBridge Auto Jump", true, () -> this.mode.getValue() == 2);
    public final IntProperty godBridgeJumpEvery = new IntProperty("GodBridge Jump Every", 7, 1, 20, () -> this.mode.getValue() == 2 && godBridgeJump.getValue());
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

    private static final double[] placeOffsets = new double[]{
            0.03125, 0.09375, 0.15625, 0.21875, 0.28125, 0.34375,
            0.40625, 0.46875, 0.53125, 0.59375, 0.65625, 0.71875,
            0.78125, 0.84375, 0.90625, 0.96875
    };

    private SlotData slot;
    private SlotData blockSlot;
    private int oldSlot;
    private int startHotbarCount = 1;
    private boolean canPlace;
    private BlockData blockData;
    private BlockData lastBlockData;
    private double posY;
    private BlockPos lastPlacePosition = null;
    private Vec3 godBridgeHitVec;
    private Rotation lastRotation;
    private Rotation rot;
    private int bridgePlaceCount = 0;
    private boolean bridgeJumping = false;
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
        bridgeJumping = false;
        if (mc.thePlayer == null) {
            return;
        }
        lastRotation = new Rotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        this.slot = new SlotData(mc.thePlayer.inventory.currentItem, false);
        this.oldSlot = mc.thePlayer.inventory.currentItem;
        this.blockSlot = null;
        startHotbarCount = Math.max(1, getBlockCountHotbar());
        blockData = null;
        lastBlockData = null;
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
        return mc.thePlayer.rotationPitch;
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
        if (lastRotation != null && blockData != null && didHitBlockFace(mc.thePlayer, lastRotation.yaw, lastRotation.pitch, blockData.blockPos(), blockData.facing(), true)) {
            return lastRotation;
        }
        if (blockData != null && !alwaysUpdateRot.getValue() && offGroundTicks >= rotTick.getValue()) {
            if (!didHitBlockFace(mc.thePlayer, rotation.yaw, rotation.pitch, blockData.blockPos(), blockData.facing(), true) && offGroundTicks >= rotTick.getValue()) {
                lastRotation.yaw += (float) Math.random();
                return lastRotation;
            }
        }
        lastRotation = rotation;
        return rotation;
    }

    private Rotation godBridgeRotation() {
        float currentYaw = MoveUtil.adjustYaw(mc.thePlayer.rotationYaw, MoveUtil.getForwardValue(), MoveUtil.getLeftValue());
        float lookYaw = MathHelper.wrapAngleTo180_float(
                isDiagonalYaw(currentYaw)
                        ? currentYaw - 180.0F
                        : currentYaw - 135.0F * ((currentYaw + 180.0F) % 90.0F < 45.0F ? 1.0F : -1.0F));
        godBridgeHitVec = null;
        float pitch = godBridgePitch.getValue();
        if (blockData != null) {
            MovingObjectPosition check = RayCastUtil.rayTrace(lookYaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
            if (check != null && check.typeOfHit == MovingObjectType.BLOCK
                    && check.getBlockPos().equals(blockData.blockPos()) && check.sideHit == blockData.facing()) {
                godBridgeHitVec = check.hitVec;
            } else {
                scanGodBridgeBlock(blockData, lookYaw, pitch);
            }
        }
        return new Rotation(lookYaw, pitch);
    }

    private static boolean isDiagonalYaw(float yaw) {
        float absYaw = Math.abs(yaw % 90.0F);
        return absYaw > 20.0F && absYaw < 70.0F;
    }

    private float[] scanGodBridgeBlock(BlockData data, float yaw, float pitch) {
        double[] x = placeOffsets;
        double[] y = placeOffsets;
        double[] z = placeOffsets;
        switch (data.facing()) {
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
                break;
        }
        float bestYaw = -180.0F;
        float bestPitch = 0.0F;
        float bestDiff = 0.0F;
        for (double dx : x) {
            for (double dy : y) {
                for (double dz : z) {
                    double relX = data.blockPos().getX() + dx - mc.thePlayer.posX;
                    double relY = data.blockPos().getY() + dy - mc.thePlayer.posY - (double) mc.thePlayer.getEyeHeight();
                    double relZ = data.blockPos().getZ() + dz - mc.thePlayer.posZ;
                    float[] rotations = RotationUtil.getRotationsTo(relX, relY, relZ, yaw, pitch);
                    MovingObjectPosition mop = RayCastUtil.rayTrace(rotations[0], rotations[1], mc.playerController.getBlockReachDistance(), 1.0F);
                    if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                            && mop.getBlockPos().equals(data.blockPos()) && mop.sideHit == data.facing()) {
                        float totalDiff = Math.abs(rotations[0] - yaw) + Math.abs(rotations[1] - pitch);
                        if (bestYaw == -180.0F || totalDiff < bestDiff) {
                            bestYaw = rotations[0];
                            bestPitch = rotations[1];
                            bestDiff = totalDiff;
                            godBridgeHitVec = mop.hitVec;
                        }
                    }
                }
            }
        }
        if (bestYaw != -180.0F || bestPitch != 0.0F) {
            return new float[]{bestYaw, bestPitch};
        }
        return null;
    }

    private static boolean didHitBlockFace(Entity player, float yaw, float pitch, BlockPos targetPos, EnumFacing expectedFace, boolean strict) {
        if (player == null || expectedFace == null) {
            return false;
        }
        MovingObjectPosition result = RayCastUtil.rayTrace(yaw, pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (result == null || result.typeOfHit != MovingObjectType.BLOCK) {
            return false;
        }
        return result.getBlockPos().equals(targetPos) && (!strict || result.sideHit == expectedFace);
    }

    private static boolean didHitBlockFace(Rotation rotation, BlockPos targetPos, EnumFacing expectedFace, boolean strict) {
        return didHitBlockFace(mc.thePlayer, rotation.yaw, rotation.pitch, targetPos, expectedFace, strict);
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
        Vec3 hitVec;
        MovingObjectPosition mop = RayCastUtil.rayTrace(rot.yaw, rot.pitch, mc.playerController.getBlockReachDistance(), 1.0F);
        if (mode.getValue() == 2) {
            if (godBridgeHitVec == null) {
                return;
            }
            hitVec = (mop != null && mop.typeOfHit == MovingObjectType.BLOCK
                    && mop.getBlockPos().equals(blockData.blockPos()) && mop.sideHit == blockData.facing())
                    ? mop.hitVec
                    : godBridgeHitVec;
        } else {
            if (mop == null || mop.typeOfHit != MovingObjectType.BLOCK
                    || !mop.getBlockPos().equals(blockData.blockPos()) || mop.sideHit != blockData.facing()) {
                return;
            }
            hitVec = mop.hitVec;
        }
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
            if (mode.getValue() == 2 && godBridgeJump.getValue() && ++bridgePlaceCount >= godBridgeJumpEvery.getValue()) {
                bridgePlaceCount = 0;
                if (mc.thePlayer.onGround) {
                    bridgeJumping = true;
                    mc.thePlayer.jump();
                }
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
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (mc.thePlayer.onGround) {
            onGroundTicks++;
            offGroundTicks = 0;
        } else {
            onGroundTicks = 0;
            offGroundTicks++;
        }

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
        lastBlockData = possible;

        canPlace = mode.getValue() == 0 ? offGroundTicks >= placeTick.getValue() : true;

        if (!this.blockSlot.offhand()) {
            mc.thePlayer.inventory.currentItem = this.blockSlot.slot();
        }

        FallingPlayer fallingPlayer = new FallingPlayer(mc.thePlayer);
        boolean reachable = true;
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
                reachable = false;
                blockData = lastBlockData = placement;
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
                reachable = false;
                posY = mc.thePlayer.getPosition().getY() - 1;
                blockData = lastBlockData = getBlockData(new BlockPos(
                        MathHelper.floor_double(mc.thePlayer.posX),
                        MathHelper.floor_double(posY),
                        MathHelper.floor_double(mc.thePlayer.posZ)
                ));
            }
        }

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

        if (abuseRotation.getValue()) {
            rotationAbuse(30f, rot.yaw);
        }
        place();
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
        if (RotationState.isActived() && RotationState.getPriority() == 3.0F && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
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
