package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.module.Module;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.ModeProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.PlayerUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.HashMap;
import java.util.Iterator;
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

    static {
        for (int i = 0; i < VOID_SCAN_DIRECTIONS; i++) {
            double angle = Math.PI * 2.0D * i / VOID_SCAN_DIRECTIONS;
            VOID_SCAN_X[i] = Math.cos(angle);
            VOID_SCAN_Z[i] = Math.sin(angle);
        }
    }

    public final FloatProperty yawOffset = new FloatProperty("Yaw Offset", 90.0F, 0.0F, 180.0F);
    public final FloatProperty delay = new FloatProperty("Delay", 500.0F, 0.0F, 1000.0F);
    public final ModeProperty direction = new ModeProperty("Direction", 0, new String[]{"Left", "Right"});
    public final BooleanProperty findVoid = new BooleanProperty("Find Void", false);
    public final BooleanProperty hasKnockback = new BooleanProperty("Has Knockback", false);
    public final BooleanProperty weaponsOnly = new BooleanProperty("Weapons Only", false);
    public final BooleanProperty allowTools = new BooleanProperty("Allow Tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty inventoryCheck = new BooleanProperty("Inventory Check", true);
    private final Map<Integer, Integer> targetWindowStartTicks = new HashMap<>();
    private boolean displaceThisTick;
    private boolean active;
    private boolean compensateNextTick;
    private boolean displaceLeft;
    private boolean wasDisplacingLastTick;
    private Float renderDisplaceYaw;
    private Float lastRenderedDisplaceYaw;
    private EntityPlayer lastRenderedTarget;
    private long lastRenderedArrowMs;
    private int tickCounter;
    private int activeTargetId = -1;

    public Displace() {
        super("Displace", false);
    }

    private static int msToTicks(double ms) {
        if (ms <= 0.0D) {
            return 0;
        }
        return (int) Math.ceil(ms / 50.0D);
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
        boolean hasKBEnchant = EnchantmentHelper.getKnockbackModifier(mc.thePlayer) > 0;
        if (target != null && target.getEntityId() != activeTargetId) {
            activeTargetId = target.getEntityId();
            resetDisplaceCycle();
        }

        active = target != null && (hasKBEnchant || mc.thePlayer.isSprinting());
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

        displaceThisTick = !displaceThisTick;
        if (!displaceThisTick && wasDisplacingLastTick) {
            int key = mc.gameSettings.keyBindAttack.getKeyCode();
            if (key != 0) {
                KeyBinding.onTick(key);
            }
        }

        wasDisplacingLastTick = displaceThisTick;
        renderDisplaceYaw = yaw;

        if (!displaceThisTick) {
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
        if (!PlayerUtil.isAttacking()) {
            return null;
        }
        MovingObjectPosition mouseOver = mc.objectMouseOver;
        if (mouseOver == null || mouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY || !(mouseOver.entityHit instanceof EntityPlayer target)) {
            return null;
        }
        return this.isValidTarget(target) ? target : null;
    }

    private boolean isValidTarget(EntityPlayer player) {
        return player != mc.thePlayer
                && !player.isDead
                && player.deathTime == 0
                && RotationUtil.distanceToEntity(player) <= 9.0D
                && !TeamUtil.shouldBlockTarget(player)
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
        activeTargetId = -1;
        displaceThisTick = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
        active = false;
        renderDisplaceYaw = null;
    }

    private void resetDisplaceCycle() {
        displaceThisTick = false;
        compensateNextTick = false;
        wasDisplacingLastTick = false;
    }

    private void clearActiveState() {
        startArrowFade();
        clearState();
    }

    private void clearFadingArrow() {
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
        }
        lastRenderedDisplaceYaw = null;
        lastRenderedTarget = null;
        lastRenderedArrowMs = 0L;
    }

    private boolean isInventoryBlocked() {
        return this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer;
    }

}
