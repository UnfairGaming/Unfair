package cn.unfair.module.modules.player;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.UpdateEvent;
import cn.unfair.events.WindowClickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.via.ModernOffhandInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.*;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int OFFHAND_TARGET_SLOT = 9;

    public final IntProperty minDelay = new IntProperty("Min Delay", 0, 0, 20);
    public final IntProperty maxDelay = new IntProperty("Max Delay", 0, 0, 20);
    public final IntProperty openDelay = new IntProperty("Open Delay", 0, 0, 20);
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Instant"});
    public final BooleanProperty autoArmor = new BooleanProperty("Auto Armor", true);
    public final BooleanProperty dropTrash = new BooleanProperty("Drop Trash", true);
    public final IntProperty dropDelay = new IntProperty("Drop Delay", 0, 0, 20);
    public final BooleanProperty sword = new BooleanProperty("Sword", true);
    public final IntProperty swordSlot = new IntProperty("Sword Slot", 1, 1, 10, this.sword::getValue);
    public final BooleanProperty pickaxe = new BooleanProperty("Pickaxe", true);
    public final IntProperty pickaxeSlot = new IntProperty("Pickaxe Slot", 9, 1, 10, this.pickaxe::getValue);
    public final BooleanProperty shovel = new BooleanProperty("Shovel", false);
    public final IntProperty shovelSlot = new IntProperty("Shovel Slot", 7, 1, 10, this.shovel::getValue);
    public final BooleanProperty axe = new BooleanProperty("Axe", true);
    public final IntProperty axeSlot = new IntProperty("Axe Slot", 8, 1, 10, this.axe::getValue);
    public final BooleanProperty blocksEnabled = new BooleanProperty("Blocks Enabled", true);
    public final IntProperty blocksSlot = new IntProperty("Blocks Slot", 2, 1, 10, this.blocksEnabled::getValue);
    public final IntProperty blocks = new IntProperty("Blocks Amount", 128, 64, 2304);
    public final BooleanProperty throwsEnabled = new BooleanProperty("Throws", true);
    public final IntProperty throwsSlot = new IntProperty("Throws Slot", 3, 1, 10, this.throwsEnabled::getValue);
    public final IntProperty throwsAmount = new IntProperty("Throws Amount", 64, 16, 320);
    public final BooleanProperty pearl = new BooleanProperty("Pearl", false);
    public final IntProperty pearlSlot = new IntProperty("Pearl Slot", 6, 1, 10, this.pearl::getValue);
    public final BooleanProperty gapple = new BooleanProperty("Gapple", true);
    public final IntProperty gappleSlot = new IntProperty("Gapple Slot", 5, 1, 10, this.gapple::getValue);
    public final BooleanProperty fishingRod = new BooleanProperty("Fishing Rod", true);
    public final IntProperty fishingRodSlot = new IntProperty("Fishing Rod Slot", 4, 1, 10, this.fishingRod::getValue);
    public final BooleanProperty bow = new BooleanProperty("Bow", false);
    public final IntProperty bowSlot = new IntProperty("Bow Slot", 6, 1, 10, this.bow::getValue);
    public final BooleanProperty waterBucket = new BooleanProperty("Water Bucket", false);
    public final IntProperty waterBucketSlot = new IntProperty("Water Bucket Slot", 5, 1, 10, this.waterBucket::getValue);

    private int actionDelay = 0;
    private int oDelay = 0;
    private int dropDelayCounter = 0;
    private boolean inventoryOpen = false;

    public InvManager() {
        super("InvManager", false);
    }

    private boolean isValidGameMode() {
        GameType gameType = mc.playerController.getCurrentGameType();
        return gameType == GameType.SURVIVAL || gameType == GameType.ADVENTURE;
    }

    private int convertSlotIndex(int slot) {
        if (slot >= 36) {
            return 8 - (slot - 36);
        } else {
            return slot <= 8 ? slot + 36 : slot;
        }
    }

    private void clickSlot(int windowId, int slotId, int mouseButtonClicked, int mode) {
        mc.playerController.windowClick(windowId, slotId, mouseButtonClicked, mode, mc.thePlayer);
    }

    private int getStackSize(int slot) {
        if (slot == -1) {
            return 0;
        }
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        return stack != null ? stack.stackSize : 0;
    }

    private boolean isThrowable(ItemStack stack) {
        return ItemUtil.isProjectile(stack);
    }

    private boolean isGapple(ItemStack stack) {
        return ItemUtil.isGoldenApple(stack);
    }

    private boolean isPearl(ItemStack stack) {
        return ItemUtil.isEnderPearl(stack);
    }

    private boolean isFishingRod(ItemStack stack) {
        return ItemUtil.isFishingRod(stack);
    }

    private boolean isBow(ItemStack stack) {
        return ItemUtil.isBow(stack);
    }

    private boolean isWaterBucket(ItemStack stack) {
        return ItemUtil.isWaterBucket(stack);
    }

    private double getDurabilityScore(ItemStack stack) {
        if (stack == null || !stack.isItemStackDamageable()) {
            return 0.0D;
        }
        return (double) (stack.getMaxDamage() - stack.getItemDamage()) / Math.max(1, stack.getMaxDamage());
    }

    private double getBowScore(ItemStack stack) {
        if (!this.isBow(stack)) {
            return Double.NEGATIVE_INFINITY;
        }
        return EnchantmentHelper.getEnchantmentLevel(Enchantment.power.effectId, stack) * 10.0D
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.punch.effectId, stack) * 4.0D
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.flame.effectId, stack) * 3.0D
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.infinity.effectId, stack) * 2.0D
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack)
                + this.getDurabilityScore(stack);
    }

    private double getFishingRodScore(ItemStack stack) {
        if (!this.isFishingRod(stack)) {
            return Double.NEGATIVE_INFINITY;
        }
        return EnchantmentHelper.getEnchantmentLevel(Enchantment.knockback.effectId, stack) * 10.0D
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.lure.effectId, stack) * 4.0D
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.luckOfTheSea.effectId, stack) * 3.0D
                + EnchantmentHelper.getEnchantmentLevel(Enchantment.unbreaking.effectId, stack)
                + this.getDurabilityScore(stack);
    }

    private boolean shouldKeepStack(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item == Items.boat
                || item == Items.arrow
                || this.isPearl(stack)) {
            return true;
        }
        if (ItemUtil.isRequiredInventoryItem(stack)) {
            return true;
        }
        if (item instanceof ItemBlock) {
            return item == Item.getItemFromBlock(Blocks.planks)
                    || item == Item.getItemFromBlock(Blocks.log)
                    || item == Item.getItemFromBlock(Blocks.log2)
                    || item == Item.getItemFromBlock(Blocks.crafting_table);
        }
        return false;
    }

    private int findMatchingSlot(int preferredSlot, boolean hotbarOnly, Predicate<ItemStack> matcher) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (matcher.test(stack)) {
                return preferredSlot;
            }
        }

        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (matcher.test(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findBestMatchingSlot(int preferredSlot, Predicate<ItemStack> matcher, ToDoubleFunction<ItemStack> scorer) {
        int bestSlot = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < 36; i++) {
            int currentSlot = ((preferredSlot + i) % 36 + 36) % 36;
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(currentSlot);
            if (!matcher.test(stack)) {
                continue;
            }

            double score = scorer.applyAsDouble(stack);
            if (score > bestScore) {
                bestSlot = currentSlot;
                bestScore = score;
            }
        }
        return bestSlot;
    }

    private int hotbarSlotOrMissing(int slot) {
        return slot >= 0 && slot <= 8 ? slot : -1;
    }

    private int inventorySlotOrMissing(int slot) {
        return slot >= 9 && slot < 36 ? slot : -1;
    }

    private int getTotalThrowsCount() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isThrowable(stack)) {
                count += stack.stackSize;
            }
        }
        return count;
    }

    private boolean isConfiguredOffhand(int targetSlot) {
        return targetSlot == OFFHAND_TARGET_SLOT && ModernOffhandInteraction.isModernTarget();
    }

    private boolean isOffhandMatching(Predicate<ItemStack> matcher) {
        return ModernOffhandInteraction.isModernTarget() && matcher.test(ModernOffhandInteraction.getOffhand(mc.thePlayer));
    }

    private boolean moveSlotToOffhand(int slot) {
        if (!ModernOffhandInteraction.isModernTarget() || slot < 0 || slot >= 36) {
            return false;
        }
        if (!(mc.thePlayer.openContainer instanceof ContainerPlayer)
                || mc.thePlayer.openContainer.inventorySlots.size() <= 45
                || mc.thePlayer.inventory.getItemStack() != null) {
            return false;
        }

        int windowId = mc.thePlayer.inventoryContainer.windowId;
        int sourceSlot = this.convertSlotIndex(slot);
        this.clickSlot(windowId, sourceSlot, 0, 0);
        this.clickSlot(windowId, 45, 0, 0);
        if (mc.thePlayer.inventory.getItemStack() != null) {
            this.clickSlot(windowId, sourceSlot, 0, 0);
        }
        return true;
    }

    private boolean organizeSlot(boolean enabled, int targetSlot, int equippedSlot, int inventorySlot,
                                 Predicate<ItemStack> matcher, LinkedHashSet<Integer> usedHotbarSlots) {
        if (!enabled || targetSlot < 0) {
            return false;
        }

        if (this.isConfiguredOffhand(targetSlot)) {
            if (this.isOffhandMatching(matcher)) {
                return false;
            }
            int slot = equippedSlot != -1 ? equippedSlot : inventorySlot;
            return slot != -1 && this.moveSlotToOffhand(slot);
        }

        if (targetSlot > 8 || usedHotbarSlots.contains(targetSlot) || (equippedSlot == -1 && inventorySlot == -1)) {
            return false;
        }

        usedHotbarSlots.add(targetSlot);
        if (equippedSlot != targetSlot && inventorySlot != targetSlot) {
            int slot = equippedSlot != -1 ? equippedSlot : inventorySlot;
            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), targetSlot, 2);
            return true;
        }
        return false;
    }

    private boolean organizeItems(InventoryPlan plan) {
        LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
        return this.organizeSlot(this.sword.getValue(), plan.swordTarget, plan.equippedSwordSlot, plan.inventorySwordSlot, ItemUtil::isSword, usedHotbarSlots)
                || this.organizeSlot(this.pickaxe.getValue(), plan.pickaxeTarget, plan.equippedPickaxeSlot, plan.inventoryPickaxeSlot, stack -> ItemUtil.isTool(stack, "pickaxe"), usedHotbarSlots)
                || this.organizeSlot(this.shovel.getValue(), plan.shovelTarget, plan.equippedShovelSlot, plan.inventoryShovelSlot, stack -> ItemUtil.isTool(stack, "shovel"), usedHotbarSlots)
                || this.organizeSlot(this.axe.getValue(), plan.axeTarget, plan.equippedAxeSlot, plan.inventoryAxeSlot, stack -> ItemUtil.isTool(stack, "axe"), usedHotbarSlots)
                || this.organizeSlot(this.blocksEnabled.getValue(), plan.blocksTarget, plan.inventoryBlocksSlot, -1, ItemUtil::isBlock, usedHotbarSlots)
                || this.organizeSlot(this.throwsEnabled.getValue(), plan.throwsTarget, plan.equippedThrowsSlot, plan.inventoryThrowsSlot, this::isThrowable, usedHotbarSlots)
                || this.organizeSlot(this.pearl.getValue(), plan.pearlTarget, plan.equippedPearlSlot, plan.inventoryPearlSlot, this::isPearl, usedHotbarSlots)
                || this.organizeSlot(this.gapple.getValue(), plan.gappleTarget, plan.equippedGappleSlot, plan.inventoryGappleSlot, this::isGapple, usedHotbarSlots)
                || this.organizeSlot(this.fishingRod.getValue(), plan.fishingRodTarget, plan.equippedFishingRodSlot, plan.inventoryFishingRodSlot, this::isFishingRod, usedHotbarSlots)
                || this.organizeSlot(this.bow.getValue(), plan.bowTarget, plan.equippedBowSlot, plan.inventoryBowSlot, this::isBow, usedHotbarSlots)
                || this.organizeSlot(this.waterBucket.getValue(), plan.waterBucketTarget, plan.equippedWaterBucketSlot, plan.inventoryWaterBucketSlot, this::isWaterBucket, usedHotbarSlots);
    }

    private boolean equipArmor(InventoryPlan plan) {
        if (!this.autoArmor.getValue()) {
            return false;
        }

        for (int i = 0; i < 4; i++) {
            int equippedSlot = plan.equippedArmorSlots.get(i);
            int inventorySlot = plan.inventoryArmorSlots.get(i);
            if (equippedSlot == -1 && inventorySlot == -1) {
                continue;
            }

            int playerArmorSlot = 39 - i;
            if (equippedSlot == playerArmorSlot || inventorySlot == playerArmorSlot) {
                continue;
            }

            if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
                if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 0, 1);
                } else {
                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 1, 4);
                }
            } else {
                int armorToEquipSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(armorToEquipSlot), 0, 1);
            }
            return true;
        }
        return false;
    }

    private void addProtectedSlot(LinkedHashSet<Integer> protectedSlots, int slot) {
        if (slot >= 0 && slot < 36) {
            protectedSlots.add(slot);
        }
    }

    private void addUniqueProtectedSlot(LinkedHashSet<Integer> protectedSlots, Predicate<ItemStack> matcher,
                                        int equippedSlot, int inventorySlot) {
        if (this.isOffhandMatching(matcher)) {
            return;
        }
        this.addProtectedSlot(protectedSlots, equippedSlot != -1 ? equippedSlot : inventorySlot);
    }

    private LinkedHashSet<Integer> buildProtectedSlots(InventoryPlan plan) {
        LinkedHashSet<Integer> protectedSlots = new LinkedHashSet<>();
        for (int slot : plan.equippedArmorSlots) this.addProtectedSlot(protectedSlots, slot);
        for (int slot : plan.inventoryArmorSlots) this.addProtectedSlot(protectedSlots, slot);
        this.addProtectedSlot(protectedSlots, plan.equippedSwordSlot);
        this.addProtectedSlot(protectedSlots, plan.inventorySwordSlot);
        this.addProtectedSlot(protectedSlots, plan.equippedPickaxeSlot);
        this.addProtectedSlot(protectedSlots, plan.inventoryPickaxeSlot);
        this.addProtectedSlot(protectedSlots, plan.equippedShovelSlot);
        this.addProtectedSlot(protectedSlots, plan.inventoryShovelSlot);
        this.addProtectedSlot(protectedSlots, plan.equippedAxeSlot);
        this.addProtectedSlot(protectedSlots, plan.inventoryAxeSlot);
        this.addProtectedSlot(protectedSlots, plan.inventoryBlocksSlot);
        this.addProtectedSlot(protectedSlots, plan.equippedThrowsSlot);
        this.addProtectedSlot(protectedSlots, plan.inventoryThrowsSlot);
        this.addProtectedSlot(protectedSlots, plan.equippedPearlSlot);
        this.addProtectedSlot(protectedSlots, plan.inventoryPearlSlot);
        this.addProtectedSlot(protectedSlots, plan.equippedGappleSlot);
        this.addProtectedSlot(protectedSlots, plan.inventoryGappleSlot);
        this.addUniqueProtectedSlot(protectedSlots, this::isFishingRod, plan.equippedFishingRodSlot, plan.inventoryFishingRodSlot);
        this.addUniqueProtectedSlot(protectedSlots, this::isBow, plan.equippedBowSlot, plan.inventoryBowSlot);
        this.addUniqueProtectedSlot(protectedSlots, this::isWaterBucket, plan.equippedWaterBucketSlot, plan.inventoryWaterBucketSlot);
        return protectedSlots;
    }

    private boolean shouldDropTrashStack(ItemStack stack, int currentBlockCount) {
        if (stack == null || this.shouldKeepStack(stack)) {
            return false;
        }

        boolean isBlock = ItemUtil.isBlock(stack);
        boolean isThrowable = this.isThrowable(stack);
        boolean isPearl = this.isPearl(stack);
        boolean isGapple = this.isGapple(stack);
        return !isThrowable
                && !isPearl
                && !isGapple
                && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= this.blocks.getValue()));
    }

    private Integer findTrashSlot(InventoryPlan plan) {
        LinkedHashSet<Integer> protectedSlots = this.buildProtectedSlots(plan);
        int currentBlockCount = this.getStackSize(plan.inventoryBlocksSlot);

        if (this.getTotalThrowsCount() > this.throwsAmount.getValue()) {
            for (int i = 35; i >= 0; i--) {
                if (!protectedSlots.contains(i) && this.isThrowable(mc.thePlayer.inventory.getStackInSlot(i))) {
                    return i;
                }
            }
        }

        for (int i = 0; i < 36; i++) {
            if (protectedSlots.contains(i)) {
                continue;
            }

            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.shouldDropTrashStack(stack, currentBlockCount)) {
                return i;
            }
            if (ItemUtil.isBlock(stack)) {
                currentBlockCount += stack.stackSize;
            }
        }
        return null;
    }

    private boolean dropTrash(InventoryPlan plan, boolean setDropDelay) {
        Integer trashSlot = this.findTrashSlot(plan);
        if (trashSlot == null) {
            return false;
        }

        this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(trashSlot), 1, 4);
        if (setDropDelay) {
            this.dropDelayCounter = this.dropDelay.getValue();
        }
        return true;
    }

    private InventoryPlan buildPlan() {
        InventoryPlan plan = new InventoryPlan();
        for (int i = 0; i < 4; i++) {
            plan.equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
            plan.inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
        }

        plan.swordTarget = this.swordSlot.getValue() - 1;
        plan.equippedSwordSlot = ItemUtil.findSwordInInventorySlot(plan.swordTarget, true);
        plan.inventorySwordSlot = ItemUtil.findSwordInInventorySlot(plan.swordTarget, false);
        plan.pickaxeTarget = this.pickaxeSlot.getValue() - 1;
        plan.equippedPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", plan.pickaxeTarget, true);
        plan.inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", plan.pickaxeTarget, false);
        plan.shovelTarget = this.shovelSlot.getValue() - 1;
        plan.equippedShovelSlot = ItemUtil.findInventorySlot("shovel", plan.shovelTarget, true);
        plan.inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", plan.shovelTarget, false);
        plan.axeTarget = this.axeSlot.getValue() - 1;
        plan.equippedAxeSlot = ItemUtil.findInventorySlot("axe", plan.axeTarget, true);
        plan.inventoryAxeSlot = ItemUtil.findInventorySlot("axe", plan.axeTarget, false);
        plan.blocksTarget = this.blocksSlot.getValue() - 1;
        plan.inventoryBlocksSlot = ItemUtil.findInventorySlot(plan.blocksTarget);
        plan.throwsTarget = this.throwsSlot.getValue() - 1;
        int bestThrowsSlot = this.findBestMatchingSlot(plan.throwsTarget, this::isThrowable, stack -> stack.stackSize);
        plan.equippedThrowsSlot = this.hotbarSlotOrMissing(bestThrowsSlot);
        plan.inventoryThrowsSlot = this.inventorySlotOrMissing(bestThrowsSlot);
        plan.pearlTarget = this.pearlSlot.getValue() - 1;
        plan.equippedPearlSlot = this.findMatchingSlot(plan.pearlTarget, true, this::isPearl);
        plan.inventoryPearlSlot = this.findMatchingSlot(plan.pearlTarget, false, this::isPearl);
        plan.gappleTarget = this.gappleSlot.getValue() - 1;
        plan.equippedGappleSlot = this.findMatchingSlot(plan.gappleTarget, true, this::isGapple);
        plan.inventoryGappleSlot = this.findMatchingSlot(plan.gappleTarget, false, this::isGapple);
        plan.fishingRodTarget = this.fishingRodSlot.getValue() - 1;
        int bestFishingRodSlot = this.findBestMatchingSlot(plan.fishingRodTarget, this::isFishingRod, this::getFishingRodScore);
        plan.equippedFishingRodSlot = this.hotbarSlotOrMissing(bestFishingRodSlot);
        plan.inventoryFishingRodSlot = this.inventorySlotOrMissing(bestFishingRodSlot);
        plan.bowTarget = this.bowSlot.getValue() - 1;
        int bestBowSlot = this.findBestMatchingSlot(plan.bowTarget, this::isBow, this::getBowScore);
        plan.equippedBowSlot = this.hotbarSlotOrMissing(bestBowSlot);
        plan.inventoryBowSlot = this.inventorySlotOrMissing(bestBowSlot);
        plan.waterBucketTarget = this.waterBucketSlot.getValue() - 1;
        int bestWaterBucketSlot = this.findBestMatchingSlot(plan.waterBucketTarget, this::isWaterBucket, stack -> 1.0D);
        plan.equippedWaterBucketSlot = this.hotbarSlotOrMissing(bestWaterBucketSlot);
        plan.inventoryWaterBucketSlot = this.inventorySlotOrMissing(bestWaterBucketSlot);
        return plan;
    }

    private boolean handleInventoryActions() {
        InventoryPlan plan = this.buildPlan();
        if (this.mode.getValue() == 1) {
            return this.dropTrash.getValue() && this.dropTrash(plan, false)
                    || this.equipArmor(plan)
                    || this.organizeItems(plan);
        }

        if (this.actionDelay <= 0 && (this.equipArmor(plan) || this.organizeItems(plan))) {
            return true;
        }
        return this.dropTrash.getValue() && this.dropDelayCounter <= 0 && this.dropTrash(plan, true);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }

        if (this.actionDelay > 0) {
            this.actionDelay--;
        }
        if (this.oDelay > 0) {
            this.oDelay--;
        }
        if (this.dropDelayCounter > 0) {
            this.dropDelayCounter--;
        }

        boolean isInventoryOpen = mc.currentScreen instanceof GuiInventory;
        if (!isInventoryOpen) {
            this.inventoryOpen = false;
            return;
        }
        if (!(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
            this.inventoryOpen = false;
            return;
        }

        if (!this.inventoryOpen) {
            this.inventoryOpen = true;
            this.oDelay = this.openDelay.getValue();
        }
        if (this.oDelay > 0) {
            return;
        }
        if (!this.isEnabled() || !this.isValidGameMode()) {
            return;
        }
        if (this.mode.getValue() == 1 || this.actionDelay <= 0 || this.dropTrash.getValue() && this.dropDelayCounter <= 0) {
            this.handleInventoryActions();
        }
    }

    @EventTarget
    public void onClick(WindowClickEvent event) {
        if (this.minDelay.getValue() == 0 && this.maxDelay.getValue() == 0) {
            this.actionDelay = 0;
        } else {
            this.actionDelay = RandomUtils.nextInt(
                    this.minDelay.getValue() + 1,
                    this.maxDelay.getValue() + 2
            );
        }
    }

    @Override
    public void verifyValue(String string) {
        switch (string) {
            case "Min Delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "Max Delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
                break;
        }
    }

    private static class InventoryPlan {
        private final ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
        private final ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
        private int swordTarget;
        private int equippedSwordSlot;
        private int inventorySwordSlot;
        private int pickaxeTarget;
        private int equippedPickaxeSlot;
        private int inventoryPickaxeSlot;
        private int shovelTarget;
        private int equippedShovelSlot;
        private int inventoryShovelSlot;
        private int axeTarget;
        private int equippedAxeSlot;
        private int inventoryAxeSlot;
        private int blocksTarget;
        private int inventoryBlocksSlot;
        private int throwsTarget;
        private int equippedThrowsSlot;
        private int inventoryThrowsSlot;
        private int pearlTarget;
        private int equippedPearlSlot;
        private int inventoryPearlSlot;
        private int gappleTarget;
        private int equippedGappleSlot;
        private int inventoryGappleSlot;
        private int fishingRodTarget;
        private int equippedFishingRodSlot;
        private int inventoryFishingRodSlot;
        private int bowTarget;
        private int equippedBowSlot;
        private int inventoryBowSlot;
        private int waterBucketTarget;
        private int equippedWaterBucketSlot;
        private int inventoryWaterBucketSlot;
    }
}
