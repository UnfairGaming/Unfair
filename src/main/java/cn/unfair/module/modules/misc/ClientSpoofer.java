package cn.unfair.module.modules.misc;

import cn.unfair.module.Module;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.TextProperty;

public class ClientSpoofer extends Module {
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"Lunar", "Feather", "Custom"});
    public final TextProperty customName = new TextProperty("custom-name", "A client", () -> this.mode.getValue() == 2);

    public ClientSpoofer() {
        super("ClientSpoofer", false);
    }

    public String getClientBrand() {
        switch (this.mode.getValue()) {
            case 0:
                return "lunarclient:v2.14.5-2411";
            case 1:
                return "Feather Forge";
            case 2:
                return this.customName.getValue();
            default:
                return "";
        }
    }
}
