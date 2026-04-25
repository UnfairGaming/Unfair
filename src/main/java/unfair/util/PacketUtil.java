package unfair.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.ThreadQuickExitException;
import net.minecraft.network.play.INetHandlerPlayClient;

import java.util.ArrayList;
import java.util.List;

public class PacketUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static List<Packet<INetHandlerPlayClient>> skipReceiveEvent = new ArrayList<>();

    public static void sendPacket(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet);
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        mc.getNetHandler().getNetworkManager().sendPacket(packet, null);
    }
    public static void receivePacketNoEvent(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            skipReceiveEvent.add(casted);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }


    public static void receivePacket(Packet<?> packet) {
        if (packet == null)
            return;
        try {
            Packet<INetHandlerPlayClient> casted = castPacket(packet);
            casted.processPacket(mc.getNetHandler());
        } catch (ThreadQuickExitException ignored) {
        }
    }
    @SuppressWarnings("unchecked")
    public static <H extends INetHandler> Packet<H> castPacket(Packet<?> packet) throws ClassCastException {
        return (Packet<H>) packet;
    }
}
