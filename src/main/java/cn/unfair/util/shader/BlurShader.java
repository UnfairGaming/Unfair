package cn.unfair.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public class BlurShader {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final String VERTEX = "#version 120\n" +
            "varying vec2 texCoord;\n" +
            "varying vec2 oneTexel;\n" +
            "uniform vec2 InSize;\n" +
            "void main() {\n" +
            "    gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "    texCoord = gl_MultiTexCoord0.st;\n" +
            "    oneTexel = 1.0 / InSize;\n" +
            "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";

    private static final String FRAGMENT = "#version 120\n" +
            "uniform sampler2D DiffuseSampler;\n" +
            "varying vec2 texCoord;\n" +
            "varying vec2 oneTexel;\n" +
            "uniform vec2 InSize;\n" +
            "uniform vec2 BlurDir;\n" +
            "uniform vec2 BlurXY;\n" +
            "uniform vec2 BlurCoord;\n" +
            "uniform float Radius;\n" +
            "float SCurve (float x) {\n" +
            "    x = x * 2.0 - 1.0;\n" +
            "    return -x * abs(x) * 0.5 + x + 0.5;\n" +
            "}\n" +
            "vec4 BlurH (sampler2D source, vec2 size, vec2 uv, float radius) {\n" +
            "    if (uv.x / oneTexel.x >= BlurXY.x && uv.y / oneTexel.y >= BlurXY.y && uv.x / oneTexel.x <= (BlurCoord.x + BlurXY.x) && uv.y / oneTexel.y <= (BlurCoord.y + BlurXY.y))\n" +
            "    {\n" +
            "        vec4 A = vec4(0.0);\n" +
            "        vec4 C = vec4(0.0);\n" +
            "        float divisor = 0.0;\n" +
            "        float weight = 0.0;\n" +
            "        float radiusMultiplier = 1.0 / radius;\n" +
            "        for (float x = -radius; x <= radius; x++)\n" +
            "        {\n" +
            "            A = texture2D(source, uv + vec2(x * size) * BlurDir);\n" +
            "            weight = SCurve(1.0 - (abs(x) * radiusMultiplier));\n" +
            "            C += A * weight;\n" +
            "            divisor += weight;\n" +
            "        }\n" +
            "        return vec4(C.r / divisor, C.g / divisor, C.b / divisor, 1.0);\n" +
            "    }\n" +
            "    return texture2D(source, uv);\n" +
            "}\n" +
            "void main() {\n" +
            "    if (texCoord.x / oneTexel.x >= BlurXY.x - Radius && texCoord.y / oneTexel.y >= BlurXY.y - Radius && texCoord.x / oneTexel.x <= (BlurCoord.x + BlurXY.x) + Radius && texCoord.y / oneTexel.y <= (BlurCoord.y + BlurXY.y) + Radius) {\n" +
            "        gl_FragColor = BlurH(DiffuseSampler, oneTexel, texCoord, Radius);\n" +
            "    } else {\n" +
            "        gl_FragColor = texture2D(DiffuseSampler, texCoord);\n" +
            "    }\n" +
            "}";

    private static ShaderUtil shader;
    private static Framebuffer pass1;
    private static Framebuffer pass2;
    private static boolean uniformLocationsInitialized;
    private static int diffuseSamplerLocation;
    private static int inSizeLocation;
    private static int blurDirLocation;
    private static int blurXYLocation;
    private static int blurCoordLocation;
    private static int radiusLocation;

    private static void ensureFramebuffers() {
        if (pass1 == null || pass1.framebufferWidth != mc.displayWidth || pass1.framebufferHeight != mc.displayHeight) {
            if (pass1 != null) {
                pass1.deleteFramebuffer();
            }
            pass1 = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            pass1.setFramebufferFilter(GL11.GL_LINEAR);
        }
        if (pass2 == null || pass2.framebufferWidth != mc.displayWidth || pass2.framebufferHeight != mc.displayHeight) {
            if (pass2 != null) {
                pass2.deleteFramebuffer();
            }
            pass2 = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
            pass2.setFramebufferFilter(GL11.GL_LINEAR);
        }
    }

    private static ShaderUtil ensureShader() {
        if (shader == null) {
            shader = new ShaderUtil(FRAGMENT, VERTEX, true);
        }
        if (!uniformLocationsInitialized) {
            diffuseSamplerLocation = shader.getUniformLocation("DiffuseSampler");
            inSizeLocation = shader.getUniformLocation("InSize");
            blurDirLocation = shader.getUniformLocation("BlurDir");
            blurXYLocation = shader.getUniformLocation("BlurXY");
            blurCoordLocation = shader.getUniformLocation("BlurCoord");
            radiusLocation = shader.getUniformLocation("Radius");
            uniformLocationsInitialized = true;
        }
        return shader;
    }

    public static int render(int inputTexture, float radius, float x, float y, float w, float h, int screenW, int screenH) {
        radius = Math.max(0.0f, radius);
        if (radius <= 0.0f) {
            return inputTexture;
        }

        ensureFramebuffers();
        ShaderUtil s = ensureShader();

        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GL11.glClearColor(0, 0, 0, 0);
        pass1.forceBind(true);
        pass1.framebufferClearNoBinding();
        s.init();
        setUniforms(screenW, screenH, 1.0f, 0.0f, x, screenH - y - h, w, h, radius);
        GlStateManager.bindTexture(inputTexture);
        ShaderUtil.drawQuads();

        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GL11.glClearColor(0, 0, 0, 0);
        pass2.forceBind(true);
        pass2.framebufferClearNoBinding();
        setUniforms(screenW, screenH, 0.0f, 1.0f, x, screenH - y - h, w, h, radius);
        GlStateManager.bindTexture(pass1.framebufferTexture);
        ShaderUtil.drawQuads();
        s.unload();

        mc.getFramebuffer().forceBind(true);
        GlStateManager.bindTexture(0);
        return pass2.framebufferTexture;
    }

    private static void setUniforms(float screenW, float screenH, float directionX, float directionY, float x, float y, float width, float height, float radius) {
        if (diffuseSamplerLocation >= 0) GL20.glUniform1i(diffuseSamplerLocation, 0);
        if (inSizeLocation >= 0) GL20.glUniform2f(inSizeLocation, screenW, screenH);
        if (blurDirLocation >= 0) GL20.glUniform2f(blurDirLocation, directionX, directionY);
        if (blurXYLocation >= 0) GL20.glUniform2f(blurXYLocation, x, y);
        if (blurCoordLocation >= 0) GL20.glUniform2f(blurCoordLocation, width, height);
        if (radiusLocation >= 0) GL20.glUniform1f(radiusLocation, radius);
    }
}
