package cn.unfair.util.anticheat;

import net.minecraft.network.Packet;

public abstract class AntiCheatCheck {
    private final String name;
    private final String description;

    protected AntiCheatCheck(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public void onTick(AntiCheatManager manager, ACPlayerData data) {
    }

    public void onPacket(AntiCheatManager manager, ACPlayerData data, Packet<?> packet) {
    }

    protected boolean isMovementPacket(Packet<?> packet) {
        return packet instanceof net.minecraft.network.play.server.S14PacketEntity.S15PacketEntityRelMove
                || packet instanceof net.minecraft.network.play.server.S14PacketEntity.S16PacketEntityLook
                || packet instanceof net.minecraft.network.play.server.S14PacketEntity.S17PacketEntityLookMove;
    }
}
