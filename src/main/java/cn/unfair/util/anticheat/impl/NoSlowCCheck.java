package cn.unfair.util.anticheat.impl;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AnticheatManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemSword;

public class NoSlowCCheck extends AntiCheatCheck {
    public NoSlowCCheck() {
        super("NoSlow C", "Checks sprint in item use.");
    }

    @Override
    public void onTick(AnticheatManager manager, ACPlayerData data) {
        EntityPlayer player = data.getPlayer();
        if (player == null || player == net.minecraft.client.Minecraft.getMinecraft().thePlayer) return;

        boolean slowdownItem = isUsingSlowdownItem(player);
        boolean activeNow = slowdownItem && data.isSprinting && !player.isRiding();
        if (activeNow && !data.noSlowActive) {
            data.noSlowStartTime = System.currentTimeMillis();
            data.noSlowActive = true;
        } else if (!activeNow) {
            data.noSlowActive = false;
            data.noSlowStartTime = 0L;
        }
        if (data.noSlowActive) {
            long duration = System.currentTimeMillis() - data.noSlowStartTime;
            if (duration > 200L) {
                manager.flag(data, this, "duration: " + duration + "ms", 1.0D);
                data.noSlowActive = false;
                data.noSlowStartTime = 0L;
            }
        }
    }

    private boolean isUsingSlowdownItem(EntityPlayer player) {
        if (player.isBlocking() && player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemSword) {
            return true;
        }
        return player.isUsingItem() && player.getHeldItem() != null
                && (player.getHeldItem().getItem() instanceof ItemFood
                || player.getHeldItem().getItem() instanceof ItemBow);
    }
}
