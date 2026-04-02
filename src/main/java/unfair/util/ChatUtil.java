package unfair.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import unfair.enums.ChatColors;

public class ChatUtil {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void send(IChatComponent iChatComponent) {
        if (ChatUtil.mc.thePlayer != null) {
            ChatUtil.mc.thePlayer.addChatMessage(iChatComponent);
        }
    }

    public static void sendFormatted(String string) {
        ChatUtil.send(new ChatComponentText(ChatColors.formatColor(string)));
    }

    public static void sendRaw(String string) {
        ChatUtil.send(new ChatComponentText(string));
    }

    public static void sendMessage(String string) {
        if (ChatUtil.mc.thePlayer != null) {
            ChatUtil.mc.thePlayer.sendChatMessage(string);
        }
    }
}
