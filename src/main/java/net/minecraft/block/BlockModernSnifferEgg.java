package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;

public class BlockModernSnifferEgg extends BlockModernShape {
    public static final PropertyInteger HATCH = PropertyInteger.create("hatch", 0, 2);
    private final int first;

    public BlockModernSnifferEgg(int first) {
        super(Material.dragonEgg, ProtocolVersion.v1_20, first, first + 2, new double[]{1, 0, 2, 15, 16, 14});
        this.first = first;
        setDefaultState(blockState.getBaseState().withProperty(HATCH, 0));
    }

    protected BlockState createBlockState() {
        return new BlockState(this, HATCH);
    }

    public IBlockState getStateFromViaStateId(int id) {
        return getDefaultState().withProperty(HATCH, id - first);
    }
}
