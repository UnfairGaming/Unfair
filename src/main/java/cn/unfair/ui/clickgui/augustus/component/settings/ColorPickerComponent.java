package cn.unfair.ui.clickgui.augustus.component.settings;

import cn.unfair.property.properties.ColorProperty;
import cn.unfair.ui.clickgui.augustus.AugustusClickGui;
import cn.unfair.ui.clickgui.augustus.component.Component;
import cn.unfair.util.render.RenderUtil;
import org.lwjgl.input.Mouse;

import java.awt.*;

public class ColorPickerComponent extends Component {
    private final ColorProperty property;
    private float hue;
    private float sat;
    private float bri;
    private boolean draggingHue;
    private boolean draggingArea;

    public ColorPickerComponent(AugustusClickGui gui, ColorProperty property) {
        super(gui);
        this.property = property;
        int rgb = property.getValue();
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.bri = hsb[2];
    }

    @Override
    public float getHeight() {
        return fh() + 2.0F + 55.0F + 6.0F + 6.0F + fh() + 4.0F;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY) {
        gui.getNormalFont().drawString(property.getDisplayName() + ": ", x, y, new Color(200, 200, 200).getRGB(), false);

        float pickerX = x;
        float pickerY = y + fh() + 2.0F;
        float pickerW = 120.0F;
        float pickerH = 55.0F;

        RenderUtil.drawRect(pickerX, pickerY, pickerX + pickerW, pickerY + pickerH, Color.getHSBColor(hue, 1.0F, 1.0F).getRGB());

        for (int ix = 0; ix < pickerW; ix++) {
            float satValue = ix / pickerW;
            int alpha = (int) (255 * (1 - satValue));
            RenderUtil.drawRect(pickerX + ix, pickerY, pickerX + ix + 1, pickerY + pickerH, new Color(255, 255, 255, alpha).getRGB());
        }

        for (int iy = 0; iy < pickerH; iy++) {
            float briValue = 1 - (iy / pickerH);
            int alpha = (int) (255 * (1 - briValue));
            RenderUtil.drawRect(pickerX, pickerY + iy, pickerX + pickerW, pickerY + iy + 1, new Color(0, 0, 0, alpha).getRGB());
        }

        float hueY = pickerY + pickerH + 6.0F;
        float hueH = 6.0F;
        for (int ix = 0; ix < pickerW; ix++) {
            float h = ix / pickerW;
            RenderUtil.drawRect(pickerX + ix, hueY, pickerX + ix + 1, hueY + hueH, Color.getHSBColor(h, 1.0F, 1.0F).getRGB());
        }

        float hx = pickerX + hue * pickerW;
        RenderUtil.drawRect(hx - 1.0F, hueY - 1.0F, hx + 1.0F, hueY + hueH + 1.0F, Color.WHITE.getRGB());

        float cx = pickerX + sat * pickerW;
        float cy = pickerY + (1 - bri) * pickerH;
        RenderUtil.drawRect(cx - 2.0F, cy - 2.0F, cx + 2.0F, cy + 2.0F, Color.WHITE.getRGB());

        float previewX = pickerX + pickerW + 10.0F;
        RenderUtil.drawRect(previewX, pickerY, previewX + 18.0F, pickerY + 18.0F, (0xFF000000 | property.getValue()));

        property.setValue(Color.HSBtoRGB(hue, sat, bri) & 0xFFFFFF);

        boolean inHue = isHovered(mouseX, mouseY, pickerX, hueY, pickerW, hueH);
        boolean inArea = isHovered(mouseX, mouseY, pickerX, pickerY, pickerW, pickerH);

        if (!Mouse.isButtonDown(0)) {
            draggingHue = false;
            draggingArea = false;
        } else {
            if (inHue) {
                draggingHue = true;
                draggingArea = false;
            } else if (inArea) {
                draggingArea = true;
                draggingHue = false;
            }
        }

        if (draggingHue) {
            hue = clamp((mouseX - pickerX) / pickerW, 0.0F, 1.0F);
        } else if (draggingArea) {
            sat = clamp((mouseX - pickerX) / pickerW, 0.0F, 1.0F);
            bri = clamp(1.0F - ((mouseY - pickerY) / pickerH), 0.0F, 1.0F);
        }
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        draggingHue = false;
        draggingArea = false;
    }

    @Override
    public boolean isVisible() {
        return property.isVisible();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
