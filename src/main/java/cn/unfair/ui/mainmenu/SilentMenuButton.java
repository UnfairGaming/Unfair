package cn.unfair.ui.mainmenu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class SilentMenuButton extends GuiButton {
    public SilentMenuButton(int buttonId, int x, int y, int width, int height, String buttonText) {
        super(buttonId, x, y, width, height, buttonText);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
    }
}
