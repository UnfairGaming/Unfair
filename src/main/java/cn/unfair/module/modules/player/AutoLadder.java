package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.SwapItemEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.world.Scaffold;
import cn.unfair.util.BlockUtil;
import cn.unfair.util.PacketUtil;
import cn.unfair.util.RayCastUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.player.SimulatedPlayer;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.*;

public final class AutoLadder extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double PREPARE_MOTION_Y = 0.25D;
    private static final double LADDER_THICKNESS = 0.1875D;
    private static final double TARGET_LADDER_GAP = 0.10D;
    private static final double MIN_LADDER_GAP = 0.025D;
    private static final double MAX_LADDER_GAP = 0.20D;
    private static final double AIR_FRICTION = 0.91D;
    private static final int MAX_ACTIVE_TICKS = 32;
    private static final int PREDICTION_TICKS = 45;

    private int state;
    private int lastGroundBlockY = Integer.MIN_VALUE;
    private int oldSlot = -1;
    private int blockSlot = -1;
    private int ladderSlot = -1;
    private int activeTicks;
    private BlockPos landing;
    private BlockPos support;
    private BlockPos top;
    private EnumFacing forward;

    public AutoLadder() {
        super("AutoLadder", false);
    }

    private static double square(double value) {
        return value * value;
    }

    @EventTarget(Priority.HIGHEST)
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && this.state != 0) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null
                || mc.theWorld == null || !mc.playerController.gameIsSurvivalOrAdventure()) {
            return;
        }

        if (mc.thePlayer.onGround) {
            this.lastGroundBlockY = MathHelper.floor_double(mc.thePlayer.getEntityBoundingBox().minY - 0.01D);
        }

        if (this.shouldYieldPlacement()) {
            if (this.state != 0) {
                this.clear(true);
            }
            return;
        }

        if (this.state != 0) {
            if (++this.activeTicks > MAX_ACTIVE_TICKS || mc.thePlayer.onGround) {
                this.clear(true);
                return;
            }
        }

        if (this.state == 0) {
            if (mc.thePlayer.onGround || mc.thePlayer.motionY > PREPARE_MOTION_Y) {
                return;
            }
            this.forward = EnumFacing.fromAngle(event.getNewYaw());
            this.landing = this.predictLanding();
            this.ladderSlot = this.findLadder();
            this.blockSlot = this.findFullBlock();
            if (this.landing == null || this.ladderSlot < 0 || !this.hasMinimumDropHeight(this.landing)) {
                return;
            }
            PlacementTarget target = this.selectPlacementTarget(this.landing);
            if (target == null) {
                this.clear(false);
                return;
            }
            this.applyPlacementTarget(target);
            this.oldSlot = mc.thePlayer.inventory.currentItem;
            this.state = BlockUtil.isReplaceable(this.top) ? 1 : 3;
        }

        if (this.state == 1) {
            this.updatePlacementTarget();
            if (!this.validSupport()) {
                this.clear(true);
                return;
            }

            if (this.getGapToBlock(this.support) < 0.05) {
                return;
            }
            AxisAlignedBB playerBox = mc.thePlayer.getEntityBoundingBox();
            AxisAlignedBB supportBox = new AxisAlignedBB(
                    this.support.getX(), this.support.getY(), this.support.getZ(),
                    this.support.getX() + 1.0, this.support.getY() + 1.0, this.support.getZ() + 1.0
            );
            if (playerBox.intersectsWith(supportBox)) {
                return;
            }

            double dist = Math.sqrt(this.distanceSqToBlock(this.support));
            double reach = mc.playerController.getBlockReachDistance();
            if (dist > reach + 0.3) {
                this.updatePlacementTarget();
                if (this.support == null || Math.sqrt(this.distanceSqToBlock(this.support)) > reach + 0.3) {
                    this.clear(true);
                    return;
                }
                return;
            }
            this.switchSlot(this.blockSlot);
            this.aim(event, this.support, EnumFacing.UP);
            if (this.place(event, this.support, EnumFacing.UP)) {
                this.state = 3;
            }
            return;
        }

        if (this.state == 3) {
            this.switchSlot(this.ladderSlot);
            this.state = 4;
            return;
        }

        if (this.state == 4) {
            EnumFacing face = this.forward.getOpposite();

            if (!this.canPlaceLadder(face)) {
                return;
            }
            this.aim(event, this.top, face);
            if (this.place(event, this.top, face)) {
                this.state = 5;
            }
            return;
        }

        if (this.state == 5) {

            if (mc.thePlayer.isOnLadder()) {
                this.state = 6;
            }
            return;
        }

        if (this.state == 6) {
            this.switchSlot(this.oldSlot);
            this.clear(false);
        }
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }

        if (this.state != 0 || (this.forward != null && this.landing != null && this.top != null && this.support != null)) {
            this.applyDirectionalBrake();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onPostUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.POST || mc.thePlayer == null) {
            return;
        }
        if (this.state == 5) {
            this.state = 6;
        }
    }

    private boolean switchSlot(int slot) {
        if (slot < 0) {
            return false;
        }
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.syncCurrentPlayItem();
            return true;
        }
        return false;
    }

    private boolean place(UpdateEvent event, BlockPos target, EnumFacing face) {
        if (!this.reachable(target)) {
            return false;
        }
        MovingObjectPosition hit = RayCastUtil.rayTrace(event.getNewYaw(), event.getNewPitch(),
                mc.playerController.getBlockReachDistance(), 1.0F);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !target.equals(hit.getBlockPos()) || hit.sideHit != face) {
            return false;
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld,
                mc.thePlayer.inventory.getCurrentItem(), target, face, hit.hitVec)) {
            PacketUtil.sendPacket(new C0APacketAnimation());
            return true;
        }
        return false;
    }

    private void aim(UpdateEvent event, BlockPos target, EnumFacing face) {
        Vec3 hit = new Vec3(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.5D);
        switch (face) {
            case UP:
                hit = new Vec3(hit.xCoord, target.getY() + 0.99D, hit.zCoord);
                break;
            case DOWN:
                hit = new Vec3(hit.xCoord, target.getY() + 0.01D, hit.zCoord);
                break;
            case NORTH:
                hit = new Vec3(hit.xCoord, hit.yCoord, target.getZ() + 0.01D);
                break;
            case SOUTH:
                hit = new Vec3(hit.xCoord, hit.yCoord, target.getZ() + 0.99D);
                break;
            case WEST:
                hit = new Vec3(target.getX() + 0.01D, hit.yCoord, hit.zCoord);
                break;
            case EAST:
                hit = new Vec3(target.getX() + 0.99D, hit.yCoord, hit.zCoord);
                break;
            default:
                break;
        }
        float[] rotation = RotationUtil.getRotations(hit.xCoord, hit.yCoord, hit.zCoord,
                mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ);
        event.setRotation(rotation[0], rotation[1], 6);
        event.setPervRotation(rotation[0], 6);
    }

    private boolean validSupport() {
        Block block = mc.theWorld.getBlockState(this.support).getBlock();
        return block != Blocks.air && !BlockUtil.isReplaceable(block) && BlockUtil.isSolid(block);
    }

    private boolean canPlaceLadder(EnumFacing face) {


        if (!mc.theWorld.getBlockState(this.top).getBlock().isNormalCube()) {
            return false;
        }
        BlockPos ladderPos = this.top.offset(face);
        return BlockUtil.isReplaceable(ladderPos);
    }

    private AxisAlignedBB ladderBox(BlockPos pos, EnumFacing face) {
        double x1 = pos.getX(), y1 = pos.getY(), z1 = pos.getZ();
        double x2 = x1 + 1.0D, y2 = y1 + 1.0D, z2 = z1 + 1.0D, t = LADDER_THICKNESS;
        if (face == EnumFacing.NORTH) z1 = z2 - t;
        if (face == EnumFacing.SOUTH) z2 = z1 + t;
        if (face == EnumFacing.WEST) x1 = x2 - t;
        if (face == EnumFacing.EAST) x2 = x1 + t;
        return new AxisAlignedBB(x1, y1, z1, x2, y2, z2);
    }

    private boolean reachable(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double x = MathHelper.clamp_double(eyes.xCoord, pos.getX(), pos.getX() + 1.0D);
        double y = MathHelper.clamp_double(eyes.yCoord, pos.getY(), pos.getY() + 1.0D);
        double z = MathHelper.clamp_double(eyes.zCoord, pos.getZ(), pos.getZ() + 1.0D);
        double reach = mc.playerController.getBlockReachDistance();
        return eyes.squareDistanceTo(new Vec3(x, y, z)) <= reach * reach;
    }

    private BlockPos predictLanding() {
        SimulatedPlayer simulated = SimulatedPlayer.fromClientPlayer(mc.thePlayer.movementInput, false, true);
        simulated.rotationYaw = mc.thePlayer.rotationYaw;
        BlockPos lastCandidate = null;
        BlockPos firstCandidate = null;
        for (int i = 0; i < PREDICTION_TICKS; i++) {
            simulated.tick();
            Vec3 p = simulated.getPos();
            BlockPos pos = new BlockPos(MathHelper.floor_double(p.xCoord), MathHelper.floor_double(p.yCoord - 0.01D), MathHelper.floor_double(p.zCoord));
            Block block = mc.theWorld.getBlockState(pos).getBlock();
            if (block != Blocks.air && BlockUtil.isSolid(block) && BlockUtil.isReplaceable(pos.up())) {
                if (firstCandidate == null) firstCandidate = pos;
                lastCandidate = pos;
            }
            if (simulated.onGround) {
                break;
            }
        }
        if (firstCandidate != null && lastCandidate != null) {
            double distFirst = Math.sqrt(
                    Math.pow(firstCandidate.getX() + 0.5 - mc.thePlayer.posX, 2) +
                            Math.pow(firstCandidate.getZ() + 0.5 - mc.thePlayer.posZ, 2)
            );
            double distLast = Math.sqrt(
                    Math.pow(lastCandidate.getX() + 0.5 - mc.thePlayer.posX, 2) +
                            Math.pow(lastCandidate.getZ() + 0.5 - mc.thePlayer.posZ, 2)
            );
            return distFirst < distLast ? firstCandidate : lastCandidate;
        }
        return lastCandidate;
    }

    private boolean hasMinimumDropHeight(BlockPos predictedLanding) {
        if (this.lastGroundBlockY != Integer.MIN_VALUE) {
            return this.lastGroundBlockY - predictedLanding.getY() >= 3;
        }
        return mc.thePlayer.posY - (predictedLanding.getY() + 1.0D) >= 3.0D;
    }

    private int findLadder() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemBlock && ((ItemBlock) stack.getItem()).getBlock() == Blocks.ladder)
                return i;
        }
        return -1;
    }

    private int findFullBlock() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (stack == null || !(stack.getItem() instanceof ItemBlock)) continue;
            Block block = ((ItemBlock) stack.getItem()).getBlock();
            if (block != Blocks.ladder && block.isFullCube() && BlockUtil.isSolid(block) && !BlockUtil.isInteractable(block))
                return i;
        }
        return -1;
    }

    private boolean shouldYieldPlacement() {
        if (Unfair.moduleManager == null) {
            return false;
        }
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.getModule(Scaffold.class);
        if (scaffold != null && scaffold.isEnabled()) {
            return true;
        }
        AutoMLG autoMLG = (AutoMLG) Unfair.moduleManager.getModule(AutoMLG.class);
        return autoMLG != null && autoMLG.isEnabled() && this.hasWaterBucket();
    }

    private boolean hasWaterBucket() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            if (stack != null && stack.getItem() == Items.water_bucket) {
                return true;
            }
        }
        return false;
    }

    private void applyDirectionalBrake() {


        if (this.forward == null || this.landing == null || this.top == null || this.support == null) {
            return;
        }


        AxisAlignedBB playerBox = mc.thePlayer.getEntityBoundingBox();
        AxisAlignedBB supportBox = new AxisAlignedBB(
                this.support.getX(), this.support.getY(), this.support.getZ(),
                this.support.getX() + 1.0, this.support.getY() + 1.0, this.support.getZ() + 1.0
        );
        if (playerBox.intersectsWith(supportBox)) {
            double directionX = this.forward.getFrontOffsetX();
            double directionZ = this.forward.getFrontOffsetZ();
            float movementYaw = RotationState.isActived()
                    ? RotationState.getSmoothedYaw()
                    : mc.thePlayer.rotationYaw;
            double yaw = movementYaw * Math.PI / 180.0D;
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            float keyValue = mc.thePlayer.movementInput.sneak ? 0.3F : 1.0F;
            KeyInput backInput = closestKeysForDirection(-directionX, -directionZ, sin, cos, keyValue);
            applyKeys(backInput);
            return;
        }


        if (this.state == 5) {
            float keyValue = mc.thePlayer.movementInput.sneak ? 0.3F : 1.0F;
            applyKeys(new KeyInput(keyValue, 0.0F));
            return;
        }


        double centerX = this.landing.getX() + 0.5;
        double centerZ = this.landing.getZ() + 0.5;
        double backX = -this.forward.getFrontOffsetX();
        double backZ = -this.forward.getFrontOffsetZ();
        double targetX = centerX + backX * 0.15;
        double targetZ = centerZ + backZ * 0.15;

        double dx = targetX - mc.thePlayer.posX;
        double dz = targetZ - mc.thePlayer.posZ;
        double distToTarget = Math.sqrt(dx * dx + dz * dz);

        if (distToTarget > 0.1) {
            double dirX = dx / distToTarget;
            double dirZ = dz / distToTarget;
            float movementYaw = RotationState.isActived()
                    ? RotationState.getSmoothedYaw()
                    : mc.thePlayer.rotationYaw;
            double yaw = movementYaw * Math.PI / 180.0D;
            double sin = Math.sin(yaw);
            double cos = Math.cos(yaw);
            float keyValue = mc.thePlayer.movementInput.sneak ? 0.3F : 1.0F;
            KeyInput targetInput = closestKeysForDirection(dirX, dirZ, sin, cos, keyValue);
            applyKeys(targetInput);
        }

    }

    private double gapToLadder(AxisAlignedBB playerBox, AxisAlignedBB ladderBox) {
        switch (this.forward) {
            case EAST:
                return ladderBox.minX - playerBox.maxX;
            case WEST:
                return playerBox.minX - ladderBox.maxX;
            case SOUTH:
                return ladderBox.minZ - playerBox.maxZ;
            case NORTH:
                return playerBox.minZ - ladderBox.maxZ;
            default:
                return 0.0D;
        }
    }

    private double getGapToBlock(BlockPos pos) {
        if (pos == null || this.forward == null) return Double.MAX_VALUE;
        AxisAlignedBB playerBox = mc.thePlayer.getEntityBoundingBox();
        AxisAlignedBB blockBox = new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D
        );
        switch (this.forward) {
            case EAST:
                return blockBox.minX - playerBox.maxX;
            case WEST:
                return playerBox.minX - blockBox.maxX;
            case SOUTH:
                return blockBox.minZ - playerBox.maxZ;
            case NORTH:
                return playerBox.minZ - blockBox.maxZ;
            default:
                return 0.0D;
        }
    }

    private ControlChoice findBestControl(int horizon, double gap, double speed,
                                          double directionX, double directionZ, double sin, double cos,
                                          float keyValue, KeyInput original) {
        ControlChoice best = null;
        for (int forwardKey = -1; forwardKey <= 1; forwardKey++) {
            for (int strafeKey = -1; strafeKey <= 1; strafeKey++) {
                KeyInput input = new KeyInput(forwardKey * keyValue, strafeKey * keyValue);
                double acceleration = this.towardAcceleration(
                        input, directionX, directionZ, sin, cos);
                double nextSpeed = (speed + acceleration) * AIR_FRICTION;
                double nextGap = gap - speed - acceleration;
                double inputCost = 0.004D * (square(input.forward() - original.forward())
                        + square(input.strafe() - original.strafe()));

                ControlChoice tail = horizon > 1
                        ? this.findBestControl(horizon - 1, nextGap, nextSpeed,
                        directionX, directionZ, sin, cos, keyValue, original)
                        : new ControlChoice(input, this.terminalControlCost(nextGap, nextSpeed));
                double cost = inputCost + tail.cost();
                if (best == null || cost < best.cost()) {
                    best = new ControlChoice(input, cost);
                }
            }
        }
        return best;
    }

    private double towardAcceleration(KeyInput input, double directionX, double directionZ,
                                      double sin, double cos) {
        double forwardInput = input.forward() * 0.98D;
        double strafeInput = input.strafe() * 0.98D;
        double lengthSquared = forwardInput * forwardInput + strafeInput * strafeInput;
        if (lengthSquared < 1.0E-7D) {
            return 0.0D;
        }
        double moveFactor = mc.thePlayer.isSprinting() ? 0.025999999D : 0.02D;
        double scale = lengthSquared > 1.0D ? moveFactor / Math.sqrt(lengthSquared) : moveFactor;
        double accelerationX = strafeInput * scale * cos - forwardInput * scale * sin;
        double accelerationZ = forwardInput * scale * cos + strafeInput * scale * sin;
        return accelerationX * directionX + accelerationZ * directionZ;
    }

    private double terminalControlCost(double gap, double speed) {
        double cost = square(gap - TARGET_LADDER_GAP) * 12.0D;
        if (gap < MIN_LADDER_GAP) {
            cost += square(MIN_LADDER_GAP - gap) * 600.0D + 20.0D;
        } else if (gap > MAX_LADDER_GAP) {
            cost += square(gap - MAX_LADDER_GAP) * 80.0D + 2.0D;
        }
        if (speed > 0.03) {
            cost += square(speed - 0.03) * 300.0D;
        }
        if (speed < -0.03) {
            cost += square(speed + 0.03) * 60.0D;
        }
        return cost;
    }

    private KeyInput closestKeysForDirection(double worldX, double worldZ, double sin, double cos,
                                             float keyValue) {
        KeyInput best = new KeyInput(0.0F, 0.0F);
        double bestDot = Double.NEGATIVE_INFINITY;
        for (int forwardKey = -1; forwardKey <= 1; forwardKey++) {
            for (int strafeKey = -1; strafeKey <= 1; strafeKey++) {
                if (forwardKey == 0 && strafeKey == 0) {
                    continue;
                }
                KeyInput input = new KeyInput(forwardKey * keyValue, strafeKey * keyValue);
                double forwardInput = input.forward();
                double strafeInput = input.strafe();
                double length = Math.sqrt(forwardInput * forwardInput + strafeInput * strafeInput);
                double inputX = (strafeInput * cos - forwardInput * sin) / length;
                double inputZ = (forwardInput * cos + strafeInput * sin) / length;
                double dot = inputX * worldX + inputZ * worldZ;
                if (dot > bestDot) {
                    bestDot = dot;
                    best = input;
                }
            }
        }
        return best;
    }

    private void applyKeys(KeyInput input) {
        mc.thePlayer.movementInput.moveForward = input.forward();
        mc.thePlayer.movementInput.moveStrafe = input.strafe();
    }

    private void updatePlacementTarget() {
        BlockPos predicted = this.predictLanding();
        if (predicted != null && this.hasMinimumDropHeight(predicted)) {
            PlacementTarget target = this.selectPlacementTarget(predicted);
            if (target != null) {
                this.applyPlacementTarget(target);
            }
        }
    }

    private PlacementTarget selectPlacementTarget(BlockPos predictedLanding) {
        PlacementTarget best = null;
        double[] thresholds = {2.0, 3.5};

        for (double maxDist : thresholds) {
            for (int shift = -1; shift <= 1; shift++) {
                BlockPos candidateLanding = predictedLanding.offset(this.forward, shift);
                BlockPos candidateSupport = candidateLanding.offset(this.forward);
                BlockPos candidateTop = candidateSupport.up();

                Block supportBlock = mc.theWorld.getBlockState(candidateSupport).getBlock();
                if (supportBlock == Blocks.air || BlockUtil.isReplaceable(supportBlock)
                        || !BlockUtil.isSolid(supportBlock)) {
                    continue;
                }

                boolean needsSupportBlock = BlockUtil.isReplaceable(candidateTop);
                if (needsSupportBlock && this.blockSlot < 0
                        || !needsSupportBlock && !mc.theWorld.getBlockState(candidateTop).getBlock().isNormalCube()) {
                    continue;
                }

                EnumFacing ladderFace = this.forward.getOpposite();
                BlockPos ladderPos = candidateTop.offset(ladderFace);
                if (!BlockUtil.isReplaceable(ladderPos)) {
                    continue;
                }

                double horizDist = Math.sqrt(
                        Math.pow(candidateSupport.getX() - mc.thePlayer.posX, 2) +
                                Math.pow(candidateSupport.getZ() - mc.thePlayer.posZ, 2)
                );
                if (horizDist > maxDist) {
                    continue;
                }

                AxisAlignedBB playerBox = mc.thePlayer.getEntityBoundingBox();
                AxisAlignedBB supportBox = new AxisAlignedBB(
                        candidateSupport.getX(), candidateSupport.getY(), candidateSupport.getZ(),
                        candidateSupport.getX() + 1.0, candidateSupport.getY() + 1.0, candidateSupport.getZ() + 1.0
                );
                if (playerBox.intersectsWith(supportBox)) {
                    continue;
                }

                double score = this.scorePlacementTarget(candidateSupport, candidateTop, shift);
                if (best == null || score < best.score()) {
                    best = new PlacementTarget(candidateLanding, candidateSupport, candidateTop, score);
                }
            }
            if (best != null) break;
        }
        return best;
    }

    private double scorePlacementTarget(BlockPos candidateSupport, BlockPos candidateTop, int shift) {
        EnumFacing ladderFace = this.forward.getOpposite();
        AxisAlignedBB targetBox = this.ladderBox(candidateTop.offset(ladderFace), ladderFace);
        AxisAlignedBB playerBox = mc.thePlayer.getEntityBoundingBox();
        double directionX = this.forward.getFrontOffsetX();
        double directionZ = this.forward.getFrontOffsetZ();
        double speed = mc.thePlayer.motionX * directionX + mc.thePlayer.motionZ * directionZ;
        double gap = this.gapToLadder(playerBox, targetBox);
        float movementYaw = RotationState.isActived()
                ? RotationState.getSmoothedYaw()
                : mc.thePlayer.rotationYaw;
        double yaw = movementYaw * Math.PI / 180.0D;
        double sin = Math.sin(yaw);
        double cos = Math.cos(yaw);
        float keyValue = mc.thePlayer.movementInput.sneak ? 0.3F : 1.0F;
        KeyInput original = new KeyInput(
                Math.signum(mc.thePlayer.movementInput.moveForward) * keyValue,
                Math.signum(mc.thePlayer.movementInput.moveStrafe) * keyValue
        );
        int horizon = this.reachable(candidateSupport) ? 4 : 5;
        double score = this.findBestControl(
                horizon, gap, speed, directionX, directionZ, sin, cos, keyValue, original).cost();

        double reach = mc.playerController.getBlockReachDistance();
        double supportDistance = Math.sqrt(this.distanceSqToBlock(candidateSupport));
        double idealDist = 1.8;
        double distPenalty = square(supportDistance - idealDist) * 12.0;
        if (supportDistance > reach) {
            distPenalty += square(supportDistance - reach) * 40.0 + 6.0;
        }
        if (supportDistance < 0.5) {
            distPenalty += square(0.5 - supportDistance) * 100.0;
        }
        return score + distPenalty + Math.abs(shift) * 0.025D;
    }

    private double distanceSqToBlock(BlockPos pos) {
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double x = MathHelper.clamp_double(eyes.xCoord, pos.getX(), pos.getX() + 1.0D);
        double y = MathHelper.clamp_double(eyes.yCoord, pos.getY(), pos.getY() + 1.0D);
        double z = MathHelper.clamp_double(eyes.zCoord, pos.getZ(), pos.getZ() + 1.0D);
        return eyes.squareDistanceTo(new Vec3(x, y, z));
    }

    private void applyPlacementTarget(PlacementTarget target) {
        this.landing = target.landing();
        this.support = target.support();
        this.top = target.top();
    }

    private void clear(boolean restore) {
        if (restore && oldSlot >= 0 && mc.thePlayer != null) {
            mc.thePlayer.inventory.currentItem = oldSlot;
            mc.playerController.syncCurrentPlayItem();
        }
        state = 0;
        activeTicks = 0;
        oldSlot = blockSlot = ladderSlot = -1;
        landing = support = top = null;
        forward = null;
    }

    @Override
    public void onEnabled() {
        clear(false);
    }

    @Override
    public void onDisabled() {
        clear(true);
    }

    private record KeyInput(float forward, float strafe) {
    }

    private record ControlChoice(KeyInput first, double cost) {
    }

    private record PlacementTarget(BlockPos landing, BlockPos support, BlockPos top, double score) {
    }
}