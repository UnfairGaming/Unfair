package cn.unfair.module.modules.misc.antibot;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.module.modules.misc.AntiBot;

public final class FuncraftAntiBot extends AntiBotCheck {

    public FuncraftAntiBot(AntiBot parent) {
        super(parent);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        mc.theWorld.playerEntities.forEach(player -> {
            if (player.getDisplayName().getUnformattedText().contains("§")) {
                Unfair.botManager.remove(this, player);
                return;
            }
            Unfair.botManager.add(this, player);
        });
    }

    @Override
    protected void onDisable() {
        Unfair.botManager.clear(this);
    }
}