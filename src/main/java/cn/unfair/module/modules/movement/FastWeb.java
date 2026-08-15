package cn.unfair.module.modules.movement;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.StuckInBlockEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import net.minecraft.client.Minecraft;

public class FastWeb extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private int playerInWebTick = 0;
    private int ticksInWeb = 0;

    public FastWeb() {
        super("FastWeb", false);
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        if (this.ticksInWeb > 1) {
            mc.thePlayer.movementInput.jump = false;
        }
    }

    @EventTarget
    public void onStuck(StuckInBlockEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.getEntity() != mc.thePlayer) {
            return;
        }

        this.playerInWebTick = mc.thePlayer.ticksExisted;
        this.ticksInWeb++;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || event.type() != EventType.POST) {
            return;
        }

        if (this.playerInWebTick < mc.thePlayer.ticksExisted) {
            this.ticksInWeb = 0;
        }
    }

    public boolean shouldBoostWeb() {
        return this.isEnabled() && this.ticksInWeb > 5;
    }

    @Override
    public void onDisabled() {
        this.playerInWebTick = 0;
        this.ticksInWeb = 0;
    }
}
