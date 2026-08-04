package cn.unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import cn.unfair.event.EventManager;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.ui.clickgui.raven.RavenClickGui;

public class ClickGui extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Raven", "Augustus"});
    public final BooleanProperty blur = new BooleanProperty("blur", false);
    private RavenClickGui ravenClickGui;
    private AugustusClickGui augustusClickGui;

    public ClickGui() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (mode.getValue() == 0){
            if (ravenClickGui == null) {
                ravenClickGui = new RavenClickGui();
                EventManager.register(ravenClickGui);
                ravenClickGui.initMain();
            }
            mc.displayGuiScreen(ravenClickGui);
        } else if (mode.getValue() == 1) {
            if (augustusClickGui == null) {
                augustusClickGui = new AugustusClickGui();
            }
            mc.displayGuiScreen(augustusClickGui);
        }
    }
}
