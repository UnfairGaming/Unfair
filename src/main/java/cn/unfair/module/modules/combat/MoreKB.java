package cn.unfair.module.modules.combat;

import cn.unfair.event.EventTarget;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;

public class MoreKB extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Legit", "LegitFast", "LessPacket", "Packet", "DoublePacket"});
    public final BooleanProperty intelligent = new BooleanProperty("Intelligent", false);
    public final BooleanProperty onlyGround = new BooleanProperty("Only Ground", true);
    private EntityLivingBase target;

    public MoreKB() {
        super("MoreKB", false);
        this.target = null;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        Entity targetEntity = event.getTarget();
        if (targetEntity instanceof EntityLivingBase) {
            this.target = (EntityLivingBase) targetEntity;
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.mode.getValue() == 1) {
            if (this.target != null && this.isMoving()) {
                if (!this.onlyGround.getValue() || mc.thePlayer.onGround) {
                    mc.thePlayer.sprintingTicksLeft = 0;
                }
                this.target = null;
            }
            return;
        }
        EntityLivingBase entity = null;
        if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mc.objectMouseOver.entityHit instanceof EntityLivingBase) {
            entity = (EntityLivingBase) mc.objectMouseOver.entityHit;
        }
        if (entity == null) {
            return;
        }
        double x = mc.thePlayer.posX - entity.posX;
        double z = mc.thePlayer.posZ - entity.posZ;
        float calcYaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI - 90.0);
        float diffY = Math.abs(MathHelper.wrapAngleTo180_float(calcYaw - entity.rotationYawHead));
        if (this.intelligent.getValue() && diffY > 120.0F) {
            return;
        }
        if (entity.hurtTime == 10) {
            switch (this.mode.getValue()) {
                case 0:
                    if (mc.thePlayer.isSprinting()) {
                        mc.thePlayer.setSprinting(false);
                        mc.thePlayer.setSprinting(true);
                    }
                    break;
                case 2:
                    if (mc.thePlayer.isSprinting()) {
                        mc.thePlayer.setSprinting(false);
                    }
                    mc.getNetHandler().addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;
                case 3:
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;
                case 4:
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.STOP_SPRINTING));
                    mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, C0BPacketEntityAction.Action.START_SPRINTING));
                    mc.thePlayer.setSprinting(true);
                    break;
            }
        }
    }

    private boolean isMoving() {
        return mc.thePlayer.moveForward != 0.0F || mc.thePlayer.moveStrafing != 0.0F;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }
}
