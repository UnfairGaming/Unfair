package cn.unfair.module.modules.misc.antibot;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.module.modules.misc.AntiBot;

public final class NPCAntiBot extends AntiBotCheck {

    public NPCAntiBot(AntiBot parent) {
        super(parent);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        mc.theWorld.playerEntities.forEach(player -> {
            if (Math.abs(player.posX - player.prevPosX) > 0.1
                    || Math.abs(player.posY - player.prevPosY) > 0.1
                    || Math.abs(player.posZ - player.prevPosZ) > 0.1) {
                Unfair.botManager.remove(this, player);
            } else {
                Unfair.botManager.add(this, player);
            }
        });
    }

    @Override
    protected void onDisable() {
        Unfair.botManager.clear(this);
    }
}