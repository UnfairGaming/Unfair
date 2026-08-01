package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.mixin.IAccessorEntity;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.util.MoveUtil;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

import static cn.unfair.module.modules.combat.Velocity.isInLiquidOrWeb;
import static cn.unfair.util.BadPacketUtil.bad;

public class Reduce extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public boolean knockback = false;

    public Reduce() {
        super("Reduce");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;

        // why reduce in the UpdateEvent? IDK.
        if (event.getType() != EventType.PRE) return;

        if (!knockback) return;

        if (bad()) return;

        boolean isInWeb = ((IAccessorEntity) mc.thePlayer).getIsInWeb();
        if (isInWeb || isInLiquidOrWeb()) return;

        if (!MoveUtil.isForwardPressed() || !mc.thePlayer.isSprinting()) return;

        boolean noAura = false;

        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura == null || !killAura.isEnabled() || killAura.getTarget() == null) {
            noAura = true;
        }
        Entity target = null;

        if (!noAura) {
            target = killAura.getTarget();
        } else {
            RayCastUtil.RayCastResult result = RayCastUtil.rayCast(new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 3.0f);
            if (result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY && result.entityHit instanceof EntityPlayer) {
                target = result.entityHit;
            }
        }

        if (target != null) {
            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));

            mc.thePlayer.motionX *= 0.6D;
            mc.thePlayer.motionZ *= 0.6D;

            mc.thePlayer.setSprinting(false);

        }
        knockback = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled()) {
            if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
                if (event.getPacket() instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) event.getPacket();
                    if (velocityPacket.getEntityID() == mc.thePlayer.getEntityId()) {
                        knockback = true;
                    }
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    @Override
    public void onEnabled() {
        knockback = false;
    }

    @Override
    public void onDisabled() {
        knockback = false;
    }
}
