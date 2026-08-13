package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;

public class BlockEndRod extends ModernBlock
{
    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    protected BlockEndRod()
    {
        super(Material.wood);
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    public boolean isOpaqueCube() { return false; }
    public boolean isFullCube() { return false; }
    public void setBlockBoundsBasedOnState(IBlockAccess world, BlockPos pos) { setBounds(world.getBlockState(pos).getValue(FACING)); }
    private void setBounds(EnumFacing facing)
    {
        if (facing.getAxis() == EnumFacing.Axis.Y) setBlockBounds(0.375F, 0.0F, 0.375F, 0.625F, 1.0F, 0.625F);
        else if (facing.getAxis() == EnumFacing.Axis.X) setBlockBounds(0.0F, 0.375F, 0.375F, 1.0F, 0.625F, 0.625F);
        else setBlockBounds(0.375F, 0.375F, 0.0F, 0.625F, 0.625F, 1.0F);
    }
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) { setBounds(state.getValue(FACING)); return super.getCollisionBoundingBox(world, pos, state); }
    public int getViaStateIdMin() { return 7997; }
    public int getViaStateIdMax() { return 8002; }
    private static final EnumFacing[] VIA_FACING = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.UP, EnumFacing.DOWN};
    private static final EnumFacing[] LEGACY_FACING = {EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};
    public boolean handlesViaState(ProtocolVersion protocol, int stateId) { return protocol.equals(ProtocolVersion.v1_9) ? stateId >= 3168 && stateId <= 3173 : super.handlesViaState(protocol, stateId); }
    public IBlockState getStateFromViaState(ProtocolVersion protocol, int stateId) { return protocol.equals(ProtocolVersion.v1_9) ? getDefaultState().withProperty(FACING, LEGACY_FACING[stateId - 3168]) : getStateFromViaStateId(stateId); }
    public IBlockState getStateFromViaStateId(int stateId) { return getDefaultState().withProperty(FACING, VIA_FACING[stateId - 7997]); }
    public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, meta >= 0 && meta < VIA_FACING.length ? VIA_FACING[meta] : EnumFacing.UP); }
    public int getMetaFromState(IBlockState state)
    {
        EnumFacing facing = state.getValue(FACING);
        for (int meta = 0; meta < VIA_FACING.length; meta++)
        {
            if (VIA_FACING[meta] == facing) return meta;
        }
        return 4;
    }
    protected BlockState createBlockState() { return new BlockState(this, new IProperty[] {FACING}); }
}
