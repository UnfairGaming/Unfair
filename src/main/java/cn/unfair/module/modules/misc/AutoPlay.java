package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

public class AutoPlay extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty(
            "Mode", 0, new String[]{"Paper", "Hypixel"}
    );
    public final ModeProperty hypixelMode = new ModeProperty(
            "Hypixel Mode", 0, new String[]{"Skywars", "Bedwars"}, () -> this.mode.getValue() == 1
    );
    public final ModeProperty skywarsMode = new ModeProperty(
            "Skywars Mode", 0, new String[]{"SoloNormal", "SoloInsane"},
            () -> this.mode.getValue() == 1 && this.hypixelMode.getValue() == 0
    );
    public final ModeProperty bedwarsMode = new ModeProperty(
            "Bedwars Mode", 0, new String[]{"Solo", "Double", "Trio", "Quad"},
            () -> this.mode.getValue() == 1 && this.hypixelMode.getValue() == 1
    );
    public final IntProperty delay = new IntProperty("Delay", 50, 0, 200);

    private int delayTick;

    public AutoPlay() {
        super("AutoPlay", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.PRE) {
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        if (player == null || mc.theWorld == null) {
            this.delayTick = 0;
            return;
        }

        if (!this.playerInGame(player) || !player.inventory.hasItemStack(new ItemStack(Items.paper))) {
            this.delayTick = 0;
            return;
        }

        ++this.delayTick;

        switch (this.mode.getValue()) {
            case 0 -> {
                int paperSlot = this.findPaperHotbarSlot(player);
                if (paperSlot == -1) {
                    return;
                }

                if (player.inventory.currentItem != paperSlot) {
                    player.inventory.currentItem = paperSlot;
                    mc.playerController.syncCurrentPlayItem();
                }

                if (this.delayTick >= this.delay.getValue()) {
                    ItemStack paper = player.inventory.getStackInSlot(paperSlot);
                    if (paper != null && paper.getItem() == Items.paper) {
                        mc.playerController.sendUseItem(player, mc.theWorld, paper);
                    }
                    this.delayTick = 0;
                }
            }

            case 1 -> {
                if (this.delayTick < this.delay.getValue()) {
                    return;
                }

                switch (this.hypixelMode.getValue()) {
                    case 0 -> {
                        if (this.skywarsMode.getValue() == 0) {
                            player.sendChatMessage("/play solo_normal");
                        } else {
                            player.sendChatMessage("/play solo_insane");
                        }
                    }
                    case 1 -> {
                        switch (this.bedwarsMode.getValue()) {
                            case 0 -> player.sendChatMessage("/play bedwars_eight_one");
                            case 1 -> player.sendChatMessage("/play bedwars_eight_two");
                            case 2 -> player.sendChatMessage("/play bedwars_four_three");
                            case 3 -> player.sendChatMessage("/play bedwars_four_four");
                        }
                    }
                }
                this.delayTick = 0;
            }
        }
    }

    private boolean playerInGame(EntityPlayerSP player) {
        return player.ticksExisted >= 20
                && (player.capabilities.isFlying
                || player.capabilities.allowFlying
                || player.capabilities.disableDamage);
    }

    private int findPaperHotbarSlot(EntityPlayerSP player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Items.paper) {
                return slot;
            }
        }
        return -1;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
