package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

import java.util.List;

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

    /** Protocol layer where this block still has its original state ID. */
    public ProtocolVersion getViaStateProtocol()
    {
        return ProtocolVersion.v1_14;
    }

    public final boolean handlesViaStateId(int stateId)
    {
        return stateId >= this.getViaStateIdMin() && stateId <= this.getViaStateIdMax();
    }

    /**
     * Modern-only blocks still have to fit through the 1.8 block registry's
     * four-bit metadata table. Use the block state's stable valid-state order
     * as the local encoding unless a block needs an explicit legacy layout.
     */
    @Override
    public IBlockState getStateFromMeta(int meta)
    {
        List<IBlockState> states = this.blockState.getValidStates();
        return meta >= 0 && meta < states.size() ? states.get(meta) : this.getDefaultState();
    }

    @Override
    public int getMetaFromState(IBlockState state)
    {
        List<IBlockState> states = this.blockState.getValidStates();

        if (states.size() > 16)
        {
            throw new IllegalStateException("Modern block " + state.getBlock()
                    + " has " + states.size() + " local states; the 1.8 registry supports at most 16");
        }

        int meta = states.indexOf(state);
        if (meta < 0)
        {
            throw new IllegalArgumentException("State " + state + " does not belong to " + this);
        }

        return meta;
    }

    /** Converts a matching 1.14 block-state ID into this client's local state. */
    public abstract IBlockState getStateFromViaStateId(int stateId);

    /** Called after the tracker installs a decoded state into the client world. */
    public void onModernStateApplied(BlockPos pos, IBlockState state)
    {
    }
}
