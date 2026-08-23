package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.render.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockMobSpawner;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S22PacketMultiBlockChange.BlockUpdateData;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;
import net.minecraft.util.Vec3i;

import java.awt.*;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArraySet;

public class Xray extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final LinkedHashSet<Integer> xrayBlocks;
    private static final LinkedHashSet<Vec3i> caveOffsetsSmall;
    private static final LinkedHashSet<Vec3i> caveOffsetsLarge;

    static {
        xrayBlocks = new LinkedHashSet<>(Arrays.asList(56, 14, 15, 16, 73, 74, 21, 129, 52, 83, 115));
        caveOffsetsSmall = new LinkedHashSet<>(
                Arrays.asList(new Vec3i(0, -1, 0), new Vec3i(1, 0, 0), new Vec3i(0, 0, -1), new Vec3i(0, 0, 1), new Vec3i(-1, 0, 0), new Vec3i(0, 1, 0))
        );
        caveOffsetsLarge = new LinkedHashSet<>(
                Arrays.asList(
                        new Vec3i(0, -2, 0),
                        new Vec3i(1, -1, 0),
                        new Vec3i(0, -1, -1),
                        new Vec3i(0, -1, 0),
                        new Vec3i(0, -1, 1),
                        new Vec3i(-1, -1, 0),
                        new Vec3i(2, 0, 0),
                        new Vec3i(0, 0, 2),
                        new Vec3i(0, 0, -2),
                        new Vec3i(-2, 0, 0),
                        new Vec3i(1, 0, -1),
                        new Vec3i(1, 0, 0),
                        new Vec3i(1, 0, 1),
                        new Vec3i(0, 0, -1),
                        new Vec3i(0, 0, 1),
                        new Vec3i(-1, 0, -1),
                        new Vec3i(-1, 0, 0),
                        new Vec3i(-1, 0, 1),
                        new Vec3i(1, 1, 0),
                        new Vec3i(0, 1, -1),
                        new Vec3i(0, 1, 0),
                        new Vec3i(0, 1, 1),
                        new Vec3i(-1, 1, 0),
                        new Vec3i(0, 2, 0)
                )
        );
    }

    public final CopyOnWriteArraySet<BlockPos> trackedBlocks = new CopyOnWriteArraySet<>();
    public final CopyOnWriteArraySet<BlockPos> pendingBlocks = new CopyOnWriteArraySet<>();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Soft", "Full"});
    public final PercentProperty opacity = new PercentProperty("Opacity", 50);
    public final IntProperty range = new IntProperty("Range", 64, 16, 512);
    public final BooleanProperty cavesOnly = new BooleanProperty("Caves Only", true);
    public final IntProperty caveRadius = new IntProperty("Caves Radius", 2, 1, 2);
    public final BooleanProperty diamonds = new BooleanProperty("Diamonds", true);
    public final BooleanProperty diamondTracers = new BooleanProperty("Diamonds Tracers", true);
    public final BooleanProperty gold = new BooleanProperty("Gold", true);
    public final BooleanProperty goldTracers = new BooleanProperty("Gold Tracers", true);
    public final BooleanProperty iron = new BooleanProperty("Iron", false);
    public final BooleanProperty ironTracers = new BooleanProperty("Iron Tracers", false);
    public final BooleanProperty coal = new BooleanProperty("Coal", false);
    public final BooleanProperty coalTracers = new BooleanProperty("Coal Tracers", false);
    public final BooleanProperty redstone = new BooleanProperty("Redstone", false);
    public final BooleanProperty redStoneTracers = new BooleanProperty("Redstone Tracers", false);
    public final BooleanProperty lapis = new BooleanProperty("Lapis", false);
    public final BooleanProperty lapisTracers = new BooleanProperty("Lapis Tracers", false);
    public final BooleanProperty emeralds = new BooleanProperty("Emeralds", false);
    public final BooleanProperty emeraldsTracers = new BooleanProperty("Emeralds Tracers", false);
    public final BooleanProperty spawners = new BooleanProperty("Spawners", false);
    public final BooleanProperty spawnerTracers = new BooleanProperty("Spawners Tracers", false);
    public final BooleanProperty canes = new BooleanProperty("Canes", false);
    public final BooleanProperty canesTracers = new BooleanProperty("Canes Tracers", false);
    public final BooleanProperty warts = new BooleanProperty("Warts", false);
    public final BooleanProperty wartsTracers = new BooleanProperty("Warts Tracers", false);

    public Xray() {
        super("Xray", false);
    }

    private void renderOreHighlight(BlockPos blockPos, int blockId, Vec3 viewVector) {
        if (mc.thePlayer.getDistance(blockPos.getX(), blockPos.getY(), blockPos.getZ()) <= this.range.getValue().doubleValue()) {
            Color color = this.getOreColor(blockId);
            RenderUtil.drawBlockBoundingBox(blockPos, 1.0, color.getRed(), color.getGreen(), color.getBlue(), RenderUtil.opacityToAlpha(this.opacity.getValue().floatValue()), 1.5F);
            if (this.shouldDrawTracer(blockId)) {
                RenderUtil.drawLine3D(
                        viewVector,
                        (double) blockPos.getX() + 0.5,
                        (double) blockPos.getY() + 0.5,
                        (double) blockPos.getZ() + 0.5,
                        (float) color.getRed() / 255.0F,
                        (float) color.getGreen() / 255.0F,
                        (float) color.getBlue() / 255.0F,
                        this.opacity.getValue().floatValue(),
                        1.5F
                );
            }
        }
    }

    private Color getOreColor(int blockId) {
        switch (blockId) {
            case 14:
                return new Color(16777045);
            case 15:
                return new Color(16777215);
            case 16:
                return new Color(0);
            case 21:
                return new Color(5592575);
            case 52:
                return new Color(16733695);
            case 56:
                return new Color(5636095);
            case 73:
            case 74:
                return new Color(16733525);
            case 83:
                return new Color(11206570);
            case 115:
                return new Color(11141120);
            case 129:
                return new Color(5635925);
            default:
                return new Color(-1);
        }
    }

    private boolean shouldDrawTracer(int blockId) {
        switch (blockId) {
            case 14:
                return this.goldTracers.getValue();
            case 15:
                return this.ironTracers.getValue();
            case 16:
                return this.coalTracers.getValue();
            case 21:
                return this.lapisTracers.getValue();
            case 52:
                return this.spawnerTracers.getValue();
            case 56:
                return this.diamondTracers.getValue();
            case 73:
            case 74:
                return this.redStoneTracers.getValue();
            case 83:
                return this.canesTracers.getValue();
            case 115:
                return this.wartsTracers.getValue();
            case 129:
                return this.emeraldsTracers.getValue();
            default:
                return false;
        }
    }

    private boolean isValidCaveBlock(BlockPos pos) {
        if (mc.theWorld.isBlockLoaded(pos, false)) {
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            return block instanceof BlockMobSpawner || !block.isFullBlock() || !block.getMaterial().isOpaque() || block.canProvidePower();
        } else {
            return false;
        }
    }

    public boolean shouldRenderSide(int blockId) {
        return xrayBlocks.contains(blockId);
    }

    public boolean isXrayBlock(int blockId) {
        switch (blockId) {
            case 14:
                return this.gold.getValue();
            case 15:
                return this.iron.getValue();
            case 16:
                return this.coal.getValue();
            case 21:
                return this.lapis.getValue();
            case 52:
                return this.spawners.getValue();
            case 56:
                return this.diamonds.getValue();
            case 73:
            case 74:
                return this.redstone.getValue();
            case 83:
                return this.canes.getValue();
            case 115:
                return this.warts.getValue();
            case 129:
                return this.emeralds.getValue();
            default:
                return false;
        }
    }

    public boolean checkBlock(BlockPos blockPos) {
        if (!this.cavesOnly.getValue()) {
            return true;
        } else {
            if (this.caveRadius.getValue() >= 2) {
                for (Vec3i vec3i : caveOffsetsLarge) {
                    if (this.isValidCaveBlock(blockPos.add(vec3i))) {
                        return true;
                    }
                }
            } else {
                for (Vec3i vec3i : caveOffsetsSmall) {
                    if (this.isValidCaveBlock(blockPos.add(vec3i))) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            Vec3 vec3;
            if (mc.gameSettings.thirdPersonView == 0) {
                vec3 = new Vec3(0.0, 0.0, 1.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationPitch,
                                                        mc.getRenderViewEntity().prevRotationPitch,
                                                        mc.timer.renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.getRenderViewEntity().rotationYaw,
                                                        mc.getRenderViewEntity().prevRotationYaw,
                                                        mc.timer.renderPartialTicks
                                                )
                                        )
                                )
                        );
            } else {
                vec3 = new Vec3(0.0, 0.0, 0.0)
                        .rotatePitch(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(
                                                        mc.thePlayer.cameraPitch, mc.thePlayer.prevCameraPitch, mc.timer.renderPartialTicks
                                                )
                                        )
                                )
                        )
                        .rotateYaw(
                                (float) (
                                        -Math.toRadians(
                                                RenderUtil.lerpFloat(mc.thePlayer.cameraYaw, mc.thePlayer.prevCameraYaw, mc.timer.renderPartialTicks)
                                        )
                                )
                        );
            }
            vec3 = new Vec3(vec3.xCoord, vec3.yCoord + (double) mc.getRenderViewEntity().getEyeHeight(), vec3.zCoord);
            RenderUtil.enableRenderState();
            for (BlockPos blockPos : this.trackedBlocks) {
                if (this.pendingBlocks.contains(blockPos)) {
                    this.trackedBlocks.remove(blockPos);
                } else {
                    int id = Block.getIdFromBlock(mc.theWorld.getBlockState(blockPos).getBlock());
                    if (this.isXrayBlock(id)) {
                        this.renderOreHighlight(blockPos, id, vec3);
                    } else {
                        this.trackedBlocks.remove(blockPos);
                    }
                }
            }
            for (BlockPos blockPos : this.pendingBlocks) {
                int id = Block.getIdFromBlock(mc.theWorld.getBlockState(blockPos).getBlock());
                if (this.isXrayBlock(id)) {
                    this.renderOreHighlight(blockPos, id, vec3);
                } else {
                    this.pendingBlocks.remove(blockPos);
                }
            }
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S22PacketMultiBlockChange) {
                for (BlockUpdateData blockUpdateData : ((S22PacketMultiBlockChange) event.getPacket()).getChangedBlocks()) {
                    if (this.isXrayBlock(Block.getIdFromBlock(blockUpdateData.getBlockState().getBlock()))) {
                        this.pendingBlocks.add(new BlockPos(blockUpdateData.getPos()));
                    }
                }
            } else if (event.getPacket() instanceof S23PacketBlockChange packet) {
                if (this.isXrayBlock(Block.getIdFromBlock(packet.getBlockState().getBlock()))) {
                    this.pendingBlocks.add(new BlockPos(packet.getBlockPosition()));
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.trackedBlocks.clear();
        this.pendingBlocks.clear();
    }

    @Override
    public void onEnabled() {
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    @Override
    public void onDisabled() {
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    @Override
    public void verifyValue(String mode) {
        this.trackedBlocks.clear();
        this.pendingBlocks.clear();
        if (this.isEnabled() && mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }
}
