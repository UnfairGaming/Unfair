package cn.unfair.module.modules.misc.antibot;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.modules.misc.AntiBot;
import cn.unfair.util.render.RenderUtil;

import java.util.HashMap;
import java.util.Map;

public final class TicksVisibleCheck extends AntiBotCheck {
    private final Map<Integer, Integer> visibleTicks = new HashMap<>();

    public TicksVisibleCheck(AntiBot parent) {
        super(parent);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        mc.theWorld.playerEntities.forEach(player -> {
            int id = player.getEntityId();
            boolean visible = !player.isInvisible()
                    && RenderUtil.isInViewFrustum(player.getEntityBoundingBox(), 0.0F);
            int ticks = visibleTicks.getOrDefault(id, 0);
            ticks = visible ? ticks + 1 : 0;
            visibleTicks.put(id, ticks);
            if (ticks < 160) {
                Unfair.botManager.add(this, player);
            } else {
                Unfair.botManager.remove(this, player);
            }
        });
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        visibleTicks.clear();
    }

    @Override
    protected void onDisable() {
        Unfair.botManager.clear(this);
    }
}