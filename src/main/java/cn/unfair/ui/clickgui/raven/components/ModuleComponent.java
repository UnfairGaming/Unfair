package cn.unfair.ui.clickgui.raven.components;

import cn.unfair.Unfair;
import cn.unfair.module.Module;
import cn.unfair.property.Property;
import cn.unfair.property.properties.*;
import cn.unfair.ui.clickgui.raven.Component;
import cn.unfair.ui.clickgui.raven.dataset.impl.FloatSlider;
import cn.unfair.ui.clickgui.raven.dataset.impl.IntSlider;
import cn.unfair.ui.clickgui.raven.dataset.impl.PercentageSlider;
import cn.unfair.util.AnimationUtil;
import cn.unfair.util.RenderUtil;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.unfair.config.Config.mc;

public class ModuleComponent implements Component {
    public final ArrayList<Component> settings;
    private final int enabledColor = new Color(24, 154, 255).getRGB();
    private final int disabledColor = new Color(192, 192, 192).getRGB();
    private final int originalHoverAlpha = 120;
    private final int hoverColor = (new Color(0, 0, 0, originalHoverAlpha)).getRGB();
    public Module mod;
    public CategoryComponent category;
    public int yPos;
    public boolean isOpened;
    private boolean hovering;
    private long hoverStartTime;
    private boolean hoverStarted;
    private long smoothStartTime;
    private int heightStart = 16;
    private int smoothingY = 16;
    private int targetHeight = 16;
    private boolean isAnimatingHeight = false;

    public ModuleComponent(Module mod, CategoryComponent category, int yPos) {
        this.mod = mod;
        this.category = category;
        this.yPos = yPos;
        this.settings = new ArrayList<>();
        this.isOpened = false;
        int y = yPos + 12;
        if (!Unfair.propertyManager.properties.get(mod.getClass()).isEmpty()) {
            for (Property<?> baseProperty : Unfair.propertyManager.properties.get(mod.getClass())) {
                if (baseProperty instanceof BooleanProperty property) {
                    CheckBoxComponent c = new CheckBoxComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof FloatProperty property) {
                    SliderComponent c = new SliderComponent(new FloatSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof IntProperty property) {
                    SliderComponent c = new SliderComponent(new IntSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof PercentProperty property) {
                    SliderComponent c = new SliderComponent(new PercentageSlider(property), this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ModeProperty property) {
                    ModeComponent c = new ModeComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof ColorProperty property) {
                    ColorSliderComponent c = new ColorSliderComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                } else if (baseProperty instanceof TextProperty property) {
                    TextComponent c = new TextComponent(property, this, y);
                    this.settings.add(c);
                    y += c.getHeight();
                }
            }
        }

        this.settings.add(new BindComponent(this, y));
    }

    public void updateHeight(int newY) {
        this.yPos = newY;
        int y = this.yPos + 16;
        Iterator var3 = this.settings.iterator();

        while (true) {
            while (var3.hasNext()) {
                Component co = (Component) var3.next();
                if (!isVisible(co)) {
                    continue;
                }
                co.updateHeight(y);
                y += co.getHeight();
            }

            return;
        }
    }

    public void render() {
        if (hovering || hoverStartTime > 0L) {
            float hoverProgress = AnimationUtil.progress(hoverStartTime, 75.0F, this.mcPartialTicks(), 1);
            double hoverAlpha = hovering ? hoverProgress * originalHoverAlpha : (1.0F - hoverProgress) * originalHoverAlpha;
            if (!hovering && AnimationUtil.finished(hoverStartTime, 75.0F)) {
                hoverStartTime = 0L;
            }
            RenderUtil.drawRoundedRectangle(this.category.getX(), this.category.getY() + yPos, this.category.getX() + this.category.getWidth(), this.category.getY() + 16 + this.yPos, 4, mergeAlpha(hoverColor, (int) hoverAlpha));
        }
        int button_rgb = this.mod.isEnabled() ? enabledColor : disabledColor;

        if (smoothStartTime > 0L) {
            if (isAnimatingHeight) {
                smoothingY = AnimationUtil.value(heightStart, targetHeight, smoothStartTime, 200.0F, this.mcPartialTicks(), 1);
                if (AnimationUtil.finished(smoothStartTime, 200.0F)) {
                    smoothingY = targetHeight;
                    smoothStartTime = 0L;
                    isAnimatingHeight = false;
                }
            } else if (isOpened) {
                smoothingY = AnimationUtil.value(heightStart, targetHeight, smoothStartTime, 200.0F, this.mcPartialTicks(), 1);
                if (AnimationUtil.finished(smoothStartTime, 200.0F)) {
                    smoothingY = targetHeight;
                    smoothStartTime = 0L;
                }
            } else {
                smoothingY = AnimationUtil.value(heightStart, targetHeight, smoothStartTime, 200.0F, this.mcPartialTicks(), 1);
                if (AnimationUtil.finished(smoothStartTime, 200.0F)) {
                    smoothingY = 16;
                    smoothStartTime = 0L;
                }
            }
            if (smoothStartTime > 0L && AnimationUtil.elapsed(smoothStartTime) >= 300L) {
                smoothingY = isAnimatingHeight ? targetHeight : (isOpened ? getModuleHeight() : 16);
                smoothStartTime = 0L;
                isAnimatingHeight = false;
            }
            this.category.updateHeight();
        }

        mc.fontRendererObj.drawString(this.mod.getName(), this.category.getX() + this.category.getWidth() / 2 - mc.fontRendererObj.getStringWidth(this.mod.getName()) / 2, this.category.getY() + this.yPos + 2, button_rgb);
        boolean scissorRequired = smoothStartTime > 0L;
        if (scissorRequired) {
            GL11.glPushMatrix();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            RenderUtil.scissor(this.category.getX() - 2, this.category.getY() + this.yPos + 4, this.category.getWidth() + 4, Math.max(0, smoothingY + 4));
        }

        if (this.isOpened || smoothStartTime > 0L) {
            for (Component settingComponent : this.settings) {
                if (!isVisible(settingComponent)) {
                    continue;
                }
                settingComponent.render();
            }
        }

        if (scissorRequired) {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glPopMatrix();
        }
    }

    public int getHeight() {
        if (smoothStartTime > 0L) {
            return smoothingY;
        }
        if (!this.isOpened) {
            return 16;
        } else {
            int h = 16;
            Iterator var2 = this.settings.iterator();

            while (true) {
                while (var2.hasNext()) {
                    Component c = (Component) var2.next();
                    if (!isVisible(c)) {
                        continue;
                    }
                    h += c.getHeight();
                }

                return h;
            }
        }
    }

    public void startHeightAnimation(int fromHeight, int toHeight) {
        this.smoothingY = fromHeight;
        this.heightStart = fromHeight;
        this.targetHeight = toHeight;
        this.isAnimatingHeight = true;
        this.smoothStartTime = AnimationUtil.start();
        this.category.updateHeight();
    }

    public void onSliderChange() {
        for (Component c : this.settings) {
            if (c instanceof SliderComponent) {
                ((SliderComponent) c).onSliderChange();
            }
        }
    }

    public int getModuleHeight() {
        int h = 16;
        Iterator var2 = this.settings.iterator();

        while (true) {
            while (var2.hasNext()) {
                Component c = (Component) var2.next();
                if (!isVisible(c)) {
                    continue;
                }
                h += c.getHeight();
            }

            return h;
        }
    }

    public void drawScreen(int x, int y) {
        for (Component c : this.settings) {
            if (!isVisible(c)) {
                continue;
            }
            c.drawScreen(x, y);
        }
        if (overModuleName(x, y) && this.category.opened) {
            hovering = true;
            if (hoverStartTime == 0L) {
                hoverStartTime = AnimationUtil.start();
                hoverStarted = true;
            }
        } else {
            if (hovering && hoverStarted) {
                hoverStartTime = AnimationUtil.start();
            }
            hoverStarted = false;
            hovering = false;
        }
    }

    public String getName() {
        return mod.getName();
    }

    public void onClick(int x, int y, int mouse) {
        if (this.overModuleName(x, y) && mouse == 0) {
            this.mod.toggle();
        }

        if (this.overModuleName(x, y) && mouse == 1) {
            this.isOpened = !this.isOpened;
            this.heightStart = this.smoothingY;
            this.targetHeight = this.isOpened ? this.getModuleHeight() : 16;
            this.isAnimatingHeight = false;
            this.smoothStartTime = AnimationUtil.start();
            this.category.updateHeight();
        }

        for (Component settingComponent : this.settings) {
            if (!isVisible(settingComponent)) {
                continue;
            }
            settingComponent.onClick(x, y, mouse);
        }
    }

    public void mouseReleased(int x, int y, int m) {
        for (Component c : this.settings) {
            c.mouseReleased(x, y, m);
        }

    }

    public void keyTyped(char t, int k) {
        for (Component c : this.settings) {
            c.keyTyped(t, k);
        }
    }

    public void onScroll(int scroll) {
        for (Component component : this.settings) {
            component.onScroll(scroll);
        }
    }

    public void onGuiClosed() {
        for (Component c : this.settings) {
            c.onGuiClosed();
        }
        smoothStartTime = 0L;
        hoverStartTime = 0L;
        smoothingY = getHeight();
    }

    public boolean overModuleName(int x, int y) {
        return x > this.category.getX() && x < this.category.getX() + this.category.getWidth() && y > this.category.getModuleY() + this.yPos && y < this.category.getModuleY() + 16 + this.yPos;
    }

    public boolean isVisible(Component component) {
        return component.isVisible();
    }

    private int mergeAlpha(int color, int alpha) {
        int newAlpha = (alpha & 0xFF) << 24;
        return (color & 0x00FFFFFF) | newAlpha;
    }

    private float mcPartialTicks() {
        return mc.timer.renderPartialTicks;
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void setComponentStartAt(int newOffsetY) {
        this.yPos = newOffsetY;
        int y = this.yPos + 16;

        for (Component c : this.settings) {
            c.setComponentStartAt(y);
            if (c.isVisible()) {
                y += c.getHeight();
            }
        }
    }

    @Override
    public void draw(AtomicInteger offset) {
    }

    @Override
    public void update(int mousePosX, int mousePosY) {
    }

    @Override
    public void mouseDown(int x, int y, int button) {
    }
}
