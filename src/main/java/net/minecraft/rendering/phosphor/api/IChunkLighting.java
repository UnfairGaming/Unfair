package net.minecraft.rendering.phosphor.api;

import net.minecraft.util.BlockPos;
import net.minecraft.world.EnumSkyBlock;

public interface IChunkLighting {
    int getCachedLightFor(EnumSkyBlock enumSkyBlock, BlockPos pos);
}
