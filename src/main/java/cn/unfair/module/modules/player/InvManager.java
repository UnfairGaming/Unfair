package cn.unfair.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAppleGold;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldSettings.GameType;
import org.apache.commons.lang3.RandomUtils;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.UpdateEvent;
import cn.unfair.events.WindowClickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ItemUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public class InvManager extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty minDelay = new IntProperty("Min Delay", 0, 0, 20);
    public final IntProperty maxDelay = new IntProperty("Max Delay", 0, 0, 20);
    public final IntProperty openDelay = new IntProperty("Open Delay", 0, 0, 20);
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Normal", "Instant"});
    public final BooleanProperty autoArmor = new BooleanProperty("Auto Armor", true);
    public final BooleanProperty dropTrash = new BooleanProperty("Drop Trash", true);
    public final IntProperty dropDelay = new IntProperty("Drop Delay", 0, 0, 20);
    public final IntProperty swordSlot = new IntProperty("Sword Slot", 1, 0, 9);
    public final IntProperty pickaxeSlot = new IntProperty("Pickaxe Slot", 8, 0, 9);
    public final IntProperty shovelSlot = new IntProperty("Shovel Slot", 7, 0, 9);
    public final IntProperty axeSlot = new IntProperty("Axe Slot", 9, 0, 9);
    public final IntProperty blocksSlot = new IntProperty("Blocks Slot", 2, 0, 9);
    public final IntProperty blocks = new IntProperty("Blocks", 128, 64, 2304);
    public final IntProperty throwsSlot = new IntProperty("Throws Slot", 4, 0, 9);
    public final IntProperty throwsAmount = new IntProperty("Throws Amount", 64, 16, 320);
    public final IntProperty gappleSlot = new IntProperty("Gapple Slot", 3, 0, 9);
    public final IntProperty fishingRodSlot = new IntProperty("Fishing Rod Slot", 6, 0, 9);
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

    private void clickSlot(int integer1, int integer2, int integer3, int integer4) {
        mc.playerController.windowClick(integer1, integer2, integer3, integer4, mc.thePlayer);
    }

    private int getStackSize(int slot) {
        if (slot == -1) {
            return 0;
        } else {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            return stack != null ? stack.stackSize : 0;
        }
    }

    private boolean isThrowable(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemSnowball || stack.getItem() instanceof ItemEgg;
    }

    private boolean isGapple(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemAppleGold;
    }

    private boolean isFishingRod(ItemStack stack) {
        if (stack == null) return false;
        return stack.getItem() instanceof ItemFishingRod;
    }

    private boolean shouldKeepStack(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        if (item == Items.boat) {
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

    private int findFishingRodSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isFishingRod(stack)) {
                return preferredSlot;
            }
        }

        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;

        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isFishingRod(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findThrowableSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isThrowable(stack)) {
                return preferredSlot;
            }
        }

        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;

        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isThrowable(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findGappleSlot(int preferredSlot, boolean hotbarOnly) {
        if (preferredSlot >= 0 && preferredSlot <= 8) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(preferredSlot);
            if (this.isGapple(stack)) {
                return preferredSlot;
            }
        }

        int start = hotbarOnly ? 0 : 9;
        int end = hotbarOnly ? 9 : 36;

        for (int i = start; i < end; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (this.isGapple(stack)) {
                return i;
            }
        }
        return -1;
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

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.actionDelay > 0) {
                this.actionDelay--;
            }
            if (this.oDelay > 0) {
                this.oDelay--;
            }
            if (this.dropDelayCounter > 0) {
                this.dropDelayCounter--;
            }

            boolean isInventoryOpen = (mc.currentScreen instanceof GuiInventory);

            if (!isInventoryOpen) {
                this.inventoryOpen = false;
            } else if ((mc.currentScreen instanceof GuiInventory) && !(((GuiInventory) mc.currentScreen).inventorySlots instanceof ContainerPlayer)) {
                this.inventoryOpen = false;
            } else {
                if (!this.inventoryOpen) {
                    this.inventoryOpen = true;
                    this.oDelay = this.openDelay.getValue();
                }
                if (this.oDelay > 0) {
                    return;
                }
                if (this.mode.getValue() == 1 || this.actionDelay <= 0 || (this.mode.getValue() == 0 && this.dropTrash.getValue() && this.dropDelayCounter <= 0)) {
                    if (this.isEnabled() && this.isValidGameMode()) {
                        ArrayList<Integer> equippedArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        ArrayList<Integer> inventoryArmorSlots = new ArrayList<>(Arrays.asList(-1, -1, -1, -1));
                        for (int i = 0; i < 4; i++) {
                            equippedArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, true));
                            inventoryArmorSlots.set(i, ItemUtil.findArmorInventorySlot(i, false));
                        }
                        int preferredSwordHotbarSlot = this.swordSlot.getValue() - 1;
                        int equippedSwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, true);
                        int inventorySwordSlot = ItemUtil.findSwordInInventorySlot(preferredSwordHotbarSlot, false);
                        int preferredPickaxeHotbarSlot = this.pickaxeSlot.getValue() - 1;
                        int equippedPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, true);
                        int inventoryPickaxeSlot = ItemUtil.findInventorySlot("pickaxe", preferredPickaxeHotbarSlot, false);
                        int preferredShovelHotbarSlot = this.shovelSlot.getValue() - 1;
                        int equippedShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, true);
                        int inventoryShovelSlot = ItemUtil.findInventorySlot("shovel", preferredShovelHotbarSlot, false);
                        int preferredAxeHotbarSlot = this.axeSlot.getValue() - 1;
                        int equippedAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, true);
                        int inventoryAxeSlot = ItemUtil.findInventorySlot("axe", preferredAxeHotbarSlot, false);
                        int preferredBlocksHotbarSlot = this.blocksSlot.getValue() - 1;
                        int inventoryBlocksSlot = ItemUtil.findInventorySlot(preferredBlocksHotbarSlot);
                        int preferredThrowsHotbarSlot = this.throwsSlot.getValue() - 1;
                        int equippedThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, true);
                        int inventoryThrowsSlot = this.findThrowableSlot(preferredThrowsHotbarSlot, false);
                        int preferredGappleHotbarSlot = this.gappleSlot.getValue() - 1;
                        int equippedGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, true);
                        int inventoryGappleSlot = this.findGappleSlot(preferredGappleHotbarSlot, false);
                        int preferredFishingRodHotbarSlot = this.fishingRodSlot.getValue() - 1;
                        int equippedFishingRodSlot = this.findFishingRodSlot(preferredFishingRodHotbarSlot, true);
                        int inventoryFishingRodSlot = this.findFishingRodSlot(preferredFishingRodHotbarSlot, false);
                        if (this.mode.getValue() == 0 && this.actionDelay <= 0) {
                            if (this.autoArmor.getValue()) {
                                for (int i = 0; i < 4; i++) {
                                    int equippedSlot = equippedArmorSlots.get(i);
                                    int inventorySlot = inventoryArmorSlots.get(i);
                                    if (equippedSlot != -1 || inventorySlot != -1) {
                                        int playerArmorSlot = 39 - i;
                                        if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
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
                                            return;
                                        }
                                    }
                                }
                            }
                            LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                            if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && (equippedSwordSlot != -1 || inventorySwordSlot != -1)) {
                                usedHotbarSlots.add(preferredSwordHotbarSlot);
                                if (equippedSwordSlot != preferredSwordHotbarSlot && inventorySwordSlot != preferredSwordHotbarSlot) {
                                    int slot = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredSwordHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredPickaxeHotbarSlot) && (equippedPickaxeSlot != -1 || inventoryPickaxeSlot != -1)) {
                                usedHotbarSlots.add(preferredPickaxeHotbarSlot);
                                if (equippedPickaxeSlot != preferredPickaxeHotbarSlot && inventoryPickaxeSlot != preferredPickaxeHotbarSlot) {
                                    int slot = equippedPickaxeSlot != -1 ? equippedPickaxeSlot : inventoryPickaxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredPickaxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredShovelHotbarSlot) && (equippedShovelSlot != -1 || inventoryShovelSlot != -1)) {
                                usedHotbarSlots.add(preferredShovelHotbarSlot);
                                if (equippedShovelSlot != preferredShovelHotbarSlot && inventoryShovelSlot != preferredShovelHotbarSlot) {
                                    int slot = equippedShovelSlot != -1 ? equippedShovelSlot : inventoryShovelSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredShovelHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredAxeHotbarSlot) && (equippedAxeSlot != -1 || inventoryAxeSlot != -1)) {
                                usedHotbarSlots.add(preferredAxeHotbarSlot);
                                if (equippedAxeSlot != preferredAxeHotbarSlot && inventoryAxeSlot != preferredAxeHotbarSlot) {
                                    int slot = equippedAxeSlot != -1 ? equippedAxeSlot : inventoryAxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredAxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBlocksHotbarSlot) && inventoryBlocksSlot != -1) {
                                usedHotbarSlots.add(preferredBlocksHotbarSlot);
                                if (inventoryBlocksSlot != preferredBlocksHotbarSlot) {
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBlocksSlot), preferredBlocksHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredThrowsHotbarSlot >= 0 && preferredThrowsHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredThrowsHotbarSlot) && (equippedThrowsSlot != -1 || inventoryThrowsSlot != -1)) {
                                usedHotbarSlots.add(preferredThrowsHotbarSlot);
                                if (equippedThrowsSlot != preferredThrowsHotbarSlot && inventoryThrowsSlot != preferredThrowsHotbarSlot) {
                                    int slot = equippedThrowsSlot != -1 ? equippedThrowsSlot : inventoryThrowsSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredThrowsHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredGappleHotbarSlot >= 0 && preferredGappleHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredGappleHotbarSlot) && (equippedGappleSlot != -1 || inventoryGappleSlot != -1)) {
                                usedHotbarSlots.add(preferredGappleHotbarSlot);
                                if (equippedGappleSlot != preferredGappleHotbarSlot && inventoryGappleSlot != preferredGappleHotbarSlot) {
                                    int slot = equippedGappleSlot != -1 ? equippedGappleSlot : inventoryGappleSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredGappleHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredFishingRodHotbarSlot >= 0 && preferredFishingRodHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredFishingRodHotbarSlot) && (equippedFishingRodSlot != -1 || inventoryFishingRodSlot != -1)) {
                                usedHotbarSlots.add(preferredFishingRodHotbarSlot);
                                if (equippedFishingRodSlot != preferredFishingRodHotbarSlot && inventoryFishingRodSlot != preferredFishingRodHotbarSlot) {
                                    int slot = equippedFishingRodSlot != -1 ? equippedFishingRodSlot : inventoryFishingRodSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredFishingRodHotbarSlot, 2);
                                    return;
                                }
                            }
                        } else if (this.mode.getValue() == 1) {
                            ArrayList<Integer> itemsToDrop = new ArrayList<>();
                            int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
                            int totalThrowsCount = this.getTotalThrowsCount();

                            if (totalThrowsCount > this.throwsAmount.getValue()) {
                                for (int i = 35; i >= 0; i--) {
                                    if (!equippedArmorSlots.contains(i)
                                            && !inventoryArmorSlots.contains(i)
                                            && equippedSwordSlot != i
                                            && inventorySwordSlot != i
                                            && equippedPickaxeSlot != i
                                            && inventoryPickaxeSlot != i
                                            && equippedShovelSlot != i
                                            && inventoryShovelSlot != i
                                            && equippedAxeSlot != i
                                            && inventoryAxeSlot != i
                                            && inventoryBlocksSlot != i
                                            && equippedThrowsSlot != i
                                            && inventoryThrowsSlot != i
                                            && equippedGappleSlot != i
                                            && inventoryGappleSlot != i
                                            && equippedFishingRodSlot != i
                                            && inventoryFishingRodSlot != i) {
                                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                        if (this.isThrowable(stack)) {
                                            itemsToDrop.add(i);
                                        }
                                    }
                                }
                            }

                            for (int i = 0; i < 36; i++) {
                                if (!equippedArmorSlots.contains(i)
                                        && !inventoryArmorSlots.contains(i)
                                        && equippedSwordSlot != i
                                        && inventorySwordSlot != i
                                        && equippedPickaxeSlot != i
                                        && inventoryPickaxeSlot != i
                                        && equippedShovelSlot != i
                                        && inventoryShovelSlot != i
                                        && equippedAxeSlot != i
                                        && inventoryAxeSlot != i
                                        && inventoryBlocksSlot != i
                                        && equippedThrowsSlot != i
                                        && inventoryThrowsSlot != i
                                        && equippedGappleSlot != i
                                        && inventoryGappleSlot != i
                                        && !itemsToDrop.contains(i)) {
                                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                    if (stack != null && !this.shouldKeepStack(stack)) {
                                        boolean isBlock = ItemUtil.isBlock(stack);
                                        boolean isThrowable = this.isThrowable(stack);
                                        boolean isGapple = this.isGapple(stack);
                                        boolean isFishingRod = this.isFishingRod(stack);

                                        if (!isThrowable && !isGapple && !isFishingRod && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= this.blocks.getValue()))) {
                                            itemsToDrop.add(i);
                                        }

                                        if (isBlock) {
                                            currentBlockCount += stack.stackSize;
                                        }
                                    }
                                }
                            }

                            if (!itemsToDrop.isEmpty()) {
                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(itemsToDrop.get(0)), 1, 4);
                                return;
                            }

                            if (this.autoArmor.getValue()) {
                                for (int i = 0; i < 4; i++) {
                                    int equippedSlot = equippedArmorSlots.get(i);
                                    int inventorySlot = inventoryArmorSlots.get(i);
                                    if (equippedSlot != -1 || inventorySlot != -1) {
                                        int playerArmorSlot = 39 - i;
                                        if (equippedSlot != playerArmorSlot && inventorySlot != playerArmorSlot) {
                                            if (mc.thePlayer.inventory.getStackInSlot(playerArmorSlot) != null) {
                                                if (mc.thePlayer.inventory.getFirstEmptyStack() != -1) {
                                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 0, 1);
                                                } else {
                                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(playerArmorSlot), 1, 4);
                                                }
                                                return;
                                            } else {
                                                int armorToEquipSlot = equippedSlot != -1 ? equippedSlot : inventorySlot;
                                                this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(armorToEquipSlot), 0, 1);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                            LinkedHashSet<Integer> usedHotbarSlots = new LinkedHashSet<>();
                            if (preferredSwordHotbarSlot >= 0 && preferredSwordHotbarSlot <= 8 && (equippedSwordSlot != -1 || inventorySwordSlot != -1)) {
                                usedHotbarSlots.add(preferredSwordHotbarSlot);
                                if (equippedSwordSlot != preferredSwordHotbarSlot && inventorySwordSlot != preferredSwordHotbarSlot) {
                                    int slot = equippedSwordSlot != -1 ? equippedSwordSlot : inventorySwordSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredSwordHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredPickaxeHotbarSlot >= 0 && preferredPickaxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredPickaxeHotbarSlot) && (equippedPickaxeSlot != -1 || inventoryPickaxeSlot != -1)) {
                                usedHotbarSlots.add(preferredPickaxeHotbarSlot);
                                if (equippedPickaxeSlot != preferredPickaxeHotbarSlot && inventoryPickaxeSlot != preferredPickaxeHotbarSlot) {
                                    int slot = equippedPickaxeSlot != -1 ? equippedPickaxeSlot : inventoryPickaxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredPickaxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredShovelHotbarSlot >= 0 && preferredShovelHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredShovelHotbarSlot) && (equippedShovelSlot != -1 || inventoryShovelSlot != -1)) {
                                usedHotbarSlots.add(preferredShovelHotbarSlot);
                                if (equippedShovelSlot != preferredShovelHotbarSlot && inventoryShovelSlot != preferredShovelHotbarSlot) {
                                    int slot = equippedShovelSlot != -1 ? equippedShovelSlot : inventoryShovelSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredShovelHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredAxeHotbarSlot >= 0 && preferredAxeHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredAxeHotbarSlot) && (equippedAxeSlot != -1 || inventoryAxeSlot != -1)) {
                                usedHotbarSlots.add(preferredAxeHotbarSlot);
                                if (equippedAxeSlot != preferredAxeHotbarSlot && inventoryAxeSlot != preferredAxeHotbarSlot) {
                                    int slot = equippedAxeSlot != -1 ? equippedAxeSlot : inventoryAxeSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredAxeHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredBlocksHotbarSlot >= 0 && preferredBlocksHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredBlocksHotbarSlot) && inventoryBlocksSlot != -1) {
                                usedHotbarSlots.add(preferredBlocksHotbarSlot);
                                if (inventoryBlocksSlot != preferredBlocksHotbarSlot) {
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(inventoryBlocksSlot), preferredBlocksHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredThrowsHotbarSlot >= 0 && preferredThrowsHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredThrowsHotbarSlot) && (equippedThrowsSlot != -1 || inventoryThrowsSlot != -1)) {
                                usedHotbarSlots.add(preferredThrowsHotbarSlot);
                                if (equippedThrowsSlot != preferredThrowsHotbarSlot && inventoryThrowsSlot != preferredThrowsHotbarSlot) {
                                    int slot = equippedThrowsSlot != -1 ? equippedThrowsSlot : inventoryThrowsSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredThrowsHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredGappleHotbarSlot >= 0 && preferredGappleHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredGappleHotbarSlot) && (equippedGappleSlot != -1 || inventoryGappleSlot != -1)) {
                                usedHotbarSlots.add(preferredGappleHotbarSlot);
                                if (equippedGappleSlot != preferredGappleHotbarSlot && inventoryGappleSlot != preferredGappleHotbarSlot) {
                                    int slot = equippedGappleSlot != -1 ? equippedGappleSlot : inventoryGappleSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredGappleHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredFishingRodHotbarSlot >= 0 && preferredFishingRodHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredFishingRodHotbarSlot) && (equippedFishingRodSlot != -1 || inventoryFishingRodSlot != -1)) {
                                usedHotbarSlots.add(preferredFishingRodHotbarSlot);
                                if (equippedFishingRodSlot != preferredFishingRodHotbarSlot && inventoryFishingRodSlot != preferredFishingRodHotbarSlot) {
                                    int slot = equippedFishingRodSlot != -1 ? equippedFishingRodSlot : inventoryFishingRodSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredFishingRodHotbarSlot, 2);
                                    return;
                                }
                            }
                            if (preferredFishingRodHotbarSlot >= 0 && preferredFishingRodHotbarSlot <= 8 && !usedHotbarSlots.contains(preferredFishingRodHotbarSlot) && (equippedFishingRodSlot != -1 || inventoryFishingRodSlot != -1)) {
                                usedHotbarSlots.add(preferredFishingRodHotbarSlot);
                                if (equippedFishingRodSlot != preferredFishingRodHotbarSlot && inventoryFishingRodSlot != preferredFishingRodHotbarSlot) {
                                    int slot = equippedFishingRodSlot != -1 ? equippedFishingRodSlot : inventoryFishingRodSlot;
                                    this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(slot), preferredFishingRodHotbarSlot, 2);
                                }
                            }
                        }
                        if (this.mode.getValue() == 0 && this.dropTrash.getValue() && this.dropDelayCounter <= 0) {
                            int currentBlockCount = this.getStackSize(inventoryBlocksSlot);
                            int totalThrowsCount = this.getTotalThrowsCount();

                            if (totalThrowsCount > this.throwsAmount.getValue()) {
                                for (int i = 35; i >= 0; i--) {
                                    if (!equippedArmorSlots.contains(i)
                                            && !inventoryArmorSlots.contains(i)
                                            && equippedSwordSlot != i
                                            && inventorySwordSlot != i
                                            && equippedPickaxeSlot != i
                                            && inventoryPickaxeSlot != i
                                            && equippedShovelSlot != i
                                            && inventoryShovelSlot != i
                                            && equippedAxeSlot != i
                                            && inventoryAxeSlot != i
                                            && inventoryBlocksSlot != i
                                            && equippedThrowsSlot != i
                                            && inventoryThrowsSlot != i
                                            && equippedGappleSlot != i
                                            && inventoryGappleSlot != i
                                            && equippedFishingRodSlot != i
                                            && inventoryFishingRodSlot != i) {
                                        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                        if (this.isThrowable(stack)) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            this.dropDelayCounter = this.dropDelay.getValue();
                                            return;
                                        }
                                    }
                                }
                            }

                            for (int i = 0; i < 36; i++) {
                                if (!equippedArmorSlots.contains(i)
                                        && !inventoryArmorSlots.contains(i)
                                        && equippedSwordSlot != i
                                        && inventorySwordSlot != i
                                        && equippedPickaxeSlot != i
                                        && inventoryPickaxeSlot != i
                                        && equippedShovelSlot != i
                                        && inventoryShovelSlot != i
                                        && equippedAxeSlot != i
                                        && inventoryAxeSlot != i
                                        && inventoryBlocksSlot != i
                                        && equippedThrowsSlot != i
                                        && inventoryThrowsSlot != i
                                        && equippedGappleSlot != i
                                        && inventoryGappleSlot != i
                                        && equippedFishingRodSlot != i
                                        && inventoryFishingRodSlot != i) {
                                    ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                                    if (stack != null && !this.shouldKeepStack(stack)) {
                                        boolean isBlock = ItemUtil.isBlock(stack);
                                        boolean isThrowable = this.isThrowable(stack);
                                        boolean isGapple = this.isGapple(stack);
                                        boolean isFishingRod = this.isFishingRod(stack);

                                        if (!isThrowable && !isGapple && !isFishingRod && (ItemUtil.isNotSpecialItem(stack) || (isBlock && currentBlockCount >= this.blocks.getValue()))) {
                                            this.clickSlot(mc.thePlayer.inventoryContainer.windowId, this.convertSlotIndex(i), 1, 4);
                                            this.dropDelayCounter = this.dropDelay.getValue();
                                            return;
                                        }

                                        if (isBlock) {
                                            currentBlockCount += stack.stackSize;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
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
            case "min-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.maxDelay.setValue(this.minDelay.getValue());
                }
                break;
            case "max-delay":
                if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                    this.minDelay.setValue(this.maxDelay.getValue());
                }
        }
    }
}
