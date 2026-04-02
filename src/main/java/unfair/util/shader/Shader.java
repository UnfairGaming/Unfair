package unfair.util.shader;

import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

public abstract class Shader {
    private static final String vertex = "#version 120\n" +
            "void main(void) {\n" +
            "gl_TexCoord[0] = gl_MultiTexCoord0;\n" +
            "gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n" +
            "}";
    private final Map<String, Integer> uniformLocations;
    protected int programId;

    public Shader(String string) {
        this.uniformLocations = new HashMap<>();
        this.createProgram(string);
    }

    private int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        int compile = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        return compile == 0 ? -1 : shader;
    }

    private void createProgram(String fragment) {
        this.programId = GL20.glCreateProgram();
        GL20.glAttachShader(this.programId, this.compileShader(vertex, GL20.GL_VERTEX_SHADER));
        GL20.glAttachShader(this.programId, this.compileShader(fragment, GL20.GL_FRAGMENT_SHADER));
        GL20.glLinkProgram(this.programId);
        int programId = GL20.glGetProgrami(this.programId, GL20.GL_LINK_STATUS);
        if (programId == 0) {
            this.programId = -1;
        } else {
            this.onLink();
        }
    }

    public int getUniformLocationCached(String name) {
        return this.uniformLocations.get(name);
    }

    public void setUniform(String name) {
        this.uniformLocations.put(name, GL20.glGetUniformLocation(this.programId, name));
    }

    public abstract void onLink();

    public abstract void onUse();

    public void use() {
        onUse();
    }

    public void stop() {
        GL20.glUseProgram(0);
    }
}
