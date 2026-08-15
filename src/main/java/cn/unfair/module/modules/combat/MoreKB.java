/*
 * LiquidBounce Hacked Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/CCBlueX/LiquidBounce/
 */
package cn.unfair.module.modules.combat;

import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public class MoreKB extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final ModeProperty mode = new ModeProperty(
            "Mode",
            3,
            new String[]{"WTap", "SprintTap", "SprintTap2", "Old", "Silent", "Packet", "SneakPacket"}
    );
    public final IntProperty chance = new IntProperty("Chance", 100, 0, 100);
    public final IntProperty delay = new IntProperty("Delay", 0, 0, 500);
    public final IntProperty hurtTime = new IntProperty("Hurt Time", 10, 0, 10);

    public final IntProperty ticksUntilBlockMin = new IntProperty(
            "Ticks Until Block Min", 0, 0, 5, () -> this.mode.getValue() == 0
    );
    public final IntProperty ticksUntilBlockMax = new IntProperty(
            "Ticks Until Block Max", 2, 0, 5, () -> this.mode.getValue() == 0
    );
    public final IntProperty reSprintTicksMin = new IntProperty(
            "Re Sprint Ticks Min", 1, 1, 5, () -> this.mode.getValue() == 0
    );
    public final IntProperty reSprintTicksMax = new IntProperty(
            "Re Sprint Ticks Max", 2, 1, 5, () -> this.mode.getValue() == 0
    );
    public final IntProperty targetDistance = new IntProperty(
            "Target Distance", 3, 1, 5, () -> this.mode.getValue() == 0
    );

    public final IntProperty pressBackTicks = new IntProperty(
            "Press Back Ticks", 1, 1, 5, () -> this.mode.getValue() == 2
    );
    public final IntProperty releaseBackTicks = new IntProperty(
            "Release Back Ticks", 2, 1, 5, () -> this.mode.getValue() == 2
    );

    public final FloatProperty minEnemyRotDiffToIgnore = new FloatProperty(
            "Min Rotation Diff From Enemy To Ignore", 180.0F, 0.0F, 180.0F
    );

    public final BooleanProperty onlyGround = new BooleanProperty("Only Ground", false);
    public final BooleanProperty onlyMove = new BooleanProperty("Only Move", true);
    public final BooleanProperty onlyMoveForward = new BooleanProperty(
            "Only Move Forward", true, this.onlyMove::getValue
    );
    public final BooleanProperty onlyWhenTargetGoesBack = new BooleanProperty("Only When Target Goes Back", false);

    private int ticks;
    private int forceSprintState;
    private final TimerUtil timer = new TimerUtil();

    private int blockInputTicks = this.randomTicksUntilBlock();
    private int blockTicksElapsed;
    private boolean startWaiting;
    private boolean blockInput;
    private int allowInputTicks = this.randomReSprintTicks();
    private int ticksElapsed;

    private int sprintTicks;

    public MoreKB() {
        super("MoreKB", false);
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || !(event.getTarget() instanceof EntityLivingBase target)) {
            return;
        }

        EntityPlayerSP player = mc.thePlayer;
        double distance = getDistanceToEntityBox(player, target);
        float rotationToPlayer = getRotationToPlayer(player, target);
        float angleDifferenceToPlayer = Math.abs(MathHelper.wrapAngleTo180_float(rotationToPlayer - target.rotationYaw));

        if (target.hurtTime > this.hurtTime.getValue()
                || !this.timer.hasTimeElapsed(this.delay.getValue())
                || this.onlyGround.getValue() && !player.onGround
                || ThreadLocalRandom.current().nextInt(100) > this.chance.getValue()) {
            return;
        }

        if (this.onlyMove.getValue()
                && (!isMoving(player)
                || this.onlyMoveForward.getValue() && player.movementInput.moveStrafe != 0.0F)) {
            return;
        }

        AxisAlignedBB targetHitBox = getHitBox(target);
        if (angleDifferenceToPlayer > this.minEnemyRotDiffToIgnore.getValue()
                && !targetHitBox.isVecInside(player.getPositionEyes(1.0F))) {
            return;
        }

        double motionX = target.posX - target.lastTickPosX;
        double motionY = target.posY - target.lastTickPosY;
        double motionZ = target.posZ - target.lastTickPosZ;
        double distanceBasedOnMotion = getDistanceToBox(player, targetHitBox.offset(motionX, motionY, motionZ));

        if (this.onlyWhenTargetGoesBack.getValue() && distanceBasedOnMotion >= distance) {
            return;
        }

        switch (this.mode.getValue()) {
            case 3 -> {
                if (player.isSprinting()) {
                    this.sendAction(C0BPacketEntityAction.Action.STOP_SPRINTING);
                }

                this.sendAction(C0BPacketEntityAction.Action.START_SPRINTING);
                this.sendAction(C0BPacketEntityAction.Action.STOP_SPRINTING);
                this.sendAction(C0BPacketEntityAction.Action.START_SPRINTING);
                player.setSprinting(true);
                player.setServerSprintState(true);
            }

            case 1, 4 -> {
                if (player.isSprinting() && player.getServerSprintState()) {
                    this.ticks = 2;
                }
            }

            case 5 -> {
                this.sendAction(C0BPacketEntityAction.Action.STOP_SPRINTING);
                this.sendAction(C0BPacketEntityAction.Action.START_SPRINTING);
            }

            case 6 -> {
                this.sendAction(C0BPacketEntityAction.Action.STOP_SPRINTING);
                this.sendAction(C0BPacketEntityAction.Action.START_SNEAKING);
                this.sendAction(C0BPacketEntityAction.Action.START_SPRINTING);
                this.sendAction(C0BPacketEntityAction.Action.STOP_SNEAKING);
            }

            case 0 -> {
                if (player.isSprinting() && player.getServerSprintState() && !this.blockInput && !this.startWaiting) {
                    double delayMultiplier = 1.0D / (Math.abs(this.targetDistance.getValue() - distance) + 1.0D);

                    this.blockInputTicks = (int) (this.randomTicksUntilBlock() * delayMultiplier);
                    this.blockInput = this.blockInputTicks == 0;

                    if (!this.blockInput) {
                        this.startWaiting = true;
                    }

                    this.allowInputTicks = (int) (this.randomReSprintTicks() * delayMultiplier);
                }
            }

            case 2 -> {
                if (++this.sprintTicks == this.pressBackTicks.getValue()) {
                    if (player.isSprinting() && player.getServerSprintState()) {
                        player.setSprinting(false);
                        player.setServerSprintState(false);
                    } else {
                        player.setSprinting(true);
                        player.setServerSprintState(true);
                    }

                    player.motionX = 0.0D;
                    player.motionZ = 0.0D;
                } else if (this.sprintTicks >= this.releaseBackTicks.getValue()) {
                    player.setSprinting(false);
                    player.setServerSprintState(false);
                    this.sprintTicks = 0;
                }
            }
        }

        this.timer.reset();
    }

    @EventTarget
    public void onPostSprintUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled() || this.mode.getValue() != 1 || mc.thePlayer == null) {
            return;
        }

        switch (this.ticks) {
            case 2 -> {
                mc.thePlayer.setSprinting(false);
                this.forceSprintState = 2;
                --this.ticks;
            }

            case 1 -> {
                if (mc.thePlayer.movementInput.moveForward > 0.8F) {
                    mc.thePlayer.setSprinting(true);
                }
                this.forceSprintState = 1;
                --this.ticks;
            }

            default -> this.forceSprintState = 0;
        }
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || this.mode.getValue() != 0) {
            return;
        }

        if (this.blockInput) {
            if (this.ticksElapsed++ >= this.allowInputTicks) {
                this.blockInput = false;
                this.ticksElapsed = 0;
            }
        } else if (this.startWaiting) {
            this.blockInput = this.blockTicksElapsed++ >= this.blockInputTicks;

            if (this.blockInput) {
                this.startWaiting = false;
                this.blockTicksElapsed = 0;
            }
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.shouldBlockInput() || mc.thePlayer == null || !this.onlyMove.getValue()) {
            return;
        }

        mc.thePlayer.movementInput.moveForward = 0.0F;
        if (!this.onlyMoveForward.getValue()) {
            mc.thePlayer.movementInput.moveStrafe = 0.0F;
        }
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()
                || event.getType() != EventType.SEND
                || !(event.getPacket() instanceof C03PacketPlayer)
                || this.mode.getValue() != 4
                || mc.thePlayer == null) {
            return;
        }

        if (this.ticks == 2) {
            this.sendAction(C0BPacketEntityAction.Action.STOP_SPRINTING);
            --this.ticks;
        } else if (this.ticks == 1 && mc.thePlayer.isSprinting()) {
            this.sendAction(C0BPacketEntityAction.Action.START_SPRINTING);
            --this.ticks;
        }
    }

    public boolean shouldBlockInput() {
        return this.isEnabled() && this.mode.getValue() == 0 && this.blockInput;
    }

    public boolean breakSprint() {
        return this.isEnabled() && this.forceSprintState == 2 && this.mode.getValue() == 1;
    }

    public boolean startSprint() {
        return this.isEnabled() && this.forceSprintState == 1 && this.mode.getValue() == 1;
    }

    @Override
    public void onEnabled() {
        this.resetTransientState();
    }

    @Override
    public void onDisabled() {
        this.resetTransientState();
    }

    @Override
    public void verifyValue(String name) {
        if (this.ticksUntilBlockMin.getName().equals(name)
                && this.ticksUntilBlockMin.getValue() > this.ticksUntilBlockMax.getValue()) {
            this.ticksUntilBlockMax.setValue(this.ticksUntilBlockMin.getValue());
        } else if (this.ticksUntilBlockMax.getName().equals(name)
                && this.ticksUntilBlockMin.getValue() > this.ticksUntilBlockMax.getValue()) {
            this.ticksUntilBlockMin.setValue(this.ticksUntilBlockMax.getValue());
        } else if (this.reSprintTicksMin.getName().equals(name)
                && this.reSprintTicksMin.getValue() > this.reSprintTicksMax.getValue()) {
            this.reSprintTicksMax.setValue(this.reSprintTicksMin.getValue());
        } else if (this.reSprintTicksMax.getName().equals(name)
                && this.reSprintTicksMin.getValue() > this.reSprintTicksMax.getValue()) {
            this.reSprintTicksMin.setValue(this.reSprintTicksMax.getValue());
        } else if (this.pressBackTicks.getName().equals(name)
                && this.pressBackTicks.getValue() > this.releaseBackTicks.getValue()) {
            this.pressBackTicks.setValue(this.releaseBackTicks.getValue());
        } else if (this.releaseBackTicks.getName().equals(name)
                && this.releaseBackTicks.getValue() < this.pressBackTicks.getValue()) {
            this.releaseBackTicks.setValue(this.pressBackTicks.getValue());
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.mode.getModeString()};
    }

    private void resetTransientState() {
        this.blockInput = false;
        this.startWaiting = false;
        this.blockTicksElapsed = 0;
        this.ticksElapsed = 0;
        this.sprintTicks = 0;
    }

    private void sendAction(C0BPacketEntityAction.Action action) {
        mc.thePlayer.sendQueue.addToSendQueue(new C0BPacketEntityAction(mc.thePlayer, action));
    }

    private int randomTicksUntilBlock() {
        return randomInclusive(this.ticksUntilBlockMin.getValue(), this.ticksUntilBlockMax.getValue());
    }

    private int randomReSprintTicks() {
        return randomInclusive(this.reSprintTicksMin.getValue(), this.reSprintTicksMax.getValue());
    }

    private static int randomInclusive(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static boolean isMoving(EntityLivingBase entity) {
        return entity.moveForward != 0.0F || entity.moveStrafing != 0.0F;
    }

    private static AxisAlignedBB getHitBox(Entity entity) {
        double borderSize = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(borderSize, borderSize, borderSize);
    }

    private static double getDistanceToEntityBox(Entity player, Entity target) {
        return getDistanceToBox(player, getHitBox(target));
    }

    private static double getDistanceToBox(Entity player, AxisAlignedBB box) {
        Vec3 eyes = player.getPositionEyes(1.0F);
        Vec3 nearest = new Vec3(
                MathHelper.clamp_double(eyes.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(eyes.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(eyes.zCoord, box.minZ, box.maxZ)
        );
        return eyes.distanceTo(nearest);
    }

    private static float getRotationToPlayer(EntityPlayerSP player, EntityLivingBase target) {
        AxisAlignedBB playerHitBox = getHitBox(player);
        double targetX = (playerHitBox.minX + playerHitBox.maxX) * 0.5D;
        double targetZ = (playerHitBox.minZ + playerHitBox.maxZ) * 0.5D;
        Vec3 targetEyes = target.getPositionEyes(1.0F);
        float yaw = MathHelper.wrapAngleTo180_float(
                (float) Math.toDegrees(Math.atan2(targetZ - targetEyes.zCoord, targetX - targetEyes.xCoord)) - 90.0F
        );

        float sensitivityFactor = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        float gcd = sensitivityFactor * sensitivityFactor * sensitivityFactor * 1.2F;
        return player.getLastReportedYaw() + Math.round((yaw - player.getLastReportedYaw()) / gcd) * gcd;
    }
}
