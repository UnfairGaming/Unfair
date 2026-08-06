package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.anticheat.AnticheatManager;
import lombok.Getter;

public class AntiCheat extends Module {
    public final BooleanProperty noSlowCheck = new BooleanProperty("no-slow-check", true);
    public final BooleanProperty autoBlockCheck = new BooleanProperty("auto-block-check", true);
    public final BooleanProperty eagleCheck = new BooleanProperty("eagle-check", false);
    public final BooleanProperty scaffoldCheck = new BooleanProperty("scaffold-check", false);
    public final BooleanProperty noSlowABCheck = new BooleanProperty("no-slow-ab-check", true);
    public final BooleanProperty motionCheck = new BooleanProperty("motion-check", true);
    public final BooleanProperty invalidSwingCheck = new BooleanProperty("invalid-swing-check", true);
    public final BooleanProperty autoClickerCheck = new BooleanProperty("autoclicker-check", true);
    public final IntProperty vl = new IntProperty("vl", 10, 1, 100);
    public final IntProperty cooldown = new IntProperty("cooldown", 5, 0, 60);

    @Getter
    private final AnticheatManager manager;

    public AntiCheat() {
        super("AntiCheat", false);
        manager = new AnticheatManager(this);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.PRE) {
            manager.tick();
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) manager.onPacket(event);
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        manager.clearPlayers();
    }

    @Override
    public void onEnabled() {
        manager.reloadChecks();
    }

    @Override
    public void onDisabled() {
        manager.clearPlayers();
    }

}
