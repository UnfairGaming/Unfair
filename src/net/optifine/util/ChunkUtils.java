package net.optifine.util;

import net.minecraft.world.chunk.Chunk;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ChunkUtils {

    public static boolean hasEntities(Chunk chunk) {
        return chunk.hasEntities;
    }

}

