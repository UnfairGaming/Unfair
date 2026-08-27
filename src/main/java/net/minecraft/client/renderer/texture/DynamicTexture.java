package net.minecraft.client.renderer.texture;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.rendering.MemoryTracker;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.system.MemoryUtil;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.IntBuffer;
import java.util.concurrent.ExecutionException;

@Getter
public class DynamicTexture extends AbstractTexture {
    static final int BUFFER_SIZE = 2097152;
    static final IntBuffer DATA_BUFFER = MemoryTracker.memAllocInt(BUFFER_SIZE);
    public int[] dynamicTextureData;
    /**
     * width of this icon in pixels
     */
    protected int width;
    /**
     * height of this icon in pixels
     */
    protected int height;
    @Getter
    @Setter
    protected boolean clearable = true;
    @Getter
    @Setter
    protected boolean linear = false;
    private boolean alphaTexture = false;

    public DynamicTexture(BufferedImage bufferedImage) {
        this(bufferedImage.getWidth(), bufferedImage.getHeight(), bufferedImage.getType());
        if (alphaTexture)
            extractAlphaData(bufferedImage);
        else
            bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), this.dynamicTextureData, 0, bufferedImage.getWidth());
        this.updateDynamicTexture();
    }

    public DynamicTexture(BufferedImage bufferedImage, boolean clearable) {
        this(bufferedImage.getWidth(), bufferedImage.getHeight(), bufferedImage.getType());
        if (alphaTexture)
            extractAlphaData(bufferedImage);
        else
            bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), this.dynamicTextureData, 0, bufferedImage.getWidth());
        this.clearable = clearable;
        this.updateDynamicTexture();
    }

    public DynamicTexture(BufferedImage bufferedImage, boolean clearable, boolean linear) {
        this(bufferedImage.getWidth(), bufferedImage.getHeight(), bufferedImage.getType());
        if (alphaTexture)
            extractAlphaData(bufferedImage);
        else
            bufferedImage.getRGB(0, 0, bufferedImage.getWidth(), bufferedImage.getHeight(), this.dynamicTextureData, 0, bufferedImage.getWidth());
        this.clearable = clearable;
        this.linear = linear;
        this.updateDynamicTexture();
    }

    public DynamicTexture(int textureWidth, int textureHeight) {
        this(textureWidth, textureHeight, BufferedImage.TYPE_INT_ARGB);
    }

    public DynamicTexture(int textureWidth, int textureHeight, int imgType) {
        this.width = textureWidth;
        this.height = textureHeight;
        this.dynamicTextureData = new int[textureWidth * textureHeight];

        if (imgType == BufferedImage.TYPE_BYTE_GRAY)
            alphaTexture = true;

        this.allocateTexture(textureWidth, textureHeight);
    }

    private static void runOnMainThreadBlocking(Runnable task) {
        try {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                task.run();
                return null;
            }).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while running texture task", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to run texture task", e);
        }
    }

    private void extractAlphaData(BufferedImage img) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = img.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                dynamicTextureData[y * width + x] = (byte) alpha;
            }
        }
    }

    public void allocateTexture(int textureWidth, int textureHeight) {

        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            runOnMainThreadBlocking(() -> this.allocateTextureImpl(0, textureWidth, textureHeight));
            return;
        }

        this.allocateTextureImpl(0, textureWidth, textureHeight);
    }

    public void allocateTextureImpl(int levels, int width, int height) {

        this.deleteTexture();
        this.bindTexture();

        if (levels >= 0) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, levels);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0.0F);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, (float) levels);
            GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, 0.0F);
        }

        for (int i = 0; i <= levels; ++i) {
            if (alphaTexture) {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, i, GL11.GL_ALPHA, width >> i, height >> i, 0, GL11.GL_ALPHA, GL12.GL_UNSIGNED_BYTE, (IntBuffer) null);
            } else {
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, i, GL11.GL_RGBA, width >> i, height >> i, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer) null);
            }
        }

        GlStateManager.bindTexture(0);

    }

    public void loadTexture(IResourceManager resourceManager) throws IOException {
    }

    @SneakyThrows
    public void updateDynamicTexture() {

        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            runOnMainThreadBlocking(this::updateDynamicTexture);
            return;
        }

        this.bindTexture();

        TextureUtil.setTextureBlurMipmap(isLinear(), false);
        TextureUtil.setTextureClamped(false);

        Minecraft mc = Minecraft.getMinecraft();

        IntBuffer dataBuffer = DATA_BUFFER;
        dataBuffer.clear();
        MemoryUtil.memSet(dataBuffer, 0);
        dataBuffer.clear();

        synchronized (dataBuffer) {
            int i = BUFFER_SIZE / width;
            int j;

            int[] aint = this.dynamicTextureData;

            if (mc.gameSettings.anaglyph) {
                aint = TextureUtil.updateAnaglyph(this.dynamicTextureData);
            }

            for (int k = 0; k < width * height; k += width * j) {
                int l = k / width;
                j = Math.min(i, height - l);
                int i1 = width * j;

                dataBuffer.clear();
                dataBuffer.put(aint, k, i1);
                dataBuffer.flip();

                if (alphaTexture)
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, l, width, j, GL11.GL_ALPHA, GL12.GL_UNSIGNED_BYTE, dataBuffer);
                else
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, l, width, j, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, dataBuffer);
            }
        }

        GlStateManager.bindTexture(0);
    }

    public void clear() {
        if (this.dynamicTextureData != null) {
            java.util.Arrays.fill(this.dynamicTextureData, 0);
        }
    }

    public int[] getTextureData() {
        return this.dynamicTextureData;
    }
}
