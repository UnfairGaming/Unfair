package net.minecraft.client.renderer.entity.layers;

import cn.unfair.util.via.ModernOffhandInteraction;
import cn.unfair.util.via.ViaBackwardsItemModels;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.ModernShieldRenderer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class LayerHeldItem implements LayerRenderer<EntityLivingBase>
{
    private final RendererLivingEntity<?> livingEntityRenderer;

    public LayerHeldItem(RendererLivingEntity<?> livingEntityRendererIn)
    {
        this.livingEntityRenderer = livingEntityRendererIn;
    }

    public void doRenderLayer(EntityLivingBase entitylivingbaseIn, float p_177141_2_, float p_177141_3_, float partialTicks, float p_177141_5_, float p_177141_6_, float p_177141_7_, float scale)
    {
        ItemStack itemstack = entitylivingbaseIn.getHeldItem();

        if (itemstack != null)
        {
            GlStateManager.pushMatrix();

            if (this.livingEntityRenderer.getMainModel().isChild)
            {
                float f = 0.5F;
                GlStateManager.translate(0.0F, 0.625F, 0.0F);
                GlStateManager.rotate(-20.0F, -1.0F, 0.0F, 0.0F);
                GlStateManager.scale(f, f, f);
            }

            ((ModelBiped)this.livingEntityRenderer.getMainModel()).postRenderArm(0.0625F);

            if (entitylivingbaseIn instanceof EntityPlayer && ((EntityPlayer)entitylivingbaseIn).fishEntity != null)
            {
                itemstack = new ItemStack(Items.fishing_rod, 0);
            }

            Item item = itemstack.getItem();
            Minecraft minecraft = Minecraft.getMinecraft();
            boolean shield = "shield".equals(ViaBackwardsItemModels.getModelName(itemstack));

            if (shield) {
                applyModernHandAnchor(false);
            } else {
                GlStateManager.translate(-0.0625F, 0.4375F, 0.0625F);
            }

            if (item instanceof ItemBlock && Block.getBlockFromItem(item).getRenderType() == 2)
            {
                GlStateManager.translate(0.0F, 0.1875F, -0.3125F);
                GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
                GlStateManager.rotate(45.0F, 0.0F, 1.0F, 0.0F);
                float f1 = 0.375F;
                GlStateManager.scale(-f1, -f1, f1);
            }

            if (entitylivingbaseIn.isSneaking())
            {
                GlStateManager.translate(0.0F, 0.203125F, 0.0F);
            }

            if (shield) {
                renderModernShield(entitylivingbaseIn, itemstack, false);
            } else {
                minecraft.getItemRenderer().renderItem(entitylivingbaseIn, itemstack, ItemCameraTransforms.TransformType.THIRD_PERSON);
            }
            GlStateManager.popMatrix();
        }

        if (!ModernOffhandInteraction.isModernTarget() || !(entitylivingbaseIn instanceof EntityPlayer)) {
            return;
        }

        ItemStack offhand = ((EntityPlayer) entitylivingbaseIn).inventory.viaforge$getOffhand();
        if (offhand == null || !(this.livingEntityRenderer.getMainModel() instanceof ModelBiped)) {
            return;
        }

        GlStateManager.pushMatrix();
        if (this.livingEntityRenderer.getMainModel().isChild) {
            float f = 0.5F;
            GlStateManager.translate(0.0F, 0.625F, 0.0F);
            GlStateManager.rotate(-20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.scale(f, f, f);
        }

        ((ModelBiped)this.livingEntityRenderer.getMainModel()).bipedLeftArm.postRender(0.0625F);
        boolean shield = "shield".equals(ViaBackwardsItemModels.getModelName(offhand));

        if (shield) {
            applyModernHandAnchor(true);
        } else {
            GlStateManager.translate(0.0625F, 0.4375F, 0.0625F);
        }

        if (offhand.getItem() instanceof ItemBlock && Block.getBlockFromItem(offhand.getItem()).getRenderType() == 2)
        {
            GlStateManager.translate(0.0F, 0.1875F, -0.3125F);
            GlStateManager.rotate(20.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(-45.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.scale(-0.375F, -0.375F, 0.375F);
        }

        if (entitylivingbaseIn.isSneaking())
        {
            GlStateManager.translate(0.0F, 0.203125F, 0.0F);
        }

        if (shield) {
            renderModernShield(entitylivingbaseIn, offhand, true);
        } else {
            Minecraft.getMinecraft().getItemRenderer().renderItem(entitylivingbaseIn, offhand, ItemCameraTransforms.TransformType.THIRD_PERSON);
        }
        GlStateManager.popMatrix();
    }

    private static void renderModernShield(EntityLivingBase entity, ItemStack shield, boolean leftHand) {
        ModernShieldRenderer.renderThirdPerson(entity, shield, leftHand);
    }

    private static void applyModernHandAnchor(boolean leftHand) {
        GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(leftHand ? -0.0625F : 0.0625F, 0.125F, -0.625F);
    }

    public boolean shouldCombineTextures()
    {
        return false;
    }
}
