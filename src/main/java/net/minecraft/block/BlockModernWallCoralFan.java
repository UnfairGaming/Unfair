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

public class BlockModernWallCoralFan extends BlockModernFacingShape {
    public BlockModernWallCoralFan(int firstState) {
        super(Material.coral, ProtocolVersion.v1_14, firstState, firstState + 7);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        switch (state.getValue(FACING)) {
            case NORTH:
                return box(pos, 0, 0, 11, 16, 16, 16);
            case SOUTH:
                return box(pos, 0, 0, 0, 16, 16, 5);
            case WEST:
                return box(pos, 11, 0, 0, 16, 16, 16);
            default:
                return box(pos, 0, 0, 0, 5, 16, 16);
        }
    }

    private static AxisAlignedBB box(BlockPos pos, double minX, double minY, double minZ,
                                     double maxX, double maxY, double maxZ) {
        return new AxisAlignedBB(pos.getX() + minX / 16.0D, pos.getY() + minY / 16.0D,
                pos.getZ() + minZ / 16.0D, pos.getX() + maxX / 16.0D,
                pos.getY() + maxY / 16.0D, pos.getZ() + maxZ / 16.0D);
    }
}
