package unfair.util.shader;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL20;

public class OutlineShader extends Shader {
    private static final String shader = String.join(
            "\n",
            "uniform sampler2D texture;",
            "uniform vec2 size;",
            "uniform float radius;",
            "void main(void) {",
            "vec4 xy = texture2D(texture, gl_TexCoord[0].xy);",
            "if(xy.a != 0) {",
            "gl_FragColor = vec4(0, 0, 0, 0);",
            "} else {",
            "for (float x = -radius; x <= radius; x++) {",
            "for (float y = -radius; y <= radius; y++) {",
            "vec4 color = texture2D(texture, gl_TexCoord[0].xy + vec2(size.x * x, size.y * y));",
            "if (color.a != 0) {",
            "gl_FragColor = color;",
            "}",
            "}",
            "}",
            "}",
            "}"
    );

    public OutlineShader() {
        super(shader);
    }

    @Override
    public void onLink() {
        this.setUniform("texture");
        this.setUniform("size");
        this.setUniform("radius");
    }

    @Override
    public void onUse() {
        GL20.glUseProgram(this.programId);
        int texLoc = this.getUniformLocationCached("texture");
        GL20.glUniform1i(texLoc, 0);
        int sizeLoc = this.getUniformLocationCached("size");
        float invW = 1.0f / Minecraft.getMinecraft().displayWidth;
        float invH = 1.0f / Minecraft.getMinecraft().displayHeight;
        GL20.glUniform2f(sizeLoc, invW, invH);
        int radiusLoc = this.getUniformLocationCached("radius");
        GL20.glUniform1f(radiusLoc, 2.0f);
    }
}
