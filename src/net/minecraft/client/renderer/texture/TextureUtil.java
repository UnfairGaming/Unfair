package net.minecraft.client.renderer.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.rendering.MemoryTracker;
import net.minecraft.src.Config;
import net.minecraft.util.ResourceLocation;
import net.optifine.Mipmaps;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class TextureUtil {

    private static final Logger logger = LogManager.getLogger();
    private static final IntBuffer dataBuffer = MemoryTracker.memAllocInt(4194304);
    public static final DynamicTexture missingTexture = new DynamicTexture(16, 16);
    public static final int[] missingTextureData = missingTexture.getTextureData();
    private static final int[] mipmapBuffer;
    public static int glGenTextures() {
        return GlStateManager.generateTexture();
    }

    public static void deleteTexture(int textureId) {
        GlStateManager.deleteTexture(textureId);
    }

    public static int uploadTextureImage(int p_110987_0_, BufferedImage p_110987_1_) {
        return uploadTextureImageAllocate(p_110987_0_, p_110987_1_, false, false);
    }

    public static void uploadTexture(int textureId, int[] p_110988_1_, int p_110988_2_, int p_110988_3_) {
        bindTexture(textureId);
        uploadTextureSub(0, p_110988_1_, p_110988_2_, p_110988_3_, 0, 0, false, false, false);
    }

    public static int[][] generateMipmapData(int p_147949_0_, int p_147949_1_, int[][] p_147949_2_) {
        int[][] aint = new int[p_147949_0_ + 1][];
        aint[0] = p_147949_2_[0];

        if (p_147949_0_ > 0) {
            boolean flag = false;

            for (int i = 0; i < p_147949_2_[0].length; ++i) {
                if (p_147949_2_[0][i] >> 24 == 0) {
                    flag = true;
                    break;
                }
            }

            for (int l1 = 1; l1 <= p_147949_0_; ++l1) {
                if (p_147949_2_[l1] != null) {
                    aint[l1] = p_147949_2_[l1];
                } else {
                    int[] aint1 = aint[l1 - 1];
                    int[] aint2 = new int[aint1.length >> 2];
                    int j = p_147949_1_ >> l1;
                    int k = aint2.length / j;
                    int l = j << 1;

                    for (int i1 = 0; i1 < j; ++i1) {
                        for (int j1 = 0; j1 < k; ++j1) {
                            int k1 = 2 * (i1 + j1 * l);
                            aint2[i1 + j1 * j] = blendColors(aint1[k1], aint1[k1 + 1], aint1[k1 + l], aint1[k1 + 1 + l], flag);
                        }
                    }

                    aint[l1] = aint2;
                }
            }
        }

        return aint;
    }

    private static int blendColors(int p_147943_0_, int p_147943_1_, int p_147943_2_, int p_147943_3_, boolean p_147943_4_) {
        return Mipmaps.alphaBlend(p_147943_0_, p_147943_1_, p_147943_2_, p_147943_3_);
    }

    private static int blendColorComponent(int p_147944_0_, int p_147944_1_, int p_147944_2_, int p_147944_3_, int p_147944_4_) {
        float f = (float) Math.pow((float) (p_147944_0_ >> p_147944_4_ & 255) * 0.003921568627451F, 2.2D);
        float f1 = (float) Math.pow((float) (p_147944_1_ >> p_147944_4_ & 255) * 0.003921568627451F, 2.2D);
        float f2 = (float) Math.pow((float) (p_147944_2_ >> p_147944_4_ & 255) * 0.003921568627451F, 2.2D);
        float f3 = (float) Math.pow((float) (p_147944_3_ >> p_147944_4_ & 255) * 0.003921568627451F, 2.2D);
        float f4 = (float) Math.pow((double) (f + f1 + f2 + f3) * 0.25D, 0.45454545454545453D);
        return (int) ((double) f4 * 255.0D);
    }

    public static void uploadTextureMipmap(int[][] p_147955_0_, int p_147955_1_, int p_147955_2_, int p_147955_3_, int p_147955_4_, boolean p_147955_5_, boolean p_147955_6_) {
        for (int i = 0; i < p_147955_0_.length; ++i) {
            int[] aint = p_147955_0_[i];
            uploadTextureSub(i, aint, p_147955_1_ >> i, p_147955_2_ >> i, p_147955_3_ >> i, p_147955_4_ >> i, p_147955_5_, p_147955_6_, p_147955_0_.length > 1);
        }
    }

    private static void uploadTextureSub(int level, int[] texData, int width, int height, int xOffset, int yOffset, boolean linear, boolean clamp, boolean mipmap) {
        if (texData == null || width <= 0 || height <= 0) {
            return;
        }

        int i = 4194304 / width;
        setTextureBlurMipmap(linear, mipmap);
        setTextureClamped(clamp);
        int j;

        synchronized (dataBuffer) {
            for (int k = 0; k < width * height; k += width * j) {
                int l = k / width;
                j = Math.min(i, height - l);
                int i1 = width * j;
                copyToBufferPos(texData, k, i1, dataBuffer);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, xOffset, yOffset + l, width, j, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, dataBuffer);
            }
        }
    }

    public static int uploadTextureImageAllocate(int p_110989_0_, BufferedImage p_110989_1_, boolean p_110989_2_, boolean p_110989_3_) {
        allocateTexture(p_110989_0_, p_110989_1_.getWidth(), p_110989_1_.getHeight());
        return uploadTextureImageSub(p_110989_0_, p_110989_1_, 0, 0, p_110989_2_, p_110989_3_);
    }

    public static void allocateTexture(int textureId, int width, int height) {
        allocateTextureImpl(textureId, 0, width, height);
    }

    public static void allocateTextureImpl(int textureId, int levels, int width, int height) {

        deleteTexture(textureId);
        bindTexture(textureId);

        if (levels >= 0) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0.0F);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, (float) levels);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0F);
        }

        for (int i = 0; i <= levels; ++i) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, i, GL11.GL_RGBA, width >> i, height >> i, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (ByteBuffer) null);
        }
    }

    public static int uploadTextureImageSub(int textureId, BufferedImage p_110995_1_, int p_110995_2_, int p_110995_3_, boolean p_110995_4_, boolean p_110995_5_) {
        bindTexture(textureId);
        uploadTextureImageSubImpl(p_110995_1_, p_110995_2_, p_110995_3_, p_110995_4_, p_110995_5_);
        return textureId;
    }

    static final int[] textureData = new int[4194304];

    private static void uploadTextureImageSubImpl(BufferedImage img, int xOffset, int yOffset, boolean blur, boolean clamp) {
        int width = img.getWidth();
        int height = img.getHeight();
        int k = 4194304 / width;
        setTextureBlurred(blur);
        setTextureClamped(clamp);
        int[] sourceData = null;

        if (img.getType() == BufferedImage.TYPE_INT_ARGB
                && img.getRaster().getDataBuffer() instanceof DataBufferInt dataBufferInt
                && img.getRaster().getSampleModel() instanceof java.awt.image.SinglePixelPackedSampleModel sampleModel
                && sampleModel.getScanlineStride() == width) {
            sourceData = dataBufferInt.getData();
        }

        synchronized (dataBuffer) {
            for (int l = 0; l < width * height; l += width * k) {
                int i1 = l / width;
                int j1 = Math.min(k, height - i1);
                int k1 = width * j1;
                if (sourceData != null) {
                    copyToBufferPos(sourceData, i1 * width, k1, dataBuffer);
                } else {
                    img.getRGB(0, i1, width, j1, textureData, 0, width);
                    copyToBuffer(textureData, k1, dataBuffer);
                }
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, xOffset, yOffset + i1, width, j1, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, dataBuffer);
            }
        }
    }

    public static void setTextureClamped(boolean clamp) {
        if (clamp) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        } else {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        }
    }

    private static void setTextureBlurred(boolean p_147951_0_) {
        setTextureBlurMipmap(p_147951_0_, false);
    }

    public static void setTextureBlurMipmap(boolean linear, boolean mipmap) {
        if (linear) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        } else {
            int i = Config.getMipmapType();
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mipmap ? i : GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
    }

    private static void copyToBuffer(int[] p_110990_0_, int p_110990_1_, IntBuffer dataBuffer) {
        copyToBufferPos(p_110990_0_, 0, p_110990_1_, dataBuffer);
    }

    private static void copyToBufferPos(int[] p_110994_0_, int p_110994_1_, int p_110994_2_, IntBuffer dataBuffer) {
        int[] aint = p_110994_0_;

        if (Minecraft.getMinecraft().gameSettings.anaglyph) {
            aint = updateAnaglyph(p_110994_0_);
        }

        dataBuffer.clear();
        dataBuffer.put(aint, p_110994_1_, p_110994_2_);
        dataBuffer.position(0).limit(p_110994_2_);
    }

    static void bindTexture(int p_94277_0_) {
        GlStateManager.bindTexture(p_94277_0_);
    }

    public static int[] readImageData(IResourceManager resourceManager, ResourceLocation imageLocation) throws IOException {
        try (NativeBackedImage bufferedimage = readBufferedImage(resourceManager.getResource(imageLocation).getInputStream());) {
            if (bufferedimage == null) {
                return null;
            } else {
                int i = bufferedimage.getWidth();
                int j = bufferedimage.getHeight();
                int[] aint = new int[i * j];
                bufferedimage.getRGB(0, 0, i, j, aint, 0, i);
                return aint;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static NativeBackedImage readBufferedImage(InputStream imageStream)  {
        if (imageStream == null) {
            return null;
        } else {
            NativeBackedImage bufferedimage;

            try {
                bufferedimage = NativeBackedImage.make(imageStream);
            } finally {
                IOUtils.closeQuietly(imageStream);
            }

            return bufferedimage;
        }
    }

    public static NativeBackedImage readBufferedImageNoClosing(InputStream imageStream)  {
        if (imageStream == null) {
            return null;
        } else {
            NativeBackedImage bufferedimage;

            try {
                bufferedimage = NativeBackedImage.makeNoClosing(imageStream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return bufferedimage;
        }
    }

    public static int[] updateAnaglyph(int[] p_110985_0_) {
        int[] aint = new int[p_110985_0_.length];

        for (int i = 0; i < p_110985_0_.length; ++i) {
            aint[i] = anaglyphColor(p_110985_0_[i]);
        }

        return aint;
    }

    public static int anaglyphColor(int p_177054_0_) {
        int i = p_177054_0_ >> 24 & 255;
        int j = p_177054_0_ >> 16 & 255;
        int k = p_177054_0_ >> 8 & 255;
        int l = p_177054_0_ & 255;
        int i1 = (j * 30 + k * 59 + l * 11) / 100;
        int j1 = (j * 30 + k * 70) / 100;
        int k1 = (j * 30 + l * 70) / 100;
        return i << 24 | i1 << 16 | j1 << 8 | k1;
    }

    public static void processPixelValues(int[] p_147953_0_, int p_147953_1_, int p_147953_2_) {
        int[] aint = new int[p_147953_1_];
        int i = p_147953_2_ / 2;

        for (int j = 0; j < i; ++j) {
            System.arraycopy(p_147953_0_, j * p_147953_1_, aint, 0, p_147953_1_);
            System.arraycopy(p_147953_0_, (p_147953_2_ - 1 - j) * p_147953_1_, p_147953_0_, j * p_147953_1_, p_147953_1_);
            System.arraycopy(aint, 0, p_147953_0_, (p_147953_2_ - 1 - j) * p_147953_1_, p_147953_1_);
        }
    }

    static {
        int[] aint = new int[]{-524040, -524040, -524040, -524040, -524040, -524040, -524040, -524040};
        int[] aint1 = new int[]{-16777216, -16777216, -16777216, -16777216, -16777216, -16777216, -16777216, -16777216};
        int k = aint.length;

        missingTexture.setClearable(false);

        for (int l = 0; l < 16; ++l) {
            System.arraycopy(l < k ? aint : aint1, 0, missingTextureData, 16 * l, k);
            System.arraycopy(l < k ? aint1 : aint, 0, missingTextureData, 16 * l + k, k);
        }

        missingTexture.updateDynamicTexture();
        missingTexture.clear();
        mipmapBuffer = new int[4];
    }
}
