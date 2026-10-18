package cn.unfair.module.modules.world;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LeftClickMouseEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.RightClickMouseEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.client.RandomUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.rotation.RayCastUtil;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BedDefender extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    private static final String RESOURCE = "/assets/minecraft/unfair/beddefender.json";
    private static final double REACH = 4.5;
    private static final double INSET = 0.05;
    private static final double STEP = 0.2;
    private static final double JITTER = 0.08;
    private static final int GRID = (int) Math.round(1.0 / STEP);
    private static final float SETTLE_ANGLE = 25.0F;
    private static final int SEARCH_RANGE = 16;
    private static final int ROTATION_PRIORITY = 6;

    private static final EnumFacing[] FACE_ORDER = {
            EnumFacing.UP, EnumFacing.DOWN, EnumFacing.NORTH,
            EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST
    };

    private static final class Step {
        final String block;
        final int x;
        final int y;
        final int z;

        Step(String block, int x, int y, int z) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static final Map<String, List<Step>> PATTERNS = load();
    private static final String[] PATTERN_NAMES =
            PATTERNS.keySet().toArray(new String[PATTERNS.size()]);

    private static Map<String, List<Step>> load() {
        Map<String, List<Step>> out = new LinkedHashMap<String, List<Step>>();
        try (InputStream in = BedDefender.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return out;
            }
            JsonObject root = new JsonParser()
                    .parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                List<Step> steps = new ArrayList<Step>();
                JsonArray array = entry.getValue().getAsJsonArray();
                for (JsonElement element : array) {
                    JsonObject step = element.getAsJsonObject();
                    String block = step.get("block").getAsString();
                    if (block != null && !block.isEmpty()) {
                        steps.add(new Step(block, step.get("x").getAsInt(),
                                step.get("y").getAsInt(), step.get("z").getAsInt()));
                    }
                }
                out.put(entry.getKey(), steps);
            }
        } catch (Throwable unreadable) {
            out.clear();
        }
        return out;
    }

    public final ModeProperty defense = new ModeProperty("Defense",
            Math.min(2, Math.max(0, PATTERN_NAMES.length - 1)),
            PATTERN_NAMES.length == 0 ? new String[]{"None"} : PATTERN_NAMES);
    public final BooleanProperty onlyTopBeds = new BooleanProperty("OnlyTopBeds", true);
    public final BooleanProperty bedwarsOnly = new BooleanProperty("BedwarsOnly", true);
    public final BooleanProperty entityCheck = new BooleanProperty("EntityCheck", true);
    public final IntProperty delayAfterSwap = new IntProperty("DelayAfterSwap", 0, 0, 10);
    public final IntProperty delayAfterAim = new IntProperty("DelayAfterAim", 1, 0, 10);
    public final IntProperty sneakHoldTicks = new IntProperty("SneakHoldTicks", 5, 0, 20);
    public final IntProperty fov = new IntProperty("Fov", 180, 0, 180);
    public final ModeProperty moveFix =
            new ModeProperty("MoveFix", 1, new String[]{"None", "Silent"});

    private List<Step> steps = Collections.emptyList();
    private int stepIndex;
    private boolean started;
    private BlockPos origin;
    private EnumFacing lockedDirection;

    private float serverYaw;
    private float serverPitch;
    private int swapTicks;
    private int aimTicks;

    private boolean sneaking;
    private int sneakRemaining;

    private int retryTicks;
    private float retryYaw;
    private float retryPitch;

    private BlockPos renderTarget;
    private final Map<String, Integer> slotCache = new HashMap<String, Integer>();

    public BedDefender() {
        super("BedDefender", false);
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer == null || PATTERN_NAMES.length == 0) {
            this.setEnabled(false);
            return;
        }
        this.steps = PATTERNS.get(this.defense.getModeString());
        if (this.steps == null || this.steps.isEmpty() || !this.hasAnyRequiredBlock()) {
            this.setEnabled(false);
            return;
        }
        this.stepIndex = 0;
        this.started = false;
        this.origin = null;
        this.lockedDirection = null;
        this.renderTarget = null;
        this.swapTicks = 0;
        this.aimTicks = this.delayAfterAim.getValue();
        this.serverYaw = mc.thePlayer.rotationYaw;
        this.serverPitch = mc.thePlayer.rotationPitch;
        this.slotCache.clear();
    }

    @Override
    public void onDisabled() {
        if (this.sneaking) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
            this.sneaking = false;
        }
        this.renderTarget = null;
        this.steps = Collections.emptyList();
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null) {
            return;
        }
        this.releaseSneak();
        float[] rotation = this.plan();
        if (rotation != null) {
            event.setRotation(rotation[0], rotation[1], ROTATION_PRIORITY);
            event.setPervRotation(this.moveFix.getValue() != 0
                    ? rotation[0] : mc.thePlayer.rotationYaw, ROTATION_PRIORITY);
        }
    }

    private void releaseSneak() {
        if (!this.sneaking) {
            return;
        }
        if (this.sneakRemaining > 0) {
            this.sneakRemaining--;
            return;
        }
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), false);
        this.sneaking = false;
    }

    private float[] plan() {
        if (!this.started && !this.begin()) {
            return null;
        }
        this.stepIndex = -1;
        for (int i = 0; i < this.steps.size(); i++) {
            if (this.isAir(this.targetOf(i))) {
                this.stepIndex = i;
                break;
            }
        }
        if (this.stepIndex < 0) {
            this.setEnabled(false);
            return null;
        }

        BlockPos target = this.targetOf(this.stepIndex);
        this.renderTarget = target;

        if (this.retryTicks > 0) {
            this.retryTicks--;
            float[] held = this.attemptPlace(this.retryYaw, this.retryPitch, target);
            if (held != null) {
                return held;
            }
        }

        float[] held = this.attemptPlace(this.serverYaw, this.serverPitch, target);
        if (held != null) {
            return held;
        }
        for (float[] aim : this.candidateAims(target)) {
            float[] result = this.attemptPlace(aim[0], aim[1], target);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private boolean begin() {
        BlockPos bed = this.bedwarsOnly.getValue() ? whitelistedBed() : this.findBed();
        if (bed == null || !(state(bed) instanceof BlockBed)) {
            this.setEnabled(false);
            return false;
        }
        this.origin = bed;
        this.lockedDirection = bedDirection(bed);
        this.started = true;
        return true;
    }

    private static BlockPos whitelistedBed() {
        BedTracker tracker = (BedTracker) Unfair.moduleManager.getModule(BedTracker.class);
        return tracker == null ? null : tracker.getBedPos();
    }

    private BlockPos targetOf(int index) {
        Step step = this.steps.get(index);
        int[] offset = rotateOffset(step.x, step.y, step.z, this.lockedDirection);
        return this.origin.add(offset[0], offset[1], offset[2]);
    }

    private List<float[]> candidateAims(BlockPos target) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0F);
        float maxYaw = this.fov.getValue();
        float maxPitch = Math.min(this.fov.getValue(), 90.0F);
        float currentYaw = normaliseYaw(mc.thePlayer.rotationYaw);
        float currentPitch = mc.thePlayer.rotationPitch;
        float referenceYaw = normaliseYaw(this.serverYaw);

        double insetTop = 1.0 - INSET - 1.0E-3;
        double insetBottom = INSET + 1.0E-3;
        List<double[]> scored = new ArrayList<double[]>();

        for (EnumFacing face : FACE_ORDER) {
            BlockPos support = target.offset(face.getOpposite());
            Block block = state(support);
            if (block == Blocks.air) {
                continue;
            }
            if (this.onlyTopBeds.getValue() && block instanceof BlockBed && face != EnumFacing.UP) {
                continue;
            }
            for (int row = 0; row <= GRID; row++) {
                boolean leftToRight = (row & 1) == 0;
                double v = clamp01(row * STEP + RandomUtil.nextDouble(-STEP * JITTER, STEP * JITTER));
                for (int column = 0; column <= GRID; column++) {
                    double raw = clamp01(column * STEP
                            + RandomUtil.nextDouble(-STEP * JITTER, STEP * JITTER));
                    double u = leftToRight ? raw : 1.0 - raw;
                    Vec3 point = facePoint(support, face, u, v, insetTop, insetBottom);
                    float[] aim = rotationTo(eye, point);
                    float yaw = normaliseYaw(aim[0]);
                    float pitch = aim[1];
                    if (Math.abs(yawDelta(currentYaw, yaw)) > maxYaw) {
                        continue;
                    }
                    if (Math.abs(pitch - currentPitch) > maxPitch || Math.abs(pitch) > 90.0F) {
                        continue;
                    }
                    double cost = Math.abs(yawDelta(referenceYaw, yaw))
                            + Math.abs(pitch - this.serverPitch)
                            + (face == EnumFacing.UP ? -0.25 : 0.0);
                    scored.add(new double[]{cost, yaw, pitch});
                }
            }
        }
        Collections.sort(scored, new Comparator<double[]>() {
            @Override
            public int compare(double[] a, double[] b) {
                return Double.compare(a[0], b[0]);
            }
        });
        List<float[]> aims = new ArrayList<float[]>(scored.size());
        for (double[] entry : scored) {
            aims.add(new float[]{unwrapYaw((float) entry[1], this.serverYaw), (float) entry[2]});
        }
        return aims;
    }

    private static Vec3 facePoint(BlockPos support, EnumFacing face, double u, double v,
                                  double insetTop, double insetBottom) {
        double x = support.getX();
        double y = support.getY();
        double z = support.getZ();
        switch (face) {
            case UP:
                return new Vec3(x + u, y + insetTop, z + v);
            case DOWN:
                return new Vec3(x + u, y + insetBottom, z + v);
            case NORTH:
                return new Vec3(x + u, y + v, z + insetBottom);
            case SOUTH:
                return new Vec3(x + u, y + v, z + insetTop);
            case WEST:
                return new Vec3(x + insetBottom, y + v, z + u);
            default:
                return new Vec3(x + insetTop, y + v, z + u);
        }
    }

    private float[] attemptPlace(float yaw, float pitch, BlockPos target) {
        if (this.stepIndex >= this.steps.size()) {
            return null;
        }
        MovingObjectPosition ray = RayCastUtil.rayTrace(yaw, pitch, REACH, 1.0F);
        if (ray == null || ray.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || ray.getBlockPos() == null || ray.sideHit == null) {
            return null;
        }
        BlockPos support = ray.getBlockPos();
        if (!support.offset(ray.sideHit).equals(target)) {
            return null;
        }
        Block supportBlock = state(support);
        if (this.onlyTopBeds.getValue() && ray.sideHit != EnumFacing.UP
                && supportBlock instanceof BlockBed) {
            return null;
        }
        if (!this.isAir(target)) {
            return null;
        }
        if (this.entityCheck.getValue() && this.blockedByEntity(target)) {
            return new float[]{yaw, pitch};
        }

        String wanted = this.steps.get(this.stepIndex).block;
        int slot = this.findSlot(wanted);
        if (slot == -1) {
            this.setEnabled(false);
            return null;
        }
        if (mc.thePlayer.inventory.currentItem != slot) {
            mc.thePlayer.inventory.currentItem = slot;
            mc.playerController.syncCurrentPlayItem();
            this.swapTicks = this.delayAfterSwap.getValue();
        }
        if (this.swapTicks-- > 0) {
            return new float[]{yaw, pitch};
        }
        if (!this.sneaking && supportBlock instanceof BlockBed) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindSneak.getKeyCode(), true);
            this.sneaking = true;
            this.sneakRemaining = this.sneakHoldTicks.getValue();
            return new float[]{yaw, pitch};
        }
        if (this.aimTicks-- > 0
                || Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.serverYaw)) > SETTLE_ANGLE
                || Math.abs(pitch - this.serverPitch) > SETTLE_ANGLE) {
            return new float[]{yaw, pitch};
        }

        MovingObjectPosition finalRay = RayCastUtil.rayTrace(yaw, pitch, REACH, 1.0F);
        if (finalRay == null
                || finalRay.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !support.equals(finalRay.getBlockPos())
                || ray.sideHit != finalRay.sideHit) {
            return new float[]{yaw, pitch};
        }

        MovingObjectPosition serverRay =
                RayCastUtil.rayTrace(this.serverYaw, this.serverPitch, REACH, 1.0F);
        if (serverRay == null
                || serverRay.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK
                || !support.equals(serverRay.getBlockPos())
                || ray.sideHit != serverRay.sideHit) {
            return new float[]{yaw, pitch};
        }

        ItemStack held = mc.thePlayer.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemBlock)) {
            return new float[]{yaw, pitch};
        }

        float savedYaw = mc.thePlayer.rotationYaw;
        float savedPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationYaw = yaw;
        mc.thePlayer.rotationPitch = pitch;
        boolean placed;
        try {
            placed = mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held,
                    support, finalRay.sideHit, finalRay.hitVec);
        } finally {
            mc.thePlayer.rotationYaw = savedYaw;
            mc.thePlayer.rotationPitch = savedPitch;
        }
        this.aimTicks = this.delayAfterAim.getValue();
        if (placed) {
            mc.thePlayer.swingItem();
            this.retryTicks = 0;
        } else {
            this.retryYaw = yaw;
            this.retryPitch = pitch;
            this.retryTicks = 3;
        }
        return new float[]{yaw, pitch};
    }

    @EventTarget(Priority.HIGHEST)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND) {
            return;
        }
        if (event.getPacket() instanceof C03PacketPlayer) {
            C03PacketPlayer packet = (C03PacketPlayer) event.getPacket();
            if (packet.getRotating()) {
                this.serverYaw = packet.getYaw();
                this.serverPitch = packet.getPitch();
            }
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || this.renderTarget == null || mc.thePlayer == null) {
            return;
        }
        RenderUtil.enableRenderState();
        RenderUtil.drawBlockBoundingBox(this.renderTarget, 1.0, 0, 255, 0, 255, 1.5F);
        RenderUtil.disableRenderState();
    }

    private boolean hasAnyRequiredBlock() {
        for (Step step : this.steps) {
            if (this.findSlot(step.block) != -1) {
                return true;
            }
        }
        return false;
    }

    private int findSlot(String blockName) {
        Integer cached = this.slotCache.get(blockName);
        if (cached != null && matches(mc.thePlayer.inventory.getStackInSlot(cached), blockName)) {
            return cached;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (matches(mc.thePlayer.inventory.getStackInSlot(slot), blockName)) {
                this.slotCache.put(blockName, slot);
                return slot;
            }
        }
        return -1;
    }

    private static boolean matches(ItemStack stack, String blockName) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) {
            return false;
        }
        return registryName(((ItemBlock) stack.getItem()).getBlock()).equalsIgnoreCase(blockName);
    }

    private BlockPos findBed() {
        BlockPos me = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int x = me.getX() - SEARCH_RANGE; x <= me.getX() + SEARCH_RANGE; x++) {
            for (int y = me.getY() - SEARCH_RANGE; y <= me.getY() + SEARCH_RANGE; y++) {
                for (int z = me.getZ() - SEARCH_RANGE; z <= me.getZ() + SEARCH_RANGE; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!(state(pos) instanceof BlockBed)) {
                        continue;
                    }
                    double distance = pos.distanceSq(me);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private static EnumFacing bedDirection(BlockPos bed) {
        IBlockState blockState = mc.theWorld.getBlockState(bed);
        int meta = blockState.getBlock().getMetaFromState(blockState);
        switch (meta) {
            case 0:
            case 10:
                return EnumFacing.NORTH;
            case 2:
            case 8:
                return EnumFacing.SOUTH;
            case 3:
            case 9:
                return EnumFacing.WEST;
            case 1:
            case 11:
                return EnumFacing.EAST;
            default:
                return null;
        }
    }

    private static int[] rotateOffset(int x, int y, int z, EnumFacing direction) {
        if (direction == null) {
            return new int[]{x, y, z};
        }
        switch (direction) {
            case NORTH:
                return new int[]{x, y, z};
            case SOUTH:
                return new int[]{-x, y, -z};
            case EAST:
                return new int[]{-z, y, x};
            case WEST:
                return new int[]{z, y, -x};
            default:
                return new int[]{x, y, z};
        }
    }

    private boolean isAir(BlockPos pos) {
        return state(pos) == Blocks.air;
    }

    private boolean blockedByEntity(BlockPos target) {
        AxisAlignedBB box = new AxisAlignedBB(
                target.getX(), target.getY(), target.getZ(),
                target.getX() + 1.0, target.getY() + 1.0, target.getZ() + 1.0);
        List<Entity> entities = mc.theWorld.getEntitiesWithinAABBExcludingEntity(null, box);
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        for (Entity entity : entities) {
            if (entity == mc.thePlayer || entity.isDead || !entity.canBeCollidedWith()) {
                continue;
            }
            if (entity.getEntityBoundingBox().intersectsWith(box)) {
                return true;
            }
        }
        return false;
    }

    private static Block state(BlockPos pos) {
        return mc.theWorld.getBlockState(pos).getBlock();
    }

    private static String registryName(Block block) {
        Object name = Block.blockRegistry.getNameForObject(block);
        if (name == null) {
            return "";
        }
        String full = name.toString();
        int colon = full.indexOf(':');
        return colon >= 0 ? full.substring(colon + 1) : full;
    }

    private static float[] rotationTo(Vec3 eye, Vec3 point) {
        double dx = point.xCoord - eye.xCoord;
        double dy = point.yCoord - eye.yCoord;
        double dz = point.zCoord - eye.zCoord;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
                (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0),
                (float) (-Math.toDegrees(Math.atan2(dy, horizontal)))
        };
    }

    private static float normaliseYaw(float yaw) {
        yaw = ((yaw % 360.0F) + 360.0F) % 360.0F;
        return yaw > 180.0F ? yaw - 360.0F : yaw;
    }

    private static float yawDelta(float base, float target) {
        return MathHelper.wrapAngleTo180_float(target - base);
    }

    private static float unwrapYaw(float yaw, float previous) {
        return previous + MathHelper.wrapAngleTo180_float(yaw - previous);
    }

    private static double clamp01(double value) {
        return value < 0.0 ? 0.0 : (value > 1.0 ? 1.0 : value);
    }

    @Override
    public String[] getSuffix() {
        if (!this.isEnabled() || this.steps.isEmpty()) {
            return new String[]{this.defense.getModeString()};
        }
        return new String[]{this.stepIndex + "/" + this.steps.size()};
    }
}
