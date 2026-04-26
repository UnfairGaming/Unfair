package unfair.ui.clickgui.modern;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import unfair.Unfair;
import unfair.module.Category;
import unfair.module.Module;
import unfair.property.Property;
import unfair.property.properties.*;
import unfair.util.RenderUtil;
import unfair.util.shader.BlurUtils;
import unfair.util.shader.RoundedUtils;

import java.awt.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModernClickGui extends GuiScreen {

    private final float windowWidth = 600;
    private final float windowHeight = 400;
    private final float sidebarWidth = 140;

    private Category selectedCategory = Category.values()[0];
    private float scrollY = 0, targetScrollY = 0;
    private float categoryAnimY = 0;

    private final Map<Module, Float> moduleToggleAnims = new HashMap<>();
    private final Map<Property<?>, Float> propToggleAnims = new HashMap<>();
    private final Map<Property<?>, Float> sliderAnims = new HashMap<>();
    private final Map<Module, Float> moduleHeightAnims = new HashMap<>();
    private final Map<Module, Boolean> expandedModules = new HashMap<>();
    private final Map<Object, Float> hoverAnims = new HashMap<>();

    private Property<?> draggingSlider = null;
    private float sliderDragX = 0, sliderDragWidth = 0;
    private TextProperty focusedText = null;

    private final Color bgSidebar = new Color(30, 30, 32, 240);
    private final Color bgContent = new Color(20, 20, 22, 240);
    private final Color colorCard = new Color(44, 44, 46, 255);
    private final Color colorCardHover = new Color(54, 54, 56, 255);
    private final Color colorTextPrimary = new Color(255, 255, 255, 255);
    private final Color colorTextSecondary = new Color(142, 142, 147, 255);
    private final Color colorAccent = new Color(10, 132, 255, 255);
    private final Color colorToggleOn = new Color(52, 199, 89, 255);
    private final Color colorToggleOff = new Color(99, 99, 102, 255);
    private final Color colorDivider = new Color(80, 80, 80, 100);

    @Override
    public void initGui() {
        super.initGui();
        draggingSlider = null;
        focusedText = null;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        ScaledResolution sr = new ScaledResolution(this.mc);
        float screenWidth = sr.getScaledWidth();
        float screenHeight = sr.getScaledHeight();

        GL11.glPushMatrix();
        GL11.glTranslatef(screenWidth / 2, screenHeight / 2, 0);
        // 直接全尺寸，无动画
        GL11.glScalef(1.0f, 1.0f, 1.0f);
        GL11.glTranslatef(-screenWidth / 2, -screenHeight / 2, 0);

        float startX = (screenWidth - windowWidth) / 2.0f;
        float startY = (screenHeight - windowHeight) / 2.0f;

        BlurUtils.prepareBloom();
        RoundedUtils.drawRound(startX, startY, windowWidth, windowHeight, 12.0f, true, new Color(0, 0, 0, 255));
        BlurUtils.bloomEnd(2, 2);
        BlurUtils.prepareBlur();
        RoundedUtils.drawRound(startX, startY, windowWidth, windowHeight, 12.0f, true, new Color(RenderUtil.mergeAlpha(Color.black.getRGB(), 255)));
        BlurUtils.blurEnd(2, 12);

        RoundedUtils.drawRound(startX, startY, windowWidth, windowHeight, 12.0f, bgContent);
        RoundedUtils.drawRound(startX, startY, sidebarWidth, windowHeight, 12.0f, bgSidebar);
        RenderUtil.drawRect(startX + sidebarWidth - 12, startY, startX + sidebarWidth, startY + windowHeight, bgSidebar.getRGB());

        drawSidebar(startX, startY, mouseX, mouseY);
        drawContent(startX + sidebarWidth, startY, windowWidth - sidebarWidth, windowHeight, mouseX, mouseY);

        GL11.glPopMatrix();
        handleScroll();
    }

    private void drawSidebar(float x, float y, int mouseX, int mouseY) {
        float padding = 15;
        int fontSizeUnfair = 36;
        float unfairTextHeight = Unfair.fontManager.getFont(fontSizeUnfair).getHeight();
        float unfairY = y + padding + 6;
        drawString("Unfair", x + padding, unfairY, fontSizeUnfair, colorTextPrimary);

        float searchY = y + padding + 35;
        RoundedUtils.drawRound(x + 10, searchY, sidebarWidth - 20, 24, 6.0f, new Color(255, 255, 255, 20));
        int searchFontSize = 18;
        float searchTextHeight = Unfair.fontManager.getFont(searchFontSize).getHeight();
        float searchTextY = searchY + (24 - searchTextHeight) / 2f;
        drawString("Search...", x + 18, searchTextY, searchFontSize, new Color(255, 255, 255, 100));

        float currentY = searchY + 40;

        for (Category category : Category.values()) {
            float itemHeight = 20;
            boolean isSelected = category == selectedCategory;

            float targetY = currentY;
            if (isSelected) {
                if (categoryAnimY == 0) categoryAnimY = targetY;
                categoryAnimY = RenderUtil.lerpFloat(targetY, categoryAnimY, 0.2f);
                BlurUtils.prepareBloom();
                RoundedUtils.drawRound(x + 10, categoryAnimY, sidebarWidth - 70, itemHeight, 8.0f, true, colorAccent);
                BlurUtils.bloomEnd(2, 1);
                RoundedUtils.drawRound(x + 10, categoryAnimY, sidebarWidth - 70, itemHeight, 8.0f, colorAccent);
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

    private void drawContent(float x, float y, float width, float height, int mouseX, int mouseY) {
        scrollY = RenderUtil.lerpFloat(targetScrollY, scrollY, 0.2f);

        RenderUtil.scissor(x, y, width, height);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);

        float padding = 20;
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
            boolean isHovered = isHoveredAnim(mouseX, mouseY, x + padding, currentY, cardWidth, cardBaseHeight);
            hoverProgress = RenderUtil.lerpFloat(isHovered ? 1f : 0f, hoverProgress, 0.2f);
            hoverAnims.put(module, hoverProgress);

            RoundedUtils.drawRound(x + padding, currentY, cardWidth, currentHeight, 8.0f, RenderUtil.interpolateColorC(colorCard, colorCardHover, hoverProgress));

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
                    RenderUtil.drawRect(x + padding + 15, currentY + cardBaseHeight, x + padding + cardWidth - 15, currentY + cardBaseHeight + 1, colorDivider.getRGB());

                    for (Property<?> prop : properties) {
                        if (!prop.isVisible()) continue;

                        float propRowHeight = 28;
                        int propFontSize = 18;
                        float propTextHeight = Unfair.fontManager.getFont(propFontSize).getHeight();
                        float propNameY = propY + (propRowHeight - propTextHeight) / 2f;
                        drawString(prop.getName(), x + padding + 15, propNameY, propFontSize, colorTextPrimary);

                        float controlX = x + padding + cardWidth - 110;
                        float controlWidth = 90;

                        if (prop instanceof BooleanProperty) {
                            drawToggle(x + padding + cardWidth - 40, propY + 6, ((BooleanProperty) prop).getValue(), propToggleAnims, prop);

                        } else if (prop instanceof ModeProperty) {
                            String modeStr = ((ModeProperty) prop).getModeString();
                            int modeFontSize = 18;
                            float strW = Unfair.fontManager.getFont(modeFontSize).getStringWidth(modeStr);
                            float modeBgX = x + padding + cardWidth - strW - 30;
                            float modeBgY = propY + 4;
                            float modeBgHeight = 20;
                            RoundedUtils.drawRound(modeBgX, modeBgY, strW + 16, modeBgHeight, 6.0f, new Color(255, 255, 255, 20));
                            float modeTextHeight = Unfair.fontManager.getFont(modeFontSize).getHeight();
                            float modeTextY = modeBgY + (modeBgHeight - modeTextHeight) / 2f;
                            drawString(modeStr, x + padding + cardWidth - strW - 22, modeTextY, modeFontSize, Color.WHITE);

                        } else if (prop instanceof IntProperty || prop instanceof FloatProperty || prop instanceof PercentProperty) {
                            float min = 0, max = 0, val = 0;
                            if (prop instanceof IntProperty) {
                                min = ((IntProperty) prop).getMinimum(); max = ((IntProperty) prop).getMaximum(); val = ((IntProperty) prop).getValue();
                            } else if (prop instanceof FloatProperty) {
                                min = ((FloatProperty) prop).getMinimum(); max = ((FloatProperty) prop).getMaximum(); val = ((FloatProperty) prop).getValue();
                            } else {
                                min = ((PercentProperty) prop).getMinimum(); max = ((PercentProperty) prop).getMaximum(); val = ((PercentProperty) prop).getValue();
                            }

                            float percent = (val - min) / (max - min);
                            float animPercent = sliderAnims.getOrDefault(prop, percent);
                            animPercent = RenderUtil.lerpFloat(percent, animPercent, 0.3f);
                            sliderAnims.put(prop, animPercent);

                            RoundedUtils.drawRound(controlX, propY + 12, controlWidth, 4, 2.0f, colorDivider);
                            RoundedUtils.drawRound(controlX, propY + 12, controlWidth * animPercent, 4, 2.0f, colorAccent);
                            RoundedUtils.drawRound(controlX + (controlWidth * animPercent) - 4, propY + 8, 10, 10, 5.0f, Color.WHITE);

                            String formatVal = prop.formatValue().replace("&", "§");
                            int sliderValFontSize = 16;
                            float strW = Unfair.fontManager.getFont(sliderValFontSize).getStringWidth(formatVal);
                            float sliderValTextHeight = Unfair.fontManager.getFont(sliderValFontSize).getHeight();
                            float sliderValTextY = propY + (propRowHeight - sliderValTextHeight) / 2f;
                            drawString(formatVal, controlX - strW - 10, sliderValTextY, sliderValFontSize, colorTextSecondary);

                        } else if (prop instanceof ColorProperty) {
                            float[] hsb = Color.RGBtoHSB((((ColorProperty) prop).getValue() >> 16) & 0xFF, (((ColorProperty) prop).getValue() >> 8) & 0xFF, ((ColorProperty) prop).getValue() & 0xFF, null);
                            float hue = hsb[0];

                            for (int i = 0; i < controlWidth; i++) {
                                int c = Color.HSBtoRGB(i / controlWidth, 1.0f, 1.0f);
                                RenderUtil.drawRect(controlX + i, propY + 10, controlX + i + 1, propY + 18, new Color(c).getRGB());
                            }
                            RenderUtil.drawRect(controlX + (controlWidth * hue) - 1, propY + 8, controlX + (controlWidth * hue) + 1, propY + 20, Color.WHITE.getRGB());

                        } else if (prop instanceof TextProperty) {
                            boolean isFocused = focusedText == prop;
                            float textBgX = controlX;
                            float textBgY = propY + 4;
                            float textBgHeight = 20;
                            RoundedUtils.drawRound(textBgX, textBgY, controlWidth, textBgHeight, 4.0f, isFocused ? new Color(255, 255, 255, 40) : new Color(255, 255, 255, 15));

                            String txt = ((TextProperty) prop).getValue();
                            if (isFocused && (System.currentTimeMillis() / 500) % 2 == 0) txt += "_";

                            RenderUtil.scissor(controlX + 5, propY + 4, controlWidth - 10, 20);
                            GL11.glEnable(GL11.GL_SCISSOR_TEST);
                            int textFontSize = 18;
                            float textTextHeight = Unfair.fontManager.getFont(textFontSize).getHeight();
                            float textTextY = textBgY + (textBgHeight - textTextHeight) / 2f;
                            drawString(txt, controlX + 5, textTextY, textFontSize, Color.WHITE);
                            GL11.glDisable(GL11.GL_SCISSOR_TEST);

                            RenderUtil.scissor(x + padding, clampedY, cardWidth, clampedHeight);
                            GL11.glEnable(GL11.GL_SCISSOR_TEST);
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

    private <T> void drawToggle(float tx, float ty, boolean state, Map<T, Float> animMap, T key) {
        float toggleWidth = 28, toggleHeight = 16;
        float animProgress = animMap.getOrDefault(key, state ? 1.0f : 0.0f);
        animProgress = RenderUtil.lerpFloat(state ? 1.0f : 0.0f, animProgress, 0.25f);
        animMap.put(key, animProgress);

        Color currentColor = RenderUtil.interpolateColorC(colorToggleOff, colorToggleOn, animProgress);
        RoundedUtils.drawRound(tx, ty, toggleWidth, toggleHeight, toggleHeight / 2.0f, currentColor);

        float circleSize = 12;
        float circleX = tx + 2 + (animProgress * (toggleWidth - circleSize - 4));
        RoundedUtils.drawRound(circleX, ty + 2, circleSize, circleSize, circleSize / 2.0f, Color.WHITE);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton != 0 && mouseButton != 1) return;

        focusedText = null;

        ScaledResolution sr = new ScaledResolution(this.mc);
        float startX = (sr.getScaledWidth() - windowWidth) / 2.0f;
        float startY = (sr.getScaledHeight() - windowHeight) / 2.0f;

        if (mouseButton == 0 && isHoveredAnim(mouseX, mouseY, startX, startY, sidebarWidth, windowHeight)) {
            float currentY = startY + 90;
            for (Category category : Category.values()) {
                if (isHoveredAnim(mouseX, mouseY, startX + 10, currentY, sidebarWidth - 70, 20)) {
                    selectedCategory = category;
                    targetScrollY = 0;
                    return;
                }
                currentY += 25;
            }
        }

        float contentX = startX + sidebarWidth;
        if (isHoveredAnim(mouseX, mouseY, contentX, startY, windowWidth - sidebarWidth, windowHeight)) {
            float padding = 20;
            float currentY = startY + padding + scrollY + 40;

            for (Module module : Unfair.moduleManager.getModulesByCategory(selectedCategory)) {
                float cardWidth = (windowWidth - sidebarWidth) - (padding * 2);
                float currentHeight = moduleHeightAnims.getOrDefault(module, 36.0f);
                boolean isExpanded = expandedModules.getOrDefault(module, false);

                if (isHoveredAnim(mouseX, mouseY, contentX + padding, currentY, cardWidth, 36)) {
                    if (mouseButton == 0 && isHoveredAnim(mouseX, mouseY, contentX + padding + cardWidth - 45, currentY, 45, 36)) {
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

                            float controlX = contentX + padding + cardWidth - 110;
                            float controlWidth = 90;

                            if (isHoveredAnim(mouseX, mouseY, contentX + padding, propY, cardWidth, 28)) {
                                if (prop instanceof BooleanProperty && mouseButton == 0) {
                                    prop.setValue(!(Boolean) prop.getValue());
                                }
                                else if (prop instanceof ModeProperty) {
                                    if (mouseButton == 0) ((ModeProperty) prop).nextMode();
                                    else if (mouseButton == 1) ((ModeProperty) prop).previousMode();
                                }
                                else if ((prop instanceof IntProperty || prop instanceof FloatProperty || prop instanceof PercentProperty || prop instanceof ColorProperty) && mouseButton == 0) {
                                    if (isHoveredAnim(mouseX, mouseY, controlX, propY, controlWidth, 28)) {
                                        draggingSlider = prop;
                                        sliderDragX = controlX;
                                        sliderDragWidth = controlWidth;
                                        updateSliderValue(mouseX);
                                    }
                                }
                                else if (prop instanceof TextProperty && mouseButton == 0) {
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
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingSlider != null && clickedMouseButton == 0) updateSliderValue(mouseX);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (state == 0) draggingSlider = null;
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

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
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
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);   // 直接关闭，无动画
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void handleScroll() {
        int wheel = Mouse.getDWheel();
        if (wheel != 0) targetScrollY += wheel > 0 ? 40 : -40;
    }

    private void drawString(String text, float x, float y, int size, Color color) {
        Unfair.fontManager.getFont(size).drawString(text, x, y, color.getRGB());
    }

    private boolean isHoveredAnim(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}