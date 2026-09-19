package cn.unfair.management;

import cn.unfair.event.EventTarget;
import cn.unfair.events.LoadWorldEvent;
import net.minecraft.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;

public class BotManager {
    private final HashMap<Object, ArrayList<Integer>> bots = new HashMap<>();

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        clear();
    }

    public boolean contains(Entity target) {
        for (ArrayList<Integer> entities : bots.values()) {
            if (entities.contains(target.getEntityId())) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(Object object, Entity target) {
        if (!bots.containsKey(object)) {
            return false;
        }
        return bots.get(object).contains(target.getEntityId());
    }

    public void add(Object object, Entity entity) {
        int id = entity.getEntityId();
        if (!bots.containsKey(object)) {
            bots.put(object, new ArrayList<>());
        }
        ArrayList<Integer> entities = bots.get(object);
        if (!entities.contains(id)) {
            entities.add(id);
        }
    }

    public void remove(Object object, Entity entity) {
        if (bots.containsKey(object)) {
            ArrayList<Integer> entities = bots.get(object);
            entities.remove((Object) entity.getEntityId());
        }
    }

    public void clear() {
        bots.clear();
    }

    public void clear(Object object) {
        if (!bots.containsKey(object)) {
            return;
        }
        bots.get(object).clear();
    }
}
