package net.minecraft.client.renderer.tileentity;

import cn.unfair.util.via.ViaBackwardsItemModels;
import com.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnderChest;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

public class TileEntityItemStackRenderer
{
    private static final ResourceLocation SHIELD_BASE_TEXTURE = ResourceLocation.of("textures/entity/shield_base_nopattern.png");
    public static TileEntityItemStackRenderer instance = new TileEntityItemStackRenderer();
    private TileEntityChest field_147717_b = new TileEntityChest(0);
    private TileEntityChest field_147718_c = new TileEntityChest(1);
    private TileEntityEnderChest enderChest = new TileEntityEnderChest();
    private TileEntityBanner banner = new TileEntityBanner();
    private TileEntitySkull skull = new TileEntitySkull();
    private final ModelShield shield = new ModelShield();

    public void renderByItem(ItemStack itemStackIn)
    {
        String viaModelName = ViaBackwardsItemModels.getModelName(itemStackIn);

        if ("shield".equals(viaModelName) || "shield_blocking".equals(viaModelName))
        {
            this.renderShield();
        }
        else if (itemStackIn.getItem() == Items.banner)
        {
            this.banner.setItemValues(itemStackIn);
            TileEntityRendererDispatcher.instance.renderTileEntityAt(this.banner, 0.0D, 0.0D, 0.0D, 0.0F);
        }
        else if (itemStackIn.getItem() == Items.skull)
        {
            GameProfile gameprofile = null;

            if (itemStackIn.hasTagCompound())
            {
                NBTTagCompound nbttagcompound = itemStackIn.getTagCompound();

                if (nbttagcompound.hasKey("SkullOwner", 10))
                {
                    gameprofile = NBTUtil.readGameProfileFromNBT(nbttagcompound.getCompoundTag("SkullOwner"));
                }
                else if (nbttagcompound.hasKey("SkullOwner", 8) && !nbttagcompound.getString("SkullOwner").isEmpty())
                {
                    gameprofile = new GameProfile(null, nbttagcompound.getString("SkullOwner"));
                    gameprofile = TileEntitySkull.updateGameprofile(gameprofile);
                    nbttagcompound.removeTag("SkullOwner");
                    nbttagcompound.setTag("SkullOwner", NBTUtil.writeGameProfile(new NBTTagCompound(), gameprofile));
                }
            }

            if (TileEntitySkullRenderer.instance != null)
            {
                GlStateManager.pushMatrix();
                GlStateManager.translate(-0.5F, 0.0F, -0.5F);
                GlStateManager.scale(2.0F, 2.0F, 2.0F);
                GlStateManager.disableCull();
                TileEntitySkullRenderer.instance.renderSkull(0.0F, 0.0F, 0.0F, EnumFacing.UP, 0.0F, itemStackIn.getMetadata(), gameprofile, -1);
                GlStateManager.enableCull();
                GlStateManager.popMatrix();
            }
        }
        else
        {
            Block block = Block.getBlockFromItem(itemStackIn.getItem());

            if (block == Blocks.ender_chest)
            {
                TileEntityRendererDispatcher.instance.renderTileEntityAt(this.enderChest, 0.0D, 0.0D, 0.0D, 0.0F);
            }
            else if (block == Blocks.trapped_chest)
            {
                TileEntityRendererDispatcher.instance.renderTileEntityAt(this.field_147718_c, 0.0D, 0.0D, 0.0D, 0.0F);
            }
            else
            {
                TileEntityRendererDispatcher.instance.renderTileEntityAt(this.field_147717_b, 0.0D, 0.0D, 0.0D, 0.0F);
            }
        }
    }

    private void renderShield()
    {
        Minecraft.getMinecraft().getTextureManager().bindTexture(SHIELD_BASE_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.disableCull();
        GlStateManager.scale(1.0F, -1.0F, -1.0F);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.shield.render();
        GlStateManager.enableCull();
        GlStateManager.popMatrix();
    }

    private static class ModelShield extends ModelBase
    {
        private final ModelRenderer plate;
        private final ModelRenderer handle;

        private ModelShield()
        {
            this.textureWidth = 64;
            this.textureHeight = 64;
            this.plate = new ModelRenderer(this, 0, 0);
            this.plate.setTextureSize(64, 64);
            this.plate.addBox(-6.0F, -11.0F, -2.0F, 12, 22, 1);
            this.handle = new ModelRenderer(this, 26, 0);
            this.handle.setTextureSize(64, 64);
            this.handle.addBox(-1.0F, -3.0F, -1.0F, 2, 6, 6);
        }

        private void render()
        {
            this.plate.render(0.0625F);
            this.handle.render(0.0625F);
        }
    }
}
