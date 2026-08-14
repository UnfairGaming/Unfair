package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.enums.ChatColors;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.*;
import cn.unfair.module.Module;
import cn.unfair.module.modules.movement.Eagle;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.util.BlockUtil;
import cn.unfair.util.ChatUtil;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.via.ViaProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.*;

public class LegitTelly extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final Map<String, Object> BRIDGE_VALUES = new HashMap<>();
    public final BooleanProperty autoSwap = new BooleanProperty("Auto Swap", true);
    public final BooleanProperty disableSafeWalk = new BooleanProperty("Disable Safewalk", true);
    public final BooleanProperty showActivationHitbox = new BooleanProperty("Show Activation Hitbox", true);
    public final BooleanProperty print = new BooleanProperty("Print", false);
    final int[] YAW_NUDGE_PATTERN = {0, 1, -1, 2, -2};
    final double ACTIVATION_ACROSS_MIN = 0.38;
    final double ACTIVATION_ACROSS_MAX = 0.65;
    final double ACTIVATION_HEIGHT_MIN = 0.25;
    final double ACTIVATION_HEIGHT_MAX = 0.75;
    final float ACTIVATION_YAW_TOLERANCE = 2.0f;
    final double[] FACE_HIT_OFFSETS = {0.5, 0.25, 0.75, 0.15, 0.85};
    final double[] EXTENDED_FACE_HIT_OFFSETS = {0.5, 0.25, 0.75, 0.15, 0.85, 0.35, 0.65, 0.05, 0.95};
    final int[] ALLOWED_PLACE_FACES = {2, 3, 4, 5, 1};
    final String[] REPLACEABLE_BLOCKS = {"air", "water", "flowing_water", "lava", "flowing_lava", "fire", "tallgrass", "deadbush", "snow_layer", "double_plant", "vine"};
    final String[] EXPERIMENTAL_REPLACEABLE_BLOCKS = {
            "sapling", "yellow_flower", "red_flower", "brown_mushroom", "red_mushroom",
            "wheat", "carrots", "potatoes", "nether_wart", "reeds"
    };
    final String[] UNPLACEABLE_EXACT = {
            "snow_layer", "web", "sapling", "daylight_detector", "beacon", "banner",
            "end_portal_frame", "end_portal", "lever", "stone_button", "wooden_button",
            "skull", "cactus", "double_plant", "waterlily", "carpet", "tripwire_hook",
            "tallgrass", "yellow_flower", "red_flower", "flower_pot", "sign", "ladder",
            "torch", "redstone_torch", "unlit_redstone_torch", "gravel", "clay", "sand",
            "soul_sand", "chest", "trapped_chest", "ender_chest", "furnace", "lit_furnace",
            "jukebox", "enchanting_table", "dropper", "dispenser", "hopper", "anvil",
            "noteblock", "crafting_table", "mob_spawner", "brewing_stand", "bed"
    };
    final String[] UNPLACEABLE_CONTAINS = {
            "stairs", "slab", "fence", "pane", "rail", "door",
            "torch", "pumpkin", "flower", "sapling", "banner", "button",
            "skull", "web", "carpet", "cactus", "sign", "mushroom"
    };
    final String[] INTERACTABLE_TYPES = {
            "BlockTrapDoor", "BlockDoor", "BlockContainer", "BlockJukebox", "BlockFenceGate",
            "BlockChest", "BlockEnderChest", "BlockEnchantmentTable", "BlockBrewingStand",
            "BlockBed", "BlockDropper", "BlockDispenser", "BlockHopper", "BlockAnvil",
            "BlockNote", "BlockWorkbench", "BlockFurnace", "BlockBeacon", "BlockMobSpawner",
            "BlockDaylightDetector", "BlockCommandBlock", "BlockStandingSign", "BlockWallSign", "BlockSkull"
    };
    private final String scriptName = "LegitTelly";
    boolean armed = false;
    boolean running = false;
    long activatePromptAt = 0L;
    long promptBrokeAt = 0L;
    float promptAlpha = 0.0f;
    long promptFadeLastAt = 0L;
    int promptFadeRgb = 0xFF5555;
    int[] hitboxLastPos = null;
    int hitboxLastFace = -1;
    int[] activationAnchorPos = null;
    int activationAnchorFace = -1;
    boolean activationMovementHeld = false;
    boolean eagleDisabledForActivation = false;
    boolean eagleWasDisabledByTelly = false;
    boolean antiSwayTapUsed = false;
    HashSet<String> cancelledGhostBlocks = new HashSet<String>();
    boolean tellyAutoPlaceWindow = false;
    boolean autoPlaceDebugActive = false;
    boolean safeWalkStateCaptured = false;
    boolean safeWalkWasEnabled = false;

    int setupTick = 0;
    int cyclePhase = 19;
    float stagedForward = -1.0f;
    float stagedStrafe = -1.0f;
    boolean stagedJump = false;
    boolean stagedSprint = false;
    float baseYaw = 0.0f;
    int travelX = 0;
    int travelZ = 0;
    double antiSwayLane = 0.0;
    float antiSwayYawOffset = 0.0f;
    int bridgeLaneBlock = 0;
    int bridgeStartProgress = 0;
    int[] latestStraightPlacedPos = null;
    boolean firstTellyPlacementPending = false;
    boolean adaptiveAimValid = false;
    float adaptiveAimYaw = 0.0f;
    float adaptiveAimPitch = 0.0f;
    long adaptiveAimUpdatedAt = 0L;
    long takeoverDetectionAt = 0L;
    boolean takeoverCameraValid = false;
    float takeoverCameraYaw = 0.0f;
    float takeoverCameraPitch = 0.0f;
    float takeoverAccumulated = 0.0f;
    long takeoverLastFrameAt = 0L;
    long freezeLastTickAt = 0L;
    boolean ignoreForwardUntilRelease = false;
    boolean ignoreBackUntilRelease = false;
    boolean ignoreLeftUntilRelease = false;
    boolean ignoreRightUntilRelease = false;
    boolean ignoreJumpUntilRelease = false;
    boolean ignoreSneakUntilRelease = false;
    boolean ignoreSprintUntilRelease = false;

    boolean rotationActive = false;
    long rotationStartedAt = 0L;
    long rotationDuration = 50L;
    float rotationStartYaw = 0.0f;
    float rotationStartPitch = 0.0f;
    float rotationTargetYaw = 0.0f;
    float rotationTargetPitch = 0.0f;
    float scriptedRotationYaw = 0.0f;
    float scriptedRotationPitch = 0.0f;
    int rotationStepCounter = 0;
    float[] yawCurve = new float[]{
            91.68f, 98.88f, 78.94f, 37.45f, 1.61f, -21.69f, -33.98f,
            -35.80f, -34.64f, -33.85f, -33.06f, -31.55f, -29.26f, -26.65f,
            -24.19f, -21.07f, -18.84f, -17.06f, -8.87f, 2.61f, 41.94f
    };
    float[] pitchCurve = new float[]{
            64.31f, 59.95f, 60.57f, 61.46f, 60.64f, 58.89f, 56.91f,
            56.63f, 58.65f, 61.63f, 64.20f, 66.74f, 68.69f, 70.64f,
            73.01f, 75.37f, 77.46f, 78.56f, 78.90f, 77.22f, 72.25f
    };
    float[] forwardCurve = new float[]{
            1.0f, 1.0f, 0.0f, 0.0f, -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f,
            -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, -1.0f, 1.0f
    };
    float[] strafeCurve = new float[]{
            -1.0f, -1.0f, -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 0.0f, -1.0f, -1.0f, -1.0f, -1.0f
    };
    int currentClientTick = -2147483648;
    int placementEvaluationTick = -2147483648;
    int lastPlacementAttemptTick = -2147483648;
    int lastSuccessfulPlaceTick = -2147483648;
    int forceSuppressTick = -2147483648;
    long totalC08Counter = 0L;
    long c08CounterAtTickBoundary = 0L;
    boolean hasLastSentServerPos = false;
    double lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ;
    Object[] cachedCandidate = null;
    int cachedCandidateTick = -2147483648;
    float cachedCandidateYaw = Float.NaN;
    float cachedCandidatePitch = Float.NaN;
    boolean candidateResolvedThisTick = false;
    int[] lastPlacedPos = null;
    int[] lastSupportPos = null;
    int lastSupportFace = -1;
    List<int[]> cachedBelowTargets = null;
    int cachedBelowTargetsTick = -2147483648;
    Map<String, Integer> rejectedTargets = new HashMap<>();
    int forcedModeCheck = 0;
    boolean useSuppressed = false;
    boolean silentPitchActive = false;
    float silentPitch = 0f;
    boolean placingViaModule = false;
    boolean manualC08InWindow = false;

    public LegitTelly() {
        super("LegitTelly", false);
    }

    private static EnumFacing enumFacing(String name, EnumFacing fallback) {
        if (name == null) return fallback;
        try {
            return EnumFacing.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String stripMinecraftPrefix(String registryName) {
        if (registryName == null) return "air";
        return registryName.startsWith("minecraft:") ? registryName.substring("minecraft:".length()) : registryName;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replace("-", "").replace("_", "").replace(" ", "");
    }

    @Override
    public void onEnabled() {
        onEnable();
    }

    @Override
    public void onDisabled() {
        onDisable();
    }

    @EventTarget(Priority.HIGHEST)
    public void handleUpdate(UpdateEvent event) {
        if (!this.isEnabled()) return;
        if (event.getType() == EventType.PRE) {
            // Raven/Flux cadence adapted to OpenMyau:
            // 1) PreUpdate/autoplace still sees the stable rotation from the previous tick.
            // 2) Then prepare this tick's movement phase and next rotation target.
            // 3) Submit that scripted yaw to OpenMyau so move-fix and packet rotation use the same frame.
            onPreUpdate();
            if (running) {
                advanceTellyCycle();
                PlayerState state = new PlayerState(event);
                onPreMotion(state);
                event.setRotation(state.yaw, state.pitch, 5);
                event.setPervRotation(state.yaw, 5);
            }
        } else if (event.getType() == EventType.POST) {
            onPostMotion();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void handleMoveInput(MoveInputEvent event) {
        if (this.isEnabled()) applyTellyMovementInput();
    }

    @EventTarget(Priority.HIGHEST)
    public void handleLivingUpdate(LivingUpdateEvent event) {
        if (!this.isEnabled()) return;
        if (running) {
            setSneak(false);
            enforceSafeWalkDisabledForRun();
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void handleRender2D(Render2DEvent event) {
        if (this.isEnabled()) onRenderTick(event.partialTicks());
    }

    @EventTarget(Priority.HIGHEST)
    public void handleRender3D(Render3DEvent event) {
        if (this.isEnabled()) onRenderWorld(event.partialTicks());
    }

    @EventTarget(Priority.HIGHEST)
    public void handlePacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) return;
        if (event.getType() == EventType.SEND) {
            CPacket packet = CPacket.from(event.getPacket());
            if (packet != null && !onPacketSent(packet)) event.setCancelled(true);
        } else if (event.getType() == EventType.RECEIVE) {
            SPacket packet = SPacket.from(event.getPacket());
            if (packet != null && !onPacketReceived(packet)) event.setCancelled(true);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void handleWorldLoad(LoadWorldEvent event) {
        if (!this.isEnabled()) return;
        stopAutomation(false);
        autoPlaceOnWorldJoin(getPlayer());
    }

    @EventTarget(Priority.HIGHEST)
    public void handleLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled() && !onMouse(0, true, 0, 0)) event.setCancelled(true);
    }

    @EventTarget(Priority.HIGHEST)
    public void handleHitBlock(HitBlockEvent event) {
        if (this.isEnabled() && !onMouse(0, true, 0, 0)) event.setCancelled(true);
    }

    @EventTarget(Priority.HIGHEST)
    public void handleSafeWalk(SafeWalkEvent event) {
        if (this.isEnabled() && running && this.disableSafeWalk.getValue()) event.setSafeWalk(false);
    }

    void onEnable() {
        autoPlaceOnEnable();
        armAutomation();
    }

    void onDisable() {
        stopAutomation(false);
        autoPlaceOnDisable();
    }

    void onWorldJoin(Entity entity) {
        if (entity != null && entity.isUser) stopAutomation(false);
        autoPlaceOnWorldJoin(entity);
    }

    void onPreUpdate() {
        enforceSafeWalkDisabledForRun();
        if (running) {
            setKeyPressed("attack", false);
            applySmoothedRotation();
            holdScriptedRotation();
        }

        if (armed && !running) updateActivationPrompt();

        if (!running) return;

        long freezeNow = now();
        if (freezeLastTickAt != 0L && freezeNow - freezeLastTickAt > 300L) {
            stopAutomation(true);
            return;
        }
        freezeLastTickAt = freezeNow;

        Entity player = getPlayer();
        if (player == null || player.isDead() || player.getFallDistance() > 7.0f) {
            stopAutomation(true);
            return;
        }
        handleAutoSwap(player);
        if (!player.isHoldingBlock()) {
            stopAutomation(true);
            return;
        }
        updateAdaptivePlacementAimIfNeeded(player);

        autoPlaceOnPreUpdate();
        updateAdaptivePlacementAimIfNeeded(player);
    }

    float activationPitch() {
        return 75.0f;
    }

    void handleAutoSwap(Entity player) {
        if (!autoSwap.getValue()) return;

        int threshold = 5;
        ItemStack held = player.getHeldItem();
        int heldCount = held != null && isUsableBlockStack(held) ? held.stackSize : 0;
        if (heldCount > threshold) return;

        int currentSlot = mc.thePlayer.inventory.currentItem;
        int bestSlot = -1;
        int bestSize = heldCount;
        for (int slot = 0; slot <= 8; slot++) {
            if (slot == currentSlot) continue;
            ItemStack stack = getWrappedStack(slot);
            if (!isUsableBlockStack(stack)) continue;
            if (stack.stackSize > bestSize) {
                bestSize = stack.stackSize;
                bestSlot = slot;
            }
        }

        if (bestSlot != -1) mc.thePlayer.inventory.currentItem = bestSlot;
    }

    boolean activationPromptReady() {
        return activatePromptAt != 0L && activationPromptElapsed() >= 1000L;
    }

    boolean activationSuppressUse() {
        return activatePromptAt != 0L && activationPromptElapsed() >= 850L;
    }

    void updateActivationPrompt() {
        Entity player = getPlayer();
        if (player == null || !(mc.currentScreen == null)) {
            clearActivationPrompt();
            return;
        }

        boolean promptReady = activationPromptReady();
        setActivationMovementHold(promptReady && Mouse.isButtonDown(1));

        boolean lookingDown = player.getPitch() >= activationPitch();
        boolean atEdge = lookingDown && isLookingAtEdge(player);

        if (isSneaking() && atEdge) {
            if (activatePromptAt == 0L) activatePromptAt = now();
            promptBrokeAt = 0L;
            captureActivationAnchor(player);
            if (activationSuppressUse()) setKeyPressed("use", false);
            if (promptReady) {
                disableEagleForActivation();
            }
            if (promptReady && Mouse.isButtonDown(1)) {
                disableSafeWalkForRun();
                enforceSafeWalkDisabledForRun();
            } else if (safeWalkStateCaptured) {
                restoreSafeWalkState();
            }
            return;
        }

        if (activatePromptAt == 0L) return;

        if (!promptReady) {
            clearActivationPrompt();
            return;
        }

        if (promptBrokeAt == 0L) {
            rememberActivationPromptColor();
            promptBrokeAt = now();
        }
        setKeyPressed("use", false);

        if (!isSneaking() && Mouse.isButtonDown(1) && isActivationYawAligned(player.getYaw())) {
            rememberActivationPromptColor();
            activatePromptAt = 0L;
            promptBrokeAt = 0L;
            beginAutomation();
            if (!running) setKeyPressed("use", false);
            return;
        }

        if (now() - promptBrokeAt > 300L) {
            clearActivationPrompt();
        }
    }

    void clearActivationPrompt() {
        rememberActivationPromptColor();
        if (activationSuppressUse()) {
            setKeyPressed("use", false);
        }
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        activationAnchorPos = null;
        activationAnchorFace = -1;
        eagleDisabledForActivation = false;
        setActivationMovementHold(false);
        if (!running) restoreSafeWalkState();
    }

    void rememberActivationPromptColor() {
        if (activatePromptAt == 0L) return;
        promptFadeRgb = activationPromptReady() ? 0x55FF55 : 0xFF5555;
    }

    int[] travelDirectionFromYaw(float yaw) {
        double radians = Math.toRadians(yaw);
        double rawX = Math.sin(radians) - Math.cos(radians);
        double rawZ = -Math.cos(radians) - Math.sin(radians);
        if (Math.abs(rawX) >= Math.abs(rawZ)) return new int[]{rawX >= 0.0 ? 1 : -1, 0};
        return new int[]{0, rawZ >= 0.0 ? 1 : -1};
    }

    boolean isLookingAtEdge(Entity player) {
        if (!isActivationYawAligned(player.getYaw())) return false;
        Object[] hit = raycastBlock(4.5);
        if (hit == null || hit.length < 3 || hit[0] == null || hit[1] == null || hit[2] == null) return false;

        int face = faceFromName((String) hit[2]);
        if (face < 2) return false;
        if (!isInActivationFaceCenter(face, (Vec3) hit[1])) return false;

        int[] travel = travelDirectionFromYaw(player.getYaw());
        int travelFace = travel[0] > 0 ? 5 : travel[0] < 0 ? 4 : travel[1] > 0 ? 3 : 2;
        if (face != travelFace) return false;

        int[] pos = posFromVec((Vec3) hit[0]);
        if (!isPlayerOnActivationBlock(player, pos)) return false;
        int aheadX = pos[0] + travel[0];
        int aheadZ = pos[2] + travel[1];
        if (!isReplaceableName(blockNameAt(aheadX, pos[1] + 1, aheadZ), false)) return false;

        Vec3 playerPos = player.getPosition();
        double lipDistance;
        if (face == 5) lipDistance = (pos[0] + 1) - playerPos.x;
        else if (face == 4) lipDistance = playerPos.x - pos[0];
        else if (face == 3) lipDistance = (pos[2] + 1) - playerPos.z;
        else lipDistance = playerPos.z - pos[2];
        if (lipDistance > 0.65) return false;

        hitboxLastPos = new int[]{pos[0], pos[1], pos[2]};
        hitboxLastFace = face;
        return true;
    }

    void captureActivationAnchor(Entity player) {
        if (player == null) return;
        Object[] hit = raycastBlock(4.5);
        if (hit == null || hit.length < 3 || hit[0] == null || hit[2] == null) {
            if (hitboxLastPos != null && hitboxLastFace >= 2) {
                activationAnchorPos = new int[]{hitboxLastPos[0], hitboxLastPos[1], hitboxLastPos[2]};
                activationAnchorFace = hitboxLastFace;
            }
            return;
        }
        int face = faceFromName((String) hit[2]);
        if (face < 2) return;
        int[] pos = posFromVec((Vec3) hit[0]);
        if (!isPlayerOnActivationBlock(player, pos)) return;
        if (!isInActivationFaceCenter(face, (Vec3) hit[1])) return;
        int[] travel = travelDirectionFromYaw(player.getYaw());
        int travelFace = travel[0] > 0 ? 5 : travel[0] < 0 ? 4 : travel[1] > 0 ? 3 : 2;
        if (face != travelFace) return;
        activationAnchorPos = new int[]{pos[0], pos[1], pos[2]};
        activationAnchorFace = face;
        hitboxLastPos = new int[]{pos[0], pos[1], pos[2]};
        hitboxLastFace = face;
    }

    boolean isActivationYawAligned(float yaw) {
        float nearestDiagonal = Math.round((yaw - 45.0f) / 90.0f) * 90.0f + 45.0f;
        return Math.abs(tellyWrapAngle(yaw - nearestDiagonal)) <= ACTIVATION_YAW_TOLERANCE;
    }

    boolean isPlayerOnActivationBlock(Entity player, int[] pos) {
        if (pos == null) return false;
        Vec3 playerPos = player.getPosition();
        if (pos[1] != floor(playerPos.y - 0.01)) return false;
        double centerX = pos[0] + 0.5;
        double centerZ = pos[2] + 0.5;
        return Math.abs(playerPos.x - centerX) <= 0.85
                && Math.abs(playerPos.z - centerZ) <= 0.85;
    }

    // Limit arming to the lower portion of the outward face and exclude its deep end.
    boolean isInActivationFaceCenter(int face, Vec3 localHit) {
        if (localHit == null) return false;
        double acrossFace = (face == 4 || face == 5) ? localHit.z : localHit.x;
        if (face == 3 || face == 4) acrossFace = 1.0 - acrossFace;
        return acrossFace >= ACTIVATION_ACROSS_MIN && acrossFace <= ACTIVATION_ACROSS_MAX
                && localHit.y >= ACTIVATION_HEIGHT_MIN && localHit.y <= ACTIVATION_HEIGHT_MAX;
    }

    void onRenderWorld(float partialTicks) {
        if (!showActivationHitbox.getValue()) return;
        if (!armed || running) return;
        if (promptAlpha < 0.05f) return;

        if (activatePromptAt != 0L) {
            Object[] hit = raycastBlock(4.5);
            if (hit != null && hit.length >= 3 && hit[0] != null && hit[2] != null) {
                int face = faceFromName((String) hit[2]);
                if (face >= 2) {
                    hitboxLastPos = posFromVec((Vec3) hit[0]);
                    hitboxLastFace = face;
                }
            }
        }

        if (hitboxLastPos == null || hitboxLastFace < 2) return;
        drawActivationFaceRegion(hitboxLastPos, hitboxLastFace);
    }

    void drawActivationFaceRegion(int[] pos, int face) {
        Vec3 cam = getRenderPosition();
        if (cam == null) return;

        double yMin = pos[1] + ACTIVATION_HEIGHT_MIN;
        double yMax = pos[1] + ACTIVATION_HEIGHT_MAX;
        double x1, z1, x2, z2;

        if (face == 5) {
            x1 = pos[0] + 1.005;
            x2 = x1;
            z1 = pos[2] + ACTIVATION_ACROSS_MIN;
            z2 = pos[2] + ACTIVATION_ACROSS_MAX;
        } else if (face == 4) {
            x1 = pos[0] - 0.005;
            x2 = x1;
            z1 = pos[2] + (1.0 - ACTIVATION_ACROSS_MAX);
            z2 = pos[2] + (1.0 - ACTIVATION_ACROSS_MIN);
        } else if (face == 3) {
            z1 = pos[2] + 1.005;
            z2 = z1;
            x1 = pos[0] + (1.0 - ACTIVATION_ACROSS_MAX);
            x2 = pos[0] + (1.0 - ACTIVATION_ACROSS_MIN);
        } else {
            z1 = pos[2] - 0.005;
            z2 = z1;
            x1 = pos[0] + ACTIVATION_ACROSS_MIN;
            x2 = pos[0] + ACTIVATION_ACROSS_MAX;
        }

        int r = (promptFadeRgb >> 16) & 0xFF;
        int g = (promptFadeRgb >> 8) & 0xFF;
        int b = promptFadeRgb & 0xFF;
        int fillAlpha = (int) (60.0f * promptAlpha);
        int lineAlpha = (int) (220.0f * promptAlpha);
        if (fillAlpha < 4) fillAlpha = 4;
        if (lineAlpha < 16) lineAlpha = 16;

        GL11.glPushMatrix();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.disableCull();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GL11.glTranslated(-cam.x, -cam.y, -cam.z);

        GlStateManager.color(r / 255.0f, g / 255.0f, b / 255.0f, fillAlpha / 255.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex3d(x1, yMin, z1);
        GL11.glVertex3d(x2, yMin, z2);
        GL11.glVertex3d(x2, yMax, z2);
        GL11.glVertex3d(x1, yMax, z1);
        GL11.glEnd();

        GL11.glLineWidth(2.0f);
        GlStateManager.color(r / 255.0f, g / 255.0f, b / 255.0f, lineAlpha / 255.0f);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(x1, yMin, z1);
        GL11.glVertex3d(x2, yMin, z2);
        GL11.glVertex3d(x2, yMax, z2);
        GL11.glVertex3d(x1, yMax, z1);
        GL11.glEnd();
        GL11.glLineWidth(1.0f);

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glPopMatrix();
    }

    void drawActivatePrompt() {
        if (promptAlpha < 0.05f) return;

        int[] display = getDisplaySize();
        if (display == null || display.length < 2) return;

        String text = "Activate?";
        int alpha = (int) (promptAlpha * 255.0f);
        if (alpha < 16) alpha = 16;
        int color = (alpha << 24) | promptFadeRgb;
        float x = display[0] / 2.0f - getFontWidth(text) / 2.0f;
        float y = display[1] / 2.0f + 10.0f;
        drawText(text, x, y, 1.0f, color, true);
    }

    void updateActivatePromptFade() {
        boolean show = armed && !running && activatePromptAt != 0L;
        if (show) rememberActivationPromptColor();

        long now = now();
        long elapsed = promptFadeLastAt == 0L ? 0L : Math.min(100L, now - promptFadeLastAt);
        promptFadeLastAt = now;
        float step = elapsed / 200.0f;
        promptAlpha += show ? step : -step;
        if (promptAlpha < 0.0f) promptAlpha = 0.0f;
        if (promptAlpha > 1.0f) promptAlpha = 1.0f;
    }

    boolean onMouse(int button, boolean state, int mouseX, int mouseY) {
        if (running) {
            if (button == 0) {
                setKeyPressed("attack", false);
                return false;
            }
            if (button == 1) {
                setKeyPressed("use", tellyAutoPlaceWindow);
                return false;
            }
            return autoPlaceOnMouse(button, state);
        }
        if (armed && button == 1 && !state) setActivationMovementHold(false);
        return !armed || !activationSuppressUse() || button != 1;
    }

    boolean onKey(String keyName, int keyCode, boolean state, boolean inGui) {
        boolean dropKey = keyCode == getKeyCode("drop")
                || (keyName != null && keyName.toLowerCase().contains("drop"));
        if (isDropProtected() && dropKey) {
            setKeyPressed("drop", false);
            return false;
        }
        if (!running && activationMovementHeld && !state
                && (keyCode == getKeyCode("back")
                || keyCode == getKeyCode("right"))) {
            setKeyPressed("back", true);
            setKeyPressed("right", true);
            return false;
        }
        if (!running) return true;
        if (keyCode == getKeyCode("sneak")) {
            suppressSneakInput();
            return false;
        }
        if (!state) clearInitialMovementHold(keyCode);
        if (state
                && setupTick < 0
                && isManualMovementKey(keyCode)
                && !isInitialMovementHold(keyCode)
                && !isScriptHeldKey(keyCode)) {
            stopAutomation(true);
            return true;
        }
        if (inGui) return true;
        return !isManualMovementKey(keyCode);
    }

    void setActivationMovementHold(boolean hold) {
        if (activationMovementHeld == hold) return;
        activationMovementHeld = hold;
        if (hold) {
            setKeyPressed("back", true);
            setKeyPressed("right", true);
        } else {
            setKeyPressed("back", isKeyDown(getKeyCode("back")));
            setKeyPressed("right", isKeyDown(getKeyCode("right")));
        }
    }

    boolean isScriptHeldKey(int keyCode) {
        if (keyCode == getKeyCode("forward")) return isKeyPressed("forward");
        if (keyCode == getKeyCode("back")) return isKeyPressed("back");
        if (keyCode == getKeyCode("left")) return isKeyPressed("left");
        if (keyCode == getKeyCode("right")) return isKeyPressed("right");
        if (keyCode == getKeyCode("jump")) return isKeyPressed("jump");
        if (keyCode == getKeyCode("sprint")) return isKeyPressed("sprint");
        return false;
    }

    boolean isManualMovementKey(int keyCode) {
        return keyCode == getKeyCode("forward")
                || keyCode == getKeyCode("back")
                || keyCode == getKeyCode("left")
                || keyCode == getKeyCode("right")
                || keyCode == getKeyCode("jump")
                || keyCode == getKeyCode("sneak")
                || keyCode == getKeyCode("sprint");
    }

    void captureInitialMovementHolds() {
        ignoreForwardUntilRelease = isKeyDown(getKeyCode("forward"));
        ignoreBackUntilRelease = isKeyDown(getKeyCode("back"));
        ignoreLeftUntilRelease = isKeyDown(getKeyCode("left"));
        ignoreRightUntilRelease = isKeyDown(getKeyCode("right"));
        ignoreJumpUntilRelease = isKeyDown(getKeyCode("jump"));
        ignoreSneakUntilRelease = isKeyDown(getKeyCode("sneak"));
        ignoreSprintUntilRelease = isKeyDown(getKeyCode("sprint"));
    }

    boolean isInitialMovementHold(int keyCode) {
        if (keyCode == getKeyCode("forward")) return ignoreForwardUntilRelease;
        if (keyCode == getKeyCode("back")) return ignoreBackUntilRelease;
        if (keyCode == getKeyCode("left")) return ignoreLeftUntilRelease;
        if (keyCode == getKeyCode("right")) return ignoreRightUntilRelease;
        if (keyCode == getKeyCode("jump")) return ignoreJumpUntilRelease;
        if (keyCode == getKeyCode("sneak")) return ignoreSneakUntilRelease;
        if (keyCode == getKeyCode("sprint")) return ignoreSprintUntilRelease;
        return false;
    }

    void clearInitialMovementHold(int keyCode) {
        if (keyCode == getKeyCode("forward")) ignoreForwardUntilRelease = false;
        if (keyCode == getKeyCode("back")) ignoreBackUntilRelease = false;
        if (keyCode == getKeyCode("left")) ignoreLeftUntilRelease = false;
        if (keyCode == getKeyCode("right")) ignoreRightUntilRelease = false;
        if (keyCode == getKeyCode("jump")) ignoreJumpUntilRelease = false;
        if (keyCode == getKeyCode("sneak")) ignoreSneakUntilRelease = false;
        if (keyCode == getKeyCode("sprint")) ignoreSprintUntilRelease = false;
    }

    void clearInitialMovementHolds() {
        ignoreForwardUntilRelease = false;
        ignoreBackUntilRelease = false;
        ignoreLeftUntilRelease = false;
        ignoreRightUntilRelease = false;
        ignoreJumpUntilRelease = false;
        ignoreSneakUntilRelease = false;
        ignoreSprintUntilRelease = false;
    }

    boolean detectManualCameraTakeover() {
        if (!running || setupTick >= 0 || now() < takeoverDetectionAt) return false;
        Entity player = getPlayer();
        if (player == null) return false;

        long now = now();
        float expectedYaw = scriptedRotationYaw;
        float expectedPitch = scriptedRotationPitch;
        if (!takeoverCameraValid) {
            takeoverCameraValid = true;
            takeoverCameraYaw = player.getYaw();
            takeoverCameraPitch = player.getPitch();
            takeoverAccumulated = 0.0f;
            takeoverLastFrameAt = now;
            return false;
        }

        double yawInput = Math.abs(tellyWrapAngle(player.getYaw() - expectedYaw));
        double pitchInput = Math.abs(player.getPitch() - expectedPitch);
        double noiseFloor = rotationGcd() * 0.45;

        long elapsed = Math.max(0L, now - takeoverLastFrameAt);
        takeoverLastFrameAt = now;
        takeoverAccumulated -= (float) (elapsed * 0.045);
        if (takeoverAccumulated < 0.0f) takeoverAccumulated = 0.0f;
        if (yawInput > noiseFloor || pitchInput > noiseFloor) {
            takeoverAccumulated += (float) (yawInput + pitchInput);
        }

        takeoverCameraYaw = player.getYaw();
        takeoverCameraPitch = player.getPitch();

        if (takeoverAccumulated >= 25.0f) {
            stopAutomation(true);
            return true;
        }
        return false;
    }

    boolean onPacketSent(CPacket packet) {
        if (isDropProtected() && packet instanceof C07 digging) {
            String status = digging.status == null ? "" : digging.status.toUpperCase();
            if (status.contains("DROP")) return false;
        }
        if (!running) return true;
        if (packet instanceof C02 interaction) {
            if ("ATTACK".equals(interaction.action)) return false;
        }
        if (packet instanceof C07 digging) {
            String status = digging.status == null ? "" : digging.status.toUpperCase();
            if (status.contains("DESTROY")) return false;
        }
        if (packet instanceof C0B action) {
            if ("START_SNEAKING".equals(action.action)) return false;
        }
        int[] placedTarget = null;
        if (packet instanceof C08 placement) {
            if (placement.direction == 255) {
                if (isAutoPlaceActiveWindow(getPlayer()) || shouldCancelAutoPlaceUseItem()) {
                    suppressUse();
                    return false;
                }
            }
            if (placement.direction != 255 && placement.position != null) {
                int[] supportPos = posFromVec(placement.position);
                placedTarget = offsetPos(supportPos, placement.direction);
                if (!isStraightTellyTarget(placedTarget)) {
                    cancelledGhostBlocks.add(posKey(placedTarget));
                    return false;
                }
                if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])
                        || isReplaceable(supportPos[0], supportPos[1], supportPos[2])
                        || !isReplaceable(placedTarget[0], placedTarget[1], placedTarget[2])) {
                    cancelledGhostBlocks.add(posKey(placedTarget));
                    return false;
                }
                if (!placingViaModule && isAutoPlaceActiveWindow(getPlayer())) {
                    suppressUse();
                    return false;
                }
            }
        }

        boolean allowed = autoPlaceOnPacketSent(packet);
        if (allowed && placedTarget != null) {
            cancelledGhostBlocks.remove(posKey(placedTarget));
            latestStraightPlacedPos = new int[]{placedTarget[0], placedTarget[1], placedTarget[2]};
            if (firstTellyPlacementPending && setupTick < 0) {
                firstTellyPlacementPending = false;
                adaptiveAimValid = false;
                adaptiveAimUpdatedAt = 0L;
            }
        }
        return allowed;
    }

    boolean isActivationInProgress() {
        return armed && !running && activatePromptAt != 0L;
    }

    boolean isDropProtected() {
        return running || isActivationInProgress();
    }

    void onPostPlayerInput() {
        // Compatibility entry point: run a full advance and apply input.
        advanceTellyCycle();
        applyTellyMovementInput();
    }

    void advanceTellyCycle() {
        if (!running) return;
        suppressSneakInput();
        enforceSafeWalkDisabledForRun();

        if (setupTick >= 0) {
            if (setupTick < 12) {
                boolean setupJump = setupTick >= 6;
                stagedForward = -1.0f;
                stagedStrafe = -1.0f;
                stagedJump = setupJump;
                stagedSprint = false;
                applyUse(true);

                if (setupTick == 11) {
                    setRotationTarget(baseYaw + yawCurve[19], pitchCurve[19], 50L);
                } else {
                    setRotationTarget(baseYaw, 74.52f, 50L);
                }
                setupTick++;
                return;
            }

            setupTick = -1;
            takeoverDetectionAt = now() + 125L;
            Entity takeoverPlayer = getPlayer();
            takeoverCameraValid = takeoverPlayer != null;
            takeoverAccumulated = 0.0f;
            takeoverLastFrameAt = now();
            if (takeoverPlayer != null) {
                takeoverCameraYaw = takeoverPlayer.getYaw();
                takeoverCameraPitch = takeoverPlayer.getPitch();
            }
            captureInitialMovementHolds();
            cyclePhase = 19;
            firstTellyPlacementPending = true;
            adaptiveAimValid = false;
            clearCachedCandidate();
            updateAdaptivePlacementAim(getPlayer());
        }

        int phase = cyclePhase;
        float strafe = strafeCurve[phase];
        boolean sprinting = phase == 0 || phase == 1;
        boolean jumping = phase >= 1 && phase <= 19;
        boolean use = phase >= 7;

        stagedForward = forwardCurve[phase];
        stagedStrafe = strafe;
        stagedJump = jumping;
        stagedSprint = sprinting;
        applyUse(use);

        int nextPhase = (phase + 1) % yawCurve.length;
        setRotationTarget(baseYaw + yawCurve[nextPhase], pitchCurve[nextPhase], 50L);
        cyclePhase = nextPhase;
    }

    void applyTellyMovementInput() {
        if (!running) return;
        suppressSneakInput();
        enforceSafeWalkDisabledForRun();
        holdScriptedRotation();
        applyMovement(stagedForward, stagedStrafe, stagedJump, stagedSprint);
    }

    void onPreMotion(PlayerState state) {
        if (!running || state == null) return;
        Entity player = getPlayer();
        if (player == null) return;
        holdScriptedRotation();
        state.yaw = scriptedRotationYaw;
        state.pitch = scriptedRotationPitch;
        autoPlaceOnPreMotion(state);
    }

    void onRenderTick(float partialTicks) {
        updateActivatePromptFade();
        drawActivatePrompt();
        if (!running) return;
        if (detectManualCameraTakeover()) return;
        applySmoothedRotation();
        // Render2DEvent is fired per frame; keep the expensive search in PRE update.
        // Searching every frame can repeat large raycasts at high FPS.
        // The actual placement logic already runs once per tick.
        // processAutoPlaceTick() handles it there.
    }

    void onPostMotion() {
        if (!running) return;
        autoPlaceOnPostMotion();
    }

    boolean onPacketReceived(SPacket packet) {
        if (running && packet != null && "S08PacketPlayerPosLook".equals(packet.name)) {
            stopAutomation(true);
            return true;
        }
        if (packet instanceof S23 change && !cancelledGhostBlocks.isEmpty()) {
            if (change.position != null) {
                cancelledGhostBlocks.remove(posKey(posFromVec(change.position)));
            }
        }
        return true;
    }

    void armAutomation() {
        armed = true;
        running = false;
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        setupTick = 0;
        cyclePhase = 19;
        rotationActive = false;
        activationMovementHeld = false;
        eagleDisabledForActivation = false;
        eagleWasDisabledByTelly = false;
        printStatus("&eArmed. Sneak looking down, wait for green, hold rmb and release sneak");
    }

    void disableEagleForActivation() {
        if (eagleDisabledForActivation) return;
        eagleDisabledForActivation = true;
        try {
            if (Unfair.moduleManager == null) return;
            Eagle eagle = (Eagle) Unfair.moduleManager.getModule("Eagle");
            if (eagle != null && eagle.isEnabled()) {
                eagle.toggle();
                eagleWasDisabledByTelly = true;
            }
        } catch (Throwable ignored) {
        }
    }

    void restoreEagleAfterTelly() {
        try {
            if (Unfair.moduleManager == null) return;
            Eagle eagle = (Eagle) Unfair.moduleManager.getModule("Eagle");
            if (eagle != null && !eagle.isEnabled()) {
                eagle.toggle();
            }
        } catch (Throwable ignored) {
        }
    }

    void beginAutomation() {
        Entity player = getPlayer();
        if (player == null || !player.isHoldingBlock()) {
            printStatus("&cHold blocks before starting");
            return;
        }
        if (!isActivationYawAligned(player.getYaw())) return;

        disableSafeWalkForRun();
        // Snap start yaw to the nearest diagonal to avoid drifting the bridge line.
        baseYaw = Math.round((player.getYaw() - 45.0f) / 90.0f) * 90.0f + 45.0f;
        calculateTravelDirection(baseYaw);
        antiSwayLane = travelX != 0 ? player.getPosition().z : player.getPosition().x;
        antiSwayYawOffset = 0.0f;
        antiSwayTapUsed = false;
        cancelledGhostBlocks.clear();
        if (activationAnchorPos == null) captureActivationAnchor(player);
        initializeStraightBridgeLane(player);
        firstTellyPlacementPending = false;
        adaptiveAimValid = false;
        adaptiveAimUpdatedAt = 0L;
        setupTick = 0;
        cyclePhase = 19;
        stagedForward = -1.0f;
        stagedStrafe = -1.0f;
        stagedJump = false;
        stagedSprint = false;
        armed = false;
        running = true;
        freezeLastTickAt = now();
        activationMovementHeld = false;
        tellyAutoPlaceWindow = true;
        scriptedRotationYaw = baseYaw;
        scriptedRotationPitch = 74.52f;
        rotationStartYaw = baseYaw;
        rotationStartPitch = 74.52f;
        rotationTargetYaw = baseYaw;
        rotationTargetPitch = 74.52f;
        rotationActive = false;
        player.setYaw(baseYaw);
        player.setPitch(74.52f);
        takeoverDetectionAt = 0L;
        takeoverCameraValid = false;
        clearInitialMovementHolds();
        resetControllerState();
        setKeyPressed("attack", false);
        applyMovement(-1.0f, -1.0f, false, false);
        setRotationTarget(baseYaw, 74.52f, 50L);
        applySmoothedRotation();
        applyUse(true);
        printStatus("&aStarted");

    }

    void stopAutomation(boolean turnOffButton) {
        boolean restoreEagleAfterStop = eagleWasDisabledByTelly;
        armed = false;
        running = false;
        setupTick = 0;
        cyclePhase = 19;
        rotationActive = false;
        activationMovementHeld = false;
        eagleDisabledForActivation = false;
        eagleWasDisabledByTelly = false;
        tellyAutoPlaceWindow = false;
        autoPlaceDebugActive = false;
        antiSwayYawOffset = 0.0f;
        antiSwayTapUsed = false;
        firstTellyPlacementPending = false;
        latestStraightPlacedPos = null;
        activationAnchorPos = null;
        activationAnchorFace = -1;
        stagedForward = 0.0f;
        stagedStrafe = 0.0f;
        stagedJump = false;
        stagedSprint = false;
        adaptiveAimValid = false;
        adaptiveAimUpdatedAt = 0L;
        scriptedRotationYaw = 0.0f;
        scriptedRotationPitch = 0.0f;
        takeoverDetectionAt = 0L;
        takeoverCameraValid = false;
        takeoverCameraYaw = 0.0f;
        takeoverCameraPitch = 0.0f;
        takeoverAccumulated = 0.0f;
        takeoverLastFrameAt = 0L;

        try {
            cancelledGhostBlocks.clear();
            clearInitialMovementHolds();
            resetControllerState();
            setForward(0.0f);
            setStrafe(0.0f);
            setJump(false);
            setSprinting(false);
            releaseMovementKeys();
            restorePhysicalUse();
            setKeyPressed("attack", Mouse.isButtonDown(0));
        } catch (Exception ignored) {
        }

        restoreSafeWalkState();

        freezeLastTickAt = 0L;
        armed = true;
        activatePromptAt = 0L;
        promptBrokeAt = 0L;
        if (restoreEagleAfterStop) {
            restoreEagleAfterTelly();
        }
        if (turnOffButton) {
            printStatus("&eStopped. Sneak looking down to arm again");
        }
    }

    void disableSafeWalkForRun() {
        if (safeWalkStateCaptured) {
            enforceSafeWalkDisabledForRun();
            return;
        }
        if (!disableSafeWalk.getValue()) return;

        try {
            safeWalkWasEnabled = isModuleEnabled("SafeWalk");
            safeWalkStateCaptured = true;
            if (safeWalkWasEnabled) disableModule("SafeWalk");
        } catch (Exception ignored) {
            safeWalkStateCaptured = false;
        }
    }

    void enforceSafeWalkDisabledForRun() {
        if (!safeWalkStateCaptured) return;
        try {
            if (isModuleEnabled("SafeWalk")) disableModule("SafeWalk");
        } catch (Exception ignored) {
        }
    }

    void restoreSafeWalkState() {
        if (!safeWalkStateCaptured) return;

        boolean restoreEnabled = safeWalkWasEnabled;
        safeWalkStateCaptured = false;
        try {
            boolean currentlyEnabled = isModuleEnabled("SafeWalk");
            if (restoreEnabled && !currentlyEnabled) enableModule("SafeWalk");
            if (!restoreEnabled && currentlyEnabled) disableModule("SafeWalk");
        } catch (Exception ignored) {
        }
    }

    long activationPromptElapsed() {
        return now() - activatePromptAt;
    }

    void updateAdaptivePlacementAimIfNeeded(Entity player) {
        if (firstTellyPlacementPending) updateAdaptivePlacementAim(player);
    }

    void printStatus(String message) {
        try {
            if (print.getValue()) {
                ChatUtil.sendRaw(ChatColors.formatColor("&bTelly &7| " + message));
            }
        } catch (Exception ignored) {
        }
    }

    void setRotationTarget(float targetYaw, float targetPitch, long duration) {
        Entity player = getPlayer();
        if (player == null) return;

        applySmoothedRotation();
        // Silent rotation restores entity yaw to camera yaw; interpolation must use script yaw while running.
        if (running) {
            rotationStartYaw = scriptedRotationYaw;
            rotationStartPitch = scriptedRotationPitch;
        } else {
            rotationStartYaw = player.getYaw();
            rotationStartPitch = player.getPitch();
        }
        float correctedTargetYaw = targetYaw;
        boolean adaptivePlacementTarget = running
                && tellyAutoPlaceWindow
                && firstTellyPlacementPending
                && adaptiveAimValid
                && now() - adaptiveAimUpdatedAt <= 125L;
        if (adaptivePlacementTarget) {
            correctedTargetYaw = adaptiveAimYaw;
            targetPitch = adaptiveAimPitch;
        } else if (running) {
            correctedTargetYaw += antiSwayYawOffset;
        }

        rotationStepCounter++;
        correctedTargetYaw += rotationGcd() * YAW_NUDGE_PATTERN[rotationStepCounter % 5];

        rotationTargetYaw = rotationStartYaw + tellyWrapAngle(correctedTargetYaw - rotationStartYaw);
        rotationTargetPitch = clamp(targetPitch, -90.0f, 90.0f);
        rotationStartedAt = now();
        rotationDuration = Math.max(1L, duration);
        rotationActive = true;
    }

    void applySmoothedRotation() {
        if (!rotationActive) {
            if (running) holdScriptedRotation();
            return;
        }
        Entity player = getPlayer();
        if (player == null) return;

        double progress = (double) (now() - rotationStartedAt) / (double) rotationDuration;
        if (progress < 0.0) progress = 0.0;
        if (progress > 1.0) progress = 1.0;

        float desiredYaw = rotationStartYaw + (rotationTargetYaw - rotationStartYaw) * (float) progress;
        float desiredPitch = rotationStartPitch + (rotationTargetPitch - rotationStartPitch) * (float) progress;
        float quantizedYaw = quantizeFrom(rotationStartYaw, desiredYaw);
        float quantizedPitch = quantizeFrom(rotationStartPitch, desiredPitch);

        scriptedRotationYaw = quantizedYaw;
        scriptedRotationPitch = clamp(quantizedPitch, -90.0f, 90.0f);
        holdScriptedRotation();
        if (progress >= 1.0) rotationActive = false;
    }

    void holdScriptedRotation() {
        Entity player = getPlayer();
        if (player == null) return;
        player.setYaw(scriptedRotationYaw);
        player.setPitch(scriptedRotationPitch);
    }

    float quantizeFrom(float origin, float value) {
        float gcd = rotationGcd();
        float delta = value - origin;
        delta -= delta % gcd;
        return origin + delta;
    }

    float rotationGcd() {
        if (mc == null || mc.gameSettings == null) return 0.03404715f;
        float f = mc.gameSettings.mouseSensitivity * 0.6F + 0.2F;
        return f * f * f * 8.0F * 0.15F;
    }

    float[] applyGCD(float[] rotations, float[] prevRotations) {
        if (rotations == null || prevRotations == null || rotations.length < 2 || prevRotations.length < 2)
            return rotations;
        float gcd = rotationGcd();
        float deltaYaw = rotations[0] - prevRotations[0];
        float deltaPitch = rotations[1] - prevRotations[1];
        deltaYaw -= deltaYaw % gcd;
        deltaPitch -= deltaPitch % gcd;
        return new float[]{prevRotations[0] + deltaYaw, prevRotations[1] + deltaPitch};
    }

    void applyMovement(float forward, float strafe, boolean jumping, boolean sprinting) {
        float controlledForward = forward;
        boolean controlledSprint = sprinting;

        float correctedStrafe = strafe;
        boolean antiSway = running;
        if (antiSway) correctedStrafe = applyAntiSwayCorrection(controlledForward, strafe);
        else antiSwayYawOffset = 0.0f;

        setKeyPressed("forward", controlledForward > 0.03f);
        setKeyPressed("back", controlledForward < -0.03f);
        setKeyPressed("left", correctedStrafe > 0.5f);
        setKeyPressed("right", correctedStrafe < -0.5f);
        setKeyPressed("jump", jumping);
        setKeyPressed("sprint", controlledSprint);
        setForward(controlledForward);
        setStrafe(correctedStrafe);
        setJump(jumping);
        setSneak(false);
        setSprinting(controlledSprint);
    }

    void suppressSneakInput() {
        setKeyPressed("sneak", false);
        setSneak(false);
    }

    void calculateTravelDirection(float yaw) {
        double radians = Math.toRadians(yaw);
        double rawX = Math.sin(radians) - Math.cos(radians);
        double rawZ = -Math.cos(radians) - Math.sin(radians);

        if (Math.abs(rawX) >= Math.abs(rawZ)) {
            travelX = rawX >= 0.0 ? 1 : -1;
            travelZ = 0;
        } else {
            travelX = 0;
            travelZ = rawZ >= 0.0 ? 1 : -1;
        }
    }

    void initializeStraightBridgeLane(Entity player) {
        Vec3 position = player.getPosition();
        int startX = floor(position.x);
        int startY = floor(position.y) - 1;
        int startZ = floor(position.z);

        // 优先用激活绿框对应的方块定车道，而不是脚下格子；否则起步就偏一格，后面连不上�?
        int[] anchor = activationAnchorPos;
        if (anchor == null && hitboxLastPos != null) {
            anchor = hitboxLastPos;
        }
        if (anchor == null) {
            Object[] hit = raycastBlock(4.5, baseYaw, Math.max(player.getPitch(), activationPitch()));
            if (hit != null && hit.length >= 3 && hit[0] instanceof Vec3 && hit[2] != null) {
                int face = faceFromName((String) hit[2]);
                if (face >= 2) {
                    int[] hitPos = posFromVec((Vec3) hit[0]);
                    if (isPlayerOnActivationBlock(player, hitPos)) {
                        anchor = hitPos;
                        activationAnchorPos = new int[]{hitPos[0], hitPos[1], hitPos[2]};
                        activationAnchorFace = face;
                    }
                }
            }
        }

        if (anchor != null) {
            startX = anchor[0];
            startY = anchor[1];
            startZ = anchor[2];
        }

        bridgeLaneBlock = travelX != 0 ? startZ : startX;
        bridgeStartProgress = startX * travelX + startZ * travelZ;


        // Placement lane uses the green-box anchor, but anti-sway keeps the player's
        // launch lane captured in beginAutomation(). Pulling to block-center makes
        // the second Telly drift instead of jumping in a straight line.

        Object[] hit = raycastBlock(4.5, baseYaw, Math.max(player.getPitch(), activationPitch()));
        if (hit != null && hit.length > 0 && hit[0] instanceof Vec3) {
            int[] hitPos = posFromVec((Vec3) hit[0]);
            int hitLane = travelX != 0 ? hitPos[2] : hitPos[0];
            int hitProgress = straightProgress(hitPos);
            if (hitLane == bridgeLaneBlock
                    && Math.abs(hitPos[0] - startX) <= 2
                    && Math.abs(hitPos[2] - startZ) <= 2
                    && hitProgress < bridgeStartProgress) {
                bridgeStartProgress = hitProgress;
                latestStraightPlacedPos = new int[]{hitPos[0], hitPos[1], hitPos[2]};
                return;
            }
        }

        latestStraightPlacedPos = new int[]{startX, startY, startZ};
    }

    int straightProgress(int[] position) {
        if (position == null) return -2147483648;
        return position[0] * travelX + position[2] * travelZ;
    }

    boolean isStraightTellyTarget(int[] position) {
        if (!running || position == null) return true;
        int lane = travelX != 0 ? position[2] : position[0];
        if (lane != bridgeLaneBlock) return false;
        return straightProgress(position) >= bridgeStartProgress;
    }

    void updateAdaptivePlacementAim(Entity player) {
        if (!firstTellyPlacementPending) return;
        Object[] candidate = cachedCandidate;
        if (candidate != null) {
            int[] target = candidatePlacedPos(candidate);
            Vec3 hitVec = candidateHitVec(candidate);
            if (isStraightTellyTarget(target) && hitVec != null) {
                setAdaptiveAimToPoint(player, hitVec);
                return;
            }
        }

        int[] support = latestStraightPlacedPos != null ? latestStraightPlacedPos : lastPlacedPos;
        if (support == null || !isStraightTellyTarget(support)) return;
        int face = travelX > 0 ? 5 : travelX < 0 ? 4 : travelZ > 0 ? 3 : 2;
        int[] nextTarget = offsetPos(support, face);
        if (!isStraightTellyTarget(nextTarget) || !isReplaceable(nextTarget[0], nextTarget[1], nextTarget[2])) return;
        Vec3 fallbackHit = getSupportFaceHitVec(support, face, 0.5, 0.5);
        setAdaptiveAimToPoint(player, fallbackHit);
    }

    void setAdaptiveAimToPoint(Entity player, Vec3 point) {
        if (player == null || point == null) return;
        Vec3 position = player.getPosition();
        double eyeX = position.x;
        double eyeY = position.y + player.getEyeHeight();
        double eyeZ = position.z;
        double dx = point.x - eyeX;
        double dy = point.y - eyeY;
        double dz = point.z - eyeZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-5 && Math.abs(dy) < 1.0E-5) return;

        adaptiveAimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        adaptiveAimPitch = clamp((float) (-Math.toDegrees(Math.atan2(dy, horizontal))), -89.0f, 89.0f);
        adaptiveAimUpdatedAt = now();
        adaptiveAimValid = true;
    }

    float applyAntiSwayCorrection(float forward, float recordedStrafe) {
        Entity player = getPlayer();
        if (player == null) return recordedStrafe;

        Vec3 position = player.getPosition();
        Vec3 motion = getPlayerMotion();
        double lanePosition = travelX != 0 ? position.z : position.x;
        double laneVelocity = motion == null ? 0.0 : (travelX != 0 ? motion.z : motion.x);
        double error = antiSwayLane - lanePosition;

        if (Math.abs(error) < 0.015 && Math.abs(laneVelocity) < 0.008) {
            antiSwayTapUsed = false;
            antiSwayYawOffset *= 0.65f;
            if (Math.abs(antiSwayYawOffset) < 0.03f) antiSwayYawOffset = 0.0f;
            return recordedStrafe;
        }

        double desiredLaneVelocity = error * 0.42 - laneVelocity * 0.78;
        if (desiredLaneVelocity > 0.16) desiredLaneVelocity = 0.16;
        if (desiredLaneVelocity < -0.16) desiredLaneVelocity = -0.16;
        double velocityCorrection = desiredLaneVelocity - laneVelocity;

        // Use scripted yaw while running; camera yaw can point the straight telly correction backward.
        double radians = Math.toRadians(running ? scriptedRotationYaw : player.getYaw());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double yawLaneDerivative = travelX != 0
                ? -forward * sin + recordedStrafe * cos
                : -forward * cos - recordedStrafe * sin;
        double desiredYawOffset = 0.0;
        if (Math.abs(yawLaneDerivative) >= 0.12) {
            desiredYawOffset = Math.toDegrees(velocityCorrection * 0.55 / yawLaneDerivative);
        }
        if (desiredYawOffset > 2.25) desiredYawOffset = 2.25;
        if (desiredYawOffset < -2.25) desiredYawOffset = -2.25;
        antiSwayYawOffset = antiSwayYawOffset * 0.60f + (float) desiredYawOffset * 0.40f;

        double strafeLaneAxis = travelX != 0 ? sin : cos;
        boolean tapHelps = Math.abs(strafeLaneAxis) >= 0.20 && velocityCorrection * strafeLaneAxis > 0.0;
        if (tapHelps
                && !antiSwayTapUsed
                && Math.abs(velocityCorrection) >= 0.03
                && recordedStrafe < 0.5f) {
            antiSwayTapUsed = true;
            return recordedStrafe + 1.0f;
        }

        return recordedStrafe;
    }

    void applyUse(boolean pressed) {
        if (pressed && !autoPlaceDebugActive) {
            printStatus("&aAutoPlace activated");
        }
        autoPlaceDebugActive = pressed;
        tellyAutoPlaceWindow = pressed;
        setKeyPressed("use", pressed);
    }

    void restorePhysicalUse() {
        tellyAutoPlaceWindow = false;
        autoPlaceDebugActive = false;
        setKeyPressed("use", Mouse.isButtonDown(1));
    }

    void releaseMovementKeys() {
        restorePhysicalKey("forward");
        restorePhysicalKey("back");
        restorePhysicalKey("left");
        restorePhysicalKey("right");
        restorePhysicalKey("jump");
        restorePhysicalKey("sneak");
        restorePhysicalKey("sprint");
    }

    void restorePhysicalKey(String key) {
        int code = getKeyCode(key);
        setKeyPressed(key, code >= 0 && isKeyDown(code));
    }

    float tellyWrapAngle(float angle) {
        while (angle <= -180.0f) angle += 360.0f;
        while (angle > 180.0f) angle -= 360.0f;
        return angle;
    }

    float clamp(float value, float minimum, float maximum) {
        if (value < minimum) return minimum;
        if (value > maximum) return maximum;
        return value;
    }

    void autoPlaceOnEnable() {
        setKeyPressed("attack", false);
        resetControllerState();
    }

    void autoPlaceOnDisable() {
        resetControllerState();
        restoreUseToPhysicalState();
        setKeyPressed("attack", false);
        bridgeRemove("AutoPlacePlacing");
        releaseExperimentalPlacementClaim();
    }

    void resetControllerState() {
        currentClientTick = -2147483648;
        placementEvaluationTick = -2147483648;
        lastPlacementAttemptTick = -2147483648;
        lastSuccessfulPlaceTick = -2147483648;
        forceSuppressTick = -2147483648;
        totalC08Counter = 0L;
        c08CounterAtTickBoundary = 0L;
        hasLastSentServerPos = false;
        clearCachedCandidate();
        lastPlacedPos = null;
        lastSupportPos = null;
        lastSupportFace = -1;
        cachedBelowTargets = null;
        cachedBelowTargetsTick = -2147483648;
        rejectedTargets.clear();
        forcedModeCheck = 0;
        useSuppressed = false;
        silentPitchActive = false;
        placingViaModule = false;
        manualC08InWindow = false;
    }

    void autoPlaceOnWorldJoin(Entity entity) {
        if (entity != null && entity.isUser) {
            resetControllerState();
        }
    }

    void autoPlaceOnPreUpdate() {
        Entity player = getPlayer();
        if (player == null) return;

        syncPlacementTick(player);

        if (placementEvaluationTick != currentClientTick) {
            placementEvaluationTick = currentClientTick;
            processAutoPlaceTick(player);
        }
    }

    void syncPlacementTick(Entity player) {
        int tick = placementTick(player);
        if (tick == currentClientTick) return;
        currentClientTick = tick;
        candidateResolvedThisTick = false;
        silentPitchActive = false;
    }

    boolean useExtendedSearch() {
        // Render-tick candidate search is disabled, so keep the original Flux wider tick search.
        // The second/next Telly needs these wider face-hit offsets to catch the next support face.
        return true;
    }

    void autoPlaceOnPostMotion() {
        c08CounterAtTickBoundary = totalC08Counter;
        manualC08InWindow = false;
    }

    void autoPlaceOnRenderTick(float partialTicks) {
        Entity player = getPlayer();
        if (player == null) return;
        if (!isAutoPlaceActiveWindow(player)) return;

        ItemStack heldStack = player.getHeldItem();
        if (!isUsableBlockStack(heldStack)) return;

        float basePitch = sanitizePitch(player.getPitch(), player.getPitch());
        Object[] candidate = resolveCandidateWithOffCursorSilentPitch(player, player.getYaw(), basePitch, heldStack);
        if (candidate != null) {
            silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
            silentPitchActive = true;
            suppressUse();
        }
    }

    void autoPlaceOnPreMotion(PlayerState state) {
        if (silentPitchActive && !manualC08InWindow) {
            state.pitch = sanitizePitch(silentPitch, state.pitch);
        }
    }

    boolean autoPlaceOnPacketSent(CPacket packet) {
        if (packet instanceof C03 c03) {
            if (c03.moving && c03.position != null) {
                hasLastSentServerPos = true;
                lastSentServerPosX = c03.position.x;
                lastSentServerPosY = c03.position.y;
                lastSentServerPosZ = c03.position.z;
            }
            return true;
        }
        if (packet instanceof C08 c08) {
            if (c08.direction == 255) {
                if (shouldCancelAutoPlaceUseItem()) {
                    suppressUse();
                    return false;
                }
            } else {
                if (c08.itemStack != null && c08.itemStack.isBlock) {
                    totalC08Counter++;
                    if (!placingViaModule) manualC08InWindow = true;
                }
            }
        }
        return true;
    }

    boolean autoPlaceOnMouse(int button, boolean state) {
        if (!state || (button != 0 && button != 1)) return true;

        if (button == 1 && shouldCancelAutoPlaceUseItem()) {
            suppressUse();
            return false;
        }
        if (!shouldSuppressManualClicksThisTick()) return true;
        setKeyPressed("attack", false);
        return false;
    }

    boolean shouldSuppressManualClicksThisTick() {
        if (!isInGameContext()) return false;
        return lastSuccessfulPlaceTick == currentClientTick || forceSuppressTick == currentClientTick;
    }

    boolean shouldCancelAutoPlaceUseItem() {
        if (!isInGameContext()) return false;
        if (shouldSuppressManualClicksThisTick()) return true;
        if (ViaProtocol.newerThan1_8() && lastPlacementAttemptTick == currentClientTick) return true;
        return useSuppressed && silentPitchActive;
    }

    void suppressUse() {
        setKeyPressed("use", false);
        useSuppressed = true;
    }

    void restoreUseToPhysicalState() {
        setKeyPressed("use", running
                ? tellyAutoPlaceWindow
                : Mouse.isButtonDown(1));
        useSuppressed = false;
    }

    boolean isInGameContext() {
        return getPlayer() != null && (mc.currentScreen == null);
    }

    boolean areAutoPlaceConditionsMet(Entity player) {
        if (!tellyAutoPlaceWindow) return false;
        return isUsableBlockStack(player.getHeldItem());
    }

    boolean isAutoPlaceActiveWindow(Entity player) {
        if (!isInGameContext()) return false;
        if (bridgeHas("ScaffoldRunning")) return false;
        if (!areAutoPlaceConditionsMet(player)) return false;
        return isUsableBlockStack(player.getHeldItem());
    }

    boolean isUsableBlockStack(ItemStack stack) {
        if (stack == null || !stack.isBlock || stack.name == null || stack.stackSize <= 0) return false;
        String name = stack.name.toLowerCase();
        for (String bad : UNPLACEABLE_EXACT) {
            if (name.equals(bad)) return false;
        }
        for (String bad : UNPLACEABLE_CONTAINS) {
            if (name.contains(bad)) return false;
        }
        return true;
    }

    boolean isBlockBelowPlayerReplaceable(Entity player) {
        Vec3 pos = player.getPosition();
        return isReplaceable(floor(pos.x), floor(pos.y) - 1, floor(pos.z));
    }

    boolean placedInCurrentWindow() {
        return totalC08Counter > c08CounterAtTickBoundary;
    }

    boolean claimExperimentalPlacementTick() {
        Object tickValue = bridgeGet("PlacementArbiterTick");
        Object ownerValue = bridgeGet("PlacementArbiterOwner");
        if (tickValue instanceof Number
                && ((Number) tickValue).intValue() == currentClientTick
                && ownerValue != null
                && !scriptName.equals(String.valueOf(ownerValue))) {
            return false;
        }
        bridgePut("PlacementArbiterTick", currentClientTick);
        bridgePut("PlacementArbiterOwner", scriptName);
        return true;
    }

    void releaseExperimentalPlacementClaim() {
        Object ownerValue = bridgeGet("PlacementArbiterOwner");
        if (ownerValue == null || !scriptName.equals(String.valueOf(ownerValue))) return;
        bridgeRemove("PlacementArbiterTick");
        bridgeRemove("PlacementArbiterOwner");
    }

    void processAutoPlaceTick(Entity player) {
        pruneRejectedTargets();

        if (lastPlacedPos != null && !isSupportAvailable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2])) {
            lastPlacedPos = null;
            lastSupportPos = null;
            lastSupportFace = -1;
        }

        if (!isAutoPlaceActiveWindow(player)) {
            clearCachedCandidate();
            bridgeRemove("AutoPlacePlacing");
            if (useSuppressed) restoreUseToPhysicalState();
            return;
        }

        ItemStack heldStack = player.getHeldItem();
        if (!isUsableBlockStack(heldStack)) {
            clearCachedCandidate();
            if (useSuppressed) restoreUseToPhysicalState();
            return;
        }

        if (!isBlockBelowPlayerReplaceable(player)) {
            clearCachedCandidate();
            if (useSuppressed) restoreUseToPhysicalState();
            return;
        }

        // 运行中用脚本视角搜点；相机视角会导致候选块偏到后左并首块放空�?
        if (ViaProtocol.newerThan1_8()) {
            setKeyPressed("use", true);
            tellyAutoPlaceWindow = true;
            autoPlaceDebugActive = true;
            forceSuppressTick = currentClientTick;
            return;
        }

        float yaw = running ? scriptedRotationYaw : player.getYaw();
        float basePitch = sanitizePitch(running ? scriptedRotationPitch : player.getPitch(), player.getPitch());
        Object[] candidate = resolveCandidateWithOffCursorSilentPitch(player, yaw, basePitch, heldStack);
        if (candidate != null) {
            silentPitch = sanitizePitch(candidatePitch(candidate), basePitch);
            silentPitchActive = true;
            suppressUse();
        } else if (useSuppressed && !placedInCurrentWindow() && lastPlacementAttemptTick != currentClientTick) {
            restoreUseToPhysicalState();
        }

        if (placedInCurrentWindow() || lastPlacementAttemptTick == currentClientTick) {
            suppressUse();
            return;
        }

        if (candidate == null) {
            clearCachedCandidate();
            return;
        }

        if (!claimExperimentalPlacementTick()) {
            clearCachedCandidate();
            return;
        }

        bridgePut("AutoPlacePlacing");
        lastPlacementAttemptTick = currentClientTick;

        if (attemptPlacement(player, candidate, heldStack)) return;

        if (placedInCurrentWindow()) return;
        if (ViaProtocol.newerThan1_8()) {
            suppressUse();
            forceSuppressTick = currentClientTick;
            releaseExperimentalPlacementClaim();
            return;
        }

        float retryYaw = running ? scriptedRotationYaw : player.getYaw();
        float retryPitch = running ? scriptedRotationPitch : player.getPitch();
        clearCachedCandidate();
        Object[] retryCandidate = findBelowPlacement(player, retryYaw, retryPitch, heldStack, now() + (useExtendedSearch() ? 4L : 2L));
        cacheCandidate(retryCandidate, retryYaw, retryPitch);
        if (retryCandidate != null) {
            silentPitch = sanitizePitch(candidatePitch(retryCandidate), retryPitch);
            silentPitchActive = true;
            if (attemptPlacement(player, retryCandidate, heldStack)) return;
        }
        releaseExperimentalPlacementClaim();
    }

    boolean attemptPlacement(Entity player, Object[] candidate, ItemStack heldStack) {
        if (candidate == null) return false;
        int[] placedPos = candidatePlacedPos(candidate);
        int[] supportPos = candidateSupportPos(candidate);
        int face = candidateFace(candidate);
        if (placedPos == null || supportPos == null || face <= 0) return false;
        if (!isStraightTellyTarget(placedPos)) return false;
        if (!isBlockBelowPlayerReplaceable(player)) return false;
        if (!isUsableBlockStack(player.getHeldItem())) return false;
        if (placedInCurrentWindow()) return false;

        float placementPitch = sanitizePitch(candidatePitch(candidate), running ? scriptedRotationPitch : player.getPitch());
        Object[] prePlaceHit = resolveVerifiedHit(running ? scriptedRotationYaw : player.getYaw(), placementPitch, supportPos, face, placedPos);
        if (prePlaceHit == null) return false;

        if (cancelledGhostBlocks.contains(posKey(supportPos))) return false;
        if (!isReplaceable(placedPos[0], placedPos[1], placedPos[2])) return false;
        if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return false;
        if (doesPlacementIntersectPlayer(player, placedPos)) return false;

        long counterBefore = totalC08Counter;
        Vec3 hitAbs = (Vec3) prePlaceHit[2];
        placingViaModule = true;
        boolean placed = placeBlock(new Vec3(supportPos[0], supportPos[1], supportPos[2]), faceName(face), hitAbs);
        placingViaModule = false;
        boolean packetSent = totalC08Counter > counterBefore;

        if (!placed && !packetSent) return false;
        if (!packetSent) {
            markRejectedTarget(placedPos);
            return false;
        }

        lastPlacedPos = placedPos;
        lastSupportPos = supportPos;
        lastSupportFace = face;
        lastSuccessfulPlaceTick = currentClientTick;
        forceSuppressTick = currentClientTick;
        swing();
        return true;
    }

    Object[] resolveVerifiedHit(float yaw, float pitch, int[] expectedSupport, int expectedFace, int[] expectedPlaced) {
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] tracedSupport = (int[]) traced[0];
        int tracedFace = (Integer) traced[1];
        if (!posEquals(tracedSupport, expectedSupport) || tracedFace != expectedFace) return null;
        int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
        if (!posEquals(tracedPlaced, expectedPlaced)) return null;
        return traced;
    }

    Object[] resolveCandidateWithOffCursorSilentPitch(Entity player, float yaw, float basePitch, ItemStack heldStack) {
        float safeBasePitch = sanitizePitch(basePitch, player.getPitch());
        Object[] previousCandidate = cachedCandidate;
        Object[] baseCandidate = resolveCandidateForCurrentTick(player, yaw, safeBasePitch, heldStack);
        if (baseCandidate == null) {
            if (previousCandidate != null) {
                float previousBlockPitch = getBlockDerivedSilentPitch(player, previousCandidate, safeBasePitch);
                Object[] recovered = resolveCandidateForCurrentTick(player, yaw, previousBlockPitch, heldStack);
                if (recovered != null) return recovered;
                cacheCandidate(previousCandidate, yaw, safeBasePitch);
                return previousCandidate;
            }
            return null;
        }
        if (isPlacementLookAligned(yaw, safeBasePitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidatePlacedPos(baseCandidate))) {
            return baseCandidate;
        }
        float blockPitch = getBlockDerivedSilentPitch(player, baseCandidate, safeBasePitch);
        if (isPlacementLookAligned(yaw, blockPitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidatePlacedPos(baseCandidate))) {
            return new Object[]{blockPitch, candidateSupportPos(baseCandidate), candidateFace(baseCandidate), candidateHitVec(baseCandidate), candidatePlacedPos(baseCandidate)};
        }
        Object[] corrected = resolveCandidateForCurrentTick(player, yaw, blockPitch, heldStack);
        if (corrected != null && posEquals(candidatePlacedPos(baseCandidate), candidatePlacedPos(corrected))) {
            return corrected;
        }
        cacheCandidate(baseCandidate, yaw, safeBasePitch);
        return baseCandidate;
    }

    Object[] resolveCandidateForCurrentTick(Entity player, float yaw, float pitch, ItemStack heldStack) {
        float safePitch = sanitizePitch(pitch, player.getPitch());
        if (hasCachedCandidateForCurrentTick(yaw, safePitch)) return cachedCandidate;
        Object[] candidate = findBelowPlacement(player, yaw, safePitch, heldStack, now() + (useExtendedSearch() ? 8L : 4L));
        cacheCandidate(candidate, yaw, safePitch);
        return candidate;
    }

    float getBlockDerivedSilentPitch(Entity player, Object[] candidate, float fallbackPitch) {
        if (candidate == null) return sanitizePitch(fallbackPitch, fallbackPitch);
        Vec3 hitVec = candidateHitVec(candidate);
        if (hitVec != null) {
            Float derived = computePitchToHitVec(player, hitVec);
            if (derived != null) return sanitizePitch(derived, fallbackPitch);
        }
        return sanitizePitch(candidatePitch(candidate), fallbackPitch);
    }

    void cacheCandidate(Object[] candidate, float yaw, float pitch) {
        cachedCandidate = candidate;
        cachedCandidateTick = currentClientTick;
        cachedCandidateYaw = yaw;
        cachedCandidatePitch = pitch;
        candidateResolvedThisTick = candidate != null;
    }

    boolean hasCachedCandidateForCurrentTick(float yaw, float pitch) {
        if (cachedCandidateTick != currentClientTick || !candidateResolvedThisTick || cachedCandidate == null)
            return false;
        if (Float.isNaN(cachedCandidateYaw) || Float.isNaN(cachedCandidatePitch)) return false;
        return Math.abs(wrapAngle(yaw - cachedCandidateYaw)) <= 0.75f && Math.abs(pitch - cachedCandidatePitch) <= 0.75f;
    }

    void clearCachedCandidate() {
        cachedCandidate = null;
        cachedCandidateTick = -2147483648;
        cachedCandidateYaw = Float.NaN;
        cachedCandidatePitch = Float.NaN;
        candidateResolvedThisTick = false;
    }

    Object[] findBelowPlacement(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (now() >= deadlineMs) return null;

        Object[] cursorRayCandidate = findDirectCursorRayPlacement(player, yaw, currentPitch, heldStack);
        if (cursorRayCandidate != null) return cursorRayCandidate;

        int modeCheck = getConditionModeCheck(player);
        forcedModeCheck = modeCheck;
        Object[] result = null;
        if (modeCheck == 1) {
            boolean straightGroundException = isStraightPreviousTickCenterOnGroundSupport(player);
            boolean straightCenterBelowAir = isStraightCenterBelowAir(player);
            boolean tryPreviousVisibleFirst = straightGroundException || !isCursorDirectedAtBlock(yaw, currentPitch) || isNearStraightSupportEdge(player) || straightCenterBelowAir;

            if (straightCenterBelowAir) {
                result = findBelowPlayerAirborneFallback(player, yaw, currentPitch, heldStack, Math.max(deadlineMs, now() + (useExtendedSearch() ? 4L : 2L)));
            }
            if (result == null && tryPreviousVisibleFirst) {
                result = findStraightPreviousVisibleFaceFallback(player, yaw, currentPitch, heldStack, deadlineMs);
            }
            if (result == null && straightGroundException) {
                result = findStraightGroundExceptionCandidate(player, yaw, currentPitch, heldStack, deadlineMs);
            }
            if (result == null) {
                result = findStraightLegacyLaneFallback(player, yaw, currentPitch, heldStack, deadlineMs);
            }
            if (result == null && !tryPreviousVisibleFirst) {
                result = findStraightPreviousVisibleFaceFallback(player, yaw, currentPitch, heldStack, deadlineMs);
            }
            if (result == null) {
                result = findPreviousBlockAirborneFallback(player, yaw, currentPitch, heldStack, Math.max(deadlineMs, now() + (useExtendedSearch() ? 4L : 2L)));
            }
        } else {
            long diagonalDeadline = Math.max(deadlineMs, now() + (useExtendedSearch() ? 10L : 6L));
            result = findBelowPlacementForSupport(player, yaw, currentPitch, heldStack, null, -1, diagonalDeadline);
            if (result == null) {
                result = findBelowPlayerAirborneFallback(player, yaw, currentPitch, heldStack, diagonalDeadline);
            }
            if (result == null) {
                result = findNearestSupportToBelowPlayerFallback(player, yaw, currentPitch, heldStack, diagonalDeadline);
            }
            if (result == null) {
                result = findLegacyBelowPlacement(player, yaw, currentPitch, heldStack, diagonalDeadline);
            }
        }
        forcedModeCheck = 0;
        return result;
    }

    Object[] findDirectCursorRayPlacement(Entity player, float yaw, float pitch, ItemStack heldStack) {
        if (!isUsableBlockStack(heldStack)) return null;
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] supportPos = (int[]) traced[0];
        int face = (Integer) traced[1];
        if (face == 0) return null;
        int[] targetPos = offsetPos(supportPos, face);
        if (!isPlacementTargetAvailable(player, targetPos)) return null;
        if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return null;
        if (shouldRejectStraightSideSwitch(player, targetPos, face)) return null;
        float tracedPitch = clampFloat(pitch, -89.0f, 89.0f);
        return new Object[]{tracedPitch, supportPos, face, traced[2], targetPos};
    }

    Object[] findStraightGroundExceptionCandidate(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        int previousForcedMode = forcedModeCheck;
        forcedModeCheck = 2;
        Object[] candidate = findBelowPlacementForSupport(player, yaw, currentPitch, heldStack, null, -1, deadlineMs);
        if (candidate == null) {
            candidate = findBelowPlayerAirborneFallback(player, yaw, currentPitch, heldStack, Math.max(deadlineMs, now() + (useExtendedSearch() ? 4L : 2L)));
        }
        if (candidate == null) {
            candidate = findNearestSupportToBelowPlayerFallback(player, yaw, currentPitch, heldStack, Math.max(deadlineMs, now() + (useExtendedSearch() ? 4L : 2L)));
        }
        forcedModeCheck = previousForcedMode;
        return candidate;
    }

    Object[] findPreviousBlockAirborneFallback(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (!hasValidLastSupportFace(player) || now() >= deadlineMs) return null;
        int[] exactTarget = offsetPos(lastSupportPos, lastSupportFace);
        if (!isPlacementTargetAvailable(player, exactTarget)) return null;
        boolean diagonal = isDiagonalMovementContext(player);
        return findPitchPlacementForTarget(player, yaw, currentPitch, exactTarget, heldStack, lastSupportPos, lastSupportFace, deadlineMs, false, diagonal);
    }

    Object[] findStraightLegacyLaneFallback(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (now() >= deadlineMs) return null;
        int currentY = getCurrentBelowTargetY(player);
        int strictY = getStrictBelowTargetY(player);
        int previousY = getPreviousBelowTargetY(player);
        int upwardY = isStraightAscendingContext(player) ? currentY + 1 : -2147483648;

        List<int[]> laneTargets = new ArrayList<>();
        addBelowTarget(player, laneTargets, getCursorStartTargetAtY(player, yaw, currentPitch, currentY));
        addBelowTarget(player, laneTargets, getCursorPlacedTargetFromRay(yaw, currentPitch, currentY));
        addBelowTarget(player, laneTargets, getCursorTargetAtY(player, yaw, currentPitch, currentY));
        if (strictY != currentY) {
            addBelowTarget(player, laneTargets, getCursorStartTargetAtY(player, yaw, currentPitch, strictY));
            addBelowTarget(player, laneTargets, getCursorPlacedTargetFromRay(yaw, currentPitch, strictY));
            addBelowTarget(player, laneTargets, getCursorTargetAtY(player, yaw, currentPitch, strictY));
        }
        if (previousY != currentY && previousY != strictY) {
            addBelowTarget(player, laneTargets, getCursorStartTargetAtY(player, yaw, currentPitch, previousY));
            addBelowTarget(player, laneTargets, getCursorPlacedTargetFromRay(yaw, currentPitch, previousY));
            addBelowTarget(player, laneTargets, getCursorTargetAtY(player, yaw, currentPitch, previousY));
        }
        if (upwardY != -2147483648 && upwardY != currentY && upwardY != strictY && upwardY != previousY) {
            addBelowTarget(player, laneTargets, getCursorStartTargetAtY(player, yaw, currentPitch, upwardY));
            addBelowTarget(player, laneTargets, getCursorPlacedTargetFromRay(yaw, currentPitch, upwardY));
            addBelowTarget(player, laneTargets, getCursorTargetAtY(player, yaw, currentPitch, upwardY));
        }

        for (int[] targetPos : laneTargets) {
            if (now() >= deadlineMs) return null;
            if (!isStraightLaneTargetAvailable(player, targetPos, currentY, strictY, previousY, upwardY)) continue;
            Object[] candidate = findLegacyPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, null, deadlineMs);
            if (candidate != null) return candidate;
        }
        return null;
    }

    boolean isCursorDirectedAtBlock(float yaw, float pitch) {
        return rayCast(yaw, pitch) != null;
    }

    boolean isStraightCenterBelowAir(Entity player) {
        Vec3 pos = player.getPosition();
        return isReplaceableName(blockNameAt(floor(pos.x), getCurrentBelowTargetY(player), floor(pos.z)), true);
    }

    boolean isStraightPreviousTickCenterOnGroundSupport(Entity player) {
        Vec3 last = player.getLastPosition();
        return !isReplaceableName(blockNameAt(floor(last.x), floor(last.y) - 1, floor(last.z)), true);
    }

    boolean isNearStraightSupportEdge(Entity player) {
        if (lastSupportPos == null || lastSupportFace < 2) return false;
        Vec3 pos = player.getPosition();
        double localX = pos.x - lastSupportPos[0];
        double localZ = pos.z - lastSupportPos[2];
        if (isPastStraightSupportEdgeThreshold(lastSupportFace, localX, localZ)) return true;
        Vec3 motion = getPlayerMotion();
        if (motion.x * motion.x + motion.z * motion.z < 1.0E-4) return false;
        if (!isMovingTowardStraightSupportEdge(lastSupportFace, motion.x, motion.z)) return false;
        return isPastStraightSupportEdgeThreshold(lastSupportFace, localX + motion.x * 1.45, localZ + motion.z * 1.45);
    }

    boolean isPastStraightSupportEdgeThreshold(int supportFace, double localX, double localZ) {
        if (supportFace == 5) return localX >= 0.52;
        if (supportFace == 4) return localX <= 0.48;
        if (supportFace == 3) return localZ >= 0.52;
        if (supportFace == 2) return localZ <= 0.48;
        return false;
    }

    boolean isMovingTowardStraightSupportEdge(int supportFace, double motionX, double motionZ) {
        if (supportFace == 5) return motionX > 0.0;
        if (supportFace == 4) return motionX < 0.0;
        if (supportFace == 3) return motionZ > 0.0;
        if (supportFace == 2) return motionZ < 0.0;
        return false;
    }

    Object[] findStraightPreviousVisibleFaceFallback(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (now() >= deadlineMs || !hasValidLastSupportFace(player)) return null;
        if (lastSupportFace <= 0) return null;
        int[] targetPos = offsetPos(lastSupportPos, lastSupportFace);
        if (!isPlacementTargetAvailable(player, targetPos)) return null;
        return findPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, lastSupportPos, lastSupportFace, deadlineMs, true, true);
    }

    List<int[]> getBelowPlayerFallbackEndpoints(Entity player, float yaw, float pitch, int targetY) {
        List<int[]> endpoints = new ArrayList<>();
        if (!isDiagonalMovementContext(player)) {
            if (!player.onGround()) {
                addBelowTargetIfUnique(player, endpoints, getFeetBelowTargetAtY(player, targetY));
                addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
                addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
            }
            addBelowTargetIfUnique(player, endpoints, getCursorStartTargetAtY(player, yaw, pitch, targetY));
            addBelowTargetIfUnique(player, endpoints, getCursorPlacedTargetFromRay(yaw, pitch, targetY));
            addBelowTargetIfUnique(player, endpoints, getCursorTargetAtY(player, yaw, pitch, targetY));
            return endpoints;
        }
        addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.0));
        addBelowTargetIfUnique(player, endpoints, getMotionBelowTargetAtY(player, targetY, 1.7));
        return endpoints;
    }

    Object[] findBelowPlayerAirborneFallback(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (now() >= deadlineMs) return null;
        int playerBelowY = getCurrentBelowTargetY(player);
        boolean diagonal = isDiagonalMovementContext(player);
        boolean allowNonCursorTarget = diagonal || !player.onGround();
        List<int[]> fallbackTargets = new ArrayList<>();
        for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, currentPitch, playerBelowY)) {
            addBelowTarget(player, fallbackTargets, endpoint);
        }
        for (int[] targetPos : fallbackTargets) {
            if (now() >= deadlineMs) return null;
            if (!isPlacementTargetAvailable(player, targetPos)) continue;
            Object[] candidate = findPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, null, -1, deadlineMs, false, allowNonCursorTarget);
            if (candidate != null) return candidate;
        }
        return null;
    }

    Object[] findNearestSupportToBelowPlayerFallback(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (now() >= deadlineMs) return null;
        int targetY = getCurrentBelowTargetY(player);
        int[] belowPlayer = getFeetBelowTargetAtY(player, targetY);
        if (belowPlayer == null || hasDirectSupportNeighbor(belowPlayer)) return null;

        int[] searchOrigin = getPathStartTowardBelowPlayer(player, targetY, belowPlayer);
        int[] nearestStart = findNearestSupportedReplaceableTarget(player, searchOrigin, belowPlayer, targetY, deadlineMs);
        if (nearestStart == null) return null;

        List<int[]> requiredPath = rasterizeHorizontalLineAtY(nearestStart, belowPlayer, targetY, 64);
        for (int i = requiredPath.size() - 1; i >= 0; i--) {
            if (now() >= deadlineMs) return null;
            int[] pathPos = requiredPath.get(i);
            if (!isPlacementTargetAvailable(player, pathPos)) continue;
            Object[] candidate = findPitchPlacementForTarget(player, yaw, currentPitch, pathPos, heldStack, null, -1, deadlineMs, false, true);
            if (candidate != null) return candidate;
        }
        return null;
    }

    int[] findNearestSupportedReplaceableTarget(Entity player, int[] origin, int[] belowPlayer, int targetY, long deadlineMs) {
        if (origin == null || belowPlayer == null || now() >= deadlineMs) return null;
        for (int radius = 0; radius <= 3; radius++) {
            int[] bestAtRadius = null;
            double bestScore = Double.POSITIVE_INFINITY;
            for (int dx = -radius; dx <= radius; dx++) {
                int dzAbs = radius - Math.abs(dx);
                int[] positive = new int[]{origin[0] + dx, targetY, origin[2] + dzAbs};
                if (isPlacementTargetAvailable(player, positive) && hasDirectSupportNeighbor(positive)) {
                    double score = scoreAirPathStartCandidate(positive, belowPlayer, origin);
                    if (score < bestScore) {
                        bestScore = score;
                        bestAtRadius = positive;
                    }
                }
                if (dzAbs == 0) continue;
                int[] negative = new int[]{origin[0] + dx, targetY, origin[2] - dzAbs};
                if (isPlacementTargetAvailable(player, negative) && hasDirectSupportNeighbor(negative)) {
                    double score = scoreAirPathStartCandidate(negative, belowPlayer, origin);
                    if (score < bestScore) {
                        bestScore = score;
                        bestAtRadius = negative;
                    }
                }
            }
            if (bestAtRadius != null) return bestAtRadius;
        }
        return null;
    }

    double scoreAirPathStartCandidate(int[] candidate, int[] belowPlayer, int[] origin) {
        double sampleY = candidate[1] + 0.5;
        double goalDistSq = distSq(candidate[0] + 0.5, sampleY, candidate[2] + 0.5, belowPlayer[0] + 0.5, sampleY, belowPlayer[2] + 0.5);
        double originDistSq = distSq(candidate[0] + 0.5, sampleY, candidate[2] + 0.5, origin[0] + 0.5, sampleY, origin[2] + 0.5);
        return goalDistSq * 4.0 + originDistSq;
    }

    int[] getPathStartTowardBelowPlayer(Entity player, int targetY, int[] fallback) {
        int[] pathStart = null;
        if (lastPlacedPos != null && lastPlacedPos[1] == targetY) pathStart = lastPlacedPos;
        if (pathStart == null) pathStart = getMotionBelowTargetAtY(player, targetY, 1.7);
        if (pathStart == null) pathStart = getMotionBelowTargetAtY(player, targetY, 1.0);
        return pathStart != null ? pathStart : fallback;
    }

    boolean hasValidLastPlacedPos(Entity player) {
        if (lastPlacedPos == null) return false;
        return isWithinReach(player, lastPlacedPos) && isSupportAvailable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2]) && !isInteractable(lastPlacedPos[0], lastPlacedPos[1], lastPlacedPos[2]);
    }

    boolean hasValidLastSupportFace(Entity player) {
        if (lastSupportPos == null || lastSupportFace < 0) return false;
        return isWithinReach(player, lastSupportPos) && isSupportAvailable(lastSupportPos[0], lastSupportPos[1], lastSupportPos[2]) && !isInteractable(lastSupportPos[0], lastSupportPos[1], lastSupportPos[2]);
    }

    Object[] findLegacyBelowPlacement(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (now() >= deadlineMs || !isUsableBlockStack(heldStack)) return null;
        if (isDiagonalMovementContext(player)) {
            Object[] diagonalCandidate = findLegacyDiagonalPlacement(player, yaw, currentPitch, heldStack, deadlineMs);
            if (diagonalCandidate != null) return diagonalCandidate;
        }
        if (hasValidLastPlacedPos(player)) {
            Object[] preferred = findLegacyBelowPlacementForSupport(player, yaw, currentPitch, heldStack, lastPlacedPos, deadlineMs);
            if (preferred != null) return preferred;
        }
        return findLegacyBelowPlacementForSupport(player, yaw, currentPitch, heldStack, null, deadlineMs);
    }

    Object[] findLegacyDiagonalPlacement(Entity player, float yaw, float currentPitch, ItemStack heldStack, long deadlineMs) {
        if (now() >= deadlineMs) return null;
        List<int[]> diagonalTargets = new ArrayList<>();
        int currentY = getCurrentBelowTargetY(player);
        int strictY = getStrictBelowTargetY(player);
        for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, currentPitch, currentY)) {
            addBelowTarget(player, diagonalTargets, endpoint);
        }
        if (strictY != currentY) {
            for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, currentPitch, strictY)) {
                addBelowTarget(player, diagonalTargets, endpoint);
            }
        }
        if (diagonalTargets.isEmpty()) return null;
        int[] preferredSupportPos = hasValidLastPlacedPos(player) ? lastPlacedPos : null;
        for (int[] targetPos : diagonalTargets) {
            if (now() >= deadlineMs) return null;
            if (!isPlacementTargetAvailable(player, targetPos)) continue;
            Object[] candidate = findLegacyPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, preferredSupportPos, deadlineMs);
            if (candidate != null) return candidate;
        }
        if (preferredSupportPos == null) return null;
        for (int[] targetPos : diagonalTargets) {
            if (now() >= deadlineMs) return null;
            if (!isPlacementTargetAvailable(player, targetPos)) continue;
            Object[] candidate = findLegacyPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, null, deadlineMs);
            if (candidate != null) return candidate;
        }
        return null;
    }

    Object[] findLegacyBelowPlacementForSupport(Entity player, float yaw, float currentPitch, ItemStack heldStack, int[] preferredSupportPos, long deadlineMs) {
        for (int[] targetPos : getMessageStyleBelowTargets(player)) {
            if (now() >= deadlineMs) return null;
            if (!isPlacementTargetAvailable(player, targetPos)) continue;
            Object[] candidate = findLegacyPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, preferredSupportPos, deadlineMs);
            if (candidate != null) return candidate;
        }
        return null;
    }

    Object[] findLegacyPitchPlacementForTarget(Entity player, float yaw, float currentPitch, int[] targetPos, ItemStack heldStack, int[] preferredSupportPos, long deadlineMs) {
        float clampedBasePitch = clampFloat(currentPitch, 40.0f, 89.0f);
        Object[] direct = tryLegacyPitch(yaw, clampedBasePitch, targetPos, preferredSupportPos, deadlineMs);
        if (direct != null) return direct;
        for (int offset = 1; offset <= 49; offset++) {
            if (now() >= deadlineMs) return null;
            float up = clampedBasePitch + offset;
            if (up <= 89.0f) {
                Object[] candidate = tryLegacyPitch(yaw, up, targetPos, preferredSupportPos, deadlineMs);
                if (candidate != null) return candidate;
            }
            float down = clampedBasePitch - offset;
            if (down >= 40.0f) {
                Object[] candidate = tryLegacyPitch(yaw, down, targetPos, preferredSupportPos, deadlineMs);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    Object[] tryLegacyPitch(float yaw, float pitch, int[] targetPos, int[] preferredSupportPos, long deadlineMs) {
        if (now() >= deadlineMs) return null;
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] supportPos = (int[]) traced[0];
        int face = (Integer) traced[1];
        if (preferredSupportPos != null && !posEquals(supportPos, preferredSupportPos)) return null;
        if (face == 0) return null;
        if (isReplaceable(supportPos[0], supportPos[1], supportPos[2]) || isInteractable(supportPos[0], supportPos[1], supportPos[2]))
            return null;
        int[] placedPos = offsetPos(supportPos, face);
        if (!posEquals(placedPos, targetPos)) return null;
        return new Object[]{Math.min(pitch, 89.0f), supportPos, face, traced[2], placedPos};
    }

    List<int[]> getMessageStyleBelowTargets(Entity player) {
        double[] offsets = {0.0, 0.29, -0.29};
        Vec3 pos = player.getPosition();
        int maxY = floor(pos.y) - 1;
        int minY = floor(pos.y) - 2;
        List<int[]> targets = new ArrayList<>();
        for (int targetY = maxY; targetY >= minY; targetY--) {
            for (double xOffset : offsets) {
                for (double zOffset : offsets) {
                    targets.add(new int[]{floor(pos.x + xOffset), targetY, floor(pos.z + zOffset)});
                }
            }
        }
        return targets;
    }

    Object[] findBelowPlacementForSupport(Entity player, float yaw, float currentPitch, ItemStack heldStack, int[] preferredSupportPos, int preferredSupportFace, long deadlineMs) {
        boolean diagonal = isDiagonalMovementContext(player);
        for (int[] targetPos : getBelowTargets(player, yaw, currentPitch)) {
            if (now() >= deadlineMs) return null;
            if (!isPlacementTargetAvailable(player, targetPos)) continue;
            if (!isStrictOneBelowPlayer(player, targetPos)) continue;
            Object[] candidate = findPitchPlacementForTarget(player, yaw, currentPitch, targetPos, heldStack, preferredSupportPos, preferredSupportFace, deadlineMs, false, diagonal);
            if (candidate != null) return candidate;
        }
        return null;
    }

    boolean isWithinReach(Entity player, int[] pos) {
        if (pos == null) return false;
        Vec3 eyes = getEyes(player);
        double cx = Math.clamp(eyes.x, pos[0], pos[0] + 1.0);
        double cy = Math.clamp(eyes.y, pos[1], pos[1] + 1.0);
        double cz = Math.clamp(eyes.z, pos[2], pos[2] + 1.0);
        double dx = eyes.x - cx;
        double dy = eyes.y - cy;
        double dz = eyes.z - cz;
        return dx * dx + dy * dy + dz * dz <= reach() * reach();
    }

    Object[] findPitchPlacementForTarget(Entity player, float yaw, float currentPitch, int[] targetPos, ItemStack heldStack, int[] preferredSupportPos, int preferredSupportFace, long deadlineMs, boolean requireLookAlignment, boolean allowNonCursorTarget) {
        if (now() >= deadlineMs || targetPos == null) return null;
        boolean effectiveAllowNonCursorTarget = allowNonCursorTarget || shouldAllowPlayerOneNonCursorTarget(player, targetPos);
        if (!effectiveAllowNonCursorTarget && !isCursorOrBelowPlayerTarget(player, targetPos, yaw, currentPitch))
            return null;
        if (!isPlacementTargetAvailable(player, targetPos)) return null;

        Object[] bestCandidate = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (int placeFace : getAllowedPlaceFacesForContext(player, yaw)) {
            if (now() >= deadlineMs) break;
            if (shouldRejectStraightSideSwitch(player, targetPos, placeFace)) continue;
            int[] supportPos = offsetPos(targetPos, opposite(placeFace));
            if (preferredSupportPos != null && !posEquals(supportPos, preferredSupportPos)) continue;
            if (preferredSupportFace >= 0 && placeFace != preferredSupportFace) continue;
            if (!isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) continue;
            if (!isWithinReach(player, supportPos)) continue;

            double[] hitOffsets = useExtendedSearch() ? EXTENDED_FACE_HIT_OFFSETS : FACE_HIT_OFFSETS;
            for (double primaryOffset : hitOffsets) {
                for (double secondaryOffset : hitOffsets) {
                    if (now() >= deadlineMs) break;
                    Vec3 hitVec = getSupportFaceHitVec(supportPos, placeFace, primaryOffset, secondaryOffset);
                    Object[] candidate = buildPlacementCandidateForHitVec(player, yaw, targetPos, supportPos, placeFace, hitVec, requireLookAlignment, effectiveAllowNonCursorTarget);
                    if (candidate == null) continue;
                    double candidateScore = scorePlacementCandidate(player, currentPitch, candidatePitch(candidate), placeFace, primaryOffset, secondaryOffset);
                    if (candidateScore < bestScore) {
                        bestScore = candidateScore;
                        bestCandidate = candidate;
                    }
                }
            }
        }
        if (bestCandidate == null && preferredSupportPos != null && preferredSupportFace >= 0) {
            return findRayAlignedPitchCandidate(yaw, currentPitch, targetPos, preferredSupportPos, preferredSupportFace, deadlineMs);
        }
        return bestCandidate;
    }

    Object[] findRayAlignedPitchCandidate(float yaw, float currentPitch, int[] targetPos, int[] supportPos, int placeFace, long deadlineMs) {
        float clampedBasePitch = clampFloat(currentPitch, 40.0f, 89.0f);
        for (int offset = 0; offset <= 49; offset++) {
            if (now() >= deadlineMs) return null;
            float upPitch = clampedBasePitch + offset;
            if (upPitch <= 89.0f) {
                Object[] candidate = tryRayAlignedPitch(yaw, upPitch, targetPos, supportPos, placeFace);
                if (candidate != null) return candidate;
            }
            if (offset == 0) continue;
            float downPitch = clampedBasePitch - offset;
            if (downPitch >= 40.0f) {
                Object[] candidate = tryRayAlignedPitch(yaw, downPitch, targetPos, supportPos, placeFace);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    Object[] tryRayAlignedPitch(float yaw, float pitch, int[] targetPos, int[] supportPos, int placeFace) {
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] tracedSupport = (int[]) traced[0];
        int tracedFace = (Integer) traced[1];
        if (!posEquals(tracedSupport, supportPos) || tracedFace != placeFace) return null;
        int[] tracedPlaced = offsetPos(tracedSupport, tracedFace);
        if (!posEquals(tracedPlaced, targetPos)) return null;
        return new Object[]{pitch, tracedSupport, tracedFace, traced[2], tracedPlaced};
    }

    double scorePlacementCandidate(Entity player, float currentPitch, float candidatePitchValue, int placeFace, double primaryOffset, double secondaryOffset) {
        double pitchPenalty = Math.abs(wrapAngle(candidatePitchValue - currentPitch));
        double centerPenalty = Math.abs(primaryOffset - 0.5) + Math.abs(secondaryOffset - 0.5);
        double facePenalty = placeFace == 1 ? 0.0 : 0.35;
        double straightSidePenalty = getStraightSideSwitchPenalty(player, placeFace);
        return pitchPenalty + centerPenalty * 2.0 + facePenalty + straightSidePenalty;
    }

    double getStraightSideSwitchPenalty(Entity player, int placeFace) {
        if (getConditionModeCheck(player) != 1) return 0.0;
        if (lastSupportFace < 2) return 0.0;
        if (placeFace == lastSupportFace) return 0.0;
        return 0.8;
    }

    boolean shouldRejectStraightSideSwitch(Entity player, int[] targetPos, int placeFace) {
        if (targetPos == null || getConditionModeCheck(player) != 1) return false;
        if (placeFace < 2) return false;
        if (lastSupportFace < 2) return false;
        if (placeFace == lastSupportFace) return false;
        if (isNearStraightSupportEdge(player)) return false;
        int[] laneSupportPos = offsetPos(targetPos, opposite(lastSupportFace));
        return isSupportAvailable(laneSupportPos[0], laneSupportPos[1], laneSupportPos[2]) && isWithinReach(player, laneSupportPos);
    }

    Object[] buildPlacementCandidateForHitVec(Entity player, float yaw, int[] targetPos, int[] supportPos, int placeFace, Vec3 hitVec, boolean requireLookAlignment, boolean allowNonCursorTarget) {
        if (hitVec == null) return null;
        int[] offsetTarget = offsetPos(supportPos, placeFace);
        if (!posEquals(offsetTarget, targetPos)) return null;
        if (!isStrictOneBelowPlayer(player, offsetTarget)) return null;
        Float pitch = computePitchToHitVec(player, hitVec);
        if (pitch == null) return null;
        if (!isPlacementLookAligned(yaw, pitch, supportPos, placeFace, targetPos)) return null;
        if (!(allowNonCursorTarget || isDiagonalMovementContext(player) || isSupportFaceVisible(player, supportPos, placeFace, hitVec)))
            return null;
        return new Object[]{pitch, supportPos, placeFace, hitVec, offsetTarget};
    }

    int[] getAllowedPlaceFacesForContext(Entity player, float yaw) {
        if (getConditionModeCheck(player) != 1) return ALLOWED_PLACE_FACES;
        int forward = getStraightForwardFacing(player, yaw);
        if (useExtendedSearch()) {
            return new int[]{rotateY(forward), rotateYCCW(forward), forward, opposite(forward), 1};
        }
        return new int[]{rotateY(forward), rotateYCCW(forward), forward, opposite(forward)};
    }

    boolean isPlacementLookAligned(float yaw, float pitch, int[] supportPos, int placeFace, int[] targetPos) {
        if (supportPos == null || placeFace < 0 || targetPos == null) return false;
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return false;
        if (!posEquals((int[]) traced[0], supportPos) || (Integer) traced[1] != placeFace) return false;
        int[] tracedOffset = offsetPos((int[]) traced[0], (Integer) traced[1]);
        return posEquals(tracedOffset, targetPos);
    }

    boolean isSupportFaceVisible(Entity player, int[] supportPos, int placeFace, Vec3 hitVec) {
        Vec3 eyes = getEyes(player);
        double dx = hitVec.x - eyes.x;
        double dy = hitVec.y - eyes.y;
        double dz = hitVec.z - eyes.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 1.0E-4) return false;
        float traceYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float tracePitch = (float) (-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
        Object[] traced = raycastBlock(distance + 0.5, traceYaw, tracePitch);
        if (traced == null) return false;
        int[] tracedPos = posFromVec((Vec3) traced[0]);
        int tracedFace = faceFromName((String) traced[2]);
        return posEquals(tracedPos, supportPos) && tracedFace == placeFace;
    }

    Vec3 getSupportFaceHitVec(int[] supportPos, int placeFace, double primaryOffset, double secondaryOffset) {
        double primary = Math.clamp(primaryOffset, 0.001, 0.999);
        double secondary = Math.clamp(secondaryOffset, 0.001, 0.999);
        if (placeFace == 2) return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.001);
        if (placeFace == 3) return new Vec3(supportPos[0] + primary, supportPos[1] + secondary, supportPos[2] + 0.999);
        if (placeFace == 5) return new Vec3(supportPos[0] + 0.999, supportPos[1] + primary, supportPos[2] + secondary);
        if (placeFace == 4) return new Vec3(supportPos[0] + 0.001, supportPos[1] + primary, supportPos[2] + secondary);
        if (placeFace == 0) return new Vec3(supportPos[0] + primary, supportPos[1] + 0.001, supportPos[2] + secondary);
        return new Vec3(supportPos[0] + primary, supportPos[1] + 0.999, supportPos[2] + secondary);
    }

    Float computePitchToHitVec(Entity player, Vec3 hitVec) {
        Vec3 eyes = getEyes(player);
        double dx = hitVec.x - eyes.x;
        double dz = hitVec.z - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double dy = hitVec.y - eyes.y;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));
        return Math.clamp(pitch, -89.0f, 89.0f);
    }

    List<int[]> getBelowTargets(Entity player, float yaw, float pitch) {
        if (cachedBelowTargetsTick == currentClientTick && cachedBelowTargets != null) return cachedBelowTargets;
        List<int[]> belowTargets = new ArrayList<>();
        boolean diagonal = isDiagonalMovementContext(player);
        if (!diagonal) {
            int currentY = getCurrentBelowTargetY(player);
            addBelowTarget(player, belowTargets, getCursorStartTargetAtY(player, yaw, pitch, currentY));
            if (belowTargets.isEmpty()) {
                int strictY = getStrictBelowTargetY(player);
                if (strictY != currentY)
                    addBelowTarget(player, belowTargets, getCursorStartTargetAtY(player, yaw, pitch, strictY));
            }
            if (belowTargets.isEmpty())
                addBelowTarget(player, belowTargets, getCursorPlacedTargetFromRay(yaw, pitch, currentY));
            if (belowTargets.isEmpty()) {
                int strictY = getStrictBelowTargetY(player);
                if (strictY != currentY)
                    addBelowTarget(player, belowTargets, getCursorPlacedTargetFromRay(yaw, pitch, strictY));
            }
            if (belowTargets.isEmpty())
                addBelowTarget(player, belowTargets, getCursorTargetAtY(player, yaw, pitch, currentY));
        } else {
            int currentY = getCurrentBelowTargetY(player);
            addBelowTarget(player, belowTargets, getMotionBelowTargetAtY(player, currentY, 1.0));
            addBelowTarget(player, belowTargets, getMotionBelowTargetAtY(player, currentY, 1.7));
            for (int[] endpoint : getBelowPlayerFallbackEndpoints(player, yaw, pitch, currentY)) {
                addBelowTarget(player, belowTargets, endpoint);
            }
        }
        cachedBelowTargets = belowTargets;
        cachedBelowTargetsTick = currentClientTick;
        return belowTargets;
    }

    boolean isCursorOrBelowPlayerTarget(Entity player, int[] targetPos, float yaw, float pitch) {
        if (targetPos == null) return false;
        if (!isDiagonalMovementContext(player)) {
            int currentY = getCurrentBelowTargetY(player);
            if (posEquals(getCursorStartTargetAtY(player, yaw, pitch, currentY), targetPos)) return true;
            if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, currentY), targetPos)) return true;
            int strictY = getStrictBelowTargetY(player);
            if (strictY != currentY) {
                if (posEquals(getCursorStartTargetAtY(player, yaw, pitch, strictY), targetPos)) return true;
                if (posEquals(getCursorPlacedTargetFromRay(yaw, pitch, strictY), targetPos)) return true;
            }
            if (isCursorInsideTargetAtY(player, targetPos, yaw, pitch, currentY)) return true;
            return posEquals(getCursorTargetAtY(player, yaw, pitch, currentY), targetPos);
        }
        int strictY = getStrictBelowTargetY(player);
        if (isBelowPlayerTargetAtY(player, targetPos, strictY, yaw, pitch)) return true;
        return isBelowPlayerTargetAtY(player, targetPos, getCurrentBelowTargetY(player), yaw, pitch);
    }

    boolean isBelowPlayerTargetAtY(Entity player, int[] targetPos, int targetY, float yaw, float pitch) {
        for (int[] candidate : getBelowPlayerFallbackEndpoints(player, yaw, pitch, targetY)) {
            if (posEquals(targetPos, candidate)) return true;
        }
        return false;
    }

    int[] getFeetBelowTargetAtY(Entity player, int targetY) {
        Vec3 pos = player.getPosition();
        return new int[]{floor(pos.x), targetY, floor(pos.z)};
    }

    boolean shouldAllowPlayerOneNonCursorTarget(Entity player, int[] targetPos) {
        if (targetPos == null) return false;
        if (isDiagonalMovementContext(player) || player.onGround()) return false;
        if (!isPlayerHitboxFullyInsideSingleBlockColumn(player)) return false;
        if (!hasValidLastSupportFace(player) || lastSupportFace == 0) return false;
        int[] continuationTarget = offsetPos(lastSupportPos, lastSupportFace);
        if (!posEquals(targetPos, continuationTarget)) return false;
        int targetY = targetPos[1];
        int currentY = getCurrentBelowTargetY(player);
        int strictY = getStrictBelowTargetY(player);
        if (targetY != currentY && targetY != strictY) return false;
        int[] feetBelow = getFeetBelowTargetAtY(player, targetY);
        int horizontalDistance = Math.abs(targetPos[0] - feetBelow[0]) + Math.abs(targetPos[2] - feetBelow[2]);
        return horizontalDistance <= 1;
    }

    boolean isPlayerHitboxFullyInsideSingleBlockColumn(Entity player) {
        Vec3 pos = player.getPosition();
        double half = player.getWidth() / 2.0;
        int minX = floor(pos.x - half + 1.0E-4);
        int maxX = floor(pos.x + half - 1.0E-4);
        if (minX != maxX) return false;
        int minZ = floor(pos.z - half + 1.0E-4);
        int maxZ = floor(pos.z + half - 1.0E-4);
        return minZ == maxZ;
    }

    int[] getMotionBelowTargetAtY(Entity player, int targetY, double multiplier) {
        Vec3 pos = player.getPosition();
        Vec3 motion = getPlayerMotion();
        return new int[]{floor(pos.x + motion.x * multiplier), targetY, floor(pos.z + motion.z * multiplier)};
    }

    boolean hasDirectSupportNeighbor(int[] targetPos) {
        for (int placeFace : ALLOWED_PLACE_FACES) {
            int[] supportPos = offsetPos(targetPos, opposite(placeFace));
            if (isSupportAvailable(supportPos[0], supportPos[1], supportPos[2])) return true;
        }
        return false;
    }

    void addBelowTargetIfUnique(Entity player, List<int[]> targets, int[] candidate) {
        if (candidate == null) return;
        if (!isStrictOneBelowPlayer(player, candidate)) return;
        for (int[] existing : targets) {
            if (posEquals(existing, candidate)) return;
        }
        targets.add(candidate);
    }

    void addBelowTarget(Entity player, List<int[]> targets, int[] candidate) {
        addBelowTargetIfUnique(player, targets, candidate);
    }

    List<int[]> rasterizeHorizontalLineAtY(int[] start, int[] end, int y, int maxSteps) {
        List<int[]> line = new ArrayList<>();
        int x0 = start[0];
        int z0 = start[2];
        int x1 = end[0];
        int z1 = end[2];
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = Integer.compare(x1, x0);
        int sz = Integer.compare(z1, z0);
        int movedX = 0;
        int movedZ = 0;
        for (int steps = 0; steps < maxSteps; steps++) {
            line.add(new int[]{x0, y, z0});
            if ((x0 == x1 && z0 == z1) || (movedX >= dx && movedZ >= dz)) break;
            if (movedX >= dx) {
                z0 += sz;
                movedZ++;
            } else if (movedZ >= dz) {
                x0 += sx;
                movedX++;
            } else if ((1 + 2 * movedX) * dz < (1 + 2 * movedZ) * dx) {
                x0 += sx;
                movedX++;
            } else {
                z0 += sz;
                movedZ++;
            }
        }
        return line;
    }

    int getDetectedModeCheck(Entity player) {
        float forwardInput = Math.abs(getForward());
        float strafeInput = Math.abs(getStrafe());
        if (forwardInput >= 0.08f || strafeInput >= 0.08f) {
            return (forwardInput >= 0.08f && strafeInput >= 0.08f) ? 1 : 2;
        }
        double[] direction = getMotionDirectionComponents(player);
        if (direction == null) return 1;
        double angleDeg = Math.toDegrees(Math.atan2(direction[1], direction[0]));
        double norm90 = (angleDeg % 90.0 + 90.0) % 90.0;
        return Math.abs(norm90 - 45.0) <= 18.0 ? 2 : 1;
    }

    double[] getMotionDirectionComponents(Entity player) {
        Vec3 pos = player.getPosition();
        Vec3 last = player.getLastPosition();
        double dirX = pos.x - last.x;
        double dirZ = pos.z - last.z;
        double speedSq = dirX * dirX + dirZ * dirZ;
        if (speedSq < 1.0E-4) {
            Vec3 motion = getPlayerMotion();
            dirX = motion.x;
            dirZ = motion.z;
            speedSq = dirX * dirX + dirZ * dirZ;
        }
        if (speedSq < 1.0E-4) return null;
        return new double[]{dirX, dirZ};
    }

    double[] getInputDirectionComponents(float referenceYaw) {
        float forwardInput = getForward();
        float strafeInput = getStrafe();
        if (Math.abs(forwardInput) < 0.08f && Math.abs(strafeInput) < 0.08f) return null;
        double yawRadians = Math.toRadians(referenceYaw);
        double sinYaw = Math.sin(yawRadians);
        double cosYaw = Math.cos(yawRadians);
        double dirX = forwardInput * -sinYaw + strafeInput * cosYaw;
        double dirZ = forwardInput * cosYaw - strafeInput * sinYaw;
        if (dirX * dirX + dirZ * dirZ < 1.0E-4) return null;
        return new double[]{dirX, dirZ};
    }

    int getStraightForwardFacing(Entity player, float fallbackYaw) {
        double[] direction = getInputDirectionComponents(fallbackYaw);
        if (direction == null) direction = getMotionDirectionComponents(player);
        if (direction == null) return facingFromYaw(fallbackYaw);
        float directionYaw = (float) (Math.toDegrees(Math.atan2(direction[1], direction[0])) - 90.0);
        return facingFromYaw(directionYaw);
    }

    int getConditionModeCheck(Entity player) {
        if (forcedModeCheck != 0) return forcedModeCheck;
        // While running, straight telly is already a diagonal bridge; forward-only input can be misclassified.
        // Keep it on mode 1 so isStraightTellyTarget does not reject the intended path.
        if (running) return 1;
        return getDetectedModeCheck(player);
    }

    boolean isDiagonalMovementContext(Entity player) {
        return getConditionModeCheck(player) == 2;
    }

    int[] getCursorPlacedTargetFromRay(float yaw, float pitch, int targetY) {
        Object[] traced = rayCast(yaw, pitch);
        if (traced == null) return null;
        int[] offsetTarget = offsetPos((int[]) traced[0], (Integer) traced[1]);
        if (offsetTarget[1] != targetY) return null;
        return offsetTarget;
    }

    int[] getCursorStartTargetAtY(Entity player, float fallbackYaw, float fallbackPitch, int targetY) {
        Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
        Vec3 lookVec = getCursorLookVec(player);
        if (cursorPoint == null || lookVec == null) return null;
        double startX = cursorPoint.x - lookVec.x * 0.03;
        double startZ = cursorPoint.z - lookVec.z * 0.03;
        return new int[]{floor(startX), targetY, floor(startZ)};
    }

    int[] getCursorTargetAtY(Entity player, float fallbackYaw, float fallbackPitch, int targetY) {
        Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
        if (cursorPoint == null) return null;
        return new int[]{floor(cursorPoint.x), targetY, floor(cursorPoint.z)};
    }

    Vec3 getCursorIntersectionAtY(Entity player, int targetY) {
        Vec3 eyes = getEyes(player);
        Vec3 lookVec = getCursorLookVec(player);
        if (lookVec == null || Math.abs(lookVec.y) < 1.0E-4) return null;
        double t = (targetY - eyes.y) / lookVec.y;
        if (t <= 0.0) return null;
        return new Vec3(eyes.x + lookVec.x * t, targetY + 0.5, eyes.z + lookVec.z * t);
    }

    Vec3 getCursorLookVec(Entity player) {
        double[] cameraRotations = getPlayerRotations();
        if (cameraRotations != null && cameraRotations.length >= 2) {
            return getLookVec((float) cameraRotations[0], (float) cameraRotations[1]);
        }
        return getLookVec(player.getYaw(), player.getPitch());
    }

    Vec3 getLookVec(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double cosPitch = Math.cos(pitchRad);
        return new Vec3(-Math.sin(yawRad) * cosPitch, -Math.sin(pitchRad), Math.cos(yawRad) * cosPitch);
    }

    boolean isCursorInsideTargetAtY(Entity player, int[] targetPos, float yaw, float pitch, int targetY) {
        if (targetPos == null || targetPos[1] != targetY) return false;
        Vec3 cursorPoint = getCursorIntersectionAtY(player, targetY);
        if (cursorPoint == null) return false;
        double x = cursorPoint.x;
        double z = cursorPoint.z;
        return x >= targetPos[0] - 1.0E-6 && x <= targetPos[0] + 1.0 + 1.0E-6 && z >= targetPos[2] - 1.0E-6 && z <= targetPos[2] + 1.0 + 1.0E-6;
    }

    boolean isPlacementTargetAvailable(Entity player, int[] pos) {
        return isBasePlacementTargetAvailable(player, pos) && isStrictOneBelowPlayer(player, pos);
    }

    boolean isStraightLaneTargetAvailable(Entity player, int[] pos, int currentY, int strictY, int previousY, int upwardY) {
        if (!isBasePlacementTargetAvailable(player, pos)) return false;
        int targetY = pos[1];
        if (targetY == currentY || targetY == strictY) return true;
        if (previousY != -2147483648 && targetY == previousY) return true;
        return upwardY != -2147483648 && targetY == upwardY;
    }

    boolean isBasePlacementTargetAvailable(Entity player, int[] pos) {
        return pos != null
                && isStraightTellyTarget(pos)
                && !isRejectedTarget(pos)
                && !doesPlacementIntersectPlayer(player, pos)
                && isReplaceable(pos[0], pos[1], pos[2]);
    }

    boolean doesPlacementIntersectPlayer(Entity player, int[] placePos) {
        if (placePos == null) return false;
        if (isInsideAnyPlayerPositionCell(player, placePos)) return true;

        Vec3 pos = player.getPosition();
        double half = player.getWidth() / 2.0;
        double height = player.getHeight();
        if (boxIntersectsBlock(pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + height, pos.z + half, placePos))
            return true;
        if (isBlockPosInsideBounds(placePos, pos.x - half, pos.y, pos.z - half, pos.x + half, pos.y + height, pos.z + half))
            return true;

        if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;

        Vec3 last = player.getLastPosition();
        if (last.x != pos.x || last.y != pos.y || last.z != pos.z) {
            if (boxIntersectsBlock(last.x - half, last.y, last.z - half, last.x + half, last.y + height, last.z + half, placePos))
                return true;
            if (isBlockPosInsideBounds(placePos, last.x - half, last.y, last.z - half, last.x + half, last.y + height, last.z + half))
                return true;
        }
        if (hasLastSentServerPos && (lastSentServerPosX != pos.x || lastSentServerPosY != pos.y || lastSentServerPosZ != pos.z)) {
            double sx = lastSentServerPosX;
            double sy = lastSentServerPosY;
            double sz = lastSentServerPosZ;
            if (boxIntersectsBlock(sx - half, sy, sz - half, sx + half, sy + height, sz + half, placePos)) return true;
            return isBlockPosInsideBounds(placePos, sx - half, sy, sz - half, sx + half, sy + height, sz + half);
        }
        return false;
    }

    boolean boxIntersectsBlock(double minX, double minY, double minZ, double maxX, double maxY, double maxZ, int[] pos) {
        return maxX > pos[0] && minX < pos[0] + 1.0 && maxY > pos[1] && minY < pos[1] + 1.0 && maxZ > pos[2] && minZ < pos[2] + 1.0;
    }

    boolean isBlockPosInsideBounds(int[] pos, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int bMinX = floor(minX + 1.0E-4);
        int bMaxX = floor(maxX - 1.0E-4);
        if (pos[0] < bMinX || pos[0] > bMaxX) return false;
        int bMinZ = floor(minZ + 1.0E-4);
        int bMaxZ = floor(maxZ - 1.0E-4);
        if (pos[2] < bMinZ || pos[2] > bMaxZ) return false;
        int bMinY = floor(minY + 1.0E-4);
        int bMaxY = floor(maxY - 1.0E-4);
        return pos[1] >= bMinY && pos[1] <= bMaxY;
    }

    boolean isInsideAnyPlayerPositionCell(Entity player, int[] placePos) {
        Vec3 pos = player.getPosition();
        if (isInsidePlayerPositionCell(placePos, pos.x, pos.y, pos.z)) return true;
        if (!shouldUseHistoricalPlayerCollisionChecks(player, placePos)) return false;
        Vec3 last = player.getLastPosition();
        if (isInsidePlayerPositionCell(placePos, last.x, last.y, last.z)) return true;
        return hasLastSentServerPos && isInsidePlayerPositionCell(placePos, lastSentServerPosX, lastSentServerPosY, lastSentServerPosZ);
    }

    boolean shouldUseHistoricalPlayerCollisionChecks(Entity player, int[] placePos) {
        if (!player.onGround()) return false;
        if (placePos == null) return true;
        return placePos[1] > getCurrentBelowTargetY(player);
    }

    boolean isInsidePlayerPositionCell(int[] placePos, double x, double y, double z) {
        int playerX = floor(x);
        int playerY = floor(y);
        int playerZ = floor(z);
        return placePos[0] == playerX && placePos[2] == playerZ && (placePos[1] == playerY || placePos[1] == playerY + 1);
    }

    boolean isStrictOneBelowPlayer(Entity player, int[] pos) {
        if (pos == null) return false;
        int targetY = pos[1];
        int currentY = getCurrentBelowTargetY(player);
        if (targetY == currentY) return true;
        if (targetY == getStrictBelowTargetY(player)) return true;
        int previousY = getPreviousBelowTargetY(player);
        if (previousY != -2147483648 && targetY == previousY) return true;
        return isStraightAscendingContext(player) && targetY == currentY + 1;
    }

    double getStableBelowReferenceY(Entity player) {
        Vec3 pos = player.getPosition();
        double referenceY = pos.y;
        Vec3 motion = getPlayerMotion();
        if (!player.onGround() && motion.y > -0.12 && motion.y <= 0.0) {
            referenceY = Math.max(referenceY, player.getLastPosition().y);
        }
        return referenceY;
    }

    int getStrictBelowTargetY(Entity player) {
        if (isDiagonalMovementContext(player)) return getCurrentBelowTargetY(player);
        double projectedY = getStableBelowReferenceY(player);
        Vec3 motion = getPlayerMotion();
        if (!player.onGround() && motion.y < -0.12) {
            projectedY = player.getPosition().y + motion.y * 0.75;
        }
        return floor(projectedY) - 1;
    }

    int getCurrentBelowTargetY(Entity player) {
        return floor(getStableBelowReferenceY(player)) - 1;
    }

    int getPreviousBelowTargetY(Entity player) {
        return floor(player.getLastPosition().y) - 1;
    }

    boolean isStraightAscendingContext(Entity player) {
        if (getConditionModeCheck(player) != 1) return false;
        Vec3 motion = getPlayerMotion();
        return motion.y > 0.0 || player.getPosition().y > player.getLastPosition().y + 1.0E-4;
    }

    boolean isSupportAvailable(int x, int y, int z) {
        if (isInteractable(x, y, z)) return false;
        return !isReplaceable(x, y, z);
    }

    boolean isRejectedTarget(int[] pos) {
        Integer rejectedAtTick = rejectedTargets.get(posKey(pos));
        if (rejectedAtTick == null) return false;
        return currentClientTick - rejectedAtTick <= 4;
    }

    void markRejectedTarget(int[] pos) {
        if (pos == null) return;
        rejectedTargets.put(posKey(pos), currentClientTick);
    }

    void pruneRejectedTargets() {
        if (rejectedTargets.isEmpty()) return;
        Iterator<Map.Entry<String, Integer>> iterator = rejectedTargets.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Integer> entry = iterator.next();
            if (currentClientTick - entry.getValue() > 4) {
                iterator.remove();
                continue;
            }
            String[] parts = entry.getKey().split(",");
            if (!isReplaceable(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]))) {
                iterator.remove();
            }
        }
    }

    Object[] rayCast(float yaw, float pitch) {
        Object[] hit = raycastBlock(reach(), yaw, pitch);
        if (hit == null || hit[0] == null || hit[2] == null) return null;
        int face = faceFromName((String) hit[2]);
        if (face < 0 || face == 0) return null;
        int[] supportPos = posFromVec((Vec3) hit[0]);
        Vec3 offset = (Vec3) hit[1];
        Vec3 hitAbs = new Vec3(supportPos[0] + offset.x, supportPos[1] + offset.y, supportPos[2] + offset.z);
        return new Object[]{supportPos, face, hitAbs};
    }

    Vec3 getEyes(Entity player) {
        Vec3 pos = player.getPosition();
        return new Vec3(pos.x, pos.y + player.getEyeHeight(), pos.z);
    }

    String blockNameAt(int x, int y, int z) {
        if (mc.theWorld == null) return "air";
        net.minecraft.block.Block block = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
        String name = block == null ? null : stripMinecraftPrefix(block.getRegistryName());
        return name == null ? "air" : name.toLowerCase();
    }

    boolean isReplaceable(int x, int y, int z) {
        return isReplaceableName(blockNameAt(x, y, z), false);
    }

    boolean isReplaceableName(String name, boolean airOnly) {
        if (airOnly) return name.equals("air");
        for (String replaceable : REPLACEABLE_BLOCKS) {
            if (name.equals(replaceable)) return true;
        }
        for (String replaceable : EXPERIMENTAL_REPLACEABLE_BLOCKS) {
            if (name.equals(replaceable)) return true;
        }
        return false;
    }

    boolean isInteractable(int x, int y, int z) {
        if (mc.theWorld == null) return false;
        net.minecraft.block.Block block = mc.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock();
        if (block == null) return false;
        if (BlockUtil.isInteractable(block)) return true;
        String type = block.getClass().getSimpleName();
        if (type == null) return false;
        for (String interactableType : INTERACTABLE_TYPES) {
            if (type.equals(interactableType)) return true;
        }
        return false;
    }

    double reach() {
        return isCreative() ? 5.0 : 4.5;
    }

    int placementTick(Entity player) {
        if (isRavenTimerActive()) return (int) (now() / 50L);
        return player.getTicksExisted();
    }

    boolean isRavenTimerActive() {
        try {
            return isModuleEnabled("Timer");
        } catch (Exception ignored) {
            return false;
        }
    }

    float candidatePitch(Object[] candidate) {
        return clampFloat((Float) candidate[0], -90.0f, 90.0f);
    }

    int[] candidateSupportPos(Object[] candidate) {
        return (int[]) candidate[1];
    }

    int candidateFace(Object[] candidate) {
        return (Integer) candidate[2];
    }

    Vec3 candidateHitVec(Object[] candidate) {
        return (Vec3) candidate[3];
    }

    int[] candidatePlacedPos(Object[] candidate) {
        return (int[]) candidate[4];
    }

    float sanitizePitch(float pitch, float fallbackPitch) {
        float safeFallback = clampFloat(Float.isNaN(fallbackPitch) ? 0.0f : fallbackPitch, -90.0f, 90.0f);
        if (Float.isNaN(pitch) || Float.isInfinite(pitch)) return safeFallback;
        return clampFloat(pitch, -90.0f, 90.0f);
    }

    int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    float clampFloat(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    float wrapAngle(float angle) {
        angle = angle % 360f;
        if (angle >= 180f) angle -= 360f;
        if (angle < -180f) angle += 360f;
        return angle;
    }

    double distSq(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    boolean posEquals(int[] a, int[] b) {
        return a != null && b != null && a[0] == b[0] && a[1] == b[1] && a[2] == b[2];
    }

    String posKey(int[] pos) {
        return pos[0] + "," + pos[1] + "," + pos[2];
    }

    int[] posFromVec(Vec3 vec) {
        return new int[]{floor(vec.x), floor(vec.y), floor(vec.z)};
    }

    int[] offsetPos(int[] pos, int face) {
        if (face == 0) return new int[]{pos[0], pos[1] - 1, pos[2]};
        if (face == 1) return new int[]{pos[0], pos[1] + 1, pos[2]};
        if (face == 2) return new int[]{pos[0], pos[1], pos[2] - 1};
        if (face == 3) return new int[]{pos[0], pos[1], pos[2] + 1};
        if (face == 4) return new int[]{pos[0] - 1, pos[1], pos[2]};
        return new int[]{pos[0] + 1, pos[1], pos[2]};
    }

    int opposite(int face) {
        if (face == 0) return 1;
        if (face == 1) return 0;
        if (face == 2) return 3;
        if (face == 3) return 2;
        if (face == 4) return 5;
        return 4;
    }

    int rotateY(int face) {
        if (face == 2) return 5;
        if (face == 5) return 3;
        if (face == 3) return 4;
        if (face == 4) return 2;
        return face;
    }

    int rotateYCCW(int face) {
        if (face == 2) return 4;
        if (face == 4) return 3;
        if (face == 3) return 5;
        if (face == 5) return 2;
        return face;
    }

    int facingFromYaw(float yaw) {
        int index = floor(yaw / 90.0 + 0.5) & 3;
        if (index == 0) return 3;
        if (index == 1) return 4;
        if (index == 2) return 2;
        return 5;
    }

    String faceName(int face) {
        if (face == 0) return "DOWN";
        if (face == 1) return "UP";
        if (face == 2) return "NORTH";
        if (face == 3) return "SOUTH";
        if (face == 4) return "WEST";
        return "EAST";
    }

    int faceFromName(String name) {
        if (name == null) return -1;
        String upper = name.toUpperCase();
        if (upper.equals("DOWN")) return 0;
        if (upper.equals("UP")) return 1;
        if (upper.equals("NORTH")) return 2;
        if (upper.equals("SOUTH")) return 3;
        if (upper.equals("WEST")) return 4;
        if (upper.equals("EAST")) return 5;
        return -1;
    }

    void bridgePut(String key) {
        BRIDGE_VALUES.put(key, null);
    }

    void bridgePut(String key, Object value) {
        BRIDGE_VALUES.put(key, value);
    }

    void bridgeRemove(String key) {
        BRIDGE_VALUES.remove(key);
    }

    boolean bridgeHas(String key) {
        return BRIDGE_VALUES.containsKey(key);
    }

    Object bridgeGet(String key) {
        return BRIDGE_VALUES.get(key);
    }

    int[] getDisplaySize() {
        ScaledResolution scaled = new ScaledResolution(mc);
        return new int[]{scaled.getScaledWidth(), scaled.getScaledHeight(), scaled.getScaleFactor()};
    }

    int getFontWidth(String text) {
        return mc.fontRendererObj == null ? 0 : mc.fontRendererObj.getStringWidth(text);
    }

    void drawText(String text, float x, float y, float scale, int color, boolean shadow) {
        if (mc.fontRendererObj == null) return;
        GlStateManager.pushMatrix();
        if (scale != 1.0f) GlStateManager.scale(scale, scale, scale);
        mc.fontRendererObj.drawString(text, x / scale, y / scale, color, shadow);
        GlStateManager.popMatrix();
    }

    Vec3 getRenderPosition() {
        if (mc.getRenderManager() != null) {
            return new Vec3(mc.getRenderManager().viewerPosX, mc.getRenderManager().viewerPosY, mc.getRenderManager().viewerPosZ);
        }
        if (mc.getRenderViewEntity() == null) return new Vec3(0.0, 0.0, 0.0);
        net.minecraft.entity.Entity entity = mc.getRenderViewEntity();
        float partial = 1.0f;
        try {
            partial = mc.timer.renderPartialTicks;
        } catch (Throwable ignored) {
        }
        return new Vec3(entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partial,
                entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partial,
                entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partial);
    }

    double[] getPlayerRotations() {
        if (mc.thePlayer == null) return null;
        return new double[]{mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch};
    }

    private Entity getPlayer() {
        return mc.thePlayer == null ? null : Entity.convert(mc.thePlayer);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private boolean isCreative() {
        return mc.thePlayer != null && mc.thePlayer.capabilities != null && mc.thePlayer.capabilities.isCreativeMode;
    }

    private boolean isSneaking() {
        return mc.thePlayer != null && mc.thePlayer.movementInput != null && mc.thePlayer.movementInput.sneak;
    }

    private void setSneak(boolean sneak) {
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) mc.thePlayer.movementInput.sneak = sneak;
    }

    private void setJump(boolean jump) {
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) mc.thePlayer.movementInput.jump = jump;
    }

    private float getForward() {
        return mc.thePlayer == null || mc.thePlayer.movementInput == null ? 0.0f : mc.thePlayer.movementInput.moveForward;
    }

    private void setForward(float forward) {
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null)
            mc.thePlayer.movementInput.moveForward = forward;
    }

    private float getStrafe() {
        return mc.thePlayer == null || mc.thePlayer.movementInput == null ? 0.0f : mc.thePlayer.movementInput.moveStrafe;
    }

    private void setStrafe(float strafe) {
        if (mc.thePlayer != null && mc.thePlayer.movementInput != null) mc.thePlayer.movementInput.moveStrafe = strafe;
    }

    private void setSprinting(boolean sprinting) {
        if (mc.thePlayer != null) mc.thePlayer.setSprinting(sprinting);
    }

    private Vec3 getPlayerMotion() {
        return mc.thePlayer == null ? new Vec3(0.0, 0.0, 0.0) : new Vec3(mc.thePlayer.motionX, mc.thePlayer.motionY, mc.thePlayer.motionZ);
    }

    private boolean isModuleEnabled(String moduleName) {
        Module module = getModule(moduleName);
        return module != null && module.isEnabled();
    }

    private void enableModule(String moduleName) {
        Module module = getModule(moduleName);
        if (module != null) module.setEnabled(true);
    }

    private void disableModule(String moduleName) {
        Module module = getModule(moduleName);
        if (module != null) module.setEnabled(false);
    }

    private Module getModule(String moduleName) {
        if (moduleName == null || Unfair.moduleManager == null) return null;
        return Unfair.moduleManager.getModule(moduleName);
    }

    private Object[] raycastBlock(double distance) {
        if (running) return raycastBlock(distance, scriptedRotationYaw, scriptedRotationPitch);
        return raycastBlock(distance, mc.thePlayer == null ? 0.0f : mc.thePlayer.rotationYaw, mc.thePlayer == null ? 0.0f : mc.thePlayer.rotationPitch);
    }

    private Object[] raycastBlock(double distance, float yaw, float pitch) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        MovingObjectPosition mop = RotationUtil.rayTrace(yaw, pitch, distance, 1.0f);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || mop.getBlockPos() == null)
            return null;
        Vec3 pos = new Vec3(mop.getBlockPos());
        Vec3 offset = new Vec3(mop.hitVec.xCoord - pos.x, mop.hitVec.yCoord - pos.y, mop.hitVec.zCoord - pos.z);
        return new Object[]{pos, offset, mop.sideHit.name()};
    }

    private boolean placeBlock(Vec3 targetPos, String side, Vec3 hitVec) {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null || targetPos == null || side == null || hitVec == null)
            return false;
        EnumFacing facing = enumFacing(side, EnumFacing.UP);
        BlockPos pos = Vec3.getBlockPos(targetPos);
        net.minecraft.util.Vec3 hit = new net.minecraft.util.Vec3(hitVec.x, hitVec.y, hitVec.z);
        return mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), pos, facing, hit);
    }

    private void swing() {
        if (mc.thePlayer == null) return;
        mc.thePlayer.swingItem();
        if (mc.thePlayer.sendQueue != null) mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        swingReset();
    }

    private void swingReset() {
        if (mc.getItemRenderer() != null) mc.getItemRenderer().resetEquippedProgress();
    }

    private ItemStack getWrappedStack(int slot) {
        if (mc.thePlayer == null || slot < 0 || slot >= mc.thePlayer.inventory.getSizeInventory()) return null;
        return ItemStack.convert(mc.thePlayer.inventory.getStackInSlot(slot));
    }

    private boolean isKeyPressed(String key) {
        KeyBinding binding = binding(key);
        return binding != null && binding.isKeyDown();
    }

    private void setKeyPressed(String key, boolean pressed) {
        KeyBinding binding = binding(key);
        if (binding == null) return;
        int code = binding.getKeyCode();
        KeyBinding.setKeyBindState(code, pressed);
        try {
            binding.pressed = pressed;
        } catch (Throwable ignored) {
        }
    }

    private int getKeyCode(String key) {
        KeyBinding binding = binding(key);
        return binding == null ? -1 : binding.getKeyCode();
    }

    private boolean isKeyDown(int keyCode) {
        if (keyCode < 0) return Mouse.isButtonDown(keyCode + 100);
        return Keyboard.isKeyDown(keyCode);
    }

    private KeyBinding binding(String key) {
        if (key == null || mc.gameSettings == null) return null;
        switch (normalize(key)) {
            case "forward":
                return mc.gameSettings.keyBindForward;
            case "back":
                return mc.gameSettings.keyBindBack;
            case "left":
                return mc.gameSettings.keyBindLeft;
            case "right":
                return mc.gameSettings.keyBindRight;
            case "jump":
                return mc.gameSettings.keyBindJump;
            case "sneak":
                return mc.gameSettings.keyBindSneak;
            case "sprint":
                return mc.gameSettings.keyBindSprint;
            case "attack":
                return mc.gameSettings.keyBindAttack;
            case "use":
            case "useitem":
                return mc.gameSettings.keyBindUseItem;
            case "drop":
                return mc.gameSettings.keyBindDrop;
            default:
                return null;
        }
    }

    private static final class Entity {
        final net.minecraft.entity.Entity entity;
        final boolean isUser;

        Entity(net.minecraft.entity.Entity entity) {
            this.entity = entity;
            this.isUser = entity != null && mc.thePlayer != null && entity.getUniqueID().equals(mc.thePlayer.getUniqueID());
        }

        static Entity convert(net.minecraft.entity.Entity entity) {
            return entity == null ? null : new Entity(entity);
        }

        boolean isDead() {
            return entity == null || entity.isDead || (entity instanceof EntityLivingBase && ((EntityLivingBase) entity).deathTime > 0);
        }

        boolean isHoldingBlock() {
            if (!(entity instanceof EntityLivingBase)) return false;
            return ItemUtil.isBlock(((EntityLivingBase) entity).getHeldItem());
        }

        ItemStack getHeldItem() {
            if (entity instanceof EntityItem) return ItemStack.convert(((EntityItem) entity).getEntityItem());
            if (!(entity instanceof EntityLivingBase)) return null;
            return ItemStack.convert(((EntityLivingBase) entity).getHeldItem());
        }

        Vec3 getPosition() {
            return entity == null ? null : new Vec3(entity.posX, entity.posY, entity.posZ);
        }

        Vec3 getLastPosition() {
            return entity == null ? null : new Vec3(entity.lastTickPosX, entity.lastTickPosY, entity.lastTickPosZ);
        }

        float getFallDistance() {
            return entity == null ? 0.0f : entity.fallDistance;
        }

        float getYaw() {
            return entity == null ? 0.0f : entity.rotationYaw;
        }

        void setYaw(float yaw) {
            if (entity != null) entity.rotationYaw = yaw;
        }

        float getPitch() {
            return entity == null ? 0.0f : entity.rotationPitch;
        }

        void setPitch(float pitch) {
            if (entity != null) entity.rotationPitch = pitch;
        }

        int getTicksExisted() {
            return entity == null ? 0 : entity.ticksExisted;
        }

        float getEyeHeight() {
            return entity == null ? 0.0f : entity.getEyeHeight();
        }

        float getHeight() {
            return entity == null ? 0.0f : entity.height;
        }

        float getWidth() {
            return entity == null ? 0.0f : entity.width;
        }

        boolean onGround() {
            return entity != null && entity.onGround;
        }
    }

    private static final class ItemStack {
        final String name;
        final int stackSize;
        final boolean isBlock;

        ItemStack(net.minecraft.item.ItemStack itemStack) {
            if (itemStack == null) {
                this.name = "";
                this.stackSize = 0;
                this.isBlock = false;
                return;
            }
            Item item = itemStack.getItem();
            this.isBlock = item instanceof ItemBlock;
            String registry = item.getRegistryName();
            this.name = stripMinecraftPrefix(registry);
            this.stackSize = itemStack.stackSize;
        }

        static ItemStack convert(net.minecraft.item.ItemStack stack) {
            return stack == null ? null : new ItemStack(stack);
        }
    }

    private static final class Vec3 {
        double x;
        double y;
        double z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3(BlockPos pos) {
            this(pos.getX(), pos.getY(), pos.getZ());
        }

        static Vec3 convert(BlockPos pos) {
            return pos == null ? null : new Vec3(pos);
        }

        static BlockPos getBlockPos(Vec3 vec) {
            return new BlockPos(vec.x, vec.y, vec.z);
        }

        @Override
        public String toString() {
            return "Vec3(" + x + "," + y + "," + z + ")";
        }
    }

    private static class CPacket {
        final String name;

        CPacket(Packet<?> packet) {
            this.name = packet == null ? "" : packet.getClass().getSimpleName();
        }

        static CPacket from(Packet<?> packet) {
            if (packet instanceof C02PacketUseEntity) return new C02((C02PacketUseEntity) packet);
            if (packet instanceof C03PacketPlayer) return new C03((C03PacketPlayer) packet);
            if (packet instanceof C07PacketPlayerDigging) return new C07((C07PacketPlayerDigging) packet);
            if (packet instanceof C08PacketPlayerBlockPlacement) return new C08((C08PacketPlayerBlockPlacement) packet);
            if (packet instanceof C0BPacketEntityAction) return new C0B((C0BPacketEntityAction) packet);
            return packet == null ? null : new CPacket(packet);
        }
    }

    private static final class C02 extends CPacket {
        final String action;

        C02(C02PacketUseEntity packet) {
            super(packet);
            this.action = packet.getAction() == null ? "" : packet.getAction().name();
        }
    }

    private static final class C03 extends CPacket {
        final Vec3 position;
        final boolean moving;

        C03(C03PacketPlayer packet) {
            super(packet);
            this.moving = packet instanceof C03PacketPlayer.C04PacketPlayerPosition || packet instanceof C03PacketPlayer.C06PacketPlayerPosLook;
            this.position = this.moving ? new Vec3(packet.getPositionX(), packet.getPositionY(), packet.getPositionZ()) : null;
        }
    }

    private static final class C07 extends CPacket {
        final String status;

        C07(C07PacketPlayerDigging packet) {
            super(packet);
            this.status = packet.getStatus() == null ? "" : packet.getStatus().name();
        }
    }

    private static final class C08 extends CPacket {
        final ItemStack itemStack;
        final Vec3 position;
        final int direction;

        C08(C08PacketPlayerBlockPlacement packet) {
            super(packet);
            this.itemStack = ItemStack.convert(packet.getStack());
            this.position = Vec3.convert(packet.getPosition());
            this.direction = packet.getPlacedBlockDirection();
        }
    }

    private static final class C0B extends CPacket {
        final String action;

        C0B(C0BPacketEntityAction packet) {
            super(packet);
            this.action = packet.getAction() == null ? "" : packet.getAction().name();
        }
    }

    private static class SPacket {
        final String name;

        SPacket(Packet<?> packet) {
            this.name = packet == null ? "" : packet.getClass().getSimpleName();
        }

        static SPacket from(Packet<?> packet) {
            if (packet instanceof S23PacketBlockChange) return new S23((S23PacketBlockChange) packet);
            return packet == null ? null : new SPacket(packet);
        }
    }

    private static final class S23 extends SPacket {
        final Vec3 position;

        S23(S23PacketBlockChange packet) {
            super(packet);
            this.position = Vec3.convert(packet.getBlockPosition());
        }
    }

    private static final class PlayerState {
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        boolean onGround;
        boolean isSprinting;
        boolean isSneaking;
        boolean rotated;

        PlayerState(UpdateEvent event) {
            if (mc.thePlayer != null) {
                this.x = mc.thePlayer.posX;
                this.y = mc.thePlayer.posY;
                this.z = mc.thePlayer.posZ;
                this.onGround = mc.thePlayer.onGround;
                this.isSprinting = mc.thePlayer.isSprinting();
                this.isSneaking = mc.thePlayer.isSneaking();
            }
            this.yaw = event.getNewYaw();
            this.pitch = event.getNewPitch();
            this.rotated = true;
        }
    }
}