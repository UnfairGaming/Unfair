package net.minecraft.client.gui;

import cn.unfair.Unfair;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.module.modules.render.Interface;
import cn.unfair.module.modules.render.PostProcessing;
import cn.unfair.util.animation.simple.SimpleAnimation;
import cn.unfair.util.font.CustomFontRenderer;
import cn.unfair.util.font.Fonts;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

import java.awt.*;
import java.util.WeakHashMap;

public class GuiButton extends Gui {
    protected static final ResourceLocation buttonTextures = ResourceLocation.of("textures/gui/widgets.png");
    /**
     * The x position of this control.
     */
    public int xPosition;
    /**
     * The y position of this control.
     */
    public int yPosition;
    /**
     * The string displayed on this control.
     */
    public String displayString;
    public int id;
    /**
     * True if this control is enabled, false to disable.
     */
    public boolean enabled;
    /**
     * Hides the button completely if false.
     */
    public boolean visible;
    /**
     * Button width in pixels
     */
    protected int width;
    /**
     * Button height in pixels
     */
    protected int height;
    protected boolean hovered;

    private static final WeakHashMap<GuiButton, SimpleAnimation> hoverAnimations = new WeakHashMap<>();

    public GuiButton(int buttonId, int x, int y, String buttonText) {
        this(buttonId, x, y, 200, 20, buttonText);
    }

    public GuiButton(int buttonId, int x, int y, int widthIn, int heightIn, String buttonText) {
        this.width = 200;
        this.height = 20;
        this.enabled = true;
        this.visible = true;
        this.id = buttonId;
        this.xPosition = x;
        this.yPosition = y;
        this.width = widthIn;
        this.height = heightIn;
        this.displayString = buttonText;
    }

    /**
     * Returns 0 if the button is disabled, 1 if the mouse is NOT hovering over this button and 2 if it IS hovering over
     * this button.
     */
    protected int getHoverState(boolean mouseOver) {
        int i = 1;

        if (!this.enabled) {
            i = 0;
        } else if (mouseOver) {
            i = 2;
        }

        return i;
    }

    /**
     * Draws this button to the screen.
     */
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (this.visible) {
            this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;

            if (GuiButton.isCustomButtonEnabled()) {
                float hoverScale = this.getHoverScale();
                float centerX = this.xPosition + this.width / 2.0F;
                float centerY = this.yPosition + this.height / 2.0F;
                float x = RenderUtil.scaleAround(this.xPosition, centerX, hoverScale);
                float y = RenderUtil.scaleAround(this.yPosition, centerY, hoverScale);
                float w = this.width * hoverScale;
                float h = this.height * hoverScale;
                GuiButton.drawCustomButton(x, y, w, h, this.enabled, this.hovered, this.displayString);
            } else {
                FontRenderer fontrenderer = mc.fontRendererObj;
                mc.getTextureManager().bindTexture(buttonTextures);
                GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
                int i = this.getHoverState(this.hovered);
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
                GlStateManager.blendFunc(770, 771);
                this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 46 + i * 20, this.width / 2, this.height);
                this.drawTexturedModalRect(this.xPosition + this.width / 2, this.yPosition, 200 - this.width / 2, 46 + i * 20, this.width / 2, this.height);
                int j = 14737632;

                if (!this.enabled) {
                    j = 10526880;
                } else if (this.hovered) {
                    j = 16777120;
                }

                this.drawCenteredString(fontrenderer, this.displayString, this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, j);
            }

            this.mouseDragged(mc, mouseX, mouseY);
        }
    }

    private float getHoverScale() {
        SimpleAnimation animation = hoverAnimations.computeIfAbsent(this, ignored -> new SimpleAnimation(1.0F));
        animation.setAnimation(this.hovered && this.enabled ? 0.96F : 1.0F, 12.0F);
        return animation.getValue();
    }

    public static void drawCustomButton(float x, float y, float width, float height, boolean enabled, boolean hovered, String text) {
        Color hudColor = HUD.getColor(System.currentTimeMillis());
        int backgroundAlpha = GuiButton.getButtonAlpha();
        int backgroundColor = enabled
                ? new Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue(), backgroundAlpha).getRGB()
                : new Color(hudColor.getRed(), hudColor.getGreen(), hudColor.getBlue(), backgroundAlpha * 2 / 3).getRGB();
        float radius = GuiButton.getButtonRadius();

        RenderUtil.drawRoundedRectangle(x, y, width, height, radius, backgroundColor);

        if (GuiButton.isBlurEnabled()) {
            ShaderElement.addBlurTask(() -> RenderUtil.drawRoundedRectangle(x, y, width, height, radius, 0xFF000000));
        }
        if (GuiButton.isBloomEnabled()) {
            ShaderElement.addBloomTask(() -> RenderUtil.drawRoundedRectangle(x, y, width, height, radius, 0xFFFFFFFF));
        }

        CustomFontRenderer font = Fonts.interMedium.get(16.0F);
        String content = text == null ? "" : text;
        float textX = x + width / 2.0F - font.getStringWidth(content) / 2.0F;
        float textY = y + font.getMiddleOfBox(height);
        int textColor = enabled ? (hovered ? 0xE6FFFFFF : 0xC8FFFFFF) : 0x59FFFFFF;
        font.drawStringWithShadow(content, textX, textY, textColor);
    }

    private static HUD getHud() {
        if (Unfair.moduleManager == null) {
            return null;
        }
        return (HUD) Unfair.moduleManager.getModule(HUD.class);
    }

    protected static float getButtonRadius() {
        HUD hud = GuiButton.getHud();
        return hud != null ? hud.roundRadius.getValue() : 2.5F;
    }

    protected static int getButtonAlpha() {
        HUD hud = GuiButton.getHud();
        return hud != null ? hud.background.getValue() * 255 / 100 : 127;
    }

    protected static boolean isCustomButtonEnabled() {
        if (Unfair.moduleManager == null) {
            return false;
        }
        Interface iface = (Interface) Unfair.moduleManager.getModule(Interface.class);
        return iface != null && iface.isEnabled() && iface.customButton.getValue();
    }

    protected static boolean isBlurEnabled() {
        return GuiButton.postProcessingEnabled(false);
    }

    protected static boolean isBloomEnabled() {
        return GuiButton.postProcessingEnabled(true);
    }

    private static boolean postProcessingEnabled(boolean bloom) {
        if (Unfair.moduleManager == null) {
            return false;
        }
        PostProcessing pp = (PostProcessing) Unfair.moduleManager.getModule(PostProcessing.class);
        return pp != null && pp.isEnabled() && (bloom ? pp.bloom.getValue() : pp.blur.getValue());
    }

    /**
     * Fired when the mouse button is dragged. Equivalent of MouseListener.mouseDragged(MouseEvent e).
     */
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
    }

    /**
     * Fired when the mouse button is released. Equivalent of MouseListener.mouseReleased(MouseEvent e).
     */
    public void mouseReleased(int mouseX, int mouseY) {
    }

    /**
     * Returns true if the mouse has been pressed on this control. Equivalent of MouseListener.mousePressed(MouseEvent
     * e).
     */
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        return this.enabled && this.visible && mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
    }

    /**
     * Whether the mouse cursor is currently over the button.
     */
    public boolean isMouseOver() {
        return this.hovered;
    }

    public void drawButtonForegroundLayer(int mouseX, int mouseY) {
    }

    public void playPressSound(SoundHandler soundHandlerIn) {
        soundHandlerIn.playSound(PositionedSoundRecord.create(ResourceLocation.of("gui.button.press"), 1.0F));
    }

    public int getButtonWidth() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = width;
    }
}