package cn.unfair.module.modules.misc;

import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.module.modules.misc.antibot.*;
import cn.unfair.property.properties.BooleanProperty;

import java.util.LinkedHashMap;
import java.util.Map;

public class AntiBot extends Module {
    public final BooleanProperty funcraftCheck = new BooleanProperty("Funcraft Check", false);
    public final BooleanProperty hypixelTestCheck = new BooleanProperty("Hypixel Test Check", false);
    public final BooleanProperty npcCheck = new BooleanProperty("NPC Detection Check", false);
    public final BooleanProperty duplicateNameCheck = new BooleanProperty("Duplicate Name Check", false);
    public final BooleanProperty pingCheck = new BooleanProperty("No Ping Check", false);
    public final BooleanProperty negativeIDCheck = new BooleanProperty("Negative Unique ID Check", false);
    public final BooleanProperty duplicateIDCheck = new BooleanProperty("Duplicate Unique ID Check", false);
    public final BooleanProperty ticksVisibleCheck = new BooleanProperty("Time Visible Check", false);
    public final BooleanProperty middleClickCheck = new BooleanProperty("Middle Click Bot", false);

    private final Map<BooleanProperty, AntiBotCheck> checks = new LinkedHashMap<>();

    public AntiBot() {
        super("AntiBot", false, true);
        checks.put(funcraftCheck, new FuncraftAntiBot(this));
        checks.put(hypixelTestCheck, new HypixelTestCheck(this));
        checks.put(npcCheck, new NPCAntiBot(this));
        checks.put(duplicateNameCheck, new DuplicateNameCheck(this));
        checks.put(pingCheck, new PingCheck(this));
        checks.put(negativeIDCheck, new NegativeIDCheck(this));
        checks.put(duplicateIDCheck, new DuplicateIDCheck(this));
        checks.put(ticksVisibleCheck, new TicksVisibleCheck(this));
        checks.put(middleClickCheck, new MiddleClickBot(this));
    }

    @Override
    public void onEnabled() {
        syncChecks();
    }

    @Override
    public void onDisabled() {
        for (AntiBotCheck check : checks.values()) {
            check.unregister();
        }
        Unfair.botManager.clear();
    }

    @Override
    public void verifyValue(String value) {
        syncChecks();
    }

    private void syncChecks() {
        for (Map.Entry<BooleanProperty, AntiBotCheck> entry : checks.entrySet()) {
            entry.getValue().sync(entry.getKey().getValue());
        }
    }
}