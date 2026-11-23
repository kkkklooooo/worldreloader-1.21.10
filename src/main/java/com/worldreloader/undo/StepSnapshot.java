package com.worldreloader.undo;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of all block, block-entity, and biome changes made in a single tick step.
 */
public final class StepSnapshot {
    private final long[] blockPositions;
    private final BlockState[] previousStates;
    private final Map<Long, NbtCompound> blockEntityNbts;
    private final List<BiomeSnapshot> biomeSnapshots;

    public StepSnapshot(long[] blockPositions,
                        BlockState[] previousStates,
                        Map<Long, NbtCompound> blockEntityNbts,
                        List<BiomeSnapshot> biomeSnapshots) {
        this.blockPositions = blockPositions;
        this.previousStates = previousStates;
        this.blockEntityNbts = blockEntityNbts;
        this.biomeSnapshots = biomeSnapshots;
    }

    public void restore(ServerWorld world) {
        for (int i = 0; i < blockPositions.length; i++) {
            long posLong = blockPositions[i];
            BlockPos pos = BlockPos.fromLong(posLong);
            BlockState prevState = previousStates[i];
            NbtCompound nbt = blockEntityNbts != null ? blockEntityNbts.get(posLong) : null;

            if (!world.getBlockState(pos).equals(prevState) || nbt != null) {
                world.setBlockState(pos, prevState, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                if (nbt != null) {
                    BlockEntity be = world.getBlockEntity(pos);
                    if (be != null) {
                        be.readNbt(nbt);
                        be.markDirty();
                    }
                }
            }
        }

        if (biomeSnapshots != null) {
            for (BiomeSnapshot snapshot : biomeSnapshots) {
                snapshot.restore(world);
            }
        }
    }
}
