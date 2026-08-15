package cn.unfair.module.modules.render.targethud.impl;

import cn.unfair.module.modules.render.TargetHUD;
import cn.unfair.module.modules.render.targethud.TargetHUDMode;
import cn.unfair.util.ColorUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.font.Fonts;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExhibitionTargetHUD extends TargetHUDMode {
    public ExhibitionTargetHUD() {
        super("Exhibition");
    }

    @Override
    public void render(TargetHUD targetHUD, TargetHUD.RenderData data, float x, float y) {
        float width = this.getSize(targetHUD, data)[0];
        float ratio = MathHelper.clamp_float(data.targetHealth() / Math.max(data.maxHealth(), 1.0F), 0.0F, 1.0F);
        Color healthColor = ColorUtil.getHealthBlend(ratio).brighter();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x - 1.0F, y + 5.0F, 0.0F);
        RenderUtil.drawSkeetRect(0.0F, -2.0F, width, 42.0F);
        Fonts.exhi.get(18.0F).drawStringWithShadow(data.entity().getName(), 42.3F, 0.3F, -1);

        RenderUtil.drawRect(42.5F, 10.3F, 103.0F, 13.5F, healthColor.darker().darker().getRGB());
        RenderUtil.drawRect(42.5F, 10.3F, 42.5F + 60.5F * ratio, 13.5F, healthColor.getRGB());
        if (data.absorption() > 0.0F) {
            RenderUtil.drawRect(97.5F - data.absorption(), 10.3F, 103.5F, 13.5F, new Color(137, 112, 9).getRGB());
        }
        RenderUtil.drawRect(42.0F, 9.8F, 104.0F, 10.3F, Color.BLACK.getRGB());
        RenderUtil.drawRect(42.0F, 13.5F, 104.0F, 14.0F, Color.BLACK.getRGB());
        for (int i = 1; i < 10; ++i) {
            float lineX = 43.5F + 60.0F / 8.5F * i;
            RenderUtil.drawRect(lineX, 9.8F, lineX + 0.5F, 14.0F, Color.BLACK.getRGB());
        }

        GlStateManager.scale(0.5F, 0.5F, 0.5F);
        int distance = (int) TargetHUD.mc.thePlayer.getDistanceToEntity(data.entity());
        TargetHUD.mc.fontRendererObj.drawString("HP: " + (int) (data.targetHealth() + data.absorption()) + " | Dist: " + distance, 85.3F, 32.3F, -1, true);
        GlStateManager.scale(2.0F, 2.0F, 2.0F);

        if (data.entity() instanceof EntityPlayer) {
            this.renderItems((EntityPlayer) data.entity());
        }
        if (data.entity() instanceof EntityPlayer) {
            GlStateManager.pushMatrix();
            GuiInventory.drawEntityOnScreen(22, 35, 15, 0.0F, 0.0F, data.entity());
            GlStateManager.popMatrix();
        } else {
            RenderUtil.renderPlayerHead(data.entity(), 4.0F, 3.0F, 34.0F);
        }
        GlStateManager.popMatrix();
    }

    @Override
    public float[] getSize(TargetHUD targetHUD, TargetHUD.RenderData data) {
        if (data == null) {
            return new float[]{124.0F, 47.0F};
        }
        float nameWidth = Fonts.exhi.get(18.0F).getStringWidth(data.entity().getName());
        return new float[]{nameWidth > 70.0F ? 124.0F + nameWidth - 70.0F : 124.0F, 47.0F};
    }

    private void renderItems(EntityPlayer target) {
        GL11.glPushMatrix();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 3; i >= 0; --i) {
            ItemStack armor = target.getCurrentArmor(i);
            if (armor != null) {
                items.add(armor);
            }
        }
        if (target.getHeldItem() != null) {
            items.add(target.getHeldItem());
        }
        int itemX = 26;
        for (ItemStack item : items) {
            RenderHelper.enableGUIStandardItemLighting();
            RenderUtil.renderItemInGUI(item, itemX += 16, 20);
        }
        GL11.glPopMatrix();
    }

}
