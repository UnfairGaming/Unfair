package cn.unfair.ui.clickgui.augustus.panel;

import cn.unfair.Unfair;
import cn.unfair.module.Category;
import cn.unfair.module.Module;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.ui.clickgui.augustus.component.ModuleComponent;

import java.util.ArrayList;
import java.util.List;

public class CategoryPanel {
    private final AugustusClickGui gui;
    private final Category category;
    private final List<ModuleComponent> modules = new ArrayList<>();

    public CategoryPanel(AugustusClickGui gui, Category category) {
        this.gui = gui;
        this.category = category;
        for (Module module : Unfair.moduleManager.getModulesByCategory(category)) {
            modules.add(new ModuleComponent(gui, module));
        }
    }

    public Category getCategory() {
        return category;
    }

    public float getContentHeight() {
        if (modules.isEmpty()) {
            return 0.0F;
        }
        return modules.size() * modules.get(0).getHeight();
    }

    public void drawScreen(int mouseX, int mouseY, float scroll) {
        float x = gui.getPosX();
        float y = gui.getPosY() + 26.0F + scroll;
        for (ModuleComponent module : modules) {
            module.drawScreen(mouseX, mouseY, x, y);
            y += module.getHeight();
        }
    }

    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton, float scroll) {
        float x = gui.getPosX();
        float y = gui.getPosY() + 26.0F + scroll;
        for (ModuleComponent module : modules) {
            if (module.mouseClicked(mouseX, mouseY, mouseButton, x, y)) {
                return true;
            }
            y += module.getHeight();
        }
        return false;
    }
}
