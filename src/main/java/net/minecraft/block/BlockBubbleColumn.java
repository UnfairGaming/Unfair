package net.minecraft.block;

import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import cn.unfair.util.via.ViaProtocol;

public class BlockBubbleColumn extends ModernBlock
{
    public static final PropertyBool DRAG = PropertyBool.create("drag");

    protected BlockBubbleColumn()
    {
        super(Material.water);
        this.setDefaultState(this.blockState.getBaseState().withProperty(DRAG, Boolean.TRUE));
        this.setLightOpacity(3);
    }

    public AxisAlignedBB getCollisionBoundingBox(World worldIn, BlockPos pos, IBlockState state)
    {
        return null;
    }

    public boolean isOpaqueCube() { return false; }
    public boolean isFullCube() { return false; }
    public boolean canCollideCheck(IBlockState state, boolean hitIfLiquid) { return false; }
    public MovingObjectPosition collisionRayTrace(World worldIn, BlockPos pos, Vec3 start, Vec3 end) { return null; }
    public int getRenderType() { return -1; }
    public EnumWorldBlockLayer getBlockLayer() { return EnumWorldBlockLayer.TRANSLUCENT; }

    public void onEntityCollidedWithBlock(World worldIn, BlockPos pos, IBlockState state, Entity entityIn)
    {
        if (!ViaProtocol.newerThanOrEqualTo1_13() || entityIn instanceof EntityBoat)
        {
            return;
        }

        boolean surface = worldIn.isAirBlock(pos.up());
        boolean drag = state.getValue(DRAG);
        if (drag)
        {
            entityIn.motionY = Math.max(surface ? -0.9D : -0.3D, entityIn.motionY - 0.03D);
        }
        else
        {
            entityIn.motionY = Math.min(surface ? 1.8D : 0.7D, entityIn.motionY + (surface ? 0.1D : 0.06D));
        }
        entityIn.fallDistance = 0.0F;
    }

    public int getViaStateIdMin() { return 9131; }
    public int getViaStateIdMax() { return 9132; }
    public IBlockState getStateFromViaStateId(int stateId)
    {
        // 1.13 registry order is drag=false (soul-sand lift), then drag=true (magma drag).
        return this.getDefaultState().withProperty(DRAG, stateId == 9132);
    }
    protected BlockState createBlockState() { return new BlockState(this, new IProperty[] {DRAG}); }
}
