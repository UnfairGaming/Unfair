package cn.unfair.module.modules.misc.antibot;

import cn.unfair.event.EventManager;
import cn.unfair.module.modules.misc.AntiBot;
import net.minecraft.client.Minecraft;

public abstract class AntiBotCheck {
    protected static final Minecraft mc = Minecraft.getMinecraft();
    protected final AntiBot parent;
    private boolean registered;

    protected AntiBotCheck(AntiBot parent) {
        this.parent = parent;
    }

    public final void sync(boolean enabled) {
        if (enabled && !registered) {
            registered = true;
            EventManager.register(this);
            onEnable();
        } else if (!enabled && registered) {
            registered = false;
            EventManager.unregister(this);
            onDisable();
        }
    }

    public final void unregister() {
        if (registered) {
            registered = false;
            EventManager.unregister(this);
            onDisable();
        }
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }
}
