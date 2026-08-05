package cn.unfair.ui.mainmenu;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.PostProcessing;
import cn.unfair.util.StencilUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.awt.Color;
import java.util.List;

public final class MainMenuButtonPostProcessor {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private MainMenuButtonPostProcessor() {
    }

    public static void render(List<ButtonBounds> bounds, MaskRenderer renderer) {
        render(bounds, renderer, true);
    }

    public static void render(List<ButtonBounds> bounds, MaskRenderer renderer, boolean blurEnabled) {
        if (bounds == null || bounds.isEmpty() || Unfair.moduleManager == null || !OpenGlHelper.isFramebufferEnabled()) {
            return;
        }

        PostProcessing pp = (PostProcessing) Unfair.moduleManager.getModule(PostProcessing.class);
        if (pp == null || !pp.isEnabled()) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GlStateManager.pushMatrix();
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        try {
            GlStateManager.disableCull();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

            if (blurEnabled && pp.blur.getValue()) {
                StencilUtil.write(false);
                for (ButtonBounds bound : bounds) {
                    renderer.renderMask(bound);
                }
                StencilUtil.erase(true);
                cn.unfair.util.postprocessing.PostProcessing.drawBlurFullScreen(pp.blurIterations.getValue(), pp.blurOffset.getValue());
                StencilUtil.dispose();
                mc.getFramebuffer().bindFramebuffer(false);
            }

            if (pp.bloom.getValue()) {
                Framebuffer bloomBuffer = cn.unfair.util.postprocessing.PostProcessing.beginBloom();
                if (bloomBuffer != null) {
                    GlStateManager.enableBlend();
                    GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                    GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                    for (ButtonBounds bound : bounds) {
                        renderer.renderMask(bound);
                    }
                    mc.getFramebuffer().bindFramebuffer(false);
                    Color color = pp.getBloomColor(System.currentTimeMillis());
                    cn.unfair.util.postprocessing.PostProcessing.endBloom(bloomBuffer, pp.bloomIterations.getValue(), pp.bloomOffset.getValue(), color);
                }
            }
        } finally {
            GL20.glUseProgram(previousProgram);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(0);
            mc.getFramebuffer().bindFramebuffer(false);
            GlStateManager.popMatrix();
            GL11.glPopAttrib();
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
            mc.entityRenderer.setupOverlayRendering();
            GlStateManager.enableTexture2D();
            GlStateManager.enableAlpha();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public interface MaskRenderer {
        void renderMask(ButtonBounds bounds);
    }

    public static final class ButtonBounds {
        public final float x;
        public final float y;
        public final float w;
        public final float h;
        public final float radius;

        public ButtonBounds(float x, float y, float w, float h, float radius) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.radius = radius;
        }
    }
}
