package cn.unfair.util.player;

import net.minecraft.client.Minecraft;

/**
 * Low-level container click helpers for anticheat-friendly instant transfers.
 * Packets are produced through the standard
 * {@link net.minecraft.client.multiplayer.PlayerControllerMP#windowClick} path so
 * they translate correctly through Via and fire the normal client events.
 */
public final class ClickUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private ClickUtil() {
    }

    /**
     * "Pre-click" on a container slot using the pick-block (middle mouse)
     * click: a real click packet on the slot that is a no-op in survival, used
     * to make the following transfer look like a normal interaction sequence.
     */
    public static void middleClick(int windowId, int slot) {
        mc.playerController.windowClick(windowId, slot, 0, 3, mc.thePlayer);
    }

    /**
     * Transfers the slot's stack into a hotbar slot using number-key / hotbar
     * swap semantics (button = hotbar index, mode 2). The stack ends up in the
     * hotbar slot and the source slot is emptied.
     */
    public static void swapWithHotbar(int windowId, int slot, int hotbarIndex) {
        mc.playerController.windowClick(windowId, slot, hotbarIndex, 2, mc.thePlayer);
    }
}
