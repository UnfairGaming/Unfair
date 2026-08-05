package org.lwjgl.opengl;

public class GLContext {

    private static final ContextCapabilities contextCapabilities = new ContextCapabilities();

    public static ContextCapabilities getCapabilities() {
        return contextCapabilities;
    }
}
