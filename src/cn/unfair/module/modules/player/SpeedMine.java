package cn.unfair.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.PercentProperty;

public class SpeedMine extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty speed = new PercentProperty("speed", 15);
    public final IntProperty delay = new IntProperty("delay", 0, 0, 4);

    public SpeedMine() {
        super("SpeedMine", false);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (!mc.playerController.isInCreativeMode()) {
                if (mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectType.BLOCK) {
                    mc.playerController.setBlockHitDelay(Math.min(mc.playerController.getBlockHitDelay(), this.delay.getValue() + 1));
                    if (mc.playerController.getIsHittingBlock()) {
                        float curBlockDamageMP = mc.playerController.getCurBlockDamageMP();
                        float damage = 0.3F * (this.speed.getValue().floatValue() / 100.0F);
                        if (curBlockDamageMP < damage) {
                            mc.playerController.setCurBlockDamageMP(damage);
                        }
                    }
                }
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{String.format("%d%%", this.speed.getValue())};
    }
}
