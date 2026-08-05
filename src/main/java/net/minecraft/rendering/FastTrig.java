package net.minecraft.rendering;

public class FastTrig {
    private static final int ATAN2_BITS = 8;

    private static final int ATAN2_BITS2 = ATAN2_BITS << 1;
    private static final int ATAN2_MASK = ~(-1 << ATAN2_BITS2);
    private static final int ATAN2_COUNT = ATAN2_MASK + 1;
    private static final int ATAN2_DIM = (int) Math.sqrt(ATAN2_COUNT);

    private static final float INV_ATAN2_DIM_MINUS_1 = 1.0f / (ATAN2_DIM - 1);

    private static final float[] atan2 = new float[ATAN2_COUNT];

    static {
        for (int i = 0; i < ATAN2_DIM; i++) {
            for (int j = 0; j < ATAN2_DIM; j++) {
                float x0 = (float) i / ATAN2_DIM;
                float y0 = (float) j / ATAN2_DIM;

                atan2[j * ATAN2_DIM + i] = (float) Math.atan2(y0, x0);
            }
        }
    }

    public static float atan2(double y, double x) {
        float add, mul;

        if (x < 0.0f) {
            if (y < 0.0f) {
                x = -x;
                y = -y;

                mul = 1.0f;
            } else {
                x = -x;
                mul = -1.0f;
            }

            add = -3.141592653f;
        } else {
            if (y < 0.0f) {
                y = -y;
                mul = -1.0f;
            } else {
                mul = 1.0f;
            }

            add = 0.0f;
        }

        double invDiv = 1.0f / ((Math.max(x, y)) * INV_ATAN2_DIM_MINUS_1);

        int xi = (int) (x * invDiv);
        int yi = (int) (y * invDiv);

        return (atan2[yi * ATAN2_DIM + xi] + add) * mul;
    }
}