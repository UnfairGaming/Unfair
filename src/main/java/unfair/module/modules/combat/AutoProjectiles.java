package unfair.module.modules.combat;

import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.UpdateEvent;
import unfair.module.Module;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.util.ItemUtil;
import unfair.util.PacketUtil;
import unfair.util.RotationUtil;
import unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;

public class AutoProjectiles extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final IntProperty cooldown = new IntProperty("cooldown", 500, 0, 2000);
    public final FloatProperty maxRange = new FloatProperty("max-range", 20.0F, 3.0F, 64.0F);
    private final TimerUtil timer = new TimerUtil();

    public AutoProjectiles() {
        super("AutoProjectiles", false, false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) return;
        if (event.getType() != EventType.PRE) return;

        KillAura killAura = (KillAura) unfair.Unfair.moduleManager.modules.get(KillAura.class);
        if (killAura == null || !killAura.isEnabled()) return;

        EntityLivingBase target = killAura.getTarget();
        if (target == null) return;

        double distance = mc.thePlayer.getDistanceToEntity(target);
        if (distance > killAura.attackRange.getValue() && distance <= maxRange.getValue()) {
            int projectileCount = ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile);
            if (projectileCount > 0 && timer.hasTimeElapsed(cooldown.getValue().longValue())) {
                int projectileSlot = findProjectileHotbarSlot();
                if (projectileSlot != -1) {
                    float[] rotations = RotationUtil.getRotationsToBox(
                            target.getEntityBoundingBox(),
                            event.getYaw(),
                            event.getPitch(),
                            180.0F,
                            0.0F
                    );
                    event.setRotation(rotations[0], rotations[1], 1);
                    int originalSlot = mc.thePlayer.inventory.currentItem;
                    if (projectileSlot != originalSlot) {
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(projectileSlot));
                    }
                    PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.inventory.getStackInSlot(projectileSlot)));
                    if (projectileSlot != originalSlot) {
                        PacketUtil.sendPacket(new C09PacketHeldItemChange(originalSlot));
                    }

                    timer.reset();
                }
            }
        }
    }

    private int findProjectileHotbarSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (ItemUtil.isProjectile(stack)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String[] getSuffix() {
        int count = ItemUtil.findInventorySlot(ItemUtil.ItemType.Projectile);
        return new String[]{String.valueOf(count)};
    }
}