package cn.unfair.ui.clickgui.augustus;

import cn.unfair.Unfair;
import cn.unfair.module.Category;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.Property;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ColorProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.property.properties.TextProperty;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AugustusClickGui extends GuiScreen {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static FontRenderer CACHED_TITLE_FONT;
    private static FontRenderer CACHED_NORMAL_FONT;
    private static int CACHED_SCALE = -1;

    private static final float SIDEBAR_WIDTH = 90f;
    private static final float CONTENT_TEXT_X_OFFSET = 100f;
    private static final float CONTENT_ACTION_X_OFFSET = 170f;

    private boolean dragging = false;
    private boolean resizing = false;
    private boolean waitingForKey = false;
    private Property<?> draggingSlider = null;

    private float dragOffsetX, dragOffsetY;

    private float posX, posY;
    private float guiWidth, guiHeight;

    private float moduleScroll = 0F;
    private float valueScroll = 0F;

    private Category selectedCategory = Category.COMBAT;
    private Module selectedModule = null;

    private final Map<Property<?>, Float> sliderAnim = new HashMap<>();
    private final Map<TextProperty, GuiTextField> textFields = new HashMap<>();
    private final Map<ColorProperty, ColorPickerState> colorPickers = new HashMap<>();

    private FontRenderer titleFont;
    private FontRenderer normalFont;

    private void ensureFonts() {
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        if (CACHED_TITLE_FONT == null || CACHED_NORMAL_FONT == null || CACHED_SCALE != scale) {
            CACHED_SCALE = scale;
            CACHED_TITLE_FONT = Fonts.esp.get(20);
            CACHED_NORMAL_FONT = Fonts.consola.get(16);
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
        return sliderY + (sliderBoxHeight() - fh()) / 2.0F;
    }

    private SliderBounds getSliderBounds(String propertyName, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        return new SliderBounds(nameX + fw(propertyName + ": ") + 2.0F, sliderBoxY(y), 120.0F, sliderBoxHeight());
    }

    private float getSliderPercent(int mouseX, SliderBounds bounds) {
        if (mouseX <= bounds.x) {
            return 0.0F;
        }
        if (mouseX >= bounds.x + bounds.width) {
            return 1.0F;
        }
        return MathHelper.clamp_float((mouseX - bounds.x) / bounds.width, 0.0F, 1.0F);
    }

    private void updateSliderValue(Property<?> property, int mouseX, SliderBounds bounds) {
        float pct = getSliderPercent(mouseX, bounds);
        if (property instanceof FloatProperty) {
            FloatProperty fp = (FloatProperty) property;
            float min = fp.getMinimum() == null ? 0.0F : fp.getMinimum();
            float max = fp.getMaximum() == null ? 1.0F : fp.getMaximum();
            fp.setValue(pct <= 0.0F ? min : pct >= 1.0F ? max : min + (max - min) * pct);
        } else if (property instanceof IntProperty) {
            IntProperty ip = (IntProperty) property;
            int min = ip.getMinimum();
            int max = ip.getMaximum();
            ip.setValue(pct <= 0.0F ? min : pct >= 1.0F ? max : Math.round(min + (max - min) * pct));
        } else if (property instanceof PercentProperty) {
            PercentProperty pp = (PercentProperty) property;
            int min = pp.getMinimum();
            int max = pp.getMaximum();
            pp.setValue(pct <= 0.0F ? min : pct >= 1.0F ? max : Math.round(min + (max - min) * pct));
        }
    }

    private void updateActiveSlider(int mouseX) {
        if (draggingSlider == null) {
            return;
        }
        if (!Mouse.isButtonDown(0)) {
            draggingSlider = null;
            return;
        }
        updateSliderValue(draggingSlider, mouseX, getSliderBounds(draggingSlider.getName(), 0.0F));
    }

    private static class ColorPickerState {
        float hue;
        float sat;
        float bri;
        boolean draggingHue;
        boolean draggingArea;
    }

    private static class SliderBounds {
        final float x;
        final float y;
        final float width;
        final float height;

        SliderBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public AugustusClickGui() {
        this.guiWidth = 520;
        this.guiHeight = 280;
    }

    @Override
    public void initGui() {
        super.initGui();
        ensureFonts();

        if (posX == 0 && posY == 0) {
            posX = (super.width / 2f) - (guiWidth / 2f);
            posY = (super.height / 2f) - (guiHeight / 2f);
        }

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
            } else {
                dragging = false;
            }
        }

        if (resizing) {
            if (Mouse.isButtonDown(0)) {
                float newW = Math.max(420, mouseX - posX);
                float newH = Math.max(220, mouseY - posY);
                guiWidth = newW;
                guiHeight = newH;
            } else {
                resizing = false;
            }
        }

        RenderUtil.drawRoundedRect(posX, posY + 17, guiWidth, guiHeight - 17, 0f, 0f, 6f, 6f, new Color(25, 25, 25, 180).getRGB());
        RenderUtil.drawRoundedRect(posX, posY, guiWidth, 17f, 6f, 6f, 0f, 0f, new Color(34, 34, 34).getRGB());
        titleFont.drawString("CLICKGUI", posX + 5, posY + 6, new Color(200, 200, 200).getRGB(), false);

        RenderUtil.drawRect(posX + 90, posY + 0.5f, posX + 92, posY + guiHeight, new Color(34, 34, 34).getRGB());
        RenderUtil.drawRect(posX + 90, posY + 40, posX + guiWidth + 0.5f, posY + 42, new Color(34, 34, 34).getRGB());

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

        if (isHovered(mouseX, mouseY, listX, listY, listW, listH)) {
            moduleScroll = Math.min(0, moduleScroll + Mouse.getDWheel() / 10F);
        }

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

        normalFont.drawString(selectedModule.getName() + ":", posX + CONTENT_TEXT_X_OFFSET, currentY, new Color(200, 200, 200).getRGB(), false);

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

        float headerHeight = (initialValueY + 8) - (posY + 40) + fh() + 3 + fh() + 10;
        float listTop = posY + 30 + headerHeight + 1.5f + 1;

        if (isHovered(mouseX, mouseY, posX + SIDEBAR_WIDTH + 1.5f, initialValueY + 1.5f + 1, contentW, guiHeight - (40 + 1.5f))) {
            valueScroll = Math.min(0, valueScroll + Mouse.getDWheel() / 10F);
        }

        scissorStart(posX + SIDEBAR_WIDTH + 1.5f + 0.5f, listTop, contentW, guiHeight - (31 + headerHeight + 1.5f));

        float y = (initialValueY - 4 + headerHeight) + valueScroll;
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

        if (p instanceof BooleanProperty) {
            BooleanProperty bp = (BooleanProperty) p;
            String full = bp.getName() + ": " + (bp.getValue() ? "true" : "false");
            boolean hovered = isHovered(mouseX, mouseY, nameX, y, fw(full), fh());
            normalFont.drawString(bp.getName() + ": ", nameX, y, nameCol, false);
            int vCol = bp.getValue() ? new Color(0, 180, 0).getRGB() : new Color(180, 0, 0).getRGB();
            normalFont.drawString(bp.getValue() ? "true" : "false", nameX + fw(bp.getName() + ": "), y, hovered ? getAccent().getRGB() : vCol, false);
            return y + fh() + 4;
        }

        if (p instanceof TextProperty) {
            TextProperty tp = (TextProperty) p;
            normalFont.drawString(tp.getName() + ": ", nameX, y, nameCol, false);

            float boxX = nameX + fw(tp.getName() + ": ");
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

        if (p instanceof ModeProperty) {
            ModeProperty mp = (ModeProperty) p;
            normalFont.drawString(mp.getName() + ": ", nameX, y, nameCol, false);
            float x = nameX + fw(mp.getName() + ": ");
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
                    x = nameX + fw(mp.getName() + ": ");
                    y += fh() + 2;
                }
            }
            return y + fh() + 4;
        }

        if (p instanceof ColorProperty) {
            return renderColorPicker((ColorProperty) p, mouseX, mouseY, y);
        }

        normalFont.drawString(p.getName() + ":", nameX, y, nameCol, false);
        return y + fh() + 4;
    }

    private float renderFloatSlider(FloatProperty fp, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(fp.getName() + ": ", nameX, y, nameCol, false);

        SliderBounds bounds = getSliderBounds(fp.getName(), y);
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
        RenderUtil.drawRect(sliderX + 1, sliderY + 1, sliderX + curLen, sliderY + sliderH - 1, getAccent().getRGB());

        String v = String.format(Locale.ROOT, "%.2f", val);
        normalFont.drawString(v, sliderX + sliderW / 2F - fw(v) / 2F, sliderValueY(sliderY), new Color(200, 200, 200).getRGB(), false);

        return y + fh() + 6;
    }

    private float renderIntSlider(IntProperty ip, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(ip.getName() + ": ", nameX, y, nameCol, false);

        SliderBounds bounds = getSliderBounds(ip.getName(), y);
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
        RenderUtil.drawRect(sliderX + 1, sliderY + 1, sliderX + curLen, sliderY + sliderH - 1, getAccent().getRGB());

        String v = String.valueOf(ip.getValue());
        normalFont.drawString(v, sliderX + sliderW / 2F - fw(v) / 2F, sliderValueY(sliderY), new Color(200, 200, 200).getRGB(), false);

        return y + fh() + 6;
    }

    private float renderPercentSlider(PercentProperty pp, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(pp.getName() + ": ", nameX, y, nameCol, false);

        SliderBounds bounds = getSliderBounds(pp.getName(), y);
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
        RenderUtil.drawRect(sliderX + 1, sliderY + 1, sliderX + curLen, sliderY + sliderH - 1, getAccent().getRGB());

        String v = pp.getValue() + "%";
        normalFont.drawString(v, sliderX + sliderW / 2F - fw(v) / 2F, sliderValueY(sliderY), new Color(200, 200, 200).getRGB(), false);

        return y + fh() + 6;
    }

    private float renderColorPicker(ColorProperty cp, int mouseX, int mouseY, float y) {
        float nameX = posX + CONTENT_TEXT_X_OFFSET;
        int nameCol = new Color(200, 200, 200).getRGB();
        normalFont.drawString(cp.getName() + ": ", nameX, y, nameCol, false);

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
            float headerHeight = (initialValueY + 8) - (posY + 40) + fh() + 3 + fh() + 10;
            float listTop = (initialValueY - 4 + headerHeight);

            float y = listTop + valueScroll;
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

                y += fh() + 4;
                if (p instanceof TextProperty) {
                    y += 2;
                } else if (p instanceof FloatProperty || p instanceof IntProperty || p instanceof PercentProperty) {
                    y += 2;
                } else if (p instanceof ColorProperty) {
                    y += 55 + 6 + 6 + fh();
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

        if (selectedModule != null) {
            float initialValueY = posY + 40;
            float currentY = initialValueY + 8;
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
            if (props != null) {
                float headerHeight = (initialValueY + 8) - (posY + 40) + fh() + 3 + fh() + 10;
                float py = (initialValueY - 4 + headerHeight) + valueScroll;
                for (Property<?> p : props) {
                    if (!p.isVisible()) continue;

                    if (p instanceof BooleanProperty) {
                        BooleanProperty bp = (BooleanProperty) p;
                        String full = bp.getName() + ": " + (bp.getValue() ? "true" : "false");
                        if (isHovered(mouseX, mouseY, posX + CONTENT_TEXT_X_OFFSET, py, fw(full), fh())) {
                            bp.setValue(!bp.getValue());
                            return;
                        }
                        py += fh() + 4;
                        continue;
                    }

                    if (p instanceof ModeProperty) {
                        ModeProperty mp = (ModeProperty) p;
                        float x = posX + CONTENT_TEXT_X_OFFSET + fw(mp.getName() + ": ");
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
                                x = posX + CONTENT_TEXT_X_OFFSET + fw(mp.getName() + ": ");
                                yy += fh() + 2;
                            }
                        }
                        py = yy + fh() + 4;
                        continue;
                    }

                    if (p instanceof TextProperty) {
                        TextProperty tp = (TextProperty) p;
                        float boxX = (posX + CONTENT_TEXT_X_OFFSET) + fw(tp.getName() + ": ");
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
                        py += fh() + 4;
                        py += 55 + 6 + 6 + fh();
                        continue;
                    }

                    if (p instanceof FloatProperty || p instanceof IntProperty || p instanceof PercentProperty) {
                        if (mouseButton == 0) {
                            SliderBounds bounds = getSliderBounds(p.getName(), py);
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
            this.mc.displayGuiScreen(null);
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
        dragging = false;
        resizing = false;
        draggingSlider = null;
        for (ColorPickerState st : colorPickers.values()) {
            st.draggingHue = false;
            st.draggingArea = false;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
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

    private Color getAccent() {
        try {
            return HUD.getColor(System.currentTimeMillis(), 0);
        } catch (Exception ignored) {
        }
        return new Color(140, 170, 255);
    }

    private static String[] getModes(ModeProperty mp) {
        try {
            java.lang.reflect.Field f = ModeProperty.class.getDeclaredField("modes");
            f.setAccessible(true);
            return (String[]) f.get(mp);
        } catch (Exception ignored) {
        }
        return new String[]{mp.getModeString()};
    }

    private static List<Module> getModulesFor(Category c) {
        return Unfair.moduleManager.getModulesByCategory(c);
    }
}
