package unfair.command.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import unfair.Unfair;
import unfair.command.Command;
import unfair.enums.ChatColors;
import unfair.util.ChatUtil;

import java.util.ArrayList;
import java.util.Arrays;

public class ItemCommand extends Command {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public ItemCommand() {
        super(new ArrayList<>(Arrays.asList("itemname", "item")));
    }

    @Override
    public void runCommand(ArrayList<String> args) {
        ItemStack stack = mc.thePlayer.inventory.getCurrentItem();
        if (stack != null) {
            String display = stack.getDisplayName().replace('§', '&');
            String registryName = stack.getItem().getRegistryName();
            String compound = stack.hasTagCompound() ? stack.getTagCompound().toString().replace('§', '&') : "";
            ChatUtil.sendRaw(String.format("%s%s (%s) %s", ChatColors.formatColor(Unfair.clientName), display, registryName, compound));
        }
    }
}
