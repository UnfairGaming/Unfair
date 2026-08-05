package cn.unfair.module.modules.movement;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;

public class AntiAFK extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int lastInput;

    public AntiAFK() {
        super("AntiAFK", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE && this.isEnabled()) {
            GameSettings gameSettings = mc.gameSettings;
            if (gameSettings.keyBindJump.isPressed() || gameSettings.keyBindRight.isPressed() || gameSettings.keyBindForward.isPressed() || gameSettings.keyBindLeft.isPressed() || gameSettings.keyBindBack.isPressed()) {
                lastInput = 0;
            }
            lastInput++;
            if (lastInput < 20 * 10) return;
            if (mc.thePlayer.ticksExisted % 5 == 0) {
                mc.gameSettings.keyBindRight.pressed = false;
                mc.gameSettings.keyBindLeft.pressed = false;
                mc.gameSettings.keyBindJump.pressed = false;
            }
            if (mc.thePlayer.ticksExisted % 20 == 0) {
                if (mc.thePlayer.ticksExisted % 40 == 0) {
                    mc.gameSettings.keyBindRight.pressed = true;
                } else {
                    mc.gameSettings.keyBindLeft.pressed = true;
                }
            }
            if (mc.thePlayer.ticksExisted % 100 == 0) {
                mc.gameSettings.keyBindJump.pressed = true;
            }
        }
    }
}
