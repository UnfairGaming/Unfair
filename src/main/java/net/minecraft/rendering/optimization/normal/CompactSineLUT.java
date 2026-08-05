package net.minecraft.rendering.optimization.normal;

import net.minecraft.util.MathHelper;

import java.lang.reflect.Field;

public class CompactSineLUT {
    private static final int[] SINE_TABLE_INT = new int[16384 + 1];
    private static final float SINE_TABLE_MIDPOINT;

    static {
        float[] SINE_TABLE;
        try {
            Field field;
            try {
                field = MathHelper.class.getDeclaredField("SIN_TABLE");
            } catch (NoSuchFieldException e) {
                // 1.17 mappings
                field = MathHelper.class.getDeclaredField("field_76144_a");
            }
            field.setAccessible(true);
            SINE_TABLE = (float[]) field.get(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            // taking this into our own hands
            SINE_TABLE = new float[65536];
            int i;
            for (i = 0; i < 65536; ++i) {
                SINE_TABLE[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
            }
        }
        // Copy the sine table, covering to raw int bits
        for (int i = 0; i < SINE_TABLE_INT.length; i++) {
            SINE_TABLE_INT[i] = Float.floatToRawIntBits(SINE_TABLE[i]);
        }

        SINE_TABLE_MIDPOINT = SINE_TABLE[SINE_TABLE.length / 2];

        // Test that the lookup table is correct during runtime
        for (int i = 0; i < SINE_TABLE.length; i++) {
            float expected = SINE_TABLE[i];
            float value = lookup(i);

            if (expected != value) {
                throw new IllegalArgumentException(String.format("LUT error at index %d (expected: %s, found: %s)", i, expected, value));
            }
        }
    }

    // [VanillaCopy] MathHelper#sin(float)
    public static float sin(float f) {
        return lookup((int) (f * 10430.378f) & 0xFFFF);
    }

    // [VanillaCopy] MathHelper#cos(float)
    public static float cos(float f) {
        return lookup((int) (f * 10430.378f + 16384.0f) & 0xFFFF);
    }

    private static float lookup(int index) {
        // A special case... Is there some way to eliminate this?
        if (index == 32768) {
            return SINE_TABLE_MIDPOINT;
        }

        // Trigonometric identity: sin(-x) = -sin(x)
        // Given a domain of 0 <= x <= 2*pi, just negate the value if x > pi.
        // This allows the sin table size to be halved.
        int neg = (index & 0x8000) << 16;

        // All bits set if (pi/2 <= x), none set otherwise
        // Extracts the 15th bit from 'half'
        int mask = (index << 17) >> 31;

        // Trigonometric identity: sin(x) = sin(pi/2 - x)
        int pos = (0x8001 & mask) + (index ^ mask);

        // Wrap the position in the table. Moving this down to immediately before the array access
        // seems to help the Hotspot compiler optimize the bit math better.
        pos &= 0x7fff;

        // Fetch the corresponding value from the LUT and invert the sign bit as needed
        // This directly manipulate the sign bit on the float bits to simplify logic
        return Float.intBitsToFloat(SINE_TABLE_INT[pos] ^ neg);
    }
}