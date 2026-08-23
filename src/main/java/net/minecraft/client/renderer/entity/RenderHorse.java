package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelHorse;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.LayeredTexture;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.util.ResourceLocation;

import java.util.Map;

public class RenderHorse extends RenderLiving<EntityHorse> {
    private static final Map<String, ResourceLocation> field_110852_a = Maps.newHashMap();
    private static final ResourceLocation whiteHorseTextures = ResourceLocation.of("textures/entity/horse/horse_white.png");
    private static final ResourceLocation creamyHorseTextures = ResourceLocation.of("textures/entity/horse/horse_creamy.png");
    private static final ResourceLocation chestnutHorseTextures = ResourceLocation.of("textures/entity/horse/horse_chestnut.png");
    private static final ResourceLocation brownHorseTextures = ResourceLocation.of("textures/entity/horse/horse_brown.png");
    private static final ResourceLocation blackHorseTextures = ResourceLocation.of("textures/entity/horse/horse_black.png");
    private static final ResourceLocation grayHorseTextures = ResourceLocation.of("textures/entity/horse/horse_gray.png");
    private static final ResourceLocation darkBrownHorseTextures = ResourceLocation.of("textures/entity/horse/horse_darkbrown.png");
    private static final ResourceLocation muleTextures = ResourceLocation.of("textures/entity/horse/mule.png");
    private static final ResourceLocation donkeyTextures = ResourceLocation.of("textures/entity/horse/donkey.png");
    private static final ResourceLocation zombieHorseTextures = ResourceLocation.of("textures/entity/horse/horse_zombie.png");
    private static final ResourceLocation skeletonHorseTextures = ResourceLocation.of("textures/entity/horse/horse_skeleton.png");

    public RenderHorse(RenderManager rendermanagerIn, ModelHorse model, float shadowSizeIn) {
        super(rendermanagerIn, model, shadowSizeIn);
    }

    /**
     * Allows the render to do any OpenGL state modifications necessary before the model is rendered. Args:
     * entityLiving, partialTickTime
     */
    protected void preRenderCallback(EntityHorse entitylivingbaseIn, float partialTickTime) {
        float f = 1.0F;
        int i = entitylivingbaseIn.getHorseType();

        if (i == 1) {
            f *= 0.87F;
        } else if (i == 2) {
            f *= 0.92F;
        }

        GlStateManager.scale(f, f, f);
        super.preRenderCallback(entitylivingbaseIn, partialTickTime);
    }

    /**
     * Returns the location of an entity's texture. Doesn't seem to be called unless you call Render.bindEntityTexture.
     */
    protected ResourceLocation getEntityTexture(EntityHorse entity) {
        return this.getBaseHorseTexture(entity);
    }

    private ResourceLocation getBaseHorseTexture(EntityHorse horse) {
        if (horse.getHorseType() != 0) {
            return switch (horse.getHorseType()) {
                case 1 -> donkeyTextures;
                case 2 -> muleTextures;
                case 3 -> zombieHorseTextures;
                case 4 -> skeletonHorseTextures;
                default -> whiteHorseTextures;
            };
        }

        return switch (horse.getHorseVariant() & 255) {
            case 1 -> creamyHorseTextures;
            case 2 -> chestnutHorseTextures;
            case 3 -> brownHorseTextures;
            case 4 -> blackHorseTextures;
            case 5 -> grayHorseTextures;
            case 6 -> darkBrownHorseTextures;
            default -> whiteHorseTextures;
        };
    }

    private ResourceLocation func_110848_b(EntityHorse horse) {
        String s = horse.getHorseTexture();

        if (!horse.func_175507_cI()) {
            return null;
        } else {
            ResourceLocation resourcelocation = field_110852_a.get(s);

            if (resourcelocation == null) {
                resourcelocation = ResourceLocation.of(s);
                boolean loaded = Minecraft.getMinecraft().getTextureManager().loadTexture(
                        resourcelocation, new LayeredTexture(horse.getVariantTexturePaths())
                );
                if (!loaded) {
                    return null;
                }
                field_110852_a.put(s, resourcelocation);
            }

            return resourcelocation;
        }
    }
}
