package cn.unfair.ui.clickgui.raven.components;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import cn.unfair.Unfair;
import cn.unfair.mixin.IAccessorMinecraft;
import cn.unfair.module.Module;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.ui.clickgui.raven.Component;
import cn.unfair.util.AnimationUtil;
import cn.unfair.util.RenderUtil;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static cn.unfair.config.Config.mc;

public class CategoryComponent {
    private final int translucentBackground = new Color(0, 0, 0, 110).getRGB();
    private final int categoryNameColor = new Color(220, 220, 220).getRGB();
    public ArrayList<ModuleComponent> modules = new ArrayList<>();
    public String categoryName;
    public boolean opened;
    public int width;
    public int y;
    public int x;
    public int titleHeight;
    public boolean dragging;
    public int xx;
    public int yy;
    public boolean hovering = false;
    public boolean hoveringOverCategory = false;
    public long smoothStartTime;
    public long smoothScrollStartTime;
    public ScaledResolution scale;
    public float big;
    public int moduleY;
    private long textStartTime;
    private float smoothDuration;
    private float bigSettings;
    private float lastHeight;
    private int lastModuleY;
    private int screenHeight;
    private boolean scrolled;
    private int targetModuleY;
    private float closedHeight;
    private int visibleContentHeight;
    private int totalContentHeight;

    public CategoryComponent(String categoryName, List<Module> modules) {
        this.categoryName = categoryName;
        this.width = 92;
        this.x = 5;
        this.moduleY = this.y = 5;
        this.titleHeight = 13;
        this.smoothStartTime = 0L;
        this.textStartTime = 0L;
        this.xx = 0;
        this.opened = false;
        this.dragging = false;
        int moduleRenderY = this.titleHeight + 3;
        this.scale = new ScaledResolution(Minecraft.getMinecraft());
        this.targetModuleY = this.moduleY;

        for (Module mod : modules) {
            ModuleComponent b = new ModuleComponent(mod, this, moduleRenderY);
            this.modules.add(b);
            moduleRenderY += 16;
        }
    }

    public ArrayList<ModuleComponent> getModules() {
        return this.modules;
    }

    public void setX(int n, boolean limit) {
        if (limit) {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            int screenW = sr.getScaledWidth();
            n = Math.max(n, 2);
            n = Math.min(n, screenW - this.width - 4);
        }
        this.x = n;
    }

    public void setY(int y, boolean limit) {
        if (limit) {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            int screenH = sr.getScaledHeight();
            float catHeight = this.titleHeight;

            y = Math.max(y, 1);
            int maxY = (int) (screenH - catHeight - 5);
            y = Math.min(y, maxY);
        }
        this.moduleY = this.y = y;
        this.targetModuleY = y;
    }

    public void overTitle(boolean d) {
        this.dragging = d;
    }

    public boolean isOpened() {
        return this.opened;
    }

    public void setOpened(boolean on) {
        this.opened = on;
        this.smoothStartTime = AnimationUtil.start();
        this.smoothDuration = 500.0F;
        this.textStartTime = AnimationUtil.start();
    }

    public void mouseClicked(boolean on) {
        setOpened(on);
    }

    public void openModule(ModuleComponent component) {
        if (!component.isOpened) {
            closedHeight = this.y + this.titleHeight + big + 4;
        }
        this.smoothStartTime = AnimationUtil.start();
        this.smoothDuration = 200.0F;
    }

    public void onScroll(int mouseScrollInput) {
        if (!canScroll()) {
            return;
        }
        int scrollSpeed = 100;
        if (mouseScrollInput > 0) {
            this.targetModuleY += scrollSpeed;
        } else if (mouseScrollInput < 0) {
            this.targetModuleY -= scrollSpeed;
        }
        clampTargetModuleY();
        scrolled = true;

        this.smoothScrollStartTime = AnimationUtil.start();
    }

    public void render() {
        this.width = 92;
        int modulesHeight = 0;
        int settingsHeight = 0;
        if (!this.modules.isEmpty() && this.opened) {
            for (ModuleComponent c : this.modules) {
                settingsHeight += c.getHeight();
                int height = !c.isOpened ? 16 : c.getModuleHeight();
                if (modulesHeight + height > this.screenHeight * 0.9d) {
                    modulesHeight = (int) (this.screenHeight * 0.9d);
                    continue;
                }
                modulesHeight += c.getHeight();
            }
            big = modulesHeight;
            bigSettings = settingsHeight;
            visibleContentHeight = modulesHeight;
            totalContentHeight = settingsHeight;
            clampTargetModuleY();
        } else {
            big = 0;
            bigSettings = 0;
            visibleContentHeight = 0;
            totalContentHeight = 0;
            this.targetModuleY = this.y;
            this.moduleY = this.y;
        }

        float middlePos = (float) (this.x + this.width / 2 - mc.fontRendererObj.getStringWidth(this.categoryName) / 2);
        float xPos = opened ? middlePos : this.x + 12;
        float extra = this.y + this.titleHeight + modulesHeight + 4;

        if (smoothStartTime > 0L && AnimationUtil.elapsed(smoothStartTime) >= 330L) {
            smoothStartTime = 0L;
        }

        if (extra != lastHeight && smoothStartTime > 0L) {
            double diff = lastHeight - extra;
            if (diff < 0) {
                extra = AnimationUtil.value(lastHeight, this.y + this.titleHeight + modulesHeight + 4, smoothStartTime, smoothDuration, this.mcPartialTicks(), 1);
            } else if (diff > 0) {
                extra = AnimationUtil.value(this.opened ? closedHeight : lastHeight, this.y + this.titleHeight + modulesHeight + 4, smoothStartTime, smoothDuration, this.mcPartialTicks(), 1);
            }
        }

        float namePos = textStartTime == 0L ? xPos : AnimationUtil.value(this.x + 12, middlePos, textStartTime, 200.0F, this.mcPartialTicks(), 1);
        if (!this.opened) {
            namePos = textStartTime == 0L ? xPos : middlePos - AnimationUtil.value(0, this.width / 2 - mc.fontRendererObj.getStringWidth(this.categoryName) / 2 - 12, textStartTime, 200.0F, this.mcPartialTicks(), 1);
        }

        if (scrolled && smoothScrollStartTime > 0L) {
            if (AnimationUtil.elapsed(smoothScrollStartTime) <= 200L) {
                float interpolated = AnimationUtil.value(lastModuleY, targetModuleY, smoothScrollStartTime, 200.0F, this.mcPartialTicks(), 4);
                moduleY = (int) interpolated;
            } else {
                moduleY = targetModuleY;
                scrolled = false;
                smoothScrollStartTime = 0L;
            }
        } else {
            moduleY = targetModuleY;
        }
        lastModuleY = moduleY;

        lastHeight = extra;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        RenderUtil.scissor(this.x - 12, this.y - 12, this.width + 24, extra - this.y + 24);
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        int hudColor1 = hud.getColor(System.currentTimeMillis()).getRGB();
        int hudColor2 = hud.getColor(System.currentTimeMillis() + 500).getRGB();

        RenderUtil.drawRoundedGradientOutlinedRectangle(this.x - 2, this.y, this.x + this.width + 2, extra, 5, translucentBackground,
                hudColor1, hudColor2);
        renderItemForCategory(this.categoryName, this.x + 1, this.y + 4, opened || hovering);
        mc.fontRendererObj.drawString(this.categoryName, namePos, (float) (this.y + 2), categoryNameColor, false);
        RenderUtil.scissor(this.x - 2, this.y + this.titleHeight + 3, this.width + 6, extra - this.y - 4 - this.titleHeight);

        int prevY = this.y;
        this.y = this.moduleY;

        if (this.opened || smoothStartTime > 0L) {
            for (Component c2 : this.modules) {
                c2.render();
            }
        }
        this.y = prevY;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopMatrix();
    }

    public void updateHeight() {
        int y = this.titleHeight + 3;

        for (Component component : this.modules) {
            component.updateHeight(y);
            y += component.getHeight();
        }
    }

    public int getX() {
        return this.x;
    }

    public void setX(int n) {
        setX(n, false);
    }

    public int getY() {
        return this.y;
    }

    public void setY(int y) {
        setY(y, false);
    }

    public int getModuleY() {
        return this.moduleY;
    }

    public int getWidth() {
        return this.width;
    }

    public void mousePosition(int mouseX, int mouseY) {
        if (this.dragging) {
            int newX = mouseX - this.xx;
            int newY = mouseY - this.yy;

            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            int screenW = sr.getScaledWidth();
            int screenH = sr.getScaledHeight();

            float catHeight = this.titleHeight;

            newX = Math.max(newX, 2);
            newX = Math.min(newX, screenW - this.width - 4);

            newY = Math.max(newY, 1);
            int maxY = (int) (screenH - catHeight - 5);
            newY = Math.min(newY, maxY);

            this.setX(newX, false);
            this.setY(newY, false);
        }

        hoveringOverCategory = overCategory(mouseX, mouseY);
        hovering = overTitle(mouseX, mouseY);
    }

    public boolean overTitle(int x, int y) {
        return x >= this.x && x <= this.x + this.width && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + 1;
    }

    public boolean overCategory(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + big + 1;
    }

    public boolean overContent(int x, int y) {
        return this.opened
                && x >= this.x - 2
                && x <= this.x + this.width + 2
                && y >= this.y + this.titleHeight + 3
                && y <= this.y + this.titleHeight + 3 + this.visibleContentHeight;
    }

    public boolean canScroll() {
        return this.opened && hoveringOverCategory && this.totalContentHeight > this.visibleContentHeight;
    }

    public boolean draggable(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.titleHeight;
    }

    public boolean overRect(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && y >= this.y && y <= lastHeight;
    }

    private void renderItemForCategory(String categoryName, int x, int y, boolean enchant) {
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        double scale = 0.55;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        ItemStack itemStack = null;
        switch (categoryName.toLowerCase()) {
            case "combat":
                itemStack = new ItemStack(Items.diamond_sword);
                break;
            case "movement":
                itemStack = new ItemStack(Items.diamond_boots);
                break;
            case "player":
                itemStack = new ItemStack(Items.golden_apple);
                break;
            case "world":
                itemStack = new ItemStack(Items.map);
                break;
            case "render":
                itemStack = new ItemStack(Items.ender_eye);
                break;
            case "misc":
                itemStack = new ItemStack(Items.clock);
                break;
        }
        if (itemStack != null) {
            if (enchant) {
                if (!categoryName.equalsIgnoreCase("player")) {
                    itemStack.addEnchantment(Enchantment.unbreaking, 2);
                } else {
                    itemStack.setItemDamage(1);
                }
            }
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.disableBlend();
            renderItem.renderItemAndEffectIntoGUI(itemStack, (int) (x / scale), (int) (y / scale));
            GlStateManager.enableBlend();
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.scale(1, 1, 1);
        GlStateManager.popMatrix();
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    public void limitPositions() {
        setX(this.x, true);
        setY(this.y, true);
    }

    public String getName() {
        return categoryName;
    }

    public void onGuiClosed() {
        this.dragging = false;
        this.hovering = false;
        this.hoveringOverCategory = false;
        this.smoothStartTime = 0L;
        this.smoothScrollStartTime = 0L;
        this.textStartTime = 0L;
        this.scrolled = false;
        this.moduleY = this.y;
        this.targetModuleY = this.y;
        this.lastModuleY = this.y;
        this.closedHeight = 0.0F;
        this.big = 0.0F;
        this.bigSettings = 0.0F;
        this.visibleContentHeight = 0;
        this.totalContentHeight = 0;
        this.lastHeight = this.y + this.titleHeight + (this.opened ? 4.0F : 0.0F);
        this.updateHeight();
    }

    private void clampTargetModuleY() {
        int minModuleY = this.y - Math.max(0, this.totalContentHeight - this.visibleContentHeight);
        this.targetModuleY = Math.max(minModuleY, Math.min(this.targetModuleY, this.y));
    }

    private float mcPartialTicks() {
        return ((IAccessorMinecraft) mc).getTimer().renderPartialTicks;
    }
}
