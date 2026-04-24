package unfair.mixin;

import net.minecraft.network.play.server.S18PacketEntityTeleport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S18PacketEntityTeleport.class)
public interface IAccessorS18PacketEntityTeleport {
    @Accessor("entityId")
    int getEntityId();
}
