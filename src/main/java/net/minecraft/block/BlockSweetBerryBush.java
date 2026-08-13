package net.minecraft.block;

import cn.unfair.util.via.ViaProtocol;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockSweetBerryBush extends ModernBlock
{
    public static final PropertyInteger AGE = PropertyInteger.create("age", 0, 3);

    protected BlockSweetBerryBush()
    {
        super(Material.plants);
        this.setDefaultState(this.blockState.getBaseState().withProperty(AGE, 0));
    }

    public AxisAlignedBB getCollisionBoundingBox(World worldIn, BlockPos pos, IBlockState state) { return null; }
    public boolean isOpaqueCube() { return false; }
    public boolean isFullCube() { return false; }
    public EnumWorldBlockLayer getBlockLayer() { return EnumWorldBlockLayer.CUTOUT; }

    public void setBlockBoundsBasedOnState(IBlockAccess worldIn, BlockPos pos)
    {
        int age = worldIn.getBlockState(pos).getValue(AGE);
        if (age == 0) this.setBlockBounds(3.0F / 16.0F, 0.0F, 3.0F / 16.0F, 13.0F / 16.0F, 0.5F, 13.0F / 16.0F);
        else if (age < 3) this.setBlockBounds(1.0F / 16.0F, 0.0F, 1.0F / 16.0F, 15.0F / 16.0F, 1.0F, 15.0F / 16.0F);
        else this.setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    public void onEntityCollidedWithBlock(World worldIn, BlockPos pos, IBlockState state, Entity entityIn)
    {
        if (!ViaProtocol.newerThanOrEqualTo1_14() || !(entityIn instanceof EntityLivingBase)) return;
        if (entityIn instanceof EntityPlayer && ((EntityPlayer) entityIn).capabilities.isFlying) return;
        entityIn.viaforge$slowMovement(0.8F, 0.75D, 0.8F);
    }

    public int getViaStateIdMin() { return 11248; }
    public int getViaStateIdMax() { return 11251; }
    public IBlockState getStateFromViaStateId(int stateId)
    {
        return this.getDefaultState().withProperty(AGE, stateId - 11248);
    }
    protected BlockState createBlockState() { return new BlockState(this, new IProperty[] {AGE}); }
}
