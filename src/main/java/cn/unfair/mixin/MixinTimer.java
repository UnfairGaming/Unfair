package cn.unfair.mixin;

import cn.unfair.event.EventManager;
import cn.unfair.events.TimerManipulationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Timer.class)
public abstract class MixinTimer {
    @Shadow
    float ticksPerSecond;
    @Shadow
    public int elapsedTicks;
    @Shadow
    public float renderPartialTicks;
    @Shadow
    public float timerSpeed;
    @Shadow
    public float elapsedPartialTicks;
    @Shadow
    private long lastSyncSysClock;

    /**
     * @author larryngton/demise, ported to Unfair
     * @reason TickBase relies on manipulating the timer's source time directly.
     */
    @Overwrite
    public void updateTimer() {
        TimerManipulationEvent event = new TimerManipulationEvent(Minecraft.getSystemTime());
        EventManager.call(event);

        long time = event.getTime();
        this.elapsedPartialTicks += (float) (time - this.lastSyncSysClock) / (1000.0F / this.ticksPerSecond) * this.timerSpeed;
        this.lastSyncSysClock = time;
        this.elapsedTicks = (int) this.elapsedPartialTicks;
        this.elapsedPartialTicks -= this.elapsedTicks;
        this.renderPartialTicks = this.elapsedPartialTicks;
    }
}
