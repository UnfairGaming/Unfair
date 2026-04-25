package unfair.module.modules.combat;

import com.google.common.base.CaseFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S27PacketExplosion;
import unfair.Unfair;
import unfair.enums.DelayModules;
import unfair.event.EventManager;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.*;
import unfair.mixin.IAccessorEntity;
import unfair.module.Module;
import unfair.module.modules.movement.LongJump;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.IntProperty;
import unfair.property.properties.ModeProperty;
import unfair.property.properties.PercentProperty;
import unfair.util.ChatUtil;
import unfair.util.MoveUtil;
import unfair.util.RayCastUtil;
import unfair.util.RotationUtil;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static boolean attack = false;
    private static boolean inventory = false;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"VANILLA", "Prediction"});
    public final BooleanProperty reduce = new BooleanProperty("reduce", true, () -> mode.getValue() == 1);
    public final BooleanProperty delay = new BooleanProperty("delay", false, () -> mode.getValue() == 1 && !this.airBuffer.getValue());
    public final IntProperty delayTicks = new IntProperty("delay-ticks", 1, 1, 5, () -> mode.getValue() == 1 && delay.getValue() && !this.airBuffer.getValue());
    //airBuffer is much better than delay in some low ping servers
    public final BooleanProperty airBuffer = new BooleanProperty("air-buffer", true, () -> mode.getValue() == 1 && !delay.getValue());
    public final PercentProperty chance = new PercentProperty("chance", 100, () -> mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100, () -> mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100, () -> mode.getValue() == 0);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debug = new BooleanProperty("debug", false);
    public boolean knockback = false;
    private int chanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean delayFlag = false;
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
        } else {
            // Prediction mode logic
        }
    }

    private boolean badPackets() {
        return (attack) || (inventory);
    }

    private void resetBadPackets() {
        attack = false;
        inventory = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!isEnabled()) return;
        if (mode.getValue() == 0) return;
        if (event.getType() == EventType.POST) {
            if (delayFlag && ((delay.getValue()
                    && (isInLiquidOrWeb() || Unfair.delayManager.getDelay() >= (long) delayTicks.getValue()))
                    || (airBuffer.getValue() && mc.thePlayer.onGround && delayFlag))) {
                dbg(Unfair.clientName + "Delay/Buffer " + Unfair.delayManager.getDelay() + " Ticks");
                Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
                delayFlag = false;
            }
        }
        if (reduce.getValue()) {
            // why reduce in the UpdateEvent? IDK.
            if (event.getType() != EventType.PRE) return;

            if (!knockback) return;

            if (badPackets()) return;

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
                RayCastUtil.RayCastResult result = RayCastUtil.rayCast(new RotationUtil.RotationVec(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch), 3.0009);
                if (result != null && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY && result.entityHit instanceof EntityPlayer) {
                    target = result.entityHit;
                }
            }

            EventManager.call(new AttackEvent(target));

            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));

            mc.thePlayer.motionX *= 0.6D;
            mc.thePlayer.motionZ *= 0.6D;

            mc.thePlayer.setSprinting(false);

            dbg(Unfair.clientName + "Reduce 40%");

            knockback = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
                    if (mode.getValue() == 1
                            && !delayFlag
                            && !isInLiquidOrWeb()
                            && !pendingExplosion
                            && (!allowNext || !(Boolean) fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        if ((airBuffer.getValue() && !mc.thePlayer.onGround) || (delay.getValue() && mc.thePlayer.onGround)) {
                            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
                            dbg(Unfair.clientName + "Delay/Buffer Active");
                            Unfair.delayManager.delayedPacket.offer(packet);
                            event.setCancelled(true);
                            delayFlag = true;
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
        if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) event.getPacket();
                if (velocityPacket.getEntityID() == mc.thePlayer.getEntityId()) {
                    knockback = true;
                }
            }
        }
        if (event.getType() == EventType.SEND && !event.isCancelled()) {
            if (event.getPacket() instanceof C02PacketUseEntity) {
                C02PacketUseEntity useEntity = (C02PacketUseEntity) event.getPacket();
                if (useEntity.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    attack = true;
                }
            } else if (event.getPacket() instanceof C0DPacketCloseWindow || event.getPacket() instanceof C0EPacketClickWindow ||
                    (event.getPacket() instanceof C16PacketClientStatus && ((C16PacketClientStatus) event.getPacket()).getStatus() == C16PacketClientStatus.EnumState.OPEN_INVENTORY_ACHIEVEMENT)) {
                inventory = true;
            } else if (event.getPacket() instanceof C03PacketPlayer) {
                resetBadPackets();
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        onDisabled();
    }

    public void dbg(String msg) {
        if (debug.getValue()) ChatUtil.sendFormatted(msg);
    }

    @Override
    public void onEnabled() {
        knockback = false;
    }

    @Override
    public void onDisabled() {
        pendingExplosion = false;
        allowNext = true;
        knockback = false;
    }

    @Override
    public void verifyValue(String name) {
        if (delay.getName().equals(name) && delay.getValue()) {
            airBuffer.setValue(false);
        } else if (airBuffer.getName().equals(name) && airBuffer.getValue()) {
            delay.setValue(false);
        }
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