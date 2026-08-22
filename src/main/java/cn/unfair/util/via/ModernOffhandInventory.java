package cn.unfair.util.via;

import net.minecraft.item.ItemStack;

/**
 * Client-side bridge for the modern inventory slot 45.
 */
public interface ModernOffhandInventory {

    ItemStack getOffhand();

    void setOffhand(ItemStack stack);
}
