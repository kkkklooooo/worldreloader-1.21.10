package com.worldreloader.client;

import com.worldreloader.WorldReloader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;

/**
 * Tracks client-side Nether biome presence in non-Nether dimensions to apply thick Nether fog
 * and spawn floating spore/ash particles when the Nether biome itself has no ambient particles.
 */
public final class NetherAtmosphereHelper {
    private static final float FADE_STEP_PER_TICK = 0.08f;
    private static final int DEFAULT_NETHER_RED_FOG = 0x330808;

    private static float netherFogProgress = 0.0f;
    private static int lastNetherFogColor = DEFAULT_NETHER_RED_FOG;

    private NetherAtmosphereHelper() {
    }

    public static void update(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.player == null || WorldReloader.config == null || !WorldReloader.config.netherAtmosphere) {
            netherFogProgress = 0.0f;
            return;
        }

        if (world.getDimensionEffects().useThickFog(0, 0)) {
            netherFogProgress = 0.0f;
            return;
        }

        BlockPos playerPos = client.player.getBlockPos();
        RegistryEntry<Biome> biome = world.getBiome(playerPos);
        if (biome.isIn(BiomeTags.IS_NETHER)) {
            lastNetherFogColor = biome.value().getFogColor();
            netherFogProgress = Math.min(1.0f, netherFogProgress + FADE_STEP_PER_TICK);
            if (!client.isPaused() && biome.value().getEffects().getParticleConfig().isEmpty()) {
                spawnFallbackNetherParticles(world, playerPos);
            }
        } else {
            netherFogProgress = Math.max(0.0f, netherFogProgress - FADE_STEP_PER_TICK);
        }
    }

    private static void spawnFallbackNetherParticles(ClientWorld world, BlockPos center) {
        Random random = world.random;
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int i = 0; i < 8; i++) {
            int dx = random.nextInt(25) - 12;
            int dy = random.nextInt(13) - 4;
            int dz = random.nextInt(25) - 12;
            mutable.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
            if (world.getBlockState(mutable).isAir() && world.getBiome(mutable).isIn(BiomeTags.IS_NETHER)) {
                double px = mutable.getX() + random.nextDouble();
                double py = mutable.getY() + random.nextDouble();
                double pz = mutable.getZ() + random.nextDouble();
                if (i < 6) {
                    world.addParticle(ParticleTypes.CRIMSON_SPORE, px, py, pz, 0.0, 0.0, 0.0);
                } else {
                    world.addParticle(ParticleTypes.ASH, px, py, pz, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    public static float getNetherFogProgress() {
        return netherFogProgress;
    }

    public static int getFogColor() {
        return lastNetherFogColor;
    }
}
