package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.List;

public class BlockChorusPlant extends ModernBlock {
    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool WEST = PropertyBool.create("west");
    public static final PropertyBool UP = PropertyBool.create("up");
    public static final PropertyBool DOWN = PropertyBool.create("down");
    private static final EnumFacing[] FACES = {EnumFacing.DOWN, EnumFacing.EAST, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.UP, EnumFacing.WEST};

    protected BlockChorusPlant() {
        super(Material.wood);
        this.setDefaultState(this.blockState.getBaseState().withProperty(NORTH, false).withProperty(EAST, false).withProperty(SOUTH, false).withProperty(WEST, false).withProperty(UP, false).withProperty(DOWN, false));
    }

    private static void addBox(BlockPos p, AxisAlignedBB mask, List<AxisAlignedBB> list, double a, double b, double c, double d, double e, double f) {
        AxisAlignedBB box = new AxisAlignedBB(p.getX() + a / 16, p.getY() + b / 16, p.getZ() + c / 16, p.getX() + d / 16, p.getY() + e / 16, p.getZ() + f / 16);
        if (box.intersectsWith(mask)) list.add(box);
    }

    public boolean isOpaqueCube() {
        return false;
    }

    public boolean isFullCube() {
        return false;
    }

    public void addCollisionBoxesToList(World world, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> list, net.minecraft.entity.Entity entity) {
        if (state == getDefaultState()) {
            state = state.withProperty(NORTH, canConnect(world, pos, EnumFacing.NORTH)).withProperty(EAST, canConnect(world, pos, EnumFacing.EAST)).withProperty(SOUTH, canConnect(world, pos, EnumFacing.SOUTH)).withProperty(WEST, canConnect(world, pos, EnumFacing.WEST)).withProperty(UP, canConnect(world, pos, EnumFacing.UP)).withProperty(DOWN, canConnect(world, pos, EnumFacing.DOWN));
        }
        addBox(pos, mask, list, 3, 3, 3, 13, 13, 13);
        if (state.getValue(NORTH)) addBox(pos, mask, list, 3, 3, 0, 13, 13, 8);
        if (state.getValue(SOUTH)) addBox(pos, mask, list, 3, 3, 8, 13, 13, 16);
        if (state.getValue(WEST)) addBox(pos, mask, list, 0, 3, 3, 8, 13, 13);
        if (state.getValue(EAST)) addBox(pos, mask, list, 8, 3, 3, 16, 13, 13);
        if (state.getValue(DOWN)) addBox(pos, mask, list, 3, 0, 3, 13, 8, 13);
        if (state.getValue(UP)) addBox(pos, mask, list, 3, 8, 3, 13, 16, 13);
    }

    private boolean canConnect(World world, BlockPos pos, EnumFacing face) {
        Block b = world.getBlockState(pos.offset(face)).getBlock();
        return b instanceof BlockChorusPlant || b instanceof BlockChorusFlower || (face == EnumFacing.DOWN && b == net.minecraft.init.Blocks.end_stone);
    }

    public int getViaStateIdMin() {
        return 8003;
    }

    public int getViaStateIdMax() {
        return 8066;
    }

    public boolean handlesViaState(ProtocolVersion protocol, int stateId) {
        return protocol.equals(ProtocolVersion.v1_9) ? stateId == 3184 : super.handlesViaState(protocol, stateId);
    }

    public IBlockState getStateFromViaState(ProtocolVersion protocol, int stateId) {
        return getDefaultState();
    }

    public IBlockState getStateFromViaStateId(int id) {
        int v = id - 8003;
        IBlockState s = getDefaultState();
        PropertyBool[] p = {DOWN, EAST, NORTH, SOUTH, UP, WEST};
        for (int i = 0; i < p.length; i++) s = s.withProperty(p[i], (v & (1 << i)) != 0);
        return s;
    }

    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    protected BlockState createBlockState() {
        return new BlockState(this, NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }
}
