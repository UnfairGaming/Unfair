package cn.unfair.ui.widget;

import cn.unfair.event.EventTarget;
import cn.unfair.events.ChatGUIEvent;
import cn.unfair.events.Render2DEvent;
import cn.unfair.util.shader.ShaderElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;

import java.util.ArrayList;
import java.util.List;

public class WidgetManager {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final List<Widget> widgets = new ArrayList<>();

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
            if (widget.shouldRenderBlurMask()) {
                ShaderElement.addBlurTask(() -> {
                    widget.updatePos(sr);
                    widget.renderBlurMask(event.partialTicks());
                });
            }
            if (widget.shouldRenderBloomMask()) {
                ShaderElement.addBloomTask(() -> {
                    widget.updatePos(sr);
                    widget.renderBloomMask(event.partialTicks());
                });
            }
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
            if (widget.shouldRenderBlurMask()) {
                ShaderElement.addBlurTask(() -> {
                    widget.updatePos(sr);
                    widget.renderBlurMask(event.partialTicks());
                });
            }
            if (widget.shouldRenderBloomMask()) {
                ShaderElement.addBloomTask(() -> {
                    widget.updatePos(sr);
                    widget.renderBloomMask(event.partialTicks());
                });
            }
        }
    }
}
