package unfair.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@SideOnly(Side.CLIENT)
@Mixin({EntityPlayer.class})
public interface IAccessorEntityPlayer {
    @Accessor
    ItemStack getItemInUse();

    @Accessor
    void setItemInUse(ItemStack itemStack);

    @Accessor
    int getItemInUseCount();

    @Accessor
    void setItemInUseCount(int integer);
}
