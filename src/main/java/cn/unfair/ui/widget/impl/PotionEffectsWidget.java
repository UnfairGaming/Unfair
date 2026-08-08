package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.PotionEffects;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public class PotionEffectsWidget extends Widget {
    private PotionEffects potionEffects;

    public PotionEffectsWidget() {
        super("PotionEffects", WidgetAlign.RIGHT | WidgetAlign.TOP);
        this.x = 0.99F;
        this.y = 0.28F;
    }

    @Override
    public boolean shouldRender() {
        PotionEffects module = this.getPotionEffects();
        return module != null && module.shouldRenderWidget();
    }

    @Override
    public void render(float partialTicks) {
        PotionEffects module = this.getPotionEffects();
        if (module == null) return;
        module.tickBlink();
        module.renderWidget(this.renderX, this.renderY);
    }

    @Override
    public void updatePos(ScaledResolution sr) {
        PotionEffects module = this.getPotionEffects();
        if (module != null) {
            float[] size = module.getWidgetSize();
            this.width = size[0];
            this.height = size[1];
        }
        super.updatePos(sr);
    }

    private PotionEffects getPotionEffects() {
        if (this.potionEffects == null && Unfair.moduleManager != null) {
            this.potionEffects = (PotionEffects) Unfair.moduleManager.getModule(PotionEffects.class);
        }
        return this.potionEffects;
    }
}
