package net.minecraft.block;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.util.EnumFacing;

/**
 * ModernBlock equivalent of BlockDirectional.
 */
public abstract class ModernBlockDirectional extends ModernBlock {
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    protected ModernBlockDirectional(Material materialIn) {
        super(materialIn);
    }

    protected ModernBlockDirectional(Material materialIn, MapColor mapColorIn) {
        super(materialIn, mapColorIn);
    }
}
