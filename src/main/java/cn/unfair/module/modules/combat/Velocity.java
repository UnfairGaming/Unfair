package cn.unfair.module.modules.combat;

import cn.unfair.module.modules.movement.Stuck;
import com.google.common.base.CaseFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.Potion;
import cn.unfair.Unfair;
import cn.unfair.enums.DelayModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.*;
import cn.unfair.mixin.IAccessorEntity;
import cn.unfair.module.Module;
import cn.unfair.module.modules.movement.LongJump;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;

import static cn.unfair.util.BadPacketUtil.bad;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int ROTATION_PRIORITY = 100;
    private static final int PREDICT_TICKS = 3;
    private static final int POST_TICKS = 2;

    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "Prediction"});
    public final PercentProperty chance = new PercentProperty("chance", 100, () -> mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100, () -> mode.getValue() == 0);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debug = new BooleanProperty("debug", false);

    private int chanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;

    private int predictTick = -1;
    private boolean predictSprinting;
    private boolean delaying;
    private int jumpResetTicks;
    private boolean rotating;
    private Entity target;

    public Velocity() {
        super("Velocity", false, false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!isEnabled() || event.isCancelled()) {
            pendingExplosion = false;
            allowNext = true;
            return;
        }
        if (mode.getValue() == 0) {
            if (!allowNext || !(Boolean) fakeCheck.getValue()) {
                allowNext = true;
                if (pendingExplosion) {
                    pendingExplosion = false;
                    if (explosionHorizontal.getValue() > 0) {
                        event.setX(event.getX() * (double) explosionHorizontal.getValue() / 100.0);
                        event.setZ(event.getZ() * (double) explosionHorizontal.getValue() / 100.0);
                    } else {
                        event.setX(mc.thePlayer.motionX);
                        event.setZ(mc.thePlayer.motionZ);
                    }
                    if (explosionVertical.getValue() > 0) {
                        event.setY(event.getY() * (double) explosionVertical.getValue() / 100.0);
                    } else {
                        event.setY(mc.thePlayer.motionY);
                    }
                } else {
                    chanceCounter = chanceCounter % 100 + chance.getValue();
                    if (chanceCounter >= 100) {
                        if (horizontal.getValue() > 0) {
                            event.setX(event.getX() * (double) horizontal.getValue() / 100.0);
                            event.setZ(event.getZ() * (double) horizontal.getValue() / 100.0);
                        } else {
                            event.setX(mc.thePlayer.motionX);
                            event.setZ(mc.thePlayer.motionZ);
                        }
                        if (vertical.getValue() > 0) {
                            event.setY(event.getY() * (double) vertical.getValue() / 100.0);
                        } else {
                            event.setY(mc.thePlayer.motionY);
                        }
                    }
                }
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled() || mode.getValue() != 1 || event.getType() != EventType.PRE) return;
        if (predictTick < 0) {
            rotating = false;
            return;
        }

        int tick = predictTick;
        predictTick++;
        rotating = true;

        if (tick < PREDICT_TICKS) {
            if (delaying) {
                if (tick == 0) {
                    mc.thePlayer.setSprinting(true);
                    dbg("Predict tick0: rotate + restore sprint");
                } else if (tick == 1) {
                    doReduce();
                } else if (tick == PREDICT_TICKS - 1) {
                    releaseDelay();
                }
            } else if (predictSprinting && tick == 0) {
                doReduce();
            }

            if (jumpResetTicks > 0) {
                doJumpReset();
                jumpResetTicks--;
            }
        } else {
            dbg("Post tick" + (tick - PREDICT_TICKS) + ": rotate + move");
        }

        if (target != null) {
            float[] rots = getRotationsToEntity(target);
            event.setRotation(rots[0], rots[1], ROTATION_PRIORITY);
            event.setPervRotation(rots[0], ROTATION_PRIORITY);
        }

        if (tick >= PREDICT_TICKS + POST_TICKS - 1) {
            if (delaying) releaseDelay();
            resetPredict();
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (mode.getValue() != 1 || !rotating) return;
        mc.thePlayer.movementInput.moveForward = 1.0F;
        mc.thePlayer.movementInput.moveStrafe = 0.0F;
        mc.thePlayer.setSprinting(true);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
                    if (mode.getValue() == 1
                            && predictTick < 0
                            && !isInLiquidOrWeb()
                            && !pendingExplosion
                            && (!allowNext || !(Boolean) fakeCheck.getValue())
                            && !Unfair.moduleManager.getModule(Stuck.class).isEnabled()
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        Entity found = findTarget();
                        if (found != null) {
                            target = found;
                            boolean grounded = mc.thePlayer.onGround;
                            predictSprinting = mc.thePlayer.isSprinting();
                            jumpResetTicks = grounded ? 3 : 0;

                            if (!predictSprinting) {
                                Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
                                Unfair.delayManager.delayedPacket.offer(packet);
                                event.setCancelled(true);
                                delaying = true;
                                KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
                                if (killAura != null) killAura.attackDisabled = true;
                                dbg("Predict delay active (non-sprint)");
                            } else {
                                dbg("Predict reduce (sprint)");
                            }
                            predictTick = 0;
                        }
                    }
                }
            } else if (!(event.getPacket() instanceof S27PacketExplosion)) {
                if (event.getPacket() instanceof S19PacketEntityStatus) {
                    S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                    Entity entity = packet.getEntity(mc.theWorld);
                    if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                        allowNext = false;
                    }
                }
            } else if (mode.getValue() == 0) {
                S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    pendingExplosion = true;
                    if (explosionHorizontal.getValue() == 0 || explosionVertical.getValue() == 0) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    private Entity findTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && killAura.getTarget() != null) {
            return killAura.getTarget();
        }
        EntityPlayer nearest = null;
        double nearestDist = 6.0;
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player.deathTime > 0) continue;
            double dist = player.getDistanceToEntity(mc.thePlayer);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = player;
            }
        }
        return nearest;
    }

    private float[] getRotationsToEntity(Entity entity) {
        RotationUtil.RotationVec rv = RayCastUtil.calculateRotationToEntity(entity);
        return new float[]{rv.x, rv.y};
    }

    private void doReduce() {
        if (target == null) return;
        if (bad()) return;
        mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
        mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));
        mc.thePlayer.motionX *= 0.6D;
        mc.thePlayer.motionZ *= 0.6D;
        mc.thePlayer.setSprinting(false);
        dbg("Reduce 40%");
    }

    private void doJumpReset() {
        if (mc.thePlayer.onGround && !isInLiquidOrWeb() && !mc.thePlayer.isPotionActive(Potion.jump)) {
            mc.thePlayer.movementInput.jump = true;
            dbg("JumpReset");
        }
    }

    private void releaseDelay() {
        Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null) killAura.attackDisabled = false;
        delaying = false;
        dbg("Delay released");
    }

    private void resetPredict() {
        predictTick = -1;
        target = null;
        jumpResetTicks = 0;
        delaying = false;
    }

    public void dbg(String msg) {
        if (debug.getValue()) ChatUtil.sendFormatted(msg);
    }

    @Override
    public void onEnabled() {
        resetPredict();
    }

    @Override
    public void onDisabled() {
        if (delaying) releaseDelay();
        pendingExplosion = false;
        allowNext = true;
        resetPredict();
    }

    @Override
    public String[] getSuffix() {
        if (mode.getValue() == 0) {
            return new String[]{
                    String.format("%d%%", horizontal.getValue()),
                    String.format("%d%%", vertical.getValue())
            };
        } else {
            return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, mode.getModeString())};
        }
    }
}
