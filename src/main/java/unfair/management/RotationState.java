package unfair.management;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MathHelper;

public class RotationState {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static int state = -1;
    private static float prevRenderYawOffset;
    private static float renderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    private static float prevRotationPitch;
    private static float rotationPitch;
    private static float smoothYaw;
    private static int priority;

    private static float calculateRenderYawOffset(float targetYaw, float currentYawOffset) {
        float newYawOffset = currentYawOffset;
        double deltaX = RotationState.mc.thePlayer.posX - RotationState.mc.thePlayer.prevPosX;
        double deltaZ = RotationState.mc.thePlayer.posZ - RotationState.mc.thePlayer.prevPosZ;
        if ((float) (deltaX * deltaX + deltaZ * deltaZ) > 0.0025000002f) {
            newYawOffset = (float) MathHelper.atan2(deltaZ, deltaX) * 180.0f / (float) Math.PI - 90.0f;
        }
        if (RotationState.mc.thePlayer.swingProgress > 0.0f) {
            newYawOffset = targetYaw;
        }
        float f4 = MathHelper.wrapAngleTo180_float(newYawOffset - currentYawOffset);
        float f5 = MathHelper.wrapAngleTo180_float(targetYaw - (currentYawOffset += f4 * 0.3f));
        if (f5 < -75.0f) {
            f5 = -75.0f;
        }
        if (f5 >= 75.0f) {
            f5 = 75.0f;
        }
        newYawOffset = targetYaw - f5;
        if (f5 * f5 > 2500.0f) {
            newYawOffset += f5 * 0.2f;
        }
        return newYawOffset;
    }

    public static void applyState(boolean bl, float f, float f2, float f3, int n) {
        state = bl ? 0 : state + 1;
        prevRenderYawOffset = renderYawOffset;
        renderYawOffset = bl ? RotationState.calculateRenderYawOffset(f, renderYawOffset) : RotationState.mc.thePlayer.renderYawOffset;
        prevRotationYawHead = rotationYawHead;
        rotationYawHead = bl ? f : RotationState.mc.thePlayer.rotationYawHead;
        prevRotationPitch = rotationPitch;
        rotationPitch = bl ? f2 : RotationState.mc.thePlayer.rotationPitch;
        smoothYaw = f3;
        priority = n;
    }

    public static boolean isActived() {
        return RotationState.isRotated(0);
    }

    public static boolean isRotated(int state) {
        if (RotationState.state < 0) return false;
        return RotationState.state <= state;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getPrevRotationPitch() {
        return prevRotationPitch;
    }

    public static float getRotationPitch() {
        return rotationPitch;
    }

    public static float getSmoothedYaw() {
        return smoothYaw;
    }

    public static float getPriority() {
        return priority;
    }
}
