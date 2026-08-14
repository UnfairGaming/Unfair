package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.util.Vec3;

import java.awt.*;

public class Ambience extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty timeMode = new ModeProperty("Mode", 2, new String[]{"None", "Normal", "Custom", "Day", "Dusk", "Night", "Dynamic"});
    public final IntProperty customWorldTime = new IntProperty("Time", 6, 0, 24, () -> this.timeMode.getValue() == 2);
    public final IntProperty changeWorldTimeSpeed = new IntProperty("Time Speed", 150, 10, 500, () -> this.timeMode.getValue() == 1);
    public final IntProperty dynamicSpeed = new IntProperty("Dynamic Speed", 20, 1, 50, () -> this.timeMode.getValue() == 6);

    public final ModeProperty weatherMode = new ModeProperty("Weather Mode", 0, new String[]{"None", "Sun", "Rain", "Thunder"});
    public final FloatProperty weatherStrength = new FloatProperty("Weather Strength", 1.0F, 0.0F, 1.0F, () -> this.weatherMode.getValue() != 0);

    public final BooleanProperty worldColor = new BooleanProperty("World Color", false);
    public final ColorProperty color = new ColorProperty("Color", new Color(0, 90, 255).getRGB(), this.worldColor::getValue);

    private long time;

    public Ambience() {
        super("Ambience", false, true);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.theWorld == null) {
            return;
        }

        switch (this.timeMode.getValue()) {
            case 1:
                this.time = (this.time + this.changeWorldTimeSpeed.getValue()) % 24000L;
                mc.theWorld.setWorldTime(this.time);
                break;
            case 2:
                mc.theWorld.setWorldTime(this.customWorldTime.getValue() * 1000L);
                break;
            case 3:
                mc.theWorld.setWorldTime(2000L);
                break;
            case 4:
                mc.theWorld.setWorldTime(13050L);
                break;
            case 5:
                mc.theWorld.setWorldTime(16000L);
                break;
            case 6:
                this.time = this.time < 24000L ? this.time + this.dynamicSpeed.getValue() : 0L;
                mc.theWorld.setWorldTime(this.time);
                break;
            default:
                break;
        }

        float strength = Math.max(0.0F, Math.min(1.0F, this.weatherStrength.getValue()));
        switch (this.weatherMode.getValue()) {
            case 1:
                mc.theWorld.setRainStrength(0.0F);
                mc.theWorld.setThunderStrength(0.0F);
                break;
            case 2:
                mc.theWorld.setRainStrength(strength);
                mc.theWorld.setThunderStrength(0.0F);
                break;
            case 3:
                mc.theWorld.setRainStrength(strength);
                mc.theWorld.setThunderStrength(strength);
                break;
            default:
                break;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.RECEIVE) {
            return;
        }

        Packet<?> packet = event.getPacket();
        if (this.timeMode.getValue() != 0 && packet instanceof S03PacketTimeUpdate) {
            event.setCancelled(true);
            return;
        }

        if (this.weatherMode.getValue() != 0 && packet instanceof S2BPacketChangeGameState) {
            int gameState = ((S2BPacketChangeGameState) packet).getGameState();
            if (gameState >= 7 && gameState <= 8) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onDisabled() {
        this.time = 0L;
    }

    @Override
    public String[] getSuffix() {
        return this.timeMode.getValue() == 0 ? new String[0] : new String[]{this.timeMode.getDisplayModeString()};
    }

    public static Vec3 getWorldColorVec() {
        if (mc == null || mc.theWorld == null || cn.unfair.Unfair.moduleManager == null) {
            return null;
        }

        Module module = cn.unfair.Unfair.moduleManager.getModule(Ambience.class);
        if (!(module instanceof Ambience ambience) || !ambience.isEnabled() || !ambience.worldColor.getValue()) {
            return null;
        }

        int rgb = ambience.color.getValue();
        return new Vec3(
                (rgb >> 16 & 255) / 255.0D,
                (rgb >> 8 & 255) / 255.0D,
                (rgb & 255) / 255.0D
        );
    }
}
