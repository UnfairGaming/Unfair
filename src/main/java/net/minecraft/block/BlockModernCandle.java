package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class BlockModernCandle extends ModernBlock {
    public static final PropertyInteger CANDLES = PropertyInteger.create("candles", 1, 4);
    public static final PropertyBool LIT = PropertyBool.create("lit");
    private final int first;

    public BlockModernCandle(int first, int last) {
        super(Material.circuits);
        this.first = first;
        setDefaultState(blockState.getBaseState().withProperty(CANDLES, 1).withProperty(LIT, false));
    }

    public int getViaStateIdMin() {
        return first;
    }

    public int getViaStateIdMax() {
        return first + 15;
    }

    public ProtocolVersion getViaStateProtocol() {
        return ProtocolVersion.v1_17;
    }

    public IBlockState getStateFromViaStateId(int id) {
        int v = id - first;
        return getDefaultState().withProperty(CANDLES, v / 4 + 1).withProperty(LIT, (v & 2) != 0);
    }

    protected BlockState createBlockState() {
        return new BlockState(this, CANDLES, LIT);
    }

    public boolean isOpaqueCube() {
        return false;
    }

    public boolean isFullCube() {
        return false;
    }

    private AxisAlignedBB getCandleCollisionBox(BlockPos pos, IBlockState state) {
        int candles = state.getValue(CANDLES);
        double minX;
        double minZ;
        double maxX;
        double maxZ;

        // 1.17+ uses one combined movement shape for candle clusters. These
        // bounds match Grim's CollisionData rather than unioning individual
        // candle stems, which produces the wrong footprint for 2-4 candles.
        switch (candles) {
            case 1:
                minX = 7.0D;
                minZ = 7.0D;
                maxX = 9.0D;
                maxZ = 9.0D;
                break;
            case 2:
                minX = 5.0D;
                minZ = 6.0D;
                maxX = 11.0D;
                maxZ = 9.0D;
                break;
            case 3:
                minX = 5.0D;
                minZ = 6.0D;
                maxX = 10.0D;
                maxZ = 11.0D;
                break;
            default:
                minX = 5.0D;
                minZ = 5.0D;
                maxX = 11.0D;
                maxZ = 10.0D;
                break;
        }

        return new AxisAlignedBB(
                pos.getX() + minX / 16.0D, pos.getY(), pos.getZ() + minZ / 16.0D,
                pos.getX() + maxX / 16.0D, pos.getY() + 6.0D / 16.0D, pos.getZ() + maxZ / 16.0D);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) {
        return getCandleCollisionBox(pos, state);
    }

    @Override
    public void addCollisionBoxesToList(World world, BlockPos pos, IBlockState state,
                                        AxisAlignedBB mask, List<AxisAlignedBB> list, Entity entity) {
        AxisAlignedBB box = getCandleCollisionBox(pos, state);
        if (box.intersectsWith(mask)) {
            list.add(box);
        }
    }
}
