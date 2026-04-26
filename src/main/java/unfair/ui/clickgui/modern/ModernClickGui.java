package unfair.ui.clickgui.modern;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import unfair.module.Category;
import unfair.util.RenderUtil;
import unfair.util.shader.BlurUtils;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.io.IOException;

public class ModernClickGui extends GuiScreen {

    private final float windowWidth = 600;
    private final float windowHeight = 400;
    private final float sidebarWidth = 140;

    private Category selectedCategory = Category.values()[0];

    private final Color bgContent = new Color(20, 20, 22, 240);
    private final Color bgSidebar = new Color(30, 30, 32, 240);

    private Sidebar sidebar;
    private ModuleList moduleList;

    @Override
    public void initGui() {
        super.initGui();
        sidebar = new Sidebar();
        moduleList = new ModuleList(selectedCategory);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScaledResolution sr = new ScaledResolution(this.mc);
        float screenWidth = sr.getScaledWidth();
        float screenHeight = sr.getScaledHeight();

        GL11.glPushMatrix();
        GL11.glTranslatef(screenWidth / 2, screenHeight / 2, 0);
        GL11.glScalef(1.0f, 1.0f, 1.0f);
        GL11.glTranslatef(-screenWidth / 2, -screenHeight / 2, 0);

        float startX = (screenWidth - windowWidth) / 2.0f;
        float startY = (screenHeight - windowHeight) / 2.0f;

        // 背景效果
        BlurUtils.prepareBloom();
        RoundedUtils.drawRound(startX, startY, windowWidth, windowHeight, 12.0f, true, new Color(0, 0, 0, 255));
        BlurUtils.bloomEnd(2, 2);
        BlurUtils.prepareBlur();
        RoundedUtils.drawRound(startX, startY, windowWidth, windowHeight, 12.0f, true, new Color(RenderUtil.mergeAlpha(Color.black.getRGB(), 255)));
        BlurUtils.blurEnd(2, 12);

        // 主背景
        RoundedUtils.drawRound(startX, startY, windowWidth, windowHeight, 12.0f, bgContent);
        RoundedUtils.drawRound(startX, startY, sidebarWidth, windowHeight, 12.0f, bgSidebar);
        RenderUtil.drawRect(startX + sidebarWidth - 12, startY, startX + sidebarWidth, startY + windowHeight, bgSidebar.getRGB());

        // 绘制子组件
        sidebar.draw(startX, startY, sidebarWidth, windowHeight, mouseX, mouseY, selectedCategory);
        moduleList.draw(startX + sidebarWidth, startY, windowWidth - sidebarWidth, windowHeight, mouseX, mouseY);

        GL11.glPopMatrix();
        moduleList.handleScroll();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0 && mouseButton != 1) return;

        ScaledResolution sr = new ScaledResolution(this.mc);
        float startX = (sr.getScaledWidth() - windowWidth) / 2.0f;
        float startY = (sr.getScaledHeight() - windowHeight) / 2.0f;

        // 侧边栏点击 - 使用带坐标参数的版本
        Category clickedCategory = sidebar.mouseClicked(mouseX, mouseY, mouseButton, startX, startY, sidebarWidth);
        if (clickedCategory != null) {
            selectedCategory = clickedCategory;
            moduleList.setSelectedCategory(clickedCategory);
            return;
        }

        // 内容区点击 - 使用带坐标参数的版本
        float contentX = startX + sidebarWidth;
        if (isHovered(mouseX, mouseY, contentX, startY, windowWidth - sidebarWidth, windowHeight)) {
            moduleList.mouseClicked(contentX, startY, windowWidth - sidebarWidth, windowHeight, mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        moduleList.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        moduleList.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        moduleList.keyTyped(typedChar, keyCode);
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}