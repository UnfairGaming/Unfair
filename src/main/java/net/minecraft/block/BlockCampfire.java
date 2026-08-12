package net.minecraft.block;

import cn.unfair.util.via.CampfireBlockTracker;
import cn.unfair.util.via.ViaProtocol;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

public class BlockCampfire extends ModernBlockDirectional
{
    public static final PropertyBool LIT = PropertyBool.create("lit");
    private static final EnumFacing[] VIA_FACINGS = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};
    private final boolean soul;

    protected BlockCampfire(boolean soul)
    {
        super(Material.wood);
        this.soul = soul;
        this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.SOUTH).withProperty(LIT, Boolean.TRUE));
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.4375F, 1.0F);
        this.setLightOpacity(0);
        this.useNeighborBrightness = true;
    }

    public void addCollisionBoxesToList(World worldIn, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> list, Entity collidingEntity)
    {
        AxisAlignedBB box = this.getCampfireCollisionBox(pos, state);

        if (box != null && box.intersectsWith(mask))
        {
            list.add(box);
        }
    }

    public AxisAlignedBB getCollisionBoundingBox(World worldIn, BlockPos pos, IBlockState state)
    {
        return this.getCampfireCollisionBox(pos, state);
    }

    private AxisAlignedBB getCampfireCollisionBox(BlockPos pos, IBlockState state)
    {
        if (ViaProtocol.olderThanOrEqualsTo1_13_2())
        {
            if (state.getValue(LIT))
            {
                return null;
            }

            return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 0.5D, pos.getZ() + 1.0D);
        }

        return new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D, pos.getY() + 0.4375D, pos.getZ() + 1.0D);
    }

    public void setBlockBoundsBasedOnState(IBlockAccess worldIn, BlockPos pos)
    {
        IBlockState state = worldIn.getBlockState(pos);

        if (state.getBlock() == this && ViaProtocol.olderThanOrEqualsTo1_13_2() && !state.getValue(LIT))
        {
            this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.5F, 1.0F);
            return;
        }

        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.4375F, 1.0F);
    }

    public void setBlockBoundsForItemRender()
    {
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 0.4375F, 1.0F);
    }

    public boolean isOpaqueCube()
    {
        return false;
    }

    public boolean isFullCube()
    {
        return false;
    }

    public IBlockState onBlockPlaced(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer)
    {
        return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing()).withProperty(LIT, Boolean.TRUE);
    }

    public EnumWorldBlockLayer getBlockLayer()
    {
        return EnumWorldBlockLayer.CUTOUT;
    }

    public Item getItemDropped(IBlockState state, Random rand, int fortune)
    {
        return Item.getItemFromBlock(this);
    }

    public Item getItem(World worldIn, BlockPos pos)
    {
        return Item.getItemFromBlock(this);
    }

    public IBlockState getStateFromMeta(int meta)
    {
        return this.getDefaultState()
                .withProperty(FACING, EnumFacing.getHorizontal(meta & 3))
                .withProperty(LIT, (meta & 4) == 0);
    }

    public int getMetaFromState(IBlockState state)
    {
        int meta = state.getValue(FACING).getHorizontalIndex();
        return state.getValue(LIT) ? meta : meta | 4;
    }

    protected BlockState createBlockState()
    {
        return new BlockState(this, new IProperty[] {FACING, LIT});
    }

    public int getViaStateIdMin()
    {
        return this.soul ? 1 : 11216;
    }

    public int getViaStateIdMax()
    {
        return this.soul ? 0 : 11247;
    }

    public IBlockState getStateFromViaStateId(int stateId)
    {
        int data = stateId - this.getViaStateIdMin();
        return this.getDefaultState()
                .withProperty(FACING, VIA_FACINGS[data >> 3])
                .withProperty(LIT, (data & 8) == 0);
    }

    public void onModernStateApplied(BlockPos pos, IBlockState state)
    {
        CampfireBlockTracker.mark(pos, state);
    }
}
