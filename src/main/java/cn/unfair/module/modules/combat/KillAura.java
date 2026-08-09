package cn.unfair.module.modules.combat;

import cn.unfair.Unfair;
import cn.unfair.enums.BlinkModules;
import cn.unfair.event.EventManager;
import cn.unfair.event.EventTarget;
import cn.unfair.event.types.EventType;
import cn.unfair.event.types.Priority;
import cn.unfair.events.*;
import cn.unfair.management.RotationState;
import cn.unfair.module.Module;
import cn.unfair.module.modules.misc.BedNuker;
import cn.unfair.module.modules.movement.NoSlow;
import cn.unfair.module.modules.player.AutoBlockIn;
import cn.unfair.module.modules.player.AutoHeal;
import cn.unfair.module.modules.player.Scaffold;
import cn.unfair.module.modules.render.HUD;
import cn.unfair.property.properties.*;
import cn.unfair.util.*;
import cn.unfair.util.player.DelayGenerator;
import com.google.common.base.CaseFormat;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.viamcp.fixes.AttackOrder;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.passive.EntitySquid;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.client.C02PacketUseEntity.Action;
import net.minecraft.util.*;
import net.minecraft.util.MovingObjectPosition.MovingObjectType;
import net.minecraft.world.WorldSettings.GameType;

import java.awt.*;
import java.util.ArrayList;
import java.util.Random;

public class KillAura extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    public static AttackData target = null;
    public final ModeProperty mode = new ModeProperty("mode", 0, new String[]{"SINGLE", "SWITCH"});
    public final ModeProperty sort = new ModeProperty("sort", 0, new String[]{"DISTANCE", "HEALTH", "HURT_TIME", "FOV"});
    public final ModeProperty autoBlock = new ModeProperty(
            "auto-block", 0, new String[]{"NONE", "VANILLA", "HYPIXEL", "LEGIT", "FAKE", "HYPIXEL_LAG"}
    );
    public final BooleanProperty autoBlockRequirePress = new BooleanProperty("auto-block-require-press", false);
    public final IntProperty autoBlockCPS = new IntProperty("auto-block-aps", 10, 1, 20);
    public final FloatProperty autoBlockRange = new FloatProperty("auto-block-range", 6.0F, 3.0F, 8.0F);
    public final FloatProperty swingRange = new FloatProperty("swing-range", 3.5F, 3.0F, 6.0F);
    public final FloatProperty attackRange = new FloatProperty("attack-range", 3.0F, 3.0F, 6.0F);
    public final IntProperty fov = new IntProperty("fov", 360, 30, 360);
    public final IntProperty minCPS = new IntProperty("min-aps", 14, 1, 20);
    public final IntProperty maxCPS = new IntProperty("max-aps", 14, 1, 20);
    public final IntProperty switchDelay = new IntProperty("switch-delay", 150, 0, 1000);
    public final ModeProperty rotations = new ModeProperty("rotations", 2, new String[]{"NONE", "LEGIT", "SILENT"});
    public final BooleanProperty lockView = new BooleanProperty("lock-view", false, () -> this.rotations.getValue() != 0);
    public final ModeProperty moveFix = new ModeProperty("move-fix", 1, new String[]{"NONE", "SILENT", "STRICT"});
    public final PercentProperty smoothing = new PercentProperty("smoothing", 0);
    public final IntProperty angleStep = new IntProperty("angle-step", 90, 30, 180);
    public final IntProperty aimSpeedYaw = new IntProperty("aim-speed-yaw", 60, 1, 180, () -> this.rotations.getValue() == 2);
    public final IntProperty aimSpeedPitch = new IntProperty("aim-speed-pitch", 60, 1, 180, () -> this.rotations.getValue() == 2);
    public final BooleanProperty throughWalls = new BooleanProperty("through-walls", true);
    public final BooleanProperty requirePress = new BooleanProperty("require-press", false);
    public final BooleanProperty allowMining = new BooleanProperty("allow-mining", false);
    public final BooleanProperty weaponsOnly = new BooleanProperty("weapons-only", false);
    public final BooleanProperty allowTools = new BooleanProperty("allow-tools", false, this.weaponsOnly::getValue);
    public final BooleanProperty inventoryCheck = new BooleanProperty("inventory-check", true);
    public final BooleanProperty players = new BooleanProperty("players", true);
    public final BooleanProperty bosses = new BooleanProperty("bosses", false);
    public final BooleanProperty mobs = new BooleanProperty("mobs", false);
    public final BooleanProperty animals = new BooleanProperty("animals", false);
    public final BooleanProperty golems = new BooleanProperty("golems", false);
    public final BooleanProperty silverfish = new BooleanProperty("silverfish", false);
    public final ModeProperty showTarget = new ModeProperty("show-target", 0, new String[]{"NONE", "3DBOX"});
    private final BooleanProperty c09Instead = new BooleanProperty("c09-instead", true, () -> this.autoBlock.getValue() == 5);
    private final TimerUtil timer = new TimerUtil();
    private final DelayGenerator delayGenerator = new DelayGenerator();
    public boolean attackDisabled = false;
    private int switchTick = 0;
    private boolean hitRegistered = false;
    private boolean blockingState = false;
    private boolean isBlocking = false;
    private boolean fakeBlockState = false;
    private long attackDelayMS = 0L;
    private int blockTick = 0;
    private float serverYaw;
    private float serverPitch;
    private boolean easingOut;

    public KillAura() {
        super("KillAura", false);
    }

    private long getAttackDelay() {
        return this.isBlocking ? this.delayGenerator.nextDelay(this.autoBlockCPS.getValue(), this.autoBlockCPS.getValue()) : this.delayGenerator.nextDelay(this.minCPS.getValue(), this.maxCPS.getValue());
    }

    private boolean performAttack(float yaw, float pitch) {
        if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
            if (this.isPlayerBlocking() && this.autoBlock.getValue() != 1) {
                return false;
            } else if (this.attackDelayMS > 0L) {
                return false;
            } else {
                this.attackDelayMS = this.attackDelayMS + this.getAttackDelay();
                if ((this.rotations.getValue() != 0 || !this.isBoxInAttackRange(target.getBox()))
                        && this.getAttackHitVec(yaw, pitch, this.attackRange.getValue()) == null) {
                    this.performSwing();
                    return false;
                } else {
                    AttackEvent event = new AttackEvent(target.getEntity());
                    EventManager.call(event);
                    mc.playerController.syncCurrentPlayItem();
                    AttackOrder.sendFixedPacketAttack(target.getEntity());
                    if (mc.playerController.getCurrentGameType() != GameType.SPECTATOR) {
                        PlayerUtil.attackEntity(target.getEntity());
                    }
                    this.hitRegistered = true;
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    private void performSwing() {
        mc.playerController.syncCurrentPlayItem();
        if (ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            PacketUtil.sendPacket(new ServerBoundSwing(EnumHand.MAIN_HAND));
            mc.thePlayer.swingClientSide();
        } else {
            mc.thePlayer.swingItem();
        }
    }

    private void sendUseItem() {
        mc.playerController.syncCurrentPlayItem();
        this.startBlock(mc.thePlayer.getHeldItem());
    }

    private void startBlock(ItemStack itemStack) {
        PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(itemStack));
        mc.thePlayer.setItemInUse(itemStack, itemStack.getMaxItemUseDuration());
        this.blockingState = true;
    }

    private void stopBlock() {
        PacketUtil.sendPacket(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        mc.thePlayer.stopUsingItem();
        this.blockingState = false;
    }

    private void interactAttack(float yaw, float pitch) {
        if (target != null) {
            Vec3 hitVec = this.getAttackHitVec(yaw, pitch, 8.0);
            if (hitVec != null) {
                mc.playerController.syncCurrentPlayItem();
                PacketUtil.sendPacket(
                        new C02PacketUseEntity(
                                target.getEntity(),
                                new Vec3(hitVec.xCoord - target.getX(), hitVec.yCoord - target.getY(), hitVec.zCoord - target.getZ())
                        )
                );
                PacketUtil.sendPacket(new C02PacketUseEntity(target.getEntity(), Action.INTERACT));
                PacketUtil.sendPacket(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
                mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                this.blockingState = true;
            }
        }
    }

    private Vec3 getAttackHitVec(float yaw, float pitch, double distance) {
        if (target == null) {
            return null;
        }

        MovingObjectPosition hit = RotationUtil.rayTrace(target.getBox(), yaw, pitch, distance);
        return hit == null ? null : hit.hitVec;
    }

    private boolean canAttack() {
        if (this.attackDisabled) {
            return false;
        }
        if (this.isInventoryBlocked()) {
            return false;
        } else if (!(Boolean) this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            if (mc.playerController.getIsHittingBlock()) {
                return false;
            } else if ((ItemUtil.isEating() || ItemUtil.isUsingBow()) && PlayerUtil.isUsingItem()) {
                return false;
            } else {
                AutoHeal autoHeal = (AutoHeal) Unfair.moduleManager.modules.get(AutoHeal.class);
                if (autoHeal.isEnabled() && autoHeal.isSwitching()) {
                    return false;
                } else {
                    BedNuker bedNuker = (BedNuker) Unfair.moduleManager.modules.get(BedNuker.class);
                    AutoBlockIn autoBlockIn = (AutoBlockIn) Unfair.moduleManager.modules.get(AutoBlockIn.class);
                    if (bedNuker.isEnabled() && bedNuker.isReady()) {
                        return false;
                    } else if (Unfair.moduleManager.modules.get(Scaffold.class).isEnabled()) {
                        return false;
                    } else if (autoBlockIn.isEnabled()) {
                        return false;
                    } else if (this.requirePress.getValue()) {
                        return PlayerUtil.isAttacking();
                    } else {
                        return !this.allowMining.getValue() || !mc.objectMouseOver.typeOfHit.equals(MovingObjectType.BLOCK) || !PlayerUtil.isAttacking();
                    }
                }
            }
        } else {
            return false;
        }
    }

    private boolean canAutoBlock() {
        if (!ItemUtil.isHoldingSword()) {
            return false;
        } else {
            return !this.autoBlockRequirePress.getValue() || PlayerUtil.isUsingItem();
        }
    }

    private boolean hasValidTarget() {
        return mc.theWorld
                .loadedEntityList
                .stream()
                .anyMatch(
                        entity -> entity instanceof EntityLivingBase
                                && this.isValidTarget((EntityLivingBase) entity)
                                && this.isInBlockRange((EntityLivingBase) entity)
                );
    }

    private boolean isValidTarget(EntityLivingBase entityLivingBase) {
        if (!mc.theWorld.loadedEntityList.contains(entityLivingBase)) {
            return false;
        } else if (entityLivingBase != mc.thePlayer && entityLivingBase != mc.thePlayer.ridingEntity) {
            if (entityLivingBase == mc.getRenderViewEntity() || entityLivingBase == mc.getRenderViewEntity().ridingEntity) {
                return false;
            } else if (entityLivingBase.deathTime > 0) {
                return false;
            } else if (RotationUtil.angleToEntity(entityLivingBase) > this.fov.getValue().floatValue()) {
                return false;
            } else if (!this.throughWalls.getValue() && RotationUtil.rayTrace(entityLivingBase) != null) {
                return false;
            } else if (entityLivingBase instanceof EntityOtherPlayerMP) {
                if (!this.players.getValue()) {
                    return false;
                } else if (TeamUtil.isFriend((EntityPlayer) entityLivingBase)) {
                    return false;
                } else {
                    return !TeamUtil.shouldBlockTarget((EntityPlayer) entityLivingBase);
                }
            } else if (entityLivingBase instanceof EntityDragon || entityLivingBase instanceof EntityWither) {
                return this.bosses.getValue();
            } else if (!(entityLivingBase instanceof EntityMob) && !(entityLivingBase instanceof EntitySlime)) {
                if (entityLivingBase instanceof EntityAnimal
                        || entityLivingBase instanceof EntityBat
                        || entityLivingBase instanceof EntitySquid
                        || entityLivingBase instanceof EntityVillager) {
                    return this.animals.getValue();
                } else if (!(entityLivingBase instanceof EntityIronGolem)) {
                    return false;
                } else {
                    return this.golems.getValue() && !TeamUtil.shouldBlockTeamColor(entityLivingBase);
                }
            } else if (!(entityLivingBase instanceof EntitySilverfish)) {
                return this.mobs.getValue();
            } else {
                return this.silverfish.getValue() && !TeamUtil.shouldBlockTeamColor(entityLivingBase);
            }
        } else {
            return false;
        }
    }

    private boolean isInRange(EntityLivingBase entityLivingBase) {
        return this.isInBlockRange(entityLivingBase) || this.isInSwingRange(entityLivingBase) || this.isInAttackRange(entityLivingBase);
    }

    private boolean isInBlockRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.autoBlockRange.getValue();
    }

    private boolean isInSwingRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.swingRange.getValue();
    }

    private boolean isBoxInSwingRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.swingRange.getValue();
    }

    private boolean isInAttackRange(EntityLivingBase entityLivingBase) {
        return RotationUtil.distanceToEntity(entityLivingBase) <= (double) this.attackRange.getValue();
    }

    private boolean isBoxInAttackRange(AxisAlignedBB axisAlignedBB) {
        return RotationUtil.distanceToBox(axisAlignedBB) <= (double) this.attackRange.getValue();
    }

    private boolean isPlayerTarget(EntityLivingBase entityLivingBase) {
        return entityLivingBase instanceof EntityPlayer && TeamUtil.isTarget((EntityPlayer) entityLivingBase);
    }

    private float[] interpolateRotation(float targetYaw, float targetPitch) {
        float maxDeltaYaw = this.aimSpeedYaw.getValue().floatValue();
        float maxDeltaPitch = this.aimSpeedPitch.getValue().floatValue();

        float deltaYaw = targetYaw - this.serverYaw;
        while (deltaYaw <= -180.0F) deltaYaw += 360.0F;
        while (deltaYaw > 180.0F) deltaYaw -= 360.0F;

        float deltaPitch = targetPitch - this.serverPitch;

        if (Math.abs(deltaYaw) > maxDeltaYaw) {
            this.serverYaw += Math.signum(deltaYaw) * maxDeltaYaw;
        } else {
            this.serverYaw = targetYaw;
        }

        if (Math.abs(deltaPitch) > maxDeltaPitch) {
            this.serverPitch += Math.signum(deltaPitch) * maxDeltaPitch;
        } else {
            this.serverPitch = targetPitch;
        }

        this.serverPitch = Math.clamp(this.serverPitch, -90.0F, 90.0F);

        return new float[]{this.serverYaw, this.serverPitch};
    }

    private boolean isAtRotation(float yaw, float pitch) {
        return Math.abs(MathHelper.wrapAngleTo180_float(yaw - this.serverYaw)) < 0.01F
                && Math.abs(pitch - this.serverPitch) < 0.01F;
    }

    public EntityLivingBase getTarget() {
        return target != null ? target.getEntity() : null;
    }

    public boolean isAttackAllowed() {
        Scaffold scaffold = (Scaffold) Unfair.moduleManager.modules.get(Scaffold.class);
        if (scaffold.isEnabled()) {
            return false;
        } else if (!this.weaponsOnly.getValue()
                || ItemUtil.hasRawUnbreakingEnchant()
                || this.allowTools.getValue() && ItemUtil.isHoldingTool()) {
            return !this.requirePress.getValue() || KeyBindUtil.isKeyDown(mc.gameSettings.keyBindAttack.getKeyCode());
        } else {
            return false;
        }
    }

    public boolean shouldAutoBlock() {
        if (this.isPlayerBlocking() && this.isBlocking) {
            return mc.thePlayer != null
                    && !mc.thePlayer.isInWater()
                    && !mc.thePlayer.isInLava()
                    && (this.autoBlock.getValue() == 2
                    || this.autoBlock.getValue() == 3
                    || this.autoBlock.getValue() == 4
                    || this.autoBlock.getValue() == 5);
        } else {
            return false;
        }
    }

    private void applyAuraRotation(UpdateEvent event, float yaw, float pitch) {
        event.setRotation(yaw, pitch, 1);
        if (this.lockView.getValue()) {
            Unfair.rotationManager.setRotation(yaw, pitch, 1, true);
        }
    }

    private void syncServerRotationToPlayer() {
        this.serverYaw = mc.thePlayer.rotationYaw;
        this.serverPitch = mc.thePlayer.rotationPitch;
    }

    private boolean isInventoryBlocked() {
        return this.inventoryCheck.getValue() && mc.currentScreen instanceof GuiContainer;
    }

    private void resetInventoryBlockedState() {
        target = null;
        Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.blockingState = false;
        this.isBlocking = false;
        this.fakeBlockState = false;
        this.blockTick = 0;
        this.attackDelayMS = 0L;
        this.easingOut = false;

        if (mc.thePlayer != null) {
            this.serverYaw = mc.thePlayer.rotationYaw;
            this.serverPitch = mc.thePlayer.rotationPitch;
        }
    }

    public boolean isBlocking() {
        return this.fakeBlockState && ItemUtil.isHoldingSword();
    }

    public boolean isPlayerBlocking() {
        return mc.thePlayer != null && (mc.thePlayer.isUsingItem() || this.blockingState) && ItemUtil.isHoldingSword();
    }

    @EventTarget(Priority.LOWEST)
    public void onUpdate(UpdateEvent event) {
        if ((this.isEnabled() || this.easingOut) && event.getType() == EventType.PRE && this.isInventoryBlocked()) {
            this.resetInventoryBlockedState();
            return;
        }

        if (this.easingOut && event.getType() == EventType.PRE) {
            float targetYaw = event.getNewYaw();
            float targetPitch = event.getNewPitch();
            float[] reset = this.interpolateRotation(targetYaw, targetPitch);
            this.applyAuraRotation(event, reset[0], reset[1]);
            if (this.moveFix.getValue() != 0) {
                event.setPervRotation(reset[0], 1);
            }
            if (this.isAtRotation(targetYaw, targetPitch)) {
                this.easingOut = false;
            }
            return;
        }
        if (this.isEnabled() && event.getType() == EventType.PRE) {
            if (this.attackDelayMS > 0L) {
                this.attackDelayMS -= 50L;
            }
            boolean attack = target != null && this.canAttack();
            boolean block = attack && this.canAutoBlock();
            if (!block) {
                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                this.isBlocking = false;
                this.fakeBlockState = false;
                this.blockTick = 0;
            }
            if (attack) {
                boolean swap = false;
                boolean blocked = false;
                if (block) {
                    switch (this.autoBlock.getValue()) {
                        case 0:
                            if (PlayerUtil.isUsingItem()) {
                                this.isBlocking = true;
                                if (!this.isPlayerBlocking() && !Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    swap = true;
                                }
                            } else {
                                this.isBlocking = false;
                                if (this.isPlayerBlocking() && !Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    this.stopBlock();
                                }
                            }
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.fakeBlockState = false;
                            break;
                        case 1:
                            if (this.hasValidTarget()) {
                                if (!this.isPlayerBlocking() && !Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    swap = true;
                                }
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 2:
                            if (this.hasValidTarget()) {
                                if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            blocked = true;
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            attack = false;
                                            this.blockTick = 2;
                                            break;
                                        case 2:
                                            if (this.isPlayerBlocking()) {
                                                if (Unfair.moduleManager.modules.get(NoSlow.class).isEnabled()) {
                                                    int randomSlot = new Random().nextInt(9);
                                                    while (randomSlot == mc.thePlayer.inventory.currentItem) {
                                                        randomSlot = new Random().nextInt(9);
                                                    }
                                                    Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(randomSlot));
                                                    mc.getNetHandler().addToSendQueue(new C17PacketCustomPayload("send", new PacketBuffer(Unpooled.buffer())));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
                                                }
                                                this.stopBlock();
                                            }
                                            attack = false;
                                            this.blockTick = 0;
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 3:
                            if (this.hasValidTarget()) {
                                if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                this.stopBlock();
                                                attack = false;
                                            }
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = true;
                                this.fakeBlockState = false;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                        case 4:
                            Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                            this.isBlocking = false;
                            this.fakeBlockState = this.hasValidTarget();
                            if (PlayerUtil.isUsingItem()
                                    && !this.isPlayerBlocking()
                                    && !Unfair.playerStateManager.digging
                                    && !Unfair.playerStateManager.placing) {
                                swap = true;
                            }
                            break;
                        case 5:
                            if (this.hasValidTarget()) {
                                if (!Unfair.playerStateManager.digging && !Unfair.playerStateManager.placing) {
                                    switch (this.blockTick) {
                                        case 0:
                                            blocked = true;
                                            if (!this.isPlayerBlocking()) {
                                                swap = true;
                                            }
                                            this.blockTick = 1;
                                            break;
                                        case 1:
                                            if (this.isPlayerBlocking()) {
                                                if (this.c09Instead.getValue()) {
                                                    int handle = mc.thePlayer.inventory.currentItem;
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 8 + 1));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle % 7 + 2));
                                                    PacketUtil.sendPacket(new C09PacketHeldItemChange(handle));
                                                }
                                                this.stopBlock();
                                            }
                                            attack = false;
                                            this.blockTick = 2;
                                            break;
                                        case 2:
                                            Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                            if (this.attackDelayMS <= 50L) {
                                                this.blockTick = 0;
                                            }
                                            break;
                                        default:
                                            this.blockTick = 0;
                                    }
                                }
                                this.isBlocking = true;
                                this.fakeBlockState = true;
                            } else {
                                Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                                if (this.isBlocking) {
                                    this.stopBlock();
                                }
                                this.isBlocking = false;
                                this.fakeBlockState = false;
                            }
                            break;
                    }
                }
                boolean attacked = false;
                if (this.isBoxInSwingRange(target.getBox())) {
                    if (this.rotations.getValue() == 2) {

                        float randomOffset = (float) this.angleStep.getValue() + RandomUtil.nextFloat(-5.0F, 5.0F);
                        float smoothFactor = (float) this.smoothing.getValue() / 100.0F;
                        float[] targetRotations = RotationUtil.getRotationsToBox(
                                target.getBox(),
                                event.getYaw(),
                                event.getPitch(),
                                randomOffset,
                                smoothFactor
                        );
                        float[] smoothed = interpolateRotation(targetRotations[0], targetRotations[1]);
                        this.applyAuraRotation(event, smoothed[0], smoothed[1]);

                        if (this.moveFix.getValue() != 0 || this.lockView.getValue()) {
                            event.setPervRotation(event.getNewYaw(), 1);
                        }
                    }
                    if (attack) {
                        attacked = this.performAttack(event.getNewYaw(), event.getNewPitch());
                    }
                } else if (this.rotations.getValue() == 2) {
                    this.syncServerRotationToPlayer();
                }
                if (swap) {
                    if (attacked) {
                        this.interactAttack(event.getNewYaw(), event.getNewPitch());
                    } else {
                        this.sendUseItem();
                    }
                }
                if (blocked) {
                    Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
                    Unfair.blinkManager.setBlinkState(true, BlinkModules.AUTO_BLOCK);
                }
            }

            if (this.rotations.getValue() == 2 && !attack) {
                this.syncServerRotationToPlayer();
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (this.isEnabled()) {
            if (mc.thePlayer == null || mc.theWorld == null) {
                target = null;
                this.blockingState = false;
                this.isBlocking = false;
                return;
            }
            if (this.isInventoryBlocked()) {
                this.resetInventoryBlockedState();
                return;
            }
            switch (event.type()) {
                case PRE:
                    if (target == null
                            || !this.isValidTarget(target.getEntity())
                            || !this.isBoxInSwingRange(target.getBox())
                            || this.timer.hasTimeElapsed(this.switchDelay.getValue().longValue())) {
                        this.timer.reset();
                        ArrayList<EntityLivingBase> targets = new ArrayList<>();
                        for (Entity entity : mc.theWorld.loadedEntityList) {
                            if (entity instanceof EntityLivingBase
                                    && this.isValidTarget((EntityLivingBase) entity)
                                    && this.isInRange((EntityLivingBase) entity)) {
                                targets.add((EntityLivingBase) entity);
                            }
                        }
                        if (targets.isEmpty()) {
                            target = null;
                        } else {
                            if (targets.stream().anyMatch(this::isInSwingRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInSwingRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isInAttackRange)) {
                                targets.removeIf(entityLivingBase -> !this.isInAttackRange(entityLivingBase));
                            }
                            if (targets.stream().anyMatch(this::isPlayerTarget)) {
                                targets.removeIf(entityLivingBase -> !this.isPlayerTarget(entityLivingBase));
                            }
                            targets.sort(
                                    (entityLivingBase1, entityLivingBase2) -> {
                                        int sortBase = 0;
                                        switch (this.sort.getValue()) {
                                            case 1:
                                                sortBase = Float.compare(TeamUtil.getHealthScore(entityLivingBase1), TeamUtil.getHealthScore(entityLivingBase2));
                                                break;
                                            case 2:
                                                sortBase = Integer.compare(entityLivingBase1.hurtResistantTime, entityLivingBase2.hurtResistantTime);
                                                break;
                                            case 3:
                                                sortBase = Float.compare(
                                                        RotationUtil.angleToEntity(entityLivingBase1),
                                                        RotationUtil.angleToEntity(entityLivingBase2)
                                                );
                                        }
                                        return sortBase != 0
                                                ? sortBase
                                                : Double.compare(RotationUtil.distanceToEntity(entityLivingBase1), RotationUtil.distanceToEntity(entityLivingBase2));
                                    }
                            );
                            if (this.mode.getValue() == 1 && this.hitRegistered) {
                                this.hitRegistered = false;
                                this.switchTick++;
                            }
                            if (this.mode.getValue() == 0 || this.switchTick >= targets.size()) {
                                this.switchTick = 0;
                            }
                            EntityLivingBase selected = targets.get(this.switchTick);
                            target = new AttackData(selected);
                        }
                    }
                    if (target != null) {
                        target = new AttackData(target.getEntity());
                    }
                    break;
                case POST:
                    if (this.isPlayerBlocking() && !mc.thePlayer.isBlocking()) {
                        mc.thePlayer.setItemInUse(mc.thePlayer.getHeldItem(), mc.thePlayer.getHeldItem().getMaxItemUseDuration());
                    }
            }
        }
    }

    @EventTarget
    public void onLoadWorld(LoadWorldEvent event) {
        this.setEnabled(false);
    }

    @EventTarget(Priority.LOWEST)
    public void onPacket(PacketEvent event) {
        if (this.isEnabled() && !event.isCancelled() && !this.isInventoryBlocked()) {
            if (event.getPacket() instanceof C07PacketPlayerDigging packet) {
                if (packet.getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
                    this.blockingState = false;
                }
            }
            if (event.getPacket() instanceof C09PacketHeldItemChange) {
                this.blockingState = false;
                if (this.isBlocking) {
                    mc.thePlayer.stopUsingItem();
                }
            }
        }
    }

    @EventTarget
    public void onMove(MoveInputEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked()) {
            if (this.moveFix.getValue() == 1
                    && !this.lockView.getValue()
                    && RotationState.isActived()
                    && RotationState.getPriority() == 1.0F
                    && MoveUtil.isForwardPressed()) {
                MoveUtil.fixStrafe(RotationState.getSmoothedYaw());
            }
            if (this.shouldAutoBlock()) {
                mc.thePlayer.movementInput.jump = false;
            }
        }
    }

    @EventTarget
    public void onRender(Render3DEvent event) {
        if (this.isEnabled() && !this.isInventoryBlocked() && target != null) {
            if (TeamUtil.isEntityLoaded(target.getEntity())
                    && this.isAttackAllowed()) {
                if (this.showTarget.getValue() == 1) {
                    Unfair.moduleManager.modules.get(HUD.class);
                    Color color = HUD.getColor(System.currentTimeMillis());
                    RenderUtil.enableRenderState();
                    RenderUtil.drawEntityBox(target.getEntity(), color.getRed(), color.getGreen(), color.getBlue());
                    RenderUtil.disableRenderState();
                }
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        boolean shouldEaseOut = !enabled
                && this.enabled
                && mc.thePlayer != null
                && this.rotations.getValue() == 2
                && !this.isAtRotation(mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
        if (shouldEaseOut) {
            this.easingOut = true;
        }
        super.setEnabled(enabled);
    }

    @EventTarget
    public void onLeftClick(LeftClickMouseEvent event) {
        if (this.isInventoryBlocked()) {
            return;
        }
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onRightClick(RightClickMouseEvent event) {
        if (this.isInventoryBlocked()) {
            return;
        }
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onHitBlock(HitBlockEvent event) {
        if (this.isInventoryBlocked()) {
            return;
        }
        if (this.isBlocking) {
            event.setCancelled(true);
        } else {
            if (this.isEnabled() && target != null && this.canAttack()) {
                event.setCancelled(true);
            }
        }
    }

    @EventTarget
    public void onCancelUse(CancelUseEvent event) {
        if (!this.isInventoryBlocked() && this.isBlocking) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEnabled() {
        this.easingOut = false;
        target = null;
        this.switchTick = 0;
        this.hitRegistered = false;
        this.attackDelayMS = 0L;
        this.delayGenerator.reset();
        this.blockTick = 0;

        this.serverYaw = mc.thePlayer != null ? mc.thePlayer.rotationYaw : 0.0F;
        this.serverPitch = mc.thePlayer != null ? mc.thePlayer.rotationPitch : 0.0F;
    }

    @Override
    public void onDisabled() {
        Unfair.blinkManager.setBlinkState(false, BlinkModules.AUTO_BLOCK);
        this.blockingState = false;
        this.isBlocking = false;
        this.fakeBlockState = false;
    }

    @Override
    public void verifyValue(String mode) {
        if (!this.autoBlock.getName().equals(mode) && !this.autoBlockCPS.getName().equals(mode)) {
            if (this.swingRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.attackRange.setValue(this.swingRange.getValue());
                }
            } else if (this.attackRange.getName().equals(mode)) {
                if (this.swingRange.getValue() < this.attackRange.getValue()) {
                    this.swingRange.setValue(this.attackRange.getValue());
                }
            } else if (this.minCPS.getName().equals(mode)) {
                if (this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.maxCPS.setValue(this.minCPS.getValue());
                }
            } else {
                if (this.maxCPS.getName().equals(mode) && this.minCPS.getValue() > this.maxCPS.getValue()) {
                    this.minCPS.setValue(this.maxCPS.getValue());
                }
            }
        } else {
            boolean badCps = this.autoBlock.getValue() == 2 || this.autoBlock.getValue() == 3;
            if (badCps && this.autoBlockCPS.getValue() > 10) {
                this.autoBlockCPS.setValue(10);
            }
        }
    }

    @Override
    public String[] getSuffix() {
        return new String[]{CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, this.mode.getModeString())};
    }

    public static class AttackData {
        private final EntityLivingBase entity;
        private final AxisAlignedBB box;
        private final double x;
        private final double y;
        private final double z;

        public AttackData(EntityLivingBase entityLivingBase) {
            this.entity = entityLivingBase;
            double collisionBorderSize = ViaLoadingBase.getInstance().getTargetVersion().newerThan(ProtocolVersion.v1_8)
                    ? 0.0D
                    : entityLivingBase.getCollisionBorderSize();
            this.box = entityLivingBase.getEntityBoundingBox().expand(collisionBorderSize, collisionBorderSize, collisionBorderSize);
            this.x = entityLivingBase.posX;
            this.y = entityLivingBase.posY;
            this.z = entityLivingBase.posZ;
        }

        public EntityLivingBase getEntity() {
            return this.entity;
        }

        public AxisAlignedBB getBox() {
            return this.box;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }
}
