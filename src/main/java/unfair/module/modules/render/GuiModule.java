package unfair.module.modules.render;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.ui.ClickGui;

public class GuiModule extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final BooleanProperty blur = new BooleanProperty("blur", false);
    private ClickGui clickGui;

    public GuiModule() {
        super("ClickGui", false);
        setKey(Keyboard.KEY_RSHIFT);
    }

    @Override
    public void onEnabled() {
        setEnabled(false);
        if (clickGui == null) {
            clickGui = new ClickGui();
            clickGui.initMain();
        }
        mc.displayGuiScreen(clickGui);
    }
}
