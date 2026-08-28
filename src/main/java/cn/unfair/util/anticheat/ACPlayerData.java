package cn.unfair.util.anticheat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ACPlayerData {
    public final Map<String, CheckData> checkDataMap = new ConcurrentHashMap<>();
    public final List<PositionSample> positionHistory = new ArrayList<>();
    public final List<Integer> crouchDurations = new ArrayList<>();
    public final List<SwingData> swingHistory = new ArrayList<>();
    public final List<Long> movementPacketTimes = new ArrayList<>();
    public final List<Long> clickIntervals = new ArrayList<>();
    public final List<Long> clickDeviationSamples = new ArrayList<>();
    private final EntityPlayer player;
    public Vec3 velocity;
    public Vec3 lastVelocity;
    public Vec3 lastPosition;
    public long lastSwingTime;
    public long lastUseItemTime;
    public long lastBlockPlaceTime;
    public long lastDamageTime;
    public long lastBlockStartTime;
    public boolean isUsingItem;
    public boolean isBlocking;
    public boolean isSprinting;
    public boolean isCrouching;
    public boolean isOnGround;
    public boolean wasSwinging;
    public boolean wasBlocking;
    public boolean wasUsingItem;
    public boolean wasCrouching;
    public float swingProgress;
    public float lastSwingProgress;
    public long currentTick;
    public long lastCrouchStartTick;
    public long lastCrouchEndTick;
    public long lastSwingTick;
    public int eaglePatternCount;
    public long lastEaglePatternTick;
    public int eagleConsecutiveViolations;
    public int scaffoldConsecutiveViolations;
    public String lastScaffoldViolationType = "";
    public long lastScaffoldViolationTime;
    public long lastScaffoldSwingTick;
    public long lastScaffoldFlagTick;
    public long lastSwingDetected;
    public long noSlowStartTime;
    public boolean noSlowActive;
    public long lastPacketTime;
    public long lastMovementPacketTime;
    public int ticksSinceLastVelocity = 1000;
    public int ticksSinceLastTeleport = 1000;
    public double packetX;
    public double packetY;
    public double packetZ;
    public double lastPacketX;
    public double lastPacketY;
    public double lastPacketZ;
    public double deltaX;
    public double deltaY;
    public double deltaZ;
    public double lastDeltaX;
    public double lastDeltaY;
    public double lastDeltaZ;
    public double packetSpeed;
    public double lastPacketSpeed;
    public boolean packetOnGround;
    public boolean lastPacketOnGround;
    public boolean lastUsingItemForPacket;
    public long timeSinceLastMovementPacket;
    public boolean fakeLagSuspicious;
    public boolean fakeLagStarted;
    public long fakeLagAverage;
    public long fakeLagPossibleDelay;
    public int fakeLagPacketsInRow;
    public double noSlowABuffer;
    public double noSlowBBuffer;
    public double motionABuffer;
    public double motionBBuffer;
    public double motionCBuffer;
    public double invalidSwingBuffer;
    public long lastSwingPacketTime;
    public double autoClickABuffer;
    public long autoClickAStarted;
    public long autoClickBStarted;
    public int autoClickBCount;

    public ACPlayerData(EntityPlayer player) {
        this.player = player;
    }

    public EntityPlayer getPlayer() {
        return player;
    }

    public void updatePosition(double x, double y, double z) {
        long now = System.currentTimeMillis();
        if (!positionHistory.isEmpty()) {
            PositionSample last = positionHistory.get(positionHistory.size() - 1);
            long delta = now - last.timestamp;
            if (delta > 0) {
                lastVelocity = velocity;
                double seconds = delta / 1000.0D;
                velocity = new Vec3(
                        (x - last.pos.xCoord) / seconds,
                        (y - last.pos.yCoord) / seconds,
                        (z - last.pos.zCoord) / seconds
                );
            }
        }
        positionHistory.add(new PositionSample(new Vec3(x, y, z), now));
        if (positionHistory.size() > 20) {
            positionHistory.remove(0);
        }
    }

    public boolean isHoldingBlock() {
        ItemStack stack = player.getHeldItem();
        return stack != null && stack.getItem() instanceof ItemBlock;
    }

    public static class CheckData {
        public double violations;
        public long lastAlertTime;
    }

    public record PositionSample(Vec3 pos, long timestamp) {
    }

    public static class SwingData {
        public final long time;
        public final boolean wasBlockingBefore;
        public Boolean wasBlockingAfter;

        public SwingData(long time, boolean wasBlockingBefore) {
            this.time = time;
            this.wasBlockingBefore = wasBlockingBefore;
        }
    }
}
