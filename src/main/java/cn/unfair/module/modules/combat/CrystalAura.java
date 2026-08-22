package cn.unfair.module.modules.combat;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.management.BadPacketManager;
import cn.unfair.module.Module;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.util.AxisAlignedBB;

import java.util.Comparator;

public class CrystalAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double RANGE = 3.0D;
    private static final int ROTATION_PRIORITY = 180;

    private EntityEnderCrystal target;

    public CrystalAura() {
        super("CrystalAura", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            this.target = null;
            return;
        }
        if (event.getType() != EventType.PRE) {
            return;
        }

        AxisAlignedBB searchBox = mc.thePlayer.getEntityBoundingBox().expand(RANGE, RANGE, RANGE);
        this.target = mc.theWorld.getEntitiesWithinAABB(EntityEnderCrystal.class, searchBox)
                .stream()
                .filter(EntityEnderCrystal::isEntityAlive)
                .filter(crystal -> mc.thePlayer.getDistanceSqToEntity(crystal) <= RANGE * RANGE)
                .min(Comparator.comparingDouble(mc.thePlayer::getDistanceSqToEntity))
                .orElse(null);

        if (this.target == null) {
            return;
        }

        RotationUtil.RotationVec rotation = RayCastUtil.calculateRotationToEntity(this.target);
        if (!this.hitsTarget(RayCastUtil.rayCast(rotation, RANGE))) {
            this.target = null;
            return;
        }

        event.setRotation(rotation.x, rotation.y, ROTATION_PRIORITY);
        event.setPervRotation(rotation.x, ROTATION_PRIORITY);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.POST || !this.isTargetValid()) {
            return;
        }

        RotationUtil.RotationVec rotation = RayCastUtil.calculateRotationToEntity(this.target);
        if (!this.hitsTarget(RayCastUtil.rayCast(rotation, RANGE))) {
            this.target = null;
            return;
        }

        if (!BadPacketManager.bad()) {
            AttackOrder.sendFixedPacketAttack(this.target);
        }
        this.target = null;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.target = null;
    }

    @Override
    public void onDisabled() {
        this.target = null;
    }

    private boolean isTargetValid() {
        return mc.thePlayer != null
                && mc.theWorld != null
                && this.target != null
                && this.target.isEntityAlive()
                && mc.theWorld.loadedEntityList.contains(this.target)
                && mc.thePlayer.getDistanceSqToEntity(this.target) <= RANGE * RANGE;
    }

    private boolean hitsTarget(RayCastUtil.RayCastResult hit) {
        return hit != null
                && hit.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                && hit.entityHit == this.target;
    }
}
