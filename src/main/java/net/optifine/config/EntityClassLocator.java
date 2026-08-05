package net.optifine.config;

import net.minecraft.util.ResourceLocation;
import net.optifine.util.EntityUtils;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class EntityClassLocator implements IObjectLocator
{
    public Object getObject(ResourceLocation loc)
    {
        Class oclass = EntityUtils.getEntityClassByName(loc.getResourcePath());
        return oclass;
    }
}
