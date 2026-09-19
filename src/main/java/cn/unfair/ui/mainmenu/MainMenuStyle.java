package cn.unfair.ui.mainmenu;

import cn.unfair.util.client.AndroidUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.font.CustomFontRenderer;
import cn.unfair.util.shader.ShaderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.*;

public final class MainMenuStyle {
    private static final String BLIT_FRAG = "#version 120\n" +
            "uniform sampler2D tex;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(tex, gl_TexCoord[0].st);\n" +
            "}";
    private static final int BACKGROUND_REFRESH_INTERVAL = 3;
    public static final int WHITE_208 = new Color(255, 255, 255, 208).getRGB();
    public static final int WHITE_170 = new Color(255, 255, 255, 170).getRGB();
    private static final int ANDROID_BACKGROUND_COLOR = 0xFFA3A5A2;
    private static ShaderUtil backgroundShader;
    private static ShaderUtil blitShader;
    private static Framebuffer backgroundFbo;
    private static int backgroundFrameCounter;

    private MainMenuStyle() {
    }

    public static void drawBackground(int width, int height, float partialTicks) {
        GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        if (AndroidUtil.isAndroid()) {
            RenderUtil.drawRect(0.0D, 0.0D, width, height, ANDROID_BACKGROUND_COLOR);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        int cacheWidth = Math.max(1, mc.displayWidth / 2);
        int cacheHeight = Math.max(1, mc.displayHeight / 2);
        if (backgroundFbo == null || backgroundFbo.framebufferWidth != cacheWidth || backgroundFbo.framebufferHeight != cacheHeight) {
            if (backgroundFbo != null) {
                backgroundFbo.deleteFramebuffer();
            }
            backgroundFbo = new Framebuffer(cacheWidth, cacheHeight, false);
            backgroundFbo.setFramebufferFilter(GL11.GL_LINEAR);
        }

        GlStateManager.disableAlpha();
        if (++backgroundFrameCounter % BACKGROUND_REFRESH_INTERVAL == 1) {
            if (backgroundShader == null) {
                backgroundShader = new ShaderUtil("minecraft:unfair/shader/panorama.frag");
            }
            backgroundFbo.forceBind(true);
            backgroundFbo.framebufferClearNoBinding();
            backgroundShader.init();
            backgroundShader.setUniformf("time", (System.currentTimeMillis() % 1000000L) / 1000.0F + partialTicks * 0.05F);
            backgroundShader.setUniformf("resolution", cacheWidth, cacheHeight);
            ShaderUtil.drawQuads((float) width, (float) height);
            backgroundShader.unload();
        }

        mc.getFramebuffer().forceBind(true);
        if (blitShader == null) {
            blitShader = new ShaderUtil(BLIT_FRAG, true);
        }
        blitShader.init();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(backgroundFbo.framebufferTexture);
        blitShader.setUniformi("tex", 0);
        ShaderUtil.drawQuads((float) width, (float) height);
        blitShader.unload();
        RenderUtil.bindTexture(0);
        GlStateManager.enableAlpha();
    }

    public static void drawCenteredString(CustomFontRenderer font, String text, float centerX, float y, int color) {
        font.drawString(text, Math.round(centerX - font.getStringVisualCenterOffset(text)), Math.round(y), color);
    }
}