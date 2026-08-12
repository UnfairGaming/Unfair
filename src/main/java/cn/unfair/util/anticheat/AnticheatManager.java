package cn.unfair.util.anticheat;

import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.module.modules.misc.AntiCheat;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.anticheat.check.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AnticheatManager {
    private final Minecraft mc = Minecraft.getMinecraft();
    private final Map<UUID, ACPlayerData> playerData = new ConcurrentHashMap<>();
    private final List<AntiCheatCheck> checks = new ArrayList<>();
    private final AntiCheat module;
    private long currentTick;

    public AnticheatManager(AntiCheat module) {
        this.module = module;
        reloadChecks();
    }

    public void reloadChecks() {
        checks.clear();
        if (module.noSlowCheck.getValue()) checks.add(new NoSlowCCheck());
        if (module.autoBlockCheck.getValue()) checks.add(new AutoBlockCheck());
        if (module.eagleCheck.getValue()) checks.add(new EagleCheck());
        if (module.scaffoldCheck.getValue()) checks.add(new ScaffoldCheck());
        if (module.noSlowABCheck.getValue()) {
            checks.add(new NoSlowACheck());
            checks.add(new NoSlowBCheck());
        }
        if (module.motionCheck.getValue()) {
            checks.add(new MotionACheck());
            checks.add(new MotionBCheck());
        }
        if (module.invalidSwingCheck.getValue()) checks.add(new InvalidSwingCheck());
        if (module.autoClickerCheck.getValue()) {
            checks.add(new AutoClickerACheck());
            checks.add(new AutoClickerBCheck());
        }
    }

    public ACPlayerData getPlayerData(EntityPlayer player) {
        return player == null ? null : playerData.get(player.getUniqueID());
    }

    public void clearPlayers() {
        playerData.clear();
    }

    public void tick() {
        if (mc.theWorld == null || mc.thePlayer == null) {
            clearPlayers();
            return;
        }

        currentTick++;
        Set<UUID> currentPlayers = new HashSet<>();
        for (Object object : mc.theWorld.playerEntities) {
            EntityPlayer player = (EntityPlayer) object;
            if (player == mc.thePlayer) continue;
            currentPlayers.add(player.getUniqueID());
            ACPlayerData data = playerData.computeIfAbsent(player.getUniqueID(), key -> new ACPlayerData(player));
            update(data);
            for (AntiCheatCheck check : checks) {
                check.onTick(this, data);
            }
        }
        playerData.keySet().removeIf(uuid -> !currentPlayers.contains(uuid));
        playerData.values().forEach(data -> data.ticksSinceLastVelocity++);
        playerData.values().forEach(data -> data.ticksSinceLastTeleport++);
    }

    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.RECEIVE || mc.theWorld == null || mc.thePlayer == null) return;
        Packet<?> packet = event.getPacket();
        EntityPlayer player = findPacketPlayer(packet);
        if (player == null || player == mc.thePlayer) return;

        ACPlayerData data = playerData.computeIfAbsent(player.getUniqueID(), key -> new ACPlayerData(player));
        updatePacketState(data, packet);
        for (AntiCheatCheck check : checks) {
            check.onPacket(this, data, packet);
        }
    }

    private EntityPlayer findPacketPlayer(Packet<?> packet) {
        int entityId = -1;
        if (packet instanceof S14PacketEntity movement) entityId = movement.getEntityId();
        else if (packet instanceof S18PacketEntityTeleport teleport) entityId = teleport.getEntityId();
        else if (packet instanceof S12PacketEntityVelocity velocity) entityId = velocity.getEntityID();
        else if (packet instanceof S0BPacketAnimation animation) entityId = animation.getEntityID();
        return entityId < 0 || mc.theWorld.getEntityByID(entityId) == null
                ? null : (mc.theWorld.getEntityByID(entityId) instanceof EntityPlayer player ? player : null);
    }

    private void updatePacketState(ACPlayerData data, Packet<?> packet) {
        long now = System.currentTimeMillis();
        data.lastPacketTime = now;
        if (packet instanceof S12PacketEntityVelocity) {
            data.ticksSinceLastVelocity = 0;
            return;
        }
        if (packet instanceof S18PacketEntityTeleport teleport) {
            data.packetX = teleport.getX();
            data.packetY = teleport.getY();
            data.packetZ = teleport.getZ();
            data.ticksSinceLastTeleport = 0;
            data.lastMovementPacketTime = now;
            data.timeSinceLastMovementPacket = 0L;
            return;
        }
        if (!(packet instanceof S14PacketEntity movement)) return;
        long deltaTime = data.lastMovementPacketTime == 0L ? 50L : Math.max(1L, now - data.lastMovementPacketTime);
        data.timeSinceLastMovementPacket = deltaTime;
        data.lastMovementPacketTime = now;
        data.lastDeltaX = data.deltaX;
        data.lastDeltaY = data.deltaY;
        data.lastDeltaZ = data.deltaZ;
        data.lastPacketSpeed = data.packetSpeed;
        data.lastPacketX = data.packetX;
        data.lastPacketY = data.packetY;
        data.lastPacketZ = data.packetZ;
        if (movement instanceof S14PacketEntity.S15PacketEntityRelMove
                || movement instanceof S14PacketEntity.S17PacketEntityLookMove) {
            data.deltaX = movement.func_149062_c() / 32.0D;
            data.deltaY = movement.func_149061_d() / 32.0D;
            data.deltaZ = movement.func_149064_e() / 32.0D;
            data.packetX += data.deltaX;
            data.packetY += data.deltaY;
            data.packetZ += data.deltaZ;
        }
        data.packetSpeed = Math.hypot(data.deltaX, data.deltaZ);
        data.packetOnGround = movement.getOnGround();
        data.lastPacketOnGround = data.packetOnGround;
        data.movementPacketTimes.add(deltaTime);
        while (data.movementPacketTimes.size() > 20) data.movementPacketTimes.remove(0);
    }

    private void update(ACPlayerData data) {
        EntityPlayer player = data.getPlayer();
        data.currentTick = currentTick;
        data.lastPosition = new net.minecraft.util.Vec3(player.lastTickPosX, player.lastTickPosY, player.lastTickPosZ);
        data.updatePosition(player.posX, player.posY, player.posZ);
        data.lastSwingProgress = data.swingProgress;
        data.swingProgress = player.swingProgress;
        data.wasCrouching = data.isCrouching;
        data.isSprinting = player.isSprinting();
        data.isCrouching = player.isSneaking();
        data.isUsingItem = player.isUsingItem();
        data.isOnGround = player.onGround;
        data.isBlocking = player.isBlocking();

        if (data.isBlocking && !data.wasBlocking) {
            data.lastBlockStartTime = System.currentTimeMillis();
        }
        data.wasBlocking = data.isBlocking;

        if (player.isSwingInProgress && !data.wasSwinging) {
            data.lastSwingTime = System.currentTimeMillis();
            data.lastSwingTick = currentTick;
        }
        data.wasSwinging = player.isSwingInProgress;

        if (data.isUsingItem && !data.wasUsingItem && data.isHoldingBlock()) {
            data.lastBlockPlaceTime = System.currentTimeMillis();
        }
        data.wasUsingItem = data.isUsingItem;

        if (data.isCrouching && !data.wasCrouching) {
            data.lastCrouchStartTick = currentTick;
        } else if (!data.isCrouching && data.wasCrouching && data.lastCrouchStartTick > 0) {
            data.crouchDurations.add(0, (int) (currentTick - data.lastCrouchStartTick));
            if (data.crouchDurations.size() > 10) {
                data.crouchDurations.remove(data.crouchDurations.size() - 1);
            }
            data.lastCrouchEndTick = currentTick;
        }
    }

    public void flag(ACPlayerData data, AntiCheatCheck check, String details, double amount) {
        ACPlayerData.CheckData checkData = data.checkDataMap.computeIfAbsent(check.getName(), key -> new ACPlayerData.CheckData());
        checkData.violations += amount;
        long now = System.currentTimeMillis();
        if (checkData.violations >= module.vl.getValue()
                && now - checkData.lastAlertTime >= module.cooldown.getValue() * 1000L) {
            String name = data.getPlayer().getName();
            ChatUtil.sendFormatted(String.format("&8[&cAC&8] &7%s &ffailed &c%s &7(%s) &c[VL: %.1f]",
                    name, check.getName(), details, checkData.violations));
            checkData.lastAlertTime = now;
        }
    }

    public void flag(ACPlayerData data, AntiCheatCheck check, String details, String verbose) {
        flag(data, check, details + (verbose == null || verbose.isEmpty() ? "" : ", " + verbose), 1.0D);
    }

    public AntiCheat getModule() {
        return module;
    }

    public List<EntityPlayer> getWorldPlayers() {
        if (mc.theWorld == null) return java.util.Collections.emptyList();
        List<EntityPlayer> players = new ArrayList<>();
        for (Object object : mc.theWorld.playerEntities) {
            if (object instanceof EntityPlayer player) players.add(player);
        }
        return players;
    }
}
