package unfair.management.altmanager;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.lang.reflect.Field;

/**
 * Sets the Minecraft session via reflection (Minecraft 1.8.9 has no public setter).
 */
public final class SessionUtil {
    private SessionUtil() {
    }

    public static void setSession(Minecraft mc, Session session) {
        if (mc == null || session == null) return;
        try {
            Field sessionField = Minecraft.class.getDeclaredField("session");
            sessionField.setAccessible(true);
            sessionField.set(mc, session);
        } catch (Exception e) {
            // Try SRG name as fallback
            try {
                Field sessionField = Minecraft.class.getDeclaredField("field_71449_j");
                sessionField.setAccessible(true);
                sessionField.set(mc, session);
            } catch (Exception ignored) {
                throw new IllegalStateException("Unable to set Minecraft session via reflection", e);
            }
        }
    }
}
