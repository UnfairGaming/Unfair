package unfair.mixin;

import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import unfair.event.EventManager;
import unfair.events.AttackEvent;
import unfair.events.CancelUseEvent;
import unfair.events.WindowClickEvent;

@SideOnly(Side.CLIENT)
@Mixin({PlayerControllerMP.class})
public abstract class MixinPlayerControllerMP {

    @Inject(
            method = "attackEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/PlayerControllerMP;syncCurrentPlayItem()V"))
    private void attackEntity(
            EntityPlayer entityPlayer, Entity targetEntity, CallbackInfo callbackInfo
    ) {
        AttackEvent event = new AttackEvent(targetEntity);
        EventManager.call(event);
    }

    @Inject(
            method = {"windowClick"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void windowClick(
            int windowId, int slotId, int mouseButtonClicked, int mode, EntityPlayer entityPlayer, CallbackInfoReturnable<ItemStack> callbackInfoReturnable
    ) {
        WindowClickEvent event = new WindowClickEvent(windowId, slotId, mouseButtonClicked, mode);
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfoReturnable.cancel();
        }
    }

    @Inject(
            method = {"onStoppedUsingItem"},
            at = {@At("HEAD")},
            cancellable = true
    )
    private void onStoppedUsingItem(CallbackInfo callbackInfo) {
        CancelUseEvent event = new CancelUseEvent();
        EventManager.call(event);
        if (event.isCancelled()) {
            callbackInfo.cancel();
        }
    }
}
