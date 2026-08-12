package cn.unfair.util.postprocessing;

import net.minecraft.client.Minecraft;

public class GlowESPOutlineShader {
    private static final String SHADER = String.join(
            "\n",
            "#version 120",
            "uniform vec2 texelSize;",
            "uniform vec2 direction;",
            "uniform sampler2D texture;",
            "uniform float radius;",
            "uniform vec3 color;",
            "",
            "#define offset direction * texelSize",
            "",
            "void main() {",
            "    vec4 center = texture2D(texture, gl_TexCoord[0].xy);",
            "    float centerAlpha = center.a;",
            "    float innerAlpha = centerAlpha;",
            "    vec3 innerColor = center.rgb * centerAlpha;",
            "",
            "    for (float r = 1.0; r <= radius; r++) {",
            "        vec4 current1 = texture2D(texture, gl_TexCoord[0].xy + offset * r);",
            "        vec4 current2 = texture2D(texture, gl_TexCoord[0].xy - offset * r);",
            "        innerAlpha += current1.a + current2.a;",
            "        innerColor += current1.rgb * current1.a + current2.rgb * current2.a;",
            "    }",
            "",
            "    vec3 sourceColor = innerAlpha > 0.0 ? innerColor / innerAlpha : color;",
            "    gl_FragColor = vec4(sourceColor, innerAlpha) * step(0.0, -centerAlpha);",
            "}"
    );

    private final ShaderUtil shader = new ShaderUtil(SHADER, true);

    public void use() {
        this.shader.init();
    }

    public void setup(float directionX, float directionY, float radius, java.awt.Color color) {
        Minecraft mc = Minecraft.getMinecraft();
        this.shader.setUniformi("texture", 0);
        this.shader.setUniformf("radius", radius);
        this.shader.setUniformf("texelSize", 1.0F / mc.displayWidth, 1.0F / mc.displayHeight);
        this.shader.setUniformf("direction", directionX, directionY);
        this.shader.setUniformf(
                "color",
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F
        );
    }

    public void stop() {
        this.shader.unload();
    }
}
