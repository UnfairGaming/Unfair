package net.minecraft.rendering;

import net.minecraft.client.shader.Framebuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * @author IzumiiKonata
 * @since 2024/9/21 21:53
 */
public class FramebufferCaching {

    private static final List<Framebuffer> LIST = new ArrayList<>();
    public static Framebuffer render2DNormalBuffer;

    public static Framebuffer getOverridingFramebuffer() {

        if (LIST.isEmpty())
            return null;

        return LIST.get(0);
    }

    public static void setOverridingFramebuffer(Framebuffer buffer) {
        LIST.add(0, buffer);
    }

    public static void removeCurrentlyBinding() {
        LIST.remove(0);
    }
}
