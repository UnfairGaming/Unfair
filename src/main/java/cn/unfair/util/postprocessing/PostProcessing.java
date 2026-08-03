package cn.unfair.util.postprocessing;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;

import java.awt.Color;

public class PostProcessing {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void drawBlur(float x, float y, float x2, float y2, int iterations, int offset) {
        if (!OpenGlHelper.isFramebufferEnabled()) return;

        float left = x;
        float top = y;
        float right = x2;
        float bottom = y2;

        if (left > right) {
            float t = left;
            left = right;
            right = t;
        }

        if (top > bottom) {
            float t = top;
            top = bottom;
            bottom = t;
        }

        ScaledResolution sc = new ScaledResolution(mc);
        int width = sc.getScaledWidth();
        int height = sc.getScaledHeight();

        float radius = Math.max(0.0f, offset);
        int tex = mc.getFramebuffer().framebufferTexture;
        for (int i = 0; i < Math.max(1, iterations); i++) {
            tex = BlurShader.render(tex, radius, left, top, right - left, bottom - top, width, height);
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.bindTexture(tex);
        ShaderUtils.drawQuads();
        GlStateManager.bindTexture(0);
    }

    public static Framebuffer beginBloom() {
        return BloomShader.beginFramebuffer();
    }

    public static void endBloom(Framebuffer bloomBuffer, int iterations, int offset, Color color) {
        if (bloomBuffer == null) return;
        mc.getFramebuffer().bindFramebuffer(false);
        BloomShader.renderBloom(bloomBuffer.framebufferTexture, iterations, Math.max(1, offset), color);
    }
}
