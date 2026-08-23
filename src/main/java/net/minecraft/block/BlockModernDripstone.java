package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;

public class BlockModernDripstone extends BlockModernShape {
    public static final PropertyEnum<Thickness> THICKNESS = PropertyEnum.create("thickness", Thickness.class);
    public static final PropertyDirection VERTICAL_DIRECTION =
            PropertyDirection.create("vertical_direction", EnumFacing.Plane.VERTICAL);
    private final int first;

    public BlockModernDripstone(int first) {
        super(Material.rock, ProtocolVersion.v1_17, first, first + 19);
        this.first = first;
        setDefaultState(blockState.getBaseState()
                .withProperty(THICKNESS, Thickness.TIP)
                .withProperty(VERTICAL_DIRECTION, EnumFacing.UP));
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, THICKNESS, VERTICAL_DIRECTION);
    }

    @Override
    public IBlockState getStateFromViaStateId(int id) {
        int value = id - first;
        // 1.17 order: thickness, vertical_direction, waterlogged.
        return getDefaultState()
                .withProperty(THICKNESS, Thickness.values()[value / 4])
                .withProperty(VERTICAL_DIRECTION, (value & 2) == 0 ? EnumFacing.UP : EnumFacing.DOWN);
    }

    @Override
    public EnumOffsetType getOffsetType() {
        return EnumOffsetType.XZ;
    }

    @Override
    public float getMaxHorizontalModelOffset() {
        return 0.125F;
    }

    private AxisAlignedBB getUnoffsetBox(BlockPos pos, IBlockState state) {
        Thickness thickness = state.getValue(THICKNESS);
        EnumFacing direction = state.getValue(VERTICAL_DIRECTION);
        double inset;
        double minY = 0.0D;
        double maxY = 1.0D;

        switch (thickness) {
            case TIP_MERGE:
                inset = 5.0D;
                break;
            case TIP:
                inset = 5.0D;
                if (direction == EnumFacing.DOWN) {
                    minY = 5.0D / 16.0D;
                } else {
                    maxY = 11.0D / 16.0D;
                }
                break;
            case FRUSTUM:
                inset = 4.0D;
                break;
            case MIDDLE:
                inset = 3.0D;
                break;
            default:
                inset = 2.0D;
        }

        return new AxisAlignedBB(pos.getX() + inset / 16.0D, pos.getY() + minY,
                pos.getZ() + inset / 16.0D, pos.getX() + (16.0D - inset) / 16.0D,
                pos.getY() + maxY, pos.getZ() + (16.0D - inset) / 16.0D);
    }

    private AxisAlignedBB applyOffset(AxisAlignedBB box, BlockPos pos) {
        long hash = MathHelper.getCoordinateRandom(pos.getX(), 0, pos.getZ()) >> 16;
        double x = MathHelper.clamp_double((((double) (hash & 15L) / 15.0D) - 0.5D) * 0.5D,
                -0.125D, 0.125D);
        double z = MathHelper.clamp_double((((double) (hash >> 8 & 15L) / 15.0D) - 0.5D) * 0.5D,
                -0.125D, 0.125D);
        return box.offset(x, 0.0D, z);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        return applyOffset(getUnoffsetBox(pos, state), pos);
    }

    @Override
    public List<AxisAlignedBB> getSelectedBoundingBoxes(World world, BlockPos pos) {
        // RenderGlobal applies the visual block offset to selection boxes.
        return Collections.singletonList(getUnoffsetBox(pos, world.getBlockState(pos)));
    }

    public enum Thickness implements IStringSerializable {
        TIP_MERGE, TIP, FRUSTUM, MIDDLE, BASE;

        @Override
        public String getName() {
            return name().toLowerCase();
        }
    }
}
