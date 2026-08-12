package net.minecraft.client.renderer.entity.layers;

import cn.unfair.util.via.ViaBackwardsItemModels;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

public class LayerElytra implements LayerRenderer<AbstractClientPlayer> {
    private static final ResourceLocation ELYTRA_TEXTURE =
            ResourceLocation.of("textures/entity/equipment/wings/elytra.png");
    private final RenderPlayer playerRenderer;
    private final ModelElytra model = new ModelElytra();

    public LayerElytra(RenderPlayer playerRenderer) {
        this.playerRenderer = playerRenderer;
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        ItemStack chest = player.getCurrentArmor(2);
        if (chest == null || !"elytra".equals(ViaBackwardsItemModels.getModelName(chest))
                || player.isInvisible()) {
            return;
        }

        GlStateManager.pushMatrix();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        playerRenderer.bindTexture(getTexture(player));
        GlStateManager.translate(0.0F, 0.0F, 0.125F);
        this.model.setAngles(player, partialTicks);
        this.model.render(scale);
        GlStateManager.popMatrix();
    }

    private ResourceLocation getTexture(AbstractClientPlayer player) {
        return player.hasElytraCape() && player.getLocationCape() != null
                ? player.getLocationCape()
                : ELYTRA_TEXTURE;
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    private static class ModelElytra extends ModelBase {
        private final ModelRenderer leftWing;
        private final ModelRenderer rightWing;

        private ModelElytra() {
            this.textureWidth = 64;
            this.textureHeight = 32;
            this.leftWing = new ModelRenderer(this, 22, 0);
            this.leftWing.addBox(-10.0F, 0.0F, 0.0F, 10, 20, 2, 1.0F);
            this.leftWing.setRotationPoint(5.0F, 0.0F, 0.0F);
            this.leftWing.rotateAngleX = 0.2617994F;
            this.leftWing.rotateAngleZ = -0.2617994F;

            this.rightWing = new ModelRenderer(this, 22, 0);
            this.rightWing.mirror = true;
            this.rightWing.addBox(0.0F, 0.0F, 0.0F, 10, 20, 2, 1.0F);
            this.rightWing.setRotationPoint(-5.0F, 0.0F, 0.0F);
            this.rightWing.rotateAngleX = 0.2617994F;
            this.rightWing.rotateAngleZ = 0.2617994F;
        }

        private void setAngles(AbstractClientPlayer player, float partialTicks) {
            float leftPitch = 0.2617994F;
            float leftYaw = 0.0F;
            float leftRoll = -0.2617994F;

            double horizontalSpeed = player.motionX * player.motionX + player.motionZ * player.motionZ;
            boolean flying = !player.onGround && horizontalSpeed > 0.01D;
            if (flying) {
                float flight = MathHelper.clamp_float((float) (-player.motionY * 20.0D), -1.0F, 1.0F);
                leftPitch = -0.7853982F + flight * 0.35F;
                leftYaw = -0.15F;
                leftRoll = -0.35F;
            } else if (player.isSneaking()) {
                leftPitch += 0.35F;
            }

            this.leftWing.rotationPointY = player.isSneaking() ? 3.0F : 0.0F;
            this.rightWing.rotationPointY = this.leftWing.rotationPointY;
            this.leftWing.rotateAngleX = leftPitch;
            this.leftWing.rotateAngleY = leftYaw;
            this.leftWing.rotateAngleZ = leftRoll;
            this.rightWing.rotateAngleX = leftPitch;
            this.rightWing.rotateAngleY = -leftYaw;
            this.rightWing.rotateAngleZ = -leftRoll;
        }

        private void render(float scale) {
            this.leftWing.render(scale);
            this.rightWing.render(scale);
        }
    }
}
