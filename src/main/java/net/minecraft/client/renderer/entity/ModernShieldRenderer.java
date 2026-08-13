package net.minecraft.client.renderer.entity;

import cn.unfair.util.via.ViaBackwardsItemModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

/**
 * Applies the hand-specific transforms that the 1.8 item model format cannot
 * represent. GUI, ground and fixed transforms remain owned by the model JSON.
 */
public final class ModernShieldRenderer {
    private static final float MODEL_UNIT = 0.0625F;

    private ModernShieldRenderer() {
    }

    public static void renderFirstPerson(EntityPlayer player, ItemStack shield, boolean leftHand,
                                         boolean blocking, float equipProgress, float swingProgress) {
        int side = leftHand ? -1 : 1;

        if (!blocking) {
            float swingRoot = MathHelper.sqrt_float(swingProgress);
            float x = -0.4F * MathHelper.sin(swingRoot * (float) Math.PI);
            float y = 0.2F * MathHelper.sin(swingRoot * ((float) Math.PI * 2.0F));
            float z = -0.2F * MathHelper.sin(swingProgress * (float) Math.PI);
            GlStateManager.translate(side * x, y, z);
        }

        GlStateManager.translate(side * 0.56F, -0.52F - equipProgress * 0.6F, -0.72F);

        if (!blocking) {
            float swingSquared = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
            float swingRoot = MathHelper.sin(MathHelper.sqrt_float(swingProgress) * (float) Math.PI);
            GlStateManager.rotate(side * (45.0F - swingSquared * 20.0F), 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(side * swingRoot * -20.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(swingRoot * -80.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(side * -45.0F, 0.0F, 1.0F, 0.0F);
        }

        if (blocking) {
            if (leftHand) {
                applyTransform(0.0F, -180.0F, 5.0F, -5.0F, 5.0F, -11.0F, 1.25F);
            } else {
                applyTransform(0.0F, 180.0F, -5.0F, -15.0F, 5.0F, -11.0F, 1.25F);
            }
        } else if (leftHand) {
            applyTransform(0.0F, -180.0F, -5.0F, -10.0F, 0.0F, -10.0F, 1.25F);
        } else {
            applyTransform(0.0F, 180.0F, 5.0F, -10.0F, 2.0F, -10.0F, 1.25F);
        }

        renderModel(player, shield);
    }

    public static void renderThirdPerson(EntityLivingBase entity, ItemStack shield, boolean leftHand) {
        boolean blocking = isActivelyBlocking(entity, shield);

        if (blocking) {
            if (leftHand) {
                applyTransform(45.0F, -155.0F, 0.0F, -11.51F, 7.0F, 2.5F, 1.0F);
            } else {
                applyTransform(45.0F, 155.0F, 0.0F, -3.49F, 11.0F, -2.0F, 1.0F);
            }
        } else if (leftHand) {
            applyTransform(0.0F, -90.0F, 0.0F, -10.0F, 6.0F, 12.0F, 1.0F);
        } else {
            applyTransform(0.0F, 90.0F, 0.0F, 10.0F, 6.0F, -4.0F, 1.0F);
        }

        renderModel(entity, shield);
    }

    public static boolean isActivelyBlocking(EntityLivingBase entity, ItemStack shield) {
        if (!(entity instanceof EntityPlayer)) {
            return false;
        }

        EntityPlayer player = (EntityPlayer) entity;
        ItemStack active = player.getItemInUse();
        return player.getItemInUseCount() > 0
                && active != null
                && "shield".equals(ViaBackwardsItemModels.getModelName(active))
                && (active == shield || ItemStack.areItemStacksEqual(active, shield));
    }

    private static void applyTransform(float rotationX, float rotationY, float rotationZ,
                                       float translationX, float translationY, float translationZ,
                                       float scale) {
        GlStateManager.translate(translationX * MODEL_UNIT, translationY * MODEL_UNIT, translationZ * MODEL_UNIT);
        // 1.21.11 Transformation uses Quaternionf.rotationXYZ.
        GlStateManager.rotate(rotationX, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(rotationY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate(rotationZ, 0.0F, 0.0F, 1.0F);
        GlStateManager.scale(scale, scale, scale);
    }

    private static void renderModel(EntityLivingBase entity, ItemStack shield) {
        // Use the modern special-model coordinate path. The legacy generic
        // item wrapper has an extra half-scale and 180 degree Y rotation.
        Minecraft.getMinecraft().getRenderItem().renderBuiltinItemDirect(shield);
    }
}
