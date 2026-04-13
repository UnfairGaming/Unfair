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
    public final BooleanProperty reduce = new BooleanProperty("reduce", true, () -> this.mode.getValue() == 1);
    public final BooleanProperty delay = new BooleanProperty("delay (only bw)", false, () -> this.mode.getValue() == 1 && !this.airBuffer.getValue());
    public final IntProperty delayTicks = new IntProperty("delay-ticks", 1, 1, 5, () -> this.mode.getValue() == 1 && this.delay.getValue() && !this.airBuffer.getValue());
    public final BooleanProperty airBuffer = new BooleanProperty("air-buffer (only bw)", true, () -> this.mode.getValue() == 1 && !this.delay.getValue());
    public final PercentProperty chance = new PercentProperty("chance", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty horizontal = new PercentProperty("horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty vertical = new PercentProperty("vertical", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionHorizontal = new PercentProperty("explosions-horizontal", 100, () -> this.mode.getValue() == 0);
    public final PercentProperty explosionVertical = new PercentProperty("explosions-vertical", 100, () -> this.mode.getValue() == 0);
    public final BooleanProperty fakeCheck = new BooleanProperty("fake-check", true);
    public final BooleanProperty debug = new BooleanProperty("debug", false);
    public boolean knockback = false;
    private int chanceCounter = 0;
    private boolean pendingExplosion = false;
    private boolean allowNext = true;
    private boolean delayFlag = false;
    private boolean Motion = false;

    public Velocity() {
        super("Velocity", false, false);
    }

    private boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @EventTarget
    public void onKnockback(KnockbackEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            this.pendingExplosion = false;
            this.allowNext = true;
            return;
        }
        if (this.mode.getValue() == 0) {
            if (!this.allowNext || !(Boolean) this.fakeCheck.getValue()) {
                this.allowNext = true;
                if (this.pendingExplosion) {
                    this.pendingExplosion = false;
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
                    this.chanceCounter = this.chanceCounter % 100 + this.chance.getValue();
                    if (this.chanceCounter >= 100) {
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
        if (this.mode.getValue() == 0) return;
        if (event.getType() == EventType.POST) {
            if (this.delayFlag && ((delay.getValue()
                    && (this.isInLiquidOrWeb() || Unfair.delayManager.getDelay() >= (long) this.delayTicks.getValue()))
                    || (airBuffer.getValue() && mc.thePlayer.onGround && this.delayFlag))) {
                dbg(Unfair.clientName + "Delay/Buffer " + Unfair.delayManager.getDelay() + " Ticks");
                Unfair.delayManager.setDelayState(false, DelayModules.VELOCITY);
                this.delayFlag = false;
            }
        }
        if (this.reduce.getValue()) {
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

            if (!Motion) {
                Motion = true;
            }

            EventManager.call(new AttackEvent(target));
            mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
            mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, C02PacketUseEntity.Action.ATTACK));

            mc.thePlayer.motionX *= 0.6;
            mc.thePlayer.motionZ *= 0.6;
            mc.thePlayer.setSprinting(false);

            dbg(Unfair.clientName + "Reduce 40%");

            knockback = false;
            Motion = false;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && event.getType() == EventType.RECEIVE && !event.isCancelled()) {
            if (event.getPacket() instanceof S12PacketEntityVelocity) {
                S12PacketEntityVelocity packet = (S12PacketEntityVelocity) event.getPacket();
                if (packet.getEntityID() == mc.thePlayer.getEntityId()) {
                    LongJump longJump = (LongJump) Unfair.moduleManager.modules.get(LongJump.class);
                    if (this.mode.getValue() == 1
                            && !this.delayFlag
                            && !this.isInLiquidOrWeb()
                            && !this.pendingExplosion
                            && (!this.allowNext || !(Boolean) this.fakeCheck.getValue())
                            && (!longJump.isEnabled() || !longJump.canStartJump())) {
                        if ((this.airBuffer.getValue() && !mc.thePlayer.onGround) || (this.delay.getValue() && mc.thePlayer.onGround)) {
                            Unfair.delayManager.setDelayState(true, DelayModules.VELOCITY);
                            dbg(Unfair.clientName + "Delay/Buffer Active");
                            Unfair.delayManager.delayedPacket.offer(packet);
                            event.setCancelled(true);
                            this.delayFlag = true;
                        }
                    }
                }
            } else if (!(event.getPacket() instanceof S27PacketExplosion)) {
                if (event.getPacket() instanceof S19PacketEntityStatus) {
                    S19PacketEntityStatus packet = (S19PacketEntityStatus) event.getPacket();
                    Entity entity = packet.getEntity(mc.theWorld);
                    if (entity != null && entity.equals(mc.thePlayer) && packet.getOpCode() == 2) {
                        this.allowNext = false;
                    }
                }
            } else if (this.mode.getValue() == 0) {
                S27PacketExplosion packet = (S27PacketExplosion) event.getPacket();
                if (packet.func_149149_c() != 0.0F || packet.func_149144_d() != 0.0F || packet.func_149147_e() != 0.0F) {
                    this.pendingExplosion = true;
                    if (this.explosionHorizontal.getValue() == 0 || this.explosionVertical.getValue() == 0) {
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
        this.onDisabled();
    }

    public void dbg(String msg) {
        if (debug.getValue()) ChatUtil.sendFormatted(msg);
    }

    @Override
    public void onEnabled() {
        Motion = false;
        knockback = false;
    }

    @Override
    public void onDisabled() {
        this.pendingExplosion = false;
        this.allowNext = true;
        Motion = false;
        knockback = false;
    }

    @Override
    public void verifyValue(String name) {
        if (this.delay.getName().equals(name) && this.delay.getValue()) {
            this.airBuffer.setValue(false);
        } else if (this.airBuffer.getName().equals(name) && this.airBuffer.getValue()) {
            this.delay.setValue(false);
        }
    }

    @Override
    public String[] getSuffix() {
        if (this.mode.getValue() == 0) {
            return new String[]{
                    String.format("%d%%", this.horizontal.getValue()),
                    String.format("%d%%", this.vertical.getValue())
            };
        } else {
            return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
        }
    }
}