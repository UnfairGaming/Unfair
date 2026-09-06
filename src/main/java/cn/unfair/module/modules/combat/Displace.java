package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.client.MathUtil;
import cn.unfair.util.rotation.RayCastUtil;
import cn.unfair.util.rotation.RotationUtil;
import cn.unfair.util.client.TeamUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Displace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int DISPLACE_WINDOW_TICKS = 20;
    private static final int VOID_SCAN_DIRECTIONS = 32;
    private static final int VOID_SCAN_RINGS = 12;
    private static final int VOID_SCAN_DEPTH = 10;
    private static final double VOID_SCAN_STEP = 0.5D;
    private static final double DYNAMIC_SCAN_STEP = 0.5D;
    private static final double DYNAMIC_SCAN_DISTANCE = 6.0D;
    private static final double DYNAMIC_SCAN_SIDE_STEP = 0.45D;
    private static final double DYNAMIC_WALL_CHECK_STEP = 0.25D;
    private static final double DYNAMIC_COLLISION_INSET = 0.03D;
    private static final double[] VOID_SCAN_X = new double[VOID_SCAN_DIRECTIONS];
    private static final double[] VOID_SCAN_Z = new double[VOID_SCAN_DIRECTIONS];
    private static final long ARROW_FADE_MS = 250L;
    private static final double ARROW_FORWARD_GAP = 0.24D;
    private static final double ARROW_BODY_LENGTH = 0.74D;
    private static final double ARROW_BODY_HALF_HEIGHT = 0.08D;
    private static final double ARROW_HEAD_BACKSET = 0.18D;
    private static final double ARROW_HEAD_LENGTH = 0.52D;
    private static final double ARROW_HEAD_HALF_HEIGHT = 0.30D;

    static {
        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double angle = Math.PI * 2.0D * i / VOID_SCAN_DIRECTIONS;
            VOID_SCAN_X[i] = Math.cos(angle);
            VOID_SCAN_Z[i] = Math.sin(angle);
        }
    }

    public final ModeProperty dynamicAngle = new ModeProperty("Dynamic Angle", 1, new String[]{"Static", "Dynamic"});
    public final FloatProperty yawOffset = new FloatProperty("Yaw Offset", 90.0F, 0.0F, 180.0F, () -> dynamicAngle.getValue() == 0);
    public final FloatProperty delay = new FloatProperty("Delay", 500.0F, 0.0F, 1000.0F);
    public final ModeProperty direction = new ModeProperty("Direction", 0, new String[]{"Left", "Right"}, () -> dynamicAngle.getValue() == 0);
    public final BooleanProperty showDirection = new BooleanProperty("Show Direction", true);
    public final BooleanProperty findVoid = new BooleanProperty("Find Void", false, () -> dynamicAngle.getValue() == 0);
    public final BooleanProperty blink = new BooleanProperty("Blink", false);
    public final BooleanProperty hasKnockback = new BooleanProperty("Has Knockback", false);
    public final BooleanProperty weaponsOnly = new BooleanProperty("Weapons Only", false);
    public final BooleanProperty allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty inventoryCheck = new BooleanProperty("Inventory Check", true);

    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();
    private boolean active;
    private boolean hasKB;
    private boolean displaceThisTick;
    private boolean compensateNextTick;
    private boolean displaceLeft;
    private boolean wasDisplacingLastTick;
    private boolean releaseBlinkNextTick;
    private Float dynamicVoidYaw;
    private Float renderDisplaceYaw;
    private EntityPlayer renderTarget;
    private Float fadingDisplaceYaw;
    private EntityPlayer fadingTarget;
    private long arrowFadeStartMs;
    private Float lastRenderedDisplaceYaw;
    private EntityPlayer lastRenderedTarget;
    private long lastRenderedArrowMs;
    private int tickCounter;

    public Displace() {
        super("Displace", false);
    }

    @Override
    public void onEnabled() {
        clearState();
        clearArrowState();
        tickCounter = 0;
        targetWindowStartTicks.clear();
        releaseBlink();
        releaseBlinkNextTick = false;
    }

    @Override
    public void onDisabled() {
        clearState();
        clearArrowState();
        targetWindowStartTicks.clear();
        releaseBlink();
        releaseBlinkNextTick = false;
    }

    @Override
    public String[] getSuffix() {
        return new String[]{Math.round(delay.getValue()) + "ms"};
    }

    private boolean isDynamicAngle() {
        return dynamicAngle.getValue() == 1;
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (event.getType() != EventType.PRE) {
            return;
        }
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null) {
            clearState();
            return;
        }
        if (this.isInventoryBlocked()) {
            clearActiveState();
            return;
        }

        tickCounter++;
        pruneTargetDelayStates();

        if (!passesItemCondition()) {
            clearActiveState();
            return;
        }

        EntityPlayer target = getTarget();
        if (target == null) {
            clearActiveState();
            return;
        }
        boolean hasKBEnchant = EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;

        // 只要锁定目标就计算方向并显示箭头；active 只决定是否执行位移动作
        dynamicVoidYaw = isDynamicAngle()
                ? findDynamicVoidYaw(target)
                : this.findVoid.getValue() ? findStaticVoidYaw(target) : null;
        if (dynamicVoidYaw == null && !isDynamicAngle()) {
            displaceLeft = this.direction.getValue() == 0;
        }
        renderDisplaceYaw = dynamicVoidYaw != null ? dynamicVoidYaw : isDynamicAngle() ? null : getFixedDisplaceYaw();
        renderTarget = renderDisplaceYaw != null ? target : null;
        if (renderDisplaceYaw == null) {
            clearActiveState();
            return;
        }

        active = hasKBEnchant || anyMovementKey();
        if (!active) {
            return;
        }

        hasKB = hasKBEnchant;
        displaceThisTick = !displaceThisTick;
        if (displaceThisTick && !shouldDisplaceInCurrentWindow(target, tickCounter)) {
            // 延迟窗口：跳过本次位移动作，但箭头继续显示方向
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        if (!displaceThisTick && wasDisplacingLastTick) {
            int key = mc.gameSettings.keyBindAttack.getKeyCode();
            if (key != 0) {
                KeyBinding.onTick(key);
            }
        }

        wasDisplacingLastTick = displaceThisTick;

        if (!displaceThisTick || renderDisplaceYaw == null) {
            return;
        }

        // 每个生命周期执行前，用服务器真实 rotation（lastReportedYaw/lastReportedPitch）做一次 rayTrace：
        // 射线必须命中目标才允许继续位移，否则跳过本次生命周期（箭头仍显示方向）
        if (!serverRotationSeesTarget(target, event.getYaw(), event.getPitch())) {
            displaceThisTick = false;
            compensateNextTick = false;
            wasDisplacingLastTick = false;
            return;
        }

        event.setRotation(renderDisplaceYaw, event.getPitch(), 10);
        event.setPervRotation(renderDisplaceYaw, 10);
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || this.isInventoryBlocked()) {
            clearState();
            return;
        }
        if (!active) {
            compensateNextTick = false;
            return;
        }
        if (compensateNextTick && !displaceThisTick) {
            compensateNextTick = false;
            mc.thePlayer.movementInput.moveStrafe = displaceLeft ? -1.0F : 1.0F;
            return;
        }
        if (!displaceThisTick || hasKB) {
            return;
        }
        if (!anyMovementKey()) {
            return;
        }
        mc.thePlayer.movementInput.moveForward = 1.0F;
        compensateNextTick = true;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        if (!this.isEnabled() || !this.blink.getValue() || !active || !displaceThisTick || releaseBlinkNextTick) {
            return;
        }
        if (!(event.getPacket() instanceof C03PacketPlayer)) {
            return;
        }
        Unfair.blinkManager.setBlinkState(true, BlinkModules.DISPLACE);
        releaseBlinkNextTick = true;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.type() != EventType.POST) {
            return;
        }
        if (releaseBlinkNextTick) {
            releaseBlink();
            releaseBlinkNextTick = false;
        }
    }

    private void releaseBlink() {
        Unfair.blinkManager.setBlinkState(false, BlinkModules.DISPLACE);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null || mc.theWorld == null || !this.showDirection.getValue()) {
            clearArrowState();
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean activeArrow = renderDisplaceYaw != null && renderTarget != null && !renderTarget.isDead;
        Float arrowYaw = renderDisplaceYaw;
        EntityPlayer arrowTarget = renderTarget;
        float alpha = 1.0F;

        if (activeArrow) {
            clearFadingArrow();
        } else {
            if (fadingDisplaceYaw == null || fadingTarget == null || fadingTarget.isDead) {
                clearFadingArrow();
                return;
            }
            long fadeElapsedMs = nowMs - arrowFadeStartMs;
            if (fadeElapsedMs >= ARROW_FADE_MS) {
                clearFadingArrow();
                return;
            }
            arrowYaw = fadingDisplaceYaw;
            arrowTarget = fadingTarget;
            alpha = 1.0F - (float) fadeElapsedMs / (float) ARROW_FADE_MS;
        }

        float partialTicks = event.partialTicks();
        double centerX = arrowTarget.lastTickPosX + (arrowTarget.posX - arrowTarget.lastTickPosX) * partialTicks;
        double centerY = arrowTarget.lastTickPosY + (arrowTarget.posY - arrowTarget.lastTickPosY) * partialTicks + arrowTarget.height * 0.5D;
        double centerZ = arrowTarget.lastTickPosZ + (arrowTarget.posZ - arrowTarget.lastTickPosZ) * partialTicks;

        double yawRad = Math.toRadians(arrowYaw);
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        double baseOffset = arrowTarget.width * 0.5D + ARROW_FORWARD_GAP;
        double tailX = centerX + forwardX * baseOffset;
        double tailZ = centerZ + forwardZ * baseOffset;
        double bodyEndX = tailX + forwardX * ARROW_BODY_LENGTH;
        double bodyEndZ = tailZ + forwardZ * ARROW_BODY_LENGTH;
        double headBackX = tailX + forwardX * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double headBackZ = tailZ + forwardZ * (ARROW_BODY_LENGTH - ARROW_HEAD_BACKSET);
        double tipX = bodyEndX + forwardX * ARROW_HEAD_LENGTH;
        double tipZ = bodyEndZ + forwardZ * ARROW_HEAD_LENGTH;

        double viewerX = mc.getRenderManager().viewerPosX;
        double viewerY = mc.getRenderManager().viewerPosY;
        double viewerZ = mc.getRenderManager().viewerPosZ;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);

        GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.82F * alpha);
        GL11.glBegin(GL11.GL_TRIANGLES);
        GL11.glVertex3d(tailX - viewerX, centerY - viewerY, tailZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, -ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glEnd();

        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.0F, 0.0F, 0.0F, 0.95F * alpha);
        GL11.glBegin(GL11.GL_LINE_LOOP);
        GL11.glVertex3d(tailX - viewerX, centerY - viewerY, tailZ - viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, -ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, -ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glVertex3d(tipX - viewerX, centerY - viewerY, tipZ - viewerZ);
        arrowVertex(headBackX, centerY, headBackZ, ARROW_HEAD_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        arrowVertex(bodyEndX, centerY, bodyEndZ, ARROW_BODY_HALF_HEIGHT, viewerX, viewerY, viewerZ);
        GL11.glEnd();

        GL11.glPopAttrib();
        GL11.glPopMatrix();

        if (activeArrow) {
            lastRenderedDisplaceYaw = arrowYaw;
            lastRenderedTarget = arrowTarget;
            lastRenderedArrowMs = nowMs;
        }
    }

    private void arrowVertex(double x, double y, double z, double verticalOffset, double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y + verticalOffset - viewerY, z - viewerZ);
    }

    private boolean passesItemCondition() {
        boolean kbPass = !this.hasKnockback.getValue() || EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        boolean weaponPass = !this.weaponsOnly.getValue()
                || ItemUtil.isHoldingSword()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool();
        return kbPass && weaponPass;
    }

    private EntityPlayer getTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && KillAura.target != null && KillAura.target.getEntity() instanceof EntityPlayer target && TeamUtil.isEntityLoaded(target)) {
            return target;
        }
        return findClosestTarget();
    }

    private EntityPlayer findClosestTarget() {
        EntityPlayer closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity instanceof EntityPlayer player && this.isValidTarget(player)) {
                double distance = RotationUtil.distanceToEntity(player);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closest = player;
                }
            }
        }
        return closest;
    }

    private boolean isValidTarget(EntityPlayer player) {
        return player != mc.thePlayer
                && !player.isDead
                && player.deathTime == 0
                && RotationUtil.distanceToEntity(player) <= 9.0D
                && !TeamUtil.shouldBlockTarget(player)
                && !TeamUtil.isFriend(player);
    }

    private boolean shouldDisplaceInCurrentWindow(EntityPlayer target, int currentTick) {
        if (target == null) {
            return true;
        }
        int targetId = target.getEntityId();
        Integer windowStartTick = targetWindowStartTicks.get(targetId);
        if (windowStartTick == null || currentTick - windowStartTick >= DISPLACE_WINDOW_TICKS) {
            targetWindowStartTicks.put(targetId, currentTick);
            return true;
        }
        int delayTicks = MathUtil.msToTicks(delay.getValue());
        if (delayTicks <= 0) {
            return true;
        }
        return currentTick - windowStartTick >= delayTicks;
    }

    private void pruneTargetDelayStates() {
        if (mc.theWorld == null) {
            targetWindowStartTicks.clear();
            return;
        }
        Iterator<Map.Entry<Integer, Integer>> iterator = targetWindowStartTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, Integer> entry = iterator.next();
            Entity entity = mc.theWorld.getEntityByID(entry.getKey());
            if (!(entity instanceof EntityPlayer) || entity.isDead || ((EntityPlayer) entity).deathTime != 0) {
                iterator.remove();
            }
        }
    }

    private Float findStaticVoidYaw(EntityPlayer target) {
        if (target == null || mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }

        double bestX = 0.0D;
        double bestZ = 0.0D;
        double bestScore = Double.MAX_VALUE;

        for (int ring = 1; ring <= VOID_SCAN_RINGS; ring++) {
            double radius = ring * VOID_SCAN_STEP;
            boolean foundInRing = false;
            for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
                double x = target.posX + VOID_SCAN_X[i] * radius;
                double z = target.posZ + VOID_SCAN_Z[i] * radius;
                if (!isVoidColumn(x, target.posY, z)) {
                    continue;
                }
                double playerDx = x - mc.thePlayer.posX;
                double playerDz = z - mc.thePlayer.posZ;
                double score = radius * radius * 1000.0D + playerDx * playerDx + playerDz * playerDz;
                if (score < bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestZ = z;
                    foundInRing = true;
                }
            }
            if (foundInRing) {
                break;
            }
        }

        if (bestScore == Double.MAX_VALUE) {
            return null;
        }
        updateDisplaceSide(target, bestX, bestZ);
        return getYawTo(target, bestX, bestZ);
    }

    private Float findDynamicVoidYaw(EntityPlayer target) {
        if (target == null || mc.thePlayer == null || mc.theWorld == null) {
            return null;
        }

        double bestForwardX = 0.0D;
        double bestForwardZ = 0.0D;
        double bestScore = 0.0D;

        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double forwardX = VOID_SCAN_X[i];
            double forwardZ = VOID_SCAN_Z[i];
            double score = scoreVoidPath(target, forwardX, forwardZ);
            if (score > bestScore) {
                bestScore = score;
                bestForwardX = forwardX;
                bestForwardZ = forwardZ;
            }
        }

        if (bestScore <= 0.0D) {
            return null;
        }
        updateDisplaceSide(target, target.posX + bestForwardX, target.posZ + bestForwardZ);
        return yawFromForward(bestForwardX, bestForwardZ);
    }

    private float yawFromForward(double forwardX, double forwardZ) {
        return (float) (Math.toDegrees(Math.atan2(forwardZ, forwardX)) - 90.0D);
    }

    private double scoreVoidPath(EntityPlayer target, double forwardX, double forwardZ) {
        double sideX = -forwardZ;
        double sideZ = forwardX;
        double score = 0.0D;
        double checkedForward = 0.0D;
        int consecutiveCenterVoid = 0;
        AxisAlignedBB baseCollisionBox = target.getEntityBoundingBox().contract(DYNAMIC_COLLISION_INSET, 0.0D, DYNAMIC_COLLISION_INSET);

        for (int step = 1; step <= (int) (DYNAMIC_SCAN_DISTANCE / DYNAMIC_SCAN_STEP); step++) {
            double forward = step * DYNAMIC_SCAN_STEP;
            if (!isDynamicPathClear(target, baseCollisionBox, forwardX, forwardZ, checkedForward, forward)) {
                break;
            }
            checkedForward = forward;

            boolean centerVoid = false;
            for (int side = -1; side <= 1; side++) {
                double sideOffset = side * DYNAMIC_SCAN_SIDE_STEP;
                double x = target.posX + forwardX * forward + sideX * sideOffset;
                double z = target.posZ + forwardZ * forward + sideZ * sideOffset;
                if (isVoidColumn(x, target.posY, z)) {
                    double laneWeight = side == 0 ? 1.4D : 1.0D;
                    score += laneWeight * (DYNAMIC_SCAN_DISTANCE + DYNAMIC_SCAN_STEP - forward);
                    centerVoid |= side == 0;
                }
            }

            if (centerVoid) {
                consecutiveCenterVoid++;
                score += consecutiveCenterVoid * 2.0D;
            } else {
                consecutiveCenterVoid = 0;
            }
        }
        return score;
    }

    private boolean isDynamicPathClear(EntityPlayer target, AxisAlignedBB baseCollisionBox, double forwardX, double forwardZ, double fromForward, double toForward) {
        for (double forward = fromForward + DYNAMIC_WALL_CHECK_STEP; forward <= toForward + 1.0E-4D; forward += DYNAMIC_WALL_CHECK_STEP) {
            AxisAlignedBB checkBox = baseCollisionBox.offset(forwardX * forward, 0.0D, forwardZ * forward);
            if (hasBlockCollision(target, checkBox)) {
                return false;
            }
        }
        return true;
    }

    private boolean hasBlockCollision(EntityPlayer target, AxisAlignedBB box) {
        int minX = MathHelper.floor_double(box.minX);
        int maxX = MathHelper.floor_double(box.maxX + 1.0D);
        int minY = MathHelper.floor_double(box.minY);
        int maxY = MathHelper.floor_double(box.maxY + 1.0D);
        int minZ = MathHelper.floor_double(box.minZ);
        int maxZ = MathHelper.floor_double(box.maxZ + 1.0D);

        List<AxisAlignedBB> collisions = new ArrayList<>();
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        for (int blockX = minX; blockX < maxX; blockX++) {
            for (int blockZ = minZ; blockZ < maxZ; blockZ++) {
                if (!mc.theWorld.isBlockLoaded(blockPos.set(blockX, 64, blockZ))) {
                    return true;
                }
                for (int blockY = minY; blockY < maxY; blockY++) {
                    if (blockY < 0 || blockY >= 256) {
                        return true;
                    }
                    blockPos.set(blockX, blockY, blockZ);
                    IBlockState state = mc.theWorld.getBlockState(blockPos);
                    state.getBlock().addCollisionBoxesToList(mc.theWorld, blockPos, state, box, collisions, target);
                    if (!collisions.isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isVoidColumn(double x, double y, double z) {
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        int startY = MathHelper.floor_double(y) - 1;
        int endY = Math.max(0, startY - VOID_SCAN_DEPTH);
        for (int blockY = startY; blockY >= endY; blockY--) {
            if (!mc.theWorld.isAirBlock(new BlockPos(blockX, blockY, blockZ))) {
                return false;
            }
        }
        return true;
    }

    private void updateDisplaceSide(EntityPlayer target, double voidX, double voidZ) {
        double targetDx = target.posX - mc.thePlayer.posX;
        double targetDz = target.posZ - mc.thePlayer.posZ;
        double voidDx = voidX - mc.thePlayer.posX;
        double voidDz = voidZ - mc.thePlayer.posZ;
        displaceLeft = targetDx * voidDz - targetDz * voidDx < 0.0D;
    }

    private float getFixedDisplaceYaw() {
        float offset = this.yawOffset.getValue();
        return mc.thePlayer.rotationYaw + (displaceLeft ? -offset : offset);
    }

    private boolean serverRotationSeesTarget(EntityPlayer target, float serverYaw, float serverPitch) {
        if (target == null || mc.thePlayer == null || mc.theWorld == null) {
            return false;
        }
        double distance = Math.max(RotationUtil.distanceToEntity(target) + 1.0D, 4.5D);
        RayCastUtil.RayCastResult result = RayCastUtil.rayCast(
                new RotationUtil.RotationVec(serverYaw, serverPitch), distance, 0.1F
        );
        return result != null
                && result.typeOfHit == RayCastUtil.RayCastResult.Type.ENTITY
                && result.entityHit == target;
    }

    private Float getYawTo(EntityPlayer target, double x, double z) {
        double dx = x - target.posX;
        double dz = z - target.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001D) {
            return null;
        }
        double radius = Math.min(distance, Math.max(0.35D, target.width * 0.5D + 0.15D));
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        double aimX = target.posX + dx / distance * radius;
        double aimZ = target.posZ + dz / distance * radius;
        return RotationUtil.getRotations(aimX, target.posY + target.getEyeHeight() * 0.5D, aimZ, eyes.xCoord, eyes.yCoord, eyes.zCoord)[0];
    }

    private boolean anyMovementKey() {
        return mc.gameSettings.keyBindForward.isKeyDown()
                || mc.gameSettings.keyBindBack.isKeyDown()
                || mc.gameSettings.keyBindLeft.isKeyDown()
                || mc.gameSettings.keyBindRight.isKeyDown();
    }

    private void clearState() {
        active = false;
        displaceThisTick = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        dynamicVoidYaw = null;
        renderDisplaceYaw = null;
        renderTarget = null;
    }

    private void clearActiveState() {
        startArrowFade();
        clearState();
    }

    private void clearFadingArrow() {
        fadingDisplaceYaw = null;
        fadingTarget = null;
        arrowFadeStartMs = 0L;
    }

    private void clearArrowState() {
        clearFadingArrow();
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private void startArrowFade() {
        long nowMs = System.currentTimeMillis();
        if (lastRenderedDisplaceYaw != null && lastRenderedTarget != null && !lastRenderedTarget.isDead
                && nowMs - lastRenderedArrowMs <= ARROW_FADE_MS) {
            fadingDisplaceYaw = lastRenderedDisplaceYaw;
            fadingTarget = lastRenderedTarget;
            arrowFadeStartMs = nowMs;
        }
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private boolean isInventoryBlocked() {
        return this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer;
    }
}
