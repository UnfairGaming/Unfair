package unfair.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.Vec3;
import unfair.Unfair;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.event.types.Priority;
import unfair.events.MoveInputEvent;
import unfair.events.UpdateEvent;
import unfair.management.RotationState;
import unfair.module.Module;
import unfair.property.properties.BooleanProperty;
import unfair.property.properties.FloatProperty;
import unfair.property.properties.IntProperty;
import unfair.util.MoveUtil;
import unfair.util.PacketUtil;
import unfair.util.TeamUtil;

import java.util.ArrayList;
import java.util.Comparator;

public class AutoProjectiles extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public final FloatProperty range = new FloatProperty("Range", 8.0F, 3.0F, 15.0F);
    public final IntProperty amount = new IntProperty("Amount", 1, 1, 10);
    public final BooleanProperty prediction = new BooleanProperty("Prediction", true);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);
    private EntityLivingBase target = null;
    private int lastSlot = -1;
    private long lastThrowTime = 0L;
    private int throwState = 0;
    private int throwsRemaining = 0;
    private boolean hasRotated = false;
    private SmartPredictor smartPredictor = new SmartPredictor();
    public AutoProjectiles() {
        super("AutoProjectile", false);
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == mc.thePlayer || entity.deathTime > 0) {
            return false;
        }
        if (!(entity instanceof EntityOtherPlayerMP)) {
            return false;
        }
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        if (distance > this.range.getValue()) {
            return false;
        }
        EntityPlayer player = (EntityPlayer) entity;
        if (TeamUtil.isFriend(player)) {
            return false;
        }
        return !this.teams.getValue() || !TeamUtil.isSameTeam(player);
    }

    private EntityLivingBase getTarget() {
        ArrayList<EntityLivingBase> targets = new ArrayList<>();
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (obj instanceof EntityLivingBase) {
                EntityLivingBase entity = (EntityLivingBase) obj;
                if (isValidTarget(entity)) {
                    targets.add(entity);
                }
            }
        }
        if (targets.isEmpty()) {
            return null;
        }
        targets.sort(Comparator.comparingDouble(entity -> mc.thePlayer.getDistanceToEntity(entity)));

        EntityLivingBase newTarget = targets.get(0);
        if (this.target != newTarget) {
            this.smartPredictor = new SmartPredictor();
        }

        return newTarget;
    }

    private boolean hasProjectile() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isProjectile(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProjectile(ItemStack stack) {
        if (stack == null) return false;
        Item item = stack.getItem();
        return item instanceof ItemSnowball || item instanceof ItemEgg;
    }

    private int getProjectileSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            if (isProjectile(stack)) {
                return i;
            }
        }
        return -1;
    }

    private Vec3 predictPosition(EntityLivingBase target) {
        long currentTime = System.currentTimeMillis();
        smartPredictor.addPosition(new Vec3(target.posX, target.posY, target.posZ), currentTime);

        if (!this.prediction.getValue()) {
            return new Vec3(target.posX, target.posY + target.getEyeHeight(), target.posZ);
        }

        double rawPing = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime();
        double networkDelay = rawPing / 1000.0;

        double clientProcessingDelay = 0.02;
        double serverProcessingDelay = 0.01;
        double packetDelay = networkDelay * 0.5;

        double distance = mc.thePlayer.getDistanceToEntity(target);
        final double PROJECTILE_SPEED = 20.0;
        final double GRAVITY = 0.03;

        double horizontalDistance = Math.sqrt(
                Math.pow(target.posX - mc.thePlayer.posX, 2) +
                        Math.pow(target.posZ - mc.thePlayer.posZ, 2)
        );
        double verticalDistance = (target.posY + target.getEyeHeight()) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());

        double horizontalTime = horizontalDistance / PROJECTILE_SPEED;
        double verticalTime = calculateVerticalFlightTime(verticalDistance, PROJECTILE_SPEED, GRAVITY);
        double actualFlightTime = Math.max(horizontalTime, verticalTime);

        double totalDelayCompensation = networkDelay + clientProcessingDelay + serverProcessingDelay + packetDelay;

        if (rawPing > 100) {
            totalDelayCompensation += (rawPing - 100) / 1000.0 * 0.8;
        }

        double basePredictionTime = actualFlightTime + totalDelayCompensation;

        Vec3 velocity = smartPredictor.getCurrentVelocity();
        double targetSpeed = Math.sqrt(velocity.xCoord * velocity.xCoord + velocity.zCoord * velocity.zCoord);

        if (targetSpeed > 0.2) {
            basePredictionTime += targetSpeed * 0.1;
        }

        double distanceFactor = Math.min(1.2, distance / 10.0);
        double finalPredictionTime = basePredictionTime * distanceFactor;

        Vec3 predictedPos = smartPredictor.predictNextPosition(finalPredictionTime);

        return new Vec3(predictedPos.xCoord, predictedPos.yCoord + target.getEyeHeight(), predictedPos.zCoord);
    }

    private double calculateVerticalFlightTime(double verticalDistance, double initialSpeed, double gravity) {

        double verticalComponent = initialSpeed * 0.2;

        if (verticalDistance >= 0) {
            double discriminant = verticalComponent * verticalComponent + 2 * gravity * verticalDistance;
            if (discriminant < 0) return 0;
            return (verticalComponent + Math.sqrt(discriminant)) / gravity;
        } else {
            double discriminant = verticalComponent * verticalComponent - 2 * gravity * verticalDistance;
            if (discriminant < 0) return 0;
            return (Math.sqrt(discriminant) - verticalComponent) / gravity;
        }
    }

    private long calculateSmartDelay() {
        if (target == null) return 800L;

        double distance = mc.thePlayer.getDistanceToEntity(target);

        if (distance <= 3.5) {
            return 0L;
        } else if (distance <= 3.8) {
            return 20L;
        } else if (distance <= 4.0) {
            return 70L;
        } else if (distance <= 4.5) {
            return 100L;
        } else if (distance <= 5.0) {
            return 200L;
        } else if (distance <= 10.0) {
            return 500L;
        } else {
            return 800L;
        }
    }

    private float[] getRotationsToPosition(Vec3 position) {
        double deltaX = position.xCoord - mc.thePlayer.posX;
        double deltaY = position.yCoord - mc.thePlayer.posY - mc.thePlayer.getEyeHeight();
        double deltaZ = position.zCoord - mc.thePlayer.posZ;
        double horizontalDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        float yaw = (float) (Math.atan2(deltaZ, deltaX) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(deltaY, horizontalDistance) * 180.0 / Math.PI);
        return new float[]{yaw, pitch};
    }

    private void switchToProjectile() {
        int projectileSlot = this.getProjectileSlot();
        if (projectileSlot != -1) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
            mc.thePlayer.inventory.currentItem = projectileSlot;
        }
    }

    private void switchBack() {
        if (this.lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = lastSlot;
            this.lastSlot = -1;
        }
    }

    private void throwProjectile() {
        int projectileSlot = this.getProjectileSlot();
        if (projectileSlot != -1) {
            ItemStack projectileStack = mc.thePlayer.inventory.getStackInSlot(projectileSlot);
            if (isProjectile(projectileStack)) {
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(projectileStack));
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) {
            return;
        }

        if (!this.hasProjectile()) {
            this.target = null;
            this.throwState = 0;
            this.throwsRemaining = 0;
            this.hasRotated = false;
            this.switchBack();
            return;
        }

        if (this.throwState == 0) {
            this.target = this.getTarget();
            if (this.target == null) {
                return;
            }

            KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
            if (killAura != null && killAura.isEnabled()) {
                double distance = mc.thePlayer.getDistanceToEntity(this.target);
                if (distance <= killAura.attackRange.getValue()) {
                    return;
                }
            }

            if (System.currentTimeMillis() - this.lastThrowTime < this.calculateSmartDelay()) {
                return;
            }

            this.throwsRemaining = this.amount.getValue();
            this.throwState = 1;
            this.hasRotated = false;
        }

        if (this.throwState == 1) {
            this.switchToProjectile();
            this.throwState = 2;
        } else if (this.throwState == 2) {
            if (this.throwsRemaining > 0) {
                Vec3 predictedPos = this.predictPosition(this.target);
                float[] rotations = this.getRotationsToPosition(predictedPos);

                event.setRotation(rotations[0], rotations[1], 2);
                event.setPervRotation(rotations[0], 2);
                this.hasRotated = true;
                this.throwState = 3;
            } else {
                this.throwState = 4;
            }
        } else if (this.throwState == 3) {
            this.throwProjectile();
            this.throwsRemaining--;

            if (this.throwsRemaining > 0) {
                this.throwState = 2;
            } else {
                this.throwState = 4;
            }
        } else if (this.throwState == 4) {
            this.switchBack();
            this.target = null;
            this.throwState = 0;
            this.hasRotated = false;
            this.lastThrowTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled()) {
            return;
        }
        if (this.hasRotated && RotationState.isActived() && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.lastSlot = -1;
        this.lastThrowTime = 0L;
        this.throwState = 0;
        this.throwsRemaining = 0;
        this.hasRotated = false;
    }

    @Override
    public void onDisabled() {
        this.switchBack();
        this.target = null;
        this.throwState = 0;
        this.throwsRemaining = 0;
        this.hasRotated = false;
    }

    private static class SmartPredictor {
        private final Vec3[] positions = new Vec3[20];
        private final long[] timestamps = new long[20];
        private int index = 0;

        private final double[] movementPatterns = new double[4];
        private double strafeFrequency = 0.0;
        private double jumpFrequency = 0.0;
        private long lastDirectionChange = 0L;
        private Vec3 lastDirection = new Vec3(0, 0, 0);
        private boolean isStrafing = false;
        private boolean isJumping = false;

        public void addPosition(Vec3 pos, long time) {
            positions[index] = pos;
            timestamps[index] = time;

            if (index > 0) {
                analyzeMovementPattern();
            }

            index = (index + 1) % positions.length;
        }

        private void analyzeMovementPattern() {
            if (index < 2) return;

            int currentIdx = index;
            int prevIdx = (index - 1 + positions.length) % positions.length;

            Vec3 currentPos = positions[currentIdx];
            Vec3 prevPos = positions[prevIdx];

            if (currentPos == null || prevPos == null) return;

            Vec3 movement = new Vec3(
                    currentPos.xCoord - prevPos.xCoord,
                    currentPos.yCoord - prevPos.yCoord,
                    currentPos.zCoord - prevPos.zCoord
            );

            if (Math.abs(movement.xCoord) > 0.01) {
                if (movement.xCoord > 0) movementPatterns[0] += 0.1;
                else movementPatterns[1] += 0.1;
            }

            if (Math.abs(movement.zCoord) > 0.01) {
                if (movement.zCoord > 0) movementPatterns[2] += 0.1;
                else movementPatterns[3] += 0.1;
            }

            for (int i = 0; i < movementPatterns.length; i++) {
                movementPatterns[i] *= 0.95;
            }

            Vec3 currentDirection = normalizeMovement(movement);
            if (lastDirection.lengthVector() > 0) {
                double dotProduct = lastDirection.xCoord * currentDirection.xCoord +
                        lastDirection.zCoord * currentDirection.zCoord;
                if (dotProduct < 0.3) {
                    lastDirectionChange = timestamps[currentIdx];
                    strafeFrequency = Math.min(1.0, strafeFrequency + 0.2);
                    isStrafing = true;
                }
            }
            lastDirection = currentDirection;

            if (movement.yCoord > 0.1) {
                jumpFrequency = Math.min(1.0, jumpFrequency + 0.15);
                isJumping = true;
            } else {
                jumpFrequency *= 0.9;
                isJumping = false;
            }

            if (System.currentTimeMillis() - lastDirectionChange > 500) {
                isStrafing = false;
                strafeFrequency *= 0.8;
            }
        }

        private Vec3 normalizeMovement(Vec3 movement) {
            double length = Math.sqrt(movement.xCoord * movement.xCoord + movement.zCoord * movement.zCoord);
            if (length < 0.001) return new Vec3(0, 0, 0);
            return new Vec3(movement.xCoord / length, 0, movement.zCoord / length);
        }

        public Vec3 predictNextPosition(double predictionTime) {
            if (index < 3) return positions[(index - 1 + positions.length) % positions.length];

            Vec3 currentPos = positions[(index - 1 + positions.length) % positions.length];
            Vec3 velocity = getCurrentVelocity();
            Vec3 acceleration = getCurrentAcceleration();

            Vec3 basePredict = new Vec3(
                    currentPos.xCoord + velocity.xCoord * predictionTime + 0.5 * acceleration.xCoord * predictionTime * predictionTime,
                    currentPos.yCoord + velocity.yCoord * predictionTime + 0.5 * acceleration.yCoord * predictionTime * predictionTime,
                    currentPos.zCoord + velocity.zCoord * predictionTime + 0.5 * acceleration.zCoord * predictionTime * predictionTime
            );

            Vec3 behaviorPredict = predictBehaviorChange(currentPos, velocity, predictionTime);

            double baseWeight = Math.max(0.3, 1.0 - strafeFrequency);
            double behaviorWeight = strafeFrequency;

            return new Vec3(
                    basePredict.xCoord * baseWeight + behaviorPredict.xCoord * behaviorWeight,
                    basePredict.yCoord * baseWeight + behaviorPredict.yCoord * behaviorWeight,
                    basePredict.zCoord * baseWeight + behaviorPredict.zCoord * behaviorWeight
            );
        }

        private Vec3 predictBehaviorChange(Vec3 currentPos, Vec3 velocity, double predictionTime) {
            Vec3 predicted = currentPos;
            double reactionTime = 0.3;
            if (isStrafing && predictionTime > reactionTime) {
                double timeSinceLastChange = (System.currentTimeMillis() - lastDirectionChange) / 1000.0;
                if (timeSinceLastChange > 0.8 && Math.random() < strafeFrequency) {
                    Vec3 oppositeVel = new Vec3(-velocity.xCoord * 0.8, velocity.yCoord, -velocity.zCoord * 0.8);
                    predicted = new Vec3(
                            currentPos.xCoord + oppositeVel.xCoord * (predictionTime - reactionTime),
                            currentPos.yCoord + oppositeVel.yCoord * (predictionTime - reactionTime),
                            currentPos.zCoord + oppositeVel.zCoord * (predictionTime - reactionTime)
                    );
                } else {
                    Vec3 continuedVel = new Vec3(velocity.xCoord * 0.9, velocity.yCoord, velocity.zCoord * 0.9);
                    predicted = new Vec3(
                            currentPos.xCoord + continuedVel.xCoord * predictionTime,
                            currentPos.yCoord + continuedVel.yCoord * predictionTime,
                            currentPos.zCoord + continuedVel.zCoord * predictionTime
                    );
                }
            } else {
                double totalPattern = movementPatterns[0] + movementPatterns[1] + movementPatterns[2] + movementPatterns[3];
                if (totalPattern > 0) {
                    double xTendency = (movementPatterns[0] - movementPatterns[1]) / totalPattern;
                    double zTendency = (movementPatterns[2] - movementPatterns[3]) / totalPattern;

                    Vec3 tendencyVel = new Vec3(
                            velocity.xCoord + xTendency * 0.5,
                            velocity.yCoord + (isJumping ? jumpFrequency * 0.3 : 0),
                            velocity.zCoord + zTendency * 0.5
                    );

                    predicted = new Vec3(
                            currentPos.xCoord + tendencyVel.xCoord * predictionTime,
                            currentPos.yCoord + tendencyVel.yCoord * predictionTime,
                            currentPos.zCoord + tendencyVel.zCoord * predictionTime
                    );
                }
            }

            return predicted;
        }

        private Vec3 getCurrentVelocity() {
            if (index < 2) return new Vec3(0, 0, 0);

            int currentIdx = (index - 1 + positions.length) % positions.length;
            int prevIdx = (index - 2 + positions.length) % positions.length;

            if (positions[currentIdx] == null || positions[prevIdx] == null) {
                return new Vec3(0, 0, 0);
            }

            long timeDiff = timestamps[currentIdx] - timestamps[prevIdx];
            if (timeDiff <= 0) return new Vec3(0, 0, 0);

            double deltaX = positions[currentIdx].xCoord - positions[prevIdx].xCoord;
            double deltaY = positions[currentIdx].yCoord - positions[prevIdx].yCoord;
            double deltaZ = positions[currentIdx].zCoord - positions[prevIdx].zCoord;

            double timeInSeconds = timeDiff / 1000.0;
            return new Vec3(deltaX / timeInSeconds, deltaY / timeInSeconds, deltaZ / timeInSeconds);
        }

        private Vec3 getCurrentAcceleration() {
            if (index < 3) return new Vec3(0, 0, 0);

            Vec3 vel1 = getVelocityBetween((index - 1 + positions.length) % positions.length,
                    (index - 2 + positions.length) % positions.length);
            Vec3 vel2 = getVelocityBetween((index - 2 + positions.length) % positions.length,
                    (index - 3 + positions.length) % positions.length);

            int currentIdx = (index - 1 + positions.length) % positions.length;
            int prevIdx = (index - 2 + positions.length) % positions.length;

            long timeDiff = timestamps[currentIdx] - timestamps[prevIdx];
            if (timeDiff <= 0) return new Vec3(0, 0, 0);

            double timeInSeconds = timeDiff / 1000.0;
            return new Vec3(
                    (vel1.xCoord - vel2.xCoord) / timeInSeconds,
                    (vel1.yCoord - vel2.yCoord) / timeInSeconds,
                    (vel1.zCoord - vel2.zCoord) / timeInSeconds
            );
        }

        private Vec3 getVelocityBetween(int idx1, int idx2) {
            if (positions[idx1] == null || positions[idx2] == null) {
                return new Vec3(0, 0, 0);
            }

            long timeDiff = timestamps[idx1] - timestamps[idx2];
            if (timeDiff <= 0) return new Vec3(0, 0, 0);

            double deltaX = positions[idx1].xCoord - positions[idx2].xCoord;
            double deltaY = positions[idx1].yCoord - positions[idx2].yCoord;
            double deltaZ = positions[idx1].zCoord - positions[idx2].zCoord;

            double timeInSeconds = timeDiff / 1000.0;
            return new Vec3(deltaX / timeInSeconds, deltaY / timeInSeconds, deltaZ / timeInSeconds);
        }
    }
}