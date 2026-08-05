package net.minecraft.rendering;

import lombok.experimental.UtilityClass;
import org.lwjgl.system.MemoryUtil;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * @author IzumiiKonata
 * Date: 2025/11/8 20:18
 */
@UtilityClass
public class MemoryTracker {

    public ByteBuffer memAlloc(int size) {
        return MemoryUtil.memAlloc(size);
    }

    public ByteBuffer memRealloc(ByteBuffer ptr, int size) {
        return MemoryUtil.memRealloc(ptr, size);
    }

    public IntBuffer memAllocInt(int size) {
        return MemoryUtil.memAllocInt(size);
    }

    public void memFree(Buffer buffer) {
        if (buffer == null)
            return;

        MemoryUtil.memFree(buffer);
    }
}