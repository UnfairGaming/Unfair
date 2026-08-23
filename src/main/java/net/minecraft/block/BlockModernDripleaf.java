package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.*;
import net.minecraft.block.state.*;
import net.minecraft.entity.Entity;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.List;

public class BlockModernDripleaf extends ModernBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
    public static final PropertyEnum<Tilt> TILT = PropertyEnum.create("tilt", Tilt.class);
    private final int first;

    public BlockModernDripleaf(int first) {
        super(Material.plants);
        this.first = first;
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(TILT, Tilt.NONE));
    }

    private static void add(BlockPos p, AxisAlignedBB m, List<AxisAlignedBB> l, double a, double b, double c, double d, double e, double f) {
        AxisAlignedBB x = new AxisAlignedBB(p.getX() + a / 16, p.getY() + b / 16, p.getZ() + c / 16, p.getX() + d / 16, p.getY() + e / 16, p.getZ() + f / 16);
        if (x.intersectsWith(m)) l.add(x);
    }

    public int getViaStateIdMin() {
        return first;
    }

    public int getViaStateIdMax() {
        return first + 31;
    }

    public ProtocolVersion getViaStateProtocol() {
        return ProtocolVersion.v1_17;
    }

    public IBlockState getStateFromViaStateId(int id) {
        int v = id - first;
        return getDefaultState().withProperty(FACING, new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST}[v / 8]).withProperty(TILT, Tilt.values()[(v / 2) % 4]);
    }

    protected BlockState createBlockState() {
        return new BlockState(this, FACING, TILT);
    }

    public boolean isOpaqueCube() {
        return false;
    }

    public boolean isFullCube() {
        return false;
    }

    public void addCollisionBoxesToList(World w, BlockPos p, IBlockState s, AxisAlignedBB m, List<AxisAlignedBB> l, Entity e) {
        if (s.getValue(TILT) != Tilt.FULL) add(p, m, l, 0, 11, 0, 16, 15, 16);
        add(p, m, l, 5, 0, 5, 11, 15, 11);
    }

    public enum Tilt implements IStringSerializable {
        NONE, UNSTABLE, PARTIAL, FULL;

        public String getName() {
            return name().toLowerCase();
        }
    }
}
