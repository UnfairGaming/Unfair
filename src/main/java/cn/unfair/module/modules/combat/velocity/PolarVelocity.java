package cn.unfair.module.modules.combat.velocity;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.module.modules.combat.Velocity;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.BadPacketUtil;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class PolarVelocity extends SubModule {

    public final static ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Reduce", "Cancel10%"});

    private static final Minecraft mc = Minecraft.getMinecraft();

    private boolean kb;
    private double sb;

    public PolarVelocity() {
        super("Polar");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (!kb || !isEnabled() || BadPacketUtil.bad() || event.getType() != EventType.PRE) return;
        if (Velocity.isInLiquidOrWeb()) return;
        switch (mode.getValue()) {
            case 0: {
                // set in EntityPlayer
                break;
            }

            case 1: {
                // cancel in Packet Event
                break;
            }
            default:
                throw new IllegalStateException("Unexpected value: " + mode.getValue());
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (Velocity.isInLiquidOrWeb()) return;
        if (this.isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity packet) {
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    kb = true;
                    switch (mode.getValue()) {
                        case 0: {
                            // set in EntityPlayer
                            break;
                        }

                        case 1: {
                            if (event.getType() == EventType.RECEIVE) {
                                RayCastUtil.RayCastResult result = RayCastUtil.rayCast(new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 2.9F);
                                EntityLivingBase target = KillAura.target.getEntity();
                                if (target != null
                                        && result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY && result.entityHit instanceof EntityPlayer
                                        && RotationUtil.distanceToEntity(target) > 1
                                        && sb < 1
                                ) {
                                    event.setCancelled(true);
                                    sb++;
                                } else {
                                    sb = Math.max(0, sb - 0.1);
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }
}
