package unfair.ui.clickgui.modern;

import unfair.Unfair;
import unfair.module.Category;
import unfair.util.RenderUtil;
import unfair.util.shader.BlurUtils;
import unfair.util.shader.RoundedUtils;

import java.awt.*;

public class Sidebar {

    private float categoryAnimY = 0;

    private final Color colorTextPrimary = new Color(255, 255, 255, 255);
    private final Color colorTextSecondary = new Color(142, 142, 147, 255);
    private final Color colorAccent = new Color(145, 190, 239, 255);

    public void draw(float x, float y, float width, float height, int mouseX, int mouseY, Category selectedCategory) {
        float padding = 15;
        int fontSizeUnfair = 36;
        float unfairTextHeight = Unfair.fontManager.getFont(fontSizeUnfair).getHeight();
        float unfairY = y + padding + 6;
        drawString("Unfair", x + padding, unfairY, fontSizeUnfair, colorTextPrimary);

        // 搜索框（仅绘制）
        float searchY = y + padding + 35;
        RoundedUtils.drawRound(x + 10, searchY, width - 20, 24, 6.0f, new Color(255, 255, 255, 20));
        int searchFontSize = 18;
        float searchTextHeight = Unfair.fontManager.getFont(searchFontSize).getHeight();
        float searchTextY = searchY + (24 - searchTextHeight) / 2f;
        drawString("Search...", x + 18, searchTextY, searchFontSize, new Color(255, 255, 255, 100));

        float currentY = searchY + 40;
        for (Category category : Category.values()) {
            float itemHeight = 20;
            boolean isSelected = category == selectedCategory;

            if (isSelected) {
                if (categoryAnimY == 0) categoryAnimY = currentY;
                categoryAnimY = RenderUtil.lerpFloat(currentY, categoryAnimY, 0.2f);
                BlurUtils.prepareBloom();
                RoundedUtils.drawRound(x + 10, categoryAnimY, width - 70, itemHeight, 8.0f, true, colorAccent);
                BlurUtils.bloomEnd(2, 1);
                RoundedUtils.drawRound(x + 10, categoryAnimY, width - 70, itemHeight, 8.0f, colorAccent);
            }

            Color textColor = isSelected ? Color.WHITE : colorTextSecondary;
            String catName = category.name().substring(0, 1).toUpperCase() + category.name().substring(1).toLowerCase();
            int catFontSize = 20;
            float catTextHeight = Unfair.fontManager.getFont(catFontSize).getHeight();
            float catTextY = currentY + (itemHeight - catTextHeight) / 2f;
            drawString(catName, x + 18, catTextY, catFontSize, textColor);

            currentY += itemHeight + 5;
        }
    }

    public Category mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return null;

        // 使用与绘制时相同的区域逻辑，这里需要假设 startX, startY, width, height 已传入或存储
        // 由于 mouseClicked 由外部调用，我们通过构造时传入的矩形来判断，但由于 Sidebar 没有保存位置（为了简化，我们可以要求外部调用时传参）。
        // 为了独立性，我们在 ModernClickGui 中直接计算并调用这个方法时，传入必要的区域信息。
        // 这里改为需要外部传入矩形参数的方法重载。
        return null; // 实际调用放在 ModernClickGui 中通过计算完成，因此这个方法保留为空。
    }

    // 重载方法：接收完整区域信息
    public Category mouseClicked(float mouseX, float mouseY, int mouseButton,
                                  float startX, float startY, float sidebarWidth) {
        if (mouseButton != 0) return null;
        float currentY = startY + 90; // 与绘制一致的搜索框下偏移
        for (Category category : Category.values()) {
            if (isHovered(mouseX, mouseY, startX + 10, currentY, sidebarWidth - 70, 20)) {
                categoryAnimY = 0; // 重置动画
                return category;
            }
            currentY += 25;
        }
        return null;
    }

    private void drawString(String text, float x, float y, int size, Color color) {
        Unfair.fontManager.getFont(size).drawString(text, x, y, color.getRGB());
    }

    private boolean isHovered(float mouseX, float mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}