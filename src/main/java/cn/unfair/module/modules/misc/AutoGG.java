package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S45PacketTitle;

public class AutoGG extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private final TimerUtil timer = new TimerUtil();

    public AutoGG() {
        super("AutoGG", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) {
            return;
        }

        if (!(event.getPacket() instanceof S45PacketTitle)) {
            return;
        }

        S45PacketTitle packet = (S45PacketTitle) event.getPacket();
        if (packet.getType() != S45PacketTitle.Type.TITLE || packet.getMessage() == null) {
            return;
        }

        String title = packet.getMessage().getUnformattedText();
        if(!title.toUpperCase().contains("VICTORY") && !title.contains("胜利")) {
            return;
        }

        if (!this.timer.hasTimeElapsed(15000L)) {
            return;
        }
        this.timer.reset();

        mc.addScheduledTask(() -> {
            if (this.isEnabled()) {
                ChatUtil.sendMessage("GG");
            }
        });
    }
}