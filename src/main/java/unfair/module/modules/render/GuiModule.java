package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.ModeProperty;
import unfair.ui.clickgui.modern.ModernClickGui;
import unfair.ui.clickgui.raven.RavenClickGui;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Raven", "Modern"});
    public final BooleanProperty blur = new BooleanProperty("blur", false);
    private RavenClickGui ravenClickGui;
    private ModernClickGui modernClickGui;

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (mode.getValue() == 0){
            if (ravenClickGui == null) {
                ravenClickGui = new RavenClickGui();
                ravenClickGui.initMain();
            }
            mc.displayGuiScreen(ravenClickGui);
        } else {
            if (modernClickGui == null) {
                modernClickGui = new ModernClickGui();
            }
            mc.displayGuiScreen(modernClickGui);
        }
    }
}
