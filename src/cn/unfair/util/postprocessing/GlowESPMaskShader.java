package cn.unfair.util.postprocessing;

import org.lwjgl.opengl.GL20;

import java.awt.Color;

public class GlowESPMaskShader extends Shader {
    private static final String SHADER = String.join(
            "\n",
            "#version 120",
            "uniform sampler2D texture;",
            "uniform vec4 color;",
            "",
            "void main() {",
            "    vec4 sampled = texture2D(texture, gl_TexCoord[0].st);",
            "    gl_FragColor = vec4(color.rgb, sampled.a > 0.0 ? color.a : 0.0);",
            "}"
    );

    public GlowESPMaskShader() {
        super(SHADER);
    }

    @Override
    public void onLink() {
        this.setUniform("texture");
        this.setUniform("color");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(this.programId);
        GL20.glUniform1i(this.getUniformLocationCached("texture"), 0);
    }

    public void setColor(Color color) {
        GL20.glUniform4f(
                this.getUniformLocationCached("color"),
                color.getRed() / 255.0F,
                color.getGreen() / 255.0F,
                color.getBlue() / 255.0F,
                color.getAlpha() / 255.0F
        );
    }
}
