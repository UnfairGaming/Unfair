package net.minecraft.rendering.image;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import net.optifine.util.TextureUtils;
import org.lwjgl.opengl.GL11;

public class Image {
    public enum Type {
        NoColor, Normal
    }

    public static void drawNearest(ResourceLocation img, double x, double y, double width, double height, Type type) {
        if (type == Type.Normal) {
            GlStateManager.color(1, 1, 1, 1);
        }

        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.enableTexture2D();
        // GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        ITextureObject textureObj = Minecraft.getMinecraft().getTextureManager().getTexture(img);
        if (textureObj != null && textureObj != TextureUtil.missingTexture) {
            TextureUtils.bindTexture(textureObj.getGlTextureId());
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, width, height);
        } else if (textureObj != TextureUtil.missingTexture) {
            textureObj = new SimpleTexture(img);
            Minecraft.getMinecraft().getTextureManager().loadTexture(img, textureObj);
        }

        GlStateManager.enableAlpha();
    }
}
