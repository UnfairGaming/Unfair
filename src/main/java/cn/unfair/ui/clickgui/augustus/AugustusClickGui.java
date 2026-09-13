package cn.unfair.ui.clickgui.augustus;

import cn.unfair.Unfair;
import cn.unfair.config.Config;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.RenderBloomEvent;
import cn.unfair.events.RenderBlurEvent;
import cn.unfair.module.Category;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.ClickGui;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.Property;
import cn.unfair.property.properties.*;
import cn.unfair.ui.clickgui.augustus.component.Component;
import cn.unfair.ui.clickgui.augustus.component.settings.*;
import cn.unfair.ui.clickgui.augustus.component.settings.TextComponent;
import cn.unfair.ui.clickgui.augustus.panel.CategoryPanel;
import cn.unfair.util.font.FontRenderer;
import cn.unfair.util.font.Fonts;
import cn.unfair.util.render.RenderUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AugustusClickGui extends GuiScreen {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float SIDEBAR_WIDTH = 90.0F;
    private static final float CONTENT_TEXT_X_OFFSET = 100.0F;
    private static final float CONTENT_ACTION_X_OFFSET = 170.0F;
    private static final ResourceLocation SETTING_ICON = new ResourceLocation("minecraft", "unfair/image/setting.png");
    private static final ResourceLocation CONFIG_ICON = new ResourceLocation("minecraft", "unfair/image/config.png");
    private static final float ICON_SIZE = 18.0F;
    private static final float ICON_SEPARATOR = 8.0F;
    private static final float CONFIG_TITLE_BAR_HEIGHT = 15.0F;
    private static final float WINDOW_BUTTON_WIDTH = 20.0F;
    private static FontRenderer CACHED_TITLE_FONT;
    private static FontRenderer CACHED_NORMAL_FONT;
    private static int CACHED_SCALE = -1;

    private final File configFile = new File("./config/Unfair/", "augustus-clickgui.json");
    private final Map<Category, CategoryPanel> categoryPanels = new HashMap<>();
    private final List<Component> settingComponents = new ArrayList<>();

    private boolean dragging = false;
    private boolean resizing = false;
    private boolean waitingForKey = false;
    private boolean isGuiOpen = true;
    private float dragOffsetX, dragOffsetY;
    @Getter
    private float posX;
    @Getter
    private float posY;
    @Getter
    private float guiWidth;
    @Getter
    private float guiHeight;
    private int lastScreenWidth = -1;
    private int lastScreenHeight = -1;
    private boolean positionInitialized = false;
    private float moduleScroll = 0F;
    private float valueScroll = 0F;
    private Category selectedCategory = Category.COMBAT;
    @Getter
    private Module selectedModule = null;
    @Getter
    private FontRenderer titleFont;
    @Getter
    private FontRenderer normalFont;

    private boolean configOpen = false;
    private boolean configDragging = false;
    private boolean configResizing = false;
    private float configDragX, configDragY;
    private float configPosX = -1, configPosY = -1;
    private float configWidth = 300, configHeight = 250;
    private String selectedConfig = null;
    private boolean creatingNewConfig = false;
    private String newConfigName = "";

    private boolean guiMaximized = false;
    private float guiRestoreX, guiRestoreY, guiRestoreW, guiRestoreH;
    private boolean configMaximized = false;
    private float configRestoreX, configRestoreY, configRestoreW, configRestoreH;

    public AugustusClickGui() {
        this.guiWidth = 600;
        this.guiHeight = 325;
        this.loadLayout();
        for (Category category : Category.values()) {
            categoryPanels.put(category, new CategoryPanel(this, category));
        }
    }

    public Color getAccent() {
        try {
            return HUD.getColor(System.currentTimeMillis(), 0);
        } catch (Exception ignored) {
        }
        return new Color(140, 170, 255);
    }

    private int getBackgroundAlpha() {
        ClickGui clickGui = (ClickGui) Unfair.moduleManager.getModule(ClickGui.class);
        if (clickGui == null) {
            return 180;
        }
        return Math.round(clickGui.backgroundOpacity.getValue() / 100.0F * 255.0F);
    }

    public void selectModule(Module module) {
        selectedModule = module;
        valueScroll = 0;
        waitingForKey = false;
        rebuildSettings();
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

    private void ensureFonts() {
        ScaledResolution sr = new ScaledResolution(mc);
        int scale = sr.getScaleFactor();
        if (CACHED_TITLE_FONT == null || CACHED_NORMAL_FONT == null || CACHED_SCALE != scale) {
            CACHED_SCALE = scale;
            CACHED_TITLE_FONT = Fonts.esp.get(16);
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

    private float valueHeaderHeight(float initialValueY) {
        return (initialValueY + 8.0F) - (posY + 40.0F) + fh() + 2.0F + fh() + 10.0F;
    }

    private float valueListStartY(float initialValueY) {
        return initialValueY - 4.0F + valueHeaderHeight(initialValueY);
    }

    private float valueClipTop(float initialValueY) {
        return posY + 30.0F + valueHeaderHeight(initialValueY) + 1.5F + 1.0F;
    }

    private float valueClipHeight(float initialValueY) {
        return guiHeight - (31.0F + valueHeaderHeight(initialValueY) + 1.5F);
    }

    private boolean isInValueClip(int mouseX, int mouseY, float initialValueY) {
        float clipTop = valueClipTop(initialValueY);
        return isHovered(mouseX, mouseY, posX + SIDEBAR_WIDTH + 2.0F, clipTop, guiWidth - SIDEBAR_WIDTH, valueClipHeight(initialValueY));
    }

    private float getModuleContentHeight() {
        CategoryPanel panel = categoryPanels.get(selectedCategory);
        return panel == null ? 0.0F : panel.getContentHeight();
    }

    private float getModuleViewportHeight() {
        return Math.max(0.0F, guiHeight - 26.0F);
    }

    private float getValueContentHeight() {
        float h = 0.0F;
        for (Component c : settingComponents) {
            if (c.isVisible()) {
                h += c.getHeight();
            }
        }
        return h;
    }

    private float getValueViewportHeight(float initialValueY) {
        float contentStart = valueListStartY(initialValueY);
        float inset = contentStart - valueClipTop(initialValueY);
        return Math.max(0.0F, valueClipHeight(initialValueY) - inset);
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

        if (isHovered(mouseX, mouseY, posX, posY + 16.0F, SIDEBAR_WIDTH, guiHeight - 16.0F)) {
            moduleScroll = clampScroll(moduleScroll + scrollAmount, getModuleContentHeight(), getModuleViewportHeight());
            return;
        }

        clampScrolls();
    }

    @Override
    public void initGui() {
        super.initGui();
        ensureFonts();
        updateScreenBounds();
        rebuildSettings();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawTopIconBar(mouseX, mouseY);

        if (isGuiOpen) {
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

            RenderUtil.drawRoundedRect(posX, posY + 15, guiWidth, guiHeight - 15, 0f, 0f, 6f, 6f, new Color(40, 39, 39, getBackgroundAlpha()).getRGB());
            RenderUtil.drawRoundedRect(posX, posY, guiWidth, 15f, 6f, 6f, 0f, 0f, new Color(34, 34, 34).getRGB());
            titleFont.drawString("CLICKGUI", posX + 5, posY + (15.0F - titleFont.getHeight()) / 2.0F, new Color(200, 200, 200).getRGB(), false);
            drawWindowControls(mouseX, mouseY, posX, posY, guiWidth, 15.0F);

            RenderUtil.drawRect(posX + 90, posY + 0.5f, posX + 91, posY + guiHeight, new Color(34, 34, 34).getRGB());
            RenderUtil.drawRect(posX + 90, posY + 40, posX + guiWidth, posY + 41, new Color(34, 34, 34).getRGB());

            renderCategories(mouseX, mouseY);
            renderModuleList(mouseX, mouseY);
            renderValues(mouseX, mouseY);
        }

        if (configOpen) {
            drawConfigInterface(mouseX, mouseY);
        }
    }

    private void drawTopIconBar(int mouseX, int mouseY) {
        float totalWidth = ICON_SIZE * 2 + ICON_SEPARATOR;
        float iconHeight = ICON_SIZE + 6;
        float iconX = (super.width - totalWidth) / 2;
        float bgX = iconX - 4;
        float bgY = 0;
        float bgWidth = totalWidth + 8;
        float iconY = bgY + 3;

        RenderUtil.drawRoundedRect(bgX, bgY, bgWidth, iconHeight, 0, 0, 3, 3, new Color(34, 34, 34, 220).getRGB());

        boolean settingHovered = isHovered(mouseX, mouseY, iconX, iconY, ICON_SIZE, ICON_SIZE);
        boolean configHovered = isHovered(mouseX, mouseY, iconX + ICON_SIZE + ICON_SEPARATOR, iconY, ICON_SIZE, ICON_SIZE);

        int settingColor = new Color(200, 200, 200).getRGB();
        if (isGuiOpen) {
            settingColor = getAccent().getRGB();
        } else if (settingHovered) {
            settingColor = new Color(220, 220, 220).getRGB();
        }

        int configColor = new Color(200, 200, 200).getRGB();
        if (configOpen) {
            configColor = getAccent().getRGB();
        } else if (configHovered) {
            configColor = new Color(220, 220, 220).getRGB();
        }

        RenderUtil.drawImage(SETTING_ICON, iconX, iconY, ICON_SIZE, ICON_SIZE, settingColor);
        RenderUtil.drawImage(CONFIG_ICON, iconX + ICON_SIZE + ICON_SEPARATOR, iconY, ICON_SIZE, ICON_SIZE, configColor);
    }

    private boolean topIconBarClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return false;
        }
        float totalWidth = ICON_SIZE * 2 + ICON_SEPARATOR;
        float iconX = (super.width - totalWidth) / 2;
        float bgY = 0;
        float iconY = bgY + 3;

        if (isHovered(mouseX, mouseY, iconX, iconY, ICON_SIZE, ICON_SIZE)) {
            isGuiOpen = !isGuiOpen;
            if (!isGuiOpen) {
                this.dragging = false;
            }
            return true;
        }
        if (isHovered(mouseX, mouseY, iconX + ICON_SIZE + ICON_SEPARATOR, iconY, ICON_SIZE, ICON_SIZE)) {
            configOpen = !configOpen;
            return true;
        }
        return false;
    }

    private enum WindowAction {
        NONE,
        CLOSE,
        MAXIMIZE,
        MINIMIZE
    }

    private void drawWindowControls(int mouseX, int mouseY, float winX, float winY, float winW, float winH) {
        float btnW = WINDOW_BUTTON_WIDTH;
        float right = winX + winW;
        drawWindowControlButton(mouseX, mouseY, right - btnW, winY, btnW, winH, WindowAction.CLOSE);
        drawWindowControlButton(mouseX, mouseY, right - btnW * 2, winY, btnW, winH, WindowAction.MAXIMIZE);
        drawWindowControlButton(mouseX, mouseY, right - btnW * 3, winY, btnW, winH, WindowAction.MINIMIZE);
    }

    private void drawWindowControlButton(int mouseX, int mouseY, float x, float y, float w, float h, WindowAction action) {
        boolean hovered = isHovered(mouseX, mouseY, x, y, w, h);
        if (hovered && action == WindowAction.CLOSE) {
            RenderUtil.drawRoundedRect(x, y, w, h, 0, 6, 0, 0, new Color(220, 45, 45, 180).getRGB());
        } else if (hovered) {
            RenderUtil.drawRect(x, y, x + w, y + h, new Color(255, 255, 255, 30).getRGB());
        }
        int color = hovered ? new Color(255, 255, 255).getRGB() : new Color(150, 150, 150).getRGB();
        float cx = x + w / 2.0F;
        float cy = y + h / 2.0F;
        float lw = Math.max(1.0F, new ScaledResolution(mc).getScaleFactor() * 0.5F);
        switch (action) {
            case CLOSE:
                RenderUtil.drawLine(cx - 3.0F, cy - 3.0F, cx + 3.0F, cy + 3.0F, lw, color);
                RenderUtil.drawLine(cx + 3.0F, cy - 3.0F, cx - 3.0F, cy + 3.0F, lw, color);
                break;
            case MAXIMIZE:
                RenderUtil.drawLine(cx - 3.0F, cy - 3.0F, cx + 3.0F, cy - 3.0F, lw, color);
                RenderUtil.drawLine(cx - 3.0F, cy + 3.0F, cx + 3.0F, cy + 3.0F, lw, color);
                RenderUtil.drawLine(cx - 3.0F, cy - 3.0F, cx - 3.0F, cy + 3.0F, lw, color);
                RenderUtil.drawLine(cx + 3.0F, cy - 3.0F, cx + 3.0F, cy + 3.0F, lw, color);
                break;
            case MINIMIZE:
                RenderUtil.drawLine(cx - 4.0F, cy, cx + 4.0F, cy, lw, color);
                break;
            default:
                break;
        }
    }

    private WindowAction hitWindowControl(int mouseX, int mouseY, float winX, float winY, float winW, float winH) {
        float btnW = WINDOW_BUTTON_WIDTH;
        float right = winX + winW;
        if (isHovered(mouseX, mouseY, right - btnW, winY, btnW, winH)) {
            return WindowAction.CLOSE;
        }
        if (isHovered(mouseX, mouseY, right - btnW * 2, winY, btnW, winH)) {
            return WindowAction.MAXIMIZE;
        }
        if (isHovered(mouseX, mouseY, right - btnW * 3, winY, btnW, winH)) {
            return WindowAction.MINIMIZE;
        }
        return WindowAction.NONE;
    }

    private void handleGuiWindowAction(WindowAction action) {
        switch (action) {
            case CLOSE:
            case MINIMIZE:
                isGuiOpen = false;
                this.dragging = false;
                break;
            case MAXIMIZE:
                toggleGuiMaximize();
                break;
            default:
                break;
        }
    }

    private void handleConfigWindowAction(WindowAction action) {
        switch (action) {
            case CLOSE:
            case MINIMIZE:
                configOpen = false;
                configDragging = false;
                configResizing = false;
                break;
            case MAXIMIZE:
                toggleConfigMaximize();
                break;
            default:
                break;
        }
    }

    private void toggleGuiMaximize() {
        if (!this.guiMaximized) {
            this.guiRestoreX = this.posX;
            this.guiRestoreY = this.posY;
            this.guiRestoreW = this.guiWidth;
            this.guiRestoreH = this.guiHeight;
            this.posX = 0;
            this.posY = 0;
            this.guiWidth = super.width;
            this.guiHeight = super.height;
            this.guiMaximized = true;
        } else {
            this.posX = this.guiRestoreX;
            this.posY = this.guiRestoreY;
            this.guiWidth = Math.max(420.0F, this.guiRestoreW);
            this.guiHeight = Math.max(220.0F, this.guiRestoreH);
            this.guiMaximized = false;
        }
        clampScrolls();
    }

    private void toggleConfigMaximize() {
        if (!this.configMaximized) {
            this.configRestoreX = this.configPosX;
            this.configRestoreY = this.configPosY;
            this.configRestoreW = this.configWidth;
            this.configRestoreH = this.configHeight;
            this.configPosX = 0;
            this.configPosY = 0;
            this.configWidth = super.width;
            this.configHeight = super.height;
            this.configMaximized = true;
        } else {
            this.configPosX = this.configRestoreX;
            this.configPosY = this.configRestoreY;
            this.configWidth = Math.max(300.0F, this.configRestoreW);
            this.configHeight = Math.max(250.0F, this.configRestoreH);
            this.configMaximized = false;
        }
    }

    private void renderCategories(int mouseX, int mouseY) {
        float x = posX + SIDEBAR_WIDTH + 15.0F;
        float y = posY + (55.0F - fh()) / 2.0F;
        for (Category c : Category.values()) {
            String display = c.getDisplayName();
            normalFont.drawString(display.toUpperCase(Locale.ROOT), x, y, new Color(255, 255, 255).getRGB(), false);

            if (c == selectedCategory) {
                float lineW = fw(display.toUpperCase(Locale.ROOT));
                float lineY = y + fh() + 1.0F;
                RenderUtil.drawRect(x, lineY, x + lineW, lineY + 1.0F, new Color(200, 200, 200).getRGB());
            }

            x += fw(display.toUpperCase(Locale.ROOT)) + 16.0F;
        }
    }

    private void renderModuleList(int mouseX, int mouseY) {
        float listX = posX;
        float listY = posY + 16.0F;
        float listW = SIDEBAR_WIDTH;
        float listH = guiHeight - 16.0F;

        scissorStart(listX, listY, listW, listH);

        CategoryPanel panel = categoryPanels.get(selectedCategory);
        if (panel != null) {
            panel.drawScreen(mouseX, mouseY, moduleScroll);
        }

        scissorEnd();
    }

    private void renderValues(int mouseX, int mouseY) {
        if (selectedModule == null) {
            return;
        }

        float initialValueY = posY + 40.0F;
        float currentY = initialValueY + 8.0F;

        titleFont.drawString(selectedModule.getName() + ":", posX + CONTENT_TEXT_X_OFFSET, currentY, getAccent().getRGB(), false);
        String resetText = "Reset";
        float resetX = posX + guiWidth - fw(resetText) - 22.0F;
        int resetCol = isHovered(mouseX, mouseY, resetX - 2.0F, currentY, fw(resetText) + 4.0F, fh())
                ? getAccent().getRGB()
                : new Color(150, 150, 150).getRGB();
        normalFont.drawString(resetText, resetX, currentY, resetCol, false);

        currentY += fh() + 2.0F;

        String keyName = selectedModule.getKey() == 0 ? "None" : Keyboard.getKeyName(selectedModule.getKey());
        int subCol = new Color(150, 150, 150).getRGB();
        if (waitingForKey) {
            normalFont.drawString("Key: ...", posX + CONTENT_TEXT_X_OFFSET, currentY + 1.0F, getAccent().getRGB(), false);
        } else {
            normalFont.drawString("Key: " + keyName, posX + CONTENT_TEXT_X_OFFSET, currentY + 1.0F, subCol, false);
        }

        normalFont.drawString("Hide: ", posX + CONTENT_ACTION_X_OFFSET, currentY + 1.0F, subCol, false);
        int stateCol = selectedModule.isHidden() ? new Color(0, 180, 0).getRGB() : new Color(180, 0, 0).getRGB();
        normalFont.drawString(String.valueOf(selectedModule.isHidden()), posX + CONTENT_ACTION_X_OFFSET + fw("Hide: "), currentY + 1.0F, stateCol, false);

        float listTop = valueClipTop(initialValueY);

        scissorStart(posX + SIDEBAR_WIDTH + 1.5f + 0.5f, listTop, guiWidth - SIDEBAR_WIDTH, valueClipHeight(initialValueY));

        float y = valueListStartY(initialValueY) + valueScroll;
        for (Component c : settingComponents) {
            if (!c.isVisible()) {
                continue;
            }
            c.setX(posX + CONTENT_TEXT_X_OFFSET);
            c.setY(y);
            c.drawScreen(mouseX, mouseY);
            y += c.getHeight();
        }

        scissorEnd();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (topIconBarClicked(mouseX, mouseY, mouseButton)) {
            return;
        }

        if (configOpen) {
            if (isHovered(mouseX, mouseY, configPosX, configPosY, configWidth, configHeight)) {
                configMouseClicked(mouseX, mouseY, mouseButton);
                return;
            }
        }

        if (!isGuiOpen) {
            return;
        }

        if (mouseButton == 0) {
            WindowAction action = hitWindowControl(mouseX, mouseY, posX, posY, guiWidth, 15.0F);
            if (action != WindowAction.NONE) {
                handleGuiWindowAction(action);
                return;
            }
        }

        if (mouseButton == 0 && isHovered(mouseX, mouseY, posX, posY, guiWidth, 16)) {
            dragging = true;
            dragOffsetX = mouseX - posX;
            dragOffsetY = mouseY - posY;
            return;
        }

        if (mouseButton == 0 && isHovered(mouseX, mouseY, posX + guiWidth - 12, posY + guiHeight - 12, 14, 14)) {
            resizing = true;
            return;
        }

        float catX = posX + SIDEBAR_WIDTH + 15.0F;
        float catY = posY + (55.0F - fh()) / 2.0F;
        for (Category c : Category.values()) {
            String display = c.getDisplayName();
            float w = fw(display.toUpperCase(Locale.ROOT));
            if (isHovered(mouseX, mouseY, catX, catY, w, fh())) {
                selectedCategory = c;
                selectedModule = null;
                moduleScroll = 0;
                valueScroll = 0;
                waitingForKey = false;
                rebuildSettings();
                return;
            }
            catX += w + 16.0F;
        }

        if (isHovered(mouseX, mouseY, posX, posY + 16.0F, SIDEBAR_WIDTH, guiHeight - 16.0F)) {
            CategoryPanel panel = categoryPanels.get(selectedCategory);
            if (panel != null && panel.mouseClicked(mouseX, mouseY, mouseButton, moduleScroll)) {
                return;
            }
        }

        if (selectedModule != null) {
            float initialValueY = posY + 40.0F;
            float currentY = initialValueY + 8.0F;
            String resetText = "Reset";
            float resetX = posX + guiWidth - fw(resetText) - 22.0F;
            if (isHovered(mouseX, mouseY, resetX - 2.0F, currentY, fw(resetText) + 4.0F, fh())) {
                resetSelectedModuleProperties();
                return;
            }
            currentY += fh() + 2.0F;
            String keyName = selectedModule.getKey() == 0 ? "None" : Keyboard.getKeyName(selectedModule.getKey());
            if (isHovered(mouseX, mouseY, posX + CONTENT_TEXT_X_OFFSET, currentY + 1.0F, fw("Key: " + keyName), fh())) {
                waitingForKey = !waitingForKey;
                return;
            }
            if (isHovered(mouseX, mouseY, posX + CONTENT_ACTION_X_OFFSET, currentY + 1.0F, fw("Hide: " + selectedModule.isHidden()), fh())) {
                selectedModule.setHidden(!selectedModule.isHidden());
                return;
            }

            if (isInValueClip(mouseX, mouseY, initialValueY)) {
                float py = valueListStartY(initialValueY) + valueScroll;
                for (Component c : settingComponents) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    c.setX(posX + CONTENT_TEXT_X_OFFSET);
                    c.setY(py);
                    c.mouseClicked(mouseX, mouseY, mouseButton);
                    py += c.getHeight();
                }
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (creatingNewConfig) {
            if (keyCode == 1) {
                creatingNewConfig = false;
                newConfigName = "";
            } else if (keyCode == 28) {
                if (!newConfigName.trim().isEmpty()) {
                    Config config = new Config(newConfigName.trim(), true);
                    config.save();
                    selectedConfig = newConfigName.trim();
                }
                creatingNewConfig = false;
                newConfigName = "";
            } else if (keyCode == 14) {
                if (!newConfigName.isEmpty()) {
                    newConfigName = newConfigName.substring(0, newConfigName.length() - 1);
                }
            } else if (Character.isLetterOrDigit(typedChar) || typedChar == '_' || typedChar == '-') {
                if (newConfigName.length() < 20) {
                    newConfigName += typedChar;
                }
            }
            return;
        }

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

        for (Component c : settingComponents) {
            c.keyTyped(typedChar, keyCode);
        }

        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        boolean changedLayout = dragging || resizing;
        dragging = false;
        resizing = false;
        configDragging = false;
        configResizing = false;
        if (changedLayout) {
            saveLayout();
        }
        for (Component c : settingComponents) {
            c.mouseReleased(mouseX, mouseY, state);
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        saveLayout();
    }

    private void rebuildSettings() {
        settingComponents.clear();
        if (selectedModule == null) {
            return;
        }
        ArrayList<Property<?>> props = Unfair.propertyManager.properties.get(selectedModule.getClass());
        if (props == null) {
            return;
        }
        for (Property<?> property : props) {
            Component component = createSettingComponent(property);
            if (component != null) {
                settingComponents.add(component);
            }
        }
    }

    private Component createSettingComponent(Property<?> property) {
        if (property instanceof BooleanProperty bp) {
            return new BooleanComponent(this, bp);
        }
        if (property instanceof TextProperty tp) {
            return new TextComponent(this, tp);
        }
        if (property instanceof FloatProperty fp) {
            return new SliderComponent(this, fp);
        }
        if (property instanceof IntProperty ip) {
            return new SliderComponent(this, ip);
        }
        if (property instanceof PercentProperty pp) {
            return new SliderComponent(this, pp);
        }
        if (property instanceof ModeProperty mp) {
            return new ModeComponent(this, mp);
        }
        if (property instanceof ColorProperty cp) {
            return new ColorPickerComponent(this, cp);
        }
        return null;
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
        rebuildSettings();
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
            float saveX = this.guiMaximized ? this.guiRestoreX : this.posX;
            float saveY = this.guiMaximized ? this.guiRestoreY : this.posY;
            float saveW = this.guiMaximized ? this.guiRestoreW : this.guiWidth;
            float saveH = this.guiMaximized ? this.guiRestoreH : this.guiHeight;
            json.addProperty("x", saveX);
            json.addProperty("y", saveY);
            json.addProperty("width", saveW);
            json.addProperty("height", saveH);
            json.addProperty("category", this.selectedCategory.name());
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private List<String> getAvailableConfigs() {
        List<String> configs = new ArrayList<>();
        File configDir = new File("./config/Unfair/");
        if (configDir.exists() && configDir.isDirectory()) {
            File[] files = configDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    String configName = file.getName().replace(".json", "");
                    if (configName.equals("augustus-clickgui") || configName.equals("widgets")) {
                        continue;
                    }
                    configs.add(configName);
                }
            }
        }
        if (configs.isEmpty()) {
            configs.add("default");
        }
        return configs;
    }

    private void drawConfigInterface(int mouseX, int mouseY) {
        if (configPosX == -1 || configPosY == -1) {
            configPosX = super.width / 2f - configWidth / 2;
            configPosY = super.height / 2f - configHeight / 2;
        }

        if (configDragging) {
            configPosX = mouseX - configDragX;
            configPosY = mouseY - configDragY;
        } else if (configResizing) {
            configWidth = Math.max(300, mouseX - configPosX);
            configHeight = Math.max(250, mouseY - configPosY);
        }

        float titleBarHeight = CONFIG_TITLE_BAR_HEIGHT;
        RenderUtil.drawRoundedRect(configPosX, configPosY + titleBarHeight, configWidth, configHeight - titleBarHeight, 0f, 0f, 6f, 6f, new Color(40, 39, 39, getBackgroundAlpha()).getRGB());
        RenderUtil.drawRoundedRect(configPosX, configPosY, configWidth, titleBarHeight, 6f, 6f, 0f, 0f, new Color(34, 34, 34).getRGB());

        titleFont.drawString("CONFIG", configPosX + 5, configPosY + (titleBarHeight - titleFont.getHeight()) / 2.0F, new Color(200, 200, 200).getRGB(), false);
        drawWindowControls(mouseX, mouseY, configPosX, configPosY, configWidth, titleBarHeight);

        float leftPanelWidth = configWidth * 0.55f;
        float rightPanelWidth = configWidth * 0.45f;
        float contentY = configPosY + titleBarHeight + 8;
        float contentHeight = configHeight - titleBarHeight - 16;

        RenderUtil.drawRect(configPosX + 8, contentY, configPosX + leftPanelWidth, contentY + contentHeight, new Color(30, 30, 30, 150).getRGB());

        normalFont.drawString("Available Configs:", configPosX + 12, contentY + 4, new Color(180, 180, 180).getRGB(), false);

        List<String> configs = getAvailableConfigs();
        float itemY = contentY + 20;
        float itemHeight = 18;

        for (String config : configs) {
            boolean hovered = isHovered(mouseX, mouseY, configPosX + 12, itemY, leftPanelWidth - 16, itemHeight);
            boolean selected = config.equals(selectedConfig);

            if (selected) {
                Color selectedColor = new Color(getAccent().getRed(), getAccent().getGreen(), getAccent().getBlue(), 50);
                RenderUtil.drawRect(configPosX + 12, itemY, configPosX + 12 + leftPanelWidth - 16, itemY + itemHeight, selectedColor.getRGB());
            } else if (hovered) {
                RenderUtil.drawRect(configPosX + 12, itemY, configPosX + 12 + leftPanelWidth - 16, itemY + itemHeight, new Color(45, 45, 45, 100).getRGB());
            }

            int textColor = selected ? Color.WHITE.getRGB() : (hovered ? getAccent().getRGB() : new Color(180, 180, 180).getRGB());
            normalFont.drawString(config, configPosX + 16, itemY + 5, textColor, false);
            itemY += itemHeight + 2;
        }

        float buttonX = configPosX + leftPanelWidth + 8;
        float buttonY = contentY + 8;
        float buttonWidth = rightPanelWidth - 16;
        float buttonHeight = 22;

        String[] buttons = {"Load", "Save", "Create", "Delete", "Folder"};
        for (String button : buttons) {
            boolean buttonHovered = isHovered(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight);

            Color buttonColor = buttonHovered ? new Color(60, 60, 60, 180) : new Color(40, 40, 40, 150);
            RenderUtil.drawRoundedRect(buttonX, buttonY, buttonWidth, buttonHeight, 2, 2, 2, 2, buttonColor.getRGB());

            int textColor = buttonHovered ? getAccent().getRGB() : new Color(200, 200, 200).getRGB();
            float textX = buttonX + buttonWidth / 2 - fw(button) / 2;
            normalFont.drawString(button, textX, buttonY + 6, textColor, false);

            buttonY += buttonHeight + 6;
        }

        if (creatingNewConfig) {
            float inputY = buttonY + 8;
            RenderUtil.drawRoundedRect(buttonX, inputY, buttonWidth, buttonHeight, 2, 2, 2, 2, new Color(30, 30, 30, 200).getRGB());
            RenderUtil.drawRoundedRect(buttonX + 1, inputY + 1, buttonWidth - 2, buttonHeight - 2, 1, 1, 1, 1, new Color(20, 20, 20, 150).getRGB());

            String displayText = newConfigName.isEmpty() ? "Enter name..." : newConfigName;
            int inputTextColor = newConfigName.isEmpty() ? new Color(120, 120, 120).getRGB() : new Color(200, 200, 200).getRGB();
            normalFont.drawString(displayText, buttonX + 4, inputY + 6, inputTextColor, false);

            if (System.currentTimeMillis() % 1000 < 500) {
                float cursorX = buttonX + 4 + fw(newConfigName);
                RenderUtil.drawRect(cursorX, inputY + 3, cursorX + 1, inputY + buttonHeight - 3, new Color(200, 200, 200).getRGB());
            }

            float confirmY = inputY + buttonHeight + 4;
            boolean confirmHovered = isHovered(mouseX, mouseY, buttonX, confirmY, buttonWidth / 2 - 2, 18);
            boolean cancelHovered = isHovered(mouseX, mouseY, buttonX + buttonWidth / 2 + 2, confirmY, buttonWidth / 2 - 2, 18);

            Color confirmColor = confirmHovered ? new Color(0, 120, 0, 180) : new Color(0, 80, 0, 150);
            Color cancelColor = cancelHovered ? new Color(120, 0, 0, 180) : new Color(80, 0, 0, 150);

            RenderUtil.drawRoundedRect(buttonX, confirmY, buttonWidth / 2 - 2, 18, 1, 1, 1, 1, confirmColor.getRGB());
            RenderUtil.drawRoundedRect(buttonX + buttonWidth / 2 + 2, confirmY, buttonWidth / 2 - 2, 18, 1, 1, 1, 1, cancelColor.getRGB());

            float confirmTextX = buttonX + (buttonWidth / 2 - 2) / 2 - fw("Create") / 2;
            float cancelTextX = buttonX + buttonWidth / 2 + 2 + (buttonWidth / 2 - 2) / 2 - fw("Cancel") / 2;

            normalFont.drawString("Create", confirmTextX, confirmY + 5, Color.WHITE.getRGB(), false);
            normalFont.drawString("Cancel", cancelTextX, confirmY + 5, Color.WHITE.getRGB(), false);
        }

        float resizeSize = 8;
        RenderUtil.drawRect(configPosX + configWidth - resizeSize, configPosY + configHeight - resizeSize, configPosX + configWidth, configPosY + configHeight, new Color(60, 60, 60, 100).getRGB());
    }

    private boolean configMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (configPosX == -1 || configPosY == -1) {
            configPosX = super.width / 2f - configWidth / 2;
            configPosY = super.height / 2f - configHeight / 2;
        }
        float titleBarHeight = CONFIG_TITLE_BAR_HEIGHT;

        if (mouseButton == 0) {
            WindowAction action = hitWindowControl(mouseX, mouseY, configPosX, configPosY, configWidth, titleBarHeight);
            if (action != WindowAction.NONE) {
                handleConfigWindowAction(action);
                return true;
            }
        }

        if (mouseButton == 0 && isHovered(mouseX, mouseY, configPosX, configPosY, configWidth, titleBarHeight)) {
            configDragging = true;
            configDragX = mouseX - configPosX;
            configDragY = mouseY - configPosY;
            return true;
        }

        float resizeSize = 8;
        if (mouseButton == 0 && isHovered(mouseX, mouseY, configPosX + configWidth - resizeSize, configPosY + configHeight - resizeSize, resizeSize, resizeSize)) {
            configResizing = true;
            configDragX = mouseX;
            configDragY = mouseY;
            return true;
        }

        float leftPanelWidth = configWidth * 0.55f;
        float contentY = configPosY + titleBarHeight + 8;
        List<String> configs = getAvailableConfigs();
        float itemY = contentY + 20;
        float itemHeight = 18;

        for (String config : configs) {
            if (isHovered(mouseX, mouseY, configPosX + 12, itemY, leftPanelWidth - 16, itemHeight)) {
                selectedConfig = config;
                return true;
            }
            itemY += itemHeight + 2;
        }

        float buttonX = configPosX + leftPanelWidth + 8;
        float buttonY = contentY + 8;
        float buttonWidth = (configWidth * 0.45f) - 16;
        float buttonHeight = 22;

        String[] buttons = {"Load", "Save", "Create", "Delete", "Folder"};
        for (String button : buttons) {
            if (isHovered(mouseX, mouseY, buttonX, buttonY, buttonWidth, buttonHeight)) {
                handleConfigAction(button);
                return true;
            }
            buttonY += buttonHeight + 6;
        }

        if (creatingNewConfig) {
            float inputY = buttonY + 8;
            float confirmY = inputY + buttonHeight + 4;

            if (isHovered(mouseX, mouseY, buttonX, confirmY, buttonWidth / 2 - 2, 18)) {
                if (!newConfigName.trim().isEmpty()) {
                    Config config = new Config(newConfigName.trim(), true);
                    config.save();
                    selectedConfig = newConfigName.trim();
                }
                creatingNewConfig = false;
                newConfigName = "";
                return true;
            } else if (isHovered(mouseX, mouseY, buttonX + buttonWidth / 2 + 2, confirmY, buttonWidth / 2 - 2, 18)) {
                creatingNewConfig = false;
                newConfigName = "";
                return true;
            }
        }
        return false;
    }

    private void handleConfigAction(String action) {
        switch (action) {
            case "Load":
                if (selectedConfig != null) {
                    new Config(selectedConfig, false).load();
                }
                break;
            case "Save":
                if (selectedConfig != null) {
                    new Config(selectedConfig, false).save();
                }
                break;
            case "Create":
                creatingNewConfig = true;
                newConfigName = "";
                break;
            case "Delete":
                if (selectedConfig != null) {
                    Config config = new Config(selectedConfig, false);
                    if (config.file.exists()) {
                        config.file.delete();
                    }
                    selectedConfig = null;
                }
                break;
            case "Folder":
                try {
                    Desktop.getDesktop().open(new File("./config/Unfair/"));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
        }
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
        float totalWidth = ICON_SIZE * 2 + ICON_SEPARATOR;
        float iconHeight = ICON_SIZE + 6;
        float iconX = (super.width - totalWidth) / 2;
        RenderUtil.drawRoundedRect(iconX - 4, 0, totalWidth + 8, iconHeight, 0, 0, 3, 3, color);

        if (isGuiOpen) {
            RenderUtil.drawRoundedRect(posX, posY + 15.0F, guiWidth, guiHeight - 15.0F, 0.0F, 0.0F, 6.0F, 6.0F, color);
            RenderUtil.drawRoundedRect(posX, posY, guiWidth, 15.0F, 6.0F, 6.0F, 0.0F, 0.0F, color);
        }

        if (configOpen) {
            RenderUtil.drawRoundedRect(configPosX, configPosY + CONFIG_TITLE_BAR_HEIGHT, configWidth, configHeight - CONFIG_TITLE_BAR_HEIGHT, 0.0F, 0.0F, 6.0F, 6.0F, color);
            RenderUtil.drawRoundedRect(configPosX, configPosY, configWidth, CONFIG_TITLE_BAR_HEIGHT, 6.0F, 6.0F, 0.0F, 0.0F, color);
        }
    }
}
