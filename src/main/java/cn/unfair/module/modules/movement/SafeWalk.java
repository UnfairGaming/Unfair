package cn.unfair.module.modules.movement;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.SafeWalkEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.player.Scaffold;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.MoveUtil;
import cn.unfair.util.PlayerUtil;
import net.minecraft.client.Minecraft;

public class SafeWalk extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty motion = new FloatProperty("motion", 1.0F, 0.5F, 1.0F);
    public final FloatProperty speedMotion = new FloatProperty("speed-motion", 1.0F, 0.5F, 1.5F);
    public final BooleanProperty air = new BooleanProperty("air", false);
    public final BooleanProperty directionCheck = new BooleanProperty("direction-check", true);
    public final BooleanProperty pitCheck = new BooleanProperty("pitch-check", true);
    public final BooleanProperty requirePress = new BooleanProperty("require-press", false);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);

    public SafeWalk() {
        super("SafeWalk", false);
    }

    private boolean canSafeWalk() {
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (this.directionCheck.getValue() && mc.gameSettings.keyBindForward.isKeyDown()) {
            return false;
        } else if (this.pitCheck.getValue() && mc.thePlayer.rotationPitch < 69.0F) {
            return false;
        } else if (this.blocksOnly.getValue() && !ItemUtil.isHoldingBlock()) {
            return false;
        } else {
            return (!this.requirePress.getValue() || mc.gameSettings.keyBindUseItem.isKeyDown()) && (mc.thePlayer.onGround && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -1.0)
                    || this.air.getValue() && PlayerUtil.canMove(mc.thePlayer.motionX, mc.thePlayer.motionZ, -2.0));
        }
    }

    @EventTarget
    public void onMove(SafeWalkEvent event) {
        if (this.isEnabled()) {
            if (this.canSafeWalk()) {
                event.setSafeWalk(true);
            }
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (mc.thePlayer.onGround && MoveUtil.isForwardPressed() && this.canSafeWalk()) {
                if (MoveUtil.getSpeedLevel() <= 0) {
                    if (this.motion.getValue() != 1.0F) {
                        MoveUtil.setSpeed(MoveUtil.getSpeed() * (double) this.motion.getValue());
                    }
                } else if (this.speedMotion.getValue() != 1.0F) {
                    MoveUtil.setSpeed(MoveUtil.getSpeed() * (double) this.speedMotion.getValue());
                }
            }
        }
    }
}
