package cn.unfair.ui.clickgui.augustus;

import cn.unfair.Unfair;
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
    private static FontRenderer CACHED_TITLE_FONT;
    private static FontRenderer CACHED_NORMAL_FONT;
    private static int CACHED_SCALE = -1;

    private final File configFile = new File("./config/Unfair/", "augustus-clickgui.json");
    private final Map<Category, CategoryPanel> categoryPanels = new HashMap<>();
    private final List<Component> settingComponents = new ArrayList<>();

    private boolean dragging = false;
    private boolean resizing = false;
    private boolean waitingForKey = false;
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

        RenderUtil.drawRect(posX + 90, posY + 0.5f, posX + 92, posY + guiHeight, new Color(34, 34, 34).getRGB());
        RenderUtil.drawRect(posX + 90, posY + 40, posX + guiWidth, posY + 42, new Color(34, 34, 34).getRGB());

        renderCategories(mouseX, mouseY);
        renderModuleList(mouseX, mouseY);
        renderValues(mouseX, mouseY);
    }

    private void renderCategories(int mouseX, int mouseY) {
        float x = posX + SIDEBAR_WIDTH + 15.0F;
        float y = posY + (55.0F - fh()) / 2.0F;
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
        RenderUtil.drawRoundedRect(posX, posY + 15.0F, guiWidth, guiHeight - 15.0F, 0.0F, 0.0F, 6.0F, 6.0F, color);
        RenderUtil.drawRoundedRect(posX, posY, guiWidth, 15.0F, 6.0F, 6.0F, 0.0F, 0.0F, color);
    }
}
