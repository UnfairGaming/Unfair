package net.minecraft.rendering.optimization.normal;

import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3i;

public class ExtendedMutableBlockPos extends BlockPos.MutableBlockPos {
    public ExtendedMutableBlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public ExtendedMutableBlockPos set(int x, int y, int z) {
        return (ExtendedMutableBlockPos) super.set(x, y, z);
    }

    public ExtendedMutableBlockPos set(double x, double y, double z) {
        return this.set(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    public ExtendedMutableBlockPos set(Entity entity) {
        return this.set(entity.posX, entity.posY, entity.posZ);
    }

    public ExtendedMutableBlockPos set(Vec3i vec) {
        return this.set(vec.getX(), vec.getY(), vec.getZ());
    }

    public ExtendedMutableBlockPos move(EnumFacing facing) {
        return this.move(facing, 1);
    }

    public ExtendedMutableBlockPos move(EnumFacing facing, int n) {
        return this.set(this.getX() + facing.getFrontOffsetX() * n, this.getY() + facing.getFrontOffsetY() * n, this.getZ() + facing.getFrontOffsetZ() * n);
    }
}