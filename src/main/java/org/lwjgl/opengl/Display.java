package org.lwjgl.opengl;

import org.lwjgl.BufferUtils;
import org.lwjgl.Sys;
import org.lwjgl.glfw.*;
import org.lwjgl.input.KeyCodes;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class Display {

    protected static DrawableGL drawable = null;
    private static String windowTitle = "Game";
    private static boolean displayCreated = false;
    private static boolean displayFocused = false;
    private static boolean displayVisible = true;
    private static boolean displayDirty = false;
    private static boolean displayResizable = false;
    private static boolean startFullscreen = false;
    private static boolean displayFullscreen = false;
    private static volatile boolean closeRequested = false;
    private static DisplayMode mode = new DisplayMode(1280, 720);
    private static DisplayMode desktopDisplayMode = new DisplayMode(854, 480);
    private static volatile boolean desktopDisplayModeInitialized = false;
    private static final int latestEventKey = 0;
    private static int displayX = 0;
    private static int displayY = 0;
    private static boolean displayResized = false;
    private static int displayWidth = 0;
    private static int displayHeight = 0;
    private static int displayFramebufferWidth = 0;
    private static int displayFramebufferHeight = 0;
    private static float displayScaleX = 1.0F;
    private static float displayScaleY = 1.0F;
    private static boolean latestResized = false;
    private static int latestWidth = 0;
    private static int latestHeight = 0;
    private static boolean cancelNextChar = false;
    private static Keyboard.KeyEvent ingredientKeyEvent;
    private static ByteBuffer[] savedIcons;
    private static final int[] savedX = new int[1];
    private static final int[] savedY = new int[1];
    private static final int[] savedW = new int[1];
    private static final int[] savedH = new int[1];

    static {
        Sys.initialize(); // init using dummy sys method
    }

    /**
     * Create the OpenGL context with the given minimum parameters. If isFullscreen() is true or if windowed context are
     * not supported on the platform, the display mode will be switched to the mode returned by getDisplayMode(), and a
     * fullscreen context will be created. If isFullscreen() is false, a windowed context will be created with the
     * dimensions given in the mode returned by getDisplayMode(). If a context can't be created with the given
     * parameters, a LWJGLException will be thrown.
     * <p/>
     * <p>
     * The window created will be set up in orthographic 2D projection, with 1:1 pixel ratio with GL coordinates.
     *
     * @param pixelFormat    Describes the minimum specifications the context must fulfill.
     * @param sharedDrawable The Drawable to share context with. (optional, may be null)
     * @throws org.lwjgl.LWJGLException
     */
    public static void create(PixelFormat pixelFormat, Drawable sharedDrawable) {
        create(pixelFormat, null, sharedDrawable.getGlfwWindowId());
    }

    public static void create() {
        create(null, (ContextAttribs) null);
    }

    public static void create(PixelFormat pixelFormat) {
        create(pixelFormat, (ContextAttribs) null);
    }

    public static void create(PixelFormat pixelFormat, ContextAttribs attribs) {
        create(pixelFormat, attribs, NULL);
    }

    public static void create(PixelFormat pixelFormat, ContextAttribs attribs, long sharedWindow) {
        if (displayCreated) {
            return;
        }

        final int ctxMajor = (attribs != null) ? attribs.getMajorVersion() : 2;
        final int ctxMinor = (attribs != null) ? attribs.getMinorVersion() : 1;
        final boolean ctxForwardCompat = attribs != null && attribs.isForwardCompatible();
        displayFocused = true;
        displayVisible = true;
        closeRequested = false;

        Window.keyCallback = new GLFWKeyCallback() {

            @Override
            public void invoke(long window, int key, int scancode, int action, int mods) {
                GlfwEventLoop.postRenderTask(() -> handleKeyCallback(window, key, scancode, action, mods));
            }
        };

        Window.charCallback = new GLFWCharCallback() {
            @Override
            public void invoke(long window, int codepoint) {
                GlfwEventLoop.postRenderTask(() -> handleCharCallback(codepoint));
            }
        };

        Window.cursorPosCallback = new GLFWCursorPosCallback() {

            @Override
            public void invoke(long window, double xpos, double ypos) {
                GlfwEventLoop.postRenderTask(() -> Mouse.addMoveEvent(xpos, ypos));
            }
        };

        Window.mouseButtonCallback = new GLFWMouseButtonCallback() {

            @Override
            public void invoke(long window, int button, int action, int mods) {
                GlfwEventLoop.postRenderTask(() -> Mouse.addButtonEvent(button, action == GLFW.GLFW_PRESS));
            }
        };

        Window.scrollCallback = new GLFWScrollCallback() {

            @Override
            public void invoke(long window, double xoffset, double yoffset) {
                GlfwEventLoop.postRenderTask(() -> Mouse.addWheelEvent(yoffset));
            }
        };

        Window.windowFocusCallback = new GLFWWindowFocusCallback() {

            @Override
            public void invoke(long window, boolean focused) {
                GlfwEventLoop.postRenderTask(() -> {
                    displayFocused = focused;
                    if (!focused) {
                        Keyboard.resetKeyStates();
                        Mouse.resetButtonStates();
                    }
                });
            }
        };


        Window.windowIconifyCallback = new GLFWWindowIconifyCallback() {

            @Override
            public void invoke(long window, boolean iconified) {
                GlfwEventLoop.postRenderTask(() -> displayVisible = !iconified);
            }
        };

        Window.windowContentScaleCallback = new GLFWWindowContentScaleCallback() {

            @Override
            public void invoke(long window, float xscale, float yscale) {
                GlfwEventLoop.postRenderTask(() -> {
                    displayScaleX = xscale;
                    displayScaleY = yscale;
                });
            }
        };

        Window.windowSizeCallback = new GLFWWindowSizeCallback() {

            @Override
            public void invoke(long window, int width, int height) {
                GlfwEventLoop.postRenderTask(() -> {
                    latestResized = true;
                    latestWidth = width;
                    latestHeight = height;
                });
            }
        };

        Window.windowPosCallback = new GLFWWindowPosCallback() {

            @Override
            public void invoke(long window, int xpos, int ypos) {
                GlfwEventLoop.postRenderTask(() -> {
                    displayX = xpos;
                    displayY = ypos;
                });
            }
        };

        Window.windowRefreshCallback = new GLFWWindowRefreshCallback() {

            @Override
            public void invoke(long window) {
                GlfwEventLoop.postRenderTask(() -> displayDirty = true);
            }
        };

        Window.framebufferSizeCallback = new GLFWFramebufferSizeCallback() {

            @Override
            public void invoke(long window, int width, int height) {
                GlfwEventLoop.postRenderTask(() -> {
                    displayFramebufferWidth = width;
                    displayFramebufferHeight = height;
                });
            }
        };

        Window.windowCloseCallback = new GLFWWindowCloseCallback() {

            @Override
            public void invoke(long window) {
                closeRequested = true;
            }
        };

        GlfwEventLoop.runOnEventThread(() -> createWindow(ctxMajor, ctxMinor, ctxForwardCompat, attribs, sharedWindow));

        glfwMakeContextCurrent(Window.handle);
        drawable = new DrawableGL();
        GL.createCapabilities();

        if (savedIcons != null) {
            setIcon(savedIcons);
            savedIcons = null;
        }

        glfwSwapInterval(1);

        displayCreated = true;

        if (isCreated() && GLFW.glfwRawMouseMotionSupported()) {
            GlfwEventLoop.runOnEventThread(
                    () -> GLFW.glfwSetInputMode(Window.handle, GLFW_RAW_MOUSE_MOTION, GLFW_TRUE));
        }

        if (startFullscreen) {
            setFullscreen(true);
        }

        GlfwEventLoop.setPollingEnabled(true);
    }

    private static void createWindow(
            int ctxMajor,
            int ctxMinor,
            boolean ctxForwardCompat,
            ContextAttribs attribs,
            long sharedWindow) {
        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidmode = glfwGetVideoMode(monitor);
        if (vidmode == null) {
            throw new IllegalStateException("Failed to query primary monitor video mode");
        }

        int monitorWidth = vidmode.width();
        int monitorHeight = vidmode.height();
        int monitorBitPerPixel = vidmode.redBits() + vidmode.greenBits() + vidmode.blueBits();
        int monitorRefreshRate = vidmode.refreshRate();
        desktopDisplayMode =
                new DisplayMode(monitorWidth, monitorHeight, monitorBitPerPixel, monitorRefreshRate);
        desktopDisplayModeInitialized = true;

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, ctxMajor);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, ctxMinor);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, ctxForwardCompat ? GLFW_TRUE : GLFW_FALSE);

        if (attribs != null) {
            if (attribs.isProfileCore()) {
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            } else if (attribs.isProfileCompatibility()) {
                glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_COMPAT_PROFILE);
            }
        }

        glfwWindowHint(GLFW_MAXIMIZED, GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUSED, GLFW_TRUE);
        glfwWindowHint(GLFW_ICONIFIED, GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, GLFW_TRUE);
        glfwWindowHint(GLFW_SRGB_CAPABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_DOUBLEBUFFER, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_NO_ERROR, GLFW_FALSE);
        glfwWindowHint(GLFW_OPENGL_DEBUG_CONTEXT, GLFW_TRUE);
        glfwWindowHint(GLFW_POSITION_X, (monitorWidth - mode.getWidth()) / 2);
        glfwWindowHint(GLFW_POSITION_Y, (monitorHeight - mode.getHeight()) / 2);

        Window.handle = glfwCreateWindow(mode.getWidth(), mode.getHeight(), windowTitle, NULL, sharedWindow);
        if (Window.handle == NULL) {
            throw new IllegalStateException("Failed to create Display window");
        }

        Window.setCallbacks();

        int[] width = new int[1];
        int[] height = new int[1];
        glfwGetWindowSize(Window.handle, width, height);
        displayWidth = width[0];
        displayHeight = height[0];
        glfwGetFramebufferSize(Window.handle, width, height);
        displayFramebufferWidth = width[0];
        displayFramebufferHeight = height[0];

        float[] xScale = new float[1];
        float[] yScale = new float[1];
        glfwGetWindowContentScale(Window.handle, xScale, yScale);
        displayScaleX = xScale[0];
        displayScaleY = yScale[0];

        glfwGetWindowPos(Window.handle, width, height);
        displayX = width[0];
        displayY = height[0];
        displayFullscreen = false;
    }

    private static void handleKeyCallback(long window, int key, int scancode, int action, int mods) {
        cancelNextChar = false;
        if (key > GLFW_KEY_SPACE && key <= GLFW_KEY_GRAVE_ACCENT) {
            if ((GLFW_MOD_ALT & mods) != 0) {
                Keyboard.addGlfwKeyEvent(window, key, scancode, action, mods, '\0');
                cancelNextChar = true;
            } else if ((GLFW_MOD_CONTROL & mods) != 0) {
                Keyboard.addGlfwKeyEvent(window, key, scancode, action, mods, (char) (key & 0x1f));
                cancelNextChar = true;
            } else if (action > 0) {
                ingredientKeyEvent = new Keyboard.KeyEvent(
                        KeyCodes.glfwToLwjgl(key),
                        '\0',
                        action > 1 ? Keyboard.KeyState.REPEAT : Keyboard.KeyState.PRESS,
                        Sys.getNanoTime());
            } else {
                if (ingredientKeyEvent != null && ingredientKeyEvent.key == KeyCodes.glfwToLwjgl(key)) {
                    ingredientKeyEvent.queueOutOfOrderRelease = true;
                }
                Keyboard.addGlfwKeyEvent(window, key, scancode, action, mods, '\0');
            }
        } else {
            char mappedChar = key == GLFW_KEY_ENTER ? 0x0D :
                    key == GLFW_KEY_ESCAPE ? 0x1B :
                    key == GLFW_KEY_TAB ? 0x09 :
                    key == GLFW_KEY_BACKSPACE ? 0x08 :
                    '\0';
            Keyboard.addGlfwKeyEvent(window, key, scancode, action, mods, mappedChar);
        }
    }

    private static void handleCharCallback(int codepoint) {
        if (cancelNextChar) {
            cancelNextChar = false;
        } else if (ingredientKeyEvent != null) {
            ingredientKeyEvent.aChar = (char) codepoint;
            Keyboard.addRawKeyEvent(ingredientKeyEvent);
            if (ingredientKeyEvent.queueOutOfOrderRelease) {
                ingredientKeyEvent = ingredientKeyEvent.copy();
                ingredientKeyEvent.state = Keyboard.KeyState.RELEASE;
                Keyboard.addRawKeyEvent(ingredientKeyEvent);
            }
            ingredientKeyEvent = null;
        } else {
            Keyboard.addCharEvent(0, (char) codepoint);
        }
    }

    public static boolean isCreated() {
        return displayCreated;
    }

    public static boolean isActive() {
        return displayFocused;
    }

    public static boolean isVisible() {
        return displayVisible;
    }

    public static void setLocation(int new_x, int new_y) {
        displayX = new_x;
        displayY = new_y;
        if (isCreated()) {
            GlfwEventLoop.runOnEventThread(() -> glfwSetWindowPos(Window.handle, new_x, new_y));
        }
    }

    public static void setVSyncEnabled(boolean sync) {
        glfwSwapInterval(sync ? 1 : 0);
    }

    public static long getWindow() {
        return Window.handle;
    }

    public static void update() {
        update(true);
    }

    public static void update(boolean processMessages) {
        swapBuffers();
        displayDirty = false;

        if (processMessages) processMessages();
    }

    public static void processMessages() {
        GlfwEventLoop.requestPollEvents();
        GlfwEventLoop.replayRenderTasks();
        Keyboard.poll();
        Mouse.poll();

        if (latestResized) {
            latestResized = false;
            displayResized = true;
            displayWidth = latestWidth;
            displayHeight = latestHeight;
        } else {
            displayResized = false;
        }
    }

    public static void swapBuffers() {
        glfwSwapBuffers(Window.handle);
    }

    public static void destroy() {
        if (!displayCreated) {
            return;
        }

        GlfwEventLoop.setPollingEnabled(false);
        Keyboard.resetKeyStates();
        Mouse.resetButtonStates();
        glfwMakeContextCurrent(NULL);
        GlfwEventLoop.runOnEventThread(() -> glfwDestroyWindow(Window.handle));
        Window.releaseCallbacks();
        Window.handle = NULL;

        /*
         * try { glfwTerminate(); } catch (Throwable t) { t.printStackTrace(); }
         */
        displayCreated = false;
        displayFullscreen = false;
        closeRequested = false;
    }

    public static DisplayMode getDisplayMode() {
        return mode;
    }

    public static void setDisplayMode(DisplayMode dm) {
        mode = dm;
    }

    public static DisplayMode[] getAvailableDisplayModes() {
        return GlfwEventLoop.callOnEventThread(() -> {
            GLFWVidMode.Buffer modes = GLFW.glfwGetVideoModes(glfwGetPrimaryMonitor());
            if (modes == null) {
                return new DisplayMode[0];
            }

            int modeCount = modes.remaining();
            DisplayMode[] displayModes = new DisplayMode[modeCount];
            for (int i = 0; i < modeCount; i++) {
                modes.position(i);
                int w = modes.width();
                int h = modes.height();
                int b = modes.redBits() + modes.greenBits() + modes.blueBits();
                int r = modes.refreshRate();
                displayModes[i] = new DisplayMode(w, h, b, r);
            }
            return displayModes;
        });
    }

    public static DisplayMode getDesktopDisplayMode() {
        if (!desktopDisplayModeInitialized) {
            GlfwEventLoop.runOnEventThread(() -> {
                long monitor = glfwGetPrimaryMonitor();
                GLFWVidMode vidmode = glfwGetVideoMode(monitor);
                if (vidmode == null) {
                    throw new IllegalStateException("Failed to query primary monitor video mode");
                }
                desktopDisplayMode = new DisplayMode(
                        vidmode.width(),
                        vidmode.height(),
                        vidmode.redBits() + vidmode.greenBits() + vidmode.blueBits(),
                        vidmode.refreshRate());
                desktopDisplayModeInitialized = true;
            });
        }
        return desktopDisplayMode;
    }

    public static boolean wasResized() {
        return displayResized;
    }

    public static int getX() {
        return displayX;
    }

    public static int getY() {
        return displayY;
    }

    public static int getWidth() {
        return displayWidth;
    }

    public static int getHeight() {
        return displayHeight;
    }

    public static int getFramebufferWidth() {
        return displayFramebufferWidth;
    }

    public static int getFramebufferHeight() {
        return displayFramebufferHeight;
    }

    public static boolean isCloseRequested() {
        return closeRequested;
    }

    public static boolean isDirty() {
        return displayDirty;
    }

    public static void setInitialBackground(float red, float green, float blue) {
        // no-op
    }

    public static int setIcon(ByteBuffer[] icons) {
        if (getWindow() == 0) {
            savedIcons = icons;
            return 0;
        }

        GlfwEventLoop.runOnEventThread(() -> {
            GLFWImage.Buffer glfwImages = GLFWImage.calloc(icons.length);
            try {
                ByteBuffer[] nativeBuffers = new ByteBuffer[icons.length];
                for (int icon = 0; icon < icons.length; icon++) {
                    ByteBuffer source = icons[icon].duplicate();
                    nativeBuffers[icon] = BufferUtils.createByteBuffer(source.remaining());
                    nativeBuffers[icon].put(source);
                    nativeBuffers[icon].flip();
                    int dimension = (int) Math.sqrt(nativeBuffers[icon].limit() / 4D);
                    if (dimension * dimension * 4 != nativeBuffers[icon].limit()) {
                        throw new IllegalStateException();
                    }
                    glfwImages.put(icon, GLFWImage.create().set(dimension, dimension, nativeBuffers[icon]));
                }
                GLFW.glfwSetWindowIcon(getWindow(), glfwImages);
            } finally {
                glfwImages.free();
            }
        });
        return 0;
    }

    public static boolean isResizable() {
        return displayResizable;
    }

    public static void setResizable(boolean resizable) {
        displayResizable = resizable;
        // Ignore the request because why would you make the game window non-resizable
    }

    public static void setDisplayModeAndFullscreen(DisplayMode mode) {
        // TODO
        System.out.println("TODO: Implement Display.setDisplayModeAndFullscreen(DisplayMode)");
    }

    public static boolean isFullscreen() {
        return displayFullscreen;
    }

    public static void setFullscreen(boolean fullscreen) {
        final long window = getWindow();
        if (window == 0) {
            startFullscreen = fullscreen;
            return;
        }
        if (displayFullscreen == fullscreen) {
            return;
        }

        GlfwEventLoop.runOnEventThread(() -> {
            if (fullscreen) {
                glfwGetWindowPos(window, savedX, savedY);
                glfwGetWindowSize(window, savedW, savedH);
                long monitorId = glfwGetPrimaryMonitor();
                final GLFWVidMode vidMode = glfwGetVideoMode(monitorId);
                if (vidMode == null) {
                    throw new IllegalStateException("Failed to query primary monitor video mode");
                }
                glfwSetWindowMonitor(
                        window, monitorId, 0, 0, vidMode.width(), vidMode.height(), vidMode.refreshRate());
            } else {
                glfwSetWindowMonitor(window, NULL, savedX[0], savedY[0], savedW[0], savedH[0], 0);
            }
        });
        displayFullscreen = fullscreen;
        startFullscreen = fullscreen;
    }

    public static void releaseContext() {
        glfwMakeContextCurrent(0);
    }

    public static boolean isCurrent() {
        return true;
    }

    public static void makeCurrent() {
        glfwMakeContextCurrent(Window.handle);
    }

    public static String getAdapter() {
        if (isCreated()) {
            return GL11.glGetString(GL11.GL_VENDOR);
        }
        return "Unknown";
    }

    public static String getVersion() {
        if (isCreated()) {
            return GL11.glGetString(GL11.GL_VERSION);
        }
        return "Unknown";
    }

    public static String getTitle() {
        return windowTitle;
    }

    public static void setTitle(String title) {
        windowTitle = title;
        if (isCreated()) {
            GlfwEventLoop.runOnEventThread(() -> glfwSetWindowTitle(getWindow(), title));
        }
    }

    public static Canvas getParent() {
        return null;
    }

    public static void setParent(Canvas parent) {
        // Do nothing as set parent not supported
    }

    public static float getPixelScaleFactor() {
        return isCreated() ? Math.max(displayScaleX, displayScaleY) : 1.0F;
    }

    public static void setSwapInterval(int value) {
        glfwSwapInterval(value);
    }

    public static void setDisplayConfiguration(float gamma, float brightness, float contrast) {
        // ignore
    }

    /**
     * An accurate sync method that will attempt to run at a constant frame rate. It should be called once every frame.
     *
     * @param fps - the desired frame rate, in frames per second
     */
    public static void sync(int fps) {
        Sync.sync(fps);
    }

    public static Drawable getDrawable() {
        return drawable;
    }

    static DisplayImplementation getImplementation() {
        return null;
    }

    private static class Window {

        static long handle;

        static GLFWKeyCallback keyCallback;
        static GLFWCharCallback charCallback;
        static GLFWCursorPosCallback cursorPosCallback;
        static GLFWMouseButtonCallback mouseButtonCallback;
        static GLFWScrollCallback scrollCallback;
        static GLFWWindowFocusCallback windowFocusCallback;
        static GLFWWindowIconifyCallback windowIconifyCallback;
        static GLFWWindowContentScaleCallback windowContentScaleCallback;
        static GLFWWindowSizeCallback windowSizeCallback;
        static GLFWWindowPosCallback windowPosCallback;
        static GLFWWindowRefreshCallback windowRefreshCallback;
        static GLFWFramebufferSizeCallback framebufferSizeCallback;
        static GLFWWindowCloseCallback windowCloseCallback;

        public static void setCallbacks() {
            GLFW.glfwSetKeyCallback(handle, keyCallback);
            GLFW.glfwSetCharCallback(handle, charCallback);
            GLFW.glfwSetCursorPosCallback(handle, cursorPosCallback);
            GLFW.glfwSetMouseButtonCallback(handle, mouseButtonCallback);
            GLFW.glfwSetScrollCallback(handle, scrollCallback);
            GLFW.glfwSetWindowFocusCallback(handle, windowFocusCallback);
            GLFW.glfwSetWindowIconifyCallback(handle, windowIconifyCallback);
            GLFW.glfwSetWindowContentScaleCallback(handle, windowContentScaleCallback);
            GLFW.glfwSetWindowSizeCallback(handle, windowSizeCallback);
            GLFW.glfwSetWindowPosCallback(handle, windowPosCallback);
            GLFW.glfwSetWindowRefreshCallback(handle, windowRefreshCallback);
            GLFW.glfwSetFramebufferSizeCallback(handle, framebufferSizeCallback);
            GLFW.glfwSetWindowCloseCallback(handle, windowCloseCallback);
        }

        public static void releaseCallbacks() {
            keyCallback.free();
            charCallback.free();
            cursorPosCallback.free();
            mouseButtonCallback.free();
            scrollCallback.free();
            windowFocusCallback.free();
            windowIconifyCallback.free();
            windowContentScaleCallback.free();
            windowSizeCallback.free();
            windowPosCallback.free();
            windowRefreshCallback.free();
            framebufferSizeCallback.free();
            windowCloseCallback.free();
        }
    }
}
