package cn.unfair.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL13;

import java.awt.*;

public class PostProcessing {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static int cachedDisplayWidth = -1;
    private static int cachedDisplayHeight = -1;
    private static int cachedGuiScale = -1;
    private static boolean cachedUnicode = false;
    private static int cachedScaledWidth = 0;
    private static int cachedScaledHeight = 0;

    public static void drawBlurFullScreen(int iterations, float offset) {
        updateScaledResolutionCache();
        drawBlurInternal(0.0F, 0.0F, cachedScaledWidth, cachedScaledHeight, iterations, offset);
    }

    public static void drawBlur(float x, float y, float x2, float y2, int iterations, float offset) {
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

        updateScaledResolutionCache();
        drawBlurInternal(left, top, right, bottom, iterations, offset);
    }

    public static Framebuffer beginBloom() {
        return BloomShader.beginFramebuffer();
    }

    public static void endBloom(Framebuffer bloomBuffer, int iterations, float offset, Color color) {
        if (bloomBuffer == null) return;
        BloomShader.renderBloom(bloomBuffer.framebufferTexture, iterations, Math.max(1.0F, offset), color);
    }

    private static void drawBlurInternal(float left, float top, float right, float bottom, int iterations, float offset) {
        int width = cachedScaledWidth;
        int height = cachedScaledHeight;
        float radius = Math.max(0.0f, offset);
        int passes = Math.max(1, iterations);
        int tex = mc.getFramebuffer().framebufferTexture;
        for (int i = 0; i < passes; i++) {
            tex = BlurShader.render(tex, radius, left, top, right - left, bottom - top, width, height);
        }

        mc.getFramebuffer().forceBind(true);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(tex);
        ShaderUtil.drawQuads();
        GlStateManager.bindTexture(0);
    }

    private static void updateScaledResolutionCache() {
        int displayWidth = mc.displayWidth;
        int displayHeight = mc.displayHeight;
        int guiScale = mc.gameSettings.guiScale;
        boolean unicode = mc.isUnicode();
        if (displayWidth == cachedDisplayWidth
                && displayHeight == cachedDisplayHeight
                && guiScale == cachedGuiScale
                && unicode == cachedUnicode) {
            return;
        }

        ScaledResolution sc = new ScaledResolution(mc);
        cachedDisplayWidth = displayWidth;
        cachedDisplayHeight = displayHeight;
        cachedGuiScale = guiScale;
        cachedUnicode = unicode;
        cachedScaledWidth = sc.getScaledWidth();
        cachedScaledHeight = sc.getScaledHeight();
    }
}
