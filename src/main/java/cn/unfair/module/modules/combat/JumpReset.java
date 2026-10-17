package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.player.PlayerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;

public class JumpReset extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty chance = new PercentProperty("Chance", 80);
    public final BooleanProperty minimizeVertical = new BooleanProperty("Minimize vertical", false);
    public final BooleanProperty onlyWhileTargeting = new BooleanProperty("Only while targeting", false);
    private boolean pending;
    private boolean explosion;
    private double kbX;
    private double kbZ;

    public JumpReset() {
        super("JumpReset", false);
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || event.isCancelled() || mc.thePlayer == null) {
            return;
        }
        if (event.getPacket() instanceof S12PacketEntityVelocity velocity) {
            if (velocity.getEntityID() != mc.thePlayer.getEntityId()) {
                return;
            }
            if (explosion) {
                return;
            }
            if (velocity.getMotionY() <= 0 || velocity.getMotionY() >= 4500) {
                return;
            }
            if (minimizeVertical.getValue() && velocity.getMotionY() < 3000) {
                return;
            }
            kbX = velocity.getMotionX() / 8000.0D;
            kbZ = velocity.getMotionZ() / 8000.0D;
            pending = true;
        } else if (event.getPacket() instanceof S27PacketExplosion packet) {
            if (mc.thePlayer.getDistanceSq(packet.getX(), packet.getY(), packet.getZ()) <= 36.0D) {
                explosion = true;
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        explosion = false;
    }

    @EventTarget
    public void onLivingUpdate(LivingUpdateEvent event) {
        if (!pending) {
            return;
        }
        pending = false;
        if (!mc.thePlayer.onGround || !mc.thePlayer.isSprinting()) {
            return;
        }
        if (PlayerUtil.isInLiquidOrWeb() || mc.thePlayer.isRiding() || mc.thePlayer.isPotionActive(Potion.jump)) {
            return;
        }
        if (onlyWhileTargeting.getValue() && !targetMatches()) {
            return;
        }
        if (chance.getValue() != 100 && Math.random() >= chance.getValue() / 100.0D) {
            return;
        }
        if (!helps()) {
            return;
        }
        mc.thePlayer.movementInput.jump = true;
    }

    private boolean targetMatches() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && matches(killAura.getTarget())) {
            return true;
        }
        AimAssist aimAssist = (AimAssist) Unfair.moduleManager.getModule(AimAssist.class);
        if (aimAssist != null && matches(aimAssist.getCurrentTarget())) {
            return true;
        }
        return mc.objectMouseOver != null && matches(mc.objectMouseOver.entityHit);
    }

    private static boolean matches(Entity entity) {
        return entity instanceof EntityPlayer || entity instanceof EntityMob || entity instanceof EntityGolem || entity instanceof EntitySlime;
    }

    private boolean helps() {
        if (kbX == 0.0D && kbZ == 0.0D) {
            return false;
        }
        double kb = Math.sqrt(kbX * kbX + kbZ * kbZ);
        float yaw = MathHelper.wrapAngleTo180_float(MoveUtil.getMoveYaw());
        double after = withJump(kbX, kbZ, yaw);
        return (kb - after) / kb >= 0.03D;
    }

    private static double withJump(double x, double z, float yaw) {
        double r = Math.toRadians(yaw);
        double nx = x - Math.sin(r) * 0.2D;
        double nz = z + Math.cos(r) * 0.2D;
        return Math.sqrt(nx * nx + nz * nz);
    }

    @Override
    public void onDisabled() {
        pending = false;
        explosion = false;
    }
}