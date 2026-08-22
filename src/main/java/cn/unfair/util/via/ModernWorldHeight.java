package cn.unfair.util.via;

import com.viaversion.viabackwards.protocol.v1_17to1_16_4.Protocol1_17To1_16_4;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.data.entity.EntityTracker;

/**
 * Height-limit model used while a 1.17+ server is translated to the 1.8 client.
 */
public final class ModernWorldHeight {
    private static final int LEGACY_BOTTOM_Y = 0;
    private static final int LEGACY_SECTION_COUNT = 16;

    private static volatile int bottomY = LEGACY_BOTTOM_Y;
    private static volatile int sectionCount = LEGACY_SECTION_COUNT;
    private static volatile boolean modern;

    private ModernWorldHeight() {
    }

    public static void sync(UserConnection connection) {
        if (connection == null || connection.getProtocolInfo() == null
                || !connection.getProtocolInfo().getPipeline().contains(Protocol1_17To1_16_4.class)) {
            reset();
            return;
        }

        EntityTracker tracker = connection.getEntityTracker(Protocol1_17To1_16_4.class);
        if (tracker != null) {
            configure(tracker.currentMinY(), tracker.currentWorldSectionHeight());
        }
    }

    public static void configure(int minY, int worldSectionCount) {
        if (worldSectionCount <= 0) {
            reset();
            return;
        }
        bottomY = minY;
        sectionCount = worldSectionCount;
        modern = true;
    }

    public static void reset() {
        bottomY = LEGACY_BOTTOM_Y;
        sectionCount = LEGACY_SECTION_COUNT;
        modern = false;
    }

    public static int getBottomY() {
        return bottomY;
    }

    public static boolean isModern() {
        return modern;
    }

    public static int getHeight() {
        return sectionCount << 4;
    }

    public static int getTopYInclusive() {
        return bottomY + getHeight() - 1;
    }

    public static int getBottomSectionY() {
        return bottomY >> 4;
    }

    public static int getSectionCount() {
        return sectionCount;
    }

    public static boolean isValidY(int y) {
        return y >= bottomY && y <= getTopYInclusive();
    }

    public static int getSectionIndex(int y) {
        return (y >> 4) - getBottomSectionY();
    }

    public static int sectionIndexToY(int index) {
        return (index + getBottomSectionY()) << 4;
    }
}
