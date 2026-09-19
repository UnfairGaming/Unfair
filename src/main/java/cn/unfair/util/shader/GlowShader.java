package cn.unfair.util.shader;

import cn.unfair.util.client.MathUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;

import java.awt.*;
import java.nio.FloatBuffer;

public class GlowShader {
    private final ShaderUtil shader = new ShaderUtil("glow");
    private final FloatBuffer weights = BufferUtils.createFloatBuffer(256);
    private float lastRadius = -1.0F;

    public void use() {
        this.shader.init();
    }

    public void setup(float directionX, float directionY, float radius, float exposure, Color color) {
        this.setup(directionX, directionY, radius, exposure, color, false);
    }

    public void setup(float directionX, float directionY, float radius, float exposure, Color color, boolean avoidTexture) {
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
