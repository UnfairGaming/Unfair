package cn.unfair.module.modules.movement;

import net.minecraft.client.Minecraft;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.IntProperty;

public class NoJumpDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delay = new IntProperty("delay", 0, 0, 8);

    public NoJumpDelay() {
        super("NoJumpDelay", false);
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            mc.thePlayer.setJumpTicks(Math.min(mc.thePlayer.getJumpTicks(), this.delay.getValue() + 1));
        }
    }
}
