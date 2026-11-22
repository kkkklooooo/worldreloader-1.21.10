package com.worldreloader.undo;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records step-by-step terrain and biome changes during a forward transformation so they can be
 * replayed in reverse.
 */
public final class TransformationRecord {
    private final RegistryKey<World> dimensionKey;
    private final List<StepSnapshot> steps;
    private final LongSet affectedChunks;

    private TransformationRecord(RegistryKey<World> dimensionKey, List<StepSnapshot> steps, LongSet affectedChunks) {
        this.dimensionKey = dimensionKey;
        this.steps = steps;
        this.affectedChunks = affectedChunks;
    }

    public RegistryKey<World> getDimensionKey() {
        return dimensionKey;
    }

    public List<StepSnapshot> getSteps() {
        return steps;
    }

    public LongSet getAffectedChunks() {
        return affectedChunks;
    }

    public int getStepCount() {
        return steps.size();
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * Mutable per-task recorder accumulating snapshots one tick step at a time.
     */
    public static final class Recorder {
        private final RegistryKey<World> dimensionKey;
        private final List<StepSnapshot> steps = new ArrayList<>();
        private final LongSet affectedChunks = new LongOpenHashSet();
        private final LongSet recordedBiomeChunks = new LongOpenHashSet();

        private final Long2ObjectOpenHashMap<BlockState> currentStepBlocks = new Long2ObjectOpenHashMap<>();
        private Map<Long, NbtCompound> currentStepBlockEntities = null;
        private List<BiomeSnapshot> currentStepBiomes = null;

        public Recorder(RegistryKey<World> dimensionKey) {
            this.dimensionKey = dimensionKey;
        }

        public void beginStep() {
            currentStepBlocks.clear();
            currentStepBlockEntities = null;
            currentStepBiomes = null;
        }

        public void recordBlockChange(ServerWorld world, BlockPos pos) {
            long posLong = pos.asLong();
            if (currentStepBlocks.containsKey(posLong)) {
                return;
            }
            BlockState oldState = world.getBlockState(pos);
            currentStepBlocks.put(posLong, oldState);
            affectedChunks.add(ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4));

            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                if (currentStepBlockEntities == null) {
                    currentStepBlockEntities = new HashMap<>();
                }
                currentStepBlockEntities.put(posLong, blockEntity.createNbtWithIdentifyingData());
            }
        }

        public void recordBiomeChange(ServerWorld world, ChunkPos chunkPos) {
            long chunkLong = chunkPos.toLong();
            if (!recordedBiomeChunks.add(chunkLong)) {
                return;
            }
            if (currentStepBiomes == null) {
                currentStepBiomes = new ArrayList<>();
            }
            currentStepBiomes.add(BiomeSnapshot.capture(world, chunkPos));
            affectedChunks.add(chunkLong);
        }

        public void finishStep() {
            boolean hasBlocks = !currentStepBlocks.isEmpty();
            boolean hasBiomes = currentStepBiomes != null && !currentStepBiomes.isEmpty();
            if (!hasBlocks && !hasBiomes) {
                return;
            }

            long[] positions;
            BlockState[] states;
            if (hasBlocks) {
                List<Long2ObjectMap.Entry<BlockState>> entries = new ArrayList<>(currentStepBlocks.long2ObjectEntrySet());
                // Sort top-down so attached blocks (plants, snow, torches) are reverted before their support.
                entries.sort((a, b) -> Integer.compare(
                        BlockPos.unpackLongY(b.getLongKey()),
                        BlockPos.unpackLongY(a.getLongKey())));
                int count = entries.size();
                positions = new long[count];
                states = new BlockState[count];
                for (int i = 0; i < count; i++) {
                    Long2ObjectMap.Entry<BlockState> entry = entries.get(i);
                    positions[i] = entry.getLongKey();
                    states[i] = entry.getValue();
                }
            } else {
                positions = new long[0];
                states = new BlockState[0];
            }

            steps.add(new StepSnapshot(positions, states, currentStepBlockEntities, currentStepBiomes));

            currentStepBlocks.clear();
            currentStepBlockEntities = null;
            currentStepBiomes = null;
        }

        public TransformationRecord build() {
            return new TransformationRecord(dimensionKey, steps, affectedChunks);
        }
    }
}
