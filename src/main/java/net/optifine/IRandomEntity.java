package net.optifine;

import net.minecraft.util.BlockPos;
import net.minecraft.world.biome.BiomeGenBase;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public interface IRandomEntity {
    int getId();

    BlockPos getSpawnPosition();

    BiomeGenBase getSpawnBiome();

    String getName();

    int getHealth();

    int getMaxHealth();
}
