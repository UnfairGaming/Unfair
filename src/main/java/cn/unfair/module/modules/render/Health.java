package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.events.Render2DEvent;
import cn.unfair.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class Health extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public Health() {
        super("Health", false, true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled()) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        ScaledResolution res = new ScaledResolution(mc);
        int width = res.getScaledWidth() / 2;
        int height = res.getScaledHeight() / 2;

        int guiOffset = (mc.currentScreen instanceof GuiInventory) ? 80
                : (mc.thePlayer.openContainer != mc.thePlayer.inventoryContainer ? 100 : 0);

        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        if (lighting) GL11.glDisable(GL11.GL_LIGHTING);

        float health = mc.thePlayer.getHealth();
        float absorption = mc.thePlayer.getAbsorptionAmount();
        String healthText = String.valueOf(roundToPlace(health / 2F, 1)).replace(".0", "") + "§c❤";
        if (absorption > 0) {
            healthText += " §e" + roundToPlace(absorption / 2F, 1) + "§6❤";
        }
        Color color = getHealthColor(health, mc.thePlayer.getMaxHealth());
        mc.fontRendererObj.drawStringWithShadow(healthText,
                -mc.fontRendererObj.getStringWidth(healthText) / 2F + width,
                height + 25 + guiOffset,
                color.getRGB());

        mc.getTextureManager().bindTexture(GuiIngame.icons);
        int lastHealth = mc.ingameGUI.lastPlayerHealth;
        int currentHealth = MathHelper.ceiling_float_int(health);
        IAttributeInstance maxHealthAttr = mc.thePlayer.getEntityAttribute(SharedMonsterAttributes.maxHealth);
        float maxHealth = (float) maxHealthAttr.getAttributeValue();
        float absorptionLeft = absorption;

        int xStart = width - 40;
        int yStart = height + 15 + guiOffset;
        int healthRows = MathHelper.ceiling_float_int((maxHealth + absorption) / 2.0F / 10.0F);
        int rowHeight = Math.max(10 - (healthRows - 2), 3);
        int regenFlash = -1;
        if (mc.thePlayer.isPotionActive(Potion.regeneration)) {
            regenFlash = mc.ingameGUI.updateCounter % MathHelper.ceiling_float_int(maxHealth + 5.0F);
        }

        for (int i = MathHelper.ceiling_float_int((maxHealth + absorption) / 2.0F) - 1; i >= 0; --i) {
            int textureIndex = 16;
            if (mc.thePlayer.isPotionActive(Potion.poison)) {
                textureIndex += 36;
            } else if (mc.thePlayer.isPotionActive(Potion.wither)) {
                textureIndex += 72;
            }

            int blink = 0;
            boolean flash = mc.ingameGUI.healthUpdateCounter > (long) mc.ingameGUI.updateCounter
                    && (mc.ingameGUI.healthUpdateCounter - (long) mc.ingameGUI.updateCounter) / 3L % 2L == 1L;
            if (flash) blink = 1;

            int row = MathHelper.ceiling_float_int((float) (i + 1) / 10.0F) - 1;
            int x = xStart + i % 10 * 8;
            int y = yStart - row * rowHeight;
            if (i == regenFlash) y -= 2;

            int hardcoreOffset = mc.thePlayer.worldObj.getWorldInfo().isHardcoreModeEnabled() ? 5 : 0;

            mc.ingameGUI.drawTexturedModalRect(x, y, 16 + blink * 9, 9 * hardcoreOffset, 9, 9);

            if (flash) {
                if (i * 2 + 1 < lastHealth) {
                    mc.ingameGUI.drawTexturedModalRect(x, y, textureIndex + 54, 9 * hardcoreOffset, 9, 9);
                }
                if (i * 2 + 1 == lastHealth) {
                    mc.ingameGUI.drawTexturedModalRect(x, y, textureIndex + 63, 9 * hardcoreOffset, 9, 9);
                }
            }

            if (absorptionLeft <= 0.0F) {
                if (i * 2 + 1 < currentHealth) {
                    mc.ingameGUI.drawTexturedModalRect(x, y, textureIndex + 36, 9 * hardcoreOffset, 9, 9);
                }
                if (i * 2 + 1 == currentHealth) {
                    mc.ingameGUI.drawTexturedModalRect(x, y, textureIndex + 45, 9 * hardcoreOffset, 9, 9);
                }
            } else {
                if (absorptionLeft == absorption && absorption % 2.0F == 1.0F) {
                    mc.ingameGUI.drawTexturedModalRect(x, y, textureIndex + 153, 9 * hardcoreOffset, 9, 9);
                } else {
                    mc.ingameGUI.drawTexturedModalRect(x, y, textureIndex + 144, 9 * hardcoreOffset, 9, 9);
                }
                absorptionLeft -= 2.0F;
            }
        }

        if (lighting) GL11.glEnable(GL11.GL_LIGHTING);
    }

    private double roundToPlace(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    private Color getHealthColor(float health, float maxHealth) {
        float progress = Math.min(Math.max(health / maxHealth, 0), 1);
        float r, g;
        if (progress < 0.5f) {
            r = 1.0f;
            g = progress * 2.0f;
        } else {
            r = 1.0f - (progress - 0.5f) * 2.0f;
            g = 1.0f;
        }
        return new Color(r, g, 0.0f).brighter();
    }
}