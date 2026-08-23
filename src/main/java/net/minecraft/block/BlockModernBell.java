package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class BlockModernBell extends BlockModernShape {
    public static final PropertyEnum<Attachment> ATTACHMENT = PropertyEnum.create("attachment", Attachment.class);
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class,
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST);
    private static final EnumFacing[] FACINGS = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    public BlockModernBell(int firstState, int lastState) {
        super(Material.iron, ProtocolVersion.v1_14, firstState, lastState);
        setDefaultState(blockState.getBaseState().withProperty(ATTACHMENT, Attachment.FLOOR)
                .withProperty(FACING, EnumFacing.NORTH));
    }

    private static void add(List<AxisAlignedBB> boxes, AxisAlignedBB mask, BlockPos pos,
                            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        AxisAlignedBB box = new AxisAlignedBB(pos.getX() + minX / 16.0D, pos.getY() + minY / 16.0D,
                pos.getZ() + minZ / 16.0D, pos.getX() + maxX / 16.0D, pos.getY() + maxY / 16.0D,
                pos.getZ() + maxZ / 16.0D);
        if (box.intersectsWith(mask)) boxes.add(box);
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, ATTACHMENT, FACING);
    }

    @Override
    public IBlockState getStateFromViaStateId(int id) {
        int value = id - firstState;
        return getDefaultState().withProperty(ATTACHMENT, Attachment.values()[(value / 4) % 4])
                .withProperty(FACING, FACINGS[value % 4]);
    }

    @Override
    public void addCollisionBoxesToList(World world, BlockPos pos, IBlockState state, AxisAlignedBB mask,
                                        List<AxisAlignedBB> list, Entity entity) {
        EnumFacing facing = state.getValue(FACING);
        Attachment attachment = state.getValue(ATTACHMENT);
        if (attachment == Attachment.FLOOR) {
            if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) add(list, mask, pos, 0, 0, 4, 16, 16, 12);
            else add(list, mask, pos, 4, 0, 0, 12, 16, 16);
            return;
        }
        add(list, mask, pos, 5, 6, 5, 11, 13, 11);
        add(list, mask, pos, 4, 4, 4, 12, 6, 12);
        if (attachment == Attachment.CEILING) add(list, mask, pos, 7, 13, 7, 9, 16, 9);
        else if (attachment == Attachment.DOUBLE_WALL) {
            if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) add(list, mask, pos, 7, 13, 0, 9, 15, 16);
            else add(list, mask, pos, 0, 13, 7, 16, 15, 9);
        } else if (facing == EnumFacing.NORTH) add(list, mask, pos, 7, 13, 0, 9, 15, 13);
        else if (facing == EnumFacing.SOUTH) add(list, mask, pos, 7, 13, 3, 9, 15, 16);
        else if (facing == EnumFacing.EAST) add(list, mask, pos, 3, 13, 7, 16, 15, 9);
        else add(list, mask, pos, 0, 13, 7, 13, 15, 9);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        addCollisionBoxesToList(world, pos, state, new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), boxes, null);
        double minX = 1, minY = 1, minZ = 1, maxX = 0, maxY = 0, maxZ = 0;
        for (AxisAlignedBB box : boxes) {
            minX = Math.min(minX, box.minX - pos.getX());
            minY = Math.min(minY, box.minY - pos.getY());
            minZ = Math.min(minZ, box.minZ - pos.getZ());
            maxX = Math.max(maxX, box.maxX - pos.getX());
            maxY = Math.max(maxY, box.maxY - pos.getY());
            maxZ = Math.max(maxZ, box.maxZ - pos.getZ());
        }
        return new AxisAlignedBB(pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
                pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ);
    }

    public enum Attachment implements IStringSerializable {
        FLOOR, CEILING, SINGLE_WALL, DOUBLE_WALL;

        public String getName() {
            return name().toLowerCase();
        }
    }
}
