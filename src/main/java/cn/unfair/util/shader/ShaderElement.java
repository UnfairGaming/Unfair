package cn.unfair.util.shader;

import cn.unfair.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

public class ShaderElement {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static ShaderUtil kawaseDown = new ShaderUtil("kawaseDown");
    public static ShaderUtil kawaseUp = new ShaderUtil("kawaseUp");
    private static final Tessellator tessellator = Tessellator.getInstance();
    private static final WorldRenderer worldrenderer = tessellator.getWorldRenderer();
    private static final ArrayList<Runnable> tasks = new ArrayList<>();
    private static final ArrayList<Runnable> bloomTasks = new ArrayList<>();

    public static ArrayList<Runnable> getTasks() {
        return tasks;
    }

    public static void addBlurTask(Runnable context) {
        tasks.add(context);
    }

    public static ArrayList<Runnable> getBloomTasks() {
        return bloomTasks;
    }

    public static void addBloomTask(Runnable context) {
        bloomTasks.add(context);
    }

    public static void setupUniforms(float offset) {
        kawaseDown.setUniformf("offset", offset, offset);
        kawaseUp.setUniformf("offset", offset, offset);
    }

    public static void drawRect(double x, double y, double width, double height, int color) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableTexture2D();
        RenderUtil.setColor(color);
        worldrenderer.begin(7, DefaultVertexFormats.POSITION);
        worldrenderer.pos(x, y + height, 0.0).endVertex();
        worldrenderer.pos(x + width, y + height, 0.0).endVertex();
        worldrenderer.pos(x + width, y, 0.0).endVertex();
        worldrenderer.pos(x, y, 0.0).endVertex();
        tessellator.draw();
        GlStateManager.resetColor();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void renderBlur(int stencilFrameBufferTexture, int iterations, int offset) {
        KawaseBlur.renderBlur(stencilFrameBufferTexture, iterations, offset);
    }

    public static void bindTexture(int texture) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer) {
        return ShaderElement.createFrameBuffer(framebuffer, false);
    }

    public static boolean needsNewFramebuffer(Framebuffer framebuffer) {
        return framebuffer == null || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight;
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer, boolean depth) {
        if (ShaderElement.needsNewFramebuffer(framebuffer)) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            return new Framebuffer(mc.displayWidth, mc.displayHeight, depth);
        }
        return framebuffer;
    }

    public static void blurArea(double x, double y, double v, double v1) {
        ShaderElement.addBlurTask(() -> ShaderElement.drawRect(x, y, v, v1, -1));
    }
}