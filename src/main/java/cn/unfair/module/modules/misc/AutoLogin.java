package cn.unfair.module.modules.misc;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.TextProperty;
import cn.unfair.util.client.ChatUtil;
import cn.unfair.util.client.TimerUtil;
import net.minecraft.network.play.server.S02PacketChat;

public class AutoLogin extends Module {
    private final TextProperty password = new TextProperty("Password", "Un1336IsCute");
    private final FloatProperty delay = new FloatProperty("Delay", 5.0F, 0.0F, 20.0F);

    private final TimerUtil timer = new TimerUtil();
    private boolean receivedRegister;
    private boolean receivedLogin;
    private boolean useShortRegisterCommand;

    public AutoLogin() {
        super("AutoLogin", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;

        if (event.getType() != EventType.PRE || !this.timer.hasTimeElapsed((long) (this.delay.getValue() * 50.0F))) {
            return;
        }

        if (this.receivedRegister) {
            String command = this.useShortRegisterCommand ? "/reg " : "/register ";
            ChatUtil.sendMessage(command + this.password.getValue() + " " + this.password.getValue());
            this.receivedRegister = false;
        } else if (this.receivedLogin) {
            ChatUtil.sendMessage("/login " + this.password.getValue());
            this.receivedLogin = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;

        if (event.getType() != EventType.RECEIVE || !(event.getPacket() instanceof S02PacketChat)) {
            return;
        }

        String message = ((S02PacketChat) event.getPacket()).getChatComponent().getUnformattedText();
        if (message.contains("/reg")) {
            this.useShortRegisterCommand = true;
            this.receivedRegister = true;
            this.timer.reset();
        } else if (message.contains("/register")) {
            this.useShortRegisterCommand = false;
            this.receivedRegister = true;
            this.timer.reset();
        }

        if (message.contains("/login") || message.contains("login to proceed")) {
            this.receivedLogin = true;
            this.timer.reset();
        }

        if (this.receivedRegister && this.receivedLogin && message.contains("already registered")) {
            this.receivedRegister = false;
            this.timer.reset();
        }
    }

    @Override
    public void onDisabled() {
        this.receivedRegister = false;
        this.receivedLogin = false;
        this.useShortRegisterCommand = false;
        this.timer.reset();
    }
}
