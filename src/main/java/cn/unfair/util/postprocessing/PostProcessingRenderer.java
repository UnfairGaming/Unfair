package cn.unfair.util.postprocessing;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PostProcessBloomEvent;
import cn.unfair.events.PostProcessBlurEvent;
import cn.unfair.module.modules.render.PostProcessing;
import cn.unfair.util.StencilUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

public class PostProcessingRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Framebuffer bloomBuffer;

    public static void render2D(float partialTicks) {
        if (Unfair.moduleManager == null) {
            return;
        }
        PostProcessing pp = (PostProcessing) Unfair.moduleManager.getModule(PostProcessing.class);
        if (pp == null || !pp.isEnabled()) {
            return;
        }

        if (pp.blur.getValue()) {
            PostProcessBlurEvent pre = new PostProcessBlurEvent(EventType.PRE, partialTicks);
            EventManager.call(pre);
            if (pre.isCancelled()) {
                PostProcessBlurEvent post = new PostProcessBlurEvent(EventType.POST, partialTicks);

                StencilUtil.write(false);
                EventManager.call(post);
                StencilUtil.erase(true);

                boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                if (cullWasEnabled) {
                    GL11.glDisable(GL11.GL_CULL_FACE);
                }

                ScaledResolution sr = new ScaledResolution(mc);
                cn.unfair.util.postprocessing.PostProcessing.drawBlur(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), pp.blurIterations.getValue(), pp.blurOffset.getValue());

                StencilUtil.dispose();
                if (cullWasEnabled) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                }
            }
        }

        if (pp.bloom.getValue()) {
            PostProcessBloomEvent pre = new PostProcessBloomEvent(EventType.PRE, partialTicks);
            EventManager.call(pre);
            if (pre.isCancelled()) {
                PostProcessBloomEvent post = new PostProcessBloomEvent(EventType.POST, partialTicks);
                boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                if (cullWasEnabled) {
                    GL11.glDisable(GL11.GL_CULL_FACE);
                }

                bloomBuffer = cn.unfair.util.postprocessing.PostProcessing.beginBloom();
                if (bloomBuffer != null) {
                    EventManager.call(post);
                    mc.getFramebuffer().bindFramebuffer(false);
                    cn.unfair.util.postprocessing.PostProcessing.endBloom(bloomBuffer, pp.bloomIterations.getValue(), pp.bloomOffset.getValue(), pp.getBloomColor(System.currentTimeMillis()));
                }

                if (cullWasEnabled) {
                    GL11.glEnable(GL11.GL_CULL_FACE);
                }
            }
        }
    }
}
