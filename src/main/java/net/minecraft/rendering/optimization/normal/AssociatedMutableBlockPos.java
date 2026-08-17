package net.minecraft.rendering.optimization.normal;

import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * BlockPos with an associated {@link ExtendedMutableBlockPos}. Used when some methods are called concurrently,
 * so we can't use a class variable to store a {@link PooledMutableBlockPos}.
 */
public class AssociatedMutableBlockPos extends ExtendedMutableBlockPos implements AutoCloseable {
    private static final List<AssociatedMutableBlockPos> POOL = new ArrayList<>(100);
    private final Companion associatedBlockPos;
    private boolean released;

    private AssociatedMutableBlockPos(Companion associatedBlockPos, int x, int y, int z) {
        super(x, y, z);
        this.associatedBlockPos = associatedBlockPos;
    }

    public static AssociatedMutableBlockPos get(BlockPos blockPos) {
        return get(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public static AssociatedMutableBlockPos get(double x, double y, double z) {
        return get(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    public static AssociatedMutableBlockPos get(Vec3i vec) {
        return get(vec.getX(), vec.getY(), vec.getZ());
    }

    public static AssociatedMutableBlockPos get(int x, int y, int z) {
        synchronized (POOL) {
            while (!POOL.isEmpty()) {
                AssociatedMutableBlockPos removed = POOL.remove(POOL.size() - 1);

                if (removed != null && removed.released) {
                    removed.released = false;
                    removed.set(x, y, z);
                    return removed;
                }
            }
        }

        Companion linked = new Companion(0, 0, 0);
        AssociatedMutableBlockPos parent = new AssociatedMutableBlockPos(linked, x, y, z);
        linked.setParent(parent);
        return parent;
    }

    @Override
    public AssociatedMutableBlockPos set(int x, int y, int z) {
        if (this.released) {
            this.released = false;
        }

        return (AssociatedMutableBlockPos) super.set(x, y, z);
    }

    @Override
    public AssociatedMutableBlockPos set(double x, double y, double z) {
        return this.set(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
    }

    @Override
    public AssociatedMutableBlockPos set(Entity entity) {
        return this.set(entity.posX, entity.posY, entity.posZ);
    }

    @Override
    public AssociatedMutableBlockPos set(Vec3i vec) {
        return this.set(vec.getX(), vec.getY(), vec.getZ());
    }

    public Companion associateWithOwnBlockPos() {
        return this.associatedBlockPos.set(this.getX(), this.getY(), this.getZ());
    }

    public Companion associateWithPos(int x, int y, int z) {
        return this.associatedBlockPos.set(x, y, z);
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
    public void close() {
        this.release();
    }

    public Companion getAssociated() {
        return associatedBlockPos;
    }

    public static class Companion extends ExtendedMutableBlockPos {
        private AssociatedMutableBlockPos parent;

        Companion(int x, int y, int z) {
            super(x, y, z);
        }

        @Override
        public Companion set(int x, int y, int z) {
            return (Companion) super.set(x, y, z);
        }

        @Override
        public Companion set(double x, double y, double z) {
            return this.set(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z));
        }

        @Override
        public Companion set(Entity entity) {
            return this.set(entity.posX, entity.posY, entity.posZ);
        }

        @Override
        public Companion set(Vec3i vec) {
            return this.set(vec.getX(), vec.getY(), vec.getZ());
        }

        public AssociatedMutableBlockPos getParent() {
            return parent;
        }

        private void setParent(AssociatedMutableBlockPos parent) {
            this.parent = parent;
        }
    }
}