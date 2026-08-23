package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

public class BlockChorusFlower extends ModernBlock {
    public static final PropertyInteger AGE = PropertyInteger.create("age", 0, 5);

    protected BlockChorusFlower() {
        super(Material.wood);
        this.setDefaultState(this.blockState.getBaseState().withProperty(AGE, 0));
    }

    public boolean isOpaqueCube() {
        return false;
    }

    public boolean isFullCube() {
        return false;
    }

    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        return new AxisAlignedBB(pos.getX() + 0.25, pos.getY() + 0.25, pos.getZ() + 0.25, pos.getX() + 0.75, pos.getY() + 0.75, pos.getZ() + 0.75);
    }

    public int getViaStateIdMin() {
        return 8067;
    }

    public int getViaStateIdMax() {
        return 8072;
    }

    public IBlockState getStateFromViaStateId(int id) {
        return getDefaultState().withProperty(AGE, id - 8067);
    }

    protected BlockState createBlockState() {
        return new BlockState(this, AGE);
    }
}
