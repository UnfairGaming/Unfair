package unfair.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import unfair.management.altmanager.AltManagerGui;

@SideOnly(Side.CLIENT)
@Mixin({GuiMainMenu.class})
public abstract class MixinGuiMainMenu extends GuiScreen {

    private static final int ALT_MANAGER_BUTTON_ID = 9999;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void onInitGui(CallbackInfo ci) {
        // Add AltManager button in bottom right corner
        int buttonWidth = 100;
        int buttonHeight = 20;
        int x = this.width - buttonWidth - 5;
        int y = this.height - buttonHeight - 5;

        this.buttonList.add(new GuiButton(ALT_MANAGER_BUTTON_ID, x, y, buttonWidth, buttonHeight, "Alt Manager"));
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void onActionPerformed(GuiButton button, CallbackInfo ci) {
        if (button.id == ALT_MANAGER_BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new AltManagerGui());
            ci.cancel();
        }
    }
}
