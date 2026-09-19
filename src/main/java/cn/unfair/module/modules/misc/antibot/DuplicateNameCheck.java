package cn.unfair.module.modules.misc.antibot;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.module.modules.misc.AntiBot;

public final class DuplicateNameCheck extends AntiBotCheck {

    public DuplicateNameCheck(AntiBot parent) {
        super(parent);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        mc.theWorld.playerEntities.forEach(player -> {
            String name = player.getDisplayName().getUnformattedText();
            if (mc.theWorld.playerEntities.stream().anyMatch(player2 ->
                    player2 != player && name.equals(player2.getDisplayName().getUnformattedText()))) {
                Unfair.botManager.add(this, player);
            }
        });
    }

    @Override
    protected void onDisable() {
        Unfair.botManager.clear(this);
    }
}