package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;

public final class HudWidgets {
    private HudWidgets() {
    }

    public static void registerAll() {
        Unfair.widgetManager.register(new WatermarkWidget());
        Unfair.widgetManager.register(new ModuleListWidget());
        Unfair.widgetManager.register(new TargetHUDWidget());
        Unfair.widgetManager.register(new ScoreboardWidget());
        Unfair.widgetManager.register(new PotionEffectsWidget());
    }
}
