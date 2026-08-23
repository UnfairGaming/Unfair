package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.module.Module;
import cn.unfair.util.client.ChatUtil;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class FlagDetector extends Module {
    public FlagDetector() {
        super("FlagDetector", false, true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) {
            return;
        }

        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            ChatUtil.sendFormatted("&7[&cFlagDetector&7] &fServer flag detected (Lagback)!");
        } else if (event.getPacket() instanceof S06PacketUpdateHealth packet) {
            int hunger = packet.getFoodLevel();
            if (hunger <= 0) {
                ChatUtil.sendFormatted(String.format(
                        "&7[&cFlagDetector&7] &fServer flag detected (Invalid Hunger: &c%d&f)!",
                        hunger
                ));
            }
        }
    }
}
