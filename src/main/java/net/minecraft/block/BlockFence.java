package net.minecraft.block;

import cn.unfair.util.via.ModernBlockStateTracker;
import cn.unfair.util.via.ViaProtocol;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemLead;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.List;

public class BlockFence extends Block {
    /**
     * Whether this fence connects in the northern direction
     */
    public static final PropertyBool NORTH = PropertyBool.create("north");

    /**
     * Whether this fence connects in the eastern direction
     */
    public static final PropertyBool EAST = PropertyBool.create("east");

    /**
     * Whether this fence connects in the southern direction
     */
    public static final PropertyBool SOUTH = PropertyBool.create("south");

    /**
     * Whether this fence connects in the western direction
     */
    public static final PropertyBool WEST = PropertyBool.create("west");

    public BlockFence(Material materialIn) {
        this(materialIn, materialIn.getMaterialMapColor());
    }

    public BlockFence(Material p_i46395_1_, MapColor p_i46395_2_) {
        super(p_i46395_1_, p_i46395_2_);
        this.setDefaultState(this.blockState.getBaseState().withProperty(NORTH, Boolean.FALSE).withProperty(EAST, Boolean.FALSE).withProperty(SOUTH, Boolean.FALSE).withProperty(WEST, Boolean.FALSE));
        this.setCreativeTab(CreativeTabs.tabDecorations);
    }

    private static void addModernCollisionBox(BlockPos pos, AxisAlignedBB mask, List<AxisAlignedBB> list, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        AxisAlignedBB box = new AxisAlignedBB(
                (double) pos.getX() + minX / 16.0D,
                (double) pos.getY() + minY / 16.0D,
                (double) pos.getZ() + minZ / 16.0D,
                (double) pos.getX() + maxX / 16.0D,
                (double) pos.getY() + maxY / 16.0D,
                (double) pos.getZ() + maxZ / 16.0D);
        if (box.intersectsWith(mask)) {
            list.add(box);
        }
    }

    /**
     * Add all collision boxes of this Block to the list that intersect with the given mask.
     */
    public void addCollisionBoxesToList(World worldIn, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> list, Entity collidingEntity) {
        boolean modernTarget = ViaProtocol.newerThanOrEqualTo1_9();
        boolean flag = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.NORTH) : this.canConnectTo(worldIn, pos.north());
        boolean flag1 = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.SOUTH) : this.canConnectTo(worldIn, pos.south());
        boolean flag2 = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.WEST) : this.canConnectTo(worldIn, pos.west());
        boolean flag3 = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.EAST) : this.canConnectTo(worldIn, pos.east());

        if (ViaProtocol.newerThanOrEqualTo1_9()) {
            addModernCollisionBox(pos, mask, list, 6.0D, 0.0D, 6.0D, 10.0D, 24.0D, 10.0D);

            if (flag) {
                addModernCollisionBox(pos, mask, list, 6.0D, 0.0D, 0.0D, 10.0D, 24.0D, 10.0D);
            }

            if (flag1) {
                addModernCollisionBox(pos, mask, list, 6.0D, 0.0D, 6.0D, 10.0D, 24.0D, 16.0D);
            }

            if (flag2) {
                addModernCollisionBox(pos, mask, list, 0.0D, 0.0D, 6.0D, 10.0D, 24.0D, 10.0D);
            }

            if (flag3) {
                addModernCollisionBox(pos, mask, list, 6.0D, 0.0D, 6.0D, 16.0D, 24.0D, 10.0D);
            }

            return;
        }

        float f = 0.375F;
        float f1 = 0.625F;
        float f2 = 0.375F;
        float f3 = 0.625F;

        if (flag) {
            f2 = 0.0F;
        }

        if (flag1) {
            f3 = 1.0F;
        }

        if (flag || flag1) {
            this.setBlockBounds(f, 0.0F, f2, f1, 1.5F, f3);
            super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
        }

        f2 = 0.375F;
        f3 = 0.625F;

        if (flag2) {
            f = 0.0F;
        }

        if (flag3) {
            f1 = 1.0F;
        }

        if (flag2 || flag3 || !flag && !flag1) {
            this.setBlockBounds(f, 0.0F, f2, f1, 1.5F, f3);
            super.addCollisionBoxesToList(worldIn, pos, state, mask, list, collidingEntity);
        }

        if (flag) {
            f2 = 0.0F;
        }

        if (flag1) {
            f3 = 1.0F;
        }

        this.setBlockBounds(f, 0.0F, f2, f1, 1.0F, f3);
    }

    public void setBlockBoundsBasedOnState(IBlockAccess worldIn, BlockPos pos) {
        boolean modernTarget = ViaProtocol.newerThanOrEqualTo1_9();
        boolean flag = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.NORTH) : this.canConnectTo(worldIn, pos.north());
        boolean flag1 = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.SOUTH) : this.canConnectTo(worldIn, pos.south());
        boolean flag2 = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.WEST) : this.canConnectTo(worldIn, pos.west());
        boolean flag3 = modernTarget ? this.hasModernConnection(worldIn, pos, EnumFacing.EAST) : this.canConnectTo(worldIn, pos.east());
        float f = 0.375F;
        float f1 = 0.625F;
        float f2 = 0.375F;
        float f3 = 0.625F;

        if (flag) {
            f2 = 0.0F;
        }

        if (flag1) {
            f3 = 1.0F;
        }

        if (flag2) {
            f = 0.0F;
        }

        if (flag3) {
            f1 = 1.0F;
        }

        this.setBlockBounds(f, 0.0F, f2, f1, 1.0F, f3);
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

    public boolean isPassable(IBlockAccess worldIn, BlockPos pos) {
        return false;
    }

    public boolean canConnectTo(IBlockAccess worldIn, BlockPos pos) {
        Block block = worldIn.getBlockState(pos).getBlock();
        return block != Blocks.barrier && ((block instanceof BlockFence && block.blockMaterial == this.blockMaterial) || block instanceof BlockFenceGate || (block.blockMaterial.isOpaque() && block.isFullCube() && block.blockMaterial != Material.gourd));
    }

    private boolean canConnectToModern(IBlockAccess worldIn, BlockPos pos, EnumFacing direction) {
        BlockPos neighbor = pos.offset(direction);
        IBlockState state = worldIn.getBlockState(neighbor);
        Block block = state.getBlock();
        int protocol = ViaProtocol.targetProtocolVersion();

        if (block == Blocks.barrier) {
            return protocol >= ProtocolVersion.v1_9.getVersion() && protocol <= ProtocolVersion.v1_11_1.getVersion();
        }

        if (block.getMaterial() == Material.leaves) {
            return false;
        }

        if (block == Blocks.tnt) {
            return protocol >= ProtocolVersion.v1_12.getVersion();
        }

        if (block instanceof BlockFence) {
            if (block == this) {
                return true;
            }

            return block != Blocks.nether_brick_fence && this != Blocks.nether_brick_fence;
        }

        if (block instanceof BlockStairs) {
            return protocol >= ProtocolVersion.v1_12.getVersion() && state.getValue(BlockStairs.FACING) == direction.getOpposite();
        }

        if (block instanceof BlockFenceGate) {
            if (protocol <= ProtocolVersion.v1_11_1.getVersion()) {
                return true;
            }

            EnumFacing gateFacing = state.getValue(BlockFenceGate.FACING);
            return gateFacing.getAxis() != direction.getAxis();
        }

        if (block.getMaterial() == Material.gourd
                || block == Blocks.enchanting_table
                || block == Blocks.glass
                || block == Blocks.stained_glass
                || block instanceof BlockTrapDoor
                || block instanceof BlockPistonBase
                || block instanceof BlockPistonExtension
                || block == Blocks.farmland
                || block == Blocks.beacon
                || block == Blocks.cauldron
                || block == Blocks.glowstone
                || block == Blocks.sea_lantern
                || block == Blocks.ice) {
            return false;
        }

        return block.isFullCube() && block.isBlockSolid(worldIn, neighbor, direction.getOpposite());
    }

    private boolean hasModernConnection(IBlockAccess worldIn, BlockPos pos, EnumFacing direction) {
        if (ViaProtocol.newerThanOrEqualTo1_13()) {
            String value = ModernBlockStateTracker.getNativeProperty(pos, direction.getName());
            if (value != null) {
                return !"false".equals(value) && !"none".equals(value);
            }
        }
        return this.canConnectToModern(worldIn, pos, direction);
    }

    public boolean shouldSideBeRendered(IBlockAccess worldIn, BlockPos pos, EnumFacing side) {
        return true;
    }

    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumFacing side, float hitX, float hitY, float hitZ) {
        return worldIn.isRemote || ItemLead.attachToFence(playerIn, worldIn, pos);
    }

    /**
     * Convert the BlockState into the correct metadata value
     */
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    /**
     * Get the actual Block state of this Block at the given position. This applies properties not visible in the
     * metadata, such as fence connections.
     */
    public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        if (ViaProtocol.newerThanOrEqualTo1_9()) {
            return state.withProperty(NORTH, this.hasModernConnection(worldIn, pos, EnumFacing.NORTH)).withProperty(EAST, this.hasModernConnection(worldIn, pos, EnumFacing.EAST)).withProperty(SOUTH, this.hasModernConnection(worldIn, pos, EnumFacing.SOUTH)).withProperty(WEST, this.hasModernConnection(worldIn, pos, EnumFacing.WEST));
        }

        return state.withProperty(NORTH, this.canConnectTo(worldIn, pos.north())).withProperty(EAST, this.canConnectTo(worldIn, pos.east())).withProperty(SOUTH, this.canConnectTo(worldIn, pos.south())).withProperty(WEST, this.canConnectTo(worldIn, pos.west()));
    }

    protected BlockState createBlockState() {
        return new BlockState(this, NORTH, EAST, WEST, SOUTH);
    }
}
