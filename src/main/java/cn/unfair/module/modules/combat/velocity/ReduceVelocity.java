package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.management.BadPacketManager;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.player.PlayerUtil;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;

public class ReduceVelocity extends SubModule {
    public static final BooleanProperty attack = new BooleanProperty("Attack Before Reduce", true);
    private static final Minecraft mc = Minecraft.getMinecraft();
    public boolean knockback = false;

    public ReduceVelocity() {
        super("Reduce");
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isReadyToReduce(event)) return;

        EntityPlayer target = findTarget();
        if (target != null) {
            reduce(target);
        }
        knockback = false;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null || !isEnabled()) return;
        if (event.getType() != EventType.RECEIVE || event.isCancelled()) return;
        if (!(event.getPacket() instanceof S12PacketEntityVelocity packet)) return;
        if (packet.getEntityID() != mc.thePlayer.getEntityId()) return;

        knockback = true;
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

    private boolean isReadyToReduce(UpdateEvent event) {
        return isEnabled()
                && mc.theWorld != null
                && mc.thePlayer != null
                && event.getType() == EventType.PRE
                && knockback
                && !BadPacketManager.bad()
                && !PlayerUtil.isInLiquidOrWeb()
                && MoveUtil.isForwardPressed()
                && mc.thePlayer.isSprinting();
    }

    private EntityPlayer findTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() instanceof EntityPlayer player) {
            return player.isEntityAlive() ? player : null;
        }

        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(
                new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 3.0F);
        if (result != null
                && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                && result.entityHit instanceof EntityPlayer player
                && player.isEntityAlive()) {
            return player;
        }
        return null;
    }

    private void reduce(EntityPlayer target) {
        if (attack.getValue()) {
            AttackOrder.sendFixedPacketAttackAndSwing(target);
        }

        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
    }
}
