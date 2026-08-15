package cn.unfair.util;

import cn.unfair.util.via.ViaBackwardsItemModels;
import com.google.common.collect.Multimap;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Objects;

public class ItemUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final ArrayList<Integer> specialItems = new SpecialItems();

    private static String getViaModelName(ItemStack itemStack) {
        return itemStack == null ? null : ViaBackwardsItemModels.getModelName(itemStack);
    }

    private static boolean isToolModel(String modelName, String toolClass) {
        return modelName != null && modelName.endsWith("_" + toolClass);
    }

    public static boolean isSword(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        String modelName = getViaModelName(itemStack);
        return modelName != null ? modelName.endsWith("_sword") : itemStack.getItem() instanceof ItemSword;
    }

    public static boolean isTool(ItemStack itemStack, String toolClass) {
        if (itemStack == null) {
            return false;
        }
        String modelName = getViaModelName(itemStack);
        return modelName != null
                ? isToolModel(modelName, toolClass)
                : itemStack.getItem() instanceof ItemTool
                && itemStack.getItem().getToolClasses(itemStack).contains(toolClass);
    }

    public static boolean isTool(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        String modelName = getViaModelName(itemStack);
        return modelName != null
                ? isToolModel(modelName, "pickaxe")
                || isToolModel(modelName, "shovel")
                || isToolModel(modelName, "axe")
                : itemStack.getItem() instanceof ItemTool;
    }

    public static int getArmorType(ItemStack itemStack) {
        if (itemStack == null) {
            return -1;
        }
        String modelName = getViaModelName(itemStack);
        if (modelName == null) {
            return itemStack.getItem() instanceof ItemArmor ? ((ItemArmor) itemStack.getItem()).armorType : -1;
        }
        if (modelName.endsWith("_helmet")) return 0;
        if (modelName.endsWith("_chestplate")) return 1;
        if (modelName.endsWith("_leggings")) return 2;
        if (modelName.endsWith("_boots")) return 3;
        return -1;
    }

    public static boolean isEnderPearl(ItemStack itemStack) {
        return itemStack != null && getViaModelName(itemStack) == null
                && itemStack.getItem() instanceof ItemEnderPearl;
    }

    public static boolean isGoldenApple(ItemStack itemStack) {
        return itemStack != null && getViaModelName(itemStack) == null
                && itemStack.getItem() instanceof ItemAppleGold;
    }

    public static boolean isFishingRod(ItemStack itemStack) {
        return itemStack != null && getViaModelName(itemStack) == null
                && itemStack.getItem() instanceof ItemFishingRod;
    }

    public static boolean isBow(ItemStack itemStack) {
        return itemStack != null && getViaModelName(itemStack) == null
                && itemStack.getItem() instanceof ItemBow;
    }

    public static boolean isRequiredInventoryItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        String modelName = getViaModelName(itemStack);
        if (modelName == null) {
            return itemStack.getItem() == Items.water_bucket;
        }
        return "mace".equals(modelName)
                || "totem_of_undying".equals(modelName)
                || "end_crystal".equals(modelName);
    }

    public static boolean isWaterBucket(ItemStack itemStack) {
        return itemStack != null && getViaModelName(itemStack) == null
                && itemStack.getItem() == Items.water_bucket;
    }

    public static boolean isNotSpecialItem(ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        String modelName = getViaModelName(itemStack);
        if (modelName != null) {
            return !isRequiredInventoryItem(itemStack)
                    && !ViaBackwardsItemModels.isBlockModel(modelName);
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemBlock) {
            return !ItemUtil.isContainerBlock((ItemBlock) item);
        }
        if (item instanceof ItemPotion) {
            return ((ItemPotion) item).getEffects(itemStack).stream().map(PotionEffect::getPotionID).noneMatch(specialItems::contains);
        }
        if (item instanceof ItemEnderPearl) return false;
        if (item instanceof ItemFood) {
            if (item != Items.spider_eye) return false;
        }
        return item != Items.nether_star;
    }

    public static boolean isBlock(ItemStack itemStack) {
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        String modelName = getViaModelName(itemStack);
        if (modelName != null) {
            return ViaBackwardsItemModels.isBlockModel(modelName);
        }
        Item item = itemStack.getItem();
        if (item instanceof ItemBlock) {
            return ItemUtil.isContainerBlock((ItemBlock) item);
        }
        return false;
    }

    public static boolean isContainerBlock(ItemBlock itemBlock) {
        Block block = itemBlock.getBlock();
        if (BlockUtil.isInteractable(block)) return false;
        return BlockUtil.isSolid(block);
    }

    public static double getAttackBonus(ItemStack itemStack) {
        double attackBonus = 0.0;
        if (!isSword(itemStack)) {
            return 0.0;
        }
        Multimap<String, AttributeModifier> multimap = itemStack.getAttributeModifiers();
        for (String attributeName : multimap.keySet()) {
            if (!attributeName.equals("generic.attackDamage")) continue;
            Iterator<AttributeModifier> iterator = multimap.get(attributeName).iterator();
            if (!iterator.hasNext()) break;
            attackBonus += (iterator.next()).getAmount();
            break;
        }
        if (itemStack.isItemEnchanted()) {
            attackBonus = attackBonus + (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.fireAspect.effectId, itemStack) + (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.sharpness.effectId, itemStack) * 1.25;
        }
        return attackBonus;
    }

    public static float getToolEfficiency(ItemStack itemStack) {
        float efficiency = 1.0f;
        if (isTool(itemStack) && itemStack.getItem() instanceof ItemTool) {
            int enchantLevel;
            efficiency = ((ItemTool) itemStack.getItem()).getToolMaterial().getEfficiencyOnProperMaterial();
            if (efficiency > 1.0f && (enchantLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.efficiency.effectId, itemStack)) > 0) {
                efficiency += (float) (enchantLevel * enchantLevel + 1);
            }
        }
        return efficiency;
    }

    public static double getArmorProtection(ItemStack itemStack) {
        double protection = 0.0;
        if (getArmorType(itemStack) != -1 && itemStack.getItem() instanceof ItemArmor) {
            protection = (double) ((ItemArmor) itemStack.getItem()).damageReduceAmount;
            if (itemStack.isItemEnchanted()) {
                protection += (double) EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, itemStack) * 0.25;
            }
        }
        return protection;
    }

    public static int findSwordInInventorySlot(int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        double bestAttackBonus = 0.0;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = ((startSlot + i) % 36 + 36) % 36;
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null) continue;
            if (!isSword(itemStack)) continue;
            if (checkDurability) {
                if (itemStack.isItemDamaged()) {
                    if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) {
                        continue;
                    }
                }
            }
            double attackBonus = ItemUtil.getAttackBonus(itemStack);
            if (!(attackBonus > bestAttackBonus)) continue;
            bestSlot = currentSlot;
            bestAttackBonus = attackBonus;
        }
        return bestSlot;
    }

    public static int findInventorySlot(String toolClass, int startSlot, boolean checkDurability) {
        int bestSlot = -1;
        float bestEfficiency = 1.0f;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = ((startSlot + i) % 36 + 36) % 36;
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null) continue;
            if (!isTool(itemStack, toolClass)) continue;
            if (checkDurability) {
                if (itemStack.isItemDamaged()) {
                    if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) {
                        continue;
                    }
                }
            }
            float efficiency = ItemUtil.getToolEfficiency(itemStack);
            if (!(efficiency > bestEfficiency)) continue;
            bestSlot = currentSlot;
            bestEfficiency = efficiency;
        }
        return bestSlot;
    }

    public static int findInventorySlot(int currentSlot, Block block) {
        ItemStack currentItem = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
        int bestSlot = currentSlot;
        float bestStrength = canUseForBlockBreaking(currentItem) ? currentItem.getStrVsBlock(block) : 1.0f;
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(i);
            if (!canUseForBlockBreaking(itemStack)) continue;
            float strength = itemStack.getStrVsBlock(block);
            if (!(strength > bestStrength)) continue;
            bestSlot = i;
            bestStrength = strength;
        }
        return bestSlot;
    }

    private static boolean canUseForBlockBreaking(ItemStack itemStack) {
        String modelName = getViaModelName(itemStack);
        return itemStack != null && (modelName == null
                || isSword(itemStack)
                || isTool(itemStack)
                || isToolModel(modelName, "hoe"));
    }

    public static int findArmorInventorySlot(int armorType, boolean checkDurability) {
        int bestSlot = -1;
        double bestProtection = 0.0;
        for (int i = 0; i < 40; ++i) {
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack == null) continue;
            if (getArmorType(itemStack) != armorType) {
                continue;
            }
            if (checkDurability) {
                if (itemStack.isItemDamaged()) {
                    if (itemStack.getMaxDamage() - itemStack.getItemDamage() < 30) {
                        continue;
                    }
                }
            }
            double protection = ItemUtil.getArmorProtection(itemStack);
            if (!(protection >= bestProtection)) continue;
            bestSlot = i;
            bestProtection = protection;
        }
        return bestSlot;
    }

    public static int findInventorySlot(int startSlot) {
        int bestSlot = -1;
        int maxStackSize = 0;
        for (int i = 0; i < 36; ++i) {
            int currentSlot = ((startSlot + i) % 36 + 36) % 36;
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (itemStack == null) continue;
            if (!ItemUtil.isBlock(itemStack)) continue;
            if (maxStackSize >= itemStack.stackSize) continue;
            bestSlot = currentSlot;
            maxStackSize = itemStack.stackSize;
        }
        return bestSlot;
    }

    public static boolean hasRawUnbreakingEnchant() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        if (getViaModelName(itemStack) != null && !isSword(itemStack)) {
            return false;
        }
        if (itemStack.hasTagCompound()) {
            NBTTagCompound tag = itemStack.getTagCompound();
            if (tag.hasKey("ExtraAttributes")) {
                NBTTagCompound extra = tag.getCompoundTag("ExtraAttributes");
                if (extra.hasKey("UHCid")) {
                    long id = extra.getLong("UHCid");
                    if (id == 50006L || id == 50009L) {
                        return true;
                    }
                }
            }
            if (tag.hasKey("HideFlags")
                    && itemStack.getItem() instanceof ItemSpade
                    && ((ItemSpade) itemStack.getItem()).getToolMaterial() == Item.ToolMaterial.EMERALD) {
                return true;
            }
        }
        if (itemStack.getItem() instanceof ItemEnchantedBook) {
            return false;
        }
        if (EnchantmentHelper.getEnchantments(itemStack).containsKey(19)) {
            return true;
        }
        return isSword(itemStack);
    }

    public static boolean isHoldingSword() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        return isSword(itemStack);
    }

    public static boolean isHoldingTool() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        return isTool(itemStack);
    }

    public static boolean isEating() {
        if (ItemUtil.mc.thePlayer.isUsingItem() && isEdible(ItemUtil.mc.thePlayer.getItemInUse())) {
            return true;
        }
        return isEdible(ItemUtil.mc.thePlayer.getHeldItem());
    }

    private static boolean isEdible(ItemStack itemStack) {
        if (itemStack == null || getViaModelName(itemStack) != null) {
            return false;
        }
        if (ItemPotion.isSplash(itemStack.getItem().getMetadata(itemStack))) {
            return false;
        }
        return itemStack.getItemUseAction() == EnumAction.EAT || itemStack.getItemUseAction() == EnumAction.DRINK;
    }

    public static boolean isUsingBow() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null) {
            return false;
        }
        return isBow(itemStack);
    }

    public static boolean isHoldingNonEmpty() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        return isBlock(itemStack);
    }

    public static boolean isHoldingBlock() {
        return ItemUtil.isBlock(ItemUtil.mc.thePlayer.getHeldItem());
    }

    public static boolean hasHoldItem() {
        ItemStack itemStack = ItemUtil.mc.thePlayer.getHeldItem();
        if (itemStack == null || itemStack.stackSize < 1) {
            return false;
        }
        return getViaModelName(itemStack) == null && itemStack.getItem() instanceof ItemFireball;
    }

    /**
     * Checks if the given item is a projectile
     */
    public static boolean isProjectile(ItemStack itemStack) {
        if (itemStack == null || getViaModelName(itemStack) != null) {
            return false;
        }
        Item item = itemStack.getItem();
        return item instanceof ItemEgg ||
                item instanceof ItemSnowball;
    }

    /**
     * Finds inventory slots with the specified item type
     */
    public static int findInventorySlot(ItemType itemType) {
        int slot = -1;
        int maxStackSize = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack itemStack = ItemUtil.mc.thePlayer.inventory.getStackInSlot(i);
            if (itemStack != null) {
                if (Objects.requireNonNull(itemType) == ItemType.Projectile) {
                    if (isProjectile(itemStack)) {
                        if (maxStackSize < itemStack.stackSize) {
                            maxStackSize = itemStack.stackSize;
                            slot = i;
                        }
                    }
                }
            }
        }
        return slot == -1 ? -1 : slot + (slot < 9 ? 36 : 0); // Convert to actual inventory slot
    }

    public enum ItemType {
        Projectile
    }

    static final class SpecialItems extends ArrayList<Integer> {
        SpecialItems() {
            this.add(1);
            this.add(3);
            this.add(5);
            this.add(6);
            this.add(8);
            this.add(10);
            this.add(11);
            this.add(12);
            this.add(14);
            this.add(21);
            this.add(22);
        }
    }
}
