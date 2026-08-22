package cn.unfair.util.via;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Vec3;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class BlockStatePredictionHandler implements AutoCloseable {
    public static boolean isC08;
    private final Long2ObjectOpenHashMap<ServerVerifiedState> serverVerifiedStates = new Long2ObjectOpenHashMap<>();
    @Setter
    public int currentSequence;
    private boolean isPredicting;

    public void retainKnownServerState(BlockPos pos, IBlockState state, EntityPlayerSP self) {
        this.serverVerifiedStates
                .compute(
                        pos.toLong(),
                        (p_420860_, p_420861_) -> p_420861_ != null
                                ? p_420861_.setSequence(this.currentSequence)
                                : new ServerVerifiedState(this.currentSequence, state, self.getPositionVector())
                );
    }

    public boolean updateKnownServerState(BlockPos p_233865_, IBlockState p_233866_) {
        ServerVerifiedState blockstatepredictionhandler$serververifiedstate = this.serverVerifiedStates.get(p_233865_.toLong());
        if (blockstatepredictionhandler$serververifiedstate == null) {
            return false;
        } else {
            blockstatepredictionhandler$serververifiedstate.setBlockState(p_233866_);
            return true;
        }
    }

    public void endPredictionsUpTo(int currentSequence, WorldClient world) {
        Map<BlockPos, ServerVerifiedState> toSync = new HashMap<>();

        ObjectIterator<Long2ObjectMap.Entry<ServerVerifiedState>> iterator =
                this.serverVerifiedStates.long2ObjectEntrySet().iterator();

        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<ServerVerifiedState> entry = iterator.next();
            ServerVerifiedState state = entry.getValue();

            if (state.sequence <= currentSequence) {
                BlockPos pos = BlockPos.fromLong(entry.getLongKey());

                ServerVerifiedState prev = toSync.get(pos);
                if (prev == null || state.sequence > prev.sequence) {
                    toSync.put(pos, state);
                }
                iterator.remove();
            }
        }

        Minecraft.getMinecraft().addScheduledTask(() -> {
            for (Map.Entry<BlockPos, ServerVerifiedState> e : toSync.entrySet()) {
                ServerVerifiedState state = e.getValue();
                world.syncBlockState(e.getKey(), state.blockState, state.playerPos);
            }
        });
        if (isC08) {
            this.isPredicting = false;
        }
    }

    public BlockStatePredictionHandler startPredicting() {
        this.currentSequence++;
        this.isPredicting = true;
        return this;
    }

    public BlockStatePredictionHandler worldLoad() {
        this.currentSequence = 0;
        this.isPredicting = false;
        return this;
    }

    @Override
    public void close() {
        if (isC08) return;
        this.isPredicting = false;
    }

    static class ServerVerifiedState {
        final Vec3 playerPos;
        int sequence;
        IBlockState blockState;

        ServerVerifiedState(int sequence, IBlockState state, Vec3 pos) {
            this.sequence = sequence;
            this.blockState = state;
            this.playerPos = pos;
        }

        ServerVerifiedState setSequence(int sequenceId) {
            this.sequence = sequenceId;
            return this;
        }

        void setBlockState(IBlockState state) {
            this.blockState = state;
        }
    }
}
