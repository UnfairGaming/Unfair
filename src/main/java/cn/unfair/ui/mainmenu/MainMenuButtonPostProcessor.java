package cn.unfair.ui.mainmenu;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.PostProcessing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

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

        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        if (cullWasEnabled) {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }

        if (blurEnabled && pp.blur.getValue()) {
            for (ButtonBounds bound : bounds) {
                cn.unfair.util.postprocessing.PostProcessing.drawBlur(
                        bound.x,
                        bound.y,
                        bound.x + bound.w,
                        bound.y + bound.h,
                        pp.blurIterations.getValue(),
                        pp.blurOffset.getValue()
                );
            }
            mc.getFramebuffer().bindFramebuffer(false);
        }

        if (pp.bloom.getValue()) {
            Framebuffer bloomBuffer = cn.unfair.util.postprocessing.PostProcessing.beginBloom();
            if (bloomBuffer != null) {
                for (ButtonBounds bound : bounds) {
                    renderer.renderMask(bound);
                }
                mc.getFramebuffer().bindFramebuffer(false);
                Color color = pp.getBloomColor(System.currentTimeMillis());
                cn.unfair.util.postprocessing.PostProcessing.endBloom(bloomBuffer, pp.bloomIterations.getValue(), pp.bloomOffset.getValue(), color);
            }
        }

        if (cullWasEnabled) {
            GL11.glEnable(GL11.GL_CULL_FACE);
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
