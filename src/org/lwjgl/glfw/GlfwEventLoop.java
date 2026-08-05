package org.lwjgl.glfw;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Keeps GLFW's platform event work on the process main thread while the game and
 * OpenGL context run on a dedicated render thread.
 */
public final class GlfwEventLoop {

    private static final Queue<EventTask<?>> EVENT_TASKS = new ConcurrentLinkedQueue<>();
    private static final Queue<Runnable> RENDER_TASKS = new ConcurrentLinkedQueue<>();
    private static final Object EVENT_LOCK = new Object();

    private static volatile Thread eventThread;
    private static volatile Thread renderThread;
    private static volatile boolean eventLoopRunning;
    private static volatile boolean pollingEnabled;
    private static volatile boolean pollRequested;

    private GlfwEventLoop() {}

    public static void initializeCurrentThread() {
        Thread currentThread = Thread.currentThread();
        if (eventThread != null && eventThread != currentThread) {
            throw new IllegalStateException("GLFW event thread has already been initialized");
        }
        eventThread = currentThread;
        eventLoopRunning = true;
    }

    public static void runEventLoop(Thread clientThread) {
        if (!isEventThread()) {
            throw new IllegalStateException("GLFW event loop must run on the initializing thread");
        }

        renderThread = clientThread;
        clientThread.start();

        try {
            while (clientThread.isAlive() || !EVENT_TASKS.isEmpty() || pollRequested) {
                drainEventTasks();

                if (pollingEnabled && pollRequested) {
                    pollRequested = false;
                    GLFW.glfwPollEvents();
                    drainEventTasks();
                } else if (!pollingEnabled) {
                    pollRequested = false;
                }

                synchronized (EVENT_LOCK) {
                    if (EVENT_TASKS.isEmpty() && clientThread.isAlive() && !pollRequested) {
                        try {
                            EVENT_LOCK.wait(10L);
                        } catch (InterruptedException interruptedException) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    } else if (!clientThread.isAlive() && EVENT_TASKS.isEmpty() && !pollRequested) {
                        break;
                    }
                }
            }

            drainEventTasks();
        } finally {
            eventLoopRunning = false;
            pollingEnabled = false;
            renderThread = null;
        }
    }

    public static boolean isEventThread() {
        return Thread.currentThread() == eventThread;
    }

    public static boolean isRenderThread() {
        return Thread.currentThread() == renderThread;
    }

    public static void setPollingEnabled(boolean enabled) {
        pollingEnabled = enabled;
        wakeEventThread();
    }

    public static void requestPollEvents() {
        pollRequested = true;
        wakeEventThread();
    }

    public static void runOnEventThread(Runnable runnable) {
        callOnEventThread(() -> {
            runnable.run();
            return null;
        });
    }

    public static <T> T callOnEventThread(Supplier<T> supplier) {
        if (eventThread == null || isEventThread()) {
            return supplier.get();
        }
        if (!eventLoopRunning) {
            throw new IllegalStateException("GLFW event loop is not running");
        }

        EventTask<T> task = new EventTask<>(supplier);
        EVENT_TASKS.add(task);
        wakeEventThread();
        task.await();
        return task.getResult();
    }

    public static void postRenderTask(Runnable runnable) {
        if (renderThread == null || isRenderThread()) {
            runnable.run();
        } else {
            RENDER_TASKS.add(runnable);
        }
    }

    public static void replayRenderTasks() {
        Runnable task;
        while ((task = RENDER_TASKS.poll()) != null) {
            task.run();
        }
    }

    private static void drainEventTasks() {
        EventTask<?> task;
        while ((task = EVENT_TASKS.poll()) != null) {
            task.run();
        }
    }

    private static void wakeEventThread() {
        synchronized (EVENT_LOCK) {
            EVENT_LOCK.notifyAll();
        }
    }

    private static final class EventTask<T> implements Runnable {

        private final Supplier<T> supplier;
        private final CountDownLatch completion = new CountDownLatch(1);
        private T result;
        private Throwable failure;

        private EventTask(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void run() {
            try {
                result = supplier.get();
            } catch (Throwable throwable) {
                failure = throwable;
            } finally {
                completion.countDown();
            }
        }

        private void await() {
            boolean interrupted = false;
            try {
                while (true) {
                    try {
                        if (completion.await(100L, TimeUnit.MILLISECONDS)) {
                            return;
                        }
                        if (!eventLoopRunning) {
                            throw new IllegalStateException("GLFW event loop stopped while a task was pending");
                        }
                    } catch (InterruptedException interruptedException) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private T getResult() {
            if (failure instanceof RuntimeException) {
                throw (RuntimeException) failure;
            }
            if (failure instanceof Error) {
                throw (Error) failure;
            }
            if (failure != null) {
                throw new RuntimeException(failure);
            }
            return result;
        }
    }
}
