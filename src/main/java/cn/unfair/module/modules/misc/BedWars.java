package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.RenderUtil;
import cn.unfair.util.TeamUtil;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockObsidian;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemFireball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BedWars extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty diamondArmor = new BooleanProperty("Diamond Armor", true);
    public final BooleanProperty fireball = new BooleanProperty("Fireball", false);
    public final BooleanProperty enderPearl = new BooleanProperty("Ender Pearl", true);
    public final BooleanProperty obsidian = new BooleanProperty("Obsidian", true);
    public final BooleanProperty shouldPing = new BooleanProperty("Should Ping", true);

    private final List<String> armoredPlayers = new ArrayList<>();
    private final Map<String, String> lastHeldItems = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> obsidianPositions = new HashMap<>();

    public BedWars() {
        super("BedWars", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.POST || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (!this.diamondArmor.getValue() && !this.enderPearl.getValue() && !this.fireball.getValue() && !this.obsidian.getValue()) {
            return;
        }

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == null || player == mc.thePlayer || TeamUtil.shouldBlockBot(player)) {
                continue;
            }

            String name = player.getName();
            if (this.diamondArmor.getValue()) {
                ItemStack leggings = player.inventory.armorInventory[1];
                if (!this.armoredPlayers.contains(name) && leggings != null && leggings.getItem() == Items.diamond_leggings) {
                    this.armoredPlayers.add(name);
                    ChatUtil.sendFormatted("&eAlert: &r" + player.getDisplayName().getFormattedText() + " &7has purchased &bDiamond Armor");
                    this.ping();
                }
            }

            ItemStack heldItem = player.getHeldItem();
            String itemType = this.getItemType(heldItem);
            if (itemType != null && !this.lastHeldItems.containsKey(name)) {
                this.lastHeldItems.put(name, itemType);
                int distance = Math.round(mc.thePlayer.getDistanceToEntity(player));
                this.handleAlert(itemType, player.getDisplayName().getFormattedText(), distance);
            } else if (this.lastHeldItems.containsKey(name) && !this.lastHeldItems.get(name).equals(itemType)) {
                this.lastHeldItems.remove(name);
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE || !(event.getPacket() instanceof S23PacketBlockChange packet)) {
            return;
        }

        BlockPos position = packet.getBlockPosition();
        if (this.obsidian.getValue() && packet.getBlockState().getBlock() instanceof BlockObsidian && this.isNextToBed(position)) {
            this.obsidianPositions.put(position, System.currentTimeMillis());
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.obsidian.getValue() || mc.theWorld == null || this.obsidianPositions.isEmpty()) {
            return;
        }

        RenderUtil.enableRenderState();
        try {
            Iterator<Map.Entry<BlockPos, Long>> iterator = this.obsidianPositions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, Long> entry = iterator.next();
                BlockPos position = entry.getKey();
                if (!(mc.theWorld.getBlockState(position).getBlock() instanceof BlockObsidian)
                        && System.currentTimeMillis() - entry.getValue() >= 500L) {
                    iterator.remove();
                    continue;
                }
                RenderUtil.drawBlockBoundingBox(position, 1.0, 106, 13, 173, 255, 1.5F);
                RenderUtil.drawBlockBox(position, 1.0, 106, 13, 173);
            }
        } finally {
            RenderUtil.disableRenderState();
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.clearTracking();
    }

    @Override
    public void onDisabled() {
        this.clearTracking();
    }

    private void clearTracking() {
        this.armoredPlayers.clear();
        this.lastHeldItems.clear();
        this.obsidianPositions.clear();
    }

    private boolean isNextToBed(BlockPos blockPos) {
        for (EnumFacing facing : EnumFacing.values()) {
            if (mc.theWorld.getBlockState(blockPos.offset(facing)).getBlock() instanceof BlockBed) {
                return true;
            }
        }
        return false;
    }

    private String getItemType(ItemStack item) {
        if (item == null || item.getItem() == null) {
            return null;
        }
        if (item.getItem() instanceof ItemEnderPearl && this.enderPearl.getValue()) {
            return "&7an &3Ender Pearl";
        }
        if (item.getItem().getUnlocalizedName().contains("tile.obsidian") && this.obsidian.getValue()) {
            return "&dObsidian";
        }
        if (item.getItem() instanceof ItemFireball && this.fireball.getValue()) {
            return "&7a &6Fireball";
        }
        return null;
    }

    private void handleAlert(String itemType, String name, int distance) {
        ChatUtil.sendFormatted("&eAlert: &r" + name + " &7is holding " + itemType + " &7(&d" + distance + "m&7)");
        this.ping();
    }

    private void ping() {
        if (this.shouldPing.getValue()) {
            mc.thePlayer.playSound("note.pling", 1.0F, 1.0F);
        }
    }
}
