package cn.unfair.util.rotation.advanced;

import cn.unfair.util.client.RandomUtil;
import net.minecraft.util.MathHelper;

import java.util.concurrent.ThreadLocalRandom;

public class HumanMouseEngine {
    private static final float SYNC_THRESHOLD = 2.5F;
    private static final float AIM_WIDTH_DEG = 10.0F;
    private static final float DEAD_ZONE_MIN = 0.12F;
    private static final float DEAD_ZONE_MAX = 0.42F;
    private static final float LAZY_ZONE = 1.6F;
    private static final float MICRO_EAT_MIN = 0.55F;
    private static final float MICRO_EAT_MAX = 0.85F;
    private static final float MICRO_INTERVAL_MIN = 120.0F;
    private static final float MICRO_INTERVAL_MAX = 420.0F;
    private static final float PULLBACK_DELAY_MIN = 80.0F;
    private static final float PULLBACK_DELAY_MAX = 260.0F;
    private static final float MIN_PLAN_MS = 80.0F;
    private static final float MAX_PLAN_MS = 950.0F;
    private static final float TICKS_PER_SECOND = 20.0F;

    private float overshootChanceMin = 0.05F;
    private float overshootChanceMax = 0.55F;
    private float overshootAmountMin = 0.005F;
    private float overshootAmountMax = 0.045F;
    private float pullbackThreshold = 1.0F;

    private float currentYaw;
    private float currentPitch;
    private boolean initialized;
    private long lastStepMs;

    private boolean planning;
    private boolean pullbackPlan;
    private boolean planOvershoot;
    private float planOvershootAmount;
    private float startYaw;
    private float startPitch;
    private float stopYaw;
    private float stopPitch;
    private float planDurationMs;
    private long planElapsedMs;
    private float peakT;
    private float peakS;
    private float decelPow;
    private float arcYaw;
    private float arcPitch;
    private float waveAmp;
    private float waveFreq;
    private float wavePhase;
    private float planSpeedFactor;

    private boolean pendingPullback;
    private long pullbackAtMs;

    private float deadZone;
    private long nextMicroMs;

    private float tremorYaw;
    private float tremorPitch;
    private float tremorTargetYaw;
    private float tremorTargetPitch;
    private int tremorRetarget;

    public void reset(float yaw, float pitch) {
        this.currentYaw = yaw;
        this.currentPitch = pitch;
        this.initialized = true;
        this.planning = false;
        this.pullbackPlan = false;
        this.planOvershoot = false;
        this.planOvershootAmount = 0.0F;
        this.pendingPullback = false;
        this.deadZone = 0.25F;
        this.nextMicroMs = System.currentTimeMillis();
        this.tremorYaw = 0.0F;
        this.tremorPitch = 0.0F;
        this.tremorTargetYaw = 0.0F;
        this.tremorTargetPitch = 0.0F;
        this.tremorRetarget = 0;
        this.lastStepMs = System.currentTimeMillis();
    }

    public float[] step(float serverYaw, float serverPitch, float targetYaw, float targetPitch,
                        float speedFactor, float overshootFactor, float tremorFactor,
                        float maxYawSpeed, float maxPitchSpeed) {
        long now = System.currentTimeMillis();
        long dtMs = this.lastStepMs == 0L ? 50L : Math.min(120L, Math.max(16L, now - this.lastStepMs));
        this.lastStepMs = now;
        if (!this.initialized) {
            this.reset(serverYaw, serverPitch);
        }

        this.deadZone = RandomUtil.nextFloat(DEAD_ZONE_MIN, DEAD_ZONE_MAX);

        float serverDriftYaw = MathHelper.wrapAngleTo180_float(serverYaw - this.currentYaw);
        float serverDriftPitch = serverPitch - this.currentPitch;
        if (Math.abs(serverDriftYaw) > SYNC_THRESHOLD || Math.abs(serverDriftPitch) > SYNC_THRESHOLD) {
            this.currentYaw = serverYaw;
            this.currentPitch = serverPitch;
            this.planning = false;
            this.pullbackPlan = false;
            this.planOvershoot = false;
            this.planOvershootAmount = 0.0F;
            this.pendingPullback = false;
        } else {
            this.currentYaw += serverDriftYaw * 0.5F;
            this.currentPitch += serverDriftPitch * 0.5F;
        }

        float dYaw = MathHelper.wrapAngleTo180_float(targetYaw - this.currentYaw);
        float dPitch = MathHelper.clamp_float(targetPitch, -90.0F, 90.0F) - this.currentPitch;
        double dist = Math.hypot(Math.abs(dYaw), Math.abs(dPitch));

        if (this.planning) {
            float planDriftYaw = MathHelper.wrapAngleTo180_float(targetYaw - this.stopYaw);
            float planDriftPitch = MathHelper.clamp_float(targetPitch, -90.0F, 90.0F) - this.stopPitch;
            double planDrift = Math.hypot(Math.abs(planDriftYaw), Math.abs(planDriftPitch));

            float planDirYaw = MathHelper.wrapAngleTo180_float(this.stopYaw - this.startYaw);
            float planDirPitch = this.stopPitch - this.startPitch;
            double planLen = Math.max(1.0E-4D, Math.hypot(Math.abs(planDirYaw), Math.abs(planDirPitch)));
            double dot = (dYaw * planDirYaw + dPitch * planDirPitch) / (Math.max(dist, 1.0E-4D) * planLen);

            if (planDrift > Math.max(3.0D, dist * 0.35D) || dot < 0.2D) {
                if (dist > this.deadZone) {
                    this.startPlan(dYaw, dPitch, dist, speedFactor, overshootFactor, this.pullbackPlan, maxYawSpeed, maxPitchSpeed);
                }
            } else {
                this.stopYaw += planDriftYaw * 0.25F;
                this.stopPitch = MathHelper.clamp_float(this.stopPitch + planDriftPitch * 0.25F, -90.0F, 90.0F);
            }
            this.advancePlan(dtMs);
        } else {
            if (this.pendingPullback && dist > LAZY_ZONE * 2.0D) {
                this.pendingPullback = false;
            }
            if (this.pendingPullback && now >= this.pullbackAtMs) {
                this.pendingPullback = false;
                if (dist > this.deadZone) {
                    this.startPlan(dYaw, dPitch, dist, speedFactor, overshootFactor, true, maxYawSpeed, maxPitchSpeed);
                    this.advancePlan(dtMs);
                }
            }
            if (!this.planning && !this.pendingPullback && dist > LAZY_ZONE) {
                this.startPlan(dYaw, dPitch, dist, speedFactor, overshootFactor, false, maxYawSpeed, maxPitchSpeed);
                this.advancePlan(dtMs);
            } else if (!this.planning && !this.pendingPullback && dist > this.deadZone && now >= this.nextMicroMs) {
                // lazy 微调：离散小步进一次吃掉大半残差，瞄到了就懒得大动
                float eat = RandomUtil.nextFloat(MICRO_EAT_MIN, MICRO_EAT_MAX);
                this.currentYaw += dYaw * eat;
                this.currentPitch += dPitch * eat;
                this.nextMicroMs = now + (long) RandomUtil.nextFloat(MICRO_INTERVAL_MIN, MICRO_INTERVAL_MAX);
            }
        }

        this.updateTremor(dtMs);
        float steadiness = dist < 2.5D ? 0.55F : 1.0F;
        this.currentYaw += this.tremorYaw * tremorFactor * steadiness;
        this.currentPitch = MathHelper.clamp_float(
                this.currentPitch + this.tremorPitch * tremorFactor * steadiness, -90.0F, 90.0F
        );

        // 每 tick 速度上限兜底
        float deltaYaw = MathHelper.wrapAngleTo180_float(this.currentYaw - serverYaw);
        float deltaPitch = this.currentPitch - serverPitch;
        if (Math.abs(deltaYaw) > maxYawSpeed) {
            deltaYaw = Math.signum(deltaYaw) * maxYawSpeed;
        }
        if (Math.abs(deltaPitch) > maxPitchSpeed) {
            deltaPitch = Math.signum(deltaPitch) * maxPitchSpeed;
        }
        this.currentYaw = serverYaw + deltaYaw;
        this.currentPitch = MathHelper.clamp_float(serverPitch + deltaPitch, -90.0F, 90.0F);
        return new float[]{this.currentYaw, this.currentPitch};
    }

    public void configureOvershoot(float minChance, float maxChance, float minAmount, float maxAmount, float pullbackThreshold) {
        this.overshootChanceMin = minChance;
        this.overshootChanceMax = maxChance;
        this.overshootAmountMin = minAmount;
        this.overshootAmountMax = maxAmount;
        this.pullbackThreshold = pullbackThreshold;
    }

    private void startPlan(float dYaw, float dPitch, double dist, float speedFactor, float overshootFactor,
                           boolean pullback, float maxYawSpeed, float maxPitchSpeed) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        this.pullbackPlan = pullback;

        // 剖面形状：加速快、减速长，真人甩头的不对称钟形
        this.peakT = RandomUtil.nextFloat(0.22F, 0.38F);
        this.peakS = RandomUtil.nextFloat(0.58F, 0.75F);
        this.decelPow = RandomUtil.nextFloat(1.8F, 3.2F);

        if (pullback) {
            this.planSpeedFactor = RandomUtil.nextFloat(1.0F, 1.35F);
        } else {
            double roll = random.nextDouble();
            this.planSpeedFactor = roll < 0.08D
                    ? RandomUtil.nextFloat(0.68F, 0.8F)
                    : roll < 0.18D
                    ? RandomUtil.nextFloat(1.25F, 1.45F)
                    : RandomUtil.nextFloat(0.88F, 1.18F);
        }

        // Fitts 定律：T = a + b·log2(D/W + 1)
        double id = Math.log(dist / AIM_WIDTH_DEG + 1.0D) / Math.log(2.0D);
        double durationMs = 105.0D + 62.0D * id;
        if (pullback) {
            durationMs *= RandomUtil.nextFloat(1.5F, 2.2F);
        }
        durationMs /= Math.max(0.05F, speedFactor) * this.planSpeedFactor;

        // 峰值角速度超每 tick 上限时拉长时间，保住速度曲线形状
        double peakDegPerSec = 2.0D * this.peakS / this.peakT * dist / (durationMs / 1000.0D);
        double maxDegPerSec = Math.max(maxYawSpeed, maxPitchSpeed) * TICKS_PER_SECOND;
        if (peakDegPerSec > maxDegPerSec) {
            durationMs *= peakDegPerSec / maxDegPerSec;
        }

        this.planDurationMs = (float) Math.min(MAX_PLAN_MS, Math.max(MIN_PLAN_MS, durationMs));

        double safeDist = Math.max(dist, 1.0E-4D);
        float dirYaw = (float) (dYaw / safeDist);
        float dirPitch = (float) (dPitch / safeDist);

        // 过冲 = 刹车不及停在目标之外，大角度更常见，修正动作不过冲
        this.planOvershoot = false;
        this.planOvershootAmount = 0.0F;
        if (!pullback && overshootFactor > 0.0F) {
            double overshootProb = (this.overshootChanceMin
                    + (this.overshootChanceMax - this.overshootChanceMin) * Math.min(1.0D, dist / 180.0D)) * overshootFactor;
            if (random.nextDouble() < overshootProb) {
                this.planOvershoot = true;
                this.planOvershootAmount = (float) (dist * RandomUtil.nextFloat(this.overshootAmountMin, this.overshootAmountMax));
            }
        }

        this.stopYaw = this.currentYaw + dYaw + dirYaw * this.planOvershootAmount;
        this.stopPitch = MathHelper.clamp_float(
                this.currentPitch + dPitch + dirPitch * this.planOvershootAmount, -90.0F, 90.0F
        );

        // 手腕旋转轴效应：轻微弓形轨迹，方向每次随机
        float arcAmp = pullback
                ? (float) (dist * RandomUtil.nextFloat(0.0F, 0.02F))
                : (float) (dist * RandomUtil.nextFloat(0.015F, 0.06F) * (random.nextBoolean() ? 1.0F : -1.0F));
        this.arcYaw = -dirPitch * arcAmp;
        this.arcPitch = dirYaw * arcAmp;

        // 中段速度波动：低频平滑调制，端点归零
        this.waveAmp = pullback ? RandomUtil.nextFloat(0.0F, 0.04F) : RandomUtil.nextFloat(0.03F, 0.08F);
        this.waveFreq = RandomUtil.nextFloat(0.8F, 2.2F);
        this.wavePhase = RandomUtil.nextFloat(0.0F, (float) (Math.PI * 2.0D));

        this.startYaw = this.currentYaw;
        this.startPitch = this.currentPitch;
        this.planElapsedMs = 0L;
        this.planning = true;
    }

    private void advancePlan(long dtMs) {
        this.planElapsedMs += dtMs;
        float t = (float) Math.min(1.0D, (double) this.planElapsedMs / this.planDurationMs);

        // 前段二次加速冲至峰值，后段幂函数长尾减速：快甩慢刹
        double s;
        if (t <= this.peakT) {
            double f = t / this.peakT;
            s = this.peakS * f * f;
        } else {
            double u = (t - this.peakT) / (1.0F - this.peakT);
            s = this.peakS + (1.0D - this.peakS) * (1.0D - Math.pow(1.0D - u, this.decelPow));
        }
        s *= 1.0D + this.waveAmp * Math.sin(Math.PI * 2.0D * this.waveFreq * t + this.wavePhase) * Math.sin(Math.PI * t);

        float totalYaw = MathHelper.wrapAngleTo180_float(this.stopYaw - this.startYaw);
        float totalPitch = this.stopPitch - this.startPitch;
        float bow = (float) Math.sin(Math.PI * t);
        this.currentYaw = this.startYaw + totalYaw * (float) s + this.arcYaw * bow;
        this.currentPitch = MathHelper.clamp_float(
                this.startPitch + totalPitch * (float) s + this.arcPitch * bow, -90.0F, 90.0F
        );

        if (t >= 1.0F) {
            this.planning = false;
            if (this.planOvershoot && this.planOvershootAmount > this.pullbackThreshold) {
                // 明显过冲：先停顿反应，再拉回；小过冲懒得拉，交给 lazy 微调
                this.pendingPullback = true;
                this.pullbackAtMs = System.currentTimeMillis()
                        + (long) RandomUtil.nextFloat(PULLBACK_DELAY_MIN, PULLBACK_DELAY_MAX);
            }
        }
    }

    private void updateTremor(long dtMs) {
        if (this.tremorRetarget <= 0) {
            this.tremorTargetYaw = (float) (ThreadLocalRandom.current().nextGaussian() * 0.025D);
            this.tremorTargetPitch = (float) (ThreadLocalRandom.current().nextGaussian() * 0.025D);
            this.tremorRetarget = RandomUtil.nextInt(3, 8);
        }
        this.tremorRetarget--;

        // 平滑低频震颤包络（低通趋向），非逐帧白噪声
        float relax = (float) (1.0D - Math.exp(-dtMs / 70.0D));
        this.tremorYaw += (this.tremorTargetYaw - this.tremorYaw) * relax;
        this.tremorPitch += (this.tremorTargetPitch - this.tremorPitch) * relax;
    }
}
