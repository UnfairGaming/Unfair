//Deobfuscated with https://github.com/SimplyProgrammer/Minecraft-Deobfuscator3000 using mappings "F:\������\Minecraft-Deobfuscator3000-master\1.8.9"!

package unfair.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.network.play.client.C16PacketClientStatus.EnumState;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.MoveInputEvent;
import unfair.events.PacketEvent;
import unfair.events.UpdateEvent;
import unfair.management.RotationState;
import unfair.mixin.IAccessorEntity;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.ModeProperty;
import unfair.util.MoveUtil;
import unfair.util.RayCastUtil;
import unfair.util.RotationUtil;

public class Velocity extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static boolean hasTakenVelocity = false;
    public static boolean noAttack = true;
    private static boolean slot = false;
    private static boolean attack = false;
    private static boolean swing = false;
    private static boolean block = false;
    private static boolean inventory = false;
    private static boolean dig = false;
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Reduce"});
    public final FloatProperty ticksLimit = new FloatProperty("Tick", 3.0F, 0.0F, 20.0F, () -> true);
    public final BooleanProperty Rotate = new BooleanProperty("Rotation", false);
    public final BooleanProperty AutoMove = new BooleanProperty("PlayerMove", false);
    private int ticksSinceVelocity = 0;
    private int rotateTickCounter = 0;
    private float[] targetRotation = null;
    private double knockbackX = 0.0D;
    private double knockbackZ = 0.0D;

    public Velocity() {
        super("Velocity", false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && mc.thePlayer != null) {
            Packet packet;
            if (event.getType() == EventType.RECEIVE && !event.isCancelled()) {
                packet = event.getPacket();
                if (packet instanceof S12PacketEntityVelocity) {
                    S12PacketEntityVelocity velocityPacket = (S12PacketEntityVelocity) packet;
                    if (velocityPacket.getEntityID() == mc.thePlayer.getEntityId()) {
                        this.ticksSinceVelocity = 0;
                        hasTakenVelocity = true;
                        if (this.Rotate.getValue()) {
                            this.knockbackX = (double) velocityPacket.getMotionX() / 8000.0D;
                            this.knockbackZ = (double) velocityPacket.getMotionZ() / 8000.0D;
                            if (Math.abs(this.knockbackX) > 0.01D || Math.abs(this.knockbackZ) > 0.01D) {
                                this.rotateTickCounter = 1;
                            }
                        }
                    }
                }
            }

            if (event.getType() == EventType.SEND && !event.isCancelled()) {
                packet = event.getPacket();
                if (packet instanceof C09PacketHeldItemChange) {
                    slot = true;
                } else if (packet instanceof C0APacketAnimation) {
                    swing = true;
                } else if (packet instanceof C02PacketUseEntity) {
                    C02PacketUseEntity useEntity = (C02PacketUseEntity) packet;
                    if (useEntity.getAction() == Action.ATTACK) {
                        attack = true;
                    }
                } else if (packet instanceof C08PacketPlayerBlockPlacement) {
                    block = true;
                } else if (packet instanceof C07PacketPlayerDigging) {
                    block = true;
                    dig = true;
                } else if (packet instanceof C0DPacketCloseWindow || packet instanceof C0EPacketClickWindow || packet instanceof C16PacketClientStatus && ((C16PacketClientStatus) packet).getStatus() == EnumState.OPEN_INVENTORY_ACHIEVEMENT) {
                    inventory = true;
                } else if (packet instanceof C03PacketPlayer) {
                    this.resetBadPackets();
                }
            }
        }

    }

    private boolean badPackets() {
        return this.badPackets(false, true, false, false, true, false);
    }

    private boolean badPackets(boolean checkSlot, boolean checkAttack, boolean checkSwing, boolean checkBlock, boolean checkInventory, boolean checkDig) {
        return slot && checkSlot || attack && checkAttack || swing && checkSwing || block && checkBlock || inventory && checkInventory || dig && checkDig;
    }

    private void resetBadPackets() {
        slot = false;
        swing = false;
        attack = false;
        block = false;
        inventory = false;
        dig = false;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && mc.thePlayer != null && event.getType() == EventType.PRE) {
            ++this.ticksSinceVelocity;
            int maxTick = 5;
            if (this.rotateTickCounter > 0 && this.rotateTickCounter <= maxTick) {
                ++this.rotateTickCounter;
                if (this.rotateTickCounter > maxTick) {
                    this.rotateTickCounter = 0;
                    this.targetRotation = null;
                    this.knockbackX = 0.0D;
                    this.knockbackZ = 0.0D;
                }
            }

            float limit = this.ticksLimit.getValue();
            boolean withinLimit = limit == 0.0F || (float) this.ticksSinceVelocity < limit;
            if (hasTakenVelocity && withinLimit) {
                Module module = Unfair.moduleManager.modules.get(KillAura.class);
                if (!(module instanceof KillAura)) {
                    return;
                }

                KillAura killAura = (KillAura) module;
                if (!killAura.isEnabled() || killAura.getTarget() == null) {
                    return;
                }

                Entity target = killAura.getTarget();

                if (!RayCastUtil.inView(target)) {
                    return;
                }

                boolean isInWeb = ((IAccessorEntity) mc.thePlayer).getIsInWeb();

                if (!MoveUtil.isForwardPressed()) return;

                if (isInWeb) {
                    return;
                }

                if (!mc.thePlayer.isSwingInProgress) {
                    return;
                }

                if (target == mc.thePlayer) {
                    return;
                }

                if (this.badPackets()) {
                    return;
                }

                if (this.rotateTickCounter == 1) {
                    double deltaX = -this.knockbackX;
                    double deltaZ = -this.knockbackZ;
                    this.targetRotation = RotationUtil.getRotationsTo(deltaX, 0.0D, deltaZ, event.getYaw(), event.getPitch());
                }

                if (this.targetRotation != null) {
                    event.setRotation(this.targetRotation[0], this.targetRotation[1], 2);
                    event.setPervRotation(this.targetRotation[0], 2);
                }

                if (mc.getNetHandler() != null) {
                    mc.getNetHandler().addToSendQueue(new C0APacketAnimation());
                    mc.getNetHandler().addToSendQueue(new C02PacketUseEntity(target, Action.ATTACK));
                }

                mc.thePlayer.motionX *= 0.6D;
                mc.thePlayer.motionZ *= 0.6D;
                mc.thePlayer.setSprinting(false);

                hasTakenVelocity = false;
                noAttack = true;
            } else {
                if (limit > 0.0F && (float) this.ticksSinceVelocity >= limit) {
                    hasTakenVelocity = false;
                }

                noAttack = true;
            }
        }

    }

    @EventTarget
    public void onPostUpdate(UpdateEvent event) {
        if (event.getType() == EventType.POST) {
            int maxTick = 5;
            if (this.rotateTickCounter > 0 && this.rotateTickCounter <= maxTick) {
                ++this.rotateTickCounter;
                if (this.rotateTickCounter > maxTick) {
                    this.rotateTickCounter = 0;
                    this.targetRotation = null;
                    this.knockbackX = 0.0D;
                    this.knockbackZ = 0.0D;
                }
            }
        }

    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled() && this.rotateTickCounter > 0 && this.rotateTickCounter <= 5) {
            if (this.AutoMove.getValue()) {
                mc.thePlayer.movementInput.moveForward = 1.0F;
            }

            if (this.targetRotation != null && RotationState.isActived() && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
        }

    }

    public String[] getSuffix() {
        return new String[]{"Reduce"};
    }

    public void onEnabled() {
        hasTakenVelocity = false;
        noAttack = true;
        this.ticksSinceVelocity = 100;
        this.rotateTickCounter = 0;
        this.targetRotation = null;
        this.knockbackX = 0.0D;
        this.knockbackZ = 0.0D;
    }

    public void onDisabled() {
        hasTakenVelocity = false;
        noAttack = true;
        this.ticksSinceVelocity = 0;
        this.rotateTickCounter = 0;
        this.targetRotation = null;
        this.knockbackX = 0.0D;
        this.knockbackZ = 0.0D;
    }
}
