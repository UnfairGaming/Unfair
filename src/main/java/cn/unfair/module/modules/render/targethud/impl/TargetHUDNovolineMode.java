package cn.unfair.module.modules.render.targethud.impl;

import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.module.modules.render.targethud.TargetHUDMode;
import cn.unfair.util.RenderUtil;
import net.minecraft.util.MathHelper;

import java.awt.Color;
import java.text.DecimalFormat;

public class TargetHUDNovolineMode extends TargetHUDMode {
    public TargetHUDNovolineMode() {
        super("Novoline");
    }

    @Override
    public void render(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y) {
        float[] size = this.getSize(targetHUD, data);
        float width = size[0];
        float height = size[1];
        float ratio = MathHelper.clamp_float(data.targetHealth / Math.max(data.maxHealth, 1.0F), 0.0F, 1.0F);
        float space = width - height - 4.5F;
        int accent = targetHUD.getRavenGradientColors()[0];

        RenderUtil.drawRect(x - 1.0F, y - 1.0F, x + width + 1.0F, y + height + 1.0F, new Color(29, 29, 29, 255).getRGB());
        RenderUtil.drawRect(x, y, x + width, y + height, new Color(40, 40, 40, 255).getRGB());
        RenderUtil.renderPlayerHead(data.entity, x + 0.5F, y + 0.5F, height - 1.0F);
        RenderUtil.drawRect(x + 2.0F + height, y + height - 19.5F, x + 2.0F + height + space, y + height - 8.7F, new Color(0, 0, 0, 50).getRGB());
        RenderUtil.drawRect(x + 2.0F + height, y + height - 19.5F, x + 2.0F + height + space * ratio, y + height - 8.7F, accent);

        String text = new DecimalFormat("0.0").format(ratio * 100.0F).replace(",", ".") + "%";
        TargetHUD.mc.fontRendererObj.drawStringWithShadow(text, x + 39.0F + space / 2.0F - TargetHUD.mc.fontRendererObj.getStringWidth(text) / 2.0F, y + 19.0F, -1);
        TargetHUD.mc.fontRendererObj.drawStringWithShadow(data.entity.getName(), x + 40.0F, y + 4.0F, -1);
    }

    @Override
    public float[] getSize(TargetHUD targetHUD, TargetHUD.RenderData data) {
        if (data == null) {
            return new float[]{100.0F, 37.0F};
        }
        return new float[]{28.0F + TargetHUD.mc.fontRendererObj.getStringWidth(data.entity.getName()) + 40.0F, 37.0F};
    }

}
