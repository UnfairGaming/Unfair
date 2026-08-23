package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;

public class BlockModernComposter extends BlockModernShape {
    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 0, 8);

    public BlockModernComposter(int firstState, int lastState) {
        super(Material.wood, ProtocolVersion.v1_14, firstState, lastState, new double[]{0, 0, 0, 16, 2, 16}, new double[]{0, 2, 0, 2, 16, 16}, new double[]{14, 2, 0, 16, 16, 16}, new double[]{0, 2, 0, 16, 16, 2}, new double[]{0, 2, 14, 16, 16, 16});
        setDefaultState(blockState.getBaseState().withProperty(LEVEL, 0));
    }

    protected BlockState createBlockState() {
        return new BlockState(this, LEVEL);
    }

    public IBlockState getStateFromViaStateId(int id) {
        return getDefaultState().withProperty(LEVEL, id - firstState);
    }
}
