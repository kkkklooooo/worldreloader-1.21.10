package com.worldreloader.undo;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSupplier;
import net.minecraft.world.chunk.Chunk;

import java.util.List;

/**
 * Captures and restores the 3D biome grid of a single chunk prior to modification.
 */
public final class BiomeSnapshot {
    private final ChunkPos chunkPos;
    private final int minBiomeY;
    private final int biomeHeight;
    private final RegistryEntry<Biome>[] biomes;

    private BiomeSnapshot(ChunkPos chunkPos, int minBiomeY, int biomeHeight, RegistryEntry<Biome>[] biomes) {
        this.chunkPos = chunkPos;
        this.minBiomeY = minBiomeY;
        this.biomeHeight = biomeHeight;
        this.biomes = biomes;
    }

    @SuppressWarnings("unchecked")
    public static BiomeSnapshot capture(ServerWorld world, ChunkPos chunkPos) {
        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        int minBiomeY = BiomeCoords.fromBlock(world.getBottomY());
        int maxBiomeY = BiomeCoords.fromBlock(world.getTopY());
        int biomeHeight = Math.max(0, maxBiomeY - minBiomeY);
        int startBiomeX = BiomeCoords.fromChunk(chunkPos.x);
        int startBiomeZ = BiomeCoords.fromChunk(chunkPos.z);

        RegistryEntry<Biome>[] entries = new RegistryEntry[4 * biomeHeight * 4];
        for (int dy = 0; dy < biomeHeight; dy++) {
            int by = minBiomeY + dy;
            for (int dx = 0; dx < 4; dx++) {
                int bx = startBiomeX + dx;
                for (int dz = 0; dz < 4; dz++) {
                    int bz = startBiomeZ + dz;
                    entries[(dy * 4 + dx) * 4 + dz] = chunk.getBiomeForNoiseGen(bx, by, bz);
                }
            }
        }
        return new BiomeSnapshot(chunkPos, minBiomeY, biomeHeight, entries);
    }

    public ChunkPos getChunkPos() {
        return chunkPos;
    }

    public void restore(ServerWorld world) {
        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        int startBiomeX = BiomeCoords.fromChunk(chunkPos.x);
        int startBiomeZ = BiomeCoords.fromChunk(chunkPos.z);

        BiomeSupplier supplier = (bx, by, bz, noise) -> {
            int dx = bx - startBiomeX;
            int dy = by - minBiomeY;
            int dz = bz - startBiomeZ;
            if (dx >= 0 && dx < 4 && dy >= 0 && dy < biomeHeight && dz >= 0 && dz < 4) {
                RegistryEntry<Biome> saved = biomes[(dy * 4 + dx) * 4 + dz];
                if (saved != null) {
                    return saved;
                }
            }
            return chunk.getBiomeForNoiseGen(bx, by, bz);
        };

        chunk.populateBiomes(supplier, world.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
        chunk.setNeedsSaving(true);
        world.getChunkManager().threadedAnvilChunkStorage.sendChunkBiomePackets(List.of(chunk));
    }
}
