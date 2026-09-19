package cn.unfair.module.modules.misc.antibot;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.module.modules.misc.AntiBot;
import net.minecraft.entity.Entity;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public final class MiddleClickBot extends AntiBotCheck {

    private boolean down;

    public MiddleClickBot(AntiBot parent) {
        super(parent);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        if (Mouse.isButtonDown(2) || (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && mc.gameSettings.keyBindAttack.isKeyDown())) {
            if (down) {
                return;
            }
            down = true;

            if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY) {
                Entity entity = mc.objectMouseOver.entityHit;
                if (Unfair.botManager.contains(this, entity)) {
                    Unfair.botManager.remove(this, entity);
                } else {
                    Unfair.botManager.add(this, entity);
                }
            }
        } else {
            down = false;
        }
    }
}