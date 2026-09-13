package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LoadWorldEvent;
import cn.unfair.events.Render2DEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.render.AnimationUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

public class Blink extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int NAVEN_PROGRESS_COLOR = 0xFF962D2D;
    private static final int NAVEN_PROGRESS_BACKGROUND = 0x80000000;
    private static final int PROJECTILE_SIMULATION_MAX_STEPS = 300;

    public final ModeProperty mode = new ModeProperty("Mode", 0, new String[]{"Default", "Pulse", "Naven"});
    public final IntProperty ticks = new IntProperty("Ticks", 20, 0, 1200);
    public final IntProperty releaseOnDamage = new IntProperty("Release Ticks on Damage", 20, 0, 50, () -> mode.getValue() == 2);
    public final FloatProperty releaseSpeed = new FloatProperty("Release Speed (Tick)", 10.0F, 3.0F, 20.0F, () -> mode.getValue() == 2);
    public final IntProperty maxTicks = new IntProperty("Max Ticks", 200, 10, 500, () -> mode.getValue() == 2);
    public final FloatProperty playerDistance = new FloatProperty("Player Distance", 4.0F, 3.0F, 10.0F, () -> mode.getValue() == 2);
    public final FloatProperty tntDistance = new FloatProperty("TNT Distance", 5.0F, 3.0F, 10.0F, () -> mode.getValue() == 2);
    public final FloatProperty fakePlayerHitBoxes = new FloatProperty("Fake Player HitBoxes", 0.2F, 0.0F, 3.0F, () -> mode.getValue() == 2);

    private boolean disabling = false;
    private EntityOtherPlayerMP fakePlayer;
    private int shouldReleaseTicks = 0;
    private int releasedTicks = 0;
    private float progress = 0.0F;
    private float progressTarget = 0.0F;

    public Blink() {
        super("Blink", false);
    }

    private boolean isNaven() {
        return this.mode.getValue() == 2;
    }

    @Override
    public void onEnabled() {
        this.disabling = false;
        this.shouldReleaseTicks = 0;
        this.releasedTicks = 0;
        this.progress = 0.0F;
        this.progressTarget = 0.0F;
        Unfair.blinkManager.setBlinkState(false, Unfair.blinkManager.getBlinkingModule());
        Unfair.blinkManager.setBlinkState(true, BlinkModules.BLINK);
        if (this.isNaven()) {
            this.createFakePlayer();
        }
    }

    @Override
    public void onDisabled() {
        this.disabling = false;
        Unfair.blinkManager.setBlinkState(false, BlinkModules.BLINK);
        this.removeFakePlayer();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (!this.isNaven() || mc.thePlayer == null) {
            super.setEnabled(enabled);
            return;
        }
        if (enabled) {
            this.disabling = false;
            super.setEnabled(true);
        } else if (!this.disabling) {
            this.disabling = true;
        } else if (Unfair.blinkManager.blinkedPackets.isEmpty()) {
            this.disabling = false;
            super.setEnabled(false);
        }
    }

    @Override
    public String[] getSuffix() {
        if (!this.isNaven()) {
            return new String[0];
        }
        return this.isEnabled()
                ? new String[]{Unfair.blinkManager.countMovement() + " Ticks"}
                : new String[]{"Naven"};
    }

    @EventTarget(Priority.LOWEST)
    public void onTick(TickEvent event) {
        if (this.isEnabled() && event.type() == EventType.POST) {
            if (!Unfair.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
                this.setEnabled(false);
            } else {
                if (this.ticks.getValue() > 0 && Unfair.blinkManager.countMovement() > (long) this.ticks.getValue()) {
                    switch (this.mode.getValue()) {
                        case 0:
                            this.setEnabled(false);
                            break;
                        case 1:
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.BLINK);
                            Unfair.blinkManager.setBlinkState(true, BlinkModules.BLINK);
                    }
                }
            }
        }
    }

    @EventTarget
    public void onNavenTick(TickEvent event) {
        if (event.type() != EventType.PRE || !this.isEnabled() || !this.isNaven()) {
            return;
        }
        if (mc.thePlayer == null || mc.theWorld == null) {
            this.setEnabled(false);
            return;
        }
        if (!Unfair.blinkManager.getBlinkingModule().equals(BlinkModules.BLINK)) {
            this.setEnabled(false);
            return;
        }

        this.releasedTicks = 0;
        this.progressTarget = MathHelper.clamp_float(
                (float) Unfair.blinkManager.countMovement() / (float) this.maxTicks.getValue() * 100.0F, 0.0F, 100.0F
        );

        if (mc.thePlayer.hurtTime == 10) {
            this.shouldReleaseTicks += this.releaseOnDamage.getValue();
        }

        while ((float) this.releasedTicks < this.releaseSpeed.getValue()
                && this.shouldReleaseTicks > 0
                && !Unfair.blinkManager.blinkedPackets.isEmpty()) {
            this.releaseTick();
            this.shouldReleaseTicks--;
        }

        while ((float) this.releasedTicks < this.releaseSpeed.getValue()
                && this.isPlayerInDanger()
                && !Unfair.blinkManager.blinkedPackets.isEmpty()) {
            this.releaseTick();
        }

        while ((float) this.releasedTicks < this.releaseSpeed.getValue()
                && (float) Unfair.blinkManager.countMovement() >= this.maxTicks.getValue()
                && !Unfair.blinkManager.blinkedPackets.isEmpty()) {
            this.releaseTick();
        }

        if (this.disabling) {
            while ((float) this.releasedTicks < this.releaseSpeed.getValue()
                    && !Unfair.blinkManager.blinkedPackets.isEmpty()) {
                this.releaseTick();
            }
            if (Unfair.blinkManager.blinkedPackets.isEmpty()) {
                this.setEnabled(false);
            }
        }
    }

    @EventTarget
    public void onWorldLoad(LoadWorldEvent event) {
        this.disabling = false;
        super.setEnabled(false);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || !this.isNaven()) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(mc);
        float x = sr.getScaledWidth() / 2.0F - 50.0F;
        float y = sr.getScaledHeight() / 2.0F + 15.0F;
        this.progress = AnimationUtil.lerp(this.progress, this.progressTarget, 0.2F);
        RenderUtil.drawRoundedRectangle(x, y, 100.0F, 5.0F, 2.0F, NAVEN_PROGRESS_BACKGROUND);
        RenderUtil.drawRoundedRectangle(x, y, this.progress, 5.0F, 2.0F, NAVEN_PROGRESS_COLOR);
    }

    private void createFakePlayer() {
        if (mc.thePlayer == null || mc.theWorld == null) {
            return;
        }
        this.removeFakePlayer();
        this.fakePlayer = new EntityOtherPlayerMP(mc.theWorld, mc.thePlayer.getGameProfile());
        this.fakePlayer.clonePlayer(mc.thePlayer, true);
        this.fakePlayer.copyLocationAndAnglesFrom(mc.thePlayer);
        this.fakePlayer.rotationYawHead = mc.thePlayer.rotationYawHead;
        this.fakePlayer.setSneaking(mc.thePlayer.isSneaking());
        this.fakePlayer.setSprinting(mc.thePlayer.isSprinting());
        mc.theWorld.spawnEntityInWorld(this.fakePlayer);
    }

    private void removeFakePlayer() {
        if (this.fakePlayer != null && mc.theWorld != null) {
            mc.theWorld.removeEntity(this.fakePlayer);
        }
        this.fakePlayer = null;
    }

    private void releaseTick() {
        C03PacketPlayer packet = Unfair.blinkManager.releaseTick();
        if (packet != null) {
            this.releasedTicks++;
            this.handleMove(packet);
        }
    }

    private void handleMove(C03PacketPlayer packet) {
        if (this.fakePlayer == null) {
            return;
        }
        double x = packet.isMoving() ? packet.getPositionX() : this.fakePlayer.posX;
        double y = packet.isMoving() ? packet.getPositionY() : this.fakePlayer.posY;
        double z = packet.isMoving() ? packet.getPositionZ() : this.fakePlayer.posZ;
        float yaw = packet.getRotating() ? packet.getYaw() : this.fakePlayer.rotationYaw;
        float pitch = packet.getRotating() ? packet.getPitch() : this.fakePlayer.rotationPitch;
        this.fakePlayer.setPositionAndRotation2(x, y, z, yaw, pitch, 3, false);
        if (packet.getRotating()) {
            this.fakePlayer.rotationYawHead = packet.getYaw();
        }
    }

    private boolean isPlayerInDanger() {
        return this.isTNTNear((double) this.tntDistance.getValue())
                || this.isPlayerNear((double) this.playerDistance.getValue())
                || this.isProjectileNear((double) this.fakePlayerHitBoxes.getValue());
    }

    private boolean isPlayerNear(double distance) {
        if (this.fakePlayer == null) {
            return false;
        }
        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player == mc.thePlayer || player == this.fakePlayer) {
                continue;
            }
            if (TeamUtil.isSameTeam(player) || TeamUtil.isFriend(player) || TeamUtil.isBot(player)) {
                continue;
            }
            Vec3 eyePosition = player.getPositionEyes(1.0F);
            Vec3 closestPoint = RotationUtil.getClosestPointOnBox(eyePosition, this.fakePlayer.getEntityBoundingBox());
            if (eyePosition.distanceTo(closestPoint) < distance) {
                return true;
            }
        }
        return false;
    }

    private boolean isTNTNear(double distance) {
        if (this.fakePlayer == null) {
            return false;
        }
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityTNTPrimed && (double) this.fakePlayer.getDistanceToEntity(entity) <= distance) {
                return true;
            }
        }
        return false;
    }

    private boolean isProjectileNear(double expands) {
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if ((entity instanceof EntityArrow || entity instanceof EntityEgg || entity instanceof EntitySnowball)
                    && this.checkProjectile(entity, expands)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkProjectile(Entity entity, double expands) {
        if (this.fakePlayer == null) {
            return false;
        }
        double posX = entity.posX;
        double posY = entity.posY;
        double posZ = entity.posZ;
        double motionX = entity.motionX;
        double motionY = entity.motionY;
        double motionZ = entity.motionZ;
        double halfSize;
        double gravity;
        if (entity instanceof EntityArrow) {
            halfSize = 0.5D;
            gravity = 0.05D;
        } else {
            halfSize = 0.25D;
            gravity = 0.03D;
        }

        for (int step = 0; step < PROJECTILE_SIMULATION_MAX_STEPS; step++) {
            AxisAlignedBB aabb = new AxisAlignedBB(
                    posX - halfSize, posY, posZ - halfSize,
                    posX + halfSize, posY + halfSize, posZ + halfSize
            );
            if (aabb.expand(expands, expands, expands).intersectsWith(this.fakePlayer.getEntityBoundingBox())) {
                return true;
            }

            Vec3 start = new Vec3(posX, posY, posZ);
            Vec3 end = new Vec3(posX + motionX, posY + motionY, posZ + motionZ);
            MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(start, end, false, true, false);

            posX += motionX;
            posY += motionY;
            posZ += motionZ;

            if ((hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) || posY < -128.0D) {
                return false;
            }

            double drag = entity.isInWater() ? 0.8D : 0.99D;
            motionX *= drag;
            motionY = motionY * drag - gravity;
            motionZ *= drag;
        }
        return false;
    }
}
