package cn.unfair.ui.clickgui.augustus.component;

import cn.unfair.module.Module;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;

import java.awt.*;

public class ModuleComponent {
    private static final float SIDEBAR_WIDTH = 90.0F;
    private final AugustusClickGui gui;
    private final Module module;

    public ModuleComponent(AugustusClickGui gui, Module module) {
        this.gui = gui;
        this.module = module;
    }

    public float getHeight() {
        return gui.getNormalFont().getHeight() + 2.0F;
    }

    public void drawScreen(int mouseX, int mouseY, float x, float y) {
        int col = module.isEnabled() ? gui.getAccent().getRGB() : new Color(200, 200, 200).getRGB();
        float textX = x + 8.0F;
        float textY = y - 4.0F;
        if (module == gui.getSelectedModule()) {
            gui.getNormalFont().drawString(">", textX, textY, col, false);
            textX += gui.getNormalFont().getStringWidth(">") + 2.0F;
        }
        gui.getNormalFont().drawString(module.getName(), textX, textY, col, false);
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton, float x, float y) {
        float h = gui.getNormalFont().getHeight();
        float tx = x + 8.0F;
        if (mouseX >= tx && mouseX <= tx + SIDEBAR_WIDTH && mouseY >= y - 3.0F && mouseY <= y - 3.0F + h + 2.0F) {
            if (mouseButton == 0) {
                module.toggle();
                return true;
            }
            if (mouseButton == 1) {
                gui.selectModule(module);
                return true;
            }
        }
        return false;
    }
}
