package cn.unfair.module.modules.render.targethud;

import cn.unfair.module.SubModule;
import cn.unfair.module.modules.render.TargetHUD;

public abstract class TargetHUDMode extends SubModule {
    public TargetHUDMode(String name) {
        super(name);
    }

    public abstract void render(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y);

    public abstract float[] getSize(TargetHUD targetHUD, TargetHUD.RenderData data);

    public boolean shouldRenderEffects(TargetHUD targetHUD) {
        return false;
    }

    public void renderMask(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y, int color) {
    }

    public boolean shouldAnimateHealth() {
        return false;
    }
}
