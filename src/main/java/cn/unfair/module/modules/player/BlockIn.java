package cn.unfair.module.modules.player;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.world.BedNuker;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.player.MoveUtil;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import cn.unfair.util.render.AnimationUtil;
import cn.unfair.util.render.RenderUtil;
import cn.unfair.util.world.BlockUtil;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockWall;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.lwjgl.input.Mouse;

import java.util.*;

public class BlockIn extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final EnumFacing[] HORIZONTALS = {
            EnumFacing.EAST, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.NORTH
    };
    private static final SupportOffset[] SUPPORTS = {
            new SupportOffset(0, 1, 0, EnumFacing.DOWN),
            new SupportOffset(0, -1, 0, EnumFacing.UP),
            new SupportOffset(0, 0, -1, EnumFacing.NORTH),
            new SupportOffset(0, 0, 1, EnumFacing.SOUTH),
            new SupportOffset(1, 0, 0, EnumFacing.EAST),
            new SupportOffset(-1, 0, 0, EnumFacing.WEST),
    };
    private static final double REACH = 4.5;
    private static final double GRID_INSET = 0.05;
    private static final double GRID_STEP = 0.2;
    private static final int GRID_N = (int) Math.round(1.0 / GRID_STEP);

    public final IntProperty speed = new IntProperty("Speed", 10, 1, 30);
    public final IntProperty randomization = new IntProperty("Randomization", 10, 0, 100);
    public final IntProperty rotationTol = new IntProperty("Rotation Tolerance", 25, 20, 100);
    public final BooleanProperty itemSpoof = new BooleanProperty("Item Spoof", true);
    public final BooleanProperty showProgress = new BooleanProperty("Show Progress", true);

    private boolean placing;
    private boolean slotWasSwapped;
    private int prevSlot = -1;
    private int plannedSlot = -1;

    private BlockPos targetHitPos;
    private EnumFacing targetSide;
    private float aimYaw;
    private float aimPitch;

    private float fillCount;
    private float lastFillCount = -1;
    private float circleProgress;
    private float animStartProgress;
    private float animTargetProgress;
    private long animStartTime;

    private boolean lastTargetAdjacent;
    private int lastSlot = -1;

    private static final int ROTATION_PRIORITY = 4;

    public BlockIn() {
        super("BlockIn", false);
        this.lastSlot = -1;
    }

    @Override
    public void onEnabled() {
        if (mc.thePlayer != null) {
            this.lastSlot = mc.thePlayer.inventory.currentItem;
        }
    }

    @Override
    public void onDisabled() {
        disablePlacing();
        fillCount = 0;
        lastFillCount = -1;
        circleProgress = 0;
    }

    @EventTarget(Priority.HIGH)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) return;
        if (event.getType() != EventType.PRE) return;
        if (this.isBedNukerActive()) {
            disablePlacing();
            return;
        }

        float baseYaw = event.getYaw();
        float basePitch = event.getPitch();

        runTargetSelection();

        if (mc.currentScreen != null) disablePlacing();
        if (!placing || targetHitPos == null) {
            return;
        }

        float[] sm = RotationUtil.smoothRotation(baseYaw, basePitch, aimYaw, aimPitch,
                speed.getValue(), randomization.getValue());
        double r = REACH;
        MovingObjectPosition mop = rayCastBlock(r, sm[0], sm[1]);

        if (mop != null) {
            BlockPos hitBlock = mop.getBlockPos();
            EnumFacing side = mop.sideHit;
            if (hitBlock.equals(targetHitPos) && side == targetSide) {
                float tol = rotationTol.getValue();
                if (Math.abs(MathHelper.wrapAngleTo180_float(sm[0] - baseYaw)) <= tol
                        && Math.abs(sm[1] - basePitch) <= tol) {
                    ItemStack held = mc.thePlayer.inventory.getCurrentItem();
                    if (held != null && held.getItem() instanceof ItemBlock
                            && mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, held, hitBlock, side, mop.hitVec)) {
                        mc.thePlayer.swingItem();
                    }
                }
            }
        }

        event.setRotation(sm[0], sm[1], ROTATION_PRIORITY);
        event.setPervRotation(sm[0], ROTATION_PRIORITY);

        updateFillCount();
    }

    private void updateFillCount() {
        fillCount = 0;
        if (selectKeyDown() && mc.currentScreen == null) {
            BlockPos feet = new BlockPos(
                    MathHelper.floor_double(mc.thePlayer.posX),
                    MathHelper.floor_double(mc.thePlayer.posY),
                    MathHelper.floor_double(mc.thePlayer.posZ)
            );

            if (!BlockUtil.isReplaceable(feet.up().up())) fillCount++;

            for (EnumFacing dir : HORIZONTALS) {
                BlockPos side = feet.offset(dir);
                if (!BlockUtil.isReplaceable(side)) fillCount++;
                if (!BlockUtil.isReplaceable(side.up())) fillCount++;
            }

            if (fillCount != lastFillCount) {
                animStartProgress = circleProgress;
                animTargetProgress = Math.max(0f, Math.min(1f, fillCount / 9f));
                animStartTime = System.currentTimeMillis();
                lastFillCount = fillCount;
            }
        }
    }

    private void runTargetSelection() {
        clearAim();

        if (!selectKeyDown() || mc.currentScreen != null) {
            disablePlacing();
            circleProgress = 0f;
            return;
        }

        int strongSlot = findFullBlock();
        int weakSlot = findFullBlock();
        if (strongSlot == -1 && weakSlot == -1) {
            disablePlacing();
            return;
        }

        plannedSlot = (strongSlot != -1 ? strongSlot : weakSlot);

        if (!getTarget()) {
            disablePlacing();
            return;
        }

        if (lastTargetAdjacent) plannedSlot = (strongSlot != -1 ? strongSlot : weakSlot);
        else plannedSlot = (weakSlot != -1 ? weakSlot : strongSlot);

        if (!placing) enablePlacing();

        if (mc.gameSettings.keyBindAttack.isKeyDown() || mc.gameSettings.keyBindUseItem.isKeyDown()) {
            clearAim();
        }

        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        equipPlannedSlot();
    }

    @EventTarget(Priority.HIGH)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.PRE) return;
        if (mc.thePlayer == null || mc.theWorld == null) return;

        long elapsed = System.currentTimeMillis() - animStartTime;
        if (elapsed < 50L) {
            float t = (float) elapsed / 50f;
            circleProgress = AnimationUtil.lerp(animStartProgress, animTargetProgress, AnimationUtil.quadInOutEasing(t));
        } else {
            circleProgress = animTargetProgress;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (!this.isEnabled() || fillCount <= 0 || !showProgress.getValue() || !this.selectKeyDown()) return;

        ScaledResolution sr = new ScaledResolution(mc);
        float cx = sr.getScaledWidth() / 2f;
        float cy = sr.getScaledHeight() / 2f;
        float radius = 10f;
        float thickness = 3f;

        RenderUtil.draw2DCircle(cx, cy, radius, 60, thickness, 0f, 0f, 0f, 0.5f);

        if (circleProgress >= 0.999f) {
            RenderUtil.draw2DCircle(cx, cy, radius, 60, thickness, 0f, 1f, 0f, 1f);
            return;
        }

        float startAngle = 90f;
        float endAngle = startAngle + circleProgress * 360f + 0.5f;

        float ratio = Math.max(0f, Math.min(1f, circleProgress));
        int r = (int) ((1f - ratio) * 255f + 0.5f);
        int g = (int) (ratio * 255f + 0.5f);
        int color = ((255 & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8);

        RenderUtil.draw2DCircleArc(cx, cy, radius, startAngle, endAngle, thickness, color);
    }

    @EventTarget(Priority.HIGHEST)
    public void onLeftClick(LeftClickMouseEvent event) {
        if (placing) event.setCancelled(true);
    }

    @EventTarget(Priority.HIGHEST)
    public void onRightClick(RightClickMouseEvent event) {
        if (placing) event.setCancelled(true);
    }

    @EventTarget
    public void onSwap(SwapItemEvent event) {
        if (this.isEnabled() && this.placing) {
            lastSlot = event.setSlot(lastSlot);
            event.setCancelled(true);
        }
    }

    @EventTarget
    public void onMoveInput(MoveInputEvent event) {
        if (this.isEnabled()
                && RotationState.isActived()
                && RotationState.getPriority() == ROTATION_PRIORITY
                && (mc.thePlayer.movementInput.moveForward != 0f || mc.thePlayer.movementInput.moveStrafe != 0f)) {
            MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
        }
    }

    public boolean isPlacing() {
        return placing;
    }

    public int getSlot() {
        return this.itemSpoof.getValue() && this.placing ? this.lastSlot : -1;
    }

    private boolean isBedNukerActive() {
        BedNuker bedNuker = (BedNuker) Unfair.moduleManager.getModule(BedNuker.class);
        return bedNuker != null && bedNuker.isEnabled();
    }

    private boolean selectKeyDown() {
        int key = this.getKey();
        if (key == 0) return false;
        return KeyBindUtil.isKeyDown(key);
    }

    private void enablePlacing() {
        if (placing) return;
        placing = true;
        slotWasSwapped = false;
        prevSlot = mc.thePlayer.inventory.currentItem;
        lastSlot = prevSlot;
    }

    private void disablePlacing() {
        if (!placing) return;

        if (slotWasSwapped && prevSlot != -1 && prevSlot != mc.thePlayer.inventory.currentItem) {
            mc.thePlayer.inventory.currentItem = prevSlot;
        }

        placing = false;
        slotWasSwapped = false;
        prevSlot = -1;
        plannedSlot = -1;

        if (mc.currentScreen == null) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), Mouse.isButtonDown(1));
        }
    }

    private void clearAim() {
        targetHitPos = null;
        targetSide = null;
    }

    private void equipPlannedSlot() {
        int cur = mc.thePlayer.inventory.currentItem;
        if (plannedSlot != -1 && plannedSlot != cur) {
            mc.thePlayer.inventory.currentItem = plannedSlot;
            slotWasSwapped = true;
        }
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

    private boolean getTarget() {
        AimResult result = roofAim();
        if (result == null) result = sidesAim();
        if (result == null) return false;

        BlockPos placed = result.supportBlock.offset(result.face);
        lastTargetAdjacent = isDirectAdjacentPlacement(placed);

        targetHitPos = result.supportBlock;
        targetSide = result.face;
        aimYaw = result.yaw;
        aimPitch = result.pitch;
        return true;
    }

    private AimResult roofAim() {
        Vec3 pos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        BlockPos aboveHead = new BlockPos(
                MathHelper.floor_double(pos.xCoord),
                MathHelper.floor_double(pos.yCoord) + 2,
                MathHelper.floor_double(pos.zCoord)
        );
        if (!BlockUtil.isReplaceable(aboveHead)) return null;

        if (plannedSlot < 0 || plannedSlot > 8) return null;
        ItemStack held = mc.thePlayer.inventory.mainInventory[plannedSlot];
        double r = REACH;
        Vec3 eye = new Vec3(pos.xCoord, pos.yCoord + mc.thePlayer.getEyeHeight(), pos.zCoord);
        double r2 = r * r;
        double rp12 = (r + 1) * (r + 1);

        int minY = MathHelper.floor_double(eye.yCoord) + 1;
        int maxY = MathHelper.floor_double(eye.yCoord + r);
        int minX = MathHelper.floor_double(eye.xCoord - r);
        int maxX = MathHelper.floor_double(eye.xCoord + r);
        int minZ = MathHelper.floor_double(eye.zCoord - r);
        int maxZ = MathHelper.floor_double(eye.zCoord + r);

        ArrayList<BlockCandidate> cands = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    double dx = (x + 0.5) - eye.xCoord;
                    double dy = (y + 0.5) - eye.yCoord;
                    double dz = (z + 0.5) - eye.zCoord;
                    if (dx * dx + dy * dy + dz * dz > rp12) continue;

                    BlockPos bp = new BlockPos(x, y, z);
                    if (BlockUtil.isReplaceable(bp)) continue;
                    Block block = getBlock(bp);
                    if (BlockUtil.isInteractable(block) || block instanceof BlockFence || block instanceof BlockWall) continue;

                    double d2 = dist2PointAABB(eye, bp);
                    if (d2 > r2) continue;

                    cands.add(new BlockCandidate(d2, bp));
                }
            }
        }

        cands.sort((a, b) -> Double.compare(a.dist, b.dist));

        for (BlockCandidate cand : cands) {
            AimResult res = getBestRotationsToBlock(held, cand.pos, eye, r, minY);
            if (res != null) return res;
        }
        return null;
    }

    private AimResult getBestRotationsToBlock(ItemStack held, BlockPos targetCell, Vec3 eye, double reachVal, int minY) {
        float baseYaw = RotationState.isActived() ? RotationState.getSmoothedYaw() : mc.thePlayer.rotationYaw;
        float basePitch = RotationState.isActived() ? RotationState.getRotationPitch() : mc.thePlayer.rotationPitch;

        boolean faceUp = Math.abs(eye.yCoord - (targetCell.getY() + 1)) < Math.abs(eye.yCoord - targetCell.getY());
        boolean faceSouth = Math.abs(eye.zCoord - (targetCell.getZ() + 1)) < Math.abs(eye.zCoord - targetCell.getZ());
        boolean faceEast = Math.abs(eye.xCoord - (targetCell.getX() + 1)) < Math.abs(eye.xCoord - targetCell.getX());

        double bx = targetCell.getX(), by = targetCell.getY(), bz = targetCell.getZ();
        double jit = GRID_STEP * 0.1;

        ArrayList<RotationCandidate> cands = new ArrayList<>((GRID_N + 1) * (GRID_N + 1) * 3 + 1);
        cands.add(new RotationCandidate(0, baseYaw, basePitch));

        for (int row = 0; row <= GRID_N; row++) {
            double v = clamp01(row * GRID_STEP + jitter(jit));
            for (int col = 0; col <= GRID_N; col++) {
                double u = clamp01(col * GRID_STEP + jitter(jit));

                float[] rY = RotationUtil.getRotations(
                        bx + u, faceUp ? by + 1 - GRID_INSET : by + GRID_INSET, bz + v,
                        eye.xCoord, eye.yCoord, eye.zCoord);
                cands.add(new RotationCandidate(
                        Math.abs(MathHelper.wrapAngleTo180_float(rY[0] - baseYaw)) + Math.abs(rY[1] - basePitch),
                        rY[0], rY[1]));

                float[] rZ = RotationUtil.getRotations(
                        bx + u, by + v, faceSouth ? bz + 1 - GRID_INSET : bz + GRID_INSET,
                        eye.xCoord, eye.yCoord, eye.zCoord);
                cands.add(new RotationCandidate(
                        Math.abs(MathHelper.wrapAngleTo180_float(rZ[0] - baseYaw)) + Math.abs(rZ[1] - basePitch),
                        rZ[0], rZ[1]));

                float[] rX = RotationUtil.getRotations(
                        faceEast ? bx + 1 - GRID_INSET : bx + GRID_INSET, by + v, bz + u,
                        eye.xCoord, eye.yCoord, eye.zCoord);
                cands.add(new RotationCandidate(
                        Math.abs(MathHelper.wrapAngleTo180_float(rX[0] - baseYaw)) + Math.abs(rX[1] - basePitch),
                        rX[0], rX[1]));
            }
        }

        cands.sort((a, b) -> Double.compare(a.cost, b.cost));

        int byY = targetCell.getY();
        for (RotationCandidate c : cands) {
            MovingObjectPosition mop = rayCastBlock(reachVal, c.yaw, c.pitch);
            if (mop == null) continue;
            BlockPos hitBlock = mop.getBlockPos();
            EnumFacing face = mop.sideHit;
            if (hitBlock.equals(targetCell) && hitBlock.getY() >= minY
                    && !(face == EnumFacing.DOWN && byY == minY)
                    && canPlaceBlockOnSide(held, hitBlock, face)) {
                return new AimResult(hitBlock, face, c.yaw, c.pitch);
            }
        }
        return null;
    }

    private AimResult sidesAim() {
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        BlockPos head = feet.up();
        double r = REACH;
        Vec3 eye = mc.thePlayer.getPositionEyes(1.0f);

        ArrayList<BlockPos> baseline = new ArrayList<>(8);
        for (EnumFacing dir : HORIZONTALS) {
            baseline.add(feet.offset(dir));
            baseline.add(head.offset(dir));
        }

        ArrayList<BlockPos> primaryGoals = new ArrayList<>(baseline.size());
        for (BlockPos pos : baseline) {
            if (!BlockUtil.isReplaceable(pos)) continue;
            if (!hasAirNeighbor(pos, feet, head)) continue;
            primaryGoals.add(pos);
        }
        if (primaryGoals.isEmpty()) return null;

        Vec3 enemyPos = getClosestPlayerPos(100);
        if (enemyPos != null) {
            baseline.sort((a, b) -> {
                double da = sq(a.getX() + 0.5 - enemyPos.xCoord)
                        + sq(a.getY() + 0.5 - enemyPos.yCoord)
                        + sq(a.getZ() + 0.5 - enemyPos.zCoord);
                double db = sq(b.getX() + 0.5 - enemyPos.xCoord)
                        + sq(b.getY() + 0.5 - enemyPos.yCoord)
                        + sq(b.getZ() + 0.5 - enemyPos.zCoord);
                return Double.compare(da, db);
            });
            int picked = 0;
            for (int i = 0; i < baseline.size() && picked < 3; i++) {
                BlockPos pos = baseline.get(i);
                if (!BlockUtil.isReplaceable(pos)) continue;
                if (!hasAirNeighbor(pos, feet, head)) continue;
                AimResult rEnemy = findBestForGoals(Collections.singletonList(pos), r, eye);
                if (rEnemy != null) return rEnemy;
                picked++;
            }
        }

        AimResult result = findBestForGoals(primaryGoals, r, eye);
        if (result != null) return result;

        ArrayList<BlockPos> frontier = new ArrayList<>(primaryGoals);
        HashSet<Long> seen = new HashSet<>(frontier.size() * 8);
        for (BlockPos g : frontier) seen.add(g.toLong());

        for (int iter = 0; iter < 5; iter++) {
            if (frontier.isEmpty()) break;

            ArrayList<BlockPos> layer = new ArrayList<>(frontier.size() * 3);
            for (BlockPos g : frontier) {
                for (EnumFacing f : EnumFacing.values()) {
                    BlockPos s = g.offset(f);
                    if (!BlockUtil.isReplaceable(s)) continue;
                    if (!seen.add(s.toLong())) continue;
                    layer.add(s);
                }
            }

            if (!layer.isEmpty()) {
                AimResult rLayer = findBestForGoals(layer, r, eye);
                if (rLayer != null) return rLayer;
            }
            frontier = layer;
        }
        return null;
    }

    private AimResult findBestForGoals(List<BlockPos> goals, double reachVal, Vec3 eye) {
        if (goals == null || goals.isEmpty()) return null;
        if (plannedSlot < 0 || plannedSlot > 8) return null;

        ItemStack held = mc.thePlayer.inventory.mainInventory[plannedSlot];
        float curYaw = RotationState.isActived() ? RotationState.getSmoothedYaw() : mc.thePlayer.rotationYaw;
        float curPitch = RotationState.isActived() ? RotationState.getRotationPitch() : mc.thePlayer.rotationPitch;

        MovingObjectPosition now = rayCastBlock(reachVal, curYaw, curPitch);
        if (now != null) {
            BlockPos support = now.getBlockPos();
            EnumFacing faceHit = now.sideHit;

            if (!BlockUtil.isReplaceable(support) && canPlaceBlockOnSide(held, support, faceHit)) {
                for (BlockPos goal : goals) {
                    AimResult ok = tryPlacement(reachVal, curYaw, curPitch, support, faceHit, goal);
                    if (ok != null) return ok;
                }
            }
        }

        double jit = GRID_STEP * 0.1;
        double insetTop = 1 - GRID_INSET - 1e-3;
        double insetBot = GRID_INSET + 1e-3;

        ArrayList<PlacementCandidate> cands = new ArrayList<>(Math.max(16, goals.size() * 6 * (GRID_N + 1) * (GRID_N + 1)));

        for (BlockPos g : goals) {
            for (SupportOffset s : SUPPORTS) {
                BlockPos support = new BlockPos(g.getX() + s.dx, g.getY() + s.dy, g.getZ() + s.dz);
                if (BlockUtil.isReplaceable(support) || !canPlaceBlockOnSide(held, support, s.face)) continue;

                double sx = support.getX(), sy = support.getY(), sz = support.getZ();

                for (int row = 0; row <= GRID_N; row++) {
                    boolean ltr = (row & 1) == 0;
                    double v = clamp01(row * GRID_STEP + jitter(jit));

                    for (int col = 0; col <= GRID_N; col++) {
                        double cu = clamp01(col * GRID_STEP + jitter(jit));
                        double u = ltr ? cu : 1.0 - cu;

                        double px, py, pz;
                        if (s.dy != 0) {
                            px = sx + u; pz = sz + v;
                            py = sy + (s.dy < 0 ? insetTop : insetBot);
                        } else if (s.dz != 0) {
                            px = sx + u; py = sy + v;
                            pz = sz + (s.dz < 0 ? insetTop : insetBot);
                        } else {
                            pz = sz + u; py = sy + v;
                            px = sx + (s.dx < 0 ? insetTop : insetBot);
                        }

                        float[] rot = RotationUtil.getRotations(px, py, pz, eye.xCoord, eye.yCoord, eye.zCoord);
                        float dYaw = Math.abs(MathHelper.wrapAngleTo180_float(rot[0] - curYaw));
                        float dPit = Math.abs(rot[1] - curPitch);
                        if (dYaw < 0.1f && dPit < 0.1f) continue;

                        cands.add(new PlacementCandidate(dYaw + dPit, rot[0], rot[1], support, s.face, g));
                    }
                }
            }
        }

        if (cands.isEmpty()) return null;

        cands.sort((a, b) -> Double.compare(a.cost, b.cost));

        for (PlacementCandidate c : cands) {
            AimResult ok = tryPlacement(reachVal, c.yaw, c.pitch, c.support, c.face, c.goal);
            if (ok != null) return ok;
        }
        return null;
    }

    private AimResult tryPlacement(double reachVal, float yaw, float pit, BlockPos expectedSupport, EnumFacing expectedFace, BlockPos goal) {
        MovingObjectPosition mop = rayCastBlock(reachVal, yaw, pit);
        if (mop == null) return null;
        BlockPos hitBlock = mop.getBlockPos();
        EnumFacing faceHit = mop.sideHit;
        if (!hitBlock.equals(expectedSupport)) return null;
        if (faceHit != expectedFace) return null;
        BlockPos placed = hitBlock.offset(faceHit);
        if (!placed.equals(goal)) return null;
        return new AimResult(hitBlock, faceHit, yaw, pit);
    }

    private boolean isDirectAdjacentPlacement(BlockPos p) {
        BlockPos feet = new BlockPos(
                MathHelper.floor_double(mc.thePlayer.posX),
                MathHelper.floor_double(mc.thePlayer.posY),
                MathHelper.floor_double(mc.thePlayer.posZ)
        );
        int dx = p.getX() - feet.getX();
        int dy = p.getY() - feet.getY();
        int dz = p.getZ() - feet.getZ();
        if (dx == 0 && dz == 0 && dy == 2) return true;
        return (dy == 0 || dy == 1)
                && ((Math.abs(dx) == 1 && dz == 0) || (Math.abs(dz) == 1 && dx == 0));
    }

    private static MovingObjectPosition rayCastBlock(double distance, float yaw, float pitch) {
        MovingObjectPosition mop = RayCastUtil.rayTrace(yaw, pitch, distance, 1.0f);
        if (mop == null || mop.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) return null;
        return mop;
    }

    private static Vec3 getClosestPlayerPos(double maxRange) {
        if (mc.thePlayer == null || mc.theWorld == null) return null;
        EntityPlayer best = null;
        double bestSq = maxRange * maxRange;
        for (EntityPlayer p : mc.theWorld.playerEntities) {
            if (p == mc.thePlayer || p.isDead || p.deathTime != 0) continue;
            double d = mc.thePlayer.getDistanceSqToEntity(p);
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        if (best == null) return null;
        return new Vec3(best.posX, best.posY, best.posZ);
    }

    private static boolean canPlaceBlockOnSide(ItemStack stack, BlockPos pos, EnumFacing side) {
        if (stack == null || !(stack.getItem() instanceof ItemBlock)) return false;
        return ((ItemBlock) stack.getItem()).canPlaceBlockOnSide(mc.theWorld, pos, side, mc.thePlayer, stack);
    }

    private static Block getBlock(BlockPos blockPos) {
        return mc.theWorld.getBlockState(blockPos).getBlock();
    }

    private static double dist2PointAABB(Vec3 p, BlockPos b) {
        double cx = Math.max(b.getX(), Math.min(b.getX() + 1, p.xCoord));
        double cy = Math.max(b.getY(), Math.min(b.getY() + 1, p.yCoord));
        double cz = Math.max(b.getZ(), Math.min(b.getZ() + 1, p.zCoord));
        double dx = p.xCoord - cx, dy = p.yCoord - cy, dz = p.zCoord - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean hasAirNeighbor(BlockPos pos, BlockPos... exclude) {
        for (EnumFacing f : EnumFacing.values()) {
            BlockPos n = pos.offset(f);
            if (BlockUtil.isReplaceable(n)) {
                boolean excluded = false;
                for (BlockPos ex : exclude) {
                    if (n.equals(ex)) {
                        excluded = true;
                        break;
                    }
                }
                if (!excluded) return true;
            }
        }
        return false;
    }

    private static double sq(double v) {
        return v * v;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }

    private static double jitter(double range) {
        return range > 0 ? (Math.random() * 2 - 1) * range : 0;
    }

    private static class SupportOffset {
        final int dx, dy, dz;
        final EnumFacing face;

        SupportOffset(int dx, int dy, int dz, EnumFacing face) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.face = face;
        }
    }

    private static class BlockCandidate {
        final double dist;
        final BlockPos pos;

        BlockCandidate(double dist, BlockPos pos) {
            this.dist = dist;
            this.pos = pos;
        }
    }

    private static class RotationCandidate {
        final double cost;
        final float yaw, pitch;

        RotationCandidate(double cost, float yaw, float pitch) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    private static class PlacementCandidate {
        final double cost;
        final float yaw, pitch;
        final BlockPos support, goal;
        final EnumFacing face;

        PlacementCandidate(double cost, float yaw, float pitch, BlockPos support, EnumFacing face, BlockPos goal) {
            this.cost = cost;
            this.yaw = yaw;
            this.pitch = pitch;
            this.support = support;
            this.face = face;
            this.goal = goal;
        }
    }

    private static class AimResult {
        final BlockPos supportBlock;
        final EnumFacing face;
        final float yaw, pitch;

        AimResult(BlockPos supportBlock, EnumFacing face, float yaw, float pitch) {
            this.supportBlock = supportBlock;
            this.face = face;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }
}
