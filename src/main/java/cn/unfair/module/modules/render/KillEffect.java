package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumParticleTypes;

public class KillEffect extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final BooleanProperty lightning = new BooleanProperty("Lightning", true);
    public final BooleanProperty blood = new BooleanProperty("Blood Explosion", true);
    public final BooleanProperty explosion = new BooleanProperty("Explosion", true);

    private EntityLivingBase target;

    public KillEffect() {
        super("KillEffect", false);
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (this.target == null || mc.theWorld == null) {
            return;
        }

        if (!mc.theWorld.loadedEntityList.contains(this.target)) {
            if (this.lightning.getValue()) {
                EntityLightningBolt entityLightningBolt = new EntityLightningBolt(mc.theWorld, this.target.posX, this.target.posY, this.target.posZ);
                mc.theWorld.addEntityToWorld((int) (-Math.random() * 100000), entityLightningBolt);
                this.playSound("ambient.weather.thunder");
            }

            if (this.explosion.getValue()) {
                for (int i = 0; i <= 8; i++) {
                    mc.effectRenderer.emitParticleAtEntity(this.target, EnumParticleTypes.FLAME);
                }
                this.playSound("item.fireCharge.use");
            }

            if (this.blood.getValue()) {
                double startY = this.target.posY;
                double endY = this.target.posY + this.target.height + 0.4;
                double step = 0.4;

                for (int i = 0; i < 100; i++) {
                    for (double y = startY; y <= endY; y += step) {
                        mc.theWorld.spawnParticle(EnumParticleTypes.BLOCK_CRACK, this.target.posX, y, this.target.posZ, 0, 0, 0, Block.getStateId(Blocks.redstone_block.getDefaultState()));
                    }
                }

                for (double y = startY; y <= endY; y += step) {
                    this.playSound("dig.stone");
                }
            }

            this.target = null;
        }
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) {
            return;
        }

        Entity entity = event.getTarget();
        if (entity instanceof EntityLivingBase) {
            this.target = (EntityLivingBase) entity;
        }
    }

    private void playSound(String soundName) {
        if (mc.thePlayer != null && mc.theWorld != null) {
            mc.theWorld.playSound(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ, soundName, 1.0F, 1.0F, false);
        }
    }

    @Override
    public void onDisabled() {
        this.target = null;
    }
}
