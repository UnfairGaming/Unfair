package cn.unfair.util.postprocessing;

import cn.unfair.util.MathUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

public class GlowESPBlurShader {
    private static final String SHADER = String.join(
            "\n",
            "#version 120",
            "uniform sampler2D textureIn;",
            "uniform sampler2D textureToCheck;",
            "uniform vec2 texelSize;",
            "uniform vec2 direction;",
            "uniform vec3 color;",
            "uniform bool avoidTexture;",
            "uniform float exposure;",
            "uniform float radius;",
            "uniform float weights[256];",
            "",
            "#define offset direction * texelSize",
            "",
            "void main() {",
            "    if (direction.y == 1.0 && avoidTexture) {",
            "        if (texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;",
            "    }",
            "",
            "    vec4 center = texture2D(textureIn, gl_TexCoord[0].st);",
            "    float innerAlpha = center.a * weights[0];",
            "    vec3 innerColor = center.rgb * center.a * weights[0];",
            "",
            "    for (float r = 1.0; r <= radius; r++) {",
            "        vec4 current1 = texture2D(textureIn, gl_TexCoord[0].st + offset * r);",
            "        vec4 current2 = texture2D(textureIn, gl_TexCoord[0].st - offset * r);",
            "        float weight = weights[int(r)];",
            "        innerAlpha += current1.a * weight;",
            "        innerAlpha += current2.a * weight;",
            "        innerColor += current1.rgb * current1.a * weight;",
            "        innerColor += current2.rgb * current2.a * weight;",
            "    }",
            "",
            "    if (direction.x == 0.0 && avoidTexture) {",
            "        float maskAlpha = texture2D(textureToCheck, gl_TexCoord[0].st).a;",
            "        innerAlpha *= 1.0 - maskAlpha;",
            "        innerColor *= 1.0 - maskAlpha;",
            "    }",
            "",
            "    vec3 sourceColor = innerAlpha > 0.0 ? innerColor / innerAlpha : color;",
            "    gl_FragColor = vec4(sourceColor, clamp(innerAlpha * exposure, 0.0, 1.0));",
            "}"
    );

    private final ShaderUtils shader = new ShaderUtils(SHADER, true);
    private final FloatBuffer weights = BufferUtils.createFloatBuffer(256);
    private float lastRadius = -1.0F;

    public void use() {
        this.shader.init();
    }

    public void setup(float directionX, float directionY, float radius, float exposure, java.awt.Color color) {
        this.setup(directionX, directionY, radius, exposure, color, false);
    }

    public void setup(float directionX, float directionY, float radius, float exposure, java.awt.Color color, boolean avoidTexture) {
        Minecraft mc = Minecraft.getMinecraft();
        this.shader.setUniformi("textureIn", 0);
        this.shader.setUniformi("textureToCheck", 16);
        this.shader.setUniformi("avoidTexture", avoidTexture ? 1 : 0);
        this.shader.setUniformf("radius", radius);
        this.shader.setUniformf("texelSize", 1.0F / mc.displayWidth, 1.0F / mc.displayHeight);
        this.shader.setUniformf("direction", directionX, directionY);
        this.shader.setUniformf("exposure", exposure);
        this.shader.setUniformf(
                "color",
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F
        );
        this.updateWeights(radius);
    }

    private void updateWeights(float radius) {
        if (this.lastRadius == radius) {
            return;
        }
        this.lastRadius = radius;
        int samples = Math.clamp((int) radius, 1, 255);
        this.weights.clear();
        for (int i = 0; i <= samples; i++) {
            this.weights.put(MathUtil.calculateGaussianValue(i, radius / 2.0F));
        }
        this.weights.flip();
        this.shader.setUniform1("weights", this.weights);
    }

    public void stop() {
        this.shader.unload();
    }
}
