package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.LeftClickMouseEvent;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.world.BedNuker;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.client.KeyBindUtil;
import cn.unfair.util.client.MathUtil;
import cn.unfair.util.client.TeamUtil;
import cn.unfair.util.player.ItemUtil;
import cn.unfair.util.rotation.RotationUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.input.Mouse;

public class AutoBlock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final IntProperty maxHurtTime = new IntProperty("MaxHurtTime", 200, 1, 500);
    public final IntProperty maxHoldTime = new IntProperty("MaxHoldTime", 150, 1, 1000);
    public final IntProperty randomHold = new IntProperty("RandomHold", 0, 0, 1000);
    public final IntProperty cooldown = new IntProperty("Cooldown", 0, 0, 1000);
    public final FloatProperty range = new FloatProperty("Range", 4.0F, 2.0F, 6.0F);
    public final PercentProperty lagChance = new PercentProperty("LagChance", 100);
    public final IntProperty lagDuration = new IntProperty("LagDuration", 200, 50, 500);
    public final BooleanProperty disableWhileChasing = new BooleanProperty("DisableWhileChasing", false);
    public final BooleanProperty forceBlockAnimation = new BooleanProperty("ForceBlockAnimation", true);
    public final BooleanProperty ignoreMobs = new BooleanProperty("IgnoreMobs", false);
    public final BooleanProperty ignoreTeammates = new BooleanProperty("IgnoreTeammates", false);
    public final BooleanProperty onlyWhileLeftClicking = new BooleanProperty("OnlyWhileLeftClicking", false);
    public final BooleanProperty onlyWhileRightClicking = new BooleanProperty("OnlyWhileRightClicking", false);
    public final BooleanProperty onlyWhileTargeting = new BooleanProperty("OnlyWhileTargeting", false);
    public final BooleanProperty flushOnAttack = new BooleanProperty("FlushOnAttack", true);
    public final BooleanProperty flushOnInteractItem = new BooleanProperty("FlushOnInteractItem", true);

    private boolean fakeBlocking;
    private boolean hasTarget;
    private int holdTicks;
    private int cooldownTicks;
    private boolean hadSword;
    private boolean lagging;
    private long lagUntil;
    private boolean startLag;
    private boolean flush;
    private int lagAccumulator;
    private long lastLeftClick;

    public AutoBlock() {
        super("AutoBlock", false, true);
    }

    @Override
    public void onEnabled() {
        hadSword = false;
        flush = false;
        startLag = false;
        fakeBlocking = false;
        lagAccumulator = 0;
        if (lagging) {
            stopLag();
        }
        if (holdTicks > 0) {
            holdTicks = 0;
            releaseUse();
        }
        cooldownTicks = 0;
    }

    @Override
    public void onDisabled() {
        this.onEnabled();
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        lastLeftClick = System.currentTimeMillis();
    }

    @EventTarget(Priority.LOW)
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.PRE) {
            return;
        }
        if (!isReady()) {
            hasTarget = false;
            releaseAll();
            return;
        }
        if (isBedNukerActive()) {
            hasTarget = false;
            releaseAll();
            return;
        }
        hasTarget = hasValidTarget();
        if (flush) {
            flush = false;
            if (lagging) {
                stopLag();
            }
        }
        if (startLag) {
            startLag = false;
            if (!lagging) {
                startLagging();
            }
            lagUntil = System.currentTimeMillis() + (long) lagDuration.getValue();
        }
        if (lagging && System.currentTimeMillis() >= lagUntil) {
            stopLag();
        }
        boolean right = Mouse.isButtonDown(1);
        boolean sword = ItemUtil.isHoldingSword();
        boolean manual = right && !onlyWhileRightClicking.getValue();
        boolean keepHeld = right && (!onlyWhileRightClicking.getValue() || !sword);
        boolean block;
        if (!forceBlockAnimation.getValue()) {
            block = canBlock() && hurtTimeOk() && conditions();
            fakeBlocking = false;
        } else {
            fakeBlocking = canBlock() && conditions();
            block = fakeBlocking && hurtTimeOk();
        }
        if (cooldownTicks > 0) {
            --cooldownTicks;
        }
        if (holdTicks > 0) {
            --holdTicks;
            if (!block || holdTicks == 0) {
                holdTicks = 0;
                if (!keepHeld) {
                    releaseUse();
                }
            }
        } else if (block && !manual && cooldownTicks == 0) {
            int n = MathUtil.msToTicks(maxHoldTime.getValue());
            int n2 = 0;
            if (randomHold.getValue() > 0) {
                n2 = MathUtil.msToTicks(randomHold.getValue());
                n2 = Math.min(MathUtil.getRandom(0, n2 + 1), MathUtil.getRandom(0, n2 + 1));
            }
            holdTicks = n + n2;
            pressUse();
        }
        if (onlyWhileRightClicking.getValue() && right && !sword && hadSword) {
            pressUse();
        }
        hadSword = sword;
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.isCancelled()) {
            return;
        }
        if (event.getType() == EventType.RECEIVE) {
            if (event.getPacket() instanceof S12PacketEntityVelocity velocity
                    && velocity.getEntityID() == mc.thePlayer.getEntityId()
                    && holdTicks > 0) {
                holdTicks = 0;
                releaseUse();
            }
            return;
        }
        if (event.getType() != EventType.SEND) {
            return;
        }
        if (lagging) {
            if (event.getPacket() instanceof C02PacketUseEntity packet) {
                if (flushOnAttack.getValue() && packet.getAction() == C02PacketUseEntity.Action.ATTACK) {
                    flush = true;
                }
            } else if (event.getPacket() instanceof C08PacketPlayerBlockPlacement packet
                    && flushOnInteractItem.getValue()
                    && !isSword(packet.getStack())) {
                flush = true;
            }
        } else if (event.getPacket() instanceof C08PacketPlayerBlockPlacement packet) {
            if (packet.getPlacedBlockDirection() == 255 && isSword(packet.getStack()) && rollLag()) {
                startLag = true;
            }
        }
    }

    public boolean isActive() {
        return this.isEnabled() && (holdTicks > 0 || lagging);
    }

    public boolean hasTarget() {
        return hasTarget;
    }

    private boolean isReady() {
        return mc.thePlayer != null
                && mc.theWorld != null
                && !mc.thePlayer.isDead
                && mc.currentScreen == null;
    }

    private boolean isBedNukerActive() {
        BedNuker bedNuker = (BedNuker) Unfair.moduleManager.getModule(BedNuker.class);
        return bedNuker != null
                && bedNuker.isEnabled()
                && (bedNuker.isReady() || bedNuker.isBreaking());
    }

    private boolean hurtTimeOk() {
        int n = MathUtil.msToTicks(maxHurtTime.getValue());
        return mc.thePlayer.hurtTime <= n;
    }

    private boolean canBlock() {
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        boolean killAuraBlocks = KillAura.target != null
                && killAura != null
                && killAura.autoBlock.getValue() != 0
                && killAura.autoBlock.getValue() != 3;
        return isReady() && !killAuraBlocks && ItemUtil.isHoldingSword();
    }

    private boolean conditions() {
        if (onlyWhileTargeting.getValue() && !targetMatches(ignoreMobs.getValue() ? 0 : 1)) {
            return false;
        }
        if (onlyWhileLeftClicking.getValue() && !isClicking()) {
            return false;
        }
        if (onlyWhileRightClicking.getValue() && !Mouse.isButtonDown(1)) {
            return false;
        }
        return enemyInRange();
    }

    private boolean hasValidTarget() {
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY) {
            return false;
        }
        if (!(mc.objectMouseOver.entityHit instanceof EntityPlayer player)) {
            return false;
        }
        return player != mc.thePlayer
                && !player.isDead
                && player.deathTime == 0
                && !TeamUtil.isFriend(player)
                && !TeamUtil.shouldBlockTarget(player)
                && mc.thePlayer.getDistanceSqToEntity(player) <= (double) (range.getValue() * range.getValue());
    }

    private boolean enemyInRange() {
        if (range.getValue() >= range.getMaximum()) {
            return true;
        }
        double rangeValue = range.getValue();
        double rangeSq = (rangeValue + 1.2D) * (rangeValue + 1.2D);
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase living)) {
                continue;
            }
            if (living == mc.thePlayer || living.deathTime != 0) {
                continue;
            }
            if (Unfair.botManager.contains(entity)) {
                continue;
            }
            if (mc.thePlayer.getDistanceSqToEntity(entity) > rangeSq) {
                continue;
            }
            if (living instanceof EntityPlayer player) {
                if (TeamUtil.isFriend(player)) {
                    continue;
                }
                if (Unfair.botManager.contains(player)) {
                    continue;
                }
            } else if (ignoreMobs.getValue() || !isMob(living)) {
                continue;
            }
            if (disableWhileChasing.getValue() && awayFromPlayer(living)) {
                continue;
            }
            if (!rayHits(living, rangeValue)) {
                continue;
            }
            if (ignoreTeammates.getValue() && TeamUtil.shouldBlockTeamColor(living)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private boolean isMob(Entity entity) {
        return entity instanceof EntityMob || entity instanceof EntitySlime;
    }

    private boolean rayHits(Entity entity, double range) {
        AxisAlignedBB box = entity.getEntityBoundingBox().expand(
                entity.getCollisionBorderSize(), entity.getCollisionBorderSize(), entity.getCollisionBorderSize()
        );
        Vec3 eyes = mc.thePlayer.getPositionEyes(1.0F);
        Vec3 toBox = RotationUtil.getClosestPointOnBox(eyes, box).subtract(eyes);
        double distance = toBox.lengthVector();
        if (distance < 1.0E-4D) {
            return true;
        }
        Vec3 to = eyes.addVector(toBox.xCoord / distance * range, toBox.yCoord / distance * range, toBox.zCoord / distance * range);
        return box.calculateIntercept(eyes, to) != null;
    }

    private boolean awayFromPlayer(EntityLivingBase entity) {
        return mc.thePlayer.getDistanceSqToEntity(entity) > 1.0D && !facingPlayer(entity, 90.0F);
    }

    private boolean facingPlayer(EntityLivingBase entity, float fov) {
        float yawTo = (float) Math.toDegrees(Math.atan2(
                mc.thePlayer.posZ - entity.posZ, mc.thePlayer.posX - entity.posX
        )) - 90.0F;
        return Math.abs(MathHelper.wrapAngleTo180_double(yawTo - entity.rotationYaw)) <= (double) fov;
    }

    private boolean isClicking() {
        AutoClicker autoClicker = (AutoClicker) Unfair.moduleManager.getModule(AutoClicker.class);
        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        if (autoClicker != null && autoClicker.isEnabled()
                || killAura != null && killAura.isEnabled() && killAura.getTarget() != null && killAura.requirePress.getValue()) {
            return Mouse.isButtonDown(0);
        }
        return System.currentTimeMillis() - lastLeftClick < 220L;
    }

    private boolean targetMatches(int type) {
        Entity killAuraTarget = KillAura.target != null ? KillAura.target.getEntity() : null;
        Entity hovered = mc.objectMouseOver != null ? mc.objectMouseOver.entityHit : null;
        return matches(killAuraTarget, type) || matches(hovered, type);
    }

    private boolean matches(Entity entity, int type) {
        if (entity == null) {
            return false;
        }
        if (type == 0) {
            return entity instanceof EntityPlayer;
        }
        if (type == 1) {
            return entity instanceof EntityPlayer || isMob(entity);
        }
        return true;
    }

    private static boolean isSword(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemSword;
    }

    private void pressUse() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        if (key == 0) {
            return;
        }
        KeyBindUtil.setKeyBindState(key, true);
        KeyBindUtil.pressKeyOnce(key);
    }

    private void releaseUse() {
        int key = mc.gameSettings.keyBindUseItem.getKeyCode();
        if (key == 0) {
            return;
        }
        KeyBindUtil.setKeyBindState(key, false);
        KeyBindUtil.updateKeyState(key);
    }

    private void startLagging() {
        Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTOBLOCK);
        lagging = true;
    }

    private void stopLag() {
        flush = false;
        lagging = false;
        Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTOBLOCK);
    }

    private void releaseAll() {
        if (lagging) {
            stopLag();
        }
        if (holdTicks > 0) {
            holdTicks = 0;
            releaseUse();
        }
        cooldownTicks = 0;
    }

    private boolean rollLag() {
        if (lagChance.getValue() <= 0 || mc.isSingleplayer()) {
            return false;
        }
        if ((lagAccumulator += lagChance.getValue()) >= 100) {
            lagAccumulator -= 100;
            return true;
        }
        return false;
    }
}
