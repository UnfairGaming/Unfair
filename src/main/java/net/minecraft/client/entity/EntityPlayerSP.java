package net.minecraft.client.entity;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.event.types.EventType;
import cn.unfair.events.LivingUpdateEvent;
import cn.unfair.events.MoveInputEvent;
import cn.unfair.events.PlayerUpdateEvent;
import cn.unfair.events.UpdateEvent;
import cn.unfair.management.RotationState;
import cn.unfair.module.modules.movement.NoSlow;
import cn.unfair.module.modules.player.AntiDebuff;
import cn.unfair.util.via.*;
import com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MovingSoundMinecartRiding;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.potion.Potion;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.*;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.World;

public class EntityPlayerSP extends AbstractClientPlayer implements ModernPlayerPhysics, ModernOffhandPlayer {
    public final NetHandlerPlayClient sendQueue;
    private final StatFileWriter statWriter;

    /**
     * The last X position which was transmitted to the server, used to determine when the X position changes and needs
     * to be re-trasmitted
     */
    private double lastReportedPosX;

    /**
     * The last Y position which was transmitted to the server, used to determine when the Y position changes and needs
     * to be re-transmitted
     */
    private double lastReportedPosY;

    /**
     * The last Z position which was transmitted to the server, used to determine when the Z position changes and needs
     * to be re-transmitted
     */
    private double lastReportedPosZ;

    /**
     * The last yaw value which was transmitted to the server, used to determine when the yaw changes and needs to be
     * re-transmitted
     */
    private float lastReportedYaw;

    /**
     * The last pitch value which was transmitted to the server, used to determine when the pitch changes and needs to
     * be re-transmitted
     */
    private float lastReportedPitch;

    /**
     * the last sneaking state sent to the server
     */
    private boolean serverSneakState;

    /**
     * the last sprinting state sent to the server
     */
    private boolean serverSprintState;

    /**
     * Reset to 0 every time position is sent to the server, used to send periodic updates every 20 ticks even when the
     * player is not moving.
     */
    private int positionUpdateTicks;
    private boolean hasValidHealth;
    @Getter
    @Setter
    private String clientBrand;
    public MovementInput movementInput;
    protected Minecraft mc;

    /**
     * Used to tell if the player pressed forward twice. If this is at 0 and it's pressed (And they are allowed to
     * sprint, aka enough food on the ground etc) it sets this to 7. If it's pressed and it's greater than 0 enable
     * sprinting.
     */
    protected int sprintToggleTimer;

    /**
     * Ticks left before sprinting is disabled.
     */
    public int sprintingTicksLeft;
    public float renderArmYaw;
    public float renderArmPitch;
    public float prevRenderArmYaw;
    public float prevRenderArmPitch;
    private boolean offhandSwinging;
    private int offhandSwingTicks;
    private float offhandSwingProgress;
    private float previousOffhandSwingProgress;
    private boolean modernSwimming;
    private boolean wasModernSwimming;
    private boolean modernSubmergedInWater;
    private boolean wasEyeInWater;
    private boolean wasSprintingBeforeInput;
    private BlockPos mainSupportingBlock;
    private boolean supportingBlockOnGround;
    private float modernEyeHeight = 1.62F;
    private boolean slowMovementFromPreviousPose;
    private boolean movementInputAdjustedThisTick;
    private double modernWaterHeight;
    private double modernLavaHeight;
    private boolean touchingModernLava;
    private boolean usingItemAtPreviousTick;
    private boolean usingItemAtTickStart;
    private boolean carryItemUseSlowdown;
    private boolean localItemUseFinished;
    private boolean serverItemUseFinished;
    private int foodUseRestartDelayTicks;
    private int foodUseRestartSlot = -1;
    private Item foodUseRestartItem;
    private int itemUseFinishGraceTicks;
    private float overrideYaw = Float.NaN;
    private float overridePitch = Float.NaN;
    private float pendingYaw = Float.NaN;
    private float pendingPitch = Float.NaN;
    private MovementState lastState;
    private int horseJumpPowerCounter;
    @Getter
    private float horseJumpPower;

    /**
     * The amount of time an entity has been in a Portal
     */
    public float timeInPortal;

    /**
     * The amount of time an entity has been in a Portal the previous tick
     */
    public float prevTimeInPortal;

    public EntityPlayerSP(Minecraft mcIn, World worldIn, NetHandlerPlayClient netHandler, StatFileWriter statFile) {
        super(worldIn, netHandler.getGameProfile());
        this.sendQueue = netHandler;
        this.statWriter = statFile;
        this.mc = mcIn;
        this.dimension = 0;
        this.lastState = new MovementState(false, false, false, false, false, false, false);
    }

    /**
     * Called when the entity is attacked.
     */
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    /**
     * Heal living entity (param: amount of half-hearts)
     */
    public void heal(float healAmount) {
    }

    /**
     * Called when a player mounts an entity. e.g. mounts a pig, mounts a boat.
     */
    public void mountEntity(Entity entityIn) {
        super.mountEntity(entityIn);

        if (entityIn instanceof EntityMinecart) {
            this.mc.getSoundHandler().playSound(new MovingSoundMinecartRiding(this, (EntityMinecart) entityIn));
        }
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void onUpdate() {
        if (this.worldObj.isBlockLoaded(new BlockPos(this.posX, 0.0D, this.posZ))) {
            UpdateEvent event = new UpdateEvent(EventType.PRE, this.lastReportedYaw, this.lastReportedPitch, this.rotationYaw, this.rotationPitch);
            EventManager.call(event);
            RotationState.applyState(event.isRotated() && !this.isRiding(), event.getNewYaw(), event.getNewPitch(), event.getPreYaw(), event.isRotating());

            if (event.isRotated()) {
                this.pendingYaw = this.rotationYaw;
                this.pendingPitch = this.rotationPitch;
                this.overrideYaw = event.getNewYaw();
                this.overridePitch = event.getNewPitch();
            } else {
                this.pendingYaw = Float.NaN;
                this.pendingPitch = Float.NaN;
                this.overrideYaw = Float.NaN;
                this.overridePitch = Float.NaN;
            }

            super.onUpdate();

            if (mc.thePlayer != null) {
                MovementState newState = new MovementState(mc.thePlayer.movementInput.moveForward > 0.0F,
                        mc.thePlayer.movementInput.moveForward < 0.0F,
                        mc.thePlayer.movementInput.moveStrafe > 0.0F,
                        mc.thePlayer.movementInput.moveStrafe < 0.0F,
                        mc.thePlayer.movementInput.jump,
                        mc.thePlayer.movementInput.sneak,
                        mc.gameSettings.keyBindSprint.isKeyDown());

                if (!this.lastState.equals(newState) && ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
                    UserConnection connection = Via.getManager().getConnectionManager().getConnections().iterator().next();
                    PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_2.PLAYER_INPUT, connection);
                    wrapper.write(Types.BYTE, newState.toByte());
                    wrapper.sendToServer(Protocol1_21_2To1_21.class);
                    this.lastState = newState;
                }
            }

            if (!Float.isNaN(this.overrideYaw) && !Float.isNaN(this.overridePitch)) {
                this.rotationYaw = this.overrideYaw;
                this.rotationPitch = this.overridePitch;
            }

            if (this.isRiding()) {
                this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(this.rotationYaw, this.rotationPitch, this.onGround));
                this.sendQueue.addToSendQueue(new C0CPacketInput(this.moveStrafing, this.moveForward, this.movementInput.jump, this.movementInput.sneak));
            } else {
                EventManager.call(new PlayerUpdateEvent());
                this.onUpdateWalkingPlayer();
            }

            if (!Float.isNaN(this.pendingYaw) && !Float.isNaN(this.pendingPitch)) {
                this.lastReportedYaw = this.rotationYaw;
                this.lastReportedPitch = this.rotationPitch;
                this.rotationYaw = this.rotationYaw + MathHelper.wrapAngleTo180_float(this.pendingYaw - this.rotationYaw);
                this.rotationPitch = this.pendingPitch;
                this.prevRotationYaw = this.rotationYaw;
                this.prevRotationPitch = this.rotationPitch;
                this.prevRenderArmYaw = this.rotationYaw - (this.renderArmYaw - this.prevRenderArmYaw) * 2.0F;
                this.renderArmYaw = this.rotationYaw;
            }

            EventManager.call(new UpdateEvent(EventType.POST, this.lastReportedYaw, this.lastReportedPitch, this.rotationYaw, this.rotationPitch));
        }

    }

    /**
     * called every tick when the player is on foot. Performs all the things that normally happen during movement.
     */
    public void onUpdateWalkingPlayer() {
        boolean flag = this.isSprinting() && this.shouldReportSprintingToServer();

        if (flag != this.serverSprintState) {
            if (ViaProtocol.newerThanOrEqualTo1_19()) {
                this.sendQueue.addToSendQueue(new ServerBoundPlayerCommand(this.getEntityId(), flag ? ServerBoundPlayerCommand.Action.START_SPRINTING : ServerBoundPlayerCommand.Action.STOP_SPRINTING));
            } else {
                if (flag) {
                    this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.START_SPRINTING));
                } else {
                    this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.STOP_SPRINTING));
                }
            }

            this.serverSprintState = flag;
        }

        boolean flag1 = this.isSneaking();

        if (flag1 != this.serverSneakState) {
            if (ViaProtocol.newerThanOrEqualTo1_19()) {
                this.sendQueue.addToSendQueue(new ServerBoundPlayerCommand(this.mc.thePlayer.getEntityId(), flag1 ? ServerBoundPlayerCommand.Action.PRESS_SHIFT_KEY : ServerBoundPlayerCommand.Action.RELEASE_SHIFT_KEY));
            } else {
                if (flag1) {
                    this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.START_SNEAKING));
                } else {
                    this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.STOP_SNEAKING));
                }
            }

            this.serverSneakState = flag1;
        }

        if (this.isCurrentViewEntity()) {
            double d0 = this.posX - this.lastReportedPosX;
            double d1 = this.getEntityBoundingBox().minY - this.lastReportedPosY;
            double d2 = this.posZ - this.lastReportedPosZ;

            float yaw = this.rotationYaw;
            float pitch = this.rotationPitch;

            double d3 = yaw - this.lastReportedYaw;
            double d4 = pitch - this.lastReportedPitch;

            if (ViaProtocol.newerThan1_8()) {
                ++this.positionUpdateTicks;
            }

            boolean flag2 = ViaProtocol.newerThanOrEqualTo1_18()
                    ? d0 * d0 + d1 * d1 + d2 * d2 > (2.0E-4D * 2.0E-4D) || this.positionUpdateTicks >= 20
                    : d0 * d0 + d1 * d1 + d2 * d2 > 9.0E-4D || this.positionUpdateTicks >= 20;
            if (this.isModernTarget()) {
                flag2 = d0 * d0 + d1 * d1 + d2 * d2 > 4.0E-8D || this.positionUpdateTicks >= 20;
            }
            boolean flag3 = d3 != 0.0D || d4 != 0.0D;

            if (this.ridingEntity == null) {
                if (flag2 && flag3) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(this.posX, this.getEntityBoundingBox().minY, this.posZ, yaw, pitch, this.onGround));
                } else if (flag2) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C04PacketPlayerPosition(this.posX, this.getEntityBoundingBox().minY, this.posZ, this.onGround));
                } else if (flag3) {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer.C05PacketPlayerLook(yaw, pitch, this.onGround));
                } else {
                    this.sendQueue.addToSendQueue(new C03PacketPlayer(this.onGround));
                }
            } else {
                this.sendQueue.addToSendQueue(new C03PacketPlayer.C06PacketPlayerPosLook(this.motionX, -999.0D, this.motionZ, yaw, pitch, this.onGround));
                flag2 = false;
            }

            if (!ViaProtocol.newerThan1_8()) {
                ++this.positionUpdateTicks;
            }

            if (flag2) {
                this.lastReportedPosX = this.posX;
                this.lastReportedPosY = this.getEntityBoundingBox().minY;
                this.lastReportedPosZ = this.posZ;
                this.positionUpdateTicks = 0;
            }

            if (flag3) {
                this.lastReportedYaw = yaw;
                this.lastReportedPitch = pitch;
            }

            mc.thePlayer.rotationYawHead = yaw;
            mc.thePlayer.rotationPitchHead = pitch;
        }
    }

    private boolean shouldReportSprintingToServer() {
        return !this.isModernTarget()
                || !this.isInWater();
    }

    /**
     * Called when player presses the drop item key
     */
    public EntityItem dropOneItem(boolean dropAll) {
        C07PacketPlayerDigging.Action c07packetplayerdigging$action = dropAll ? C07PacketPlayerDigging.Action.DROP_ALL_ITEMS : C07PacketPlayerDigging.Action.DROP_ITEM;
        ItemStack held = this.inventory.getCurrentItem();
        if (held == null || held.stackSize <= 0) {
            return null;
        }

        this.sendQueue.addToSendQueue(new C07PacketPlayerDigging(c07packetplayerdigging$action, BlockPos.ORIGIN, EnumFacing.DOWN));
        this.inventory.decrStackSize(this.inventory.currentItem, dropAll ? held.stackSize : 1);
        if (ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_17)) {
            this.swingItem();
        }
        return null;
    }

    /**
     * Joins the passed in entity item with the world. Args: entityItem
     */
    protected void joinEntityItemWithWorld(EntityItem itemIn) {
    }

    /**
     * Sends a chat message from the player. Args: chatMessage
     */
    public void sendChatMessage(String message) {
        this.sendQueue.addToSendQueue(new C01PacketChatMessage(message));
    }

    /**
     * Swings the item the player is holding.
     */
    public void swingItem() {
        super.swingItem();
        this.sendQueue.addToSendQueue(new C0APacketAnimation());
    }

    public void respawnPlayer() {
        this.sendQueue.addToSendQueue(new C16PacketClientStatus(C16PacketClientStatus.EnumState.PERFORM_RESPAWN));
    }

    /**
     * Deals damage to the entity. If its a EntityPlayer then will take damage from the armor first and then health
     * second with the reduced value. Args: damageAmount
     */
    protected void damageEntity(DamageSource damageSrc, float damageAmount) {
        if (!this.isEntityInvulnerable(damageSrc)) {
            this.setHealth(this.getHealth() - damageAmount);
        }
    }

    /**
     * set current crafting inventory back to the 2x2 square
     */
    public void closeScreen() {
        this.sendQueue.addToSendQueue(new C0DPacketCloseWindow(this.openContainer.windowId));
        this.closeScreenAndDropStack();
    }

    public void closeScreenAndDropStack() {
        this.inventory.setItemStack(null);
        super.closeScreen();
        this.mc.displayGuiScreen(null);
    }

    /**
     * Updates health locally.
     */
    public void setPlayerSPHealth(float health) {
        if (this.hasValidHealth) {
            float f = this.getHealth() - health;

            if (f <= 0.0F) {
                this.setHealth(health);

                if (f < 0.0F) {
                    this.hurtResistantTime = this.maxHurtResistantTime / 2;
                }
            } else {
                this.lastDamage = f;
                this.setHealth(this.getHealth());
                this.hurtResistantTime = this.maxHurtResistantTime;
                this.damageEntity(DamageSource.generic, f);
                this.hurtTime = this.maxHurtTime = 10;
            }
        } else {
            this.setHealth(health);
            this.hasValidHealth = true;
        }
    }

    /**
     * Adds a value to a statistic field.
     */
    public void addStat(StatBase stat, int amount) {
        if (stat != null) {
            if (stat.isIndependent) {
                super.addStat(stat, amount);
            }
        }
    }

    /**
     * Sends the player's abilities to the server (if there is one).
     */
    public void sendPlayerAbilities() {
        this.sendQueue.addToSendQueue(new C13PacketPlayerAbilities(this.capabilities));
    }

    /**
     * returns true if this is an EntityPlayerSP, or the logged in player.
     */
    public boolean isUser() {
        return true;
    }

    protected void sendHorseJump() {
        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.RIDING_JUMP, (int) (this.getHorseJumpPower() * 100.0F)));
    }

    public void sendHorseInventory() {
        this.sendQueue.addToSendQueue(new C0BPacketEntityAction(this, C0BPacketEntityAction.Action.OPEN_INVENTORY));
    }

    public StatFileWriter getStatFileWriter() {
        return this.statWriter;
    }

    public void addChatComponentMessage(IChatComponent chatComponent) {
        this.mc.ingameGUI.getChatGUI().printChatMessage(chatComponent);
    }

    protected boolean pushOutOfBlocks(double x, double y, double z) {
        if (!this.noClip) {
            BlockPos blockpos = new BlockPos(x, y, z);
            double d0 = x - (double) blockpos.getX();
            double d1 = z - (double) blockpos.getZ();

            if (!this.isOpenBlockSpace(blockpos)) {
                int i = -1;
                double d2 = 9999.0D;

                if (this.isOpenBlockSpace(blockpos.west()) && d0 < d2) {
                    d2 = d0;
                    i = 0;
                }

                if (this.isOpenBlockSpace(blockpos.east()) && 1.0D - d0 < d2) {
                    d2 = 1.0D - d0;
                    i = 1;
                }

                if (this.isOpenBlockSpace(blockpos.north()) && d1 < d2) {
                    d2 = d1;
                    i = 4;
                }

                if (this.isOpenBlockSpace(blockpos.south()) && 1.0D - d1 < d2) {
                    i = 5;
                }

                float f = 0.1F;

                if (i == 0) {
                    this.motionX = -f;
                }

                if (i == 1) {
                    this.motionX = f;
                }

                if (i == 4) {
                    this.motionZ = -f;
                }

                if (i == 5) {
                    this.motionZ = f;
                }
            }

        }
        return false;
    }

    /**
     * Returns true if the block at the given BlockPos and the block above it are NOT full cubes.
     */
    private boolean isOpenBlockSpace(BlockPos pos) {
        return !this.worldObj.getBlockState(pos).getBlock().isNormalCube() && !this.worldObj.getBlockState(pos.up()).getBlock().isNormalCube();
    }

    /**
     * Set sprinting switch for Entity.
     */
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        this.sprintingTicksLeft = sprinting ? 600 : 0;
    }

    /**
     * Sets the current XP, total XP, and level number.
     */
    public void setXPStats(float currentXP, int maxXP, int level) {
        this.experience = currentXP;
        this.experienceTotal = maxXP;
        this.experienceLevel = level;
    }

    /**
     * Send a chat message to the CommandSender
     */
    public void addChatMessage(IChatComponent component) {
        this.mc.ingameGUI.getChatGUI().printChatMessage(component);
    }

    /**
     * Returns {@code true} if the CommandSender is allowed to execute the command, {@code false} if not
     */
    public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
        return permLevel <= 0;
    }

    /**
     * Get the position in the world. <b>{@code null} is not allowed!</b> If you are not an entity in the world, return
     * the coordinates 0, 0, 0
     */
    public BlockPos getPosition() {
        return new BlockPos(this.posX + 0.5D, this.posY + 0.5D, this.posZ + 0.5D);
    }

    public void playSound(String name, float volume, float pitch) {
        this.worldObj.playSound(this.posX, this.posY, this.posZ, name, volume, pitch, false);
    }

    /**
     * Returns whether the entity is in a server world
     */
    public boolean isServerWorld() {
        return true;
    }

    public boolean isRidingHorse() {
        return this.ridingEntity != null && this.ridingEntity instanceof EntityHorse && ((EntityHorse) this.ridingEntity).isHorseSaddled();
    }

    public void openEditSign(TileEntitySign signTile) {
        this.mc.displayGuiScreen(new GuiEditSign(signTile));
    }

    public void openEditCommandBlock(CommandBlockLogic cmdBlockLogic) {
        this.mc.displayGuiScreen(new GuiCommandBlock(cmdBlockLogic));
    }

    /**
     * Displays the GUI for interacting with a book.
     */
    public void displayGUIBook(ItemStack bookStack) {
        Item item = bookStack.getItem();

        if (item == Items.writable_book) {
            this.mc.displayGuiScreen(new GuiScreenBook(this, bookStack, true));
        }
    }

    /**
     * Displays the GUI for interacting with a chest inventory. Args: chestInventory
     */
    public void displayGUIChest(IInventory chestInventory) {
        String s = chestInventory instanceof IInteractionObject ? ((IInteractionObject) chestInventory).getGuiID() : "minecraft:container";

        if ("minecraft:chest".equals(s)) {
            this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
        } else if ("minecraft:hopper".equals(s)) {
            this.mc.displayGuiScreen(new GuiHopper(this.inventory, chestInventory));
        } else if ("minecraft:furnace".equals(s)) {
            this.mc.displayGuiScreen(new GuiFurnace(this.inventory, chestInventory));
        } else if ("minecraft:brewing_stand".equals(s)) {
            this.mc.displayGuiScreen(new GuiBrewingStand(this.inventory, chestInventory));
        } else if ("minecraft:beacon".equals(s)) {
            this.mc.displayGuiScreen(new GuiBeacon(this.inventory, chestInventory));
        } else if (!"minecraft:dispenser".equals(s) && !"minecraft:dropper".equals(s)) {
            this.mc.displayGuiScreen(new GuiChest(this.inventory, chestInventory));
        } else {
            this.mc.displayGuiScreen(new GuiDispenser(this.inventory, chestInventory));
        }
    }

    public void displayGUIHorse(EntityHorse horse, IInventory horseInventory) {
        this.mc.displayGuiScreen(new GuiScreenHorseInventory(this.inventory, horseInventory, horse));
    }

    public void displayGui(IInteractionObject guiOwner) {
        String s = guiOwner.getGuiID();

        if ("minecraft:crafting_table".equals(s)) {
            this.mc.displayGuiScreen(new GuiCrafting(this.inventory, this.worldObj));
        } else if ("minecraft:enchanting_table".equals(s)) {
            this.mc.displayGuiScreen(new GuiEnchantment(this.inventory, this.worldObj, guiOwner));
        } else if ("minecraft:anvil".equals(s)) {
            this.mc.displayGuiScreen(new GuiRepair(this.inventory, this.worldObj));
        }
    }

    public void displayVillagerTradeGui(IMerchant villager) {
        this.mc.displayGuiScreen(new GuiMerchant(this.inventory, villager, this.worldObj));
    }

    /**
     * Called when the player performs a critical hit on the Entity. Args: entity that was hit critically
     */
    public void onCriticalHit(Entity entityHit) {
        this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT);
    }

    public void onEnchantmentCritical(Entity entityHit) {
        this.mc.effectRenderer.emitParticleAtEntity(entityHit, EnumParticleTypes.CRIT_MAGIC);
    }

    /**
     * Returns if this entity is sneaking.
     */
    public boolean isSneaking() {
        boolean flag = this.movementInput != null && this.movementInput.sneak;
        return flag && !this.sleeping;
    }

    public void updateEntityActionState() {
        super.updateEntityActionState();

        if (this.isCurrentViewEntity()) {
            this.moveStrafing = this.movementInput.moveStrafe;
            this.moveForward = this.movementInput.moveForward;
            this.isJumping = this.movementInput.jump;
            this.prevRenderArmYaw = this.renderArmYaw;
            this.prevRenderArmPitch = this.renderArmPitch;
            this.renderArmPitch = (float) ((double) this.renderArmPitch + (double) (this.rotationPitch - this.renderArmPitch) * 0.5D);
            this.renderArmYaw = (float) ((double) this.renderArmYaw + (double) (this.rotationYaw - this.renderArmYaw) * 0.5D);
        }
    }

    protected boolean isCurrentViewEntity() {
        return this.mc.getRenderViewEntity() == this;
    }

    /**
     * Called frequently so the entity can update its state every tick as required. For example, zombies and skeletons
     * use this to react to sunlight and start to burn.
     */
    public void onLivingUpdate() {
        this.movementInputAdjustedThisTick = false;
        this.updateModernSwimmingStateHead();
        this.updateModernSneakingPose();

        if (this.sprintingTicksLeft > 0) {
            --this.sprintingTicksLeft;

            if (this.sprintingTicksLeft == 0) {
                this.setSprinting(false);
            }
        }

        if (this.sprintToggleTimer > 0) {
            --this.sprintToggleTimer;
        }

        this.prevTimeInPortal = this.timeInPortal;

        if (this.inPortal) {
            if (this.mc.currentScreen != null && !this.mc.currentScreen.doesGuiPauseGame()) {
                this.mc.displayGuiScreen(null);
            }

            if (this.timeInPortal == 0.0F) {
                this.mc.getSoundHandler().playSound(PositionedSoundRecord.create(ResourceLocation.of("portal.trigger"), this.rand.nextFloat() * 0.4F + 0.8F));
            }

            this.timeInPortal += 0.0125F;

            if (this.timeInPortal >= 1.0F) {
                this.timeInPortal = 1.0F;
            }

            this.inPortal = false;
        } else if (this.isPotionActiveForLivingUpdate(Potion.confusion) && this.getActivePotionEffect(Potion.confusion).getDuration() > 60) {
            this.timeInPortal += 0.006666667F;

            if (this.timeInPortal > 1.0F) {
                this.timeInPortal = 1.0F;
            }
        } else {
            if (this.timeInPortal > 0.0F) {
                this.timeInPortal -= 0.05F;
            }

            if (this.timeInPortal < 0.0F) {
                this.timeInPortal = 0.0F;
            }
        }

        if (this.timeUntilPortal > 0) {
            --this.timeUntilPortal;
        }

        boolean flag = this.movementInput.jump;
        boolean flag1 = this.movementInput.sneak;
        float f = 0.8F;
        boolean flag2 = this.movementInput.moveForward >= f;
        this.movementInput.updatePlayerMoveState();
        EventManager.call(new MoveInputEvent());
        this.viaforge$updateModernMovementInput(this.movementInput);
        boolean currentlySneaking = this.movementInput.sneak;

        if (this.isUsingItemForSlowdown() && !this.isRiding()) {
            this.movementInput.moveStrafe *= 0.2F;
            this.movementInput.moveForward *= 0.2F;
            this.sprintToggleTimer = 0;
        }

        if (ViaLoadingBase.getInstance().getTargetVersion().newerThanOrEqualTo(ProtocolVersion.v1_14)) {
            jumpMovementFactor = 0.02F;
            if (this.isSprinting()) {
                jumpMovementFactor = (float) ((double) jumpMovementFactor + 0.005999999865889549D);
            }
        }

        this.viaforge$pushOutOfBlocks(this.posX - (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double) this.width * 0.35D);
        this.viaforge$pushOutOfBlocks(this.posX - (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double) this.width * 0.35D);
        this.viaforge$pushOutOfBlocks(this.posX + (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ - (double) this.width * 0.35D);
        this.viaforge$pushOutOfBlocks(this.posX + (double) this.width * 0.35D, this.getEntityBoundingBox().minY + 0.5D, this.posZ + (double) this.width * 0.35D);
        boolean flag3 = (float) this.getFoodStats().getFoodLevel() > 6.0F || this.capabilities.allowFlying;

        if (this.onGround && !currentlySneaking && !flag2 && this.movementInput.moveForward >= f && !this.isSprinting() && flag3 && !this.isUsingItem() && !this.isPotionActive(Potion.blindness)) {
            if (this.sprintToggleTimer <= 0 && !this.mc.gameSettings.keyBindSprint.isKeyDown()) {
                this.sprintToggleTimer = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if (!this.isSprinting() && !currentlySneaking && this.movementInput.moveForward >= f && flag3 && !this.isUsingItem() && !this.isPotionActive(Potion.blindness) && this.mc.gameSettings.keyBindSprint.isKeyDown()) {
            this.setSprinting(true);
        }

        boolean collidedBlockingSprint = this.isCollidedHorizontally
                && (ViaLoadingBase.getInstance().getTargetVersion().olderThan(ProtocolVersion.v1_14) || !this.isCollidingWithWall);

        boolean waterBlockingSprint = this.isInWater()
                && ViaLoadingBase.getInstance().getTargetVersion().olderThan(ProtocolVersion.v1_13);

        boolean sneakingBlockingSprint = ViaProtocol.newerThanOrEqualTo1_9() && currentlySneaking;

        if (this.isSprinting() && (this.movementInput.moveForward < f || collidedBlockingSprint || !flag3 || waterBlockingSprint || sneakingBlockingSprint)) {
            this.setSprinting(false);
        }

        if (this.capabilities.allowFlying) {
            if (this.mc.playerController.isSpectatorMode()) {
                if (!this.capabilities.isFlying) {
                    this.capabilities.isFlying = true;
                    this.sendPlayerAbilities();
                }
            } else if (!flag && this.movementInput.jump) {
                if (this.flyToggleTimer == 0) {
                    this.flyToggleTimer = 7;
                } else {
                    this.capabilities.isFlying = !this.capabilities.isFlying;
                    this.sendPlayerAbilities();
                    this.flyToggleTimer = 0;
                }
            }
        }

        if (this.capabilities.isFlying && this.isCurrentViewEntity()) {
            if (this.movementInput.sneak) {
                this.motionY -= this.capabilities.getFlySpeed() * 3.0F;
            }

            if (this.movementInput.jump) {
                this.motionY += this.capabilities.getFlySpeed() * 3.0F;
            }
        }

        if (this.isRidingHorse()) {
            if (this.horseJumpPowerCounter < 0) {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter == 0) {
                    this.horseJumpPower = 0.0F;
                }
            }

            if (flag && !this.movementInput.jump) {
                this.horseJumpPowerCounter = -10;
                this.sendHorseJump();
            } else if (!flag && this.movementInput.jump) {
                this.horseJumpPowerCounter = 0;
                this.horseJumpPower = 0.0F;
            } else if (flag) {
                ++this.horseJumpPowerCounter;

                if (this.horseJumpPowerCounter < 10) {
                    this.horseJumpPower = (float) this.horseJumpPowerCounter * 0.1F;
                } else {
                    this.horseJumpPower = 0.8F + 2.0F / (float) (this.horseJumpPowerCounter - 9) * 0.1F;
                }
            }
        } else {
            this.horseJumpPower = 0.0F;
        }

        EventManager.call(new LivingUpdateEvent());
        super.onLivingUpdate();

        this.previousOffhandSwingProgress = this.offhandSwingProgress;
        if (!ModernOffhandInteraction.isModernTarget()) {
            this.offhandSwinging = false;
            this.offhandSwingTicks = 0;
            this.offhandSwingProgress = 0.0F;
            this.previousOffhandSwingProgress = 0.0F;
        } else
        if (this.offhandSwinging) {
            ++this.offhandSwingTicks;
            if (this.offhandSwingTicks >= 6) {
                this.offhandSwingTicks = 0;
                this.offhandSwinging = false;
            }
        } else {
            this.offhandSwingTicks = 0;
        }
        this.offhandSwingProgress = (float) this.offhandSwingTicks / 6.0F;
        if (this.isModernTarget()) {
            this.usingItemAtPreviousTick = this.usingItemAtTickStart;
        }

        if (ViaProtocol.newerThanOrEqualTo1_14()) {
            this.jumpMovementFactor = 0.02F;
            if (isSprinting()) {
                this.jumpMovementFactor = (float) (this.jumpMovementFactor + 0.005999999865889549D);
            }
        }

        if (this.onGround && this.capabilities.isFlying && !this.mc.playerController.isSpectatorMode()) {
            this.capabilities.isFlying = false;
            this.sendPlayerAbilities();
        }
    }

    @Override
    public void viaforge$swingOffhand() {
        if (!this.offhandSwinging || this.offhandSwingTicks >= 3) {
            this.offhandSwingTicks = 0;
            this.offhandSwingProgress = 0.0F;
            this.previousOffhandSwingProgress = 0.0F;
            this.offhandSwinging = true;
        }
    }

    @Override
    public float viaforge$getOffhandSwingProgress(float partialTicks) {
        float delta = this.offhandSwingProgress - this.previousOffhandSwingProgress;
        if (delta < 0.0F) {
            delta += 1.0F;
        }
        return this.previousOffhandSwingProgress + delta * partialTicks;
    }

    @Override
    public float getEyeHeight() {
        if (this.ridingEntity instanceof EntityBoat) {
            return super.getEyeHeight();
        }
        return this.usesModernSneakPose() || this.isModernTarget() ? this.modernEyeHeight : super.getEyeHeight();
    }

    private void updateModernSwimmingStateHead() {
        if (!this.usesModernInputPhysics()) {
            this.modernSwimming = false;
            this.wasModernSwimming = false;
            this.modernSubmergedInWater = false;
            this.wasEyeInWater = false;
            this.wasSprintingBeforeInput = false;
            this.mainSupportingBlock = null;
            this.supportingBlockOnGround = false;
            this.modernEyeHeight = 1.62F;
            if (this.ridingEntity instanceof EntityBoat) {
                this.setModernHeight(1.8F);
            }
            this.slowMovementFromPreviousPose = false;
            this.modernWaterHeight = 0.0D;
            this.modernLavaHeight = 0.0D;
            this.touchingModernLava = false;
            this.usingItemAtPreviousTick = false;
            this.usingItemAtTickStart = false;
            this.carryItemUseSlowdown = false;
            this.localItemUseFinished = false;
            this.serverItemUseFinished = false;
            this.itemUseFinishGraceTicks = 0;
            return;
        }

        this.wasModernSwimming = this.modernSwimming;
        this.wasSprintingBeforeInput = this.isSprinting();
        this.usingItemAtTickStart = this.isUsingItem();
        if (this.localItemUseFinished) {
            this.itemUseFinishGraceTicks = this.serverItemUseFinished ? 0 : 2;
            this.localItemUseFinished = false;
        }
        if (this.serverItemUseFinished) {
            this.itemUseFinishGraceTicks = 0;
            this.serverItemUseFinished = false;
        }
        this.carryItemUseSlowdown = this.itemUseFinishGraceTicks > 0
                || this.usingItemAtPreviousTick
                && !this.usingItemAtTickStart
                && Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown();
        if (this.itemUseFinishGraceTicks > 0) {
            this.itemUseFinishGraceTicks--;
        }
    }

    @Override
    public void viaforge$updateModernMovementInput(MovementInput input) {
        if (this.movementInputAdjustedThisTick || !this.usesModernInputPhysics()) {
            return;
        }
        this.movementInputAdjustedThisTick = true;

        if (ViaProtocol.newerThanOrEqualTo1_14() && input.sneak && !this.slowMovementFromPreviousPose) {
            input.moveStrafe /= 0.3F;
            input.moveForward /= 0.3F;
        } else if (ViaProtocol.newerThanOrEqualTo1_14() && !input.sneak && this.slowMovementFromPreviousPose) {
            input.moveStrafe *= 0.3F;
            input.moveForward *= 0.3F;
        }

        if (ViaProtocol.newerThanOrEqualTo1_14() && this.carryItemUseSlowdown) {
            input.moveStrafe *= 0.2F;
            input.moveForward *= 0.2F;
        }

        this.updateSwimmingAndPose();

        if (ViaProtocol.newerThanOrEqualTo1_14()
                && (this.isUsingItem() || this.carryItemUseSlowdown) && !this.isRiding()) {
            this.setSprinting(false);
        }

        if (ViaProtocol.newerThanOrEqualTo1_13() && this.isInWater()
                && input.sneak
                && !this.capabilities.isFlying
                && !this.isRiding()) {
            this.motionY -= 0.04F;
        }

        if (ViaProtocol.newerThanOrEqualTo1_14()) {
            this.jumpMovementFactor = this.isSprinting() ? 0.025999999F : 0.02F;
        }
    }

    private void updateSwimmingAndPose() {
        boolean eyeInWater = this.isModernEyeInWater();
        this.wasEyeInWater = ViaProtocol.olderThanOrEqualTo(ProtocolVersion.v1_15_2)
                ? eyeInWater
                : this.modernSubmergedInWater;
        this.modernSubmergedInWater = eyeInWater;
        boolean feetInWater = !ViaProtocol.newerThanOrEqualTo(ProtocolVersion.v1_17)
                || ModernFluidPhysics.getWaterHeight(this.worldObj, new BlockPos(this.posX, this.posY, this.posZ)) > 0.0F;
        if (this.capabilities.isFlying || this.isRiding()) {
            this.modernSwimming = false;
        } else if (this.wasModernSwimming) {
            this.modernSwimming = this.wasSprintingBeforeInput && this.isInWater();
        } else {
            this.modernSwimming = this.wasSprintingBeforeInput && this.wasEyeInWater && this.isInWater() && feetInWater;
        }

        float desiredHeight;
        boolean canCrouch = this.canUseHeight(1.5F);
        boolean canStand = this.canUseHeight(1.8F);
        if (this.isElytraFlying() || this.modernSwimming || ViaProtocol.newerThanOrEqualTo1_14() && !canCrouch) {
            desiredHeight = 0.6F;
            this.modernEyeHeight = 0.4F;
        } else if (ViaProtocol.newerThanOrEqualTo1_14() && (this.isSneaking() || !canStand)) {
            desiredHeight = 1.5F;
            this.modernEyeHeight = 1.27F;
        } else if (this.isSneaking()) {
            desiredHeight = 1.65F;
            this.modernEyeHeight = 1.54F;
        } else {
            desiredHeight = 1.8F;
            this.modernEyeHeight = 1.62F;
        }
        this.setModernHeight(desiredHeight);

        this.slowMovementFromPreviousPose = ViaProtocol.newerThanOrEqualTo1_14()
                && !this.capabilities.isFlying
                && !this.isRiding()
                && !this.modernSwimming
                && canCrouch
                && (this.isSneaking() || !canStand);
    }

    private void updateModernSneakingPose() {
        if (this.isModernTarget() || !this.usesModernSneakPose()) {
            return;
        }

        if (this.isElytraFlying()) {
            this.modernEyeHeight = 0.4F;
            this.setModernHeight(0.6F);
            return;
        }

        float crouchingHeight = ViaProtocol.newerThanOrEqualTo1_14() ? 1.5F : 1.65F;
        float crouchingEyeHeight = ViaProtocol.newerThanOrEqualTo1_14() ? 1.27F : 1.54F;
        boolean canCrouch = this.canUseHeight(crouchingHeight);
        boolean canStand = this.canUseHeight(1.8F);

        if ((this.isSneaking() || !canStand) && canCrouch) {
            this.modernEyeHeight = crouchingEyeHeight;
            this.setModernHeight(crouchingHeight);
        } else {
            this.modernEyeHeight = 1.62F;
            this.setModernHeight(1.8F);
        }
    }

    private boolean canUseHeight(float height) {
        AxisAlignedBB box = this.getEntityBoundingBox();
        AxisAlignedBB requested = new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.minY + height, box.maxZ);
        return this.worldObj.getCollidingBoundingBoxes(this, requested).isEmpty();
    }

    private void setModernHeight(float height) {
        if (this.height == height) {
            return;
        }

        AxisAlignedBB box = this.getEntityBoundingBox();
        this.height = height;
        this.setEntityBoundingBox(new AxisAlignedBB(box.minX, box.minY, box.minZ, box.maxX, box.minY + height, box.maxZ));
    }

    private boolean isModernEyeInWater() {
        double eyeY = this.posY + (double) this.modernEyeHeight - 0.1111111119389534D;
        BlockPos eyePosition = new BlockPos(this.posX, eyeY, this.posZ);
        return (double) eyePosition.getY() + (double) ModernFluidPhysics.getWaterHeight(this.worldObj, eyePosition) > eyeY;
    }

    @Override
    public boolean viaforge$isModernSwimming() {
        return this.modernSwimming;
    }

    @Override
    public boolean viaforge$wasModernSwimming() {
        return this.wasModernSwimming;
    }

    @Override
    public boolean viaforge$isModernSubmergedInWater() {
        return this.modernSubmergedInWater;
    }

    @Override
    public void viaforge$setModernSubmergedInWater(boolean submerged) {
        this.modernSubmergedInWater = submerged;
    }

    @Override
    public boolean viaforge$wasModernEyeInWater() {
        return this.wasEyeInWater;
    }

    @Override
    public float viaforge$getModernEyeHeight() {
        return this.modernEyeHeight;
    }

    @Override
    public double viaforge$getModernWaterHeight() {
        return this.modernWaterHeight;
    }

    @Override
    public void viaforge$setModernWaterHeight(double height) {
        this.modernWaterHeight = height;
    }

    @Override
    public double viaforge$getModernLavaHeight() {
        return this.modernLavaHeight;
    }

    @Override
    public void viaforge$setModernLavaHeight(double height) {
        this.modernLavaHeight = height;
    }

    @Override
    public boolean viaforge$isTouchingModernLava() {
        return this.touchingModernLava;
    }

    @Override
    public void viaforge$setTouchingModernLava(boolean touching) {
        this.touchingModernLava = touching;
    }

    @Override
    public BlockPos viaforge$getMainSupportingBlock() {
        return this.mainSupportingBlock;
    }

    @Override
    public boolean viaforge$wasSupportingBlockOnGround() {
        return this.supportingBlockOnGround;
    }

    @Override
    public void viaforge$setMainSupportingBlock(BlockPos position, boolean onGround) {
        this.mainSupportingBlock = position;
        this.supportingBlockOnGround = onGround;
    }

    @Override
    public void viaforge$markLocalItemUseFinished() {
        this.localItemUseFinished = true;
    }

    @Override
    public void viaforge$confirmServerItemUseFinished() {
        this.serverItemUseFinished = true;
    }

    private boolean viaforge$pushOutOfBlocks(double x, double y, double z) {
        return !ViaProtocol.newerThanOrEqualTo1_9() && this.pushOutOfBlocks(x, y, z);
    }

    public void viaforge$delayFoodUseRestart() {
        this.foodUseRestartDelayTicks = 1;
        this.foodUseRestartSlot = this.inventory.currentItem;
        ItemStack held = this.inventory.getCurrentItem();
        this.foodUseRestartItem = held == null ? null : held.getItem();
    }

    public boolean viaforge$consumeFoodUseRestartDelayTick() {
        if (this.foodUseRestartDelayTicks <= 0) {
            return false;
        }

        --this.foodUseRestartDelayTicks;
        ItemStack held = this.inventory.getCurrentItem();
        boolean sameFood = this.inventory.currentItem == this.foodUseRestartSlot
                && held != null
                && held.getItem() == this.foodUseRestartItem
                && held.getItem() instanceof ItemFood;
        this.foodUseRestartSlot = -1;
        this.foodUseRestartItem = null;
        return sameFood;
    }

    private boolean isModernTarget() {
        return !(this.ridingEntity instanceof EntityBoat) && ViaProtocol.newerThanOrEqualTo1_14();
    }

    private boolean usesModernInputPhysics() {
        return !(this.ridingEntity instanceof EntityBoat) && ViaProtocol.newerThanOrEqualTo1_13();
    }

    private boolean usesModernSneakPose() {
        return !(this.ridingEntity instanceof EntityBoat) && ViaProtocol.newerThanOrEqualTo1_9();
    }

    private boolean isUsingItemForSlowdown() {
        if (Unfair.moduleManager != null) {
            NoSlow noSlow = (NoSlow) Unfair.moduleManager.modules.get(NoSlow.class);

            if (noSlow.isEnabled()) {
                if (noSlow.shouldApplyC0FSwapSlowdown()) {
                    return true;
                }

                if (noSlow.isAnyActive()) {
                    return false;
                }
            }
        }

        return this.isUsingItem();
    }

    private boolean isPotionActiveForLivingUpdate(Potion potion) {
        if (potion == Potion.confusion && Unfair.moduleManager != null) {
            AntiDebuff antiDebuff = (AntiDebuff) Unfair.moduleManager.modules.get(AntiDebuff.class);

            if (antiDebuff.isEnabled() && antiDebuff.nausea.getValue()) {
                return false;
            }
        }

        return this.getActivePotionsMap().containsKey(potion.id);
    }
}
