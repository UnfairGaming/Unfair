package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.PacketEvent;
import cn.unfair.events.RenderItemEvent;
import cn.unfair.events.RightClickMouseEvent;
import cn.unfair.events.TickEvent;
import cn.unfair.events.UseItemEvent;
import cn.unfair.module.Module;
import cn.unfair.module.modules.misc.BedNuker;
import cn.unfair.property.properties.BooleanProperty;
import cn.unfair.property.properties.FloatProperty;
import cn.unfair.property.properties.IntProperty;
import cn.unfair.property.properties.PercentProperty;
import cn.unfair.util.ItemUtil;
import cn.unfair.util.KeyBindUtil;
import cn.unfair.util.RotationUtil;
import cn.unfair.util.TeamUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.input.Mouse;

public class Autoblock extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public final FloatProperty range = new FloatProperty("Range", 4.0F, 2.0F, 6.0F);
    public final IntProperty maxHurtTime = new IntProperty("Maximum Hurt Time", 200, 50, 500);
    public final IntProperty maxHoldTime = new IntProperty("Maximum Hold Time", 150, 50, 500);
    public final PercentProperty lagChance = new PercentProperty("Lag Chance", 100);
    public final IntProperty lagMaxDuration = new IntProperty("Lag Max Duration", 200, 50, 500);
    public final BooleanProperty preventDelayAttacks = new BooleanProperty("Prevent Delaying Attacks", true);
    public final BooleanProperty blockAgainImmediately = new BooleanProperty("Block Again Immediately", true);
    public final BooleanProperty forceBlockAnimation = new BooleanProperty("Force Block Animation", true);
    public final BooleanProperty requireLmb = new BooleanProperty("Require Left Mouse", true);
    public final BooleanProperty requireRmb = new BooleanProperty("Require Right Mouse", false);
    public final BooleanProperty onlyWhenDamaged = new BooleanProperty("Damaged", false);

    private boolean blocking;
    private boolean manualBlock;
    private int blockStartTick = -1;
    private EntityPlayer currentTarget;
    private int lastSelfHurtTime;
    private boolean lagging;
    private int lagStartTick = -1;
    private int tickCounter;

    public Autoblock() {
        super("Autoblock", false, true);
    }

    @Override
    public void onEnabled() {
        this.tickCounter = 0;
        this.resetState(false);
    }

    @Override
    public void onDisabled() {
        this.resetState(true);
    }

    @EventTarget(Priority.HIGHEST)
    public void onRightClickMouse(RightClickMouseEvent event) {
        if (this.shouldBlockVanillaUse()
                || this.isEnabled()
                && this.isReady()
                && ItemUtil.isHoldingSword()
                && !this.isBedNukerActive()
                && !this.blocking) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.HIGHEST)
    public void onUseItem(UseItemEvent event) {
        if (this.shouldBlockVanillaUse()) {
            event.setCancelled(true);
        }
    }

    @EventTarget(Priority.HIGH)
    public void onPacket(PacketEvent event) {
        if (!this.isEnabled() || event.getType() != EventType.SEND || event.isCancelled()) {
            return;
        }
        if (this.isBedNukerActive()) {
            this.releaseLag();
            return;
        }
        if (!this.lagging
                || !this.preventDelayAttacks.getValue()
                || !(event.getPacket() instanceof C02PacketUseEntity packet)
                || packet.getAction() != C02PacketUseEntity.Action.ATTACK) {
            return;
        }
        this.releaseLag();
        if (this.blockAgainImmediately.getValue() && ItemUtil.isHoldingSword()) {
            this.startBlocking(this.tickCounter);
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (!this.isEnabled() || event.type() != EventType.PRE) {
            return;
        }
        if (!this.isReady()) {
            this.resetState(true);
            return;
        }
        if (this.isBedNukerActive()) {
            this.resetState(true);
            return;
        }

        int selfHurtTime = mc.thePlayer.hurtTime;
        boolean hurtAgain = selfHurtTime > this.lastSelfHurtTime;
        this.lastSelfHurtTime = selfHurtTime;

        if (!ItemUtil.isHoldingSword()) {
            this.resetState(false);
            return;
        }

        this.tickCounter++;
        int currentTick = this.tickCounter;
        this.currentTarget = this.findTarget();

        KillAura killAura = (KillAura) Unfair.moduleManager.getModule(KillAura.class);
        boolean killAuraAttacking = killAura != null
                && killAura.isEnabled()
                && KillAura.target != null;
        boolean rmbDown = Mouse.isButtonDown(1);
        boolean lmbDown = Mouse.isButtonDown(0) || killAuraAttacking;

        if (!rmbDown && this.requireRmb.getValue()) {
            this.resetState(true);
            return;
        }
        if (!lmbDown) {
            if (this.lagging) {
                this.releaseLag();
            }
            if (rmbDown && !this.blocking) {
                this.startBlocking(currentTick);
                this.manualBlock = true;
            } else if (!rmbDown) {
                this.resetState(true);
            }
            return;
        }
        if (this.manualBlock) {
            this.stopBlocking(true);
            this.manualBlock = false;
        }

        boolean conditionsMet = this.currentTarget != null && this.checkConditions(lmbDown, rmbDown);
        if (this.lagging) {
            int lagMaxTicks = msToTicks(this.lagMaxDuration.getValue());
            boolean lagExpired = lagMaxTicks > 0
                    && this.lagStartTick >= 0
                    && currentTick - this.lagStartTick >= lagMaxTicks;
            if (lagExpired || !conditionsMet) {
                this.releaseLag();
                if (lagExpired && this.blockAgainImmediately.getValue() && conditionsMet) {
                    this.startBlocking(currentTick);
                }
            }
        }
        if (!conditionsMet) {
            this.stopBlocking(true);
            return;
        }
        if (!this.blocking && !this.lagging) {
            boolean shouldStart = !this.onlyWhenDamaged.getValue() || this.shouldPredictiveBlock();
            if (shouldStart) {
                this.startBlocking(currentTick);
            }
        }
        if (this.blocking) {
            int maxHoldTicks = msToTicks(this.maxHoldTime.getValue());
            boolean timeExpired = maxHoldTicks > 0
                    && this.blockStartTick >= 0
                    && currentTick - this.blockStartTick >= maxHoldTicks;
            if (timeExpired || this.onlyWhenDamaged.getValue() && hurtAgain) {
                if (this.shouldStartLag()) {
                    this.startLag(currentTick);
                }
                this.stopBlocking(true);
            }
        }
    }

    @EventTarget
    public void onRenderItem(RenderItemEvent event) {
        if (this.isEnabled()
                && this.forceBlockAnimation.getValue()
                && (this.blocking || this.lagging)
                && ItemUtil.isHoldingSword()) {
            event.setEnumAction(EnumAction.BLOCK);
            event.setUseItem(true);
        }
    }

    private static int msToTicks(int milliseconds) {
        if (milliseconds <= 0) {
            return 0;
        }
        return (int) Math.ceil(milliseconds / 50.0D);
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

    private EntityPlayer findTarget() {
        double maxDistanceSquared = this.range.getValue() * this.range.getValue();
        if (mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY
                && mc.objectMouseOver.entityHit instanceof EntityPlayer player
                && this.isValidTarget(player, maxDistanceSquared)) {
            return player;
        }

        EntityPlayer closest = null;
        double closestDistanceSquared = Double.MAX_VALUE;
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityPlayer player) || !this.isValidTarget(player, maxDistanceSquared)) {
                continue;
            }
            double distanceSquared = this.getDistanceSquaredToBox(player);
            if (distanceSquared < closestDistanceSquared) {
                closestDistanceSquared = distanceSquared;
                closest = player;
            }
        }
        return closest;
    }

    private boolean isValidTarget(EntityPlayer player, double maxDistanceSquared) {
        return player != mc.thePlayer
                && !player.isDead
                && player.deathTime == 0
                && !TeamUtil.isFriend(player)
                && !TeamUtil.shouldBlockTarget(player)
                && this.getDistanceSquaredToBox(player) <= maxDistanceSquared;
    }

    private double getDistanceSquaredToBox(EntityPlayer player) {
        double distance = RotationUtil.distanceToEntity(player);
        return distance * distance;
    }

    private boolean checkConditions(boolean lmbDown, boolean rmbDown) {
        if (this.requireLmb.getValue() && !lmbDown) {
            return false;
        }
        return !this.requireRmb.getValue() || rmbDown;
    }

    private boolean shouldPredictiveBlock() {
        int triggerTick = (int) Math.round(this.maxHurtTime.getValue() / 50.0D);
        triggerTick = Math.max(1, Math.min(10, triggerTick));
        return mc.thePlayer.hurtTime == triggerTick;
    }

    private boolean shouldBlockVanillaUse() {
        return this.isEnabled()
                && this.lagging
                && this.isReady()
                && ItemUtil.isHoldingSword();
    }

    private void startBlocking(int currentTick) {
        if (!ItemUtil.isHoldingSword()) {
            return;
        }
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBindUtil.setKeyBindState(keyCode, true);
        KeyBindUtil.pressKeyOnce(keyCode);
        this.blocking = true;
        this.blockStartTick = currentTick;
    }

    private void stopBlocking(boolean forceRelease) {
        if (!this.blocking && !forceRelease) {
            return;
        }
        KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        this.blocking = false;
        this.blockStartTick = -1;
    }

    private boolean shouldStartLag() {
        int chance = this.lagChance.getValue();
        return chance >= 100 || chance > 0 && Math.random() * 100.0D < chance;
    }

    private void startLag(int currentTick) {
        if (this.lagging) {
            return;
        }
        BlinkModules blinkingModule = Unfair.blinkManager.getBlinkingModule();
        if (blinkingModule != BlinkModules.NONE) {
            return;
        }
        int lagReferenceTick = this.blockStartTick >= 0 ? this.blockStartTick : currentTick;
        int lagMaxTicks = msToTicks(this.lagMaxDuration.getValue());
        if (lagMaxTicks > 0 && currentTick - lagReferenceTick >= lagMaxTicks) {
            return;
        }
        if (Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTOBLOCK)) {
            this.lagging = true;
            this.lagStartTick = lagReferenceTick;
        }
    }

    private void releaseLag() {
        if (!this.lagging) {
            return;
        }
        Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTOBLOCK);
        this.lagging = false;
        this.lagStartTick = -1;
    }

    public boolean isActive() {
        return this.isEnabled() && (this.blocking || this.lagging);
    }

    private void resetState(boolean releaseUseKey) {
        this.releaseLag();
        this.stopBlocking(releaseUseKey);
        this.manualBlock = false;
        if (Mouse.isButtonDown(1) && mc.currentScreen == null) {
            KeyBindUtil.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), true);
        }
        this.currentTarget = null;
        this.lastSelfHurtTime = 0;
    }
}
