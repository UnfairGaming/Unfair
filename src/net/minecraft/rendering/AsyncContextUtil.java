package net.minecraft.rendering;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GlfwEventLoop;
import org.lwjgl.opengl.Display;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;

public class AsyncContextUtil {
    public static long createSubWindow() {
        return GlfwEventLoop.callOnEventThread(() -> {
            GLFW.glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            return GLFW.glfwCreateWindow(1, 1, "SubWindow", MemoryUtil.NULL, Display.getWindow());
        });
    }
}
