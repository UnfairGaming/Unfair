package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;

public class BlockModernFacingShape extends BlockModernShape {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    private static final EnumFacing[] STATE_ORDER = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    public BlockModernFacingShape(Material material, ProtocolVersion protocol, int firstState, int lastState, double[]... boxes) {
        super(material, protocol, firstState, lastState, boxes);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    public IBlockState getStateFromViaStateId(int id) {
        return getDefaultState().withProperty(FACING, STATE_ORDER[(id - firstState) % 4]);
    }

    protected BlockState createBlockState() {
        return new BlockState(this, FACING);
    }
}
