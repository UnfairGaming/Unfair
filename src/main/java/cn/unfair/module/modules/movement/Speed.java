package cn.unfair.module.modules.movement;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.TimerManipulationEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.util.ChatUtil;
import net.minecraft.client.Minecraft;

public class Speed extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty logging = new BooleanProperty("logging", false);
    public final FloatProperty startTicks = new FloatProperty("start-ticks", 2.0F, 0.0F, 10.0F);
    public final FloatProperty skipTicks = new FloatProperty("skip-ticks", 2.0F, 1.0F, 10.0F);
    public final FloatProperty ticks = new FloatProperty("ticks", 3.0F, 1.0F, 10.0F);

    private int airTicks;
    private boolean canTimer;
    private boolean timer;
    private boolean skipping;
    private long shifted;
    private long previousTime;

    public Speed() {
        super("Speed", false);
    }

    @Override
    public void onEnabled() {
        this.reset();
    }

    @Override
    public void onDisabled() {
        this.reset();
    }

    private void reset() {
        this.airTicks = 0;
        this.canTimer = false;
        this.timer = false;
        this.skipping = false;
        this.shifted = 0L;
        this.previousTime = 0L;
    }

    private void log(String message) {
        if (this.logging.getValue()) {
            ChatUtil.sendFormatted(message);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (this.airTicks >= this.startTicks.getValue().intValue() && !this.canTimer) {
            this.canTimer = true;
            this.timer = false;
            this.skipping = true;
            this.shifted = 0L;
            this.log("Skipping " + this.skipTicks.getValue().intValue() + " ticks");
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (mc.thePlayer.onGround) {
            this.airTicks = 0;
            this.canTimer = false;
        } else {
            this.airTicks++;
        }

        if (this.canTimer && !this.timer) {
            this.timer = true;
            for (int i = 0; i < this.ticks.getValue().intValue(); i++) {
                mc.thePlayer.onUpdate();
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        mc.thePlayer.movementInput.jump = true;
    }

    @EventTarget
    public void onTimerManipulation(TimerManipulationEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        long now = event.getTime();
        if (this.previousTime == 0L) {
            this.previousTime = now;
            return;
        }

        if (this.skipping) {
            this.shifted += now - this.previousTime;
            if (this.shifted >= this.skipTicks.getValue().intValue() * 50L) {
                this.shifted = 0L;
                this.skipping = false;
                this.log("Skip finished");
            }
        } else {
            this.shifted = 0L;
        }

        this.previousTime = now;
        event.setTime(now - this.shifted);
    }
}
