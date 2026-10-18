package cn.unfair.ui.mainmenu;

import cn.unfair.util.render.RenderUtil;

/** Plays a short handoff from the welcome animation into the main menu. */
public final class WelcomeTransition {
    private static final long DURATION_NANOS = 850_000_000L;
    private static LottieAnimationRenderer animation;
    private static long startNanos;

    private WelcomeTransition() {
    }

    public static void begin(LottieAnimationRenderer welcomeAnimation) {
        animation = welcomeAnimation;
        startNanos = System.nanoTime();
    }

    public static boolean isActive() {
        return animation != null && progress() < 1.0F;
    }

    public static void draw(int width, int height) {
        if (animation == null) {
            return;
        }

        float progress = progress();
        if (progress >= 1.0F) {
            animation = null;
            startNanos = 0L;
            return;
        }

        float eased = progress * progress * (3.0F - 2.0F * progress);
        int overlayAlpha = Math.round((1.0F - eased) * 255.0F);
        RenderUtil.drawRect(0.0D, 0.0D, width, height, overlayAlpha << 24);

        float size = Math.min(width, height) * 0.68F * (1.0F - eased * 0.72F);
        float centerY = height * (0.47F - eased * 0.035F);
        animation.draw(width * 0.5F, centerY, size, 1.0F - eased);
    }

    private static float progress() {
        if (startNanos == 0L) {
            return 1.0F;
        }
        return Math.min(1.0F, (System.nanoTime() - startNanos) / (float) DURATION_NANOS);
    }
}
