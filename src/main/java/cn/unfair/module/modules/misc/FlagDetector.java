package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.events.PacketEvent;
import cn.unfair.module.Module;
import cn.unfair.util.ChatUtil;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;

public class FlagDetector extends Module {
    public FlagDetector() {
        super("FlagDetector", false, true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled())
            return;

        if (event.getPacket() instanceof S08PacketPlayerPosLook) {
            ChatUtil.sendFormatted("&7[&cFlagDetector&7] &fServer flag detected (Lagback)!");
        }
    }
}