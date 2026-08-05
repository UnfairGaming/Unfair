package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.Scoreboard;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public class ScoreboardWidget extends Widget {
    private Scoreboard scoreboard;

    public ScoreboardWidget() {
        super("Scoreboard", WidgetAlign.RIGHT | WidgetAlign.MIDDLE);
        this.x = 0.99F;
        this.y = 0.5F;
    }

    @Override
    public boolean shouldRender() {
        Scoreboard module = this.getScoreboard();
        return module != null && module.shouldRenderWidget();
    }

    @Override
    public boolean shouldRenderBlurMask() {
        Scoreboard module = this.getScoreboard();
        return module != null && module.shouldRenderWidgetEffects();
    }

    @Override
    public void renderBlurMask(float partialTicks) {
        Scoreboard module = this.getScoreboard();
        if (module == null) return;
        this.updateBounds(module);
        module.renderWidgetMask(this.renderX, this.renderY, 0xFF000000);
    }

    @Override
    public boolean shouldRenderBloomMask() {
        return this.shouldRenderBlurMask();
    }

    @Override
    public void renderBloomMask(float partialTicks) {
        Scoreboard module = this.getScoreboard();
        if (module == null) return;
        this.updateBounds(module);
        module.renderWidgetMask(this.renderX, this.renderY, 0xFFFFFFFF);
    }

    @Override
    public void render(float partialTicks) {
        Scoreboard module = this.getScoreboard();
        if (module == null) return;
        this.updateBounds(module);
        module.renderWidget(this.renderX, this.renderY);
    }

    @Override
    public void updatePos(ScaledResolution sr) {
        Scoreboard module = this.getScoreboard();
        if (module != null) {
            this.updateBounds(module);
        }
        super.updatePos(sr);
    }

    private Scoreboard getScoreboard() {
        if (this.scoreboard == null && Unfair.moduleManager != null) {
            this.scoreboard = (Scoreboard) Unfair.moduleManager.getModule(Scoreboard.class);
        }
        return this.scoreboard;
    }

    private void updateBounds(Scoreboard scoreboard) {
        float[] size = scoreboard.getWidgetSize();
        this.width = size[0];
        this.height = size[1];
    }
}
