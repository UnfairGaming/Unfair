package cn.unfair.module.modules.movement;

import cn.unfair.module.Module;
import cn.unfair.event.EventTarget;
import cn.unfair.events.TickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;
public class SaveMoveKeys extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean wasInventoryOpen = false;

    public SaveMoveKeys() {
        super("SaveMoveKeys", false);
    }

    @Override
    public void onEnabled() {
        super.onEnabled();
        wasInventoryOpen = false;
    }

    @Override
    public void onDisabled() {
        super.onDisabled();
        wasInventoryOpen = false;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (mc.currentScreen != null) {
            wasInventoryOpen = true;
        } else {
            if (wasInventoryOpen) {
                // 恢复所有移动按键状态
                mc.addScheduledTask(() -> {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.getKeyCode(), 
                            Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()));
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindBack.getKeyCode(), 
                            Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()));
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.getKeyCode(), 
                            Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()));
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.getKeyCode(), 
                            Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()));
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.getKeyCode(), 
                            Keyboard.isKeyDown(mc.gameSettings.keyBindSprint.getKeyCode()));
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), 
                            Keyboard.isKeyDown(mc.gameSettings.keyBindSneak.getKeyCode()));
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(),
                            Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()));
                });
                wasInventoryOpen = false;
            }
        }
    }
}
