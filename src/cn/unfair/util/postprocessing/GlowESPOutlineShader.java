package cn.unfair.util.postprocessing;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;

public class GlowESPOutlineShader extends Shader {
    private static final String SHADER = String.join(
            "\n",
            "#version 120",
            "uniform vec2 texelSize;",
            "uniform vec2 direction;",
            "uniform sampler2D texture;",
            "uniform float radius;",
            "",
            "#define offset direction * texelSize",
            "",
            "void main() {",
            "    vec4 center = texture2D(texture, gl_TexCoord[0].xy);",
            "    float innerAlpha = center.a;",
            "    vec3 colorSum = center.rgb * center.a;",
            "    float colorWeight = center.a;",
            "",
            "    for (float r = 1.0; r <= radius; r++) {",
            "        vec4 sample1 = texture2D(texture, gl_TexCoord[0].xy + offset * r);",
            "        vec4 sample2 = texture2D(texture, gl_TexCoord[0].xy - offset * r);",
            "        innerAlpha += sample1.a + sample2.a;",
            "        colorSum += sample1.rgb * sample1.a + sample2.rgb * sample2.a;",
            "        colorWeight += sample1.a + sample2.a;",
            "    }",
            "",
            "    vec3 color = colorSum / max(colorWeight, 0.001);",
            "    gl_FragColor = vec4(color, clamp(innerAlpha, 0.0, 1.0)) * step(0.0, -center.a);",
            "}"
    );

    public GlowESPOutlineShader() {
        super(SHADER);
    }

    @Override
    public void onLink() {
        this.setUniform("texture");
        this.setUniform("radius");
        this.setUniform("texelSize");
        this.setUniform("direction");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(this.programId);
    }

    public void setup(float directionX, float directionY, float radius) {
        Minecraft mc = Minecraft.getMinecraft();
        GL20.glUniform1i(this.getUniformLocationCached("texture"), 0);
        GL20.glUniform1f(this.getUniformLocationCached("radius"), radius);
        GL20.glUniform2f(this.getUniformLocationCached("texelSize"), 1.0F / mc.displayWidth, 1.0F / mc.displayHeight);
        GL20.glUniform2f(this.getUniformLocationCached("direction"), directionX, directionY);
    }
}
