package net.minecraft.client.renderer;

import net.minecraft.block.BlockBubbleColumn;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Exposes a bubble column's still-water fluid state to the legacy fluid renderer.
 */
public final class BubbleColumnFluidBlockAccess implements IBlockAccess {
    private static final IBlockState STILL_WATER = Blocks.water.getDefaultState().withProperty(BlockLiquid.LEVEL, 0);
    private final IBlockAccess delegate;

    public BubbleColumnFluidBlockAccess(IBlockAccess delegate) {
        this.delegate = delegate;
    }

    public TileEntity getTileEntity(BlockPos pos) {
        return this.delegate.getTileEntity(pos);
    }

    public int getCombinedLight(BlockPos pos, int lightValue) {
        return this.delegate.getCombinedLight(pos, lightValue);
    }

    public IBlockState getBlockState(BlockPos pos) {
        IBlockState state = this.delegate.getBlockState(pos);
        return state.getBlock() instanceof BlockBubbleColumn ? STILL_WATER : state;
    }

    public boolean isAirBlock(BlockPos pos) {
        return this.delegate.isAirBlock(pos);
    }

    public BiomeGenBase getBiomeGenForCoords(BlockPos pos) {
        return this.delegate.getBiomeGenForCoords(pos);
    }

    public boolean extendedLevelsInChunkCache() {
        return this.delegate.extendedLevelsInChunkCache();
    }

    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return this.delegate.getStrongPower(pos, direction);
    }

    public WorldType getWorldType() {
        return this.delegate.getWorldType();
    }
}
