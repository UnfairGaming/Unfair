package cn.unfair.util.player;

import net.minecraft.client.Minecraft;

public final class ClickUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private ClickUtil() {
    }

    public static void middleClick(int windowId, int slot) {
        mc.playerController.windowClick(windowId, slot, 0, 3, mc.thePlayer);
    }

    public static void swapWithHotbar(int windowId, int slot, int hotbarIndex) {
        mc.playerController.windowClick(windowId, slot, hotbarIndex, 2, mc.thePlayer);
    }
}
