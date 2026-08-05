package net.minecraft.rendering.optimization.normal;

import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

public class PooledMutableBlockPos extends ExtendedMutableBlockPos implements AutoCloseable {
    private static final List<PooledMutableBlockPos> POOL = new ArrayList<>(100);
    private boolean released;

    private PooledMutableBlockPos(int x, int y, int z) {
        super(x, y, z);
    }

    public static PooledMutableBlockPos get() {
        return get(0, 0, 0);
    }

    public static PooledMutableBlockPos get(double x, double y, double z) {
        return get(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    public static PooledMutableBlockPos get(Vec3i vec) {
        return get(vec.getX(), vec.getY(), vec.getZ());
    }

    public static PooledMutableBlockPos get(int x, int y, int z) {
        synchronized (POOL) {
            while (!POOL.isEmpty()) {
                PooledMutableBlockPos removed = POOL.remove(POOL.size() - 1);

                if (removed != null && removed.released) {
                    removed.released = false;
                    removed.set(x, y, z);
                    return removed;
                }
            }
        }

        return new PooledMutableBlockPos(x, y, z);
    }

    public void release() {
        synchronized (POOL) {
            if (POOL.size() < 100) {
                POOL.add(this);
            }

            this.released = true;
        }
    }

    @Override
    public PooledMutableBlockPos set(int x, int y, int z) {
        if (this.released) {
            this.released = false;
        }

        return (PooledMutableBlockPos) super.set(x, y, z);
    }

    @Override
    public PooledMutableBlockPos set(double x, double y, double z) {
        return this.set(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    @Override
    public PooledMutableBlockPos set(Entity entity) {
        return this.set(entity.posX, entity.posY, entity.posZ);
    }

    @Override
    public PooledMutableBlockPos set(Vec3i vec) {
        return this.set(vec.getX(), vec.getY(), vec.getZ());
    }

    @Override
    public PooledMutableBlockPos move(EnumFacing facing) {
        return this.move(facing, 1);
    }

    @Override
    public PooledMutableBlockPos move(EnumFacing facing, int n) {
        return this.set(this.getX() + facing.getFrontOffsetX() * n, this.getY() + facing.getFrontOffsetY() * n, this.getZ() + facing.getFrontOffsetZ() * n);
    }

    @Override
    public void close() {
        this.release();
    }
}