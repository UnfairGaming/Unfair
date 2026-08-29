package cn.unfair.module.modules.render;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.render.ColorUtil;
import cn.unfair.util.render.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

public class FireBallPredict extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double MIN_DISTANCE = 8.0;
    private static final double MID_DISTANCE = 24.0;
    private static final double MAX_DISTANCE = 48.0;
    private static final int ORANGE = 0xFFB000;
    public final FloatProperty predictRange = new FloatProperty("Predict Range", 100.0F, 16.0F, 200.0F);
    public final FloatProperty renderRadius = new FloatProperty("Render Radius", 2.0F, 1.0F, 2.0F);
    public final BooleanProperty realFireballs = new BooleanProperty("Real Fireballs", true);
    public final BooleanProperty heldFireCharges = new BooleanProperty("Held Fire Charges", true);
    public final PercentProperty opacity = new PercentProperty("Opacity", 50);
    private BlockPos target;
    private int impactColor;

    public FireBallPredict() {
        super("FireBallPredict", false, true);
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.POST || mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        Impact impact = null;
        if (this.realFireballs.getValue()) {
            impact = this.findRealFireballImpact();
        }
        if (impact == null && this.heldFireCharges.getValue()) {
            impact = this.findHeldFireChargeImpact();
        }
        if (impact == null) {
            this.target = null;
            this.impactColor = 0;
        } else {
            this.target = impact.pos;
            this.impactColor = impact.color;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (this.isEnabled() && mc.theWorld != null && mc.thePlayer != null && this.target != null) {
            this.render();
        }
    }

    private Impact findRealFireballImpact() {
        WorldClient world = mc.theWorld;
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Impact best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityFireball) || entity instanceof EntityWitherSkull) {
                continue;
            }
            EntityFireball fireball = (EntityFireball) entity;
            double speedSq = fireball.motionX * fireball.motionX + fireball.motionY * fireball.motionY + fireball.motionZ * fireball.motionZ;
            if (speedSq < 1.0E-4) {
                continue;
            }
            Vec3 pos = new Vec3(fireball.posX, fireball.posY, fireball.posZ);
            Vec3 dir = new Vec3(fireball.motionX, fireball.motionY, fireball.motionZ).normalize();
            Vec3 end = pos.addVector(dir.xCoord * this.predictRange.getValue(), dir.yCoord * this.predictRange.getValue(), dir.zCoord * this.predictRange.getValue());
            MovingObjectPosition mop = world.rayTraceBlocks(pos, end, false, true, false);
            if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
                BlockPos blockPos = mop.getBlockPos();
                Vec3 center = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                double distance = playerPos.squareDistanceTo(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Impact(blockPos, this.colorForDistance(pos.distanceTo(mop.hitVec)));
                }
            }
        }
        return best;
    }

    private Impact findHeldFireChargeImpact() {
        WorldClient world = mc.theWorld;
        Vec3 playerPos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Impact best = null;
        double bestDistance = Double.MAX_VALUE;
        for (EntityPlayer player : world.playerEntities) {
            ItemStack held = player.getHeldItem();
            if (held == null || held.getItem() != Items.fire_charge) {
                continue;
            }
            Vec3 eyes = player.getPositionEyes(1.0F);
            Vec3 look = player.getLook(1.0F);
            Vec3 end = eyes.addVector(look.xCoord * this.predictRange.getValue(), look.yCoord * this.predictRange.getValue(), look.zCoord * this.predictRange.getValue());
            MovingObjectPosition mop = world.rayTraceBlocks(eyes, end, false, true, false);
            if (mop != null && mop.typeOfHit == MovingObjectType.BLOCK) {
                BlockPos blockPos = mop.getBlockPos();
                Vec3 center = new Vec3(blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5);
                double distance = playerPos.squareDistanceTo(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new Impact(blockPos, 0xFFFF00);
                }
            }
        }
        return best;
    }

    private int colorForDistance(double distance) {
        if (distance <= MIN_DISTANCE) {
            return 0xFF0000;
        }
        if (distance >= MAX_DISTANCE) {
            return 0x00FF00;
        }
        if (distance <= MID_DISTANCE) {
            return ColorUtil.rgb(255, Math.round(255.0F * (float) ((distance - MIN_DISTANCE) / 16.0)), 0);
        }
        return ColorUtil.rgb(Math.round(255.0F * (1.0F - (float) ((distance - MID_DISTANCE) / 24.0))), 255, 0);
    }

    private int adjustColor(int color) {
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        return red > 200 && green < 96 && blue < 96 ? ORANGE : color;
    }

    private void render() {
        World world = mc.theWorld;
        int radius = Math.max(1, (int) Math.min(2, this.renderRadius.getValue()));
        int color = this.adjustColor(this.impactColor);
        boolean orange = color == ORANGE;
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        int outlineAlpha = this.applyOpacity(255, orange);
        int fillAlpha = this.applyOpacity(orange ? 70 : 42, orange);
        if (outlineAlpha <= 0 && fillAlpha <= 0) {
            return;
        }
        RenderUtil.enableRenderState();
        try {
            if (fillAlpha > 0) {
                this.renderBlocks(world, radius, red, green, blue, fillAlpha, true);
            }
            if (outlineAlpha > 0) {
                this.renderBlocks(world, radius, red, green, blue, outlineAlpha, false);
                RenderUtil.drawBlockBoundingBox(this.target, 1.0, 255, 255, 255, outlineAlpha, 2.2F);
            }
        } finally {
            RenderUtil.disableRenderState();
        }
    }

    private void renderBlocks(World world, int radius, int red, int green, int blue, int alpha, boolean filled) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = this.target.add(dx, dy, dz);
                    if (!this.isRenderable(world, pos)) {
                        continue;
                    }
                    AxisAlignedBB box = new AxisAlignedBB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0)
                            .offset(-mc.getRenderManager().getRenderPosX(), -mc.getRenderManager().getRenderPosY(), -mc.getRenderManager().getRenderPosZ());
                    if (filled) {
                        RenderUtil.drawFilledBox(box, red, green, blue, alpha);
                    } else {
                        RenderUtil.drawBoundingBox(box, red, green, blue, alpha, 1.5F);
                    }
                }
            }
        }
    }

    private boolean isRenderable(World world, BlockPos pos) {
        if (world.isAirBlock(pos)) {
            return false;
        }
        Block block = world.getBlockState(pos).getBlock();
        return block.isFullCube();
    }

    private int applyOpacity(int base, boolean boosted) {
        double percent = this.opacity.getValue();
        if (boosted && percent > 0.0) {
            percent = Math.min(100.0, percent * 1.4);
        }
        return Math.max(0, Math.min(base, (int) (base * percent / 100.0)));
    }

    private record Impact(BlockPos pos, int color) {
    }
}