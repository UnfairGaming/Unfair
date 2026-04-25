package unfair.mixin;


import net.minecraft.network.play.server.S14PacketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(S14PacketEntity.class)
public interface S14PacketEntityAccessor {

    @Accessor("entityId")
    int getEntityId();
}
