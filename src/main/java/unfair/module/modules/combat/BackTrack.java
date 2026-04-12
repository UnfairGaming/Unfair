package unfair.module.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.*;
import net.minecraft.network.status.client.C00PacketServerQuery;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import unfair.event.EventTarget;
import unfair.event.types.EventType;
import unfair.events.AttackEvent;
import unfair.events.LoadWorldEvent;
import unfair.events.PacketEvent;
import unfair.events.Render3DEvent;
import unfair.mixin.IAccessorRenderManager;
import unfair.module.Module;
import unfair.property.properties.*;
import unfair.util.RenderUtil;
import unfair.util.RotationUtil;
import unfair.util.TimerUtil;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BackTrack extends Module {

    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final float ESP_SMOOTH_FACTOR = 0.2f;
    public final ModeProperty mode = new ModeProperty("Mode", 1, new String[]{"Legacy", "Modern"});
    public final IntProperty nextBacktrackDelay = new IntProperty(
            "NextBacktrackDelay",
            0,
            0,
            2000,
            () -> this.mode.getValue() == 1
    );
    public final IntProperty maxDelay = new IntProperty("MaxDelay", 80, 0, 2000);
    public final IntProperty minDelay = new IntProperty(
            "MinDelay",
            80,
            0,
            2000,
            () -> this.mode.getValue() == 1
    );
    public final ModeProperty cachingMode = new ModeProperty(
            "Caching mode",
            0,
            new String[]{"ClientPos", "ServerPos"},
            () -> this.mode.getValue() == 0
    );
    public final ModeProperty style = new ModeProperty(
            "Style",
            1,
            new String[]{"Pulse", "Smooth"},
            () -> this.mode.getValue() == 1
    );
    public final FloatProperty distanceMin = new FloatProperty(
            "DistanceMin",
            2.0f,
            0.0f,
            6.0f,
            () -> this.mode.getValue() == 1
    );
    public final FloatProperty distanceMax = new FloatProperty(
            "DistanceMax",
            3.0f,
            0.0f,
            6.0f,
            () -> this.mode.getValue() == 1
    );
    public final BooleanProperty smart = new BooleanProperty(
            "Smart",
            true,
            () -> this.mode.getValue() == 1
    );
    public final ModeProperty espMode = new ModeProperty(
            "ESP-Mode",
            0,
            new String[]{"Box", "FullBox"},
            () -> this.mode.getValue() == 1
    );
    public final ColorProperty espColor = new ColorProperty(
            "ESPColor",
            0xFFFFFF,
            () -> this.mode.getValue() == 1
    );
    public final IntProperty maximumCachedPositions = new IntProperty(
            "MaxCachedPositions",
            10,
            1,
            20,
            () -> this.mode.getValue() == 0
    );
    private final Queue<QueueData> packetQueue = new ConcurrentLinkedQueue<>();
    private final Queue<PosData> positions = new ConcurrentLinkedQueue<>();
    private final Map<Integer, Vec3> truePositions = new HashMap<>();
    private final TimerUtil globalTimer = new TimerUtil();
    private final ConcurrentHashMap<UUID, LinkedList<BacktrackData>> backtrackedPlayer = new ConcurrentHashMap<>();
    private final String[] nonDelayedSoundSubstrings = new String[]{"game.player.hurt", "game.player.die"};
    private final ArrayDeque<Packet<?>> queuedPackets = new ArrayDeque<>();
    private final Object queueLock = new Object();
    private EntityLivingBase target = null;
    private boolean shouldRender = true;
    private boolean ignoreWholeTick = false;
    private long delayForNextBacktrack = 0L;
    private ModernDelay modernDelay = new ModernDelay(80, false);
    private Object lastWorld = null;
    private Object lastNetHandler = null;
    private Vec3 smoothedEspOffset = new Vec3(0, 0, 0);

    public BackTrack() {
        super("BackTrack", false);
    }

    private boolean areQueuedPacketsEmpty() {
        synchronized (queueLock) {
            return queuedPackets.isEmpty();
        }
    }

    @Override
    public void onEnabled() {
        this.reset();
        this.clearPackets(true, false);
        this.backtrackedPlayer.clear();
        this.lastWorld = mc.theWorld;
        this.lastNetHandler = mc.getNetHandler();
    }

    @Override
    public void onDisabled() {
        this.clearPackets(true, true);
        this.backtrackedPlayer.clear();
        this.truePositions.clear();
        this.target = null;
        this.lastWorld = null;
        this.lastNetHandler = null;

        synchronized (queueLock) {
            queuedPackets.clear();
        }
    }

    private void resetAllState(boolean handleQueuedPackets) {
        this.clearPackets(handleQueuedPackets, true);
        this.reset();
        this.backtrackedPlayer.clear();
        this.truePositions.clear();
        this.target = null;
    }

    private void syncContextAndResetIfNeeded() {
        Object world = mc.theWorld;
        Object netHandler = mc.getNetHandler();

        if (world == null || mc.thePlayer == null || netHandler == null) {
            if (!this.packetQueue.isEmpty() || !this.positions.isEmpty() || !this.truePositions.isEmpty() || this.target != null) {
                resetAllState(false);
            }
            this.lastWorld = world;
            this.lastNetHandler = netHandler;
            return;
        }

        if (this.lastWorld != world || this.lastNetHandler != netHandler) {
            resetAllState(false);
        }

        this.lastWorld = world;
        this.lastNetHandler = netHandler;
    }

    @Override
    public void verifyValue(String name) {
        if (this.maxDelay.getName().equals(name)) {
            if (this.maxDelay.getValue() < this.minDelay.getValue()) {
                this.maxDelay.setValue(this.minDelay.getValue());
            }
        } else if (this.minDelay.getName().equals(name)) {
            if (this.minDelay.getValue() > this.maxDelay.getValue()) {
                this.minDelay.setValue(this.maxDelay.getValue());
            }
        } else if (this.distanceMin.getName().equals(name)) {
            if (this.distanceMin.getValue() > this.distanceMax.getValue()) {
                this.distanceMin.setValue(this.distanceMax.getValue());
            }
        } else if (this.distanceMax.getName().equals(name)) {
            if (this.distanceMax.getValue() < this.distanceMin.getValue()) {
                this.distanceMax.setValue(this.distanceMin.getValue());
            }
        } else if (this.mode.getName().equals(name)) {
            this.clearPackets(true, true);
            this.backtrackedPlayer.clear();
            this.target = null;
        }
    }

    private boolean isModern() {
        return this.mode.getValue() == 1;
    }

    private boolean isLegacy() {
        return this.mode.getValue() == 0;
    }

    private int supposedDelay() {
        if (isModern()) {
            return modernDelay.delay;
        }
        return this.maxDelay.getValue();
    }

    private boolean inDistanceRange(double range) {
        return range >= this.distanceMin.getValue() && range <= this.distanceMax.getValue();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (!this.isEnabled()) return;
        if (!(event.getTarget() instanceof EntityLivingBase)) return;

        EntityLivingBase newTarget = (EntityLivingBase) event.getTarget();
        if (this.target != newTarget) {
            this.clearPackets(true, true);
            this.reset();
        }
        this.target = newTarget;
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        if (!this.isEnabled()) return;
        resetAllState(false);
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled()) return;
        syncContextAndResetIfNeeded();
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;
        if (event.isCancelled()) return;

        Packet<?> packet = event.getPacket();

        if (isLegacy()) {
            handleLegacyPacket(packet, event);
            return;
        }

        handleModernPacket(packet, event);
    }

    private void handleLegacyPacket(Packet<?> packet, PacketEvent event) {
        if (packet instanceof S0CPacketSpawnPlayer) {
            S0CPacketSpawnPlayer p = (S0CPacketSpawnPlayer) packet;
            Entity e = mc.theWorld.getEntityByID(p.getEntityID());
            if (e instanceof EntityPlayer) {
                addBacktrackData(e.getUniqueID(), p.getX() / 32.0, p.getY() / 32.0, p.getZ() / 32.0, System.currentTimeMillis());
            }
            return;
        }

        if (packet instanceof S14PacketEntity) {
            updateTruePosition((S14PacketEntity) packet);
            if (this.cachingMode.getValue() == 1) {
                Entity e = ((S14PacketEntity) packet).getEntity(mc.theWorld);
                if (e != null) {
                    Vec3 v = this.truePositions.get(e.getEntityId());
                    if (v != null) {
                        addBacktrackData(e.getUniqueID(), v.xCoord, v.yCoord, v.zCoord, System.currentTimeMillis());
                    }
                }
            }
            return;
        }

        if (packet instanceof S18PacketEntityTeleport) {
            updateTruePosition((S18PacketEntityTeleport) packet);
            if (this.cachingMode.getValue() == 1) {
                Entity e = mc.theWorld.getEntityByID(((S18PacketEntityTeleport) packet).getEntityId());
                if (e != null) {
                    Vec3 v = this.truePositions.get(e.getEntityId());
                    if (v != null) {
                        addBacktrackData(e.getUniqueID(), v.xCoord, v.yCoord, v.zCoord, System.currentTimeMillis());
                    }
                }
            }
        }

        if (packet instanceof S29PacketSoundEffect) {
            // no-op
        }

        if (this.cachingMode.getValue() == 0 && packet instanceof S14PacketEntity) {
            Entity e = ((S14PacketEntity) packet).getEntity(mc.theWorld);
            if (e instanceof EntityPlayer) {
                addBacktrackData(e.getUniqueID(), e.posX, e.posY, e.posZ, System.currentTimeMillis());
            }
        }
    }

    private void handleModernPacket(Packet<?> packet, PacketEvent event) {
        if (mc.isSingleplayer()) {
            clearPackets(true, true);
            return;
        }

        if (packet instanceof S40PacketDisconnect) {
            resetAllState(false);
            return;
        }

        if (packet instanceof S01PacketJoinGame
                || packet instanceof S07PacketRespawn
                || packet instanceof S08PacketPlayerPosLook
                || packet instanceof S21PacketChunkData
                || packet instanceof S26PacketMapChunkBulk) {
            this.clearPackets(false, true);
            this.reset();
            this.target = null;
            this.truePositions.clear();
            return;
        }

        if (packet instanceof C00Handshake
                || packet instanceof C00PacketServerQuery
                || packet instanceof S02PacketChat
                || packet instanceof net.minecraft.network.status.server.S01PacketPong) {
            return;
        }

        if (packet instanceof S29PacketSoundEffect) {
            S29PacketSoundEffect s29 = (S29PacketSoundEffect) packet;
            for (String s : nonDelayedSoundSubstrings) {
                if (s29.getSoundName().contains(s)) {
                    return;
                }
            }
        }

        if (packet instanceof S06PacketUpdateHealth) {
            if (((S06PacketUpdateHealth) packet).getHealth() <= 0.0f) {
                clearPackets(true, true);
                return;
            }
        }

        if (packet instanceof S13PacketDestroyEntities && this.target != null) {
            for (int id : ((S13PacketDestroyEntities) packet).getEntityIDs()) {
                if (id == this.target.getEntityId()) {
                    clearPackets(true, true);
                    reset();
                    return;
                }
            }
        }

        if (packet instanceof S1CPacketEntityMetadata && this.target != null) {
            S1CPacketEntityMetadata meta = (S1CPacketEntityMetadata) packet;
            if (meta.getEntityId() == this.target.getEntityId()) {
                List<?> data = meta.func_149376_c();
                if (data != null) {
                    for (Object obj : data) {
                        if (obj instanceof net.minecraft.entity.DataWatcher.WatchableObject) {
                            net.minecraft.entity.DataWatcher.WatchableObject wo = (net.minecraft.entity.DataWatcher.WatchableObject) obj;
                            if (wo.getDataValueId() == 6) {
                                try {
                                    double v = Double.parseDouble(String.valueOf(wo.getObject()));
                                    if (!Double.isNaN(v) && v <= 0.0) {
                                        clearPackets(true, true);
                                        reset();
                                        return;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    }
                }
                return;
            }
        }

        if (packet instanceof S19PacketEntityStatus && this.target != null) {
            int id = PacketFieldCache.getS19EntityId((S19PacketEntityStatus) packet);
            if (id != -1 && id == this.target.getEntityId()) {
                return;
            }
        }

        if (packet instanceof S0CPacketSpawnPlayer) {
            S0CPacketSpawnPlayer p = (S0CPacketSpawnPlayer) packet;
            this.truePositions.put(p.getEntityID(), new Vec3(p.getX() / 32.0, p.getY() / 32.0, p.getZ() / 32.0));
        } else if (packet instanceof S0FPacketSpawnMob) {
            S0FPacketSpawnMob p = (S0FPacketSpawnMob) packet;
            this.truePositions.put(p.getEntityID(), new Vec3(p.getX() / 32.0, p.getY() / 32.0, p.getZ() / 32.0));
        } else if (packet instanceof S14PacketEntity) {
            updateTruePosition((S14PacketEntity) packet);
        } else if (packet instanceof S18PacketEntityTeleport) {
            updateTruePosition((S18PacketEntityTeleport) packet);
        }

        if (this.packetQueue.isEmpty() && areQueuedPacketsEmpty() && !shouldBacktrack()) {
            return;
        }

        if (event.getType() == EventType.RECEIVE) {
            if (packet instanceof S14PacketEntity && this.target != null) {
                S14PacketEntity p = (S14PacketEntity) packet;
                int id = PacketFieldCache.getS14EntityId(p);
                if (id != -1 && id == this.target.getEntityId()) {
                    Vec3 v = this.truePositions.get(id);
                    if (v != null) {
                        this.positions.add(new PosData(v, System.currentTimeMillis()));
                    }
                }
            }

            if (packet instanceof S18PacketEntityTeleport && this.target != null) {
                S18PacketEntityTeleport p = (S18PacketEntityTeleport) packet;
                int id = PacketFieldCache.getS18EntityId(p);
                if (id != -1 && id == this.target.getEntityId()) {
                    Vec3 v = this.truePositions.get(id);
                    if (v != null) {
                        this.positions.add(new PosData(v, System.currentTimeMillis()));
                    }
                }
            }

            event.setCancelled(true);
            this.packetQueue.add(new QueueData(packet, System.currentTimeMillis()));
        }
    }

    @EventTarget
    public void onTick(unfair.events.TickEvent event) {
        if (!this.isEnabled()) return;
        syncContextAndResetIfNeeded();
        if (mc.thePlayer == null || mc.theWorld == null || mc.getNetHandler() == null) return;
        if (event.getType() != EventType.POST) return;

        if (isLegacy()) {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, LinkedList<BacktrackData>> e : backtrackedPlayer.entrySet()) {
                LinkedList<BacktrackData> list = e.getValue();
                list.removeIf(d -> d.time + supposedDelay() < now);
                if (list.isEmpty()) {
                    backtrackedPlayer.remove(e.getKey());
                }
            }

            processQueuedPackets();
            return;
        }

        if (shouldBacktrack() && this.target != null) {
            Vec3 truePos = this.truePositions.get(this.target.getEntityId());
            if (truePos != null) {
                double trueDist = mc.thePlayer.getDistance(truePos.xCoord, truePos.yCoord, truePos.zCoord);
                double dist = mc.thePlayer.getDistanceToEntity(this.target);

                boolean smoothAllow = this.style.getValue() == 1 || !this.globalTimer.hasTimeElapsed(supposedDelay());

                if (trueDist <= 6.0
                        && (!this.smart.getValue() || trueDist >= dist)
                        && smoothAllow) {
                    this.shouldRender = true;
                    double rangeToTarget = RotationUtil.distanceToEntity(this.target);
                    if (inDistanceRange(rangeToTarget)) {
                        handlePackets();
                    } else {
                        handlePacketsRange();
                    }
                } else {
                    clear();
                }
            } else {
                clear();
            }
        } else {
            clear();
        }

        if (this.packetQueue.isEmpty() && areQueuedPacketsEmpty()) {
            if (!this.modernDelay.changed && !shouldBacktrack()) {
                this.delayForNextBacktrack = System.currentTimeMillis() + this.nextBacktrackDelay.getValue();
                this.modernDelay = new ModernDelay((int) unfair.util.RandomUtil.nextLong(this.minDelay.getValue(), this.maxDelay.getValue()), true);
            }
        } else {
            this.modernDelay = new ModernDelay(this.modernDelay.delay, false);
        }

        this.ignoreWholeTick = false;

        processQueuedPackets();
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled()) return;
        if (!isModern()) return;
        if (!shouldBacktrack() || !this.shouldRender) return;
        if (this.target == null) return;
        if (mc.theWorld == null) return;

        Vec3 truePos = this.truePositions.get(this.target.getEntityId());
        if (truePos == null) return;

        // Safety: verify target is still valid in the world
        if (mc.theWorld.getEntityByID(this.target.getEntityId()) != this.target) {
            this.clear();
            return;
        }

        double partialTicks = event.getPartialTicks();
        double entityRenderX = this.target.lastTickPosX + (this.target.posX - this.target.lastTickPosX) * partialTicks;
        double entityRenderY = this.target.lastTickPosY + (this.target.posY - this.target.lastTickPosY) * partialTicks;
        double entityRenderZ = this.target.lastTickPosZ + (this.target.posZ - this.target.lastTickPosZ) * partialTicks;

        Vec3 targetOffset = new Vec3(
                truePos.xCoord - entityRenderX,
                truePos.yCoord - entityRenderY,
                truePos.zCoord - entityRenderZ
        );

        this.smoothedEspOffset = new Vec3(
                this.smoothedEspOffset.xCoord + (targetOffset.xCoord - this.smoothedEspOffset.xCoord) * ESP_SMOOTH_FACTOR,
                this.smoothedEspOffset.yCoord + (targetOffset.yCoord - this.smoothedEspOffset.yCoord) * ESP_SMOOTH_FACTOR,
                this.smoothedEspOffset.zCoord + (targetOffset.zCoord - this.smoothedEspOffset.zCoord) * ESP_SMOOTH_FACTOR
        );

        double x = entityRenderX + this.smoothedEspOffset.xCoord;
        double y = entityRenderY + this.smoothedEspOffset.yCoord;
        double z = entityRenderZ + this.smoothedEspOffset.zCoord;

        int rgb = this.espColor.getValue();
        int r = (rgb >> 16) & 255;
        int g = (rgb >> 8) & 255;
        int b = rgb & 255;

        AxisAlignedBB aabb = this.target.getEntityBoundingBox()
                .offset(x - this.target.posX, y - this.target.posY, z - this.target.posZ)
                .offset(
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosX(),
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosY(),
                        -((IAccessorRenderManager) mc.getRenderManager()).getRenderPosZ()
                );

        switch (this.espMode.getValue()) {
            case 0: {
                GlStateManager.pushMatrix();
                GlStateManager.disableTexture2D();
                GlStateManager.disableLighting();
                GlStateManager.enableBlend();
                GlStateManager.disableDepth();
                GlStateManager.depthMask(false);

                RenderGlobal.drawOutlinedBoundingBox(aabb, r, g, b, 200);

                GlStateManager.depthMask(true);
                GlStateManager.enableDepth();
                GlStateManager.disableBlend();
                GlStateManager.enableLighting();
                GlStateManager.enableTexture2D();
                GlStateManager.popMatrix();
                break;
            }
            case 1: {
                RenderUtil.enableRenderState();
                RenderUtil.drawFilledBox(aabb, r, g, b);
                RenderUtil.disableRenderState();
                break;
            }
        }
    }

    private void handlePackets() {
        long now = System.currentTimeMillis();
        int delay = supposedDelay();

        packetQueue.removeIf(data -> {
            if (data.time <= now - delay) {
                schedulePacketProcess(data.packet);
                return true;
            }
            return false;
        });

        positions.removeIf(pd -> pd.time < now - delay);
    }

    private void handlePacketsRange() {
        long time = getRangeTime();
        if (time == -1L) {
            clearPackets(true, true);
            return;
        }

        packetQueue.removeIf(data -> {
            if (data.time <= time) {
                schedulePacketProcess(data.packet);
                return true;
            }
            return false;
        });

        positions.removeIf(pd -> pd.time < time);
    }

    private long getRangeTime() {
        if (this.target == null) return 0L;

        long time = 0L;
        boolean found = false;
        for (PosData data : positions) {
            time = data.time;

            AxisAlignedBB box = this.target.getEntityBoundingBox().offset(
                    data.pos.xCoord - this.target.posX,
                    data.pos.yCoord - this.target.posY,
                    data.pos.zCoord - this.target.posZ
            );

            double dist = RotationUtil.clampVecToBox(box, mc.thePlayer.getPositionEyes(1.0f));
            if (inDistanceRange(dist)) {
                found = true;
                break;
            }
        }

        return found ? time : -1L;
    }

    private void clearPackets(boolean handlePackets, boolean stopRendering) {
        packetQueue.removeIf(data -> {
            if (handlePackets) {
                schedulePacketProcess(data.packet);
            }
            return true;
        });
        positions.clear();

        if (stopRendering) {
            this.shouldRender = false;
            this.ignoreWholeTick = true;
        }
    }

    private void schedulePacketProcess(Packet<?> packet) {
        synchronized (queueLock) {
            queuedPackets.add(packet);
        }
    }

    private void processQueuedPackets() {
        synchronized (queueLock) {
            while (!queuedPackets.isEmpty()) {
                Packet<?> packet = queuedPackets.poll();
                if (packet != null) {
                    processPacket(packet);
                }
            }
        }
    }

    private void processPacket(Packet<?> packet) {
        if (mc.getNetHandler() != null) {
            try {
                ((Packet<INetHandlerPlayClient>) packet).processPacket(mc.getNetHandler());
            } catch (Throwable ignored) {
            }
        }
    }

    private void updateTruePosition(S14PacketEntity packet) {
        int id = PacketFieldCache.getS14EntityId(packet);
        if (id == -1) {
            return;
        }
        Vec3 pos = this.truePositions.getOrDefault(id, new Vec3(0, 0, 0));
        Vec3 next = pos.addVector(packet.func_149062_c() / 32.0, packet.func_149061_d() / 32.0, packet.func_149064_e() / 32.0);
        this.truePositions.put(id, next);
    }

    private void updateTruePosition(S18PacketEntityTeleport packet) {
        int id = PacketFieldCache.getS18EntityId(packet);
        if (id == -1) {
            return;
        }
        this.truePositions.put(id, new Vec3(packet.getX() / 32.0, packet.getY() / 32.0, packet.getZ() / 32.0));
    }

    private void addBacktrackData(UUID id, double x, double y, double z, long time) {
        LinkedList<BacktrackData> list = this.backtrackedPlayer.get(id);
        if (list == null) {
            list = new LinkedList<>();
            this.backtrackedPlayer.put(id, list);
        }
        if (list.size() >= this.maximumCachedPositions.getValue()) {
            list.removeFirst();
        }
        list.add(new BacktrackData(x, y, z, time));
    }

    public void loopThroughBacktrackData(Entity entity, Runnable action) {
        if (!this.isEnabled() || !isLegacy()) return;
        if (!(entity instanceof EntityPlayer)) return;

        LinkedList<BacktrackData> list = this.backtrackedPlayer.get(entity.getUniqueID());
        if (list == null) return;

        double currX = entity.posX;
        double currY = entity.posY;
        double currZ = entity.posZ;
        double prevX = entity.prevPosX;
        double prevY = entity.prevPosY;
        double prevZ = entity.prevPosZ;

        ListIterator<BacktrackData> it = list.listIterator(list.size());
        while (it.hasPrevious()) {
            BacktrackData d = it.previous();
            entity.setPosition(d.x, d.y, d.z);
            entity.prevPosX = d.x;
            entity.prevPosY = d.y;
            entity.prevPosZ = d.z;
            action.run();
        }

        entity.setPosition(currX, currY, currZ);
        entity.prevPosX = prevX;
        entity.prevPosY = prevY;
        entity.prevPosZ = prevZ;
    }

    public <T> T runWithNearestTrackedDistance(Entity entity, java.util.concurrent.Callable<T> f) {
        if (!this.isEnabled() || !isLegacy() || !(entity instanceof EntityPlayer)) {
            try {
                return f.call();
            } catch (Exception e) {
                return null;
            }
        }

        LinkedList<BacktrackData> list = this.backtrackedPlayer.get(entity.getUniqueID());
        if (list == null || list.isEmpty()) {
            try {
                return f.call();
            } catch (Exception e) {
                return null;
            }
        }

        BacktrackData best = null;
        double bestDist = Double.MAX_VALUE;
        for (BacktrackData d : list) {
            double oldX = entity.posX, oldY = entity.posY, oldZ = entity.posZ;
            double oldPX = entity.prevPosX, oldPY = entity.prevPosY, oldPZ = entity.prevPosZ;

            entity.setPosition(d.x, d.y, d.z);
            entity.prevPosX = d.x;
            entity.prevPosY = d.y;
            entity.prevPosZ = d.z;

            double dist = RotationUtil.distanceToEntity(entity);

            entity.setPosition(oldX, oldY, oldZ);
            entity.prevPosX = oldPX;
            entity.prevPosY = oldPY;
            entity.prevPosZ = oldPZ;

            if (dist < bestDist) {
                bestDist = dist;
                best = d;
            }
        }

        if (best == null) {
            try {
                return f.call();
            } catch (Exception e) {
                return null;
            }
        }

        double currX = entity.posX;
        double currY = entity.posY;
        double currZ = entity.posZ;
        double prevX = entity.prevPosX;
        double prevY = entity.prevPosY;
        double prevZ = entity.prevPosZ;

        entity.setPosition(best.x, best.y, best.z);
        entity.prevPosX = best.x;
        entity.prevPosY = best.y;
        entity.prevPosZ = best.z;

        try {
            return f.call();
        } catch (Exception e) {
            return null;
        } finally {
            entity.setPosition(currX, currY, currZ);
            entity.prevPosX = prevX;
            entity.prevPosY = prevY;
            entity.prevPosZ = prevZ;
        }
    }

    private boolean shouldBacktrack() {
        if (mc.thePlayer == null || mc.theWorld == null) return false;
        if (this.target == null) return false;
        if (mc.thePlayer.getHealth() <= 0.0f) return false;
        if (!(this.target.getHealth() > 0.0f) && !Float.isNaN(this.target.getHealth())) return false;
        if (mc.playerController != null && mc.playerController.getCurrentGameType() == net.minecraft.world.WorldSettings.GameType.SPECTATOR)
            return false;
        if (System.currentTimeMillis() < this.delayForNextBacktrack) return false;
        if (mc.thePlayer.ticksExisted <= 20) return false;
        return !this.ignoreWholeTick;
    }

    private void reset() {
        this.target = null;
        this.globalTimer.reset();
        this.delayForNextBacktrack = 0L;
        this.modernDelay = new ModernDelay((int) unfair.util.RandomUtil.nextLong(this.minDelay.getValue(), this.maxDelay.getValue()), false);
    }

    private void clear() {
        this.clearPackets(true, true);
        this.globalTimer.reset();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{supposedDelay() + "ms"};
    }

    private static final class PacketFieldCache {
        private static Field s14EntityId;
        private static Field s18EntityId;
        private static Field s19EntityId;

        private static int getS14EntityId(S14PacketEntity packet) {
            try {
                if (s14EntityId == null) {
                    s14EntityId = S14PacketEntity.class.getDeclaredField("entityId");
                    s14EntityId.setAccessible(true);
                }
                return (int) s14EntityId.get(packet);
            } catch (Throwable t) {
                return -1;
            }
        }

        private static int getS18EntityId(S18PacketEntityTeleport packet) {
            try {
                if (s18EntityId == null) {
                    s18EntityId = S18PacketEntityTeleport.class.getDeclaredField("entityId");
                    s18EntityId.setAccessible(true);
                }
                return (int) s18EntityId.get(packet);
            } catch (Throwable t) {
                return -1;
            }
        }

        private static int getS19EntityId(S19PacketEntityStatus packet) {
            try {
                if (s19EntityId == null) {
                    s19EntityId = S19PacketEntityStatus.class.getDeclaredField("entityId");
                    s19EntityId.setAccessible(true);
                }
                return (int) s19EntityId.get(packet);
            } catch (Throwable t) {
                return -1;
            }
        }
    }

    private static final class QueueData {
        private final Packet<?> packet;
        private final long time;

        private QueueData(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }
    }

    private static final class PosData {
        private final Vec3 pos;
        private final long time;

        private PosData(Vec3 pos, long time) {
            this.pos = pos;
            this.time = time;
        }
    }

    private static final class BacktrackData {
        private final double x;
        private final double y;
        private final double z;
        private final long time;

        private BacktrackData(double x, double y, double z, long time) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.time = time;
        }
    }

    private static final class ModernDelay {
        private final int delay;
        private final boolean changed;

        private ModernDelay(int delay, boolean changed) {
            this.delay = delay;
            this.changed = changed;
        }
    }
}
