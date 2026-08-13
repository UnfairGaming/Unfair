package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;

public class BlockModernLantern extends BlockModernShape {
    public static final PropertyBool HANGING = PropertyBool.create("hanging");
    public BlockModernLantern(ProtocolVersion protocol, int firstState, int lastState) { super(Material.iron, protocol, firstState, lastState, new double[]{5,0,5,11,7,11}, new double[]{6,7,6,10,9,10}); setDefaultState(blockState.getBaseState().withProperty(HANGING, false)); }
    protected BlockState createBlockState() { return new BlockState(this, new IProperty[]{HANGING}); }
    public IBlockState getStateFromViaStateId(int id) { return getDefaultState().withProperty(HANGING, ((id - firstState) & 1) != 0); }
}
