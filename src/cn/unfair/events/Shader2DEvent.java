package cn.unfair.events;

import cn.unfair.event.events.Event;

public record Shader2DEvent(ShaderType shaderType) implements Event {

    public enum ShaderType {
        GLOW,
        BLUR,
        SHADOW
    }
}
