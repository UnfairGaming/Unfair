package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;

public class BlockModernShrieker extends BlockModernShape {
    public static final PropertyBool CAN_SUMMON = PropertyBool.create("can_summon");
    private final int first;

    public BlockModernShrieker(int first) {
        super(Material.rock, ProtocolVersion.v1_19, first, first + 7, new double[]{0, 0, 0, 16, 8, 16});
        this.first = first;
        setDefaultState(blockState.getBaseState().withProperty(CAN_SUMMON, false));
    }

    protected BlockState createBlockState() {
        return new BlockState(this, new IProperty[]{CAN_SUMMON});
    }

    public IBlockState getStateFromViaStateId(int id) {
        return getDefaultState().withProperty(CAN_SUMMON, ((id - first) / 4) != 0);
    }
}
