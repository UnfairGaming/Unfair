package cn.unfair.ui.clickgui.augustus.component.settings;

import cn.unfair.property.Property;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.ui.clickgui.augustus.component.Component;
import cn.unfair.util.render.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;

import java.awt.*;
import java.util.Locale;

public class SliderComponent extends Component {
    private static final double FLOAT_SLIDER_STEP = 0.01D;
    private static final float TRACK_WIDTH = 100.0F;
    private final Property<?> property;
    private boolean dragging;

    public SliderComponent(AugustusClickGui gui, Property<?> property) {
        super(gui);
        this.property = property;
    }

    @Override
    public float getHeight() {
        return fh() + 4.0F;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }

    private float getMin() {
        if (property instanceof FloatProperty fp) return fp.getMinimum() == null ? 0.0F : fp.getMinimum();
        if (property instanceof IntProperty ip) return ip.getMinimum();
        if (property instanceof PercentProperty pp) return pp.getMinimum();
        return 0.0F;
    }

    private float getMax() {
        if (property instanceof FloatProperty fp) return fp.getMaximum() == null ? 1.0F : fp.getMaximum();
        if (property instanceof IntProperty ip) return ip.getMaximum();
        if (property instanceof PercentProperty pp) return pp.getMaximum();
        return 1.0F;
    }

    private float getValue() {
        if (property instanceof FloatProperty fp) return fp.getValue();
        if (property instanceof IntProperty ip) return ip.getValue();
        if (property instanceof PercentProperty pp) return pp.getValue();
        return 0.0F;
    }

    private String formatValue() {
        if (property instanceof FloatProperty fp) return String.format(Locale.ROOT, "%.2f", fp.getValue());
        if (property instanceof IntProperty ip) return String.valueOf(ip.getValue());
        if (property instanceof PercentProperty pp) return pp.getValue() + "%";
        return "";
    }

    private void setValueFromPercent(float pct) {
        if (property instanceof FloatProperty fp) {
            float min = fp.getMinimum() == null ? 0.0F : fp.getMinimum();
            float max = fp.getMaximum() == null ? 1.0F : fp.getMaximum();
            fp.setValue(getFloatSliderValue(min, max, pct));
        } else if (property instanceof IntProperty ip) {
            int min = ip.getMinimum();
            int max = ip.getMaximum();
            ip.setValue(pct <= 0.0F ? min : pct >= 1.0F ? max : Math.round(min + (max - min) * pct));
        } else if (property instanceof PercentProperty pp) {
            int min = pp.getMinimum();
            int max = pp.getMaximum();
            pp.setValue(pct <= 0.0F ? min : pct >= 1.0F ? max : Math.round(min + (max - min) * pct));
        }
    }

    private float getFloatSliderValue(float min, float max, float percent) {
        if (percent <= 0.0F) {
            return min;
        }
        if (percent >= 1.0F) {
            return max;
        }
        long totalSteps = Math.max(1L, Math.round(((double) max - min) / FLOAT_SLIDER_STEP));
        long selectedStep = Math.round((double) percent * totalSteps);
        double value = min + selectedStep * FLOAT_SLIDER_STEP;
        return MathHelper.clamp_float((float) value, min, max);
    }

    private float getTrackX() {
        return x + fw(property.getDisplayName() + ": ") + 2.0F;
    }

    private float getBoxHeight() {
        return Math.max(11.0F, fh() + 2.0F);
    }

    private float getBoxY() {
        return y + (fh() - getBoxHeight()) / 2.0F;
    }

    private float getValueY(float boxY) {
        return (float) Math.floor(boxY + (getBoxHeight() - fh()) / 2.0F);
    }

    private float getPreciseMouseX() {
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution sr = new ScaledResolution(minecraft);
        return (float) Mouse.getX() * (float) sr.getScaledWidth() / (float) minecraft.displayWidth;
    }

    private float getPercent(float mouseX, float trackX, float trackW) {
        if (mouseX <= trackX) {
            return 0.0F;
        }
        if (mouseX >= trackX + trackW) {
            return 1.0F;
        }
        return MathHelper.clamp_float((mouseX - trackX) / trackW, 0.0F, 1.0F);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        gui.getNormalFont().drawString(property.getDisplayName() + ": ", x, y, new Color(200, 200, 200).getRGB(), false);

        float trackX = getTrackX();
        float trackY = getBoxY();
        float trackH = getBoxHeight();

        if (dragging) {
            if (!Mouse.isButtonDown(0)) {
                dragging = false;
            } else {
                setValueFromPercent(getPercent(getPreciseMouseX(), trackX, TRACK_WIDTH));
            }
        }

        float min = getMin();
        float max = getMax();
        float val = getValue();
        float t = MathHelper.clamp_float((val - min) / (max - min), 0.0F, 1.0F);
        float targetLen = t * TRACK_WIDTH;

        RenderUtil.drawRoundedGradientOutlinedRectangle(trackX, trackY, trackX + TRACK_WIDTH, trackY + trackH, 2.0F, 0, new Color(34, 34, 34).getRGB(), new Color(34, 34, 34).getRGB());
        drawProgress(trackX, trackY, trackH, targetLen);
        drawValue(formatValue(), trackX, trackY);
    }

    private void drawProgress(float trackX, float trackY, float trackH, float progressWidth) {
        float left = trackX + 2.0F;
        float right = trackX + progressWidth - 2.0F;
        if (right > left) {
            RenderUtil.drawRect(left, trackY + 2.0F, right, trackY + trackH - 2.0F, gui.getAccent().getRGB());
        }
    }

    private void drawValue(String value, float trackX, float trackY) {
        float centerX = trackX + TRACK_WIDTH / 2.0F;
        gui.getNormalFont().drawString(value, centerX - gui.getNormalFont().getStringVisualCenterOffset(value), getValueY(trackY), new Color(200, 200, 200).getRGB(), false);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return;
        }
        float trackX = getTrackX();
        float trackY = getBoxY();
        if (isHovered(mouseX, mouseY, trackX, trackY, TRACK_WIDTH, getBoxHeight())) {
            dragging = true;
            setValueFromPercent(getPercent(mouseX, trackX, TRACK_WIDTH));
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        dragging = false;
    }
}
