package net.minecraft.client.multiplayer;

import cn.unfair.event.EventManager;
import cn.unfair.events.AttackEvent;
import cn.unfair.events.CancelUseEvent;
import cn.unfair.events.WindowClickEvent;
import cn.unfair.module.modules.misc.ViaVersionFix;
import cn.unfair.util.via.BlockStatePredictionHandler;
import cn.unfair.util.via.ViaProtocol;
import com.viaversion.viabackwards.protocol.v1_19_1to1_19.Protocol1_19_1To1_19;
import com.viaversion.viabackwards.protocol.v1_19_3to1_19_1.Protocol1_19_3To1_19_1;
import com.viaversion.viabackwards.protocol.v1_19_4to1_19_3.Protocol1_19_4To1_19_3;
import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3;
import com.viaversion.viabackwards.protocol.v1_20to1_19_4.Protocol1_20To1_19_4;
import com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21;
import com.viaversion.viabackwards.protocol.v1_21_4to1_21_2.Protocol1_21_4To1_21_2;
import com.viaversion.viabackwards.protocol.v1_21_5to1_21_4.Protocol1_21_5To1_21_4;
import com.viaversion.viabackwards.protocol.v1_21_6to1_21_5.Protocol1_21_6To1_21_5;
import com.viaversion.viabackwards.protocol.v1_21_7to1_21_6.Protocol1_21_7To1_21_6;
import com.viaversion.viabackwards.protocol.v1_21to1_20_5.Protocol1_21To1_20_5;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.ProtocolInfo;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.Protocol;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocol.packet.PacketWrapperImpl;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import com.viaversion.viaversion.protocols.v1_19_1to1_19_3.packet.ServerboundPackets1_19_3;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ServerboundPackets1_19_4;
import com.viaversion.viaversion.protocols.v1_19to1_19_1.packet.ServerboundPackets1_19_1;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.packet.ServerboundPackets1_21_4;
import com.viaversion.viaversion.protocols.v1_21_4to1_21_5.packet.ServerboundPackets1_21_5;
import com.viaversion.viaversion.protocols.v1_21_5to1_21_6.packet.ServerboundPackets1_21_6;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.vialoadingbase.netty.handler.VLBViaDecodeHandler;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.*;
import net.minecraft.stats.StatFileWriter;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;

public class PlayerControllerMP
{
    /** The Minecraft instance. */
    private final Minecraft mc;
    private final NetHandlerPlayClient netClientHandler;
    private BlockPos currentBlock = new BlockPos(-1, -1, -1);

    /** The Item currently being used to destroy a block */
    private ItemStack currentItemHittingBlock;

    /** Current block damage (MP) */
    private float curBlockDamageMP;

    /**
     * Tick counter, when it hits 4 it resets back to 0 and plays the step sound
     */
    private float stepSoundTickCounter;

    /**
     * Delays the first damage on the block after the first click on the block
     */
    private int blockHitDelay;

    /** Tells if the player is hitting a block */
    private boolean isHittingBlock;

    /** Current game type for the player */
    private WorldSettings.GameType currentGameType = WorldSettings.GameType.SURVIVAL;

    /** Index of the current item held by the player in the inventory hotbar */
    private int currentPlayerItem;

    public PlayerControllerMP(Minecraft mcIn, NetHandlerPlayClient netHandler)
    {
        this.mc = mcIn;
        this.netClientHandler = netHandler;
    }

    public static void clickBlockCreative(Minecraft mcIn, PlayerControllerMP playerController, BlockPos pos, EnumFacing facing)
    {
        if (!mcIn.theWorld.extinguishFire(mcIn.thePlayer, pos, facing))
        {
            playerController.onPlayerDestroyBlock(pos, facing);
        }
    }

    /**
     * Sets player capabilities depending on current gametype. params: player
     *  
     * @param player The player's instance
     */
    public void setPlayerCapabilities(EntityPlayer player)
    {
        this.currentGameType.configurePlayerCapabilities(player.capabilities);
    }

    /**
     * None
     */
    public boolean isSpectator()
    {
        return this.currentGameType == WorldSettings.GameType.SPECTATOR;
    }

    /**
     * Sets the game type for the player.
     *  
     * @param type The GameType to set
     */
    public void setGameType(WorldSettings.GameType type)
    {
        this.currentGameType = type;
        this.currentGameType.configurePlayerCapabilities(this.mc.thePlayer.capabilities);
    }

    /**
     * Flips the player around.
     */
    public void flipPlayer(EntityPlayer playerIn)
    {
        playerIn.rotationYaw = -180.0F;
    }

    public boolean shouldDrawHUD()
    {
        return this.currentGameType.isSurvivalOrAdventure();
    }

    /**
     * Called when a player completes the destruction of a block
     */
    public boolean onPlayerDestroyBlock(BlockPos pos, EnumFacing side)
    {
        if (this.currentGameType.isAdventure())
        {
            if (this.currentGameType == WorldSettings.GameType.SPECTATOR)
            {
                return false;
            }

            if (!this.mc.thePlayer.isAllowEdit())
            {
                Block block = this.mc.theWorld.getBlockState(pos).getBlock();
                ItemStack itemstack = this.mc.thePlayer.getCurrentEquippedItem();

                if (itemstack == null)
                {
                    return false;
                }

                if (!itemstack.canDestroy(block))
                {
                    return false;
                }
            }
        }

        if (this.currentGameType.isCreative() && this.mc.thePlayer.getHeldItem() != null && this.mc.thePlayer.getHeldItem().getItem() instanceof ItemSword)
        {
            return false;
        }
        else
        {
            World world = this.mc.theWorld;
            IBlockState iblockstate = world.getBlockState(pos);
            Block block1 = iblockstate.getBlock();

            if (block1.getMaterial() == Material.air)
            {
                return false;
            }
            else
            {
                world.playAuxSFX(2001, pos, Block.getStateId(iblockstate));
                boolean flag = world.setBlockToAir(pos);

                if (flag)
                {
                    block1.onBlockDestroyedByPlayer(world, pos, iblockstate);
                }

                this.currentBlock = new BlockPos(this.currentBlock.getX(), -1, this.currentBlock.getZ());

                if (!this.currentGameType.isCreative())
                {
                    ItemStack itemstack1 = this.mc.thePlayer.getCurrentEquippedItem();

                    if (itemstack1 != null)
                    {
                        itemstack1.onBlockDestroyed(world, block1, pos, this.mc.thePlayer);

                        if (itemstack1.stackSize == 0)
                        {
                            this.mc.thePlayer.destroyCurrentEquippedItem();
                        }
                    }
                }

                return flag;
            }
        }
    }

    /**
     * Called when the player is hitting a block with an item.
     */
    public boolean clickBlock(BlockPos loc, EnumFacing face)
    {
        if (this.currentGameType.isAdventure())
        {
            if (this.currentGameType == WorldSettings.GameType.SPECTATOR)
            {
                return false;
            }

            if (!this.mc.thePlayer.isAllowEdit())
            {
                Block block = this.mc.theWorld.getBlockState(loc).getBlock();
                ItemStack itemstack = this.mc.thePlayer.getCurrentEquippedItem();

                if (itemstack == null)
                {
                    return false;
                }

                if (!itemstack.canDestroy(block))
                {
                    return false;
                }
            }
        }

        if (!this.mc.theWorld.getWorldBorder().contains(loc))
        {
            return false;
        }
        else
        {
            if (this.currentGameType.isCreative())
            {
                if (ViaProtocol.newerThanOrEqualTo1_19()) {
                    BlockStatePredictionHandler.isC08 = false;
                    this.netClientHandler.addToSendQueue(new ServerBoundPlayerAction(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, loc, face));
                } else {
                    this.netClientHandler.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, loc, face));
                }
                clickBlockCreative(this.mc, this, loc, face);
                this.blockHitDelay = 5;
            }
            else if (!this.isHittingBlock || !this.isHittingPosition(loc))
            {
                if (this.isHittingBlock)
                {
                    if (ViaProtocol.newerThanOrEqualTo1_19()) {
                        BlockStatePredictionHandler.isC08 = false;
                        this.netClientHandler.addToSendQueue(new ServerBoundPlayerAction(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.currentBlock, face));
                    } else {
                        this.netClientHandler.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.currentBlock, face));
                    }
                }

                if (ViaProtocol.newerThanOrEqualTo1_19()) {
                    this.netClientHandler.addToSendQueue(new ServerBoundPlayerAction(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, loc, face));
                } else {
                    this.netClientHandler.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, loc, face));
                }
                Block block1 = this.mc.theWorld.getBlockState(loc).getBlock();
                boolean flag = block1.getMaterial() != Material.air;

                if (flag && this.curBlockDamageMP == 0.0F)
                {
                    block1.onBlockClicked(this.mc.theWorld, loc, this.mc.thePlayer);
                }

                if (flag && block1.getPlayerRelativeBlockHardness(this.mc.thePlayer, this.mc.thePlayer.worldObj, loc) >= 1.0F)
                {
                    this.onPlayerDestroyBlock(loc, face);
                }
                else
                {
                    this.isHittingBlock = true;
                    this.currentBlock = loc;
                    this.currentItemHittingBlock = this.mc.thePlayer.getHeldItem();
                    this.curBlockDamageMP = 0.0F;
                    this.stepSoundTickCounter = 0.0F;
                    this.mc.theWorld.sendBlockBreakProgress(this.mc.thePlayer.getEntityId(), this.currentBlock, (int)(this.curBlockDamageMP * 10.0F) - 1);
                }
            }

            return true;
        }
    }

    /**
     * Resets current block damage and isHittingBlock
     */
    public void resetBlockRemoving()
    {
        if (this.isHittingBlock)
        {
            if (ViaProtocol.newerThanOrEqualTo1_19()) {
                this.netClientHandler.addToSendQueue(new ServerBoundPlayerAction(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.currentBlock, EnumFacing.DOWN));
            } else {
                this.netClientHandler.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK, this.currentBlock, EnumFacing.DOWN));
            }
            this.isHittingBlock = false;
            this.curBlockDamageMP = 0.0F;
            this.mc.theWorld.sendBlockBreakProgress(this.mc.thePlayer.getEntityId(), this.currentBlock, -1);
        }
    }

    public boolean onPlayerDamageBlock(BlockPos posBlock, EnumFacing directionFacing)
    {
        this.syncCurrentPlayItem();

        if (this.blockHitDelay > 0)
        {
            --this.blockHitDelay;
            return true;
        }
        else if (this.currentGameType.isCreative() && this.mc.theWorld.getWorldBorder().contains(posBlock))
        {
            this.blockHitDelay = 5;
            if (ViaProtocol.newerThanOrEqualTo1_19()) {
                BlockStatePredictionHandler.isC08 = false;
                this.netClientHandler.addToSendQueue(new ServerBoundPlayerAction(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, posBlock, directionFacing));
            } else {
                this.netClientHandler.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, posBlock, directionFacing));
            }
            clickBlockCreative(this.mc, this, posBlock, directionFacing);
            return true;
        }
        else if (this.isHittingPosition(posBlock))
        {
            Block block = this.mc.theWorld.getBlockState(posBlock).getBlock();

            if (block.getMaterial() == Material.air)
            {
                this.isHittingBlock = false;
                return false;
            }
            else
            {
                this.curBlockDamageMP += block.getPlayerRelativeBlockHardness(this.mc.thePlayer, this.mc.thePlayer.worldObj, posBlock);

                if (this.stepSoundTickCounter % 4.0F == 0.0F)
                {
                    this.mc.getSoundHandler().playSound(new PositionedSoundRecord(ResourceLocation.of(block.stepSound.getStepSound()), (block.stepSound.getVolume() + 1.0F) / 8.0F, block.stepSound.getFrequency() * 0.5F, (float)posBlock.getX() + 0.5F, (float)posBlock.getY() + 0.5F, (float)posBlock.getZ() + 0.5F));
                }

                ++this.stepSoundTickCounter;

                if (this.curBlockDamageMP >= 1.0F)
                {
                    this.isHittingBlock = false;
                    if (ViaProtocol.newerThanOrEqualTo1_19()) {
                        BlockStatePredictionHandler.isC08 = false;
                        this.netClientHandler.addToSendQueue(new ServerBoundPlayerAction(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, posBlock, directionFacing));
                    } else {
                        this.netClientHandler.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, posBlock, directionFacing));
                    }
                    this.onPlayerDestroyBlock(posBlock, directionFacing);
                    this.curBlockDamageMP = 0.0F;
                    this.stepSoundTickCounter = 0.0F;
                    this.blockHitDelay = 5;
                }

                this.mc.theWorld.sendBlockBreakProgress(this.mc.thePlayer.getEntityId(), this.currentBlock, (int)(this.curBlockDamageMP * 10.0F) - 1);
                return true;
            }
        }
        else
        {
            return this.clickBlock(posBlock, directionFacing);
        }
    }

    /**
     * player reach distance = 4F
     */
    public float getBlockReachDistance()
    {
        return this.currentGameType.isCreative() ? 5.0F : 4.5F;
    }

    public void updateController()
    {
        this.syncCurrentPlayItem();

        if (this.netClientHandler.getNetworkManager().isChannelOpen())
        {
            this.netClientHandler.getNetworkManager().processReceivedPackets();
        }
        else
        {
            this.netClientHandler.getNetworkManager().checkDisconnected();
        }
    }

    private boolean isHittingPosition(BlockPos pos)
    {
        ItemStack itemstack = this.mc.thePlayer.getHeldItem();
        boolean flag = this.currentItemHittingBlock == null && itemstack == null;

        if (this.currentItemHittingBlock != null && itemstack != null)
        {
            flag = itemstack.getItem() == this.currentItemHittingBlock.getItem() && ItemStack.areItemStackTagsEqual(itemstack, this.currentItemHittingBlock) && (itemstack.isItemStackDamageable() || itemstack.getMetadata() == this.currentItemHittingBlock.getMetadata());
        }

        return pos.equals(this.currentBlock) && flag;
    }

    /**
     * Syncs the current player item with the server
     */
    public void syncCurrentPlayItem()
    {
        int i = this.mc.thePlayer.inventory.currentItem;

        if (i != this.currentPlayerItem)
        {
            this.currentPlayerItem = i;
            this.netClientHandler.addToSendQueue(new C09PacketHeldItemChange(this.currentPlayerItem));
        }
    }

    public boolean onPlayerRightClick(EntityPlayerSP player, WorldClient worldIn, ItemStack heldStack, BlockPos hitPos, EnumFacing side, Vec3 hitVec)
    {
        this.syncCurrentPlayItem();
        float f = (float)(hitVec.xCoord - (double)hitPos.getX());
        float f1 = (float)(hitVec.yCoord - (double)hitPos.getY());
        float f2 = (float)(hitVec.zCoord - (double)hitPos.getZ());
        boolean flag = false;

        if (!this.mc.theWorld.getWorldBorder().contains(hitPos))
        {
            return false;
        }
        else
        {
            if (this.currentGameType != WorldSettings.GameType.SPECTATOR)
            {
                IBlockState iblockstate = worldIn.getBlockState(hitPos);

                if ((!player.isSneaking() || player.getHeldItem() == null) && iblockstate.getBlock().onBlockActivated(worldIn, hitPos, iblockstate, player, side, f, f1, f2))
                {
                    flag = true;
                }

                if (!flag && heldStack != null && heldStack.getItem() instanceof ItemBlock)
                {
                    ItemBlock itemblock = (ItemBlock)heldStack.getItem();

                    if (!itemblock.canPlaceBlockOnSide(worldIn, hitPos, side, player, heldStack))
                    {
                        return false;
                    }
                }
            }

            if (ViaProtocol.newerThanOrEqualTo1_19()) {
                BlockStatePredictionHandler.isC08 = true;
                UserConnection userConnection = getViaUserConnection();
                if (userConnection != null) {
                    PacketWrapper packetWrapperCreate = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM_ON, userConnection);
                    packetWrapperCreate.write(Types.VAR_INT, 0);
                    packetWrapperCreate.write(Types.BLOCK_POSITION1_14, new BlockPosition(hitPos.getX(), hitPos.getY(), hitPos.getZ()));
                    packetWrapperCreate.write(Types.VAR_INT, side.ordinal());
                    packetWrapperCreate.write(Types.FLOAT, f);
                    packetWrapperCreate.write(Types.FLOAT, f1);
                    packetWrapperCreate.write(Types.FLOAT, f2);
                    packetWrapperCreate.write(Types.BOOLEAN, false);
                    packetWrapperCreate.write(Types.VAR_INT, ViaVersionFix.sequence());
                    packetWrapperCreate.sendToServer(Protocol1_19To1_18_2.class);
                }
            } else {
                this.netClientHandler.addToSendQueue(new C08PacketPlayerBlockPlacement(hitPos, side.getIndex(), player.inventory.getCurrentItem(), f, f1, f2));
            }

            if (!flag && this.currentGameType != WorldSettings.GameType.SPECTATOR)
            {
                if (heldStack == null)
                {
                    return false;
                }
                else if (this.currentGameType.isCreative())
                {
                    int i = heldStack.getMetadata();
                    int j = heldStack.stackSize;
                    boolean flag1 = heldStack.onItemUse(player, worldIn, hitPos, side, f, f1, f2);
                    heldStack.setItemDamage(i);
                    heldStack.stackSize = j;
                    return flag1;
                }
                else
                {
                    return heldStack.onItemUse(player, worldIn, hitPos, side, f, f1, f2);
                }
            }
            else
            {
                return true;
            }
        }
    }

    private UserConnection getViaUserConnection() {
        if (Via.getManager() == null || Via.getManager().getConnectionManager() == null) {
            return null;
        }

        try {
            return Via.getManager().getConnectionManager().getConnections().iterator().next();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Notifies the server of things like consuming food, etc...
     */
    public boolean sendUseItem(EntityPlayer playerIn, World worldIn, ItemStack itemStackIn)
    {
        if (this.currentGameType == WorldSettings.GameType.SPECTATOR)
        {
            return false;
        }
        else
        {
            this.syncCurrentPlayItem();
            if (ViaProtocol.newerThanOrEqualTo1_19()) {
                BlockStatePredictionHandler.isC08 = true;
                this.netClientHandler.addToSendQueue(new ServerBoundUseItem(EnumHand.MAIN_HAND));
            } else {
                this.netClientHandler.addToSendQueue(new C08PacketPlayerBlockPlacement(playerIn.inventory.getCurrentItem()));
            }
            int i = itemStackIn.stackSize;
            ItemStack itemstack = itemStackIn.useItemRightClick(worldIn, playerIn);

            if (itemstack != itemStackIn || itemstack != null && itemstack.stackSize != i)
            {
                playerIn.inventory.mainInventory[playerIn.inventory.currentItem] = itemstack;

                if (itemstack.stackSize == 0)
                {
                    playerIn.inventory.mainInventory[playerIn.inventory.currentItem] = null;
                }

                return true;
            }
            else
            {
                return false;
            }
        }
    }

    public EntityPlayerSP func_178892_a(World worldIn, StatFileWriter statWriter)
    {
        return new EntityPlayerSP(this.mc, worldIn, this.netClientHandler, statWriter);
    }

    /**
     * Attacks an entity
     */
    public void attackEntity(EntityPlayer playerIn, Entity targetEntity) {
        AttackEvent event = new AttackEvent(targetEntity);
        EventManager.call(event);

        if (event.isCancelled()) {
            return;
        }

        this.syncCurrentPlayItem();
        if (ViaProtocol.newerThanOrEqualTo1_19()) {
            this.netClientHandler.addToSendQueue(new ServerBoundInteractAttack(targetEntity));
        } else {
            this.netClientHandler.addToSendQueue(new C02PacketUseEntity(targetEntity, C02PacketUseEntity.Action.ATTACK));
        }

        if (this.currentGameType != WorldSettings.GameType.SPECTATOR) {
            playerIn.attackTargetEntityWithCurrentItem(targetEntity);
        }
    }

    /**
     * Send packet to server - player is interacting with another entity (left click)
     */
    public boolean interactWithEntitySendPacket(EntityPlayer playerIn, Entity targetEntity)
    {
        this.syncCurrentPlayItem();
        this.netClientHandler.addToSendQueue(new C02PacketUseEntity(targetEntity, C02PacketUseEntity.Action.INTERACT));
        return this.currentGameType != WorldSettings.GameType.SPECTATOR && playerIn.interactWith(targetEntity);
    }

    /**
     * Return true when the player rightclick on an entity
     *  
     * @param player The player's instance
     * @param entityIn The entity clicked
     * @param movingObject The object clicked
     */
    public boolean isPlayerRightClickingOnEntity(EntityPlayer player, Entity entityIn, MovingObjectPosition movingObject)
    {
        this.syncCurrentPlayItem();
        Vec3 vec3 = new Vec3(movingObject.hitVec.xCoord - entityIn.posX, movingObject.hitVec.yCoord - entityIn.posY, movingObject.hitVec.zCoord - entityIn.posZ);
        this.netClientHandler.addToSendQueue(new C02PacketUseEntity(entityIn, vec3));
        return this.currentGameType != WorldSettings.GameType.SPECTATOR && entityIn.interactAt(player, vec3);
    }

    /**
     * Handles slot clicks sends a packet to the server.
     */
    public ItemStack windowClick(int windowId, int slotId, int mouseButtonClicked, int mode, EntityPlayer playerIn)
    {
        WindowClickEvent event = new WindowClickEvent(windowId, slotId, mouseButtonClicked, mode);
        EventManager.call(event);

        if (event.isCancelled()) {
            return null;
        }

        short short1 = playerIn.openContainer.getNextTransactionID(playerIn.inventory);
        ItemStack itemstack = playerIn.openContainer.slotClick(slotId, mouseButtonClicked, mode, playerIn);

        ProtocolVersion targetVersion = ViaLoadingBase.getInstance().getTargetVersion();
        if (ViaProtocol.newerThanOrEqualTo1_19() && ViaProtocol.olderThanOrEqualTo1_20_5() && !(this.mc.currentScreen instanceof GuiContainer)) {
            try {
                UserConnection userConnection = Via.getManager().getConnectionManager().getConnections().iterator().next();
                Class<? extends Protocol> protocolClass = null;
                PacketWrapperImpl packetWrapper = null;

                if (targetVersion == ProtocolVersion.v1_21_7) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_21_6.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_21_7To1_21_6.class;
                } else if (targetVersion == ProtocolVersion.v1_21_6) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_21_6.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_21_6To1_21_5.class;
                } else if (targetVersion == ProtocolVersion.v1_21_5) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_21_5.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_21_5To1_21_4.class;
                } else if (targetVersion == ProtocolVersion.v1_21_4) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_21_4.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_21_4To1_21_2.class;
                } else if (targetVersion == ProtocolVersion.v1_21_2) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_21_2.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_21_2To1_21.class;
                } else if (targetVersion == ProtocolVersion.v1_21) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_20_5.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_21To1_20_5.class;
                } else if (targetVersion == ProtocolVersion.v1_20_5) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_20_5.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_20_5To1_20_3.class;
                } else if (targetVersion == ProtocolVersion.v1_20) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_19_4.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_20To1_19_4.class;
                } else if (targetVersion == ProtocolVersion.v1_19_4) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_19_4.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_19_4To1_19_3.class;
                } else if (targetVersion == ProtocolVersion.v1_19_3) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_19_3.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_19_3To1_19_1.class;
                } else if (targetVersion == ProtocolVersion.v1_19_1) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_19_1.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_19_1To1_19.class;
                } else if (targetVersion == ProtocolVersion.v1_19) {
                    packetWrapper = (PacketWrapperImpl) PacketWrapper.create(ServerboundPackets1_19.CONTAINER_CLICK, userConnection);
                    protocolClass = Protocol1_19To1_18_2.class;
                }

                int stateId = VLBViaDecodeHandler.stateId;
                ProtocolInfo protocolInfo = userConnection.getProtocolInfo();

                if (packetWrapper != null && protocolClass != null) {
                    packetWrapper.write(Types.BYTE, (byte) windowId);
                    packetWrapper.write(Types.VAR_INT, stateId);
                    packetWrapper.write(Types.SHORT, (short) slotId);
                    packetWrapper.write(Types.BYTE, (byte) mouseButtonClicked);
                    packetWrapper.write(Types.VAR_INT, mode);
                    packetWrapper.write(Types.VAR_INT, 0);
                    packetWrapper.write(Types.BYTE, (byte) 0);

                    if (protocolInfo.getPipeline().contains(protocolClass)) {
                        packetWrapper.sendToServer(protocolClass);
                    }
                } else {
                    this.netClientHandler.addToSendQueue(new C0EPacketClickWindow(windowId, slotId, mouseButtonClicked, mode, itemstack, short1));
                }
            } catch (Exception e) {
                e.printStackTrace();
                this.netClientHandler.addToSendQueue(new C0EPacketClickWindow(windowId, slotId, mouseButtonClicked, mode, itemstack, short1));
            }
        } else {
            this.netClientHandler.addToSendQueue(new C0EPacketClickWindow(windowId, slotId, mouseButtonClicked, mode, itemstack, short1));
        }

        return itemstack;
    }

    /**
     * GuiEnchantment uses this during multiplayer to tell PlayerControllerMP to send a packet indicating the
     * enchantment action the player has taken.
     *  
     * @param windowID The ID of the current window
     * @param button The button id (enchantment selected)
     */
    public void sendEnchantPacket(int windowID, int button)
    {
        this.netClientHandler.addToSendQueue(new C11PacketEnchantItem(windowID, button));
    }

    /**
     * Used in PlayerControllerMP to update the server with an ItemStack in a slot.
     */
    public void sendSlotPacket(ItemStack itemStackIn, int slotId)
    {
        if (this.currentGameType.isCreative())
        {
            this.netClientHandler.addToSendQueue(new C10PacketCreativeInventoryAction(slotId, itemStackIn));
        }
    }

    /**
     * Sends a Packet107 to the server to drop the item on the ground
     */
    public void sendPacketDropItem(ItemStack itemStackIn)
    {
        if (this.currentGameType.isCreative() && itemStackIn != null)
        {
            this.netClientHandler.addToSendQueue(new C10PacketCreativeInventoryAction(-1, itemStackIn));
        }
    }

    public void onStoppedUsingItem(EntityPlayer playerIn)
    {
        CancelUseEvent event = new CancelUseEvent();
        EventManager.call(event);

        if (event.isCancelled()) {
            return;
        }

        this.syncCurrentPlayItem();
        this.netClientHandler.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
        playerIn.stopUsingItem();
    }

    public boolean gameIsSurvivalOrAdventure()
    {
        return this.currentGameType.isSurvivalOrAdventure();
    }

    /**
     * Checks if the player is not creative, used for checking if it should break a block instantly
     */
    public boolean isNotCreative()
    {
        return !this.currentGameType.isCreative();
    }

    /**
     * returns true if player is in creative mode
     */
    public boolean isInCreativeMode()
    {
        return this.currentGameType.isCreative();
    }

    /**
     * true for hitting entities far away.
     */
    public boolean extendedReach()
    {
        return this.currentGameType.isCreative();
    }

    /**
     * Checks if the player is riding a horse, used to chose the GUI to open
     */
    public boolean isRidingHorse()
    {
        return this.mc.thePlayer.isRiding() && this.mc.thePlayer.ridingEntity instanceof EntityHorse;
    }

    public boolean isSpectatorMode()
    {
        return this.currentGameType == WorldSettings.GameType.SPECTATOR;
    }

    public WorldSettings.GameType getCurrentGameType()
    {
        return this.currentGameType;
    }

    /**
     * Return isHittingBlock
     */
    public boolean getIsHittingBlock()
    {
        return this.isHittingBlock;
    }

    public float getCurBlockDamageMP()
    {
        return this.curBlockDamageMP;
    }

    public void setCurBlockDamageMP(float curBlockDamageMPIn)
    {
        this.curBlockDamageMP = curBlockDamageMPIn;
    }

    public int getBlockHitDelay()
    {
        return this.blockHitDelay;
    }

    public void setBlockHitDelay(int blockHitDelayIn)
    {
        this.blockHitDelay = blockHitDelayIn;
    }

    public int getCurrentPlayerItem()
    {
        return this.currentPlayerItem;
    }

    public void setCurrentPlayerItem(int currentPlayerItemIn)
    {
        this.currentPlayerItem = currentPlayerItemIn;
    }
}
