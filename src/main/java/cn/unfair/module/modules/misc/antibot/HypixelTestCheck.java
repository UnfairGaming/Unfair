package cn.unfair.module.modules.misc.antibot;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.modules.misc.AntiBot;
import cn.unfair.util.client.TimerUtil;

public final class HypixelTestCheck extends AntiBotCheck {
    private final TimerUtil delay = new TimerUtil();

    public HypixelTestCheck(AntiBot parent) {
        super(parent);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        mc.theWorld.playerEntities.forEach(player -> {
            String name = player.getCustomNameTag().toLowerCase();
            if (player.ticksExisted < 10) {
                if (delay.hasTimeElapsed(5000)) {
                    if (!player.onGround) {
                        Unfair.botManager.add(this, player);
                    }
                }
            } else if (player.onGround) {
                Unfair.botManager.remove(this, player);
            }
            if (name.contains("§c") && name.contains("§r")) {
                Unfair.botManager.add(this, player);
            }
        });
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        delay.reset();
        Unfair.botManager.clear(this);
    }

    @Override
    protected void onDisable() {
        Unfair.botManager.clear(this);
    }
}