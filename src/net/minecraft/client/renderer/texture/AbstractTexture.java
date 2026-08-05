package net.minecraft.client.renderer.texture;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.optifine.shaders.MultiTexID;
import net.optifine.shaders.ShadersTex;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.ExecutionException;

public abstract class AbstractTexture implements ITextureObject {
    protected int glTextureId = -1;
    protected boolean blur;
    protected boolean mipmap;
    protected boolean blurLast;
    protected boolean mipmapLast;
    public MultiTexID multiTex;

    public void setBlurMipmapDirect(boolean blur, boolean mipmap) {
        this.blur = blur;
        this.mipmap = mipmap;
        int minFilter = -1;
        int magFilter = -1;

        if (blur) {
            minFilter = mipmap ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR;
            magFilter = GL11.GL_LINEAR;
        } else {
            minFilter = mipmap ? GL11.GL_NEAREST_MIPMAP_LINEAR : GL11.GL_NEAREST;
            magFilter = GL11.GL_NEAREST;
        }

        this.bindTexture();
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
    }

    public void setBlurMipmap(boolean blur, boolean mipmap) {

        if (blur != this.blurLast || mipmap != this.mipmapLast) {
            this.setBlurMipmapDirect(blur, mipmap);
        }

        this.blurLast = this.blur;
        this.mipmapLast = this.mipmap;
    }

    public void restoreLastBlurMipmap() {
        this.setBlurMipmapDirect(this.blurLast, this.mipmapLast);
    }

    public int getGlTextureId() {
        if (this.glTextureId == -1) {
            this.glTextureId = TextureUtil.glGenTextures();
        }

        return this.glTextureId;
    }

    @Getter
    private FilterState filterState = FilterState.NEAREST;

    @Override
    public void linearFilter() {
        if (this.filterState != FilterState.LINEAR) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
    }

    @Override
    public void nearestFilter() {
        if (this.filterState != FilterState.NEAREST) {
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }
    }

    public void deleteGlTexture() {
        ShadersTex.deleteTextures(this, this.glTextureId);

        if (this.glTextureId != -1) {
            TextureUtil.deleteTexture(this.glTextureId);
            this.glTextureId = -1;
        }
    }

    public MultiTexID getMultiTexID() {
        return ShadersTex.getMultiTexID(this);
    }

    public void bindTexture() {

        if (this.getGlTextureId() == -1) {
            return;
        }

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.getGlTextureId());

    }

    public void deleteTexture() {

        if (this.getGlTextureId() == -1) {
            return;
        }

        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            GlStateManager.deleteTexture(this.getGlTextureId());
        } else {
            try {
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    GL11.glDeleteTextures(this.getGlTextureId());
                    return null;
                }).get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while deleting texture", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Failed to delete texture", e);
            }
        }

    }
}
