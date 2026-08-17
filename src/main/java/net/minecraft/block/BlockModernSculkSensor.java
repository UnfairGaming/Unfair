package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.IStringSerializable;

public class BlockModernSculkSensor extends BlockModernShape {
    public static final PropertyEnum<Phase> PHASE = PropertyEnum.create("sculk_sensor_phase", Phase.class);

    public BlockModernSculkSensor(int firstState) {
        super(Material.rock, ProtocolVersion.v1_17, firstState, firstState + 95,
                new double[]{0, 0, 0, 16, 8, 16});
        setDefaultState(blockState.getBaseState().withProperty(PHASE, Phase.INACTIVE));
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, new IProperty[]{PHASE});
    }

    @Override
    public IBlockState getStateFromViaStateId(int id) {
        // Registry order is power, phase, waterlogged; only phase affects this client model.
        return getDefaultState().withProperty(PHASE, Phase.values()[((id - firstState) / 2) % 3]);
    }

    public enum Phase implements IStringSerializable {
        INACTIVE, ACTIVE, COOLDOWN;

        public String getName() {
            return name().toLowerCase();
        }
    }
}
