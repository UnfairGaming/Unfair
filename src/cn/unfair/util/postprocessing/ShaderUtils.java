package cn.unfair.util.postprocessing;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ShaderUtils {
    private static final String DEFAULT_VERTEX = "#version 120\n" +
            "void main() {\n" +
            "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";

    private final int programID;
    private final Map<String, Integer> uniformLocations = new HashMap<>();
    private static int cachedDisplayWidth = -1;
    private static int cachedDisplayHeight = -1;
    private static int cachedGuiScale = -1;
    private static boolean cachedUnicode = false;
    private static int cachedScaleFactor = 1;
    private static float cachedScaledWidth = 0.0F;
    private static float cachedScaledHeight = 0.0F;

    public ShaderUtils(String fragmentName) {
        this(fragmentName, DEFAULT_VERTEX);
    }

    public ShaderUtils(String fragment, boolean inlineSource) {
        this(fragment, DEFAULT_VERTEX, inlineSource);
    }

    public ShaderUtils(String fragmentName, String vertexSource) {
        this(fragmentName, vertexSource, false);
    }

    public ShaderUtils(String fragment, String vertexSource, boolean inlineSource) {
        int program = GL20.glCreateProgram();
        int vertexShader = createShader(vertexSource, GL20.GL_VERTEX_SHADER);
        int fragmentShader = createShader(inlineSource ? fragment : loadFragment(fragment), GL20.GL_FRAGMENT_SHADER);
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
            throw new IllegalStateException("Failed to link shader program: " + GL20.glGetProgramInfoLog(program, 1024));
        }
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        this.programID = program;
    }

    private int createShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String info = GL20.glGetShaderInfoLog(shader, 1024);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile shader: " + info);
        }
        return shader;
    }

    private String loadFragment(String name) {
        switch (name) {
            case "kawaseDownBloom":
                return KAWASE_DOWN_BLOOM;
            case "kawaseUpBloom":
                return KAWASE_UP_BLOOM;
            default:
                return name.trim().startsWith("#version") || !name.contains(":") ? name : loadResource(name);
        }
    }

    private String loadResource(String location) {
        try (InputStream inputStream = Minecraft.getMinecraft().getResourceManager().getResource(new ResourceLocation(location)).getInputStream()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load shader resource: " + location, e);
        }
    }

    public void init() {
        GL20.glUseProgram(programID);
    }

    public void unload() {
        GL20.glUseProgram(0);
    }

    private int getUniformLocation(String name) {
        Integer location = this.uniformLocations.get(name);
        if (location == null) {
            location = GL20.glGetUniformLocation(programID, name);
            this.uniformLocations.put(name, location);
        }
        return location;
    }

    public void setUniformf(String name, float v0) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform1f(loc, v0);
    }

    public void setUniformf(String name, float v0, float v1) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform2f(loc, v0, v1);
    }

    public void setUniformf(String name, float v0, float v1, float v2) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform3f(loc, v0, v1, v2);
    }

    public void setUniformf(String name, float v0, float v1, float v2, float v3) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform4f(loc, v0, v1, v2, v3);
    }

    public void setUniformf(String name, float... values) {
        int loc = this.getUniformLocation(name);
        if (loc < 0) {
            return;
        }
        switch (values.length) {
            case 1:
                GL20.glUniform1f(loc, values[0]);
                break;
            case 2:
                GL20.glUniform2f(loc, values[0], values[1]);
                break;
            case 3:
                GL20.glUniform3f(loc, values[0], values[1], values[2]);
                break;
            case 4:
                GL20.glUniform4f(loc, values[0], values[1], values[2], values[3]);
                break;
            default:
                throw new IllegalArgumentException("Unsupported uniformf size for " + name);
        }
    }

    public void setUniformi(String name, int v0) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform1i(loc, v0);
    }

    public void setUniformi(String name, int v0, int v1) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform2i(loc, v0, v1);
    }

    public void setUniformi(String name, int v0, int v1, int v2) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform3i(loc, v0, v1, v2);
    }

    public void setUniformi(String name, int v0, int v1, int v2, int v3) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform4i(loc, v0, v1, v2, v3);
    }

    public void setUniformi(String name, int... values) {
        int loc = this.getUniformLocation(name);
        if (loc < 0) {
            return;
        }
        switch (values.length) {
            case 1:
                GL20.glUniform1i(loc, values[0]);
                break;
            case 2:
                GL20.glUniform2i(loc, values[0], values[1]);
                break;
            case 3:
                GL20.glUniform3i(loc, values[0], values[1], values[2]);
                break;
            case 4:
                GL20.glUniform4i(loc, values[0], values[1], values[2], values[3]);
                break;
            default:
                throw new IllegalArgumentException("Unsupported uniformi size for " + name);
        }
    }

    public void setUniform1(String name, FloatBuffer values) {
        int loc = this.getUniformLocation(name);
        if (loc >= 0) GL20.glUniform1(loc, values);
    }

    public static void drawQuads(float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(0.0, 1.0);
        GL11.glVertex2d(0.0, 0.0);
        GL11.glTexCoord2d(0.0, 0.0);
        GL11.glVertex2d(0.0, height);
        GL11.glTexCoord2d(1.0, 0.0);
        GL11.glVertex2d(width, height);
        GL11.glTexCoord2d(1.0, 1.0);
        GL11.glVertex2d(width, 0.0);
        GL11.glEnd();
    }

    public static void drawQuads() {
        updateScaledResolutionCache();
        drawQuads(cachedScaledWidth, cachedScaledHeight);
    }

    public static void drawFixedQuads() {
        updateScaledResolutionCache();
        Minecraft mc = Minecraft.getMinecraft();
        float width = mc.displayWidth / (float) cachedScaleFactor;
        float height = mc.displayHeight / (float) cachedScaleFactor;
        drawQuads(width, height);
    }

    private static void updateScaledResolutionCache() {
        Minecraft mc = Minecraft.getMinecraft();
        int displayWidth = mc.displayWidth;
        int displayHeight = mc.displayHeight;
        int guiScale = mc.gameSettings.guiScale;
        boolean unicode = mc.isUnicode();
        if (displayWidth == cachedDisplayWidth
                && displayHeight == cachedDisplayHeight
                && guiScale == cachedGuiScale
                && unicode == cachedUnicode) {
            return;
        }

        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(mc);
        cachedDisplayWidth = displayWidth;
        cachedDisplayHeight = displayHeight;
        cachedGuiScale = guiScale;
        cachedUnicode = unicode;
        cachedScaleFactor = sr.getScaleFactor();
        cachedScaledWidth = sr.getScaledWidth();
        cachedScaledHeight = sr.getScaledHeight();
    }

    private static final String KAWASE_DOWN_BLOOM = "#version 120\n" +
            "uniform sampler2D inTexture;\n" +
            "uniform vec2 offset, halfpixel, iResolution;\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, gl_TexCoord[0].st);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    sum *= 4.0;\n" +
            "    vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);\n" +
            "    smp1.rgb *= smp1.a;\n" +
            "    sum += smp1;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 result = sum / 8.0;\n" +
            "    gl_FragColor = vec4(result.rgb / max(result.a, 0.0001), result.a);\n" +
            "}";

    private static final String KAWASE_UP_BLOOM = "#version 120\n" +
            "uniform sampler2D inTexture, textureToCheck;\n" +
            "uniform vec2 halfpixel, offset, iResolution;\n" +
            "uniform vec3 color;\n" +
            "uniform int check;\n" +
            "void main() {\n" +
            "    vec2 uv = vec2(gl_FragCoord.xy / iResolution);\n" +
            "    vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    sum.rgb *= sum.a;\n" +
            "    vec4 smp1 = texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);\n" +
            "    smp1.rgb *= smp1.a;\n" +
            "    sum += smp1 * 2.0;\n" +
            "    vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);\n" +
            "    smp2.rgb *= smp2.a;\n" +
            "    sum += smp2;\n" +
            "    vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);\n" +
            "    smp3.rgb *= smp3.a;\n" +
            "    sum += smp3 * 2.0;\n" +
            "    vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);\n" +
            "    smp4.rgb *= smp4.a;\n" +
            "    sum += smp4;\n" +
            "    vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp5.rgb *= smp5.a;\n" +
            "    sum += smp5 * 2.0;\n" +
            "    vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);\n" +
            "    smp6.rgb *= smp6.a;\n" +
            "    sum += smp6;\n" +
            "    vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);\n" +
            "    smp7.rgb *= smp7.a;\n" +
            "    sum += smp7 * 2.0;\n" +
            "    vec4 result = sum / 12.0;\n" +
            "    float alphaMask = texture2D(textureToCheck, gl_TexCoord[0].st).a;\n" +
            "    float finalAlpha = mix(result.a, result.a * (1.0 - alphaMask), check);\n" +
            "    vec3 colored = result.rgb / max(result.a, 0.0001) * color;\n" +
            "    gl_FragColor = vec4(colored, finalAlpha);\n" +
            "}";
}
