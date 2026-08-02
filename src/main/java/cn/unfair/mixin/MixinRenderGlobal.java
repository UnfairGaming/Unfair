package cn.unfair.mixin;

import cn.unfair.event.EventManager;
import cn.unfair.events.RenderEntityEvent;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(RenderGlobal.class)
public abstract class MixinRenderGlobal {
    @Inject(
            method = "renderEntities",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderHelper;enableStandardItemLighting()V")
    )
    private void renderEntities(Entity renderViewEntity, ICamera camera, float partialTicks, CallbackInfo callbackInfo) {
        EventManager.call(new RenderEntityEvent());
    }
}
