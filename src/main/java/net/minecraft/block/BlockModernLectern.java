package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;

public class BlockModernLectern extends BlockModernFacingShape {
    public static final PropertyBool HAS_BOOK = PropertyBool.create("has_book");
    public static final PropertyBool POWERED = PropertyBool.create("powered");
    public BlockModernLectern(int firstState, int lastState) { super(Material.wood, ProtocolVersion.v1_14, firstState, lastState, new double[]{0,0,0,16,2,16}, new double[]{4,2,4,12,14,12}); setDefaultState(blockState.getBaseState().withProperty(FACING, net.minecraft.util.EnumFacing.NORTH).withProperty(HAS_BOOK,false).withProperty(POWERED,false)); }
    protected BlockState createBlockState() { return new BlockState(this, new IProperty[]{FACING,HAS_BOOK,POWERED}); }
    public IBlockState getStateFromViaStateId(int id) { int v=id-firstState; return getDefaultState().withProperty(FACING, new net.minecraft.util.EnumFacing[]{net.minecraft.util.EnumFacing.NORTH,net.minecraft.util.EnumFacing.SOUTH,net.minecraft.util.EnumFacing.WEST,net.minecraft.util.EnumFacing.EAST}[(v/4)%4]).withProperty(HAS_BOOK,(v&1)!=0).withProperty(POWERED,(v&2)!=0); }
}
