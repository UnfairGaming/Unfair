package cn.unfair.module.modules.movement;

import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.PercentProperty;
import net.minecraft.client.Minecraft;

public class KeepSprint extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final PercentProperty slowdown = new PercentProperty("Slowdown", 0);
    public final BooleanProperty groundOnly = new BooleanProperty("Ground Only", false);
    public final BooleanProperty reachOnly = new BooleanProperty("Reach Only", false);

    public KeepSprint() {
        super("KeepSprint", false);
    }

    public boolean shouldKeepSprint() {
        if (this.groundOnly.getValue() && !mc.thePlayer.onGround) {
            return false;
        } else {
            return !this.reachOnly.getValue() || mc.objectMouseOver.hitVec.distanceTo(mc.getRenderViewEntity().getPositionEyes(1.0F)) > 3.0;
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{slowdown.getValue() + "%"};
    }
}
