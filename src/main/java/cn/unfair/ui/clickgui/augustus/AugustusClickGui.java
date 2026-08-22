package cn.unfair.ui.clickgui.augustus;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.RenderBloomEvent;
import cn.unfair.events.RenderBlurEvent;
import cn.unfair.module.Category;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.Property;
import cn.unfair.property.properties.*;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class AugustusClickGui extends GuiScreen {
    private static final double FLOAT_SLIDER_STEP = 0.01D;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float SIDEBAR_WIDTH = 90f;
    private static final float CONTENT_TEXT_X_OFFSET = 100f;
    private static final float CONTENT_ACTION_X_OFFSET = 170f;
    private static FontRenderer CACHED_TITLE_FONT;
    private static FontRenderer CACHED_NORMAL_FONT;
    private static int CACHED_SCALE = -1;
    private final Map<Property<?>, Float> sliderAnim = new HashMap<>();
    private final Map<TextProperty, GuiTextField> textFields = new HashMap<>();
    private final Map<ColorProperty, ColorPickerState> colorPickers = new HashMap<>();
    private final File configFile = new File("./config/Unfair/", "augustus-clickgui.json");
    private boolean dragging = false;
    private boolean resizing = false;
    private boolean waitingForKey = false;
    private Property<?> draggingSlider = null;
    private float dragOffsetX, dragOffsetY;
    private float posX, posY;
    private float guiWidth, guiHeight;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;
    private boolean positionInitialized = false;
    private float moduleScroll = 0F;
    private float valueScroll = 0F;
    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule = null;
    private FontRenderer titleFont;
    private FontRenderer normalFont;

    public AugustusClickGui() {
        this.guiWidth = 600;
        this.guiHeight = 325;
        this.loadLayout();
    }

    private static boolean isHovered(int mouseX, int mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private static void scissorStart(float x, float y, float w, float h) {
        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) (x * sf), (int) ((sr.getScaledHeight() - (y + h)) * sf), (int) (w * sf), (int) (h * sf));
    }

    private static void scissorEnd() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private static String[] getModes(ModeProperty mp) {
        return mp.getDisplayModes();
    }

    private static List<Module> getModulesFor(Category c) {
        return Unfair.moduleManager.getModulesByCategory(c);
    }

    private void ensureFonts() {
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        if (CACHED_TITLE_FONT == null || CACHED_NORMAL_FONT == null || CACHED_SCALE != scale) {
            CACHED_SCALE = scale;
            CACHED_TITLE_FONT = Fonts.esp.get(18);
            CACHED_NORMAL_FONT = Fonts.consola.get(18);
        }
        titleFont = CACHED_TITLE_FONT;
        normalFont = CACHED_NORMAL_FONT;
    }

    private float fw(String text) {
        return (float) normalFont.getStringWidth(text);
    }

    private float fh() {
        return (float) normalFont.getHeight();
    }

    private float sliderBoxHeight() {
        return Math.max(11.0F, fh() + 2.0F);
    }

    private float sliderBoxY(float textY) {
        return textY + (fh() - sliderBoxHeight()) / 2.0F;
    }

    private float sliderValueY(float sliderY) {
        return (float) Math.floor(sliderY + (sliderBoxHeight() - fh()) / 2.0F);
    }

    private float valueHeaderHeight(float initialValueY) {
        return (initialValueY + 8) - (posY + 40) + fh() + 3 + fh() + 10;
    }

    private float valueListStartY(float initialValueY) {
        return initialValueY - 4 + valueHeaderHeight(initialValueY);
    }

    private float valueClipTop(float initialValueY) {
        return posY + 30 + valueHeaderHeight(initialValueY) + 1.5F + 1.0F;
    }

    private float valueClipHeight(float initialValueY) {
        return guiHeight - (31.0F + valueHeaderHeight(initialValueY) + 1.5F);
    }

    private boolean isInValueClip(int mouseX, int mouseY, float initialValueY) {
        float clipTop = valueClipTop(initialValueY);
        return isHovered(mouseX, mouseY, posX + SIDEBAR_WIDTH + 2.0F, clipTop, guiWidth - SIDEBAR_WIDTH, valueClipHeight(initialValueY));
    }

    private float colorPropertyHeight(float y) {
        return y + fh() + 2.0F + 55.0F + 6.0F + 6.0F + fh() + 6.0F;
    }

    private float modePropertyHeight(ModeProperty mp, float y) {
        float x = posX + CONTENT_TEXT_X_OFFSET + fw(mp.getDisplayName() + ": ");
        float yy = y;
        String[] modes = getModes(mp);
        for (int i = 0; i < modes.length; i++) {
            x += fw(modes[i]);
            if (i < modes.length - 1) {
                x += fw(", ");
            }
            if (x > posX + guiWidth - 60) {
                x = posX + CONTENT_TEXT_X_OFFSET + fw(mp.getDisplayName() + ": ");
                yy += fh() + 2;
            }
        }
        return yy + fh() + 4;
    }

    private float getModuleContentHeight() {
        return getModulesFor(selectedCategory).size() * (fh() + 4.0F);
    }

    private float getModuleViewportHeight() {
        return Math.max(0.0F, guiHeight - 26.0F);
    }

    private float getValueContentHeight() {
        if (selectedModule == null) {
            return 0.0F;
        }

        ArrayList<Property<?>> props = Unfair.propertyManager.properties.get(selectedModule.getClass());
        if (props == null) {
            return 0.0F;
        }

        float y = 0.0F;
        for (Property<?> property : props) {
            if (property.isVisible()) {
                y = advancePropertyY(property, y);
            }
        }
        return y;
    }

    private float getValueViewportHeight(float initialValueY) {
        float contentStart = valueListStartY(initialValueY);
        float inset = contentStart - valueClipTop(initialValueY);
        return Math.max(0.0F, valueClipHeight(initialValueY) - inset);
    }

    private float advancePropertyY(Property<?> property, float y) {
        if (property instanceof TextProperty
                || property instanceof FloatProperty
                || property instanceof IntProperty
                || property instanceof PercentProperty) {
            return y + fh() + 6.0F;
        }

        if (property instanceof ColorProperty) {
            return colorPropertyHeight(y);
        }

        if (property instanceof ModeProperty) {
            return modePropertyHeight((ModeProperty) property, y);
        }

        return y + fh() + 4.0F;
    }

    private float clampScroll(float scroll, float contentHeight, float viewportHeight) {
        float minScroll = Math.min(0.0F, viewportHeight - contentHeight);
        return MathHelper.clamp_float(scroll, minScroll, 0.0F);
    }

    private void clampScrolls() {
        moduleScroll = clampScroll(moduleScroll, getModuleContentHeight(), getModuleViewportHeight());

        if (selectedModule == null) {
            valueScroll = 0.0F;
        } else {
            float initialValueY = posY + 40.0F;
            valueScroll = clampScroll(valueScroll, getValueContentHeight(), getValueViewportHeight(initialValueY));
        }
    }

    private void updateScreenBounds() {
        int screenWidth = super.width;
        int screenHeight = super.height;
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }

        boolean screenChanged = screenWidth != this.lastScreenWidth || screenHeight != this.lastScreenHeight;
        float centerXRatio = 0.5F;
        float centerYRatio = 0.5F;

        if (this.positionInitialized && screenChanged && this.lastScreenWidth > 0 && this.lastScreenHeight > 0) {
            centerXRatio = MathHelper.clamp_float((this.posX + this.guiWidth * 0.5F) / (float) this.lastScreenWidth, 0.0F, 1.0F);
            centerYRatio = MathHelper.clamp_float((this.posY + this.guiHeight * 0.5F) / (float) this.lastScreenHeight, 0.0F, 1.0F);
        }

        if (!this.positionInitialized) {
            this.posX = (screenWidth - this.guiWidth) * 0.5F;
            this.posY = (screenHeight - this.guiHeight) * 0.5F;
            this.positionInitialized = true;
        } else if (screenChanged) {
            this.posX = centerXRatio * screenWidth - this.guiWidth * 0.5F;
            this.posY = centerYRatio * screenHeight - this.guiHeight * 0.5F;
        }

        clampGuiToScreen(screenWidth, screenHeight);
        this.lastScreenWidth = screenWidth;
        this.lastScreenHeight = screenHeight;
        clampScrolls();
    }

    private void clampGuiToScreen(int screenWidth, int screenHeight) {
        this.posX = clampGuiPosition(this.posX, this.guiWidth, screenWidth);
        this.posY = clampGuiPosition(this.posY, this.guiHeight, screenHeight);
    }

    private float clampGuiPosition(float pos, float size, int screenSize) {
        float margin = 8.0F;
        if (size + margin * 2.0F >= screenSize) {
            return (screenSize - size) * 0.5F;
        }
        return MathHelper.clamp_float(pos, margin, screenSize - size - margin);
    }

    private void applyMouseWheel(int mouseX, int mouseY) {
        int wheel = Mouse.getDWheel();
        if (wheel == 0) {
            clampScrolls();
            return;
        }

        float rowHeight = fh() + 6.0F;
        float notches = Math.abs(wheel) >= 120 ? wheel / 120.0F : Math.signum(wheel);
        float scrollAmount = notches * rowHeight * 3.0F;
        if (selectedModule != null && isInValueClip(mouseX, mouseY, posY + 40.0F)) {
            valueScroll = clampScroll(valueScroll + scrollAmount, getValueContentHeight(), getValueViewportHeight(posY + 40.0F));
            return;
        }

        if (isHovered(mouseX, mouseY, posX, posY + 18.0F, SIDEBAR_WIDTH, guiHeight - 18.0F)) {
            moduleScroll = clampScroll(moduleScroll + scrollAmount, getModuleContentHeight(), getModuleViewportHeight());
            return;
        }

        clampScrolls();
    }

    private SliderBounds getSliderBounds(String propertyName, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        return new SliderBounds(nameX + fw(propertyName + ": ") + 2.0F, sliderBoxY(y), 120.0F, sliderBoxHeight());
    }

    private float getPreciseMouseX() {
        ScaledResolution sr = new ScaledResolution(mc);
        return (float) Mouse.getX() * (float) sr.getScaledWidth() / (float) mc.displayWidth;
    }

    private float getSliderPercent(float mouseX, SliderBounds bounds) {
        if (mouseX <= bounds.x) {
            return 0.0F;
        }
        if (mouseX >= bounds.x + bounds.width) {
            return 1.0F;
        }
        return MathHelper.clamp_float((mouseX - bounds.x) / bounds.width, 0.0F, 1.0F);
    }

    private void updateSliderValue(Property<?> property, float mouseX, SliderBounds bounds) {
        float pct = getSliderPercent(mouseX, bounds);
        if (property instanceof FloatProperty fp) {
            float min = fp.getMinimum() == null ? 0.0F : fp.getMinimum();
            float max = fp.getMaximum() == null ? 1.0F : fp.getMaximum();
            fp.setValue(getFloatSliderValue(min, max, pct));
        } else if (property instanceof IntProperty ip) {
            int min = ip.getMinimum();
            int max = ip.getMaximum();
            ip.setValue(pct <= 0.0F ? min : pct >= 1.0F ? max : Math.round(min + (max - min) * pct));
        } else if (property instanceof PercentProperty pp) {
            int min = pp.getMinimum();
            int max = pp.getMaximum();
            pp.setValue(pct <= 0.0F ? min : pct >= 1.0F ? max : Math.round(min + (max - min) * pct));
        }
    }

    private void drawSliderProgress(float sliderX, float sliderY, float sliderH, float progressWidth) {
        float left = sliderX + 2.0F;
        float right = sliderX + progressWidth - 1.0F;
        if (right > left) {
            RenderUtil.drawRect(left, sliderY + 2.0F, right, sliderY + sliderH - 2.0F, getAccent().getRGB());
        }
    }

    private void drawSliderValue(String value, float sliderX, float sliderY, float sliderW) {
        float centerX = sliderX + (sliderW + 1.0F) / 2.0F;
        normalFont.drawString(
                value,
                centerX - normalFont.getStringVisualCenterOffset(value),
                sliderValueY(sliderY),
                new Color(200, 200, 200).getRGB(),
                false
        );
    }

    private float getFloatSliderValue(float min, float max, float percent) {
        if (percent <= 0.0F) {
            return min;
        }
        if (percent >= 1.0F) {
            return max;
        }

        long totalSteps = Math.max(1L, Math.round(((double) max - min) / FLOAT_SLIDER_STEP));
        long selectedStep = Math.round((double) percent * totalSteps);
        double value = min + selectedStep * FLOAT_SLIDER_STEP;
        return MathHelper.clamp_float((float) value, min, max);
    }

    private void updateActiveSlider(int mouseX) {
        if (draggingSlider == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            draggingSlider = null;
            return;
        }
        updateSliderValue(draggingSlider, getPreciseMouseX(), getSliderBounds(draggingSlider.getName(), 0.0F));
    }

    @Override
    public void initGui() {
        super.initGui();
        ensureFonts();
        updateScreenBounds();

        textFields.clear();
        colorPickers.clear();
        sliderAnim.clear();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        updateActiveSlider(mouseX);

        if (dragging) {
            if (Mouse.isButtonDown(0)) {
                posX = mouseX - dragOffsetX;
                posY = mouseY - dragOffsetY;
                clampGuiToScreen(super.width, super.height);
            } else {
                dragging = false;
            }
        }

        if (resizing) {
            if (Mouse.isButtonDown(0)) {
                guiWidth = Math.max(420.0F, mouseX - posX);
                guiHeight = Math.max(220.0F, mouseY - posY);
                clampGuiToScreen(super.width, super.height);
                clampScrolls();
            } else {
                resizing = false;
            }
        }

        updateScreenBounds();
        applyMouseWheel(mouseX, mouseY);

        RenderUtil.drawRoundedRect(posX, posY + 17, guiWidth, guiHeight - 17, 0f, 0f, 6f, 6f, new Color(25, 25, 25, 180).getRGB());
        RenderUtil.drawRoundedRect(posX, posY, guiWidth, 17f, 6f, 6f, 0f, 0f, new Color(34, 34, 34).getRGB());
        titleFont.drawString("CLICKGUI", posX + 5, posY + 4, new Color(200, 200, 200).getRGB(), false);

        RenderUtil.drawRect(posX + 90, posY + 0.5f, posX + 92, posY + guiHeight, new Color(34, 34, 34).getRGB());
        RenderUtil.drawRect(posX + 90, posY + 40, posX + guiWidth, posY + 42, new Color(34, 34, 34).getRGB());

        renderCategories(mouseX, mouseY);
        renderModuleList(mouseX, mouseY);
        renderValues(mouseX, mouseY);
    }

    private void renderCategories(int mouseX, int mouseY) {
        float x = posX + SIDEBAR_WIDTH + 15;
        float y = posY + 25;
        for (Category c : Category.values()) {
            String display = c.getDisplayName();
            int col;
            if (c == selectedCategory) {
                col = getAccent().getRGB();
            } else if (isHovered(mouseX, mouseY, x, y, fw(display), fh())) {
                col = new Color(220, 220, 220).getRGB();
            } else {
                col = new Color(180, 180, 180).getRGB();
            }
            boolean hovered = isHovered(mouseX, mouseY, x, y, fw(display), fh());
            if (hovered && c != selectedCategory) {
                col = new Color(220, 220, 220).getRGB();
            }
            normalFont.drawString(display.toUpperCase(Locale.ROOT), x, y, col, false);
            x += fw(display.toUpperCase(Locale.ROOT)) + 16;
        }
    }

    private void renderModuleList(int mouseX, int mouseY) {
        float listX = posX;
        float listY = posY + 18;
        float listW = SIDEBAR_WIDTH;
        float listH = guiHeight - 18;

        scissorStart(listX, listY, listW, listH);

        float y = posY + 26 + moduleScroll;
        for (Module m : getModulesFor(selectedCategory)) {
            int col = m.isEnabled() ? getAccent().getRGB() : new Color(200, 200, 200).getRGB();
            float textX = posX + 8;
            if (m == selectedModule) {
                normalFont.drawString(">", textX, y, col, false);
                textX += fw(">") + 2;
            }
            normalFont.drawString(m.getName(), textX, y, col, false);
            y += fh() + 4;
        }

        scissorEnd();
    }

    private void renderValues(int mouseX, int mouseY) {
        if (selectedModule == null) {
            return;
        }

        float headerY = posY + 40;
        float contentW = guiWidth - SIDEBAR_WIDTH;
        float initialValueY = headerY;
        float currentY = initialValueY + 8;

        titleFont.drawString(selectedModule.getName() + ":", posX + CONTENT_TEXT_X_OFFSET, currentY, getAccent().getRGB(), false);
        String resetText = "Reset";
        float resetX = posX + guiWidth - fw(resetText) - 22.0F;
        int resetCol = isHovered(mouseX, mouseY, resetX - 2.0F, currentY, fw(resetText) + 4.0F, fh())
                ? getAccent().getRGB()
                : new Color(150, 150, 150).getRGB();
        normalFont.drawString(resetText, resetX, currentY, resetCol, false);

        currentY += fh() + 3;

        String keyName = selectedModule.getKey() == 0 ? "None" : Keyboard.getKeyName(selectedModule.getKey());

        int subCol = new Color(150, 150, 150).getRGB();
        if (waitingForKey) {
            normalFont.drawString("Key: ...", posX + CONTENT_TEXT_X_OFFSET, currentY + 1, getAccent().getRGB(), false);
        } else {
            normalFont.drawString("Key: " + keyName, posX + CONTENT_TEXT_X_OFFSET, currentY + 1, subCol, false);
        }

        normalFont.drawString("Hide: ", posX + CONTENT_ACTION_X_OFFSET, currentY + 1, subCol, false);
        int stateCol = selectedModule.isHidden() ? new Color(0, 180, 0).getRGB() : new Color(180, 0, 0).getRGB();
        normalFont.drawString(String.valueOf(selectedModule.isHidden()), posX + CONTENT_ACTION_X_OFFSET + fw("Hide: "), currentY + 1, stateCol, false);

        float headerHeight = valueHeaderHeight(initialValueY);
        float listTop = valueClipTop(initialValueY);

        scissorStart(posX + SIDEBAR_WIDTH + 1.5f + 0.5f, listTop, contentW, valueClipHeight(initialValueY));

        float y = valueListStartY(initialValueY) + valueScroll;
        ArrayList<Property<?>> props = Unfair.propertyManager.properties.get(selectedModule.getClass());
        if (props != null) {
            for (Property<?> p : props) {
                if (!p.isVisible()) continue;

                y = renderProperty(p, mouseX, mouseY, y);
            }
        }

        scissorEnd();

        updateColorDragging(mouseX, mouseY);
    }

    private float renderProperty(Property<?> p, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();

        if (p instanceof BooleanProperty bp) {
            String full = bp.getDisplayName() + ": " + (bp.getValue().toString());
            boolean hovered = isHovered(mouseX, mouseY, nameX, y, fw(full), fh());
            normalFont.drawString(bp.getDisplayName() + ": ", nameX, y, nameCol, false);
            int vCol = bp.getValue() ? new Color(0, 180, 0).getRGB() : new Color(180, 0, 0).getRGB();
            normalFont.drawString(bp.getValue().toString(), nameX + fw(bp.getDisplayName() + ": "), y, hovered ? getAccent().getRGB() : vCol, false);
            return y + fh() + 4;
        }

        if (p instanceof TextProperty tp) {
            normalFont.drawString(tp.getDisplayName() + ": ", nameX, y, nameCol, false);

            float boxX = nameX + fw(tp.getDisplayName() + ": ");
            float boxW = 140;
            float boxH = fh() + 4;
            float boxY = y - 2;

            RenderUtil.drawRect(boxX - 1, boxY, boxX + boxW + 1, boxY + boxH, new Color(34, 34, 34).getRGB());
            RenderUtil.drawRect(boxX, boxY + 1, boxX + boxW, boxY + boxH - 1, new Color(45, 45, 45, 200).getRGB());

            GuiTextField tf = textFields.computeIfAbsent(tp, k -> {
                GuiTextField f = new GuiTextField(0, mc.fontRendererObj, 0, 0, (int) boxW - 6, (int) boxH);
                f.setMaxStringLength(64);
                f.setFocused(false);
                f.setText(tp.getValue());
                return f;
            });

            tf.xPosition = (int) (boxX + 3);
            tf.yPosition = (int) (boxY + 2);
            tf.width = (int) boxW - 6;
            tf.height = (int) boxH;
            tf.setEnableBackgroundDrawing(false);
            tf.drawTextBox();

            tp.setValue(tf.getText());
            return y + fh() + 6;
        }

        if (p instanceof FloatProperty) {
            return renderFloatSlider((FloatProperty) p, mouseX, mouseY, y);
        }

        if (p instanceof IntProperty) {
            return renderIntSlider((IntProperty) p, mouseX, mouseY, y);
        }

        if (p instanceof PercentProperty) {
            return renderPercentSlider((PercentProperty) p, mouseX, mouseY, y);
        }

        if (p instanceof ModeProperty mp) {
            normalFont.drawString(mp.getDisplayName() + ": ", nameX, y, nameCol, false);
            float x = nameX + fw(mp.getDisplayName() + ": ");
            String[] modes = getModes(mp);
            for (int i = 0; i < modes.length; i++) {
                String mode = modes[i];
                int col = mp.getValue() == i ? getAccent().getRGB() : new Color(200, 200, 200).getRGB();
                boolean hovered = isHovered(mouseX, mouseY, x, y, fw(mode), fh());
                normalFont.drawString(mode, x, y, hovered ? getAccent().getRGB() : col, false);
                x += fw(mode);
                if (i < modes.length - 1) {
                    normalFont.drawString(", ", x, y, new Color(200, 200, 200).getRGB(), false);
                    x += fw(", ");
                }
                if (x > posX + guiWidth - 60) {
                    x = nameX + fw(mp.getDisplayName() + ": ");
                    y += fh() + 2;
                }
            }
            return y + fh() + 4;
        }

        if (p instanceof ColorProperty) {
            return renderColorPicker((ColorProperty) p, mouseX, mouseY, y);
        }

        normalFont.drawString(p.getDisplayName() + ":", nameX, y, nameCol, false);
        return y + fh() + 4;
    }

    private float renderFloatSlider(FloatProperty fp, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(fp.getDisplayName() + ": ", nameX, y, nameCol, false);

        SliderBounds bounds = getSliderBounds(fp.getDisplayName(), y);
        float sliderX = bounds.x;
        float sliderY = bounds.y;
        float sliderW = bounds.width;
        float sliderH = bounds.height;

        float min = fp.getMinimum() == null ? 0F : fp.getMinimum();
        float max = fp.getMaximum() == null ? 1F : fp.getMaximum();
        float val = fp.getValue();
        float t = (val - min) / (max - min);
        t = MathHelper.clamp_float(t, 0F, 1F);
        float targetLen = t * sliderW;
        float curLen = sliderAnim.getOrDefault(fp, targetLen);
        curLen += (targetLen - curLen) * 0.25F;
        sliderAnim.put(fp, curLen);

        RenderUtil.drawRect(sliderX, sliderY, sliderX + sliderW + 1, sliderY + sliderH, new Color(34, 34, 34).getRGB());
        drawSliderProgress(sliderX, sliderY, sliderH, curLen);

        String v = String.format(Locale.ROOT, "%.2f", val);
        drawSliderValue(v, sliderX, sliderY, sliderW);

        return y + fh() + 6;
    }

    private float renderIntSlider(IntProperty ip, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(ip.getDisplayName() + ": ", nameX, y, nameCol, false);

        SliderBounds bounds = getSliderBounds(ip.getDisplayName(), y);
        float sliderX = bounds.x;
        float sliderY = bounds.y;
        float sliderW = bounds.width;
        float sliderH = bounds.height;

        float min = ip.getMinimum();
        float max = ip.getMaximum();
        float val = ip.getValue();
        float t = (val - min) / (max - min);
        t = MathHelper.clamp_float(t, 0F, 1F);
        float targetLen = t * sliderW;
        float curLen = sliderAnim.getOrDefault(ip, targetLen);
        curLen += (targetLen - curLen) * 0.25F;
        sliderAnim.put(ip, curLen);

        RenderUtil.drawRect(sliderX, sliderY, sliderX + sliderW + 1, sliderY + sliderH, new Color(34, 34, 34).getRGB());
        drawSliderProgress(sliderX, sliderY, sliderH, curLen);

        String v = String.valueOf(ip.getValue());
        drawSliderValue(v, sliderX, sliderY, sliderW);

        return y + fh() + 6;
    }

    private float renderPercentSlider(PercentProperty pp, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(pp.getDisplayName() + ": ", nameX, y, nameCol, false);

        SliderBounds bounds = getSliderBounds(pp.getDisplayName(), y);
        float sliderX = bounds.x;
        float sliderY = bounds.y;
        float sliderW = bounds.width;
        float sliderH = bounds.height;

        float min = pp.getMinimum();
        float max = pp.getMaximum();
        float val = pp.getValue();
        float t = (val - min) / (max - min);
        t = MathHelper.clamp_float(t, 0F, 1F);
        float targetLen = t * sliderW;
        float curLen = sliderAnim.getOrDefault(pp, targetLen);
        curLen += (targetLen - curLen) * 0.25F;
        sliderAnim.put(pp, curLen);

        RenderUtil.drawRect(sliderX, sliderY, sliderX + sliderW + 1, sliderY + sliderH, new Color(34, 34, 34).getRGB());
        drawSliderProgress(sliderX, sliderY, sliderH, curLen);

        String v = pp.getValue() + "%";
        drawSliderValue(v, sliderX, sliderY, sliderW);

        return y + fh() + 6;
    }

    private float renderColorPicker(ColorProperty cp, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(cp.getDisplayName() + ": ", nameX, y, nameCol, false);

        ColorPickerState st = colorPickers.computeIfAbsent(cp, k -> {
            ColorPickerState s = new ColorPickerState();
            int rgb = cp.getValue();
            float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
            s.hue = hsb[0];
            s.sat = hsb[1];
            s.bri = hsb[2];
            return s;
        });

        float pickerX = nameX;
        float pickerY = y + fh() + 2;
        float pickerW = 120;
        float pickerH = 55;

        Color hueColor = Color.getHSBColor(st.hue, 1F, 1F);
        RenderUtil.drawRect(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, hueColor.getRGB());

        for (int ix = 0; ix < pickerW; ix++) {
            float sat = ix / pickerW;
            int alpha = (int) (255 * (1 - sat));
            RenderUtil.drawRect(pickerX + ix, pickerY, pickerX + ix + 1, pickerY + pickerH, new Color(255, 255, 255, alpha).getRGB());
        }

        for (int iy = 0; iy < pickerH; iy++) {
            float bri = 1 - (iy / pickerH);
            int alpha = (int) (255 * (1 - bri));
            RenderUtil.drawRect(pickerX, pickerY + iy, pickerX + pickerW, pickerY + iy + 1, new Color(0, 0, 0, alpha).getRGB());
        }

        float hueY = pickerY + pickerH + 6;
        float hueH = 6;
        for (int ix = 0; ix < pickerW; ix++) {
            float h = ix / pickerW;
            RenderUtil.drawRect(pickerX + ix, hueY, pickerX + ix + 1, hueY + hueH, Color.getHSBColor(h, 1F, 1F).getRGB());
        }

        float hx = pickerX + st.hue * pickerW;
        RenderUtil.drawRect(hx - 1, hueY - 1, hx + 1, hueY + hueH + 1, Color.WHITE.getRGB());

        float cx = pickerX + st.sat * pickerW;
        float cy = pickerY + (1 - st.bri) * pickerH;
        RenderUtil.drawRect(cx - 2, cy - 2, cx + 2, cy + 2, Color.WHITE.getRGB());

        float previewX = pickerX + pickerW + 10;
        float previewY = pickerY;
        RenderUtil.drawRect(previewX, previewY, previewX + 18, previewY + 18, (0xFF000000 | cp.getValue()));

        boolean inHue = isHovered(mouseX, mouseY, pickerX, hueY, pickerW, hueH);
        boolean inArea = isHovered(mouseX, mouseY, pickerX, pickerY, pickerW, pickerH);

        if (Mouse.isButtonDown(0)) {
            if (inHue) {
                st.draggingHue = true;
                st.draggingArea = false;
            } else if (inArea) {
                st.draggingArea = true;
                st.draggingHue = false;
            }
        }

        int rgb = Color.HSBtoRGB(st.hue, st.sat, st.bri) & 0xFFFFFF;
        cp.setValue(rgb);

        return hueY + hueH + fh() + 6;
    }

    private void updateColorDragging(int mouseX, int mouseY) {
        if (!Mouse.isButtonDown(0)) {
            for (ColorPickerState st : colorPickers.values()) {
                st.draggingHue = false;
                st.draggingArea = false;
            }
            return;
        }

        for (Map.Entry<ColorProperty, ColorPickerState> e : colorPickers.entrySet()) {
            ColorPickerState st = e.getValue();
            if (!st.draggingHue && !st.draggingArea) {
                continue;
            }

            float nameX = posX + CONTENT_TEXT_X_OFFSET;
            float pickerW = 120;
            float pickerH = 55;

            float initialValueY = posY + 40;
            float y = valueListStartY(initialValueY) + valueScroll;
            ArrayList<Property<?>> props = Unfair.propertyManager.properties.get(selectedModule.getClass());
            if (props == null) {
                continue;
            }
            for (Property<?> p : props) {
                if (!p.isVisible()) continue;

                if (p == e.getKey()) {
                    float pickerY = y + fh() + 2;
                    float hueY = pickerY + pickerH + 6;

                    if (st.draggingHue) {
                        float hue = (mouseX - nameX) / pickerW;
                        st.hue = MathHelper.clamp_float(hue, 0F, 1F);
                    } else if (st.draggingArea) {
                        float sat = (mouseX - nameX) / pickerW;
                        float bri = 1 - ((mouseY - pickerY) / pickerH);
                        st.sat = MathHelper.clamp_float(sat, 0F, 1F);
                        st.bri = MathHelper.clamp_float(bri, 0F, 1F);
                    }
                    break;
                }

                if (p instanceof TextProperty) {
                    y += fh() + 6;
                } else if (p instanceof FloatProperty || p instanceof IntProperty || p instanceof PercentProperty) {
                    y += fh() + 6;
                } else if (p instanceof ColorProperty) {
                    y = colorPropertyHeight(y);
                } else if (p instanceof ModeProperty) {
                    y = modePropertyHeight((ModeProperty) p, y);
                } else {
                    y += fh() + 4;
                }
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0 && isHovered(mouseX, mouseY, posX, posY, guiWidth, 18)) {
            dragging = true;
            dragOffsetX = mouseX - posX;
            dragOffsetY = mouseY - posY;
            return;
        }

        if (mouseButton == 0 && isHovered(mouseX, mouseY, posX + guiWidth - 12, posY + guiHeight - 12, 14, 14)) {
            resizing = true;
            return;
        }

        float catX = posX + SIDEBAR_WIDTH + 15;
        float catY = posY + 25;
        for (Category c : Category.values()) {
            String display = c.getDisplayName();
            float w = fw(display.toUpperCase(Locale.ROOT));
            if (isHovered(mouseX, mouseY, catX, catY, w, fh())) {
                selectedCategory = c;
                selectedModule = null;
                moduleScroll = 0;
                valueScroll = 0;
                colorPickers.clear();
                textFields.clear();
                waitingForKey = false;
                draggingSlider = null;
                return;
            }
            catX += w + 16;
        }

        if (isHovered(mouseX, mouseY, posX, posY + 18, SIDEBAR_WIDTH, guiHeight - 18)) {
            float y = posY + 26 + moduleScroll;
            for (Module m : getModulesFor(selectedCategory)) {
                float h = fh();
                float tx = posX + 8;
                if (isHovered(mouseX, mouseY, tx, y - 3, SIDEBAR_WIDTH, h + 4)) {
                    if (mouseButton == 0) {
                        m.toggle();
                        return;
                    }
                    if (mouseButton == 1) {
                        selectedModule = m;
                        valueScroll = 0;
                        colorPickers.clear();
                        textFields.clear();
                        waitingForKey = false;
                        draggingSlider = null;
                        return;
                    }
                }
                y += h + 4;
            }
        }

        if (selectedModule != null) {
            float initialValueY = posY + 40;
            float currentY = initialValueY + 8;
            String resetText = "Reset";
            float resetX = posX + guiWidth - fw(resetText) - 22.0F;
            if (isHovered(mouseX, mouseY, resetX - 2.0F, currentY, fw(resetText) + 4.0F, fh())) {
                resetSelectedModuleProperties();
                return;
            }
            currentY += fh() + 3;
            String keyName = selectedModule.getKey() == 0 ? "None" : Keyboard.getKeyName(selectedModule.getKey());
            if (isHovered(mouseX, mouseY, posX + CONTENT_TEXT_X_OFFSET, currentY + 1, fw("Key: " + keyName), fh())) {
                waitingForKey = !waitingForKey;
                return;
            }
            if (isHovered(mouseX, mouseY, posX + CONTENT_ACTION_X_OFFSET, currentY + 1, fw("Hide: " + selectedModule.isHidden()), fh())) {
                selectedModule.setHidden(!selectedModule.isHidden());
                return;
            }

            ArrayList<Property<?>> props = Unfair.propertyManager.properties.get(selectedModule.getClass());
            if (props != null && isInValueClip(mouseX, mouseY, initialValueY)) {
                float py = valueListStartY(initialValueY) + valueScroll;
                for (Property<?> p : props) {
                    if (!p.isVisible()) continue;

                    if (p instanceof BooleanProperty bp) {
                        String full = bp.getDisplayName() + ": " + (bp.getValue().toString());
                        if (isHovered(mouseX, mouseY, posX + CONTENT_TEXT_X_OFFSET, py, fw(full), fh())) {
                            bp.setValue(!bp.getValue());
                            return;
                        }
                        py += fh() + 4;
                        continue;
                    }

                    if (p instanceof ModeProperty mp) {
                        float x = posX + CONTENT_TEXT_X_OFFSET + fw(mp.getDisplayName() + ": ");
                        float yy = py;
                        String[] modes = getModes(mp);
                        for (int i = 0; i < modes.length; i++) {
                            String mode = modes[i];
                            if (isHovered(mouseX, mouseY, x, yy, fw(mode), fh())) {
                                mp.setValue(i);
                                return;
                            }
                            x += fw(mode);
                            if (i < modes.length - 1) {
                                x += fw(", ");
                            }
                            if (x > posX + guiWidth - 60) {
                                x = posX + CONTENT_TEXT_X_OFFSET + fw(mp.getDisplayName() + ": ");
                                yy += fh() + 2;
                            }
                        }
                        py = yy + fh() + 4;
                        continue;
                    }

                    if (p instanceof TextProperty tp) {
                        float boxX = (posX + CONTENT_TEXT_X_OFFSET) + fw(tp.getDisplayName() + ": ");
                        float boxW = 140;
                        float boxH = fh() + 4;
                        float boxY = py - 2;
                        GuiTextField tf = textFields.get(tp);
                        if (tf != null) {
                            tf.mouseClicked(mouseX, mouseY, mouseButton);
                        } else if (isHovered(mouseX, mouseY, boxX, boxY, boxW, boxH)) {
                            GuiTextField f = new GuiTextField(0, mc.fontRendererObj, (int) (boxX + 3), (int) (boxY + 2), (int) boxW - 6, (int) boxH);
                            f.setMaxStringLength(64);
                            f.setText(tp.getValue());
                            f.setFocused(true);
                            textFields.put(tp, f);
                        }
                        py += fh() + 6;
                        continue;
                    }

                    if (p instanceof ColorProperty) {
                        py = colorPropertyHeight(py);
                        continue;
                    }

                    if (p instanceof FloatProperty || p instanceof IntProperty || p instanceof PercentProperty) {
                        if (mouseButton == 0) {
                            SliderBounds bounds = getSliderBounds(p.getDisplayName(), py);
                            if (isHovered(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height)) {
                                draggingSlider = p;
                                updateSliderValue(p, mouseX, bounds);
                                return;
                            }
                        }
                        py += fh() + 6;
                        continue;
                    }

                    py += fh() + 4;
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1 && !waitingForKey) {
            mc.displayGuiScreen(null);
            return;
        }

        if (waitingForKey && selectedModule != null) {
            if (keyCode == 1) {
                selectedModule.setKey(0);
            } else {
                selectedModule.setKey(keyCode);
            }
            waitingForKey = false;
            return;
        }

        if (!textFields.isEmpty()) {
            for (GuiTextField tf : textFields.values()) {
                if (tf.isFocused()) {
                    tf.textboxKeyTyped(typedChar, keyCode);
                }
            }
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        boolean changedLayout = dragging || resizing;
        dragging = false;
        resizing = false;
        draggingSlider = null;
        if (changedLayout) {
            saveLayout();
        }
        for (ColorPickerState st : colorPickers.values()) {
            st.draggingHue = false;
            st.draggingArea = false;
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        saveLayout();
    }

    private void loadLayout() {
        if (!configFile.exists()) {
            return;
        }
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("x")) {
                this.posX = json.get("x").getAsFloat();
            }
            if (json.has("y")) {
                this.posY = json.get("y").getAsFloat();
            }
            if (json.has("width")) {
                this.guiWidth = Math.max(420.0F, json.get("width").getAsFloat());
            }
            if (json.has("height")) {
                this.guiHeight = Math.max(220.0F, json.get("height").getAsFloat());
            }
            if (json.has("category")) {
                try {
                    this.selectedCategory = Category.valueOf(json.get("category").getAsString());
                } catch (IllegalArgumentException ignored) {
                }
            }
            this.positionInitialized = json.has("x") && json.has("y");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveLayout() {
        try {
            File parent = configFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            JsonObject json = new JsonObject();
            json.addProperty("x", this.posX);
            json.addProperty("y", this.posY);
            json.addProperty("width", this.guiWidth);
            json.addProperty("height", this.guiHeight);
            json.addProperty("category", this.selectedCategory.name());
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void resetSelectedModuleProperties() {
        if (selectedModule == null) {
            return;
        }

        selectedModule.resetSettings();
        ArrayList<Property<?>> props = Unfair.propertyManager.properties.get(selectedModule.getClass());
        if (props != null) {
            for (Property<?> property : props) {
                property.resetValue();
            }
        }

        waitingForKey = false;
        draggingSlider = null;
        sliderAnim.clear();
        textFields.clear();
        colorPickers.clear();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @EventTarget
    public void onRenderBlur(RenderBlurEvent event) {
        if (mc.currentScreen != this) {
            return;
        }
        if (event.getType() == EventType.PRE) {
            event.setCancelled(true);
            return;
        }
        if (event.getType() == EventType.POST) {
            renderPostProcessMask(0xFFFFFFFF);
        }
    }

    @EventTarget
    public void onRenderBloom(RenderBloomEvent event) {
        if (mc.currentScreen != this) {
            return;
        }
        if (event.getType() == EventType.PRE) {
            event.setCancelled(true);
            return;
        }
        if (event.getType() == EventType.POST) {
            renderPostProcessMask(0xFFFFFFFF);
        }
    }

    private void renderPostProcessMask(int color) {
        RenderUtil.drawRoundedRect(posX, posY + 17.0F, guiWidth, guiHeight - 17.0F, 0.0F, 0.0F, 6.0F, 6.0F, color);
        RenderUtil.drawRoundedRect(posX, posY, guiWidth, 17.0F, 6.0F, 6.0F, 0.0F, 0.0F, color);
    }

    private Color getAccent() {
        try {
            return HUD.getColor(System.currentTimeMillis(), 0);
        } catch (Exception ignored) {
        }
        return new Color(140, 170, 255);
    }

    private static class ColorPickerState {
        float hue;
        float sat;
        float bri;
        boolean draggingHue;
        boolean draggingArea;
    }

    private record SliderBounds(float x, float y, float width, float height) {
    }
}
