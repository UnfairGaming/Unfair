package cn.unfair.ui.widget.impl;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.Gif;
import cn.unfair.ui.widget.Widget;
import cn.unfair.ui.widget.WidgetAlign;
import net.minecraft.client.gui.ScaledResolution;

public final class GifWidget extends Widget {
    private final String gifName;
    private Gif gif;

    public GifWidget(String gifName, float x, float y) {
        super("Gif-" + gifName, WidgetAlign.LEFT | WidgetAlign.TOP);
        this.gifName = gifName;
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean shouldRender() {
        Gif module = this.getGif();
        return module != null && module.shouldRenderWidget(this.gifName);
    }

    @Override
    public void render(float partialTicks) {
        Gif module = this.getGif();
        if (module == null) {
            return;
        }
        module.renderWidget(this.gifName, this.renderX, this.renderY, this.width, this.height,
                System.currentTimeMillis());
    }

    @Override
    public void updatePos(ScaledResolution sr) {
        Gif module = this.getGif();
        if (module != null) {
            float[] size = module.getWidgetSize(this.gifName);
            this.width = size[0];
            this.height = size[1];
        }
        super.updatePos(sr);
    }

    private Gif getGif() {
        if (this.gif == null && Unfair.moduleManager != null) {
            this.gif = (Gif) Unfair.moduleManager.getModule(Gif.class);
        }
        return this.gif;
    }
}
