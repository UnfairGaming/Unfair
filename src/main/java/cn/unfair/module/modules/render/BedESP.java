package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.events.Render3DEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.render.RenderUtil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.BlockObsidian;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.awt.*;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class BedESP extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final CopyOnWriteArraySet<BlockPos> beds = new CopyOnWriteArraySet<>();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Default", "Full"});
    public final ModeProperty color = new ModeProperty("Color", 0, new String[]{"Custom", "Hud"});
    public final ColorProperty customColor;
    public final PercentProperty opacity;
    public final BooleanProperty outline;
    public final BooleanProperty obsidian;
    private int lastBedScanTick = -20;

    public BedESP() {
        super("BedESP", false, true);
        this.customColor = new ColorProperty("Custom Color", (int) 8085714755840333141L, () -> this.color.getValue() == 0);
        this.opacity = new PercentProperty("Opacity", 25);
        this.outline = new BooleanProperty("Outline", false);
        this.obsidian = new BooleanProperty("Obsidian", true);
    }

    private Color getColor() {
        switch (this.color.getValue()) {
            case 0:
                return new Color(this.customColor.getValue());
            case 1:
                Unfair.moduleManager.modules.get(HUD.class);
                return HUD.getColor(System.currentTimeMillis());
            default:
                return new Color(-1);
        }
    }

    private void drawObsidianBox(AxisAlignedBB axisAlignedBB) {
        if (this.outline.getValue()) {
            RenderUtil.drawBoundingBox(axisAlignedBB, 170, 0, 170, 255, 1.5F);
        }
        RenderUtil.drawFilledBox(axisAlignedBB, 170, 0, 170);
    }

    private void drawObsidian(BlockPos blockPos) {
        if (this.outline.getValue()) {
            RenderUtil.drawBlockBoundingBox(blockPos, 1.0, 170, 0, 170, 255, 1.5F);
        }
        RenderUtil.drawBlockBox(
                blockPos, 1.0, 170, 0, 170
        );
    }

    public double getHeight() {
        return this.mode.getValue() == 1 ? 1.0 : 0.5625;
    }

    private void updateTrackedBeds() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            this.beds.clear();
            return;
        }
        if (mc.thePlayer.ticksExisted - this.lastBedScanTick < 20) {
            return;
        }

        this.lastBedScanTick = mc.thePlayer.ticksExisted;
        IChunkProvider chunkProvider = mc.theWorld.getChunkProvider();
        if (!(chunkProvider instanceof ChunkProviderClient)) {
            return;
        }

        Set<BlockPos> foundBeds = new HashSet<>();
        try {
            for (Chunk chunk : ((ChunkProviderClient) chunkProvider).getLoadedChunks()) {
                if (chunk == null || chunk.isEmpty()) {
                    continue;
                }
                for (ExtendedBlockStorage storage : chunk.getBlockStorageArray()) {
                    if (storage == null || storage.isEmpty()) {
                        continue;
                    }
                    int yBase = storage.getYLocation();
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                IBlockState state = storage.get(x, y, z);
                                if (state.getBlock() instanceof BlockBed && state.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                                    foundBeds.add(new BlockPos((chunk.xPosition << 4) + x, yBase + y, (chunk.zPosition << 4) + z));
                                }
                            }
                        }
                    }
                }
            }
        } catch (ConcurrentModificationException ignored) {
            return;
        }

        this.beds.clear();
        this.beds.addAll(foundBeds);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled()) {
            this.updateTrackedBeds();
            RenderUtil.enableRenderState();
            try {
                for (BlockPos blockPos : this.beds) {
                    IBlockState state = mc.theWorld.getBlockState(blockPos);
                if (state.getBlock() instanceof BlockBed && state.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                    BlockPos opposite = blockPos.offset(state.getValue(BlockBed.FACING).getOpposite());
                    IBlockState oppositeState = mc.theWorld.getBlockState(opposite);
                    if (oppositeState.getBlock() instanceof BlockBed && oppositeState.getValue(BlockBed.PART) == EnumPartType.FOOT) {
                        if (this.obsidian.getValue()) {
                            for (EnumFacing facing : Arrays.asList(EnumFacing.UP, EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST)) {
                                BlockPos offsetX = blockPos.offset(facing);
                                BlockPos offsetZ = opposite.offset(facing);
                                boolean xObsidian = mc.theWorld.getBlockState(offsetX).getBlock() instanceof BlockObsidian;
                                boolean zObsidian = mc.theWorld.getBlockState(offsetZ).getBlock() instanceof BlockObsidian;
                                if (xObsidian && zObsidian) {
                                    this.drawObsidianBox(
                                            new AxisAlignedBB(
                                                    Math.min(offsetX.getX(), offsetZ.getX()),
                                                    offsetX.getY(),
                                                    Math.min(offsetX.getZ(), offsetZ.getZ()),
                                                    Math.max((double) offsetX.getX() + 1.0, (double) offsetZ.getX() + 1.0),
                                                    (double) offsetX.getY() + 1.0,
                                                    Math.max((double) offsetX.getZ() + 1.0, (double) offsetZ.getZ() + 1.0)
                                            )
                                                    .offset(
                                                            -mc.getRenderManager().getRenderPosX(),
                                                            -mc.getRenderManager().getRenderPosY(),
                                                            -mc.getRenderManager().getRenderPosZ()
                                                    )
                                    );
                                } else if (xObsidian) {
                                    this.drawObsidian(offsetX);
                                } else if (zObsidian) {
                                    this.drawObsidian(offsetZ);
                                }
                            }
                        }
                        AxisAlignedBB aabb = new AxisAlignedBB(
                                Math.min(blockPos.getX(), opposite.getX()),
                                blockPos.getY(),
                                Math.min(blockPos.getZ(), opposite.getZ()),
                                Math.max((double) blockPos.getX() + 1.0, (double) opposite.getX() + 1.0),
                                (double) blockPos.getY() + this.getHeight(),
                                Math.max((double) blockPos.getZ() + 1.0, (double) opposite.getZ() + 1.0)
                        )
                                .offset(
                                        -mc.getRenderManager().getRenderPosX(),
                                        -mc.getRenderManager().getRenderPosY(),
                                        -mc.getRenderManager().getRenderPosZ()
                                );
                        Color color = this.getColor();
                        if (this.outline.getValue()) {
                            RenderUtil.drawBoundingBox(aabb, color.getRed(), color.getGreen(), color.getBlue(), 255, 1.5F);
                        }
                        RenderUtil.drawFilledBox(
                                aabb,
                                color.getRed(),
                                color.getGreen(),
                                color.getBlue()
                        );
                    }
                } else {
                    this.beds.remove(blockPos);
                }
                }
            } finally {
                RenderUtil.disableRenderState();
            }
        }
    }

    @Override
    public void onEnabled() {
        this.lastBedScanTick = -20;
        if (mc.renderGlobal != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    @Override
    public void onDisabled() {
        this.beds.clear();
    }
}
