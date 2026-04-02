package unfair.management;

import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

public class PlayerStateManager {
    public boolean attacking = false;
    public boolean digging = false;
    public boolean placing = false;
    public boolean swapping = false;
    public boolean swinging = false;

    public void handlePacket(Packet<?> packet) {
        if (packet instanceof C02PacketUseEntity) {
            this.attacking = true;
        }
        if (packet instanceof C07PacketPlayerDigging) {
            this.digging = true;
        }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            this.placing = true;
        }
        if (packet instanceof C09PacketHeldItemChange) {
            this.swapping = true;
        }
        if (packet instanceof C0APacketAnimation) {
            this.swinging = true;
        }
        if (packet instanceof C03PacketPlayer) {
            this.attacking = false;
            this.digging = false;
            this.placing = false;
            this.swapping = false;
            this.swinging = false;
        }
    }
}
