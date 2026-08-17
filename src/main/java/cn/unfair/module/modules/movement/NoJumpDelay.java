package cn.unfair.module.modules.movement;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.IntProperty;
import net.minecraft.client.Minecraft;

public class NoJumpDelay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty delay = new IntProperty("Delay", 0, 0, 8);

    public NoJumpDelay() {
        super("NoJumpDelay", false, true);
    }

    @EventTarget(Priority.HIGHEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.PRE) {
            mc.thePlayer.setJumpTicks(Math.min(mc.thePlayer.getJumpTicks(), this.delay.getValue() + 1));
        }
    }
}
