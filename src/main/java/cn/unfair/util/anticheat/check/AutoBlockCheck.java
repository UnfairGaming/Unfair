package cn.unfair.util.anticheat.check;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AnticheatManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class AutoBlockCheck extends AntiCheatCheck {
    public AutoBlockCheck() {
        super("AutoBlock", "Checks for attacking while blocking.");
    }

    @Override
    public void onTick(AnticheatManager manager, ACPlayerData data) {
        EntityPlayer player = data.getPlayer();
        if (player == null || player == net.minecraft.client.Minecraft.getMinecraft().thePlayer) return;
        long now = System.currentTimeMillis();
        boolean swinging = data.swingProgress > 0.0F;

        if (swinging && (data.lastSwingDetected == 0L || now - data.lastSwingDetected > 100L)) {
            boolean blockingBefore = data.isBlocking
                    && data.lastBlockStartTime > 0L
                    && now - data.lastBlockStartTime >= 150L;
            data.swingHistory.add(new ACPlayerData.SwingData(now, blockingBefore));
            data.lastSwingDetected = now;
            if (data.swingHistory.size() > 20) data.swingHistory.remove(0);
        }

        for (ACPlayerData.SwingData swing : data.swingHistory) {
            if (swing.wasBlockingAfter != null) continue;
            long age = now - swing.time;
            if (age >= 150L && age <= 200L) {
                swing.wasBlockingAfter = data.isBlocking;
            } else if (age > 200L) {
                swing.wasBlockingAfter = false;
            }
        }

        List<ACPlayerData.SwingData> recent = new ArrayList<>();
        for (ACPlayerData.SwingData swing : data.swingHistory) {
            if (now - swing.time < 1000L && swing.wasBlockingAfter != null && isHoldingSword(player)) {
                recent.add(swing);
            }
        }
        int autoblocks = 0;
        for (ACPlayerData.SwingData swing : recent) {
            if (swing.wasBlockingBefore && Boolean.TRUE.equals(swing.wasBlockingAfter)) autoblocks++;
        }
        if (autoblocks >= 2) {
            String item = player.getHeldItem() == null ? "nothing" : player.getHeldItem().getDisplayName();
            manager.flag(data, this, "item: " + item + ", autoblks: " + autoblocks, 1.0D);
        }
    }

    private boolean isHoldingSword(EntityPlayer player) {
        return player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemSword;
    }
}
