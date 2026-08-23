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

public class BlockModernGrindstone extends BlockModernShape {
    public static final PropertyEnum<Face> FACE = PropertyEnum.create("face", Face.class);
    public static final PropertyEnum<EnumFacing> FACING = PropertyEnum.create("facing", EnumFacing.class,
            EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST);
    private static final EnumFacing[] FACINGS = {EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};

    public BlockModernGrindstone(int firstState, int lastState) {
        super(Material.wood, ProtocolVersion.v1_14, firstState, lastState);
        setDefaultState(blockState.getBaseState().withProperty(FACE, Face.FLOOR).withProperty(FACING, EnumFacing.NORTH));
    }

    private static void add(List<AxisAlignedBB> boxes, AxisAlignedBB mask, BlockPos pos,
                            double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        AxisAlignedBB box = new AxisAlignedBB(pos.getX() + minX / 16, pos.getY() + minY / 16, pos.getZ() + minZ / 16,
                pos.getX() + maxX / 16, pos.getY() + maxY / 16, pos.getZ() + maxZ / 16);
        if (box.intersectsWith(mask)) boxes.add(box);
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, FACE, FACING);
    }

    @Override
    public IBlockState getStateFromViaStateId(int id) {
        int value = id - firstState;
        return getDefaultState().withProperty(FACE, Face.values()[(value / 4) % 3]).withProperty(FACING, FACINGS[value % 4]);
    }

    @Override
    public void addCollisionBoxesToList(World world, BlockPos pos, IBlockState state, AxisAlignedBB mask,
                                        List<AxisAlignedBB> list, Entity entity) {
        Face face = state.getValue(FACE);
        EnumFacing facing = state.getValue(FACING);
        if (face == Face.FLOOR) {
            if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) {
                add(list, mask, pos, 2, 0, 6, 4, 7, 10);
                add(list, mask, pos, 12, 0, 6, 14, 7, 10);
                add(list, mask, pos, 2, 7, 5, 4, 13, 11);
                add(list, mask, pos, 12, 7, 5, 14, 13, 11);
                add(list, mask, pos, 4, 4, 2, 12, 16, 14);
            } else {
                add(list, mask, pos, 6, 0, 2, 10, 7, 4);
                add(list, mask, pos, 6, 0, 12, 10, 7, 14);
                add(list, mask, pos, 5, 7, 2, 11, 13, 4);
                add(list, mask, pos, 5, 7, 12, 11, 13, 14);
                add(list, mask, pos, 2, 4, 4, 14, 16, 12);
            }
            return;
        }
        boolean wall = face == Face.WALL;
        if (wall) {
            switch (facing) {
                case NORTH:
                    add(list, mask, pos, 2, 6, 7, 4, 10, 16);
                    add(list, mask, pos, 12, 6, 7, 14, 10, 16);
                    add(list, mask, pos, 2, 5, 3, 4, 11, 9);
                    add(list, mask, pos, 12, 5, 3, 14, 11, 9);
                    add(list, mask, pos, 4, 2, 0, 12, 14, 12);
                    break;
                case SOUTH:
                    add(list, mask, pos, 2, 6, 0, 4, 10, 7);
                    add(list, mask, pos, 12, 6, 0, 14, 10, 7);
                    add(list, mask, pos, 2, 5, 7, 4, 11, 13);
                    add(list, mask, pos, 12, 5, 7, 14, 11, 13);
                    add(list, mask, pos, 4, 2, 4, 12, 14, 16);
                    break;
                case WEST:
                    add(list, mask, pos, 7, 6, 2, 16, 10, 4);
                    add(list, mask, pos, 7, 6, 12, 16, 10, 14);
                    add(list, mask, pos, 3, 5, 2, 9, 11, 4);
                    add(list, mask, pos, 3, 5, 12, 9, 11, 14);
                    add(list, mask, pos, 0, 2, 4, 12, 14, 12);
                    break;
                default:
                    add(list, mask, pos, 0, 6, 2, 9, 10, 4);
                    add(list, mask, pos, 0, 6, 12, 9, 10, 14);
                    add(list, mask, pos, 7, 5, 2, 13, 11, 4);
                    add(list, mask, pos, 7, 5, 12, 13, 11, 14);
                    add(list, mask, pos, 4, 2, 4, 16, 14, 12);
            }
        } else if (facing == EnumFacing.NORTH || facing == EnumFacing.SOUTH) {
            add(list, mask, pos, 2, 9, 6, 4, 16, 10);
            add(list, mask, pos, 12, 9, 6, 14, 16, 10);
            add(list, mask, pos, 2, 3, 5, 4, 9, 11);
            add(list, mask, pos, 12, 3, 5, 14, 9, 11);
            add(list, mask, pos, 4, 0, 2, 12, 12, 14);
        } else {
            add(list, mask, pos, 6, 9, 2, 10, 16, 4);
            add(list, mask, pos, 6, 9, 12, 10, 16, 14);
            add(list, mask, pos, 5, 3, 2, 11, 9, 4);
            add(list, mask, pos, 5, 3, 12, 11, 9, 14);
            add(list, mask, pos, 2, 0, 4, 14, 12, 12);
        }
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        addCollisionBoxesToList(world, pos, state, new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1), boxes, null);
        double minX = 1, minY = 1, minZ = 1, maxX = 0, maxY = 0, maxZ = 0;
        for (AxisAlignedBB b : boxes) {
            minX = Math.min(minX, b.minX - pos.getX());
            minY = Math.min(minY, b.minY - pos.getY());
            minZ = Math.min(minZ, b.minZ - pos.getZ());
            maxX = Math.max(maxX, b.maxX - pos.getX());
            maxY = Math.max(maxY, b.maxY - pos.getY());
            maxZ = Math.max(maxZ, b.maxZ - pos.getZ());
        }
        return new AxisAlignedBB(pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ, pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ);
    }

    public enum Face implements IStringSerializable {
        FLOOR, WALL, CEILING;

        public String getName() {
            return name().toLowerCase();
        }
    }
}
