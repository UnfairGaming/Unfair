package cn.unfair.module.modules.combat;

import cn.unfair.mixin.IAccessorEntity;
import cn.unfair.module.ModuleWithModuleSettings;
import cn.unfair.module.modules.combat.velocity.Prediction;
import cn.unfair.module.modules.combat.velocity.Reduce;
import cn.unfair.module.modules.combat.velocity.Vanilla;
import net.minecraft.client.Minecraft;

public class Velocity extends ModuleWithModuleSettings {

    private static final Minecraft mc = Minecraft.getMinecraft();

    public Velocity() {
        super("Velocity", false, "mode", new Vanilla(), new Prediction(), new Reduce());
    }

    public static boolean isInLiquidOrWeb() {
        return mc.thePlayer.isInWater() || mc.thePlayer.isInLava() || ((IAccessorEntity) mc.thePlayer).getIsInWeb();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.modeProperty.getModeString()};
    }
}
