package cn.unfair.ui.clickgui.raven;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PostProcessBlurEvent;
import cn.unfair.module.Category;
import cn.unfair.module.modules.render.ClickGui;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.ui.clickgui.raven.components.BindComponent;
import cn.unfair.ui.clickgui.raven.components.CategoryComponent;
import cn.unfair.ui.clickgui.raven.components.ModuleComponent;
import cn.unfair.util.AnimationUtil;
import cn.unfair.util.RenderUtil;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

public class RavenClickGui extends GuiScreen {
    public static ArrayList<CategoryComponent> categories;
    private static RavenClickGui instance;
    private static boolean isNotFirstOpen;
    private final File configFile = new File("./config/Unfair/", "clickgui.txt");
    private final String clientName = "Unfair";
    private final String developer = "dev, UnfairGaming";
    public int originalScale;
    public int previousScale;
    private long logoSmoothWidthStart;
    private long logoSmoothLengthStart;
    private long smoothEntityStart;
    private long backgroundFadeStart;
    private long blurSmoothStart;
    private long footerSlideStart;
    private float partialTicks;
    private boolean clickGuiOpen = false;
    private long openedTime;
    private int hudColorCached = Color.white.getRGB();
    private long lastHudColorUpdate = 0;
    public RavenClickGui() {
        instance = this;
        categories = new ArrayList<>();
        int y = 5;
        Category[] values = Category.values();

        for (int i = 0; i < values.length; ++i) {
            Category c = values[i];
            CategoryComponent categoryComponent = new CategoryComponent(c.getDisplayName(), Unfair.moduleManager.getModulesByCategory(c));
            categoryComponent.setY(y, false);
            categories.add(categoryComponent);
            y += 20;
        }

        loadPositions();
    }

    public static RavenClickGui getInstance() {
        return instance;
    }

    public void initMain() {
        this.logoSmoothWidthStart = AnimationUtil.start();
        this.logoSmoothLengthStart = AnimationUtil.start();
        this.smoothEntityStart = AnimationUtil.start();
        this.backgroundFadeStart = AnimationUtil.start();
        this.blurSmoothStart = AnimationUtil.start();
        this.footerSlideStart = AnimationUtil.start();
    }

    @Override
    public void initGui() {
        super.initGui();
        if (!isNotFirstOpen) {
            isNotFirstOpen = true;
            this.previousScale = 2;
        }
        ScaledResolution sr = new ScaledResolution(this.mc);
        for (CategoryComponent categoryComponent : categories) {
            categoryComponent.setScreenHeight(sr.getScaledHeight());
        }
        this.previousScale = 2;
    }

    @Override
    public void drawScreen(int x, int y, float p) {
        this.partialTicks = p;

        drawRect(0, 0, this.width, this.height, (int) (this.getLerpValueFloat(this.backgroundFadeStart, 450.0F, 0.0F, 0.7F, 2) * 255.0F) << 24);

        // Update cached HUD color every 50ms
        if (System.currentTimeMillis() - lastHudColorUpdate > 50) {
            HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
            hudColorCached = hud.getColor(System.currentTimeMillis()).getRGB();
            lastHudColorUpdate = System.currentTimeMillis();
        }

        int h = this.height / 4;
        int wd = this.width / 2;
        int w_c = 30 - this.getLerpValueInt(this.logoSmoothWidthStart, 500.0F, 0, 30, 3);

        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        int hudColor1 = hud.getColor(System.currentTimeMillis()).getRGB();
        int hudColor2 = hud.getColor(System.currentTimeMillis() + 300).getRGB();
        int hudColor3 = hud.getColor(System.currentTimeMillis() + 600).getRGB();
        int hudColor4 = hud.getColor(System.currentTimeMillis() + 900).getRGB();
        int hudColor5 = hud.getColor(System.currentTimeMillis() + 1200).getRGB();
        int hudColor6 = hud.getColor(System.currentTimeMillis() + 1500).getRGB();

        this.drawCenteredString(this.fontRendererObj, "U", wd + 1 - w_c, h - 25, hudColor6);
        this.drawCenteredString(this.fontRendererObj, "n", wd - w_c, h - 15, hudColor5);
        this.drawCenteredString(this.fontRendererObj, "f", wd - w_c, h - 5, hudColor4);
        this.drawCenteredString(this.fontRendererObj, "a", wd - w_c, h + 5, hudColor3);
        this.drawCenteredString(this.fontRendererObj, "i", wd - w_c, h + 15, hudColor2);
        this.drawCenteredString(this.fontRendererObj, "r", wd + 1 + w_c, h + 30, hudColor1);
        this.drawVerticalLine(wd - 10 - w_c, h - 30, h + 43, Color.white.getRGB());
        this.drawVerticalLine(wd + 10 + w_c, h - 30, h + 43, Color.white.getRGB());
        if (this.logoSmoothLengthStart > 0L) {
            int r = this.getLerpValueInt(this.logoSmoothLengthStart, 350.0F, 0, 20, 2);
            this.drawHorizontalLine(wd - 10, wd - 10 + r, h - 29, -1);
            this.drawHorizontalLine(wd + 10, wd + 10 - r, h + 42, -1);
        }

        for (CategoryComponent c : categories) {
            c.render();
            c.mousePosition(x, y);

            for (Component m : c.getModules()) {
                m.drawScreen(x, y);
            }
        }

        GL11.glColor3f(1.0f, 1.0f, 1.0f);
        GlStateManager.pushMatrix();
        GlStateManager.disableBlend();
        if (this.mc.thePlayer != null) {
            GuiInventory.drawEntityOnScreen(this.width + 15 - this.getLerpValueInt(this.smoothEntityStart, 650.0F, 0, 40, 2), this.height - 10, 40, (float) (this.width - 25 - x), (float) (this.height - 50 - y), this.mc.thePlayer);
        }
        GlStateManager.enableBlend();
        GlStateManager.popMatrix();

        onRenderTick(p);
    }

    private void onRenderTick(float partialTicks) {
        if (!clickGuiOpen && this.mc.currentScreen instanceof RavenClickGui) {
            clickGuiOpen = true;
            this.footerSlideStart = AnimationUtil.start();
            openedTime = System.currentTimeMillis();
        } else if (!(this.mc.currentScreen instanceof RavenClickGui)) {
            clickGuiOpen = false;
        } else {
            int[] displaySize = {this.width, this.height};
            int y = displaySize[1] + (8 - this.getLerpValueInt(this.footerSlideStart, 600.0F, 0, 30, 2));

            mc.fontRendererObj.drawString(clientName + "-" + Unfair.version, 4, y, hudColorCached, true);

            long elapsedTime = System.currentTimeMillis() - openedTime + 50L;
            int characterIndex = (int) (elapsedTime / 200L);
            y += this.fontRendererObj.FONT_HEIGHT + 1;

            if (characterIndex < developer.length()) {
                String obfuscated = "";

                for (int i = 0; i < developer.length(); ++i) {
                    char currentChar = i < characterIndex
                            ? developer.charAt(i)
                            : (char) ((new java.util.Random()).nextInt(26) + 'a');
                    obfuscated += currentChar;
                }

                mc.fontRendererObj.drawString(obfuscated, 4, y, hudColorCached, true);
            } else {
                mc.fontRendererObj.drawString(developer, 4, y, hudColorCached, true);
            }
        }
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton == 0) {
            boolean draggingAssigned = false;
            for (int i = categories.size() - 1; i >= 0; i--) {
                CategoryComponent category = categories.get(i);
                if (!draggingAssigned && category.draggable(mouseX, mouseY)) {
                    category.overTitle(true);
                    category.xx = mouseX - category.getX();
                    category.yy = mouseY - category.getY();
                    category.dragging = true;
                    draggingAssigned = true;
                } else {
                    category.overTitle(false);
                }
            }
        }

        if (mouseButton == 1) {
            boolean toggled = false;
            for (int i = categories.size() - 1; i >= 0; i--) {
                CategoryComponent category = categories.get(i);
                if (!toggled && category.overTitle(mouseX, mouseY)) {
                    category.mouseClicked(!category.isOpened());
                    toggled = true;
                }
            }
        }

        for (CategoryComponent category : categories) {
            if (category.isOpened() && !category.getModules().isEmpty() && category.overRect(mouseX, mouseY)) {
                for (Component component : category.getModules()) {
                    if (component instanceof ModuleComponent) {
                        ModuleComponent moduleComponent = (ModuleComponent) component;
                        moduleComponent.onClick(mouseX, mouseY, mouseButton);
                        category.openModule(moduleComponent);
                    }
                }
            }
        }
    }

    public void mouseReleased(int x, int y, int button) {
        if (button == 0) {
            Iterator<CategoryComponent> iterator = categories.iterator();
            while (iterator.hasNext()) {
                CategoryComponent category = iterator.next();
                category.overTitle(false);
                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.mouseReleased(x, y, button);
                    }
                }
            }
        }
    }

    @Override
    public void handleMouseInput() {
        try {
            super.handleMouseInput();
        } catch (IOException e) {
            e.printStackTrace();
        }
        int wheelInput = Mouse.getDWheel();
        if (wheelInput != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;

            boolean handled = false;
            for (int i = categories.size() - 1; i >= 0; i--) {
                CategoryComponent category = categories.get(i);
                if (category.overContent(mouseX, mouseY)) {
                    category.onScroll(wheelInput);
                    handled = category.canScroll();
                    break;
                }
            }

            if (!handled) {
                for (int i = categories.size() - 1; i >= 0; i--) {
                    CategoryComponent category = categories.get(i);
                    if (category.overTitle(mouseX, mouseY)) {
                        break;
                    }
                    if (category.overCategory(mouseX, mouseY)) {
                        category.onScroll(wheelInput);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void setWorldAndResolution(Minecraft p_setWorldAndResolution_1_, final int p_setWorldAndResolution_2_, final int p_setWorldAndResolution_3_) {
        this.mc = p_setWorldAndResolution_1_;
        originalScale = this.mc.gameSettings.guiScale;
        this.mc.gameSettings.guiScale = 2;
        this.itemRender = p_setWorldAndResolution_1_.getRenderItem();
        this.fontRendererObj = p_setWorldAndResolution_1_.fontRendererObj;
        final ScaledResolution scaledresolution = new ScaledResolution(this.mc);
        this.width = scaledresolution.getScaledWidth();
        this.height = scaledresolution.getScaledHeight();
        this.buttonList.clear();
        this.initGui();
    }

    @Override
    public void keyTyped(char t, int k) {
        if (k == Keyboard.KEY_ESCAPE && !binding()) {
            this.mc.displayGuiScreen(null);
        } else {
            Iterator<CategoryComponent> iterator = categories.iterator();
            while (iterator.hasNext()) {
                CategoryComponent category = iterator.next();

                if (category.isOpened() && !category.getModules().isEmpty()) {
                    for (Component module : category.getModules()) {
                        module.keyTyped(t, k);
                    }
                }
            }
        }
    }

    @Override
    public void onGuiClosed() {
        this.logoSmoothLengthStart = 0L;
        this.footerSlideStart = 0L;
        for (CategoryComponent c : categories) {
            c.onGuiClosed();
            for (Component m : c.getModules()) {
                m.onGuiClosed();
            }
        }
        this.mc.gameSettings.guiScale = originalScale;
        savePositions();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private boolean binding() {
        for (CategoryComponent c : categories) {
            for (Component component : c.getModules()) {
                if (component instanceof ModuleComponent) {
                    ModuleComponent moduleComponent = (ModuleComponent) component;
                    for (Component setting : moduleComponent.settings) {
                        if (setting instanceof BindComponent && ((BindComponent) setting).isBinding) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        for (CategoryComponent cat : categories) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", cat.getX());
            pos.addProperty("y", cat.getY());
            pos.addProperty("open", cat.isOpened());
            json.add(cat.getName(), pos);
        }
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        try (java.io.FileWriter writer = new java.io.FileWriter(configFile)) {
            gson.toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (java.io.FileReader reader = new java.io.FileReader(configFile)) {
            JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            for (CategoryComponent cat : categories) {
                if (json.has(cat.getName())) {
                    JsonObject pos = json.getAsJsonObject(cat.getName());
                    cat.setX(pos.get("x").getAsInt());
                    cat.setY(pos.get("y").getAsInt());
                    cat.setOpened(pos.get("open").getAsBoolean());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private float getLerpValueFloat(long startTime, float duration, float begin, float end, int easing) {
        return AnimationUtil.value(begin, end, startTime, duration, this.partialTicks, easing);
    }

    private int getLerpValueInt(long startTime, float duration, int begin, int end, int easing) {
        return AnimationUtil.value(begin, end, startTime, duration, this.partialTicks, easing);
    }
}
