package net.optifine.gui;

import net.minecraft.client.gui.GuiButton;

@SuppressWarnings({"unchecked", "rawtypes", "deprecation"})
public class GuiButtonOF extends GuiButton {
    public GuiButtonOF(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        super(buttonId, x, y, widthIn, heightIn, buttonText);
    }

    public GuiButtonOF(int buttonId, int x, int y, String buttonText) {
        super(buttonId, x, y, buttonText);
    }
}
