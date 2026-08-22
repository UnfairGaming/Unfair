package cn.unfair.ui.widget;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.ChatGUIEvent;
import cn.unfair.events.RenderBloomEvent;
import cn.unfair.events.RenderBlurEvent;
import cn.unfair.events.Render2DEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;

import java.util.ArrayList;
import java.util.List;

public class WidgetManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final List<Widget> widgets = new ArrayList<>();
    private final List<Widget> blurMaskWidgets = new ArrayList<>();
    private final List<Widget> bloomMaskWidgets = new ArrayList<>();

    public void register(Widget widget) {
        this.widgets.add(widget);
    }

    public Widget get(String name) {
        for (Widget widget : this.widgets) {
            if (widget.name.equalsIgnoreCase(name)) {
                return widget;
            }
        }
        return null;
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.gameSettings.showDebugInfo || mc.currentScreen instanceof GuiChat) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        for (Widget widget : this.widgets) {
            if (!widget.shouldRender()) {
                continue;
            }
            widget.updatePos(sr);
            widget.render(event.partialTicks());
        }
    }

    @EventTarget
    public void onChatGUI(ChatGUIEvent event) {
        if (mc.gameSettings.showDebugInfo) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);

        Widget draggingWidget = null;
        for (Widget widget : this.widgets) {
            if (widget.shouldRender() && widget.dragging) {
                draggingWidget = widget;
                break;
            }
        }

        for (Widget widget : this.widgets) {
            if (!widget.shouldRender()) {
                continue;
            }
            widget.updatePos(sr);
            widget.render(event.partialTicks());
            widget.updatePos(sr);
            widget.onChatGUI(sr, event.mouseX(), event.mouseY(), draggingWidget == null || draggingWidget == widget);
            if (widget.dragging) {
                draggingWidget = widget;
            }
            widget.updatePos(sr);
        }
    }

    @EventTarget
    public void onPostProcessBlur(RenderBlurEvent event) {
        if (event.getType() == EventType.PRE) {
            this.blurMaskWidgets.clear();
        }
        if (mc.gameSettings.showDebugInfo) {
            return;
        }
        if (event.getType() == EventType.PRE) {
            for (Widget widget : this.widgets) {
                if (widget.shouldRenderBlurMask()) {
                    this.blurMaskWidgets.add(widget);
                }
            }
            if (!this.blurMaskWidgets.isEmpty()) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getType() != EventType.POST) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        for (Widget widget : this.blurMaskWidgets) {
            widget.updatePos(sr);
            widget.renderBlurMask(event.getPartialTicks());
        }
    }

    @EventTarget
    public void onPostProcessBloom(RenderBloomEvent event) {
        if (event.getType() == EventType.PRE) {
            this.bloomMaskWidgets.clear();
        }
        if (mc.gameSettings.showDebugInfo) {
            return;
        }
        if (event.getType() == EventType.PRE) {
            for (Widget widget : this.widgets) {
                if (widget.shouldRenderBloomMask()) {
                    this.bloomMaskWidgets.add(widget);
                }
            }
            if (!this.bloomMaskWidgets.isEmpty()) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getType() != EventType.POST) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        for (Widget widget : this.bloomMaskWidgets) {
            widget.updatePos(sr);
            widget.renderBloomMask(event.getPartialTicks());
        }
    }
}
