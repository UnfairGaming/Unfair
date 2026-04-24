package unfair.mixin;

import net.minecraft.network.play.server.S19PacketEntityStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S19PacketEntityStatus.class)
public interface IAccessorS19PacketEntityStatus {
    @Accessor("entityId")
    int getEntityId();
}
