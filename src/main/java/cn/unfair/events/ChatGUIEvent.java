package cn.unfair.events;

import cn.unfair.event.events.Event;

public class ChatGUIEvent implements Event {
    private final int mouseX;
    private final int mouseY;
    private final float partialTicks;

    public ChatGUIEvent(int mouseX, int mouseY, float partialTicks) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.partialTicks = partialTicks;
    }

    public int getMouseX() {
        return this.mouseX;
    }

    public int getMouseY() {
        return this.mouseY;
    }

    public float getPartialTicks() {
        return this.partialTicks;
    }
}
