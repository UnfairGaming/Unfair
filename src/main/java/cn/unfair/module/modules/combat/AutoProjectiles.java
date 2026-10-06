package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.player.PacketUtil;
import cn.unfair.util.rotation.RotationUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.via.ViaProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;

import static cn.unfair.management.BadPacketManager.bad;

public class AutoProjectiles extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final double THROW_SPEED = 1.5;
    private static final double THROW_DRAG = 0.99;
    private static final double GRAVITY_TERM = 3.0;
    private static final double DRAG_SCALE = 100.0;
    private static final double AIM_HEIGHT = 1.4;
    public final FloatProperty minRange = new FloatProperty("MinRange", 3.0f, 2.0f, 6.0f);
    public final FloatProperty maxRange = new FloatProperty("MaxRange", 8.0f, 3.0f, 15.0f);
    public final BooleanProperty smartDelay = new BooleanProperty("SmartDelay", true);
    public final IntProperty throwDelay = new IntProperty("ThrowDelay", 3, 1, 15, () -> !smartDelay.getValue());
    public final IntProperty fov = new IntProperty("Fov", 90, 30, 360);
    public final BooleanProperty rotation = new BooleanProperty("Rotation", true);
    public final BooleanProperty prediction = new BooleanProperty("Prediction", true);
    public final BooleanProperty inventoryCheck = new BooleanProperty("InventoryCheck", true);
    private EntityLivingBase target = null;
    private int lastSlot = -1;
    private long lastThrowTime = 0L;
    private int throwState = 0;
    private int throwsRemaining = 0;
    private boolean hasRotated = false;
    private final TargetTracker targetTracker = new TargetTracker();
    private double smoothedPing = -1.0;

    public AutoProjectiles() {
        super("AutoProjectiles", false);
    }

    private boolean isEntityHeightVisible(EntityLivingBase entity) {
        Vec3 eyePos = mc.thePlayer.getPositionEyes(1.0f);
        Vec3 top = new Vec3(entity.posX, entity.posY + entity.height, entity.posZ);
        Vec3 bottom = new Vec3(entity.posX, entity.posY, entity.posZ);
        return mc.theWorld.rayTraceBlocks(eyePos, top) == null || mc.theWorld.rayTraceBlocks(eyePos, bottom) == null;
    }

    private boolean isValidTarget(EntityLivingBase entity) {
        if (!mc.theWorld.loadedEntityList.contains(entity) || entity == mc.thePlayer || entity.deathTime > 0) {
            return false;
        }
        if (!(entity instanceof EntityOtherPlayerMP player)) {
            return false;
        }
        double distance = mc.thePlayer.getDistanceToEntity(entity);
        if (distance > this.maxRange.getValue()) {
            return false;
        }
        if (TeamUtil.isFriend(player)) {
            return false;
        }
        if (TeamUtil.shouldBlockBot(player)) {
            return false;
        }
        if (!isEntityHeightVisible(entity)) return false;
        if (RotationUtil.angleToEntity(player) > this.fov.getValue().floatValue()) return false;
        return !TeamUtil.shouldBlockTeam(player);
    }

    private int getThrowDelay() {
        if (!this.smartDelay.getValue()) {
            return this.throwDelay.getValue();
        }

        EntityLivingBase target = this.getTarget();
        if (target == null) {
            return this.throwDelay.getValue();
        }

        if (AutoProjectiles.mc.gameSettings.keyBindBack.isKeyDown()) {
            return 1;
        }

        double distance = mc.thePlayer.getDistanceToEntity(target);

        if (distance <= 4.5) {
            return 1;
        }
        if (distance <= 6.0) {
            return 2;
        }
        if (distance <= 8.0) {
            return 3;
        }
        if (distance <= 9.0) {
            return 5;
        }
        if (distance <= 15.0) {
            return 8;
        }
        return 20;
    }

    private EntityLivingBase getTarget() {
        ArrayList<EntityLivingBase> targets = new ArrayList<>();
        for (Object obj : mc.theWorld.loadedEntityList) {
            if (obj instanceof EntityLivingBase entity) {
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
            this.targetTracker.reset();
        }

        return newTarget;
    }

    public boolean hasProjectile() {
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

    private double getPingMillis() {
        NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
        if (info == null) {
            return Math.max(0.0, this.smoothedPing);
        }
        double raw = MathHelper.clamp_double(info.getResponseTime(), 0.0, 1000.0);
        this.smoothedPing = this.smoothedPing < 0.0 ? raw : this.smoothedPing + (raw - this.smoothedPing) * 0.5;
        return this.smoothedPing;
    }

    private float[] computeAim(EntityLivingBase target) {
        // 提前量 = 目标观察滞后 + 指令到达(合计一个 ping) + 状态机 1 tick + 服务器 tick 对齐半 tick
        double leadSeconds = this.getPingMillis() / 1000.0 + 0.075;
        double flightTicks = 6.0;
        float[] result = null;
        for (int i = 0; i < 3; i++) {
            Vec3 predicted = this.prediction.getValue()
                    ? this.targetTracker.predict(target, leadSeconds + flightTicks * 0.05)
                    : new Vec3(target.posX, target.posY, target.posZ);
            Vec3 aim = new Vec3(predicted.xCoord, predicted.yCoord + AIM_HEIGHT, predicted.zCoord);
            double[] sol = this.solveAim(aim);
            if (sol == null) {
                return null;
            }
            result = new float[]{(float) sol[0], (float) sol[1]};
            flightTicks = sol[2];
        }
        return result;
    }

    private double[] solveAim(Vec3 aim) {
        double sx = mc.thePlayer.posX;
        double sy = mc.thePlayer.posY + mc.thePlayer.getEyeHeight() - 0.1;
        double sz = mc.thePlayer.posZ;
        double dx = aim.xCoord - sx;
        double dz = aim.zCoord - sz;
        float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
        if (!ViaProtocol.newerThan1_8()) {
            // 原版 1.8 服务器 spawn 点带朝向的水平偏移
            double yawRad = Math.toRadians(yaw);
            sx -= Math.cos(yawRad) * 0.16;
            sz -= Math.sin(yawRad) * 0.16;
            dx = aim.xCoord - sx;
            dz = aim.zCoord - sz;
        }
        double dh = Math.sqrt(dx * dx + dz * dz);
        double dy = aim.yCoord - sy;
        double[] sol = this.solveThrowPitch(dh, dy);
        if (sol == null) {
            return null;
        }
        return new double[]{yaw, sol[0], sol[1]};
    }

    private double[] solveThrowPitch(double dh, double dy) {
        // 原版每 tick: pos += v; v *= 0.99; vy -= 0.03 的闭合解:
        // x(t) = 100*vx*(1-0.99^t), y(t) = 100*(vy+3)*(1-0.99^t) - 3t
        // 反解所需初速模方 h(t), 二分求 h(t) = 1.5^2 的最小 t (低弹道)
        final double speedSq = THROW_SPEED * THROW_SPEED;
        double tLo = 0.0;
        double tHi = -1.0;
        for (double t = 0.25; t <= 80.0; t += 0.5) {
            if (this.requiredSpeedSq(dh, dy, t) <= speedSq) {
                tHi = t;
                break;
            }
            tLo = t;
        }
        if (tHi < 0.0) {
            return null;
        }
        for (int i = 0; i < 24; i++) {
            double mid = (tLo + tHi) * 0.5;
            if (this.requiredSpeedSq(dh, dy, mid) <= speedSq) {
                tHi = mid;
            } else {
                tLo = mid;
            }
        }
        double den = DRAG_SCALE * (1.0 - Math.pow(THROW_DRAG, tHi));
        double vy = (dy + GRAVITY_TERM * tHi) / den - GRAVITY_TERM;
        double pitch = -Math.toDegrees(Math.asin(MathHelper.clamp_double(vy / THROW_SPEED, -1.0, 1.0)));
        return new double[]{pitch, tHi};
    }

    private double requiredSpeedSq(double dh, double dy, double t) {
        double den = DRAG_SCALE * (1.0 - Math.pow(THROW_DRAG, t));
        double vh = dh / den;
        double vy = (dy + GRAVITY_TERM * t) / den - GRAVITY_TERM;
        return vh * vh + vy * vy;
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

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || bad()) {
            return;
        }

        if (this.isInventoryBlocked()) {
            this.resetState(true);
            return;
        }

        BackTrack backTrack = (BackTrack) Unfair.moduleManager.modules.get(BackTrack.class);
        if (backTrack.isEnabled() && backTrack.isBackTracking) {
            return;
        }

        if (!this.hasProjectile()) {
            this.resetState(true);
            return;
        }

        if (this.throwState != 0 && (this.target == null || !this.isValidTarget(this.target))) {
            this.resetState(true);
            return;
        }

        if (this.target != null) {
            this.targetTracker.sample(this.target.posX, this.target.posY, this.target.posZ, System.currentTimeMillis());
        }

        if (this.throwState == 0) {
            this.target = this.getTarget();
            if (this.target == null) {
                return;
            }
            if (System.currentTimeMillis() - this.lastThrowTime < this.getThrowDelay() * 50L) {
                return;
            }

            KillAura killAura = (KillAura) Unfair.moduleManager.modules.get(KillAura.class);
            if (killAura != null && killAura.isEnabled()) {
                double distance = mc.thePlayer.getDistanceToEntity(this.target);
                if (distance <= minRange.getValue()) {
                    return;
                }
            }

            if (System.currentTimeMillis() - this.lastThrowTime < this.calculateSmartDelay()) {
                return;
            }

            this.throwsRemaining = 1;
            this.throwState = 1;
            this.hasRotated = false;
        }

        if (this.throwState == 1) {
            this.switchToProjectile();
            this.throwState = 2;
        } else if (this.throwState == 2) {
            if (this.throwsRemaining > 0) {
                if (this.rotation.getValue()) {
                    float[] aim = this.computeAim(this.target);
                    if (aim == null) {
                        Vec3 center = new Vec3(this.target.posX, this.target.posY + AIM_HEIGHT, this.target.posZ);
                        aim = RotationUtil.getRotations(
                                center.xCoord, center.yCoord, center.zCoord,
                                mc.thePlayer.posX, mc.thePlayer.posY + mc.thePlayer.getEyeHeight(), mc.thePlayer.posZ
                        );
                    }
                    event.setRotation(aim[0], aim[1], 2);
                    event.setPervRotation(aim[0], 2);
                    this.hasRotated = true;
                } else {
                    this.hasRotated = false;
                }
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
        if (this.isInventoryBlocked()) {
            this.hasRotated = false;
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
        this.targetTracker.reset();
        this.smoothedPing = -1.0;
    }

    @Override
    public void onDisabled() {
        this.resetState(true);
    }

    private void resetState(boolean restoreSlot) {
        if (restoreSlot) {
            this.switchBack();
        }
        this.target = null;
        this.throwState = 0;
        this.throwsRemaining = 0;
        this.hasRotated = false;
    }

    private boolean isInventoryBlocked() {
        return this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer;
    }

    @Override
    public void verifyValue(String name) {
        if (this.minRange.getName().equals(name) && this.minRange.getValue() > this.maxRange.getValue()) {
            this.maxRange.setValue(this.minRange.getValue());
        } else if (this.maxRange.getName().equals(name) && this.minRange.getValue() > this.maxRange.getValue()) {
            this.minRange.setValue(this.maxRange.getValue());
        }
    }

    private static class TargetTracker {
        private static final int CAPACITY = 12;
        private final double[] xs = new double[CAPACITY];
        private final double[] ys = new double[CAPACITY];
        private final double[] zs = new double[CAPACITY];
        private final long[] ts = new long[CAPACITY];
        private int head = 0;
        private int count = 0;
        private double velX = 0.0;
        private double velY = 0.0;
        private double velZ = 0.0;
        private boolean hasVelocity = false;

        public void reset() {
            this.head = 0;
            this.count = 0;
            this.velX = 0.0;
            this.velY = 0.0;
            this.velZ = 0.0;
            this.hasVelocity = false;
        }

        public void sample(double x, double y, double z, long now) {
            if (this.count > 0) {
                int newest = (this.head - 1 + CAPACITY) % CAPACITY;
                if (this.ts[newest] >= now) {
                    return;
                }
            }
            this.xs[this.head] = x;
            this.ys[this.head] = y;
            this.zs[this.head] = z;
            this.ts[this.head] = now;
            this.head = (this.head + 1) % CAPACITY;
            this.count = Math.min(this.count + 1, CAPACITY);
            if (this.count >= 2) {
                int newest = (this.head - 1 + CAPACITY) % CAPACITY;
                int oldest = (newest - Math.min(this.count - 1, 4) + CAPACITY) % CAPACITY;
                double dt = (this.ts[newest] - this.ts[oldest]) / 1000.0;
                if (dt >= 0.05) {
                    double nvx = (this.xs[newest] - this.xs[oldest]) / dt;
                    double nvy = (this.ys[newest] - this.ys[oldest]) / dt;
                    double nvz = (this.zs[newest] - this.zs[oldest]) / dt;
                    double w = this.hasVelocity ? 0.6 : 1.0;
                    this.velX += (nvx - this.velX) * w;
                    this.velY += (nvy - this.velY) * w;
                    this.velZ += (nvz - this.velZ) * w;
                    this.hasVelocity = true;
                }
            }
        }

        public Vec3 predict(EntityLivingBase target, double seconds) {
            double px = target.posX + this.velX * seconds;
            double pz = target.posZ + this.velZ * seconds;
            double py = target.posY;
            if (this.hasVelocity && Math.abs(this.velY) >= 0.5) {
                // 垂直按原版实体物理外推: pos += vy; vy = (vy - 0.08) * 0.98
                double vy = this.velY * 0.05;
                int full = (int) Math.floor(seconds / 0.05);
                for (int i = 0; i < full; i++) {
                    py += vy;
                    vy = (vy - 0.08) * 0.98;
                }
                py += vy * (seconds / 0.05 - full);
                py = MathHelper.clamp_double(py, target.posY - 3.0, target.posY + 3.0);
            }
            return new Vec3(px, py, pz);
        }
    }
}
