package net.minecraft.block;

import cn.unfair.util.via.ModernBlockStateTracker;
import cn.unfair.util.via.ViaProtocol;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;
import java.util.Random;

public class BlockPane extends Block {
    public static final PropertyBool NORTH = PropertyBool.create("north");
    public static final PropertyBool EAST = PropertyBool.create("east");
    public static final PropertyBool SOUTH = PropertyBool.create("south");
    public static final PropertyBool WEST = PropertyBool.create("west");
    private final boolean canDrop;

    protected BlockPane(Material materialIn, boolean canDrop) {
        super(materialIn);
        this.setDefaultState(this.blockState.getBaseState().withProperty(NORTH, Boolean.FALSE).withProperty(EAST, Boolean.FALSE).withProperty(SOUTH, Boolean.FALSE).withProperty(WEST, Boolean.FALSE));
        this.canDrop = canDrop;
        this.setCreativeTab(CreativeTabs.tabDecorations);
    }

    /**
     * Get the actual Block state of this Block at the given position. This applies properties not visible in the
     * metadata, such as fence connections.
     */
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        return state.withProperty(NORTH, this.canPaneConnectTo(worldIn, pos, EnumFacing.NORTH)).withProperty(SOUTH, this.canPaneConnectTo(worldIn, pos, EnumFacing.SOUTH)).withProperty(WEST, this.canPaneConnectTo(worldIn, pos, EnumFacing.WEST)).withProperty(EAST, this.canPaneConnectTo(worldIn, pos, EnumFacing.EAST));
    }

    /**
     * Get the Item that this Block should drop when harvested.
     */
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return !this.canDrop ? null : super.getItemDropped(state, rand, fortune);
    }

    /**
     * Used to determine ambient occlusion and culling when rebuilding chunks for render
     */
    public boolean isOpaqueCube() {
        return false;
    }

    public boolean isFullCube() {
        return false;
    }

    public boolean shouldSideBeRendered(IBlockAccess worldIn, BlockPos pos, EnumFacing side) {
        return worldIn.getBlockState(pos).getBlock() != this && super.shouldSideBeRendered(worldIn, pos, side);
    }

    /**
     * Add all collision boxes of this Block to the list that intersect with the given mask.
     */
    public void addCollisionBoxesToList(World worldIn, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> list, Entity collidingEntity) {
        boolean flag = this.canPaneConnectTo(worldIn, pos, EnumFacing.NORTH);
        boolean flag1 = this.canPaneConnectTo(worldIn, pos, EnumFacing.SOUTH);
        boolean flag2 = this.canPaneConnectTo(worldIn, pos, EnumFacing.WEST);
        boolean flag3 = this.canPaneConnectTo(worldIn, pos, EnumFacing.EAST);

        if (ViaProtocol.newerThanOrEqualTo1_9()) {
            this.addPaneCollisionBox(worldIn, pos, state, mask, list, 0.4375F, 0.0F, 0.4375F, 0.5625F, 1.0F, 0.5625F);

            if (flag) {
                this.addPaneCollisionBox(worldIn, pos, state, mask, list, 0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 0.5625F);
            }

            if (flag1) {
                this.addPaneCollisionBox(worldIn, pos, state, mask, list, 0.4375F, 0.0F, 0.4375F, 0.5625F, 1.0F, 1.0F);
            }

            if (flag2) {
                this.addPaneCollisionBox(worldIn, pos, state, mask, list, 0.0F, 0.0F, 0.4375F, 0.5625F, 1.0F, 0.5625F);
            }

            if (flag3) {
                this.addPaneCollisionBox(worldIn, pos, state, mask, list, 0.4375F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F);
            }

            return;
        }

        if ((!flag2 || !flag3) && (flag2 || flag3 || flag || flag1)) {
            if (flag2) {
                this.setBlockBounds(0.0F, 0.0F, 0.4375F, 0.5F, 1.0F, 0.5625F);
                super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
            } else if (flag3) {
                this.setBlockBounds(0.5F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F);
                super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
            }
        } else {
            this.setBlockBounds(0.0F, 0.0F, 0.4375F, 1.0F, 1.0F, 0.5625F);
            super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
        }

        if ((!flag || !flag1) && (flag2 || flag3 || flag || flag1)) {
            if (flag) {
                this.setBlockBounds(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 0.5F);
                super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
            } else if (flag1) {
                this.setBlockBounds(0.4375F, 0.0F, 0.5F, 0.5625F, 1.0F, 1.0F);
                super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
            }
        } else {
            this.setBlockBounds(0.4375F, 0.0F, 0.0F, 0.5625F, 1.0F, 1.0F);
            super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
        }
    }

    /**
     * Sets the block's bounds for rendering it as an item
     */
    public void setBlockBoundsForItemRender() {
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    public void setBlockBoundsBasedOnState(IBlockAccess worldIn, BlockPos pos) {
        float f = 0.4375F;
        float f1 = 0.5625F;
        float f2 = 0.4375F;
        float f3 = 0.5625F;
        boolean flag = this.canPaneConnectTo(worldIn, pos, EnumFacing.NORTH);
        boolean flag1 = this.canPaneConnectTo(worldIn, pos, EnumFacing.SOUTH);
        boolean flag2 = this.canPaneConnectTo(worldIn, pos, EnumFacing.WEST);
        boolean flag3 = this.canPaneConnectTo(worldIn, pos, EnumFacing.EAST);

        if (ViaProtocol.newerThanOrEqualTo1_9()) {
            if (flag2) {
                f = 0.0F;
            }

            if (flag3) {
                f1 = 1.0F;
            }

            if (flag) {
                f2 = 0.0F;
            }

            if (flag1) {
                f3 = 1.0F;
            }

            this.setBlockBounds(f, 0.0F, f2, f1, 1.0F, f3);
            return;
        }

        if ((!flag2 || !flag3) && (flag2 || flag3 || flag || flag1)) {
            if (flag2) {
                f = 0.0F;
            } else if (flag3) {
                f1 = 1.0F;
            }
        } else {
            f = 0.0F;
            f1 = 1.0F;
        }

        if ((!flag || !flag1) && (flag2 || flag3 || flag || flag1)) {
            if (flag) {
                f2 = 0.0F;
            } else if (flag1) {
                f3 = 1.0F;
            }
        } else {
            f2 = 0.0F;
            f3 = 1.0F;
        }

        this.setBlockBounds(f, 0.0F, f2, f1, 1.0F, f3);
    }

    public final boolean canPaneConnectToBlock(Block blockIn) {
        return blockIn.isFullBlock() || blockIn == this || blockIn == Blocks.glass || blockIn == Blocks.stained_glass || blockIn == Blocks.stained_glass_pane || blockIn instanceof BlockPane;
    }

    private boolean canPaneConnectTo(IBlockAccess worldIn, BlockPos pos, EnumFacing direction) {
        if (ViaProtocol.newerThanOrEqualTo1_13()) {
            String value = ModernBlockStateTracker.getNativeProperty(pos, direction.getName());
            if (value != null) {
                return !"false".equals(value) && !"none".equals(value);
            }
        }

        BlockPos neighbor = pos.offset(direction);
        Block block = worldIn.getBlockState(neighbor).getBlock();

        if (!ViaProtocol.newerThanOrEqualTo1_9()) {
            return this.canPaneConnectToBlock(block);
        }

        if (block == this || block == Blocks.iron_bars || block instanceof BlockPane || block == Blocks.glass || block == Blocks.stained_glass) {
            return true;
        }

        if (block.getMaterial() == Material.leaves
                || block instanceof BlockTrapDoor
                || block == Blocks.farmland
                || block == Blocks.beacon
                || block == Blocks.cauldron
                || block == Blocks.glowstone
                || block == Blocks.sea_lantern
                || block == Blocks.ice
                || block == Blocks.packed_ice) {
            return false;
        }

        return block.isFullCube() && block.isBlockSolid(worldIn, neighbor, direction.getOpposite());
    }

    private void addPaneCollisionBox(World worldIn, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> list, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        this.setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
        super.addCollisionBoxesToList(worldIn, pos, state, mask, list, null);
    }

    protected boolean canSilkHarvest() {
        return true;
    }

    public EnumWorldBlockLayer getBlockLayer() {
        return this.getXrayTranslucentLayer(EnumWorldBlockLayer.CUTOUT_MIPPED);
    }

    /**
     * Convert the BlockState into the correct metadata value
     */
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    protected BlockState createBlockState() {
        return new BlockState(this, NORTH, EAST, WEST, SOUTH);
    }
}
