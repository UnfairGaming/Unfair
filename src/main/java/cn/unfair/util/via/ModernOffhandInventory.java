package cn.unfair.util.via;

import net.minecraft.item.ItemStack;

/** Client-side bridge for the modern inventory slot 45. */
public interface ModernOffhandInventory {

    ItemStack viaforge$getOffhand();

    void viaforge$setOffhand(ItemStack stack);
}
