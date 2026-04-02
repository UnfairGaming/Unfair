package unfair.ui.components;

import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.module.modules.render.GuiModule;
import unfair.module.modules.render.HUD;
import unfair.ui.Component;
import unfair.ui.dataset.BindStage;

import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        this.renderText(this.isBinding ? BindStage.binding : BindStage.bind + ": " + Keyboard.getKeyName(this.parentModule.mod.getKey()), ((HUD) Unfair.moduleManager.modules.get(HUD.class)).getColor(System.currentTimeMillis(), offset.get()).getRGB());
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
                if (this.parentModule.mod instanceof GuiModule) {
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

    private void renderText(String s, int color) {
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(s, (float) ((this.parentModule.category.getX() + 4) * 2), (float) ((this.parentModule.category.getY() + this.offsetY + 3) * 2), color);
    }

    @Override
    public void render() {
        GL11.glPushMatrix();
        GL11.glScaled(0.5D, 0.5D, 0.5D);
        String text = this.isBinding ? "Press a key..." : "Bind: " + Keyboard.getKeyName(this.parentModule.mod.getKey());
        Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text,
                (float) ((this.parentModule.category.getX() + 4) * 2),
                (float) ((this.parentModule.category.getModuleY() + this.offsetY + 3) * 2),
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
