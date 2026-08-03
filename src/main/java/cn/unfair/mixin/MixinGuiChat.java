package cn.unfair.mixin;

import cn.unfair.event.EventManager;
import cn.unfair.events.ChatGUIEvent;
import net.minecraft.client.gui.GuiChat;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SideOnly(Side.CLIENT)
@Mixin(GuiChat.class)
public abstract class MixinGuiChat {
    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void drawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo callbackInfo) {
        EventManager.call(new ChatGUIEvent(mouseX, mouseY, partialTicks));
    }
}
