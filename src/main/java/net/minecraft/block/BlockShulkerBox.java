package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.entity.Entity;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BlockShulkerBox extends ModernBlock
{
    public static final PropertyDirection FACING = PropertyDirection.create("facing");
    private final int colorIndex;
    private static final ConcurrentMap<BlockPos, Animation> ANIMATIONS = new ConcurrentHashMap<>();
    protected BlockShulkerBox(int colorIndex) { super(Material.rock); this.colorIndex=colorIndex; this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.UP)); }
    public boolean isOpaqueCube() { return false; }
    public boolean isFullCube() { return false; }
    public void setBlockBoundsBasedOnState(IBlockAccess world, BlockPos pos) { setBounds(pos, world.getBlockState(pos).getValue(FACING)); }
    private void setBounds(BlockPos pos, EnumFacing facing) {
        float extension = getProgress(pos) * 0.5F;
        setBlockBounds(Math.min(0.0F, facing.getFrontOffsetX() * extension), Math.min(0.0F, facing.getFrontOffsetY() * extension), Math.min(0.0F, facing.getFrontOffsetZ() * extension), 1.0F + Math.max(0.0F, facing.getFrontOffsetX() * extension), 1.0F + Math.max(0.0F, facing.getFrontOffsetY() * extension), 1.0F + Math.max(0.0F, facing.getFrontOffsetZ() * extension));
    }
    public void addCollisionBoxesToList(World world, BlockPos pos, IBlockState state, AxisAlignedBB mask, List<AxisAlignedBB> list, Entity entity) { setBounds(pos, state.getValue(FACING)); super.addCollisionBoxesToList(world, pos, state, mask, list, entity); }
    public AxisAlignedBB getCollisionBoundingBox(World world, BlockPos pos, IBlockState state) { setBounds(pos, state.getValue(FACING)); return super.getCollisionBoundingBox(world, pos, state); }
    public static void handleBlockAction(BlockPos pos, boolean open) { ANIMATIONS.compute(pos, (ignored, old) -> new Animation(old == null ? 0.0F : old.progress(), open)); }
    private static float getProgress(BlockPos pos) { Animation animation = ANIMATIONS.get(pos); if (animation == null) return 0.0F; float value = animation.progress(); if (value == animation.target) ANIMATIONS.remove(pos, animation); return value; }
    public int getViaStateIdMin() { return 8211 + colorIndex * 6; }
    public int getViaStateIdMax() { return getViaStateIdMin() + 5; }
    public boolean handlesViaState(ProtocolVersion protocol, int id) { int base = 3504 + (colorIndex - 1) * 16; return protocol.equals(ProtocolVersion.v1_11) ? colorIndex > 0 && id >= base && id <= base + 5 : super.handlesViaState(protocol, id); }
    public IBlockState getStateFromViaState(ProtocolVersion protocol, int id) { int base = 3504 + (colorIndex - 1) * 16; return protocol.equals(ProtocolVersion.v1_11) ? getDefaultState().withProperty(FACING, LEGACY_FACING[id - base]) : getStateFromViaStateId(id); }
    private static final EnumFacing[] VIA_FACING = {EnumFacing.NORTH, EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.UP, EnumFacing.DOWN};
    private static final EnumFacing[] LEGACY_FACING = {EnumFacing.DOWN, EnumFacing.UP, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST};
    public IBlockState getStateFromViaStateId(int id) { return getDefaultState().withProperty(FACING, VIA_FACING[id-getViaStateIdMin()]); }
    public IBlockState getStateFromMeta(int meta) { return getDefaultState().withProperty(FACING, meta >= 0 && meta < VIA_FACING.length ? VIA_FACING[meta] : EnumFacing.UP); }
    public int getMetaFromState(IBlockState state) { for (int i = 0; i < VIA_FACING.length; i++) if (VIA_FACING[i] == state.getValue(FACING)) return i; return 4; }
    protected BlockState createBlockState() { return new BlockState(this, new IProperty[] {FACING}); }
    private static final class Animation { private final float start; private final float target; private final long started = System.nanoTime(); private Animation(float start, boolean open) { this.start = start; this.target = open ? 1.0F : 0.0F; } private float progress() { float amount = Math.min(1.0F, (System.nanoTime() - started) / 500000000.0F); return start + (target - start) * amount; } }
}
