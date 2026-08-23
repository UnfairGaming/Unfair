package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.anticheat.AntiCheatManager;
import lombok.Getter;

public class AntiCheat extends Module {
    public final BooleanProperty noSlowCheck = new BooleanProperty("No Slow Check", true);
    public final BooleanProperty autoBlockCheck = new BooleanProperty("Auto Block Check", true);
    public final BooleanProperty eagleCheck = new BooleanProperty("Eagle Check", false);
    public final BooleanProperty scaffoldCheck = new BooleanProperty("Scaffold Check", false);
    public final BooleanProperty noSlowABCheck = new BooleanProperty("No Slow Ab Check", true);
    public final BooleanProperty motionCheck = new BooleanProperty("Motion Check", true);
    public final BooleanProperty invalidSwingCheck = new BooleanProperty("Invalid Swing Check", true);
    public final BooleanProperty autoClickerCheck = new BooleanProperty("Autoclicker Check", true);
    public final IntProperty vl = new IntProperty("Vl", 10, 1, 100);
    public final IntProperty cooldown = new IntProperty("Cooldown", 5, 0, 60);

    @Getter
    private final AntiCheatManager manager;

    public AntiCheat() {
        super("AntiCheat", false);
        manager = new AntiCheatManager(this);
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

    @Override
    public void verifyValue(String string) {
        if (manager != null) {
            manager.reloadChecks();
            manager.clearPlayers();
        }
    }

}
