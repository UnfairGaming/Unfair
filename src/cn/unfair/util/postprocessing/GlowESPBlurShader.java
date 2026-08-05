package cn.unfair.util.postprocessing;

import cn.unfair.util.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;

public class GlowESPBlurShader extends Shader {
    private static final String SHADER = String.join(
            "\n",
            "#version 120",
            "uniform sampler2D textureIn;",
            "uniform vec2 texelSize;",
            "uniform vec2 direction;",
            "uniform float exposure;",
            "uniform float radius;",
            "uniform float weights[256];",
            "",
            "#define offset direction * texelSize",
            "",
            "void main() {",
            "    vec4 center = texture2D(textureIn, gl_TexCoord[0].st);",
            "    float alpha = center.a * weights[0];",
            "    vec3 colorSum = center.rgb * center.a * weights[0];",
            "",
            "    for (float r = 1.0; r <= radius; r++) {",
            "        vec4 sample1 = texture2D(textureIn, gl_TexCoord[0].st + offset * r);",
            "        vec4 sample2 = texture2D(textureIn, gl_TexCoord[0].st - offset * r);",
            "        float weight = weights[int(r)];",
            "        alpha += sample1.a * weight + sample2.a * weight;",
            "        colorSum += sample1.rgb * sample1.a * weight + sample2.rgb * sample2.a * weight;",
            "    }",
            "",
            "    vec3 color = colorSum / max(alpha, 0.001);",
            "    float finalAlpha = clamp(mix(alpha, 1.0 - exp(-alpha * exposure), step(0.0, direction.y)), 0.0, 1.0);",
            "    gl_FragColor = vec4(color, finalAlpha);",
            "}"
    );

    private final FloatBuffer weights = BufferUtils.createFloatBuffer(256);

    public GlowESPBlurShader() {
        super(SHADER);
    }

    @Override
    public void onLink() {
        this.setUniform("textureIn");
        this.setUniform("radius");
        this.setUniform("texelSize");
        this.setUniform("direction");
        this.setUniform("exposure");
        this.setUniform("weights");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(this.programId);
    }

    public void setup(float directionX, float directionY, float radius, float exposure) {
        Minecraft mc = Minecraft.getMinecraft();
        GL20.glUniform1i(this.getUniformLocationCached("textureIn"), 0);
        GL20.glUniform1f(this.getUniformLocationCached("radius"), radius);
        GL20.glUniform2f(this.getUniformLocationCached("texelSize"), 1.0F / mc.displayWidth, 1.0F / mc.displayHeight);
        GL20.glUniform2f(this.getUniformLocationCached("direction"), directionX, directionY);
        GL20.glUniform1f(this.getUniformLocationCached("exposure"), exposure);
        this.updateWeights(radius);
    }

    private void updateWeights(float radius) {
        int samples = Math.min(255, Math.max(1, (int) radius));
        this.weights.clear();
        for (int i = 0; i <= samples; i++) {
            this.weights.put(MathUtil.calculateGaussianValue(i, radius / 2.0F));
        }
        this.weights.flip();
        OpenGlHelper.glUniform1(this.getUniformLocationCached("weights"), this.weights);
    }
}
