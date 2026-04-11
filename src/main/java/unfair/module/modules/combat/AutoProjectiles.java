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
import net.minecraft.util.MovingObjectPosition;
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
    public final IntProperty amount = new IntProperty("Amount", 3, 1, 10);
    // 新增投掷延迟，默认100ms，用于控制两轮“爆发”之间的间隔
    public final IntProperty throwDelay = new IntProperty("Throw Delay", 100, 0, 1000);
    public final BooleanProperty prediction = new BooleanProperty("Prediction", true);
    public final BooleanProperty teams = new BooleanProperty("Teams", true);

    private EntityLivingBase target = null;
    private int lastSlot = -1;
    private long lastThrowTime = 0L;
    private int throwState = 0; // 0: 寻找目标/冷却, 1: 切换槽位, 2: 旋转, 3: 瞬时爆发投掷, 4: 切回原位
    private boolean hasRotated = false;
    private SmartPredictor smartPredictor = new SmartPredictor();

    public AutoProjectiles() {
        super("AutoProjectiles", false);
    }

    // ... (isValidTarget, getTarget, hasProjectile, isProjectile, getProjectileSlot, calculateSimulatedRotations, simulateProjectile 等逻辑保持不变)

    private boolean isValidTarget(EntityLivingBase entity) {
        if (entity == mc.thePlayer || entity.deathTime > 0) return false;
        if (!(entity instanceof EntityOtherPlayerMP)) return false;
        if (mc.thePlayer.getDistanceToEntity(entity) > this.range.getValue()) return false;
        EntityPlayer player = (EntityPlayer) entity;
        if (TeamUtil.isFriend(player)) return false;
        return !this.teams.getValue() || !TeamUtil.isSameTeam(player);
    }

    private EntityLivingBase getTarget() {
        ArrayList<EntityLivingBase> targets = new ArrayList<>();
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (obj instanceof EntityLivingBase) {
                EntityLivingBase entity = (EntityLivingBase) obj;
                if (isValidTarget(entity)) targets.add(entity);
            }
        }
        if (targets.isEmpty()) return null;
        targets.sort(Comparator.comparingDouble(entity -> mc.thePlayer.getDistanceToEntity(entity)));
        EntityLivingBase newTarget = targets.get(0);
        if (this.target != newTarget) this.smartPredictor = new SmartPredictor();
        return newTarget;
    }

    private boolean hasProjectile() {
        for (int i = 0; i < 9; i++) {
            if (isProjectile(mc.thePlayer.inventory.getStackInSlot(i))) return true;
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
            if (isProjectile(mc.thePlayer.inventory.getStackInSlot(i))) return i;
        }
        return -1;
    }

    private float[] calculateSimulatedRotations(EntityLivingBase target) {
        smartPredictor.addPosition(new Vec3(target.posX, target.posY, target.posZ), System.currentTimeMillis());
        double ping = 0;
        try { ping = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID()).getResponseTime(); } catch (Exception ignored) {}
        double distance = mc.thePlayer.getDistanceToEntity(target);
        double flightTicks = distance / 1.5;
        double totalPredictTicks = flightTicks + (ping / 50.0) + 1.0;
        Vec3 predictedPos;
        if (this.prediction.getValue()) {
            predictedPos = smartPredictor.predictNextPosition(totalPredictTicks * 0.05);
        } else {
            predictedPos = new Vec3(target.posX, target.posY, target.posZ);
        }
        double diffX = predictedPos.xCoord - mc.thePlayer.posX;
        double diffZ = predictedPos.zCoord - mc.thePlayer.posZ;
        double diffY = (predictedPos.yCoord + target.getEyeHeight() * 0.7) - (mc.thePlayer.posY + mc.thePlayer.getEyeHeight());
        float yaw = (float) (Math.atan2(diffZ, diffX) * 180.0 / Math.PI) - 90.0F;
        double horizontalDist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float bestPitch = 0;
        double minDiff = Double.MAX_VALUE;
        boolean found = false;
        for (float pitch = -90; pitch < 90; pitch += 0.5F) {
            double simulatedY = simulateProjectile(horizontalDist, pitch);
            double currentDiff = Math.abs(simulatedY - diffY);
            if (currentDiff < minDiff) {
                minDiff = currentDiff;
                bestPitch = pitch;
                found = true;
            }
        }
        if (!found) return null;
        MovingObjectPosition mop = mc.theWorld.rayTraceBlocks(
                new Vec3(mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ),
                new Vec3(predictedPos.xCoord, predictedPos.yCoord + target.getEyeHeight(), predictedPos.zCoord),
                false, true, false);
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) return null;
        return new float[]{yaw, bestPitch};
    }

    private double simulateProjectile(double dist, float pitch) {
        double v = 1.5;
        double vY = -Math.sin(Math.toRadians(pitch)) * v;
        double vH = Math.cos(Math.toRadians(pitch)) * v;
        double curH = 0;
        double curY = 0;
        for (int i = 0; i < 100; i++) {
            curH += vH;
            curY += vY;
            vH *= 0.99;
            vY *= 0.99;
            vY -= 0.03;
            if (curH >= dist) return curY;
        }
        return curY;
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
            mc.thePlayer.inventory.currentItem = this.lastSlot;
            this.lastSlot = -1;
        }
    }

    private void throwProjectile() {
        int projectileSlot = this.getProjectileSlot();
        if (projectileSlot != -1) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(projectileSlot);
            if (isProjectile(stack)) {
                // 发送封包进行投掷
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(stack));
            }
        }
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE) return;

        // 检查是否有投掷物
        if (!this.hasProjectile()) {
            this.target = null;
            this.throwState = 0;
            this.switchBack();
            return;
        }

        switch (this.throwState) {
            case 0: // 等待延迟并寻找目标
                if (System.currentTimeMillis() - this.lastThrowTime < this.throwDelay.getValue()) return;

                this.target = this.getTarget();
                if (this.target == null) return;

                // 如果正在KillAura范围内，通常不需要投掷（可选逻辑）
                KillAura aura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
                if (aura != null && aura.isEnabled() && mc.thePlayer.getDistanceToEntity(this.target) <= aura.attackRange.getValue()) return;

                this.throwState = 1;
                break;

            case 1: // 切换到投掷物
                this.switchToProjectile();
                this.throwState = 2;
                break;

            case 2: // 设置旋转角度
                float[] rots = calculateSimulatedRotations(this.target);
                if (rots != null) {
                    event.setRotation(rots[0], rots[1], 2);
                    event.setPervRotation(rots[0], 2);
                    this.hasRotated = true;
                    this.throwState = 3;
                } else {
                    this.throwState = 4; // 无法瞄准则跳过
                }
                break;

            case 3: // 瞬时爆发投掷 (Amount)
                for (int i = 0; i < this.amount.getValue(); i++) {
                    this.throwProjectile();
                }
                this.lastThrowTime = System.currentTimeMillis(); // 投掷完一轮后开始计时
                this.throwState = 4;
                break;

            case 4: // 清理状态并切回原槽位
                this.switchBack();
                this.target = null;
                this.hasRotated = false;
                this.throwState = 0;
                break;
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled() && this.hasRotated && RotationState.isActived() && RotationState.getPriority() == 2.0F && MoveUtil.isForwardPressed()) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    @Override
    public void onEnabled() {
        this.target = null;
        this.lastSlot = -1;
        this.throwState = 0;
        this.hasRotated = false;
        this.lastThrowTime = 0L;
    }

    @Override
    public void onDisabled() {
        this.switchBack();
        this.target = null;
        this.throwState = 0;
        this.hasRotated = false;
    }

    // ... (SmartPredictor 内部类保持不变)
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
            analyzeMovementPattern();
            index = (index + 1) % positions.length;
        }

        private void analyzeMovementPattern() {
            int currentIdx = index;
            int prevIdx = (index - 1 + positions.length) % positions.length;
            if (positions[currentIdx] == null || positions[prevIdx] == null) return;

            Vec3 movement = new Vec3(
                    positions[currentIdx].xCoord - positions[prevIdx].xCoord,
                    positions[currentIdx].yCoord - positions[prevIdx].yCoord,
                    positions[currentIdx].zCoord - positions[prevIdx].zCoord
            );

            if (Math.abs(movement.xCoord) > 0.01) {
                if (movement.xCoord > 0) movementPatterns[0] += 0.1; else movementPatterns[1] += 0.1;
            }
            if (Math.abs(movement.zCoord) > 0.01) {
                if (movement.zCoord > 0) movementPatterns[2] += 0.1; else movementPatterns[3] += 0.1;
            }
            for (int i = 0; i < 4; i++) movementPatterns[i] *= 0.95;

            double len = Math.sqrt(movement.xCoord * movement.xCoord + movement.zCoord * movement.zCoord);
            Vec3 currentDirection = len < 0.001 ? new Vec3(0, 0, 0) : new Vec3(movement.xCoord / len, 0, movement.zCoord / len);

            if (lastDirection.lengthVector() > 0) {
                double dot = lastDirection.xCoord * currentDirection.xCoord + lastDirection.zCoord * currentDirection.zCoord;
                if (dot < 0.3) {
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

        public Vec3 predictNextPosition(double predictionTime) {
            int curIdx = (index - 1 + positions.length) % positions.length;
            if (positions[curIdx] == null) return new Vec3(0,0,0);

            Vec3 velocity = getCurrentVelocity();
            Vec3 acceleration = getCurrentAcceleration();

            Vec3 basePredict = new Vec3(
                    positions[curIdx].xCoord + velocity.xCoord * predictionTime + 0.5 * acceleration.xCoord * predictionTime * predictionTime,
                    positions[curIdx].yCoord + velocity.yCoord * predictionTime + 0.5 * acceleration.yCoord * predictionTime * predictionTime,
                    positions[curIdx].zCoord + velocity.zCoord * predictionTime + 0.5 * acceleration.zCoord * predictionTime * predictionTime
            );

            Vec3 behaviorPredict = predictBehaviorChange(positions[curIdx], velocity, predictionTime);
            double baseWeight = Math.max(0.3, 1.0 - strafeFrequency);
            return new Vec3(
                    basePredict.xCoord * baseWeight + behaviorPredict.xCoord * strafeFrequency,
                    basePredict.yCoord * baseWeight + behaviorPredict.yCoord * strafeFrequency,
                    basePredict.zCoord * baseWeight + behaviorPredict.zCoord * strafeFrequency
            );
        }

        private Vec3 predictBehaviorChange(Vec3 currentPos, Vec3 velocity, double predictionTime) {
            if (isStrafing && predictionTime > 0.3) {
                if ((System.currentTimeMillis() - lastDirectionChange) / 1000.0 > 0.8) {
                    return new Vec3(currentPos.xCoord - velocity.xCoord * 0.8 * (predictionTime - 0.3), currentPos.yCoord + velocity.yCoord * predictionTime, currentPos.zCoord - velocity.zCoord * 0.8 * (predictionTime - 0.3));
                }
            }
            double totalPattern = movementPatterns[0] + movementPatterns[1] + movementPatterns[2] + movementPatterns[3];
            if (totalPattern > 0) {
                Vec3 tendency = new Vec3(velocity.xCoord + ((movementPatterns[0] - movementPatterns[1]) / totalPattern) * 0.5, velocity.yCoord + (isJumping ? jumpFrequency * 0.3 : 0), velocity.zCoord + ((movementPatterns[2] - movementPatterns[3]) / totalPattern) * 0.5);
                return new Vec3(currentPos.xCoord + tendency.xCoord * predictionTime, currentPos.yCoord + tendency.yCoord * predictionTime, currentPos.zCoord + tendency.zCoord * predictionTime);
            }
            return new Vec3(currentPos.xCoord + velocity.xCoord * predictionTime, currentPos.yCoord + velocity.yCoord * predictionTime, currentPos.zCoord + velocity.zCoord * predictionTime);
        }

        public Vec3 getCurrentVelocity() {
            int c = (index - 1 + positions.length) % positions.length;
            int p = (index - 2 + positions.length) % positions.length;
            if (positions[c] == null || positions[p] == null) return new Vec3(0, 0, 0);
            long time = timestamps[c] - timestamps[p];
            if (time <= 0) return new Vec3(0, 0, 0);
            return new Vec3((positions[c].xCoord - positions[p].xCoord) / (time / 1000.0), (positions[c].yCoord - positions[p].yCoord) / (time / 1000.0), (positions[c].zCoord - positions[p].zCoord) / (time / 1000.0));
        }

        public Vec3 getCurrentAcceleration() {
            int c = (index - 1 + positions.length) % positions.length;
            int p = (index - 2 + positions.length) % positions.length;
            int pp = (index - 3 + positions.length) % positions.length;
            if (positions[pp] == null) return new Vec3(0, 0, 0);
            Vec3 v1 = getVel(c, p); Vec3 v2 = getVel(p, pp);
            long time = timestamps[c] - timestamps[p];
            if (time <= 0) return new Vec3(0, 0, 0);
            return new Vec3((v1.xCoord - v2.xCoord) / (time / 1000.0), (v1.yCoord - v2.yCoord) / (time / 1000.0), (v1.zCoord - v2.zCoord) / (time / 1000.0));
        }

        private Vec3 getVel(int i1, int i2) {
            if (positions[i1] == null || positions[i2] == null) return new Vec3(0, 0, 0);
            long t = timestamps[i1] - timestamps[i2];
            if (t <= 0) return new Vec3(0, 0, 0);
            return new Vec3((positions[i1].xCoord - positions[i2].xCoord) / (t / 1000.0), (positions[i1].yCoord - positions[i2].yCoord) / (t / 1000.0), (positions[i1].zCoord - positions[i2].zCoord) / (t / 1000.0));
        }
    }
}