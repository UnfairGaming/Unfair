package unfair.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.TickEvent;
import unfair.mixin.IAccessorMinecraft;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.IntProperty;
import unfair.util.RotationUtil;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class FastPlace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final DecimalFormat df = new DecimalFormat("0.0#", new DecimalFormatSymbols(Locale.US));
    public final IntProperty delay = new IntProperty("delay", 1, 1, 3);
    public final BooleanProperty blocksOnly = new BooleanProperty("blocks-only", true);
    public final BooleanProperty placeFix = new BooleanProperty("place-fix", true);
    private long delayMS = 0L;

    public FastPlace() {
        super("FastPlace", false);
    }

    private boolean canPlace() {
        ItemStack stack = mc.thePlayer.getHeldItem();
        if (stack != null) {
            Item item = stack.getItem();
            if (item instanceof ItemFishingRod) {
                return false;
            }
            if (item instanceof ItemBlock) {
                if (!(Boolean) this.placeFix.getValue()) {
                    return true;
                }
                MovingObjectPosition mop = RotationUtil.rayTrace(
                        mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch, mc.playerController.getBlockReachDistance(), 1.0F
                );
                return mop != null
                        && mop.typeOfHit == MovingObjectType.BLOCK
                        && ((ItemBlock) item).canPlaceBlockOnSide(mc.theWorld, mop.getBlockPos(), mop.sideHit, mc.thePlayer, stack);
            }
        }
        return !(Boolean) this.blocksOnly.getValue();
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            int rightClickDelayTimer = ((IAccessorMinecraft) mc).getRightClickDelayTimer();
            if (rightClickDelayTimer == 4) {
                this.delayMS = this.delayMS + (long) (50.0F * this.delay.getValue());
            }
            if (this.delayMS > 0L) {
                this.delayMS = this.delayMS - 50;
            }
            if (this.delayMS <= 0L && rightClickDelayTimer > 1 && this.canPlace()) {
                ((IAccessorMinecraft) mc).setRightClickDelayTimer(0);
            }
        }
    }

    @Override
    public void onDisabled() {
        this.delayMS = 0L;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{df.format(this.delay.getValue())};
    }
}
