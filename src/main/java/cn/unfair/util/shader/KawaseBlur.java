package cn.unfair.util.shader;

import cn.unfair.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public class KawaseBlur {

    private static final String DOWN_FRAG = "#version 120\n" +
            "uniform sampler2D inTexture;\n" +
            "void main() {\n" +
            "    gl_FragColor = texture2D(inTexture, gl_TexCoord[0].st);\n" +
            "}";

    private static final String BLUR_FRAG = "#version 120\n" +
            "uniform sampler2D DiffuseSampler;\n" +
            "uniform vec2 oneTexel;\n" +
            "uniform vec2 BlurDir;\n" +
            "uniform float Radius;\n" +
            "float SCurve (float x) {\n" +
            "    x = x * 2.0 - 1.0;\n" +
            "    return -x * abs(x) * 0.5 + x + 0.5;\n" +
            "}\n" +
            "void main() {\n" +
            "    vec4 sum = vec4(0.0);\n" +
            "    float divisor = 0.0;\n" +
            "    float radiusMultiplier = 1.0 / Radius;\n" +
            "    for (float x = -Radius; x <= Radius; x++) {\n" +
            "        vec4 color = texture2D(DiffuseSampler, gl_TexCoord[0].st + vec2(x * oneTexel) * BlurDir);\n" +
            "        float weight = SCurve(1.0 - (abs(x) * radiusMultiplier));\n" +
            "        sum += color * weight;\n" +
            "        divisor += weight;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(sum.r / divisor, sum.g / divisor, sum.b / divisor, 1.0);\n" +
            "}";

    private static final String MASK_FRAG = "#version 120\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform sampler2D maskTexture;\n" +
            "void main() {\n" +
            "    vec4 color = texture2D(inTexture, gl_TexCoord[0].st);\n" +
            "    float maskAlpha = texture2D(maskTexture, gl_TexCoord[0].st).a;\n" +
            "    gl_FragColor = vec4(color.rgb, color.a * maskAlpha);\n" +
            "}";

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ShaderUtil downShader = new ShaderUtil(DOWN_FRAG, true);
    private static final ShaderUtil blurShader = new ShaderUtil(BLUR_FRAG, true);
    private static final ShaderUtil maskShader = new ShaderUtil(MASK_FRAG, true);
    private static Framebuffer downFbo = new Framebuffer(1, 1, false);
    private static Framebuffer passA = new Framebuffer(1, 1, false);
    private static Framebuffer passB = new Framebuffer(1, 1, false);

    public static void setupUniforms(float offset) {
    }

    private static Framebuffer ensureFramebuffer(Framebuffer framebuffer, int width, int height) {
        if (framebuffer == null || framebuffer.framebufferWidth != width || framebuffer.framebufferHeight != height) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            framebuffer = new Framebuffer(width, height, false);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        }
        return framebuffer;
    }

    public static void renderBlur(int maskTexture, int iterations, int offset) {
        renderBlur(maskTexture, mc.getFramebuffer().framebufferTexture, iterations, offset);
    }

    public static void renderBlur(int maskTexture, int sourceTexture, int iterations, int offset) {
        int downWidth = Math.max(1, mc.displayWidth / 2);
        int downHeight = Math.max(1, mc.displayHeight / 2);
        downFbo = ensureFramebuffer(downFbo, downWidth, downHeight);
        passA = ensureFramebuffer(passA, downWidth, downHeight);
        passB = ensureFramebuffer(passB, downWidth, downHeight);

        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthMaskWasEnabled = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        downFbo.forceBind(true);
        downFbo.framebufferClearNoBinding();
        downShader.init();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(sourceTexture);
        downShader.setUniformi("inTexture", 0);
        ShaderUtil.drawQuads();
        downShader.unload();

        int input = downFbo.framebufferTexture;
        for (int i = 0; i < iterations; i++) {
            passA.forceBind(true);
            passA.framebufferClearNoBinding();
            blurShader.init();
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            RenderUtil.bindTexture(input);
            blurShader.setUniformi("DiffuseSampler", 0);
            blurShader.setUniformf("oneTexel", 1.0F / downWidth, 1.0F / downHeight);
            blurShader.setUniformf("BlurDir", 1.0F, 0.0F);
            blurShader.setUniformf("Radius", Math.max(1.0F, offset));
            ShaderUtil.drawQuads();
            blurShader.unload();

            passB.forceBind(true);
            passB.framebufferClearNoBinding();
            blurShader.init();
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            RenderUtil.bindTexture(passA.framebufferTexture);
            blurShader.setUniformi("DiffuseSampler", 0);
            blurShader.setUniformf("oneTexel", 1.0F / downWidth, 1.0F / downHeight);
            blurShader.setUniformf("BlurDir", 0.0F, 1.0F);
            blurShader.setUniformf("Radius", Math.max(1.0F, offset));
            ShaderUtil.drawQuads();
            blurShader.unload();
            input = passB.framebufferTexture;
        }

        mc.getFramebuffer().forceBind(true);
        maskShader.init();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        RenderUtil.bindTexture(maskTexture);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(input);
        maskShader.setUniformi("inTexture", 0);
        maskShader.setUniformi("maskTexture", 1);
        RenderUtil.setAlphaLimit(0);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        ShaderUtil.drawQuads();
        maskShader.unload();
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        RenderUtil.bindTexture(0);
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(depthMaskWasEnabled);
        if (depthWasEnabled) {
            GlStateManager.enableDepth();
        } else {
            GlStateManager.disableDepth();
        }
    }
}