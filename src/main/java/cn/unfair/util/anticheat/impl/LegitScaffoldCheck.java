package cn.unfair.util.anticheat.impl;

import cn.unfair.util.anticheat.ACPlayerData;
import cn.unfair.util.anticheat.AntiCheatCheck;
import cn.unfair.util.anticheat.AntiCheatManager;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;

public class LegitScaffoldCheck extends AntiCheatCheck {
    private static final int SWING_AGE_LIMIT = 10;
    private static final long FLAG_COOLDOWN_TICKS = 60L;

    public LegitScaffoldCheck() {
        super("Legit Scaffold", "Checks for 1-2 tick sneaks while looking down with a block.");
    }

    @Override
    public void onTick(AntiCheatManager manager, ACPlayerData data) {
        EntityPlayer player = data.getPlayer();
        if (player == null || player == Minecraft.getMinecraft().thePlayer) return;
        // crouch edges/durations are tracked in AntiCheatManager.update()
        if (player.swingProgressInt == 1) {
            data.lastScaffoldSwingTick = data.currentTick;
        }
        if (!isScaffold(player)) return;
        if (data.lastCrouchStartTick == 0L || data.lastCrouchEndTick == 0L) return;

        int crouchDuration = (int) (data.lastCrouchEndTick - data.lastCrouchStartTick);
        boolean quickCrouch = crouchDuration >= 1 && crouchDuration <= 2;
        boolean swingTiming = data.lastScaffoldSwingTick >= data.lastCrouchEndTick
                && data.lastScaffoldSwingTick <= data.lastCrouchEndTick + 3L
                && data.currentTick - data.lastScaffoldSwingTick <= SWING_AGE_LIMIT;
        boolean consistent = data.crouchDurations.size() >= 3
                && data.crouchDurations.get(0) <= 3
                && data.crouchDurations.get(1) <= 3
                && data.crouchDurations.get(2) <= 3;
        if (!quickCrouch || !swingTiming || !consistent) return;
        if (data.lastScaffoldFlagTick != 0L
                && data.currentTick - data.lastScaffoldFlagTick < FLAG_COOLDOWN_TICKS) return;

        manager.flag(data, this, "micro-sneak: " + crouchDuration + "t, swing: "
                + (data.currentTick - data.lastScaffoldSwingTick) + "t", 1.0D);
        data.lastScaffoldFlagTick = data.currentTick;
        resetState(data);
    }

    private boolean isScaffold(EntityPlayer player) {
        return player.rotationPitch >= 60.0F && player.onGround
                && player.getHeldItem() != null && player.getHeldItem().getItem() instanceof ItemBlock;
    }

    private void resetState(ACPlayerData data) {
        data.lastScaffoldSwingTick = 0L;
        data.lastScaffoldFlagTick = 0L;
        data.crouchDurations.clear();
        data.lastCrouchStartTick = 0L;
        data.lastCrouchEndTick = 0L;
    }
}
