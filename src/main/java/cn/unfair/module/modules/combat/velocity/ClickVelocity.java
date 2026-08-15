package cn.unfair.module.modules.combat.velocity;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.SubModule;
import cn.unfair.module.modules.combat.KillAura;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.TeamUtil;
import cn.unfair.util.PlayerUtil;
import de.florianmichael.viamcp.fixes.AttackOrder;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import java.util.Comparator;
import java.util.concurrent.ThreadLocalRandom;

/** Repeats attacks during the velocity hurt window to reduce received knockback. */
public class ClickVelocity extends SubModule {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty minClicks = new IntProperty("Min Clicks", 3, 1, 20);
    public final IntProperty maxClicks = new IntProperty("Max Clicks", 5, 1, 20);
    public final IntProperty hurtTime = new IntProperty("Hurt Time To Click", 10, 0, 10);
    public final BooleanProperty whenFacingEnemyOnly = new BooleanProperty("When Facing Enemy Only", true);
    public final BooleanProperty ignoreBlocking = new BooleanProperty("Ignore Blocking", false);
    public final FloatProperty clickRange = new FloatProperty("Click Range", 3.0F, 1.0F, 6.0F);
    public final ModeProperty swingMode = new ModeProperty("Swing Mode", 1, new String[]{"Off", "Normal", "Packet"});

    public ClickVelocity() {
        super("Click");
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!isEnabled() || event.type() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            return;
        }

        if (mc.thePlayer.isDead || mc.thePlayer.hurtTime != hurtTime.getValue()) {
            return;
        }

        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (ignoreBlocking.getValue()
                && (mc.thePlayer.isBlocking() || killAura != null && killAura.isBlocking())) {
            return;
        }

        Entity target = findTarget();
        if (target == null) {
            return;
        }

        int min = Math.min(minClicks.getValue(), maxClicks.getValue());
        int max = Math.max(minClicks.getValue(), maxClicks.getValue());
        int clicks = ThreadLocalRandom.current().nextInt(min, max + 1);
        boolean wasSprinting = mc.thePlayer.isSprinting();

        for (int i = 0; i < clicks; i++) {
            // Keep the sprint state active while each attack is assembled. This is the
            // behavior the original Click mode relies on for sprint knockback.
            mc.thePlayer.setSprinting(true);
            attack(target);
        }

        mc.thePlayer.setSprinting(wasSprinting);
    }

    private void attack(Entity target) {
        AttackEvent event = new AttackEvent(target);
        EventManager.call(event);
        if (event.isCancelled()) {
            return;
        }

        mc.playerController.syncCurrentPlayItem();
        switch (swingMode.getValue()) {
            case 0:
                AttackOrder.sendFixedPacketAttackWithoutSwing(target);
                break;
            case 1:
                AttackOrder.sendFixedPacketAttack(target);
                break;
            case 2:
                AttackOrder.sendFixedPacketAttackAndSwing(target);
                break;
            default:
                throw new IllegalStateException("Unexpected swing mode: " + swingMode.getValue());
        }
        PlayerUtil.attackEntity(target);
    }

    private Entity findTarget() {
        Entity pointed = mc.objectMouseOver == null ? null : mc.objectMouseOver.entityHit;
        if (isValidTarget(pointed) && RotationUtil.distanceToEntity(pointed) <= clickRange.getValue()) {
            return pointed;
        }

        if (whenFacingEnemyOnly.getValue()) {
            RayCastUtil.RayCastResult result = RayCastUtil.rayCast(
                    new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch),
                    clickRange.getValue()
            );
            return result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                    && isValidTarget(result.entityHit) ? result.entityHit : null;
        }

        return mc.theWorld.loadedEntityList.stream()
                .filter(this::isValidTarget)
                .filter(entity -> RotationUtil.distanceToEntity(entity) <= clickRange.getValue())
                .min(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .orElse(null);
    }

    private boolean isValidTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase living)) {
            return false;
        }
        if (entity instanceof EntityPlayer player
                && (TeamUtil.isFriend(player) || TeamUtil.shouldBlockTarget(player))) {
            return false;
        }
        return entity != mc.thePlayer
                && !entity.isDead
                && living.isEntityAlive();
    }
}
