package cn.unfair.ui.clickgui.augustus.component.settings;

import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.ui.clickgui.augustus.component.Component;

import java.awt.*;

public class BooleanComponent extends Component {
    private final BooleanProperty property;

    public BooleanComponent(AugustusClickGui gui, BooleanProperty property) {
        super(gui);
        this.property = property;
    }

    @Override
    public float getHeight() {
        return fh() + 2.0F;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        String name = property.getDisplayName() + ": ";
        int nameCol = new Color(200, 200, 200).getRGB();
        gui.getNormalFont().drawString(name, x, y, nameCol, false);
        boolean hovered = isHovered(mouseX, mouseY, x, y, fw(name + property.getValue()), fh());
        int vCol = property.getValue() ? new Color(0, 180, 0).getRGB() : new Color(180, 0, 0).getRGB();
        gui.getNormalFont().drawString(property.getValue().toString(), x + fw(name), y, hovered ? gui.getAccent().getRGB() : vCol, false);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        String full = property.getDisplayName() + ": " + property.getValue();
        if (isHovered(mouseX, mouseY, x, y, fw(full), fh())) {
            property.setValue(!property.getValue());
        }
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}
