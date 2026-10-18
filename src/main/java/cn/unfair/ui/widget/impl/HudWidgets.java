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
        Unfair.widgetManager.register(new RadarWidget());
        Unfair.widgetManager.register(new GifWidget("Deobf", 0.05F, 0.12F));
        Unfair.widgetManager.register(new GifWidget("Fucker", 0.83F, 0.12F));
        Unfair.widgetManager.register(new GifWidget("MonsterCat", 0.05F, 0.72F));
        Unfair.widgetManager.register(new GifWidget("Pig", 0.83F, 0.72F));
    }
}
