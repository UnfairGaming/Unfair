package net.minecraft.block;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import cn.unfair.util.via.ViaProtocol;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;

public class BlockPowderSnow extends ModernBlock
{
    protected BlockPowderSnow() { super(Material.craftedSnow); }

    public AxisAlignedBB getCollisionBoundingBox(World worldIn, BlockPos pos, IBlockState state)
    {
        return null;
    }

    public boolean isOpaqueCube() { return false; }

    public void setBlockBoundsBasedOnState(IBlockAccess worldIn, BlockPos pos) { this.setBlockBounds(0, 0, 0, 1, 1, 1); }

    public void addCollisionBoxesToList(World worldIn, BlockPos pos, IBlockState state, AxisAlignedBB mask,
                                        java.util.List<AxisAlignedBB> list, Entity entityIn)
    {
        if (entityIn == null) return;
        if (entityIn.fallDistance > 2.5F)
        {
            addBox(pos, mask, list, ViaProtocol.newerThanOrEqualTo(ProtocolVersion.v1_21_4) ? 1.0D : 0.9D);
            return;
        }
        if (entityIn instanceof EntityPlayer)
        {
            ItemStack boots = ((EntityPlayer) entityIn).getCurrentArmor(0);
            if (boots != null && boots.getItem() == Items.leather_boots && !entityIn.isSneaking()
                    && !entityIn.isRiding()
                    && entityIn.getEntityBoundingBox().minY >= pos.getY() + 1.0D - 1.0E-5D)
            {
                addBox(pos, mask, list, 1.0D);
            }
        }
    }

    private static void addBox(BlockPos pos, AxisAlignedBB mask, java.util.List<AxisAlignedBB> list, double height)
    {
        AxisAlignedBB box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0D,
                pos.getY() + height, pos.getZ() + 1.0D);
        if (box.intersectsWith(mask)) list.add(box);
    }

    public void onEntityCollidedWithBlock(World worldIn, BlockPos pos, IBlockState state, Entity entityIn)
    {
        if (ViaProtocol.newerThanOrEqualTo(ProtocolVersion.v1_17)
                && MathHelper.floor_double(entityIn.posX) == pos.getX()
                && MathHelper.floor_double(entityIn.posY) == pos.getY()
                && MathHelper.floor_double(entityIn.posZ) == pos.getZ()) {
            entityIn.motionX *= 0.9D;
            entityIn.motionY *= 1.5D;
            entityIn.motionZ *= 0.9D;
        }
    }

    public ProtocolVersion getViaStateProtocol() { return ProtocolVersion.v1_17; }
    public int getViaStateIdMin() { return 17717; }
    public int getViaStateIdMax() { return 17717; }
    public IBlockState getStateFromViaStateId(int stateId) { return this.getDefaultState(); }
}
