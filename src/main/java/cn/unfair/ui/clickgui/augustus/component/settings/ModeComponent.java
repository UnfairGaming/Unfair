package cn.unfair.ui.clickgui.augustus.component.settings;

import cn.unfair.property.properties.ModeProperty;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.ui.clickgui.augustus.component.Component;

import java.awt.*;

public class ModeComponent extends Component {
    private final ModeProperty property;

    public ModeComponent(AugustusClickGui gui, ModeProperty property) {
        super(gui);
        this.property = property;
    }

    @Override
    public float getHeight() {
        float penX = x + fw(property.getDisplayName() + ": ");
        float yy = 0.0F;
        String[] modes = property.getDisplayModes();
        for (int i = 0; i < modes.length; i++) {
            penX += fw(modes[i]);
            if (i < modes.length - 1) {
                penX += fw(", ");
            }
            if (penX > gui.getPosX() + gui.getGuiWidth() - 60.0F) {
                penX = x + fw(property.getDisplayName() + ": ");
                yy += fh() + 2.0F;
            }
        }
        return yy + fh() + 2.0F;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        gui.getNormalFont().drawString(property.getDisplayName() + ": ", x, y, new Color(200, 200, 200).getRGB(), false);
        float penX = x + fw(property.getDisplayName() + ": ");
        float penY = y;
        String[] modes = property.getDisplayModes();
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            boolean hovered = isHovered(mouseX, mouseY, penX, penY, fw(mode), fh());
            int col = property.getValue() == i ? gui.getAccent().getRGB() : new Color(200, 200, 200).getRGB();
            gui.getNormalFont().drawString(mode, penX, penY, hovered ? gui.getAccent().getRGB() : col, false);
            penX += fw(mode);
            if (i < modes.length - 1) {
                gui.getNormalFont().drawString(", ", penX, penY, new Color(200, 200, 200).getRGB(), false);
                penX += fw(", ");
            }
            if (penX > gui.getPosX() + gui.getGuiWidth() - 60.0F) {
                penX = x + fw(property.getDisplayName() + ": ");
                penY += fh() + 2.0F;
            }
        }
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        float penX = x + fw(property.getDisplayName() + ": ");
        float penY = y;
        String[] modes = property.getDisplayModes();
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            if (isHovered(mouseX, mouseY, penX, penY, fw(mode), fh())) {
                property.setValue(i);
                return;
            }
            penX += fw(mode);
            if (i < modes.length - 1) {
                penX += fw(", ");
            }
            if (penX > gui.getPosX() + gui.getGuiWidth() - 60.0F) {
                penX = x + fw(property.getDisplayName() + ": ");
                penY += fh() + 2.0F;
            }
        }
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }
}
