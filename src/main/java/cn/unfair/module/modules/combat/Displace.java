package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.Render3DEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.PlayerUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.TeamUtil;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Displace extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private static final int DISPLACE_WINDOW_TICKS = 10;
    private static final int VOID_SCAN_DIRECTIONS = 32;
    private static final int VOID_SCAN_RINGS = 12;
    private static final int VOID_SCAN_DEPTH = 10;
    private static final double VOID_SCAN_STEP = 0.5D;
    private static final double[] VOID_SCAN_X = new double[VOID_SCAN_DIRECTIONS];
    private static final double[] VOID_SCAN_Z = new double[VOID_SCAN_DIRECTIONS];
    private static final long ARROW_FADE_MS = 250L;
    private static final double ARROW_FORWARD_GAP = 0.24D;
    private static final double ARROW_BODY_LENGTH = 0.74D;
    private static final double ARROW_BODY_HALF_HEIGHT = 0.08D;
    private static final double ARROW_HEAD_BACKSET = 0.18D;
    private static final double ARROW_HEAD_LENGTH = 0.52D;
    private static final double ARROW_HEAD_HALF_HEIGHT = 0.30D;

    public final FloatProperty yawOffset = new FloatProperty("yaw-offset", 90.0F, 0.0F, 180.0F);
    public final FloatProperty delay = new FloatProperty("delay", 0.0F, 0.0F, 500.0F);
    public final ModeProperty direction = new ModeProperty("direction", 0, new String[]{"LEFT", "RIGHT"});
    public final BooleanProperty showDirection = new BooleanProperty("show-direction", true);
    public final BooleanProperty findVoid = new BooleanProperty("find-void", false);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("ignore-teammates", true);
    public final BooleanProperty hasKnockback = new BooleanProperty("has-knockback", false);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", false);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);

    private boolean displaceThisTick;
    private boolean active;
    private boolean hasKB;
    private boolean compensateNextTick;
    private boolean displaceLeft;
    private boolean wasDisplacingLastTick;
    private Float renderDisplaceYaw;
    private EntityPlayer renderTarget;
    private Float fadingDisplaceYaw;
    private EntityPlayer fadingTarget;
    private long arrowFadeStartMs;
    private Float lastRenderedDisplaceYaw;
    private EntityPlayer lastRenderedTarget;
    private long lastRenderedArrowMs;
    private int tickCounter;
    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();

    static {
        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double angle = Math.PI * 2.0D * i / VOID_SCAN_DIRECTIONS;
            VOID_SCAN_X[i] = Math.cos(angle);
            VOID_SCAN_Z[i] = Math.sin(angle);
        }
    }

    public Displace() {
        super("Displace", false);
    }

    @Override
    public void onEnabled() {
        clearState();
        clearArrowState();
        tickCounter = 0;
        targetWindowStartTicks.clear();
    }

    @Override
    public void onDisabled() {
        clearState();
        clearArrowState();
        targetWindowStartTicks.clear();
    }

    @Override
    public String[] getSuffix() {
        return new String[]{this.yawOffset.getValue() + " yaw"};
    }

    @EventTarget(Priority.HIGHEST)
    public void onUpdate(UpdateEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.PRE || mc.thePlayer == null || mc.theWorld == null) {
            clearState();
            return;
        }

        tickCounter++;
        pruneTargetDelayStates();

        if (!passesItemCondition()) {
            clearActiveState();
            return;
        }

        EntityPlayer target = getTarget();
        boolean hasKBEnchant = EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        active = target != null && (hasKBEnchant || anyMovementKey());
        if (!active) {
            clearActiveState();
            return;
        }

        Float yaw = this.findVoid.getValue() ? findStaticVoidYaw(target) : null;
        if (yaw == null) {
            displaceLeft = this.direction.getValue() == 0;
            yaw = getFixedDisplaceYaw();
        }

        if (!shouldDisplaceInCurrentWindow(target, tickCounter)) {
            clearActiveState();
            return;
        }

        hasKB = hasKBEnchant;
        displaceThisTick = !displaceThisTick;
        if (!displaceThisTick && wasDisplacingLastTick) {
            int key = mc.gameSettings.keyBindAttack.getKeyCode();
            if (key != 0) {
                KeyBinding.onTick(key);
            }
        }

        wasDisplacingLastTick = displaceThisTick;
        renderDisplaceYaw = yaw;
        renderTarget = target;

        if (!displaceThisTick) {
            return;
        }

        event.setRotation(renderDisplaceYaw, event.getPitch(), 10);
        event.setPervRotation(renderDisplaceYaw, 10);
    }

    @EventTarget(Priority.LOWEST)
    public void onMoveInput(MoveInputEvent event) {
        if (!this.isEnabled() || mc.thePlayer == null) {
            return;
        }
        if (compensateNextTick && !displaceThisTick) {
            compensateNextTick = false;
            mc.thePlayer.movementInput.moveStrafe = displaceLeft ? -1.0F : 1.0F;
            return;
        }
        if (displaceThisTick && anyMovementKey() && EnchantmentHelper.getKnockbackModifier(mc.thePlayer) <= 0) {
            mc.thePlayer.movementInput.moveForward = 1.0F;
            compensateNextTick = true;
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!this.isEnabled() || !this.showDirection.getValue() || mc.thePlayer == null || mc.theWorld == null) {
            clearArrowState();
            return;
        }

        long nowMs = System.currentTimeMillis();
        boolean activeArrow = active && renderDisplaceYaw != null && renderTarget != null && !renderTarget.isDead;
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
        double sideX = -forwardZ;
        double sideZ = forwardX;
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

    private boolean passesItemCondition() {
        boolean kbPass = !this.hasKnockback.getValue() || EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        boolean weaponPass = !this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool();
        return kbPass && weaponPass;
    }

    private EntityPlayer getTarget() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (killAura != null && killAura.isEnabled() && KillAura.target != null && KillAura.target.getEntity() instanceof EntityPlayer target && TeamUtil.isEntityLoaded(target)) {
            return target;
        }
        if (!Mouse.isButtonDown(0)) {
            return null;
        }
        return mc.theWorld.loadedEntityList.stream()
                .filter(EntityPlayer.class::isInstance)
                .map(EntityPlayer.class::cast)
                .filter(this::isValidTarget)
                .min(Comparator.comparingDouble(RotationUtil::distanceToEntity))
                .orElse(null);
    }

    private boolean isValidTarget(EntityPlayer player) {
        return player != mc.thePlayer
                && !player.isDead
                && player.deathTime == 0
                && RotationUtil.distanceToEntity(player) <= 9.0D
                && (!ignoreTeammates.getValue() || !TeamUtil.shouldBlockTarget(player))
                && !Unfair.friendManager.isFriend(player.getName());
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
        int delayTicks = msToTicks(delay.getValue());
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

    private static int msToTicks(double ms) {
        if (ms <= 0.0D) {
            return 0;
        }
        return (int) Math.ceil(ms / 50.0D);
    }

    private Float findStaticVoidYaw(EntityPlayer target) {
        double bestX = 0.0D;
        double bestZ = 0.0D;
        double bestScore = Double.MAX_VALUE;

        for (int ring = 1; ring <= VOID_SCAN_RINGS; ring++) {
            double radius = ring * VOID_SCAN_STEP;
            boolean found = false;
            for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
                double x = target.posX + VOID_SCAN_X[i] * radius;
                double z = target.posZ + VOID_SCAN_Z[i] * radius;
                if (!isVoidColumn(x, target.posY, z)) {
                    continue;
                }
                double dx = x - mc.thePlayer.posX;
                double dz = z - mc.thePlayer.posZ;
                double score = radius * radius * 1000.0D + dx * dx + dz * dz;
                if (score < bestScore) {
                    bestScore = score;
                    bestX = x;
                    bestZ = z;
                    found = true;
                }
            }
            if (found) {
                break;
            }
        }

        if (bestScore == Double.MAX_VALUE) {
            return null;
        }
        updateDisplaceSide(target, bestX, bestZ);
        return getYawTo(target, bestX, bestZ);
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

    private float getYawTo(EntityPlayer target, double x, double z) {
        double dx = x - target.posX;
        double dz = z - target.posZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.001D) {
            return getFixedDisplaceYaw();
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
        displaceThisTick = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        active = false;
        hasKB = false;
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

    private void arrowVertex(double x, double y, double z, double verticalOffset, double viewerX, double viewerY, double viewerZ) {
        GL11.glVertex3d(x - viewerX, y + verticalOffset - viewerY, z - viewerZ);
    }
}
