package cn.unfair.events;

import cn.unfair.event.events.Event;

public class Shader2DEvent implements Event {
    private final ShaderType shaderType;
    
    public enum ShaderType {
        GLOW,
        BLUR,
        SHADOW
    }
    
    public Shader2DEvent(ShaderType shaderType) {
        this.shaderType = shaderType;
    }
    
    public ShaderType getShaderType() {
        return shaderType;
    }
}
