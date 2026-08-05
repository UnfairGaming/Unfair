package net.optifine.shaders;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class DefaultTexture extends AbstractTexture
{
    public DefaultTexture()
    {
        this.loadTexture((IResourceManager)null);
    }

    public void loadTexture(IResourceManager resourcemanager)
    {
        int[] aint = ShadersTex.createAIntImage(1, -1);
        ShadersTex.setupTexture(this.getMultiTexID(), aint, 1, 1, false, false);
    }
}
