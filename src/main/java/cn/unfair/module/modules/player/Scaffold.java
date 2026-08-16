package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
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

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Telly", "Snap", "Normal"});
    public final BooleanProperty alwaysUpdateRot = new BooleanProperty("Always Update Rotation", false);
    public final IntProperty placeTick = new IntProperty("Place Tick", 1, 1, 5, () -> this.mode.getValue() == 0);
    public final IntProperty rotTick = new IntProperty("Rotation Tick", 1, 1, 5, () -> this.mode.getValue() == 0);
    public final BooleanProperty itemSpoof = new BooleanProperty("Spoof Item", true);
    public final BooleanProperty noSwing = new BooleanProperty("No Swing", false);
    public final BooleanProperty eagle = new BooleanProperty("Eagle", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty snap = new BooleanProperty("Snap", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty noUptelly = new BooleanProperty("No Up Telly", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty smoothed = new BooleanProperty("Smoothed", true, () -> this.mode.getValue() == 0);
    public final BooleanProperty safeMode = new BooleanProperty("Safe Mode", false, () -> this.mode.getValue() == 0 && this.smoothed.getValue());
    public final BooleanProperty testOnGround = new BooleanProperty("Test On Ground", false, () -> this.mode.getValue() == 0 && this.smoothed.getValue());
    public final BooleanProperty fixRotation = new BooleanProperty("Fix Rotation", true);
    public final BooleanProperty randomSlow = new BooleanProperty("Slow Up Telly", false, () -> this.mode.getValue() == 0);
    public final BooleanProperty blockFly = new BooleanProperty("Block Fly", false);
    public final BooleanProperty abuseRotation = new BooleanProperty("Abuse Rotation", true);
    public final ModeProperty blockSlotMode = new ModeProperty("Block Slot Mode", 0, new String[]{"Farthest", "Most Blocks"});
    public final ModeProperty jumpMode = new ModeProperty("Jump Mode", 1, new String[]{"Parkour", "Normal", "None"}, () -> this.mode.getValue() == 0);
    public final FloatProperty safeDistance = new FloatProperty("Clutch Safe Distance", 4.5F, 1.0F, 5.0F);
    public final IntProperty tellyEagleTick = new IntProperty("Eagle Tick", 1, 1, 5, () -> this.mode.getValue() == 0 && this.eagle.getValue());
    public final IntProperty keepEagleSneakTick = new IntProperty("Keep Eagle Tick", 1, 1, 5, () -> this.mode.getValue() == 0 && this.eagle.getValue());
    public final BooleanProperty dbgV = new BooleanProperty("Debug", false);
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
    private BlockData lastBlockData;
    private int rotateCount = 0;
    private double posY;
    private BlockPos lastPlacePosition = null;
    private int tellyJumpTicks;
    private boolean waitingForEagleSneak;
    private Rotation lastRotation;
    private Rotation rot;
    private int placeCount = 0;
    private int ups = 0;
    private int onGroundTicks = 0;
    private int offGroundTicks = 0;
    private boolean cancelMove = false;

    public Scaffold() {
        super("Scaffold", false);
    }

    @Override
    public void onEnabled() {
        placeCount = 0;
        ups = 0;
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
        tellyJumpTicks = 0;
        waitingForEagleSneak = false;
        rot = null;
        onGroundTicks = 0;
        offGroundTicks = 0;
        cancelMove = false;
    }

    @Override
    public void onDisabled() {
        if (mc.thePlayer == null) {
            return;
        }
        mc.thePlayer.inventory.currentItem = slot != null ? slot.slot() : oldSlot;
        mc.gameSettings.keyBindSneak.pressed = false;
        cancelMove = false;
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

    private Rotation getBRot(boolean forceRotation) {
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
        if (cancelMove) {
            return getClosestToBlockFace(blockData, getServerYaw(), getServerPitch());
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
            if (smoothed.getValue() && (offGroundTicks < rotTick.getValue() || safeMode.getValue())) {
                if (onGroundTicks > 0) {
                    if (safeMode.getValue() && (!testOnGround.getValue() || mc.gameSettings.keyBindJump.isKeyDown())) {
                        switch (onGroundTicks) {
                            case 1: {
                                if (!forceRotation) {
                                    rotation.yaw = getServerYaw() + smooth((float) diff, 50.0F);
                                    rotation.pitch = 75.5f;
                                } else {
                                    rotation = getClosestToBlockFace(blockData, mc.thePlayer.rotationYaw, getServerPitch());
                                }
                                break;
                            }
                            case 2: {
                                return new Rotation(mc.thePlayer.rotationYaw, 75.5f);
                            }
                        }
                    } else {
                        return new Rotation(mc.thePlayer.rotationYaw, 75.5f);
                    }
                } else {
                    float smoothFactor = offGroundTicks == 1 ? 80f : 50.0f;
                    smoothFactor -= (float) RandomUtil.nextDouble(0.001, 0.005);
                    rotation.yaw = getServerYaw() + smooth((float) diff, smoothFactor);
                }
            } else {
                if (snap.getValue() && mc.gameSettings.keyBindJump.isKeyDown()) {
                    if (lastBlockData == null || offGroundTicks < rotTick.getValue()) {
                        return new Rotation(mc.thePlayer.rotationYaw, 85.0F + (float) Math.random());
                    }
                } else if (offGroundTicks < rotTick.getValue()) {
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

    private static boolean didHitBlockFace(BlockData blockData, Rotation rot) {
        return blockData == null || !didHitBlockFace(rot, blockData.blockPos(), blockData.facing(), true);
    }

    private boolean doesNotContainBlock(int down) {
        return BlockUtil.isReplaceable(mc.thePlayer.getPosition().down(down));
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
        if (!didHitBlockFace(mc.thePlayer, rot.yaw, rot.pitch, blockData.blockPos(), blockData.facing(), true)) {
            return;
        }
        if (!this.blockSlot.offhand()) {
            mc.thePlayer.inventory.currentItem = this.blockSlot.slot();
        }
        if (interactItem.getValue()) {
            mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem());
        }
        Vec3 hitVec = BlockUtil.getHitVec(blockData.blockPos(), blockData.facing(), rot.yaw, rot.pitch);
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.inventory.getCurrentItem(), blockData.blockPos(), blockData.facing(), hitVec)) {
            placeCount++;
            lastPlacePosition = blockData.blockPos().offset(blockData.facing());
            if (noSwing.getValue()) {
                PacketUtil.sendPacket(new C0APacketAnimation());
            } else {
                mc.thePlayer.swingItem();
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

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (event.type() != EventType.POST) {
            return;
        }
        if (!this.isEnabled()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        if (blockFly.getValue()) {
        }
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
            posY = MathHelper.floor_double(mc.thePlayer.posY - 1);
        }
        if (mc.gameSettings.keyBindJump.isKeyDown()) {
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

        if (mode.getValue() == 2) {
            canPlace = true;
        } else if (mode.getValue() == 1) {
            canPlace = doesNotContainBlock(1);
        } else {
            canPlace = offGroundTicks >= placeTick.getValue();
            if (safeMode.getValue() && testOnGround.getValue() && !canPlace && mc.gameSettings.keyBindJump.isKeyDown()) {
                canPlace = onGroundTicks == 1;
            }
        }

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
        boolean forceRotation = false;
        if (placement != null) {
            if (safeMode.getValue() && testOnGround.getValue() && onGroundTicks == 1 && mc.gameSettings.keyBindJump.isKeyDown()) {
                forceRotation = true;
            }
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

        if (!reachable && rotateCount < 8) {
            if (dbgV.getValue() && rotateCount == 1) {
                ChatUtil.sendFormatted("working");
            }
            cancelMove = true;
            rotateCount++;
        } else {
            rotateCount = 0;
        }

        rot = getBRot(forceRotation);
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
        if (didHitBlockFace(blockData, rot)) {
            this.cancelMove = false;
            this.rotateCount = 0;
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

        if (waitingForEagleSneak) {
            tellyJumpTicks++;
            if (tellyJumpTicks == tellyEagleTick.getValue() && !mc.gameSettings.keyBindSneak.isKeyDown()) {
                mc.gameSettings.keyBindSneak.pressed = true;
            }
            if (tellyJumpTicks == tellyEagleTick.getValue() + keepEagleSneakTick.getValue()) {
                mc.gameSettings.keyBindSneak.pressed = false;
                waitingForEagleSneak = false;
                tellyJumpTicks = 0;
            }
        }
    }

    @EventTarget
    public void onStrafe(StrafeEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.blockSlot == null || blockSlot.check()) {
            return;
        }
        if (onGroundTicks > (smoothed.getValue() && safeMode.getValue() && !testOnGround.getValue() ? 1 : 0)
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
            if (eagle.getValue() && mode.getValue() == 0) {
                waitingForEagleSneak = true;
                tellyJumpTicks = 0;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (this.cancelMove) {
            mc.thePlayer.movementInput.moveForward = 0.0F;
            mc.thePlayer.movementInput.moveStrafe = 0.0F;
            mc.thePlayer.motionX = 0.0;
            mc.thePlayer.motionY = 0.0;
            mc.thePlayer.motionZ = 0.0;
        } else if (RotationState.isActived() && RotationState.getPriority() == 3.0F && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
        if (mode.getValue() == 0 && eagle.getValue()) {
            mc.thePlayer.movementInput.sneak = placeCount % 4 == 0;
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
