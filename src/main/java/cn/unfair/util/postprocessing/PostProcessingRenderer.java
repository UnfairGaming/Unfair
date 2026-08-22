package cn.unfair.util.postprocessing;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.event.types.EventType;
import cn.unfair.events.RenderBloomEvent;
import cn.unfair.events.RenderBlurEvent;
import cn.unfair.module.modules.render.PostProcessing;
import cn.unfair.util.AndroidUtil;
import cn.unfair.util.StencilUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class PostProcessingRenderer {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static Framebuffer bloomBuffer;

    public static void render2D(float partialTicks) {
        if (AndroidUtil.isAndroid()) {
            return;
        }
        if (Unfair.moduleManager == null) {
            return;
        }
        PostProcessing pp = (PostProcessing) Unfair.moduleManager.getModule(PostProcessing.class);
        if (pp == null || !pp.isEnabled()) {
            return;
        }

        if (pp.blur.getValue()) {
            RenderBlurEvent pre = new RenderBlurEvent(EventType.PRE, partialTicks);
            EventManager.call(pre);
            if (pre.isCancelled()) {
                RenderBlurEvent post = new RenderBlurEvent(EventType.POST, partialTicks);

                boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                boolean depthMaskWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                try {
                    GlStateManager.disableDepth();
                    GlStateManager.depthMask(false);
                    StencilUtil.write(false);
                    EventManager.call(post);
                    StencilUtil.erase(true);

                    if (cullWasEnabled) {
                        GL11.glDisable(GL11.GL_CULL_FACE);
                    }

                    cn.unfair.util.postprocessing.PostProcessing.drawBlurFullScreen(pp.blurIterations.getValue(), pp.blurOffset.getValue());
                } finally {
                    StencilUtil.dispose();
                    if (cullWasEnabled) {
                        GL11.glEnable(GL11.GL_CULL_FACE);
                    }
                    GlStateManager.depthMask(depthMaskWasEnabled);
                    if (depthWasEnabled) {
                        GlStateManager.enableDepth();
                    } else {
                        GlStateManager.disableDepth();
                    }
                    GL11.glColorMask(true, true, true, true);
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
                    GlStateManager.bindTexture(0);
                    mc.getFramebuffer().forceBind(true);
                }
            }
        }

        if (pp.bloom.getValue()) {
            RenderBloomEvent pre = new RenderBloomEvent(EventType.PRE, partialTicks);
            EventManager.call(pre);
            if (pre.isCancelled()) {
                RenderBloomEvent post = new RenderBloomEvent(EventType.POST, partialTicks);
                boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
                boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
                boolean depthMaskWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
                try {
                    GlStateManager.disableDepth();
                    GlStateManager.depthMask(false);
                    if (cullWasEnabled) {
                        GL11.glDisable(GL11.GL_CULL_FACE);
                    }

                    bloomBuffer = cn.unfair.util.postprocessing.PostProcessing.beginBloom();
                    if (bloomBuffer != null) {
                        EventManager.call(post);
                        mc.getFramebuffer().forceBind(true);
                        cn.unfair.util.postprocessing.PostProcessing.endBloom(bloomBuffer, pp.bloomIterations.getValue(), pp.bloomOffset.getValue(), pp.getBloomColor(System.currentTimeMillis()));
                    }
                } finally {
                    if (cullWasEnabled) {
                        GL11.glEnable(GL11.GL_CULL_FACE);
                    }
                    GlStateManager.depthMask(depthMaskWasEnabled);
                    if (depthWasEnabled) {
                        GlStateManager.enableDepth();
                    } else {
                        GlStateManager.disableDepth();
                    }
                    GL11.glColorMask(true, true, true, true);
                    GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
                    GlStateManager.bindTexture(0);
                    mc.getFramebuffer().forceBind(true);
                }
            }
        }
    }
}
