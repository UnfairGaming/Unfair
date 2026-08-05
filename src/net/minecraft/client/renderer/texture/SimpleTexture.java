package net.minecraft.client.renderer.texture;

import net.minecraft.rendering.MemoryTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.src.Config;
import net.minecraft.util.ResourceLocation;
import net.optifine.EmissiveTextures;
import net.optifine.shaders.ShadersTex;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;

public class SimpleTexture extends AbstractTexture {
    private static final Logger logger = LogManager.getLogger("SimpleTexture");
    protected final ResourceLocation textureLocation;
    public ResourceLocation locationEmissive;
    public boolean isEmissive;

    public SimpleTexture(ResourceLocation textureResourceLocation) {
        this.textureLocation = textureResourceLocation;
    }

    public void loadTexture(IResourceManager resourceManager) throws IOException {
        this.deleteGlTexture();
        InputStream inputstream = null;

        try {
            IResource iresource = resourceManager.getResource(this.textureLocation);
            inputstream = iresource.getInputStream();
            try (NativeBackedImage bufferedimage = TextureUtil.readBufferedImage(inputstream);) {
                boolean flag = false;
                boolean flag1 = false;

                if (iresource.hasMetadata()) {
                    try {
                        TextureMetadataSection texturemetadatasection = iresource.getMetadata("texture");

                        if (texturemetadatasection != null) {
                            flag = texturemetadatasection.getTextureBlur();
                            flag1 = texturemetadatasection.getTextureClamp();
                        }
                    } catch (RuntimeException runtimeexception) {
                        logger.warn("Failed reading metadata of: " + this.textureLocation, runtimeexception);
                    }
                }

                if (Config.isShaders()) {
                    ShadersTex.loadSimpleTexture(this.getGlTextureId(), bufferedimage, flag, flag1, resourceManager, this.textureLocation, this.getMultiTexID());
                } else {
                    this.uploadTexture(this.getGlTextureId(), bufferedimage, flag, flag1);
                }

                if (EmissiveTextures.isActive()) {
                    EmissiveTextures.loadTexture(this.textureLocation, this);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        } finally {
            if (inputstream != null) {
                inputstream.close();
            }
        }
    }

    private void uploadTexture(int texID, BufferedImage bufferedimage, boolean linear, boolean clamp) {
        TextureUtil.allocateTexture(this.getGlTextureId(), bufferedimage.getWidth(), bufferedimage.getHeight());

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.getGlTextureId());
        TextureUtil.setTextureBlurMipmap(linear, false);
        TextureUtil.setTextureClamped(clamp);

        Minecraft mc = Minecraft.getMinecraft();

        int width = bufferedimage.getWidth();
        int height = bufferedimage.getHeight();
        int[] dynamicTextureData = new int[width * height];
        bufferedimage.getRGB(0, 0, bufferedimage.getWidth(), bufferedimage.getHeight(), dynamicTextureData, 0, bufferedimage.getWidth());

        if (dynamicTextureData.length < 4194304) {

            int padd = 0;

            if (dynamicTextureData.length % 4 != 0) {
                padd = (4 - dynamicTextureData.length % 4);
            }

            IntBuffer buffer = MemoryTracker.memAllocInt(dynamicTextureData.length + padd);
            buffer.clear();
            buffer.put(dynamicTextureData, 0, dynamicTextureData.length);
            buffer.flip();

            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buffer);

            MemoryTracker.memFree(buffer);

        } else {

            // creates a 16 MB buffer
            IntBuffer dataBuffer = MemoryTracker.memAllocInt(4194304);

            int i = 4194304 / width;
            int j;

            int[] aint = dynamicTextureData;

            if (mc.gameSettings.anaglyph) {
                aint = TextureUtil.updateAnaglyph(dynamicTextureData);
            }

            for (int k = 0; k < width * height; k += width * j) {
                int l = k / width;
                j = Math.min(i, height - l);
                int i1 = width * j;

                dataBuffer.clear();
                dataBuffer.put(aint, k, i1);
                dataBuffer.flip();

                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, l, width, j, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, dataBuffer);
            }

            MemoryTracker.memFree(dataBuffer);

        }

        GL11.glFlush();
    }
}
