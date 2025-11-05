package com.worldreloader;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

public class SurfaceTransformationTask extends BaseTransformationTask {
    private final int DESTROY_DEPTH;
    private final int DESTROY_HEIGHT;
    private final int COPY_DEPTH;
    private final int COPY_HEIGHT;

    public SurfaceTransformationTask(ServerWorld world, BlockPos center, BlockPos referenceCenter,
                                     net.minecraft.entity.player.PlayerEntity player) {
        super(world, center, referenceCenter, player,
                WorldReloader.config.maxRadius,
                WorldReloader.config.totalSteps,
                WorldReloader.config.itemCleanupInterval);
        this.DESTROY_DEPTH = WorldReloader.config.DESTROY_DEPTH;
        this.DESTROY_HEIGHT = WorldReloader.config.DESTROY_HEIGHT;
        this.COPY_DEPTH = WorldReloader.config.COPY_DEPTH;
        this.COPY_HEIGHT = WorldReloader.config.COPY_HEIGHT;
    }

    @Override
    protected void processPosition(BlockPos circlePos) {
        int targetX = circlePos.getX();
        int targetZ = circlePos.getZ();

        if (!world.isChunkLoaded(targetX >> 4, targetZ >> 4)) {
            return;
        }

        int offsetX = targetX - center.getX();
        int offsetZ = targetZ - center.getZ();
        int referenceX = referenceCenter.getX() + offsetX;
        int referenceZ = referenceCenter.getZ() + offsetZ;

        if (!world.isChunkLoaded(referenceX >> 4, referenceZ >> 4)) {
            return;
        }

        int originalSurfaceY = world.getChunk(targetX >> 4, targetZ >> 4)
                .getHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES)
                .get(targetX & 15, targetZ & 15);

        ReferenceTerrainInfo referenceInfo = getReferenceTerrainInfo(referenceX, referenceZ);
        if (referenceInfo == null) {
            return;
        }

        int referenceSurfaceYAtTarget = referenceInfo.surfaceY + center.getY() - this.referenceCenter.getY();

        if (shouldSkipProcessing(referenceSurfaceYAtTarget, originalSurfaceY)) {
            return;
        }
        if(currentRadius<=8) {
            destroyAtPositionWithPreserve(targetX, targetZ, originalSurfaceY);
            copyTerrainStructureTopDownWithPreserve(targetX, targetZ, referenceInfo);
        }
        else
        {
            destroyAtPosition(targetX, targetZ, originalSurfaceY);
            copyTerrainStructureTopDown(targetX, targetZ, referenceInfo);
        }
    }

    @Override
    protected ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ) {
        if (!world.isChunkLoaded(referenceX >> 4, referenceZ >> 4)) {
            return null;
        }

        int referenceSurfaceY = world.getChunk(referenceX >> 4, referenceZ >> 4)
                .getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                .get(referenceX & 15, referenceZ & 15);

        if (referenceSurfaceY < 19) {
            return null;
        }

        referenceSurfaceY = validateAndAdjustHeight(world, referenceX, referenceZ, referenceSurfaceY, minY);
        return analyzeTerrain(world, referenceX, referenceZ, referenceSurfaceY, minY, COPY_DEPTH, COPY_HEIGHT);
    }

    @Override
    protected void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo) {
        copyTerrainStructureTopDown(targetX, targetZ, referenceInfo);
    }

    @Override
    protected boolean shouldSkipProcessing(int referenceSurfaceYAtTarget, int originalSurfaceY) {
        return referenceSurfaceYAtTarget < originalSurfaceY - DESTROY_HEIGHT;
    }

    private void destroyAtPositionWithPreserve(int targetX, int targetZ, int surfaceY) {
        if (surfaceY < 18) {
            return;
        }

        int startY = Math.max(minY, surfaceY - DESTROY_DEPTH);
        int endY = surfaceY + DESTROY_HEIGHT;

        for (int y = startY; y <= endY; y++) {
            BlockPos targetPos = new BlockPos(targetX, y, targetZ);
            if (shouldPreserveCenterArea(targetPos)) {
                continue;
            }
            BlockState currentState = world.getBlockState(targetPos);
            if (!currentState.isAir()) {
                world.setBlockState(targetPos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }
    private void destroyAtPosition(int targetX, int targetZ, int surfaceY) {
        if (surfaceY < 18) {
            return;
        }

        int startY = Math.max(minY, surfaceY - DESTROY_DEPTH);
        int endY = surfaceY + DESTROY_HEIGHT;

        for (int y = startY; y <= endY; y++) {
            BlockPos targetPos = new BlockPos(targetX, y, targetZ);
            BlockState currentState = world.getBlockState(targetPos);
            if (!currentState.isAir()) {
                world.setBlockState(targetPos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    private void copyTerrainStructureTopDownWithPreserve(int targetX, int targetZ, ReferenceTerrainInfo reference) {
        if (reference.aboveSurfaceBlocks != null && reference.aboveSurfaceHeights != null) {
            for (int i = reference.aboveSurfaceBlocks.length - 1; i >= 0; i--) {
                int targetY = reference.aboveSurfaceHeights[i] + center.getY() - this.referenceCenter.getY();
                BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
                BlockState referenceState = reference.aboveSurfaceBlocks[i];

                if (shouldPreserveCenterArea(targetPos)) {
                    continue;
                }

                BlockState currentState = world.getBlockState(targetPos);
                if (currentState.isAir() || currentState.isReplaceable()) {
                    world.setBlockState(targetPos, referenceState, 3);
                }
            }
        }

        if (reference.blocks != null && reference.heights.length != 0) {
            for (int i = reference.blocks.length - 1; i >= 0; i--) {
                int targetY = reference.heights[i] + center.getY() - this.referenceCenter.getY();
                BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
                BlockState referenceState = reference.blocks[i];

                if (shouldPreserveCenterArea(targetPos)) {
                    continue;
                }

                if (!referenceState.isAir()) {
                    BlockState currentState = world.getBlockState(targetPos);
                    if (!currentState.equals(referenceState)) {
                        world.setBlockState(targetPos, referenceState, 3);
                    }
                }
            }
        }
    }
    private void copyTerrainStructureTopDown(int targetX, int targetZ, ReferenceTerrainInfo reference) {
        if (reference.aboveSurfaceBlocks != null && reference.aboveSurfaceHeights != null) {
            for (int i = reference.aboveSurfaceBlocks.length - 1; i >= 0; i--) {
                int targetY = reference.aboveSurfaceHeights[i] + center.getY() - this.referenceCenter.getY();
                BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
                BlockState referenceState = reference.aboveSurfaceBlocks[i];


                BlockState currentState = world.getBlockState(targetPos);
                if (currentState.isAir() || currentState.isReplaceable()) {
                    world.setBlockState(targetPos, referenceState, 3);
                }
            }
        }

        if (reference.blocks != null && reference.heights.length != 0) {
            for (int i = reference.blocks.length - 1; i >= 0; i--) {
                int targetY = reference.heights[i] + center.getY() - this.referenceCenter.getY();
                BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
                BlockState referenceState = reference.blocks[i];


                if (!referenceState.isAir()) {
                    BlockState currentState = world.getBlockState(targetPos);
                    if (!currentState.equals(referenceState)) {
                        world.setBlockState(targetPos, referenceState, 3);
                    }
                }
            }
        }
    }
}