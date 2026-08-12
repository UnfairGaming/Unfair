package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;

/**
 * Base class for blocks whose real state must be preserved before ViaBackwards
 * replaces it with a legacy fallback.
 */
public abstract class ModernBlock extends Block
{
    protected ModernBlock(Material materialIn)
    {
        super(materialIn);
    }

    protected ModernBlock(Material materialIn, MapColor mapColorIn)
    {
        super(materialIn, mapColorIn);
    }

    /** First inclusive 1.14 block-state ID handled by this block. */
    public abstract int getViaStateIdMin();

    /** Last inclusive 1.14 block-state ID handled by this block. */
    public abstract int getViaStateIdMax();

    public final boolean handlesViaStateId(int stateId)
    {
        return stateId >= this.getViaStateIdMin() && stateId <= this.getViaStateIdMax();
    }

    /** Converts a matching 1.14 block-state ID into this client's local state. */
    public abstract IBlockState getStateFromViaStateId(int stateId);

    /** Called after the tracker installs a decoded state into the client world. */
    public void onModernStateApplied(BlockPos pos, IBlockState state)
    {
    }
}
