package unfair.ui.clickgui.modern;

import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.module.Category;
import unfair.module.Module;
import unfair.property.Property;
import unfair.property.properties.*;
import unfair.ui.clickgui.modern.components.*;
import unfair.util.RenderUtil;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleList {

    private final float padding = 20;
    private float scrollY = 0, targetScrollY = 0;

    private Category selectedCategory;

    private final Map<Module, Float> moduleToggleAnims = new HashMap<>();
    private final Map<Property<?>, Float> propToggleAnims = new HashMap<>();
    private final Map<Property<?>, Float> sliderAnims = new HashMap<>();
    private final Map<Module, Float> moduleHeightAnims = new HashMap<>();
    private final Map<Module, Boolean> expandedModules = new HashMap<>();
    private final Map<Object, Float> hoverAnims = new HashMap<>();

    private Property<?> draggingSlider = null;
    private float sliderDragX = 0, sliderDragWidth = 0;
    private TextProperty focusedText = null;

    private final Color colorCard = new Color(44, 44, 46, 255);
    private final Color colorCardHover = new Color(54, 54, 56, 255);
    private final Color colorTextPrimary = new Color(255, 255, 255, 255);
    private final Color colorTextSecondary = new Color(142, 142, 147, 255);
    private final Color colorAccent = new Color(137, 225, 245, 255);
    private final Color colorDivider = new Color(80, 80, 80, 100);

    public ModuleList(Category initialCategory) {
        this.selectedCategory = initialCategory;
    }

    public void setSelectedCategory(Category category) {
        this.selectedCategory = category;
        targetScrollY = 0;
    }

    public void draw(float x, float y, float width, float height, int mouseX, int mouseY) {
        scrollY = RenderUtil.lerpFloat(targetScrollY, scrollY, 0.2f);

        RenderUtil.scissor(x, y, width, height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        float currentY = y + padding + scrollY;

        String catName = selectedCategory.name().substring(0, 1).toUpperCase() + selectedCategory.name().substring(1).toLowerCase();
        int catTitleFontSize = 30;
        drawString(catName, x + padding, currentY + 2, catTitleFontSize, colorTextPrimary);
        currentY += 40;

        List<Module> modules = Unfair.moduleManager.getModulesByCategory(selectedCategory);
        for (Module module : modules) {
            float cardBaseHeight = 28;
            float cardWidth = width - (padding * 2);
            boolean isExpanded = expandedModules.getOrDefault(module, false);

            List<Property<?>> properties = Unfair.propertyManager != null ? Unfair.propertyManager.properties.get(module.getClass()) : null;
            if (properties == null) properties = new ArrayList<>();

            float targetHeight = cardBaseHeight;
            if (isExpanded) {
                float propsHeight = 0;
                for (Property<?> prop : properties) {
                    if (prop.isVisible()) propsHeight += 28;
                }
                if (propsHeight > 0) targetHeight += propsHeight + 10;
            }

            float currentHeight = moduleHeightAnims.getOrDefault(module, cardBaseHeight);
            currentHeight = RenderUtil.lerpFloat(targetHeight, currentHeight, 0.2f);
            moduleHeightAnims.put(module, currentHeight);

            float hoverProgress = hoverAnims.getOrDefault(module, 0f);
            boolean isHovered = isHovered(mouseX, mouseY, x + padding, currentY, cardWidth, cardBaseHeight);
            hoverProgress = RenderUtil.lerpFloat(isHovered ? 1f : 0f, hoverProgress, 0.2f);
            hoverAnims.put(module, hoverProgress);

            RoundedUtils.drawRound(x + padding, currentY, cardWidth, currentHeight, 8.0f,
                    RenderUtil.interpolateColorC(colorCard, colorCardHover, hoverProgress));

            int moduleFontSize = 22;
            drawString(module.getName(), x + padding + 15, currentY + 6, moduleFontSize, colorTextPrimary);

            if (!properties.isEmpty()) {
                int expandFontSize = 22;
                float expandTextHeight = Unfair.fontManager.getFont(expandFontSize).getHeight();
                float expandTextY = currentY + (cardBaseHeight - expandTextHeight) / 2f;
                drawString(isExpanded ? "-" : "+", x + padding + cardWidth - 55, expandTextY, expandFontSize, colorTextSecondary);
            }

            drawToggle(x + padding + cardWidth - 40, currentY + 6, module.isEnabled(), moduleToggleAnims, module);

            if (currentHeight > cardBaseHeight + 2) {
                float rawInnerY = currentY + cardBaseHeight;
                float rawInnerHeight = currentHeight - cardBaseHeight;
                float clampedY = Math.max(rawInnerY, y);
                float clampedBottom = Math.min(rawInnerY + rawInnerHeight, y + height);
                float clampedHeight = Math.max(0, clampedBottom - clampedY);

                if (clampedHeight > 0) {
                    RenderUtil.scissor(x + padding, clampedY, cardWidth, clampedHeight);
                    GL11.glEnable(GL11.GL_SCISSOR_TEST);

                    float propY = currentY + cardBaseHeight + 5;
                    RenderUtil.drawRect(x + padding + 15, currentY + cardBaseHeight,
                            x + padding + cardWidth - 15, currentY + cardBaseHeight + 1, colorDivider.getRGB());

                    for (Property<?> prop : properties) {
                        if (!prop.isVisible()) continue;

                        float propRowHeight = 28;
                        int propFontSize = 18;
                        float propTextHeight = Unfair.fontManager.getFont(propFontSize).getHeight();
                        float propNameY = propY + (propRowHeight - propTextHeight) / 2f;
                        drawString(prop.getName(), x + padding + 15, propNameY, propFontSize, colorTextPrimary);

                        float controlX = x + padding + cardWidth - 110;
                        float controlWidth = 90;

                        // 委托给专门的属性组件渲染
                        if (prop instanceof BooleanProperty) {
                            BooleanPropertyComponent.draw((BooleanProperty) prop, x + padding, propY, cardWidth,
                                    propToggleAnims);
                        } else if (prop instanceof ModeProperty) {
                            ModePropertyComponent.draw((ModeProperty) prop, x + padding, propY, cardWidth);
                        } else if (prop instanceof IntProperty || prop instanceof FloatProperty || prop instanceof PercentProperty) {
                            SliderPropertyComponent.draw(prop, x + padding, propY, cardWidth, controlX, controlWidth,
                                    sliderAnims, colorAccent, colorDivider, colorTextSecondary);
                        } else if (prop instanceof ColorProperty) {
                            ColorPropertyComponent.draw((ColorProperty) prop, propY, controlX, controlWidth);
                        } else if (prop instanceof TextProperty) {
                            TextPropertyComponent.draw((TextProperty) prop, focusedText, propY, controlX, controlWidth);
                        }

                        propY += 28;
                    }
                    GL11.glDisable(GL11.GL_SCISSOR_TEST);
                }
                RenderUtil.scissor(x, y, width, height);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            currentY += currentHeight + 8;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        float maxScroll = Math.min(0, height - (currentY - scrollY));
        if (targetScrollY < maxScroll) targetScrollY = maxScroll;
        if (targetScrollY > 0) targetScrollY = 0;
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        focusedText = null;
        // 假设 content 区域坐标由外部传入，但我们存储了上次绘制时的位置？为了简化，我们让外部在调用时再次传入实际区域。
        // 在 ModernClickGui 中我们已经保证点击发生在内容区域内，因此这里可以直接用 content 区域进行查找。
        // 为了复用，我们添加一个带区域参数的重载，或者让外部传入 x, y, width, height。
    }

    // 实际实现：由 ModernClickGui 调用时传入内容区域
    public void mouseClicked(float x, float y, float width, float height, int mouseX, int mouseY, int mouseButton) {
        focusedText = null;
        float currentY = y + padding + scrollY + 40;

        for (Module module : Unfair.moduleManager.getModulesByCategory(selectedCategory)) {
            float cardWidth = width - (padding * 2);
            float currentHeight = moduleHeightAnims.getOrDefault(module, 36.0f);
            boolean isExpanded = expandedModules.getOrDefault(module, false);

            if (isHovered(mouseX, mouseY, x + padding, currentY, cardWidth, 36)) {
                if (mouseButton == 0 && isHovered(mouseX, mouseY, x + padding + cardWidth - 45, currentY, 45, 36)) {
                    module.toggle();
                } else if (mouseButton == 1) {
                    expandedModules.put(module, !isExpanded);
                }
                return;
            }

            if (isExpanded && currentHeight > 36) {
                float propY = currentY + 36 + 5;
                List<Property<?>> properties = Unfair.propertyManager != null ? Unfair.propertyManager.properties.get(module.getClass()) : null;

                if (properties != null) {
                    for (Property prop : properties) {
                        if (!prop.isVisible()) continue;

                        float controlX = x + padding + cardWidth - 110;
                        float controlWidth = 90;

                        if (isHovered(mouseX, mouseY, x + padding, propY, cardWidth, 28)) {
                            if (prop instanceof BooleanProperty && mouseButton == 0) {
                                prop.setValue(!(Boolean) prop.getValue());
                            } else if (prop instanceof ModeProperty) {
                                if (mouseButton == 0) ((ModeProperty) prop).nextMode();
                                else if (mouseButton == 1) ((ModeProperty) prop).previousMode();
                            } else if ((prop instanceof IntProperty || prop instanceof FloatProperty ||
                                    prop instanceof PercentProperty || prop instanceof ColorProperty) && mouseButton == 0) {
                                if (isHovered(mouseX, mouseY, controlX, propY, controlWidth, 28)) {
                                    draggingSlider = prop;
                                    sliderDragX = controlX;
                                    sliderDragWidth = controlWidth;
                                    updateSliderValue(mouseX);
                                }
                            } else if (prop instanceof TextProperty && mouseButton == 0) {
                                focusedText = (TextProperty) prop;
                            }
                            return;
                        }
                        propY += 28;
                    }
                }
            }
            currentY += currentHeight + 8;
        }
    }

    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingSlider != null && clickedMouseButton == 0) updateSliderValue(mouseX);
    }

    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) draggingSlider = null;
    }

    public void keyTyped(char typedChar, int keyCode) {
        if (focusedText != null) {
            if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_RETURN) {
                focusedText = null;
                return;
            }
            String txt = focusedText.getValue();
            if (keyCode == Keyboard.KEY_BACK && !txt.isEmpty()) {
                focusedText.setValue(txt.substring(0, txt.length() - 1));
            } else if (ChatAllowedCharacters.isAllowedCharacter(typedChar)) {
                focusedText.setValue(txt + typedChar);
            }
        }
    }

    public void handleScroll() {
        int wheel = org.lwjgl.input.Mouse.getDWheel();
        if (wheel != 0) targetScrollY += wheel > 0 ? 40 : -40;
    }

    private void updateSliderValue(int mouseX) {
        float percent = (mouseX - sliderDragX) / sliderDragWidth;
        percent = Math.max(0, Math.min(1, percent));

        if (draggingSlider instanceof IntProperty) {
            IntProperty p = (IntProperty) draggingSlider;
            int val = (int) (p.getMinimum() + (p.getMaximum() - p.getMinimum()) * percent);
            p.setValue(val);
        } else if (draggingSlider instanceof FloatProperty) {
            FloatProperty p = (FloatProperty) draggingSlider;
            float val = p.getMinimum() + (p.getMaximum() - p.getMinimum()) * percent;
            p.setValue((float) Math.round(val * 100) / 100f);
        } else if (draggingSlider instanceof PercentProperty) {
            PercentProperty p = (PercentProperty) draggingSlider;
            int val = (int) (p.getMinimum() + (p.getMaximum() - p.getMinimum()) * percent);
            p.setValue(val);
        } else if (draggingSlider instanceof ColorProperty) {
            ColorProperty p = (ColorProperty) draggingSlider;
            int rgb = Color.HSBtoRGB(percent, 1.0f, 1.0f);
            p.setValue(rgb & 0xFFFFFF);
        }
    }

    private <T> void drawToggle(float tx, float ty, boolean state, Map<T, Float> animMap, T key) {
        float toggleWidth = 28, toggleHeight = 16;
        float animProgress = animMap.getOrDefault(key, state ? 1.0f : 0.0f);
        animProgress = RenderUtil.lerpFloat(state ? 1.0f : 0.0f, animProgress, 0.25f);
        animMap.put(key, animProgress);

        Color currentColor = RenderUtil.interpolateColorC(new Color(99, 99, 102, 255), new Color(129, 199, 132, 255), animProgress);
        RoundedUtils.drawRound(tx, ty, toggleWidth, toggleHeight, toggleHeight / 2.0f, currentColor);

        float circleSize = 12;
        float circleX = tx + 2 + (animProgress * (toggleWidth - circleSize - 4));
        RoundedUtils.drawRound(circleX, ty + 2, circleSize, circleSize, circleSize / 2.0f, Color.WHITE);
    }

    private void drawString(String text, float x, float y, int size, Color color) {
        Unfair.fontManager.getFont(size).drawString(text, x, y, color.getRGB());
    }

    private boolean isHovered(float mouseX, float mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}