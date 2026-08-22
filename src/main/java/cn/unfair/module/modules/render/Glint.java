package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.events.GlintEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemSword;
import net.minecraft.src.Config;
import net.optifine.CustomItems;
import net.optifine.shaders.Shaders;
import net.optifine.shaders.ShadersRender;

import java.awt.*;

public final class Glint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int GL_TEXTURE = 5890;
    private static final int GL_MODELVIEW = 5888;
    private final BooleanProperty glintWeapons = new BooleanProperty("Glint Weapons", true);
    private final IntProperty hueMin = new IntProperty("Hue Min", 0, 0, 360);
    private final IntProperty hueMax = new IntProperty("Hue Max", 360, 0, 360);
    private final IntProperty layers = new IntProperty("Layers", 4, 1, 8);

    public Glint() {
        super("Glint", false, true);
    }

    @EventTarget
    public void onGlint(GlintEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        Item item = event.getItemStack().getItem();
        if (this.glintWeapons.getValue() && (item instanceof ItemSword || item instanceof ItemAxe)) {
            event.setEnchanted(true);
        }

        event.setCancelled(true);
        if (event.isEnchanted()) {
            this.renderEffect(event.getModel());
        }
    }

    private void renderEffect(IBakedModel model) {
        if (!Config.isCustomItems() || CustomItems.isUseGlint()) {
            if (!Config.isShaders() || !Shaders.isShadowPass) {
                RenderItem renderItem = mc.getRenderItem();
                GlStateManager.depthMask(false);
                GlStateManager.depthFunc(514);
                GlStateManager.disableLighting();
                GlStateManager.blendFunc(768, 1);
                renderItem.getTextureManager().bindTexture(RenderItem.RES_ITEM_GLINT);

                if (Config.isShaders() && !renderItem.isRenderItemGui()) {
                    ShadersRender.renderEnchantedGlintBegin();
                }

                GlStateManager.matrixMode(GL_TEXTURE);
                GlStateManager.pushMatrix();
                GlStateManager.scale(8.0F, 8.0F, 8.0F);
                float offset = (float) (Minecraft.getSystemTime() % 3000L) / 3000.0F / 8.0F;
                GlStateManager.translate(offset, 0.0F, 0.0F);

                for (int layer = 1; layer <= this.layers.getValue(); layer++) {
                    GlStateManager.rotate(-50.0F, 0.0F, 0.0F, 1.0F);
                    float hue = (this.hueMin.getValue()
                            + (this.hueMax.getValue() - this.hueMin.getValue())
                            * (layer / (float) this.layers.getValue())) / 360.0F;
                    renderItem.renderModel(model, Color.HSBtoRGB(hue, 1.0F, 1.0F));
                }

                GlStateManager.popMatrix();
                GlStateManager.matrixMode(GL_MODELVIEW);
                GlStateManager.blendFunc(770, 771);
                GlStateManager.enableLighting();
                GlStateManager.depthFunc(515);
                GlStateManager.depthMask(true);
                renderItem.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);

                if (Config.isShaders() && !renderItem.isRenderItemGui()) {
                    ShadersRender.renderEnchantedGlintEnd();
                }
            }
        }
    }

    @Override
    public void verifyValue(String name) {
        if (this.hueMin.getName().equals(name) && this.hueMin.getValue() > this.hueMax.getValue()) {
            this.hueMax.setValue(this.hueMin.getValue());
        } else if (this.hueMax.getName().equals(name) && this.hueMax.getValue() < this.hueMin.getValue()) {
            this.hueMin.setValue(this.hueMax.getValue());
        }
    }
}
