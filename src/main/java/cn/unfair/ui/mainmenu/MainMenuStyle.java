package cn.unfair.ui.mainmenu;

import cn.unfair.util.AndroidUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.postprocessing.ShaderUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.awt.*;

public final class MainMenuStyle {
    public static final int OVERLAY = new Color(1, 1, 1, 20).getRGB();
    public static final int BUTTON_COLOR = new Color(22, 22, 22, 156).getRGB();
    public static final int BUTTON_HOVER_COLOR = new Color(32, 32, 32, 184).getRGB();
    public static final int BUTTON_MASK_COLOR = new Color(255, 255, 255, 205).getRGB();
    public static final int WHITE_208 = new Color(255, 255, 255, 208).getRGB();
    public static final int WHITE_170 = new Color(255, 255, 255, 170).getRGB();
    private static final int ANDROID_BACKGROUND_COLOR = 0xFFA3A5A2;
    private static ShaderUtils backgroundShader;

    private MainMenuStyle() {
    }

    public static void drawBackground(int width, int height, float partialTicks) {
        GlStateManager.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        if (AndroidUtil.isAndroid()) {
            RenderUtil.drawRect(0.0D, 0.0D, width, height, ANDROID_BACKGROUND_COLOR);
            return;
        }

        if (backgroundShader == null) {
            backgroundShader = new ShaderUtils("minecraft:shaders/dark_mainmenu.fsh");
        }
        Minecraft mc = Minecraft.getMinecraft();
        GlStateManager.disableAlpha();
        backgroundShader.init();
        backgroundShader.setUniformf("TIME", (System.currentTimeMillis() % 1000000L) / 1000.0F + partialTicks * 0.05F);
        backgroundShader.setUniformf("RESOLUTION", mc.displayWidth, mc.displayHeight);
        ShaderUtils.drawQuads((float) width, (float) height);
        backgroundShader.unload();
        GlStateManager.enableAlpha();
        RenderUtil.drawRect(0.0D, 0.0D, width, height, OVERLAY);
    }

    public static void drawButton(float x, float y, float w, float h, float radius, boolean hovered) {
        resetGuiState();
        RenderUtil.drawRoundedRectangle(x, y, x + w, y + h, radius, hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
    }

    public static void drawButtonMask(float x, float y, float w, float h, float radius) {
        resetGuiState();
        RenderUtil.drawRoundedRectangle(x, y, x + w, y + h, radius, BUTTON_MASK_COLOR);
    }

    public static void drawCenteredString(FontRenderer font, String text, float centerX, float y, int color) {
        font.drawString(text, Math.round(centerX - font.getStringVisualCenterOffset(text)), Math.round(y), color);
    }

    public static void drawCenteredInBox(FontRenderer font, String text, float x, float y, float width, float height, int color) {
        float textX = x + width / 2.0F - font.getStringVisualCenterOffset(text);
        float textY = y + font.getMiddleOfBox(height);
        font.drawString(text, Math.round(textX), Math.round(textY), color);
    }

    private static void resetGuiState() {
        GL20.glUseProgram(0);
        GlStateManager.resetColor();
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.disableCull();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
}
