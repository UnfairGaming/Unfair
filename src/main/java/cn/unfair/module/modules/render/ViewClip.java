package cn.unfair.module.modules.render;

import cn.unfair.module.Module;
import net.minecraft.client.Minecraft;

public class ViewClip extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public ViewClip() {
        super("ViewClip", false, true);
    }

    @Override
    public void onEnabled() {
        if (mc.theWorld != null) {
            mc.renderGlobal.loadRenderers();
        }
    }

    @Override
    public void onDisabled() {
        if (mc.theWorld != null) {
            mc.renderGlobal.loadRenderers();
        }
    }
}
