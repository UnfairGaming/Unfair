package cn.unfair.util.via;

import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viabackwards.protocol.v1_20_2to1_20.Protocol1_20_2To1_20;
import com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import com.viaversion.viaversion.protocols.v1_20to1_20_2.packet.ServerboundPackets1_20_2;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_9;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.viamcp.ViaMCP;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemSword;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketSwapItemWithOffHand;
import net.minecraft.network.play.client.ServerBoundUseItem;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Vec3;

public final class ModernOffhandInteraction {

    private static boolean clientOffhandAction;

    private ModernOffhandInteraction() {
    }

    public static boolean hasOffhand(EntityPlayer player) {
        return isModernTarget() && player != null && getOffhand(player) != null;
    }

    public static ItemStack getOffhand(EntityPlayer player) {
        if (player == null || !(player.inventory instanceof ModernOffhandInventory)) {
            return null;
        }
        return player.inventory.viaforge$getOffhand();
    }

    public static boolean shouldUseItemAfterBlock(EntityPlayer player) {
        ItemStack stack = getOffhand(player);
        return stack != null && stack.getItemUseAction() != EnumAction.NONE;
    }

    public static boolean shouldMainHandUseTakePriority(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        // Buckets have no item-use animation, but their right-click must still
        // own the interaction instead of falling through to the offhand.
        if (stack.getItem() instanceof ItemBucket) {
            return true;
        }

        if (stack.getItem() instanceof ItemSword) {
            return false;
        }

        if (stack.getItemUseAction() != EnumAction.NONE) {
            return true;
        }

        String modelName = ViaBackwardsItemModels.getModelName(stack);
        return "shield".equals(modelName) || "crossbow".equals(modelName) || "trident".equals(modelName);
    }

    public static void beginRightClick() {
        clientOffhandAction = false;
    }

    public static boolean wasClientOffhandAction() {
        return clientOffhandAction;
    }

    public static boolean sendSwapItemWithOffhand(EntityPlayerSP player) {
        if (!isModernTarget()) {
            return false;
        }

        player.sendQueue.addToSendQueue(new CPacketSwapItemWithOffHand());
        ModernOffhandInventory inventory = player.inventory;
        ItemStack mainHand = player.inventory.getCurrentItem();
        ItemStack offhand = inventory.viaforge$getOffhand();
        player.inventory.mainInventory[player.inventory.currentItem] = offhand;
        inventory.viaforge$setOffhand(mainHand);
        return true;
    }

    public static boolean sendUseItem(EntityPlayerSP player) {
        if (!isModernTarget()) {
            return false;
        }

        ItemStack stack = getOffhand(player);
        UserConnection connection = getConnection();
        if (stack == null || connection == null) {
            return false;
        }

        player.sendQueue.addToSendQueue(new ServerBoundUseItem(EnumHand.OFF_HAND));
        clientOffhandAction = true;

        int previousSize = stack.stackSize;
        ItemStack result = stack.useItemRightClick(player.worldObj, player);
        if (result != stack || result == null || result.stackSize != previousSize) {
            player.inventory.viaforge$setOffhand(result != null && result.stackSize > 0 ? result : null);
        }

        ItemStack activeStack = getOffhand(player);
        if (activeStack != null && "shield".equals(ViaBackwardsItemModels.getModelName(activeStack))) {
            player.setItemInUse(activeStack, 72000);
        }

        return true;
    }

    public static boolean sendUseItemOnBlock(EntityPlayerSP player, BlockPos pos, EnumFacing face, Vec3 hitVec) {
        if (!isModernTarget()) {
            return false;
        }

        UserConnection connection = getConnection();
        if (connection == null || getOffhand(player) == null) {
            return false;
        }

        sendUseItemOnBlockPacket(connection, pos, face, hitVec);
        clientOffhandAction = true;
        if (!shouldUseItemAfterBlock(player)) {
            sendSwing(connection, player);
        }
        return true;
    }

    public static void sendInteract(EntityPlayer player, Entity target) {
        if (!isModernTarget()) {
            return;
        }

        UserConnection connection = getConnection();
        if (connection == null) {
            return;
        }

        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.INTERACT, connection);
        wrapper.write(Types.VAR_INT, target.getEntityId());
        wrapper.write(Types.VAR_INT, 0);
        wrapper.write(Types.VAR_INT, 1);
        wrapper.scheduleSendToServer(Protocol1_9To1_8.class);
        clientOffhandAction = true;
    }

    public static void sendInteractAt(EntityPlayer player, Entity target, Vec3 hit) {
        if (!isModernTarget()) {
            return;
        }

        UserConnection connection = getConnection();
        if (connection == null) {
            return;
        }

        PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.INTERACT, connection);
        wrapper.write(Types.VAR_INT, target.getEntityId());
        wrapper.write(Types.VAR_INT, 2);
        wrapper.write(Types.FLOAT, (float) hit.xCoord);
        wrapper.write(Types.FLOAT, (float) hit.yCoord);
        wrapper.write(Types.FLOAT, (float) hit.zCoord);
        wrapper.write(Types.VAR_INT, 1);
        wrapper.scheduleSendToServer(Protocol1_9To1_8.class);
        clientOffhandAction = true;
    }

    private static void sendUseItemOnBlockPacket(UserConnection connection, BlockPos pos, EnumFacing face, Vec3 hitVec) {
        ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
        float hitX = (float) (hitVec.xCoord - pos.getX());
        float hitY = (float) (hitVec.yCoord - pos.getY());
        float hitZ = (float) (hitVec.zCoord - pos.getZ());

        if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
            PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_20_5.USE_ITEM_ON, connection);
            writeModernUseItemOnBlock(wrapper, pos, face, hitX, hitY, hitZ, true);
            wrapper.sendToServer(Protocol1_20_5To1_20_3.class);
        } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
            PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_20_2.USE_ITEM_ON, connection);
            writeModernUseItemOnBlock(wrapper, pos, face, hitX, hitY, hitZ, true);
            wrapper.sendToServer(Protocol1_20_2To1_20.class);
        } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
            PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM_ON, connection);
            writeModernUseItemOnBlock(wrapper, pos, face, hitX, hitY, hitZ, true);
            wrapper.sendToServer(Protocol1_19To1_18_2.class);
        } else {
            PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_9.USE_ITEM_ON, connection);
            wrapper.write(Types.BLOCK_POSITION1_8, new BlockPosition(pos.getX(), pos.getY(), pos.getZ()));
            wrapper.write(Types.VAR_INT, face.ordinal());
            wrapper.write(Types.VAR_INT, 1);
            wrapper.write(Types.FLOAT, hitX);
            wrapper.write(Types.FLOAT, hitY);
            wrapper.write(Types.FLOAT, hitZ);
            wrapper.sendToServer(Protocol1_9To1_8.class);
        }
    }

    private static void writeModernUseItemOnBlock(PacketWrapper wrapper, BlockPos pos, EnumFacing face, float hitX, float hitY, float hitZ, boolean sequence) {
        wrapper.write(Types.VAR_INT, 1);
        wrapper.write(Types.BLOCK_POSITION1_14, new BlockPosition(pos.getX(), pos.getY(), pos.getZ()));
        wrapper.write(Types.VAR_INT, face.ordinal());
        wrapper.write(Types.FLOAT, hitX);
        wrapper.write(Types.FLOAT, hitY);
        wrapper.write(Types.FLOAT, hitZ);
        wrapper.write(Types.BOOLEAN, false);
        if (sequence) {
            wrapper.write(Types.VAR_INT, ViaVersionFix.sequence());
        }
    }

    private static void sendSwing(UserConnection connection, EntityPlayer player) {
        PacketWrapper swing = PacketWrapper.create(ServerboundPackets1_9.SWING, connection);
        swing.write(Types.VAR_INT, 1);
        swing.scheduleSendToServer(Protocol1_9To1_8.class);
        if (player instanceof ModernOffhandPlayer) {
            ((ModernOffhandPlayer) player).viaforge$swingOffhand();
        }
    }

    private static UserConnection getConnection() {
        if (ViaMCP.INSTANCE != null && ViaMCP.INSTANCE.user != null) {
            return ViaMCP.INSTANCE.user;
        }

        if (Via.getManager() == null || Via.getManager().getConnectionManager() == null) {
            return null;
        }

        try {
            return Via.getManager().getConnectionManager().getConnections().iterator().next();
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isModernTarget() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null
                && ViaProtocol.newerThanOrEqualTo1_9();
    }
}
