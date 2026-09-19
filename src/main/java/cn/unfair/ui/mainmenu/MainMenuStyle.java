package cn.unfair.ui.mainmenu;

import cn.unfair.module.modules.render.HUD;
import cn.unfair.util.client.AndroidUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.font.CustomFontRenderer;
import cn.unfair.util.shader.ShaderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public final class MainMenuStyle {
    public static final int WHITE_208 = new Color(255, 255, 255, 208).getRGB();
    public static final int WHITE_170 = new Color(255, 255, 255, 170).getRGB();
    private static final int ANDROID_BACKGROUND_COLOR = 0xFFA3A5A2;
    private static ShaderUtil backgroundShader;

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
            backgroundShader = new ShaderUtil("minecraft:shaders/hud_mainmenu.fsh");
        }
        Minecraft mc = Minecraft.getMinecraft();
        Color hudColor = HUD.getColor(System.currentTimeMillis());
        GlStateManager.disableAlpha();
        backgroundShader.init();
        backgroundShader.setUniformf("TIME", (System.currentTimeMillis() % 1000000L) / 1000.0F + partialTicks * 0.05F);
        backgroundShader.setUniformf("RESOLUTION", mc.displayWidth, mc.displayHeight);
        backgroundShader.setUniformf(
                "HUD_COLOR",
                hudColor.getRed() / 255.0F,
                hudColor.getGreen() / 255.0F,
                hudColor.getBlue() / 255.0F
        );
        ShaderUtil.drawQuads((float) width, (float) height);
        backgroundShader.unload();
        GlStateManager.enableAlpha();
    }

    public static void drawCenteredString(CustomFontRenderer font, String text, float centerX, float y, int color) {
        font.drawString(text, Math.round(centerX - font.getStringVisualCenterOffset(text)), Math.round(y), color);
    }
}