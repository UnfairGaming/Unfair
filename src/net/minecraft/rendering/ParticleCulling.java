package net.minecraft.rendering;

import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.culling.ICamera;

/**
 * @author IzumiiKonata
 * @since 2024/10/5 22:33
 */
public class ParticleCulling {

    public static ICamera camera;

    public static boolean shouldRender(EntityFX entityFX) {
        return entityFX != null && (camera == null || (entityFX).patcher$getCullState() > -1);
    }

}
