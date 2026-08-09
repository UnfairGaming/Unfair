package net.minecraft.network;

import cn.unfair.Unfair;
import cn.unfair.event.EventManager;
import cn.unfair.event.types.EventType;
import cn.unfair.events.PacketEvent;
import cn.unfair.util.via.ViaVersionFix;
import cn.unfair.util.PacketUtil;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.viaversion.viabackwards.protocol.v1_19to1_18_2.Protocol1_19To1_18_2;
import com.viaversion.viabackwards.protocol.v1_20_2to1_20.Protocol1_20_2To1_20;
import com.viaversion.viabackwards.protocol.v1_20_5to1_20_3.Protocol1_20_5To1_20_3;
import com.viaversion.viabackwards.protocol.v1_20to1_19_4.Protocol1_20To1_19_4;
import com.viaversion.viabackwards.protocol.v1_21_2to1_21.Protocol1_21_2To1_21;
import com.viaversion.viarewind.protocol.v1_9to1_8.Protocol1_9To1_8;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.minecraft.BlockPosition;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.connection.UserConnectionImpl;
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl;
import com.viaversion.viaversion.protocols.v1_18_2to1_19.packet.ServerboundPackets1_19;
import com.viaversion.viaversion.protocols.v1_19_3to1_19_4.packet.ServerboundPackets1_19_4;
import com.viaversion.viaversion.protocols.v1_20_3to1_20_5.packet.ServerboundPackets1_20_5;
import com.viaversion.viaversion.protocols.v1_20to1_20_2.packet.ServerboundPackets1_20_2;
import com.viaversion.viaversion.protocols.v1_21to1_21_2.packet.ServerboundPackets1_21_2;
import com.viaversion.viaversion.protocols.v1_8to1_9.packet.ServerboundPackets1_9;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import de.florianmichael.vialoadingbase.netty.event.CompressionReorderEvent;
import de.florianmichael.viamcp.MCPVLBPipeline;
import de.florianmichael.viamcp.ViaMCP;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.CPacketConfirmTeleport;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.client.CPacketSwapItemWithOffHand;
import net.minecraft.network.play.client.ServerBoundInteractAttack;
import net.minecraft.network.play.client.ServerBoundPlayerAction;
import net.minecraft.network.play.client.ServerBoundPlayerCommand;
import net.minecraft.network.play.client.ServerBoundSwing;
import net.minecraft.network.play.client.ServerBoundUseItem;
import net.minecraft.util.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import javax.crypto.SecretKey;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static com.viaversion.viaversion.api.protocol.version.ProtocolVersion.v1_8;

public class NetworkManager extends SimpleChannelInboundHandler<Packet<?>> {
    private static final Logger logger = LogManager.getLogger();
    public static final Marker logMarkerNetwork = MarkerManager.getMarker("NETWORK");
    public static final Marker logMarkerPackets = MarkerManager.getMarker("NETWORK_PACKETS", logMarkerNetwork);
    public static final AttributeKey<EnumConnectionState> attrKeyConnectionState = AttributeKey.valueOf("protocol");
    public static final LazyLoadBase<NioEventLoopGroup> CLIENT_NIO_EVENTLOOP = new LazyLoadBase<>() {
        protected NioEventLoopGroup load() {
            return new NioEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Client IO #%d").setDaemon(true).build());
        }
    };
    public static final LazyLoadBase<EpollEventLoopGroup> CLIENT_EPOLL_EVENTLOOP = new LazyLoadBase<>() {
        protected EpollEventLoopGroup load() {
            return new EpollEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Epoll Client IO #%d").setDaemon(true).build());
        }
    };
    public static final LazyLoadBase<DefaultEventLoopGroup> CLIENT_LOCAL_EVENTLOOP = new LazyLoadBase<>() {
        protected DefaultEventLoopGroup load() {
            return new DefaultEventLoopGroup(0, (new ThreadFactoryBuilder()).setNameFormat("Netty Local Client IO #%d").setDaemon(true).build());
        }
    };
    private final EnumPacketDirection direction;
    private final Queue<NetworkManager.InboundHandlerTuplePacketListener> outboundPacketsQueue = Queues.newConcurrentLinkedQueue();
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /** The active channel */
    private Channel channel;

    /** The address of the remote party */
    private SocketAddress socketAddress;

    /** The INetHandler instance responsible for processing received packets */
    private INetHandler packetListener;

    /** A String indicating why the network has shutdown. */
    private IChatComponent terminationReason;
    private boolean isEncrypted;
    private boolean disconnected;

    public NetworkManager(EnumPacketDirection packetDirection)
    {
        this.direction = packetDirection;
    }

    public void channelActive(ChannelHandlerContext p_channelActive_1_) throws Exception {
        super.channelActive(p_channelActive_1_);
        this.channel = p_channelActive_1_.channel();
        this.socketAddress = this.channel.remoteAddress();

        try {
            this.setConnectionState(EnumConnectionState.HANDSHAKING);
        } catch (Throwable throwable) {
            logger.fatal(throwable);
        }
    }

    /**
     * Sets the new connection state and registers which packets this channel may send and receive
     */
    public void setConnectionState(EnumConnectionState newState) {
        this.channel.attr(attrKeyConnectionState).set(newState);
        this.channel.config().setAutoRead(true);
        // logger.debug("Enabled auto read");
    }

    public void channelInactive(ChannelHandlerContext p_channelInactive_1_) throws Exception {
        this.closeChannel(new ChatComponentTranslation("disconnect.endOfStream"));
    }

    public void exceptionCaught(ChannelHandlerContext p_exceptionCaught_1_, Throwable p_exceptionCaught_2_) throws Exception {
        ChatComponentTranslation chatcomponenttranslation;

        if (p_exceptionCaught_2_ instanceof TimeoutException) {
            chatcomponenttranslation = new ChatComponentTranslation("disconnect.timeout");
        } else {
            chatcomponenttranslation = new ChatComponentTranslation("disconnect.genericReason", "Internal Exception: " + p_exceptionCaught_2_);
        }

        this.closeChannel(chatcomponenttranslation);
    }

    protected void channelRead0(ChannelHandlerContext p_channelRead0_1_, Packet<?> packet) {
        if (packet != null && PacketUtil.skipReceiveEvent.remove(packet)) {
            return;
        }

        if (!packet.getClass().getName().startsWith("net.minecraft.network.play.client")) {
            if (Unfair.delayManager != null && Unfair.delayManager.shouldDelay(packet)) {
                return;
            }

            PacketEvent event = new PacketEvent(EventType.RECEIVE, packet);
            EventManager.call(event);

            if (event.isCancelled()) {
                return;
            }
        }

        if (this.channel.isOpen()) {
            try {
                processPacket(packet, this.packetListener);
            } catch (ThreadQuickExitException e) {
                // e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends INetHandler> void processPacket(Packet<?> packet, INetHandler handler) {
        ((Packet<T>) packet).processPacket((T) handler);
    }

    /**
     * Sets the NetHandler for this NetworkManager, no checks are made if this handler is suitable for the particular
     * connection state (protocol)
     */
    public void setNetHandler(INetHandler handler) {
        Validate.notNull(handler, "packetListener");
        // logger.debug("Set listener of {} to {}", this, handler);
        this.packetListener = handler;
    }

    public void sendPacket(Packet<?> packetIn) {
        if (this.handleNewPackets(packetIn)) {
            return;
        }

        if (this.shouldCancelSendPacket(packetIn)) {
            return;
        }

        if (this.isChannelOpen()) {
            this.flushOutboundQueue();
            this.dispatchPacket(packetIn, null);
        } else {
            this.readWriteLock.writeLock().lock();

            try {
                this.outboundPacketsQueue.add(new NetworkManager.InboundHandlerTuplePacketListener(packetIn));
            } finally {
                this.readWriteLock.writeLock().unlock();
            }
        }

        this.callSendPostEvent(packetIn);
    }

    @SafeVarargs
    public final void sendPacket(Packet<?> packetIn, GenericFutureListener<? extends Future<? super Void>> listener, GenericFutureListener<? extends Future<? super Void>>... listeners) {
        if (this.handleNewPackets(packetIn)) {
            return;
        }

        if (this.shouldCancelSendPacket(packetIn)) {
            return;
        }

        if (this.isChannelOpen()) {
            this.flushOutboundQueue();
            this.dispatchPacket(packetIn, ArrayUtils.add(listeners, 0, listener));
        } else {
            this.readWriteLock.writeLock().lock();

            try {
                this.outboundPacketsQueue.add(new NetworkManager.InboundHandlerTuplePacketListener(packetIn, ArrayUtils.add(listeners, 0, listener)));
            } finally {
                this.readWriteLock.writeLock().unlock();
            }
        }

        this.callSendPostEvent(packetIn);
    }

    private boolean shouldCancelSendPacket(Packet<?> packet) {
        if (packet.getClass().getName().startsWith("net.minecraft.network.play.server") || this.isRespawnPacket(packet)) {
            return false;
        }

        if (PacketUtil.skipSendEvent.remove(packet)) {
            PacketUtil.skipSendPostEvent.add(packet);
            return false;
        }

        PacketEvent event = new PacketEvent(EventType.SEND, packet);
        EventManager.call(event);

        if (event.isCancelled()) {
            return true;
        }

        if (Unfair.playerStateManager != null && Unfair.blinkManager != null && Unfair.lagManager != null && !Unfair.lagManager.isFlushing()) {
            Unfair.playerStateManager.handlePacket(packet);

            if (Unfair.blinkManager.isBlinking() && Unfair.blinkManager.offerPacket(packet)) {
                return true;
            }

            return Unfair.lagManager.handlePacket(packet);
        }

        return false;
    }

    private boolean isRespawnPacket(Packet<?> packet) {
        return packet instanceof C16PacketClientStatus
                && ((C16PacketClientStatus) packet).getStatus() == C16PacketClientStatus.EnumState.PERFORM_RESPAWN;
    }

    /**
     * Will commit the packet to the channel. If the current thread 'owns' the channel it will write and flush the
     * packet, otherwise it will add a task for the channel eventloop thread to do that.
     */
    private void dispatchPacket(Packet<?> inPacket, GenericFutureListener <? extends Future <? super Void >> [] futureListeners) {
        EnumConnectionState enumconnectionstate = EnumConnectionState.getFromPacket(inPacket);
        EnumConnectionState enumconnectionstate1 = this.channel.attr(attrKeyConnectionState).get();

        if (enumconnectionstate1 != enumconnectionstate) {
            // logger.debug("Disabled auto read");
            this.channel.config().setAutoRead(false);
        }

        if (this.channel.eventLoop().inEventLoop()) {
            if (enumconnectionstate != enumconnectionstate1) {
                this.setConnectionState(enumconnectionstate);
            }

            ChannelFuture channelfuture = this.channel.writeAndFlush(inPacket);

            if (futureListeners != null) {
                channelfuture.addListeners(futureListeners);
            }

            channelfuture.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        } else {
            this.channel.eventLoop().execute(() -> {
                if (enumconnectionstate != enumconnectionstate1) {
                    NetworkManager.this.setConnectionState(enumconnectionstate);
                }

                ChannelFuture channelfuture1 = NetworkManager.this.channel.writeAndFlush(inPacket);

                if (futureListeners != null) {
                    channelfuture1.addListeners(futureListeners);
                }

                channelfuture1.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            });
        }
    }

    private void callSendPostEvent(Packet<?> packet) {
        if (PacketUtil.skipSendPostEvent.remove(packet)) {
            return;
        }

        EventManager.call(new PacketEvent(EventType.POST, packet));
    }

    /**
     * Will iterate through the outboundPacketQueue and dispatch all Packets
     */
    private void flushOutboundQueue() {
        if (this.channel != null && this.channel.isOpen()) {
            this.readWriteLock.readLock().lock();

            try {
                while (!this.outboundPacketsQueue.isEmpty()) {
                    NetworkManager.InboundHandlerTuplePacketListener networkmanager$inboundhandlertuplepacketlistener = this.outboundPacketsQueue.poll();
                    this.dispatchPacket(networkmanager$inboundhandlertuplepacketlistener.packet, networkmanager$inboundhandlertuplepacketlistener.futureListeners);
                }
            } finally {
                this.readWriteLock.readLock().unlock();
            }
        }
    }

    /**
     * Checks timeouts and processes all packets received
     */
    public void processReceivedPackets() {
        this.flushOutboundQueue();

        if (this.packetListener instanceof ITickable) {
            ((ITickable)this.packetListener).update();
        }

        this.channel.flush();
    }

    /**
     * Returns the socket address of the remote side. Server-only.
     */
    public SocketAddress getRemoteAddress()
    {
        return this.socketAddress;
    }

    /**
     * Closes the channel, the parameter can be used for an exit message (not certain how it gets sent)
     */
    public void closeChannel(IChatComponent message) {
        if (this.channel.isOpen()) {
            this.channel.close().awaitUninterruptibly();
            this.terminationReason = message;
        }
    }

    /**
     * True if this NetworkManager uses a memory connection (single player game). False may imply both an active TCP
     * connection or simply no active connection at all
     */
    public boolean isLocalChannel() {
        return this.channel instanceof LocalChannel || this.channel instanceof LocalServerChannel;
    }

    /**
     * Create a new NetworkManager from the server host and connect it to the server
     *  
     * @param address The address of the server
     * @param serverPort The server port
     * @param useNativeTransport True if the client use the native transport system
     */
    public static NetworkManager createNetworkManagerAndConnect(InetAddress address, int serverPort, boolean useNativeTransport) {
        final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        Class <? extends SocketChannel > oclass;
        LazyLoadBase <? extends EventLoopGroup > lazyloadbase;

        if (Epoll.isAvailable() && useNativeTransport) {
            oclass = EpollSocketChannel.class;
            lazyloadbase = CLIENT_EPOLL_EVENTLOOP;
        } else {
            oclass = NioSocketChannel.class;
            lazyloadbase = CLIENT_NIO_EVENTLOOP;
        }

        (new Bootstrap()).group(lazyloadbase.getValue()).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel p_initChannel_1_) throws Exception
            {
                try {
                    p_initChannel_1_.config().setOption(ChannelOption.TCP_NODELAY, Boolean.valueOf(true));
                }
                catch (ChannelException var3) {
                }

                p_initChannel_1_.pipeline().addLast("timeout", new ReadTimeoutHandler(30)).addLast("splitter", new MessageDeserializer2()).addLast("decoder", new MessageDeserializer(EnumPacketDirection.CLIENTBOUND)).addLast("prepender", new MessageSerializer2()).addLast("encoder", new MessageSerializer(EnumPacketDirection.SERVERBOUND)).addLast("packet_handler", networkmanager);

                if (p_initChannel_1_ instanceof SocketChannel && ViaLoadingBase.getInstance().getTargetVersion().getVersion() != ViaMCP.NATIVE_VERSION) {
                    UserConnection user = new UserConnectionImpl(p_initChannel_1_, true);
                    new ProtocolPipelineImpl(user);
                    p_initChannel_1_.pipeline().addLast(new MCPVLBPipeline(user));
                }
            }
        }).channel(oclass).connect(address, serverPort).syncUninterruptibly();
        return networkmanager;
    }

    /**
     * Prepares a clientside NetworkManager: establishes a connection to the socket supplied and configures the channel
     * pipeline. Returns the newly created instance.
     */
    public static NetworkManager provideLocalClient(SocketAddress address) {
        final NetworkManager networkmanager = new NetworkManager(EnumPacketDirection.CLIENTBOUND);
        (new Bootstrap()).group(CLIENT_LOCAL_EVENTLOOP.getValue()).handler(new ChannelInitializer<Channel>() {
            protected void initChannel(Channel p_initChannel_1_) throws Exception {
                p_initChannel_1_.pipeline().addLast("packet_handler", networkmanager);
            }
        }).channel(LocalChannel.class).connect(address).syncUninterruptibly();
        return networkmanager;
    }

    private boolean handleNewPackets(Packet<?> packet) {
        boolean isHighVersion = ViaLoadingBase.getInstance().getTargetVersion().getVersion() > v1_8.getVersion();

        if (packet instanceof ViaPacket) {
            if (isHighVersion) {
                Iterator<UserConnection> iterator = Via.getManager().getConnectionManager().getConnections().iterator();

                if (iterator.hasNext()) {
                    UserConnection connection = iterator.next();

                    try {
                        if (packet instanceof CPacketPlayerTryUseItem useItemPacket) {
                            PacketWrapper useItem = PacketWrapper.create(ServerboundPackets1_9.USE_ITEM, null, connection);
                            useItem.write(Types.VAR_INT, useItemPacket.getHand());
                            useItem.sendToServer(Protocol1_9To1_8.class);
                        } else if (packet instanceof CPacketSwapItemWithOffHand) {
                            ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
                            if (target.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
                                PacketWrapper swap = PacketWrapper.create(ServerboundPackets1_21_2.PLAYER_ACTION, null, connection);
                                swap.write(Types.VAR_INT, 6);
                                swap.write(Types.BLOCK_POSITION1_14, new BlockPosition(0, 0, 0));
                                swap.write(Types.VAR_INT, 0);
                                swap.write(Types.VAR_INT, 0);
                                swap.sendToServer(Protocol1_21_2To1_21.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
                                PacketWrapper swap = PacketWrapper.create(ServerboundPackets1_20_5.PLAYER_ACTION, null, connection);
                                swap.write(Types.VAR_INT, 6);
                                swap.write(Types.BLOCK_POSITION1_14, new BlockPosition(0, 0, 0));
                                swap.write(Types.VAR_INT, 0);
                                swap.write(Types.VAR_INT, 0);
                                swap.sendToServer(Protocol1_20_5To1_20_3.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
                                PacketWrapper swap = PacketWrapper.create(ServerboundPackets1_20_2.PLAYER_ACTION, null, connection);
                                swap.write(Types.VAR_INT, 6);
                                swap.write(Types.BLOCK_POSITION1_14, new BlockPosition(0, 0, 0));
                                swap.write(Types.VAR_INT, 0);
                                swap.write(Types.VAR_INT, 0);
                                swap.sendToServer(Protocol1_20_2To1_20.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_19_4)) {
                                PacketWrapper swap = PacketWrapper.create(ServerboundPackets1_19_4.PLAYER_ACTION, null, connection);
                                swap.write(Types.VAR_INT, 6);
                                swap.write(Types.BLOCK_POSITION1_14, new BlockPosition(0, 0, 0));
                                swap.write(Types.VAR_INT, 0);
                                swap.write(Types.VAR_INT, 0);
                                swap.sendToServer(Protocol1_20To1_19_4.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
                                PacketWrapper swap = PacketWrapper.create(ServerboundPackets1_19.PLAYER_ACTION, null, connection);
                                swap.write(Types.VAR_INT, 6);
                                swap.write(Types.BLOCK_POSITION1_14, new BlockPosition(0, 0, 0));
                                swap.write(Types.VAR_INT, 0);
                                swap.write(Types.VAR_INT, 0);
                                swap.sendToServer(Protocol1_19To1_18_2.class);
                            } else {
                                PacketWrapper swap = PacketWrapper.create(ServerboundPackets1_9.PLAYER_ACTION, null, connection);
                                swap.write(Types.VAR_INT, 6);
                                swap.write(Types.BLOCK_POSITION1_8, new BlockPosition(0, 0, 0));
                                swap.write(Types.BYTE, (byte) 0);
                                swap.sendToServer(Protocol1_9To1_8.class);
                            }
                        } else if (packet instanceof CPacketConfirmTeleport confirmTeleportPacket) {
                            Collection<UserConnection> connections = Via.getManager().getConnectionManager().getConnections();
                            if (!connections.isEmpty()) {
                                PacketWrapper packetWrapper = PacketWrapper.create(ServerboundPackets1_9.ACCEPT_TELEPORTATION, connections.iterator().next());
                                packetWrapper.write(Types.VAR_INT, confirmTeleportPacket.getTeleportId());
                                packetWrapper.sendToServer(Protocol1_9To1_8.class);
                            } else {
                                logger.warn("No ViaVersion connections available when handling player position look packet");
                            }
                        } else if (packet instanceof ServerBoundUseItem useItem) {
                            ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
                            if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
                                PacketWrapper use = PacketWrapper.create(ServerboundPackets1_20_5.USE_ITEM, null, connection);
                                use.write(Types.VAR_INT, useItem.getHand().ordinal());
                                use.write(Types.VAR_INT, ViaVersionFix.sequence());
                                use.sendToServer(Protocol1_20_5To1_20_3.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
                                PacketWrapper use = PacketWrapper.create(ServerboundPackets1_20_2.USE_ITEM, null, connection);
                                use.write(Types.VAR_INT, useItem.getHand().ordinal());
                                use.write(Types.VAR_INT, ViaVersionFix.sequence());
                                use.sendToServer(Protocol1_20_2To1_20.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
                                PacketWrapper use = PacketWrapper.create(ServerboundPackets1_19.USE_ITEM, null, connection);
                                use.write(Types.VAR_INT, useItem.getHand().ordinal());
                                use.write(Types.VAR_INT, ViaVersionFix.sequence());
                                use.sendToServer(Protocol1_19To1_18_2.class);
                            } else {
                                PacketWrapper use = PacketWrapper.create(ServerboundPackets1_9.USE_ITEM, null, connection);
                                use.write(Types.VAR_INT, useItem.getHand().ordinal());
                                use.sendToServer(Protocol1_9To1_8.class);
                            }
                        } else if (packet instanceof ServerBoundInteractAttack attack) {
                            ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
                            if (target.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
                                PacketWrapper interact = PacketWrapper.create(ServerboundPackets1_21_2.INTERACT, null, connection);
                                interact.write(Types.VAR_INT, attack.getEntityId());
                                interact.write(Types.VAR_INT, 1);
                                interact.write(Types.BOOLEAN, false);
                                interact.sendToServer(Protocol1_21_2To1_21.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
                                PacketWrapper interact = PacketWrapper.create(ServerboundPackets1_20_5.INTERACT, null, connection);
                                interact.write(Types.VAR_INT, attack.getEntityId());
                                interact.write(Types.VAR_INT, 1);
                                interact.write(Types.BOOLEAN, false);
                                interact.sendToServer(Protocol1_20_5To1_20_3.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
                                PacketWrapper interact = PacketWrapper.create(ServerboundPackets1_20_2.INTERACT, null, connection);
                                interact.write(Types.VAR_INT, attack.getEntityId());
                                interact.write(Types.VAR_INT, 1);
                                interact.write(Types.BOOLEAN, false);
                                interact.sendToServer(Protocol1_20_2To1_20.class);
                            } else {
                                PacketWrapper interact = PacketWrapper.create(ServerboundPackets1_19.INTERACT, null, connection);
                                interact.write(Types.VAR_INT, attack.getEntityId());
                                interact.write(Types.VAR_INT, 1);
                                interact.write(Types.BOOLEAN, false);
                                interact.sendToServer(Protocol1_19To1_18_2.class);
                            }
                        } else if (packet instanceof ServerBoundSwing swing) {
                            ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
                            if (target.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
                                PacketWrapper swingPacket = PacketWrapper.create(ServerboundPackets1_21_2.SWING, null, connection);
                                swingPacket.write(Types.VAR_INT, swing.getHand().ordinal());
                                swingPacket.sendToServer(Protocol1_21_2To1_21.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
                                PacketWrapper swingPacket = PacketWrapper.create(ServerboundPackets1_20_5.SWING, null, connection);
                                swingPacket.write(Types.VAR_INT, swing.getHand().ordinal());
                                swingPacket.sendToServer(Protocol1_20_5To1_20_3.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
                                PacketWrapper swingPacket = PacketWrapper.create(ServerboundPackets1_20_2.SWING, null, connection);
                                swingPacket.write(Types.VAR_INT, swing.getHand().ordinal());
                                swingPacket.sendToServer(Protocol1_20_2To1_20.class);
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_19)) {
                                PacketWrapper swingPacket = PacketWrapper.create(ServerboundPackets1_19.SWING, null, connection);
                                swingPacket.write(Types.VAR_INT, swing.getHand().ordinal());
                                swingPacket.sendToServer(Protocol1_19To1_18_2.class);
                            } else {
                                PacketWrapper swingPacket = PacketWrapper.create(ServerboundPackets1_9.SWING, null, connection);
                                swingPacket.write(Types.VAR_INT, swing.getHand().ordinal());
                                swingPacket.sendToServer(Protocol1_9To1_8.class);
                            }
                        } else if (packet instanceof ServerBoundPlayerAction action) {
                            PacketWrapper packetWrapper = PacketWrapper.create(ServerboundPackets1_19.PLAYER_ACTION, connection);
                            packetWrapper.write(Types.VAR_INT, action.getAction().ordinal());
                            packetWrapper.write(Types.BLOCK_POSITION1_14, new BlockPosition(action.getPos().getX(), action.getPos().getY(), action.getPos().getZ()));
                            packetWrapper.write(Types.BYTE, (byte) action.getFacing().ordinal());
                            packetWrapper.write(Types.VAR_INT, action.getAction() == C07PacketPlayerDigging.Action.ABORT_DESTROY_BLOCK ? 0 : ViaVersionFix.sequence());
                            packetWrapper.sendToServer(Protocol1_19To1_18_2.class);
                        } else if (packet instanceof ServerBoundPlayerCommand command) {
                            ProtocolVersion target = ViaLoadingBase.getInstance().getTargetVersion();
                            if (target.newerThanOrEqualTo(ProtocolVersion.v1_21_2)) {
                                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_21_2.PLAYER_COMMAND, null, connection);
                                wrapper.write(Types.VAR_INT, command.getId());
                                wrapper.write(Types.VAR_INT, command.getAction().ordinal());
                                wrapper.write(Types.VAR_INT, command.getData());
                                wrapper.sendToServer(Protocol1_21_2To1_21.class);
                                return true;
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) {
                                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_20_5.PLAYER_COMMAND, null, connection);
                                wrapper.write(Types.VAR_INT, command.getId());
                                wrapper.write(Types.VAR_INT, command.getAction().ordinal());
                                wrapper.write(Types.VAR_INT, command.getData());
                                wrapper.sendToServer(Protocol1_20_5To1_20_3.class);
                                return true;
                            } else if (target.newerThanOrEqualTo(ProtocolVersion.v1_20_2)) {
                                PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_20_2.PLAYER_COMMAND, null, connection);
                                wrapper.write(Types.VAR_INT, command.getId());
                                wrapper.write(Types.VAR_INT, command.getAction().ordinal());
                                wrapper.write(Types.VAR_INT, command.getData());
                                wrapper.sendToServer(Protocol1_20_2To1_20.class);
                                return true;
                            }
                            PacketWrapper wrapper = PacketWrapper.create(ServerboundPackets1_19.PLAYER_COMMAND, connection);
                            wrapper.write(Types.VAR_INT, command.getId());
                            wrapper.write(Types.VAR_INT, command.getAction().ordinal());
                            wrapper.write(Types.VAR_INT, command.getData());
                            wrapper.sendToServer(Protocol1_19To1_18_2.class);
                        }
                    } catch (Exception exception) {
                        logger.error("Failed to send ViaVersion packet", exception);
                    }
                }
            }

            return true;
        }

        return false;
    }

    /**
     * Adds an encoder+decoder to the channel pipeline. The parameter is the secret key used for encrypted communication
     */
    public void enableEncryption(SecretKey key) {
        this.isEncrypted = true;
        this.channel.pipeline().addBefore("splitter", "decrypt", new NettyEncryptingDecoder(CryptManager.createNetCipherInstance(2, key)));
        this.channel.pipeline().addBefore("prepender", "encrypt", new NettyEncryptingEncoder(CryptManager.createNetCipherInstance(1, key)));
    }

    public boolean getIsencrypted()
    {
        return this.isEncrypted;
    }

    /**
     * Returns true if this NetworkManager has an active channel, false otherwise
     */
    public boolean isChannelOpen()
    {
        return this.channel != null && this.channel.isOpen();
    }

    public boolean hasNoChannel()
    {
        return this.channel == null;
    }

    /**
     * Gets the current handler for processing packets
     */
    public INetHandler getNetHandler()
    {
        return this.packetListener;
    }

    /**
     * If this channel is closed, returns the exit message, null otherwise.
     */
    public IChatComponent getExitMessage()
    {
        return this.terminationReason;
    }

    /**
     * Switches the channel to manual reading modus
     */
    public void disableAutoRead()
    {
        this.channel.config().setAutoRead(false);
    }

    public void setCompressionTreshold(int treshold) {
        if (treshold >= 0) {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder) {
                ((NettyCompressionDecoder)this.channel.pipeline().get("decompress")).setCompressionTreshold(treshold);
            } else {
                this.channel.pipeline().addBefore("decoder", "decompress", new NettyCompressionDecoder(treshold));
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder) {
                ((NettyCompressionEncoder)this.channel.pipeline().get("decompress")).setCompressionTreshold(treshold);
            } else {
                this.channel.pipeline().addBefore("encoder", "compress", new NettyCompressionEncoder(treshold));
            }
        } else {
            if (this.channel.pipeline().get("decompress") instanceof NettyCompressionDecoder)
            {
                this.channel.pipeline().remove("decompress");
            }

            if (this.channel.pipeline().get("compress") instanceof NettyCompressionEncoder)
            {
                this.channel.pipeline().remove("compress");
            }
        }

        this.channel.pipeline().fireUserEventTriggered(new CompressionReorderEvent());
    }

    public void checkDisconnected() {
        if (this.channel != null && !this.channel.isOpen()) {
            if (!this.disconnected) {
                this.disconnected = true;

                if (this.getExitMessage() != null) {
                    this.getNetHandler().onDisconnect(this.getExitMessage());
                } else if (this.getNetHandler() != null) {
                    this.getNetHandler().onDisconnect(new ChatComponentText("Disconnected"));
                }
            } else {
                logger.warn("handleDisconnection() called twice");
            }
        }
    }

    static class InboundHandlerTuplePacketListener {
        private final Packet<?> packet;
        private final GenericFutureListener <? extends Future <? super Void >> [] futureListeners;

        InboundHandlerTuplePacketListener(Packet<?> inPacket) {
            this.packet = inPacket;
            this.futureListeners = null;
        }

        @SafeVarargs
        InboundHandlerTuplePacketListener(Packet<?> inPacket, GenericFutureListener <? extends Future <? super Void >> ... inFutureListeners) {
            this.packet = inPacket;
            this.futureListeners = inFutureListeners;
        }
    }
}
