package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

public class BlockModernAmethyst extends BlockModernShape {
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    private static final EnumFacing[] VIA_FACINGS = {
            EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH,
            EnumFacing.WEST, EnumFacing.UP, EnumFacing.DOWN
    };

    private final int first;
    private final int length;
    private final int inset;

    public BlockModernAmethyst(int first, int length, int inset) {
        super(Material.glass, ProtocolVersion.v1_17, first, first + 11);
        this.first = first;
        this.length = length;
        this.inset = inset;
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.UP));
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, new IProperty[]{FACING});
    }

    @Override
    public IBlockState getStateFromViaStateId(int id) {
        // In 1.17 waterlogged is the fastest-changing property, so each facing owns two IDs.
        return getDefaultState().withProperty(FACING, VIA_FACINGS[(id - first) / 2]);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        EnumFacing facing = state.getValue(FACING);
        double min = inset / 16.0D;
        double max = (16 - inset) / 16.0D;
        double extent = length / 16.0D;
        double minX = min, minY = min, minZ = min;
        double maxX = max, maxY = max, maxZ = max;

        switch (facing) {
            case DOWN:
                minY = 1.0D - extent;
                maxY = 1.0D;
                break;
            case NORTH:
                minZ = 1.0D - extent;
                maxZ = 1.0D;
                break;
            case SOUTH:
                minZ = 0.0D;
                maxZ = extent;
                break;
            case EAST:
                minX = 0.0D;
                maxX = extent;
                break;
            case WEST:
                minX = 1.0D - extent;
                maxX = 1.0D;
                break;
            default:
                minY = 0.0D;
                maxY = extent;
        }

        return new AxisAlignedBB(pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ);
    }
}
