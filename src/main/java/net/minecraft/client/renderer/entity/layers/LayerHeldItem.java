package net.minecraft.client.renderer.entity.layers;

import cn.unfair.util.via.ModernOffhandInteraction;
import cn.unfair.util.via.ViaBackwardsItemModels;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
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
            GlStateManager.translate(-0.0625F, 0.4375F, 0.0625F);

            if (entitylivingbaseIn instanceof EntityPlayer && ((EntityPlayer)entitylivingbaseIn).fishEntity != null)
            {
                itemstack = new ItemStack(Items.fishing_rod, 0);
            }

            Item item = itemstack.getItem();
            Minecraft minecraft = Minecraft.getMinecraft();

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

            if ("shield".equals(ViaBackwardsItemModels.getModelName(itemstack))) {
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
        GlStateManager.translate(0.0625F, 0.4375F, 0.0625F);

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

        if ("shield".equals(ViaBackwardsItemModels.getModelName(offhand))) {
            renderModernShield(entitylivingbaseIn, offhand, true);
        } else {
            Minecraft.getMinecraft().getItemRenderer().renderItem(entitylivingbaseIn, offhand, ItemCameraTransforms.TransformType.THIRD_PERSON);
        }
        GlStateManager.popMatrix();
    }

    private static void renderModernShield(EntityLivingBase entity, ItemStack shield, boolean leftHand) {
        ItemStack activeStack = entity instanceof EntityPlayer ? ((EntityPlayer) entity).getItemInUse() : null;
        boolean blocking = activeStack != null && "shield".equals(ViaBackwardsItemModels.getModelName(activeStack));

        GlStateManager.scale(2.0F, 2.0F, 2.0F);

        if (blocking) {
            if (leftHand) {
                applyItemTransform(45.0F, 135.0F, 0.0F, -0.75F, 3.5F, 1.5F, 1.0F);
            } else {
                applyItemTransform(45.0F, 135.0F, 0.0F, -3.0F, 6.5F, -1.5F, 1.0F);
            }
        } else {
            applyItemTransform(0.0F, 90.0F, 0.0F, leftHand ? -0.75F : -3.0F, 5.0F, leftHand ? 1.5F : -2.0F, 1.0F);
        }

        Minecraft.getMinecraft().getRenderItem().renderItemModelForEntity(shield, entity, ItemCameraTransforms.TransformType.NONE);
    }

    private static void applyItemTransform(float rotationX, float rotationY, float rotationZ,
                                           float translationX, float translationY, float translationZ, float scale) {
        GlStateManager.translate(translationX * 0.0625F, translationY * 0.0625F, translationZ * 0.0625F);
        GlStateManager.rotate(rotationY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rotationX, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(rotationZ, 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(scale, scale, scale);
    }

    public boolean shouldCombineTextures()
    {
        return false;
    }
}
