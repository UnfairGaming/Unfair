package unfair.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@SideOnly(Side.CLIENT)
@Mixin({EntityLivingBase.class})
public interface IAccessorEntityLivingBase {
    @Accessor
    Map<Integer, PotionEffect> getActivePotionsMap();

    @Accessor
    AttributeModifier getSprintingSpeedBoostModifier();

    @Accessor
    int getJumpTicks();

    @Accessor
    void setJumpTicks(int integer);
}
