/*
 * Copyright (c) 2002-2012 LWJGL Project All rights reserved. Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following conditions are met: * Redistributions of source code
 * must retain the above copyright notice, this list of conditions and the following disclaimer. * Redistributions in
 * binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. * Neither the name of 'LWJGL' nor the names of
 * its contributors may be used to endorse or promote products derived from this software without specific prior written
 * permission. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.lwjgl.opengl;

import java.util.concurrent.locks.LockSupport;

/**
 * A hybrid frame limiter using a coarse park followed by a short spin phase.
 *
 * The old LWJGL2 limiter relied on millisecond sleeps and repeated yields. Those
 * calls are especially coarse on Windows, which makes frame pacing noticeably
 * uneven at high FPS targets.
 */
class Sync {

    private static final long NANOS_IN_SECOND = 1000L * 1000L * 1000L;
    private static final long SPIN_THRESHOLD_NANOS = 500_000L;
    private static final long RESET_THRESHOLD_FRAMES = 3L;

    private static long nextFrame;
    private static int lastFps;
    private static boolean initialised;

    /**
     * An accurate sync method that will attempt to run at a constant frame rate. It should be called once every frame.
     *
     * @param fps - the desired frame rate, in frames per second
     */
    public static void sync(int fps) {
        if (fps <= 0) return;
        long now = System.nanoTime();
        long frameNanos = NANOS_IN_SECOND / fps;

        if (!initialised || lastFps != fps) {
            initialised = true;
            lastFps = fps;
            nextFrame = now;
            return;
        }

        long targetFrame = nextFrame + frameNanos;
        if (now - nextFrame > frameNanos * RESET_THRESHOLD_FRAMES) {
            targetFrame = now + frameNanos;
        }

        nextFrame = targetFrame;
        long remaining;
        while ((remaining = targetFrame - System.nanoTime()) > SPIN_THRESHOLD_NANOS) {
            LockSupport.parkNanos(remaining - SPIN_THRESHOLD_NANOS);
        }

        while ((remaining = targetFrame - System.nanoTime()) > 0L) {
            Thread.onSpinWait();
        }

        // Keep the next frame anchored to the current time after a long stall.
        if (System.nanoTime() - targetFrame > frameNanos * RESET_THRESHOLD_FRAMES) {
            nextFrame = System.nanoTime();
        }
    }
}
