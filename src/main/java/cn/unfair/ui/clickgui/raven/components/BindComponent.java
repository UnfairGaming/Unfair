package cn.unfair.ui.clickgui.raven.components;

import cn.unfair.module.modules.render.ClickGui;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.ui.clickgui.raven.Component;
import cn.unfair.ui.clickgui.raven.dataset.BindStage;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.unfair.config.Config.mc;

public class BindComponent implements Component {
    public static boolean isAnyBinding = false;
    private final ModuleComponent parentModule;
    public boolean isBinding;
    private int offsetY;
    private int x;
    private int y;

    public BindComponent(ModuleComponent b, int offsetY) {
        this.parentModule = b;
        this.x = b.category.getX() + b.category.getWidth();
        this.y = b.category.getY() + b.yPos;
        this.offsetY = offsetY;
    }

    public void draw(AtomicInteger offset) {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        this.renderText(this.isBinding ? BindStage.binding : BindStage.bind + ": " + Keyboard.getKeyName(this.parentModule.mod.getKey()), offset.get());
        GL11.glPopMatrix();
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
        boolean h = this.isHovered(mousePosX, mousePosY);
        this.y = this.parentModule.category.getY() + this.offsetY;
        this.x = this.parentModule.category.getX();
    }

    public void mouseDown(int x, int y, int button) {
    }

    @Override
    public void onClick(int x, int y, int mouse) {
        if (this.isHovered(x, y) && mouse == 0 && this.parentModule.isOpened) {
            this.isBinding = !this.isBinding;
            isAnyBinding = this.isBinding;
        }
    }

    @Override
    public void mouseReleased(int x, int y, int button) {

    }

    @Override
    public void keyTyped(char chatTyped, int keyCode) {
        if (this.isBinding) {
            if (keyCode == 1 || keyCode == 14) {
                if (this.parentModule.mod instanceof ClickGui) {
                    this.parentModule.mod.setKey(54);
                } else {
                    this.parentModule.mod.setKey(0);
                }
            } else {
                this.parentModule.mod.setKey(keyCode);
            }

            this.isBinding = false;
            isAnyBinding = false;
        }
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.offsetY = newOffsetY;
    }

    public boolean isHovered(int x, int y) {
        return x > this.x && x < this.x + this.parentModule.category.getWidth() && y > this.y - 1 && y < this.y + 12;
    }

    public int getHeight() {
        return 12;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    private void renderText(String s, int offset) {
        float characterX = (this.parentModule.category.getX() + 4) * 2.0F;
        float y = (this.parentModule.category.getY() + this.offsetY + 3) * 2.0F;
        long time = System.currentTimeMillis() + offset * 120L;
        for (int i = 0; i < s.length(); i++) {
            char character = s.charAt(i);
            mc.fontRendererObj.drawString(String.valueOf(character), characterX, y,
                    HUD.getColor(time + i * 120L).getRGB());
            characterX += mc.fontRendererObj.getCharWidth(character);
        }
    }

    @Override
    public void render() {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String text = this.isBinding ? "Press a key..." : "Bind: " + Keyboard.getKeyName(this.parentModule.mod.getKey());
        mc.fontRendererObj.drawString(text,
                (this.parentModule.category.getX() + 4) * 2,
                (this.parentModule.category.getModuleY() + this.offsetY + 3) * 2,
                this.isBinding ? new Color(255, 100, 100).getRGB() : -1);
        GL11.glPopMatrix();
    }

    @Override
    public void drawScreen(int x, int y) {
        this.y = this.parentModule.category.getModuleY() + this.offsetY;
        this.x = this.parentModule.category.getX();
    }

    @Override
    public void updateHeight(int y) {
        this.offsetY = y;
    }

    @Override
    public void onScroll(int scroll) {
    }

    @Override
    public void onGuiClosed() {
    }
}
