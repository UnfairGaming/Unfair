package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.KnockbackEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;

public class VanillaVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final PercentProperty chance = new PercentProperty("chance", 100);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100);
    public final PercentProperty vertical = new PercentProperty("vertical", 100);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);

    private int chanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;

    public VanillaVelocity() {
        super("Vanilla");
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        Velocity velocity = (Velocity) Unfair.moduleManager.getModule(Velocity.class);
        if (mc.theWorld == null || mc.thePlayer == null) {
            pendingExplosion = false;
            allowNext = true;
            return;
        }
        if (velocity == null || !isEnabled() || event.isCancelled()) {
            pendingExplosion = false;
            allowNext = true;
            return;
        }
        if (!allowNext || !this.fakeCheck.getValue()) {
            allowNext = true;
            if (pendingExplosion) {
                pendingExplosion = false;
                if (this.explosionHorizontal.getValue() > 0) {
                    event.setX(event.getX() * (double) this.explosionHorizontal.getValue() / 100.0);
                    event.setZ(event.getZ() * (double) this.explosionHorizontal.getValue() / 100.0);
                } else {
                    event.setX(mc.thePlayer.motionX);
                    event.setZ(mc.thePlayer.motionZ);
                }
                if (this.explosionVertical.getValue() > 0) {
                    event.setY(event.getY() * (double) this.explosionVertical.getValue() / 100.0);
                } else {
                    event.setY(mc.thePlayer.motionY);
                }
            } else {
                chanceCounter = chanceCounter % 100 + this.chance.getValue();
                if (chanceCounter >= 100) {
                    if (this.horizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) this.horizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) this.horizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (this.vertical.getValue() > 0) {
                        event.setY(event.getY() * (double) this.vertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        Velocity velocity = (Velocity) Unfair.moduleManager.getModule(Velocity.class);
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        if (velocity == null || !isEnabled() || event.getType() != EventType.RECEIVE || event.isCancelled()) {
            return;
        }
        if (event.getPacket() instanceof S19PacketEntityStatus packet) {
            if (packet.getEntity(mc.theWorld) == mc.thePlayer && packet.getOpCode() == 2) {
                allowNext = false;
            }
        } else if (event.getPacket() instanceof S27PacketExplosion packet) {
            if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                pendingExplosion = true;
                if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
                    event.setCancelled(true);
                }
            }
        }
    }
}
