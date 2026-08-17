package net.optifine.entity.model;

import net.minecraft.util.ResourceLocation;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public interface IEntityRenderer {
    Class getEntityClass();

    void setEntityClass(Class var1);

    ResourceLocation getLocationTextureCustom();

    void setLocationTextureCustom(ResourceLocation var1);
}
