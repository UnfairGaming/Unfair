package net.optifine.shaders;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.rendering.MemoryTracker;
import net.minecraft.src.Config;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class ShadersTex {
    public static Map<Integer, MultiTexID> multiTexMap = new HashMap();

    public static int roundUpPOT(int x) {
        int i = x - 1;
        i = i | i >> 1;
        i = i | i >> 2;
        i = i | i >> 4;
        i = i | i >> 8;
        i = i | i >> 16;
        return i + 1;
    }

    public static int log2(int x) {
        int i = 0;

        if ((x & -65536) != 0) {
            i += 16;
            x >>= 16;
        }

        if ((x & 65280) != 0) {
            i += 8;
            x >>= 8;
        }

        if ((x & 240) != 0) {
            i += 4;
            x >>= 4;
        }

        if ((x & 6) != 0) {
            i += 2;
            x >>= 2;
        }

        if ((x & 2) != 0) {
            ++i;
        }

        return i;
    }

    public static int[] createAIntImage(int size, int color) {
        int[] aint = new int[size * 3];
        Arrays.fill(aint, 0, size, color);
        Arrays.fill(aint, size, size * 2, -8421377);
        Arrays.fill(aint, size * 2, size * 3, 0);
        return aint;
    }

    public static MultiTexID getMultiTexID(AbstractTexture tex) {
        MultiTexID multitexid = tex.multiTex;

        if (multitexid == null) {
            int i = tex.getGlTextureId();
            multitexid = multiTexMap.get(i);

            if (multitexid == null) {
                multitexid = new MultiTexID(i, TextureUtil.glGenTextures(), TextureUtil.glGenTextures());
                multiTexMap.put(i, multitexid);
            }

            tex.multiTex = multitexid;
        }

        return multitexid;
    }

    public static void deleteTextures(AbstractTexture atex, int texid) {
        MultiTexID multitexid = atex.multiTex;

        if (multitexid != null) {
            atex.multiTex = null;
            multiTexMap.remove(multitexid.base);
            GlStateManager.deleteTexture(multitexid.norm);
            GlStateManager.deleteTexture(multitexid.spec);

            if (multitexid.base != texid) {
                SMCLog.warning("Error : MultiTexID.base mismatch: " + multitexid.base + ", texid: " + texid);
                GlStateManager.deleteTexture(multitexid.base);
            }
        }
    }

    public static void bindNSTextures(int normTex, int specTex) {
        if (Shaders.isRenderingWorld && GlStateManager.getActiveTextureUnit() == 33984) {
            GlStateManager.setActiveTexture(33986);
            GlStateManager.bindTexture(normTex);
            GlStateManager.setActiveTexture(33987);
            GlStateManager.bindTexture(specTex);
            GlStateManager.setActiveTexture(33984);
        }
    }

    public static void bindNSTextures(MultiTexID multiTex) {
        bindNSTextures(multiTex.norm, multiTex.spec);
    }

    public static void bindTextures(MultiTexID multiTex) {
        if (Shaders.isRenderingWorld && GlStateManager.getActiveTextureUnit() == 33984) {
            if (Shaders.configNormalMap) {
                GlStateManager.setActiveTexture(33986);
                GlStateManager.bindTexture(multiTex.norm);
            }

            if (Shaders.configSpecularMap) {
                GlStateManager.setActiveTexture(33987);
                GlStateManager.bindTexture(multiTex.spec);
            }

            GlStateManager.setActiveTexture(33984);
        }

        GlStateManager.bindTexture(multiTex.base);
    }

    public static void bindTexture(ITextureObject tex) {
        int i = tex.getGlTextureId();
        bindTextures(tex.getMultiTexID());

        if (GlStateManager.getActiveTextureUnit() == 33984) {
            int j = Shaders.atlasSizeX;
            int k = Shaders.atlasSizeY;

            if (tex instanceof TextureMap) {
                Shaders.atlasSizeX = ((TextureMap) tex).atlasWidth;
                Shaders.atlasSizeY = ((TextureMap) tex).atlasHeight;
            } else {
                Shaders.atlasSizeX = 0;
                Shaders.atlasSizeY = 0;
            }

            if (Shaders.atlasSizeX != j || Shaders.atlasSizeY != k) {
                Shaders.uniform_atlasSize.setValue(Shaders.atlasSizeX, Shaders.atlasSizeY);
            }
        }
    }
    public static void updateDynTexSubImage1(int[] src, int width, int height, int posX, int posY, int page) {
        int i = width * height;
        IntBuffer intbuffer = MemoryTracker.memAllocInt(roundUpPOT(i) * 4);
        intbuffer.clear();
        int j = page * i;

        if (src.length >= j + i) {
            intbuffer.put(src, j, i).position(0).limit(i);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, posX, posY, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intbuffer);
        }

        MemoryTracker.memFree(intbuffer);
    }

    public static ITextureObject createDefaultTexture() {
        DynamicTexture dynamictexture = new DynamicTexture(1, 1);
        dynamictexture.getTextureData()[0] = -1;
        dynamictexture.updateDynamicTexture();
        return dynamictexture;
    }

    public static void allocateTextureMap(int texID, int mipmapLevels, int width, int height, Stitcher stitcher, TextureMap tex) {
        SMCLog.info("閸掑棝鍘ょ痪鍦倞閺勭姴鐨?" + mipmapLevels + " " + width + " " + height + " ");
        tex.atlasWidth = width;
        tex.atlasHeight = height;
        MultiTexID multitexid = getMultiTexID(tex);
        TextureUtil.allocateTextureImpl(multitexid.base, mipmapLevels, width, height);

        if (Shaders.configNormalMap) {
            TextureUtil.allocateTextureImpl(multitexid.norm, mipmapLevels, width, height);
        }

        if (Shaders.configSpecularMap) {
            TextureUtil.allocateTextureImpl(multitexid.spec, mipmapLevels, width, height);
        }

        GlStateManager.bindTexture(texID);
    }

    public static void uploadTexSubForLoadAtlas(TextureMap textureMap, String iconName, int[][] data, int width, int height, int xoffset, int yoffset, boolean linear, boolean clamp) {
        MultiTexID multitexid = textureMap.multiTex;
        TextureUtil.uploadTextureMipmap(data, width, height, xoffset, yoffset, linear, clamp);
        boolean flag = false;

        if (Shaders.configNormalMap) {
            int[][] aint = readImageAndMipmaps(textureMap, iconName + "_n", width, height, data.length, flag, -8421377);
            GlStateManager.bindTexture(multitexid.norm);
            TextureUtil.uploadTextureMipmap(aint, width, height, xoffset, yoffset, linear, clamp);
        }

        if (Shaders.configSpecularMap) {
            int[][] aint1 = readImageAndMipmaps(textureMap, iconName + "_s", width, height, data.length, flag, 0);
            GlStateManager.bindTexture(multitexid.spec);
            TextureUtil.uploadTextureMipmap(aint1, width, height, xoffset, yoffset, linear, clamp);
        }

        GlStateManager.bindTexture(multitexid.base);
    }

    public static int[][] readImageAndMipmaps(TextureMap updatingTextureMap, String name, int width, int height, int numLevels, boolean border, int defColor) {
        MultiTexID multitexid = updatingTextureMap.multiTex;
        int[][] aint = new int[numLevels][];
        int[] aint1;
        aint[0] = aint1 = new int[width * height];
        boolean flag = false;
        BufferedImage bufferedimage = readImage(updatingTextureMap.completeResourceLocation(ResourceLocation.of(name)));

        if (bufferedimage != null) {
            int i = bufferedimage.getWidth();
            int j = bufferedimage.getHeight();

            if (i + (border ? 16 : 0) == width) {
                flag = true;
                bufferedimage.getRGB(0, 0, i, i, aint1, 0, i);
            }
        }

        if (bufferedimage instanceof NativeBackedImage) {
            ((NativeBackedImage) bufferedimage).close();
        }

        if (!flag) {
            Arrays.fill(aint1, defColor);
        }

        GlStateManager.bindTexture(multitexid.spec);
        aint = genMipmapsSimple(aint.length - 1, width, aint);
        return aint;
    }

    public static BufferedImage readImage(ResourceLocation resLoc) {
        try {
            if (!Config.hasResource(resLoc)) {
                return null;
            } else {
                InputStream inputstream = Config.getResourceStream(resLoc);

                if (inputstream == null) {
                    return null;
                } else {
                    BufferedImage bufferedimage = NativeBackedImage.make(inputstream);
                    inputstream.close();
                    return bufferedimage;
                }
            }
        } catch (IOException var3) {
            return null;
        }
    }

    public static int[][] genMipmapsSimple(int maxLevel, int width, int[][] data) {
        for (int i = 1; i <= maxLevel; ++i) {
            if (data[i] == null) {
                int j = width >> i;
                int k = j * 2;
                int[] aint = data[i - 1];
                int[] aint1 = data[i] = new int[j * j];

                for (int i1 = 0; i1 < j; ++i1) {
                    for (int l = 0; l < j; ++l) {
                        int j1 = i1 * 2 * k + l * 2;
                        aint1[i1 * j + l] = blend4Simple(aint[j1], aint[j1 + 1], aint[j1 + k], aint[j1 + k + 1]);
                    }
                }
            }
        }

        return data;
    }

    public static int blend4Simple(int c0, int c1, int c2, int c3) {
        int i = ((c0 >>> 24 & 255) + (c1 >>> 24 & 255) + (c2 >>> 24 & 255) + (c3 >>> 24 & 255) + 2) / 4 << 24 | ((c0 >>> 16 & 255) + (c1 >>> 16 & 255) + (c2 >>> 16 & 255) + (c3 >>> 16 & 255) + 2) / 4 << 16 | ((c0 >>> 8 & 255) + (c1 >>> 8 & 255) + (c2 >>> 8 & 255) + (c3 >>> 8 & 255) + 2) / 4 << 8 | ((c0 >>> 0 & 255) + (c1 >>> 0 & 255) + (c2 >>> 0 & 255) + (c3 >>> 0 & 255) + 2) / 4 << 0;
        return i;
    }

    public static void setupTexture(MultiTexID multiTex, int[] src, int width, int height, boolean linear, boolean clamp) {
        int i = linear ? 9729 : 9728;
        int j = clamp ? 33071 : 10497;
        int k = width * height;
        IntBuffer intbuffer = MemoryTracker.memAllocInt(roundUpPOT(k) * 4);
        intbuffer.clear();
        intbuffer.put(src, 0, k).position(0).limit(k);
        GlStateManager.bindTexture(multiTex.base);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intbuffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, i);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, i);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, j);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, j);
        intbuffer.put(src, k, k).position(0).limit(k);
        GlStateManager.bindTexture(multiTex.norm);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intbuffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, i);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, i);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, j);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, j);
        intbuffer.put(src, k * 2, k).position(0).limit(k);
        GlStateManager.bindTexture(multiTex.spec);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intbuffer);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, i);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, i);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, j);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, j);
        GlStateManager.bindTexture(multiTex.base);

        MemoryTracker.memFree(intbuffer);
    }

    public static ResourceLocation getNSMapResourceLocation(ResourceLocation location, String mapName) {
        if (location == null) {
            return null;
        } else {
            String s = location.getResourcePath();
            String[] astring = s.split(".png");
            String s1 = astring[0];
            return ResourceLocation.of(location.getResourceDomain(), s1 + "_" + mapName + ".png");
        }
    }

    public static void loadNSMap(IResourceManager manager, ResourceLocation location, int width, int height, int[] aint) {
        if (Shaders.configNormalMap) {
            loadNSMap1(manager, getNSMapResourceLocation(location, "n"), width, height, aint, width * height, -8421377);
        }

        if (Shaders.configSpecularMap) {
            loadNSMap1(manager, getNSMapResourceLocation(location, "s"), width, height, aint, width * height * 2, 0);
        }
    }

    private static void loadNSMap1(IResourceManager manager, ResourceLocation location, int width, int height, int[] aint, int offset, int defaultColor) {
        if (!loadNSMapFile(manager, location, width, height, aint, offset)) {
            Arrays.fill(aint, offset, offset + width * height, defaultColor);
        }
    }

    private static boolean loadNSMapFile(IResourceManager manager, ResourceLocation location, int width, int height, int[] aint, int offset) {
        if (location == null) {
            return false;
        } else {
            try {
                IResource iresource = manager.getResource(location);
                NativeBackedImage bufferedimage = NativeBackedImage.make(iresource.getInputStream());

                if (bufferedimage == null) {
                    return false;
                } else if (bufferedimage.getWidth() == width && bufferedimage.getHeight() == height) {
                    bufferedimage.getRGB(0, 0, width, height, aint, offset, width);
                    bufferedimage.close();
                    return true;
                } else {
                    bufferedimage.close();
                    return false;
                }
            } catch (IOException var8) {
                return false;
            }
        }
    }

    public static int loadSimpleTexture(int textureID, BufferedImage bufferedimage, boolean linear, boolean clamp, IResourceManager resourceManager, ResourceLocation location, MultiTexID multiTex) {
        int i = bufferedimage.getWidth();
        int j = bufferedimage.getHeight();
        int k = i * j;
        int[] aint = new int[roundUpPOT(k * 3)];
        bufferedimage.getRGB(0, 0, i, j, aint, 0, i);
        loadNSMap(resourceManager, location, i, j, aint);
        setupTexture(multiTex, aint, i, j, linear, clamp);
        aint = null;
        return textureID;
    }

    public static void updateTextureMinMagFilter() {
        TextureManager texturemanager = Minecraft.getMinecraft().getTextureManager();
        ITextureObject itextureobject = texturemanager.getTexture(TextureMap.locationBlocksTexture);

        if (itextureobject != null) {
            MultiTexID multitexid = itextureobject.getMultiTexID();
            GlStateManager.bindTexture(multitexid.base);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, Shaders.texMinFilValue[Shaders.configTexMinFilB]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, Shaders.texMagFilValue[Shaders.configTexMagFilB]);
            GlStateManager.bindTexture(multitexid.norm);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, Shaders.texMinFilValue[Shaders.configTexMinFilN]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, Shaders.texMagFilValue[Shaders.configTexMagFilN]);
            GlStateManager.bindTexture(multitexid.spec);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, Shaders.texMinFilValue[Shaders.configTexMinFilS]);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, Shaders.texMagFilValue[Shaders.configTexMagFilS]);
            GlStateManager.bindTexture(0);
        }
    }

}
