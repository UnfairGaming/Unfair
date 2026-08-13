package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumWorldBlockLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockHoney extends ModernBlock
{
    protected BlockHoney()
    {
        super(Material.clay);
        this.setLightOpacity(0);
    }

    public boolean isOpaqueCube() { return false; }

    public EnumWorldBlockLayer getBlockLayer() { return EnumWorldBlockLayer.TRANSLUCENT; }

    public AxisAlignedBB getCollisionBoundingBox(World worldIn, BlockPos pos, IBlockState state)
    {
        return new AxisAlignedBB(pos.getX() + 1.0D / 16.0D, pos.getY(), pos.getZ() + 1.0D / 16.0D,
                pos.getX() + 15.0D / 16.0D, pos.getY() + 15.0D / 16.0D, pos.getZ() + 15.0D / 16.0D);
    }

    public void setBlockBoundsBasedOnState(IBlockAccess worldIn, BlockPos pos)
    {
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
    }

    public void onEntityCollidedWithBlock(World worldIn, BlockPos pos, IBlockState state, Entity entityIn)
    {
        double edgeX = Math.abs(entityIn.prevPosX - (pos.getX() + 0.5D));
        double edgeZ = Math.abs(entityIn.prevPosZ - (pos.getZ() + 0.5D));
        double slidingEdge = 0.4375D + entityIn.width / 2.0F;
        boolean sliding = !entityIn.onGround
                && entityIn.posY <= pos.getY() + 0.9375D + 1.0E-7D
                && entityIn.motionY < -0.08D
                && (edgeX + 1.0E-7D > slidingEdge || edgeZ + 1.0E-7D > slidingEdge);
        if (sliding)
        {
            double scale = entityIn.motionY < -0.13D ? -0.05D / entityIn.motionY : 1.0D;
            entityIn.motionX *= scale;
            entityIn.motionY = -0.05D;
            entityIn.motionZ *= scale;
        }
        entityIn.fallDistance = 0.0F;
    }

    public ProtocolVersion getViaStateProtocol() { return ProtocolVersion.v1_15; }
    public int getViaStateIdMin() { return 11335; }
    public int getViaStateIdMax() { return 11335; }
    public IBlockState getStateFromViaStateId(int stateId) { return this.getDefaultState(); }
}
