package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;

public class BlockModernCandleCake extends BlockModernShape {
    public static final PropertyBool LIT = PropertyBool.create("lit");

    public BlockModernCandleCake(int firstState) {
        super(Material.cake, ProtocolVersion.v1_17, firstState, firstState + 1,
                new double[]{1, 0, 1, 15, 8, 15});
        setDefaultState(blockState.getBaseState().withProperty(LIT, false));
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, new IProperty[]{LIT});
    }

    @Override
    public IBlockState getStateFromViaStateId(int id) {
        // Boolean state IDs are ordered true, false in the 1.17 registry.
        return getDefaultState().withProperty(LIT, id == firstState);
    }
}
