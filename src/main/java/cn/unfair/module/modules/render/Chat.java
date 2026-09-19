package cn.unfair.module.modules.render;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.Render2DEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.animation.simple.SimpleAnimation;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

public class Chat extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty background = new PercentProperty("Background", 50);
    public final FloatProperty scrollSmoothSpeed = new FloatProperty("ScrollSmooth", 12.0F, 0.0F, 50.0F);
    public final FloatProperty openAnimSpeed = new FloatProperty("OpenAnimSpeed", 12.0F, 0.0F, 50.0F);
    public final FloatProperty openAnimDistance = new FloatProperty("OpenAnimDistance", 12.0F, 0.0F, 50.0F);

    private final SimpleAnimation renderOffsetAnimation = new SimpleAnimation();
    private int lastScrollPos;
    private boolean wasChatOpen;
    private float renderOffsetY;
    private long lastUpdateTime;
    private int lastVisibleLines = 0;

    public Chat() {
        super("Chat", false, true);
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public static Chat getModule() {
        if (Unfair.moduleManager == null) {
            return null;
        }
        return (Chat) Unfair.moduleManager.modules.get(Chat.class);
    }

    public boolean shouldRenderChat() {
        return this.isEnabled() && mc.theWorld != null && mc.thePlayer != null;
    }

    public boolean shouldRenderEffects() {
        return this.shouldRenderChat()
                && this.background.getValue() > 0
                && (this.getVisibleLines() > 0 || mc.currentScreen instanceof GuiChat);
    }

    public float getRenderOffset() {
        return this.renderOffsetY;
    }

    public float getRadius() {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        if (hud == null) {
            return 1.5F;
        }
        return hud.roundRadius.getValue() * hud.scale.getValue();
    }

    private int getBackgroundColor() {
        int alpha = (int) (this.background.getValue().floatValue() / 100.0F * 230.0F);
        return alpha << 24 | (8 << 16 | 10 << 8 | 14);
    }

    public boolean useShadow() {
        HUD hud = (HUD) Unfair.moduleManager.modules.get(HUD.class);
        return hud != null && hud.shadow.getValue();
    }

    public int getVisibleLines() {
        return this.lastVisibleLines;
    }

    public void drawChatBackgroundScreen(float x, float y, float width, float height) {
        ScaledResolution sr = new ScaledResolution(mc);
        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, -(float) (sr.getScaledHeight() - 48), 0.0F);
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRectangle(x, y, width, height, this.getRadius(), this.getBackgroundColor());
        RenderUtil.disableRenderState();
        GlStateManager.popMatrix();
    }

    public void setVisibleLines(int lines) {
        this.lastVisibleLines = lines;
    }

    public void drawInputBackground(float x, float y, float width, float height) {
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRectangle(x, y, width, height, this.getRadius(), this.getBackgroundColor());
        RenderUtil.disableRenderState();
    }

    public void renderMask(float x, float y, float width, float height, int color) {
        RenderUtil.enableRenderState();
        RenderUtil.drawRoundedRectangle(x, y, width, height, this.getRadius(), color);
        RenderUtil.disableRenderState();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.PRE) {
            return;
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.ingameGUI == null) {
            return;
        }
        long now = System.currentTimeMillis();
        this.lastUpdateTime = now;

        GuiNewChat chatGui = mc.ingameGUI.getChatGUI();
        boolean chatOpen = mc.currentScreen instanceof GuiChat;

        if (!this.wasChatOpen && chatOpen) {
            this.renderOffsetAnimation.setValue(this.openAnimDistance.getValue());
        }
        this.wasChatOpen = chatOpen;

        int targetScroll = chatGui.getScrollPos();
        float scrollDelta = targetScroll - this.lastScrollPos;
        this.lastScrollPos = targetScroll;
        if (scrollDelta != 0.0F && !chatOpen) {
            this.renderOffsetAnimation.setValue(this.renderOffsetAnimation.getValue() - scrollDelta * 9.0F);
        }

        float speed = chatOpen
                ? Math.max(0.1F, this.openAnimSpeed.getValue())
                : Math.max(0.1F, this.scrollSmoothSpeed.getValue());
        this.renderOffsetAnimation.setAnimation(0.0F, speed);
        this.renderOffsetY = this.renderOffsetAnimation.getValue();
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.shouldRenderEffects()) {
            return;
        }
        ShaderElement.addBlurTask(() -> this.renderPostProcessMask(0xFF000000));
        ShaderElement.addBloomTask(() -> this.renderPostProcessMask(0xFFFFFFFF));
    }

    private void renderPostProcessMask(int color) {
        if (!this.shouldRenderEffects() || mc.ingameGUI == null) {
            return;
        }
        GuiNewChat chatGui = mc.ingameGUI.getChatGUI();
        if (chatGui == null) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        float f1 = chatGui.getChatScale();
        int l = (int) Math.ceil((float) chatGui.getChatWidth() / f1);
        int lineCount = this.getVisibleLines();

        GlStateManager.pushMatrix();
        this.renderMask(2.0F, (float) (sr.getScaledHeight() - 48) + 20.0F - lineCount * 9.0F * f1, (l + 4) * f1, lineCount * 9.0F * f1, color);
        GlStateManager.popMatrix();

        if (mc.currentScreen instanceof GuiChat) {
            this.renderMask(2.0F, (float) (sr.getScaledHeight() - 14), sr.getScaledWidth() - 4.0F, 12.0F, color);
        }
    }
}
