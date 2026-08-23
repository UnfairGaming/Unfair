package net.minecraft.block;

import cn.unfair.util.via.ViaBackwardsItemModels;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockScaffolding extends ModernBlock {
    public static final PropertyBool BOTTOM = PropertyBool.create("bottom");
    public static final PropertyInteger DISTANCE = PropertyInteger.create("distance", 0, 7);

    protected BlockScaffolding() {
        super(Material.wood);
        this.setDefaultState(this.blockState.getBaseState()
                .withProperty(BOTTOM, Boolean.FALSE)
                .withProperty(DISTANCE, 7));
    }

    private static void addBox(BlockPos pos, AxisAlignedBB mask, java.util.List<AxisAlignedBB> list,
                               double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        AxisAlignedBB box = new AxisAlignedBB(pos.getX() + minX / 16.0D, pos.getY() + minY / 16.0D,
                pos.getZ() + minZ / 16.0D, pos.getX() + maxX / 16.0D, pos.getY() + maxY / 16.0D,
                pos.getZ() + maxZ / 16.0D);
        if (box.intersectsWith(mask)) list.add(box);
    }

    public static int calculateDistance(IBlockAccess world, BlockPos pos) {
        IBlockState below = world.getBlockState(pos.down());
        int distance = 7;
        if (below.getBlock() instanceof BlockScaffolding) {
            distance = below.getValue(DISTANCE);
        } else if (World.doesBlockHaveSolidTopSurface(world, pos.down())) {
            return 0;
        }

        net.minecraft.util.EnumFacing[] horizontal = {
                net.minecraft.util.EnumFacing.NORTH, net.minecraft.util.EnumFacing.SOUTH,
                net.minecraft.util.EnumFacing.WEST, net.minecraft.util.EnumFacing.EAST
        };
        for (net.minecraft.util.EnumFacing facing : horizontal) {
            IBlockState neighbor = world.getBlockState(pos.offset(facing));
            if (neighbor.getBlock() instanceof BlockScaffolding) {
                distance = Math.min(distance, neighbor.getValue(DISTANCE) + 1);
                if (distance == 1) break;
            }
        }
        return distance;
    }

    public boolean isOpaqueCube() {
        return false;
    }

    public boolean isFullCube() {
        return false;
    }

    public EnumWorldBlockLayer getBlockLayer() {
        return EnumWorldBlockLayer.CUTOUT;
    }

    public void addCollisionBoxesToList(World worldIn, BlockPos pos, IBlockState state, AxisAlignedBB mask,
                                        java.util.List<AxisAlignedBB> list, Entity entityIn) {
        if (entityIn != null && entityIn.prevPosY > pos.getY() + 1.0D - 1.0E-5D && !entityIn.isSneaking()) {
            addBox(pos, mask, list, 0, 14, 0, 16, 16, 16);
            addBox(pos, mask, list, 0, 0, 0, 2, 16, 2);
            addBox(pos, mask, list, 14, 0, 0, 16, 16, 2);
            addBox(pos, mask, list, 0, 0, 14, 2, 16, 16);
            addBox(pos, mask, list, 14, 0, 14, 16, 16, 16);
        } else if (state.getValue(DISTANCE) != 0 && state.getValue(BOTTOM) && entityIn != null
                && entityIn.prevPosY > pos.getY() - 1.0E-5D) {
            addBox(pos, mask, list, 0, 0, 0, 16, 2, 16);
        }
    }

    public void setBlockBoundsBasedOnState(IBlockAccess worldIn, BlockPos pos) {
        this.setBlockBounds(0, 0, 0, 1, 1, 1);
    }

    public java.util.List<AxisAlignedBB> getSelectedBoundingBoxes(World worldIn, BlockPos pos) {
        ItemStack held = Minecraft.getMinecraft().thePlayer == null ? null : Minecraft.getMinecraft().thePlayer.getHeldItem();
        if (held != null && (held.getItem() == Item.getItemFromBlock(Blocks.scaffolding)
                || "scaffolding".equals(ViaBackwardsItemModels.getModelName(held)))) {
            return java.util.Collections.singletonList(new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D));
        }

        java.util.List<AxisAlignedBB> boxes = new java.util.ArrayList<>();
        AxisAlignedBB mask = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
        addBox(pos, mask, boxes, 0, 14, 0, 16, 16, 16);
        addBox(pos, mask, boxes, 0, 0, 0, 2, 16, 2);
        addBox(pos, mask, boxes, 14, 0, 0, 16, 16, 2);
        addBox(pos, mask, boxes, 0, 0, 14, 2, 16, 16);
        addBox(pos, mask, boxes, 14, 0, 14, 16, 16, 16);
        if (worldIn.getBlockState(pos).getValue(BOTTOM)) {
            addBox(pos, mask, boxes, 0, 0, 0, 2, 2, 16);
            addBox(pos, mask, boxes, 14, 0, 0, 16, 2, 16);
            addBox(pos, mask, boxes, 0, 0, 14, 16, 2, 16);
            addBox(pos, mask, boxes, 0, 0, 0, 16, 2, 2);
        }
        return boxes;
    }

    public IBlockState onBlockPlaced(World worldIn, BlockPos pos, net.minecraft.util.EnumFacing facing,
                                     float hitX, float hitY, float hitZ, int meta,
                                     net.minecraft.entity.EntityLivingBase placer) {
        int distance = calculateDistance(worldIn, pos);
        return this.getDefaultState()
                .withProperty(DISTANCE, distance)
                .withProperty(BOTTOM, distance > 0 && worldIn.getBlockState(pos.down()).getBlock() != this);
    }

    public boolean canPlaceBlockAt(World worldIn, BlockPos pos) {
        return calculateDistance(worldIn, pos) < 7;
    }

    public int getViaStateIdMin() {
        return 11099;
    }

    public int getViaStateIdMax() {
        return 11130;
    }

    public IBlockState getStateFromViaStateId(int stateId) {
        int data = stateId - 11099;
        return this.getDefaultState()
                .withProperty(BOTTOM, data < 16)
                .withProperty(DISTANCE, data % 16 / 2);
    }

    public IBlockState getStateFromMeta(int meta) {
        return this.getDefaultState()
                .withProperty(BOTTOM, (meta & 8) == 0)
                .withProperty(DISTANCE, meta & 7);
    }

    public int getMetaFromState(IBlockState state) {
        return (state.getValue(BOTTOM) ? 0 : 8) | state.getValue(DISTANCE);
    }

    protected BlockState createBlockState() {
        return new BlockState(this, BOTTOM, DISTANCE);
    }
}
