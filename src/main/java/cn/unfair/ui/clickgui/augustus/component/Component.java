package cn.unfair.ui.clickgui.augustus.component;

import cn.unfair.ui.clickgui.augustus.AugustusClickGui;

public abstract class Component {
    protected final AugustusClickGui gui;
    protected float x;
    protected float y;

    protected Component(AugustusClickGui gui) {
        this.gui = gui;
    }

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    protected float fw(String text) {
        return gui.getNormalFont().getStringWidth(text);
    }

    protected float fh() {
        return gui.getNormalFont().getHeight();
    }

    protected boolean isHovered(int mouseX, int mouseY, float hx, float hy, float w, float h) {
        return mouseX >= hx && mouseX <= hx + w && mouseY >= hy && mouseY <= hy + h;
    }

    public abstract float getHeight();

    public abstract void drawScreen(int mouseX, int mouseY);

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
    }

    public void keyTyped(char typedChar, int keyCode) {
    }

    public boolean isVisible() {
        return true;
    }
}
