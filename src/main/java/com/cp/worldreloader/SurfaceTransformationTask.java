package com.cp.worldreloader;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.level.ServerPlayer;

public class SurfaceTransformationTask extends BaseTransformationTask {
    private final int DESTROY_DEPTH;
    private final int DESTROY_HEIGHT;
    private final int COPY_DEPTH;
    private final int COPY_HEIGHT;

    public SurfaceTransformationTask(ServerLevel world, BlockPos center, BlockPos referenceCenter,
                                     ServerPlayer player) {
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

        if (!world.hasChunk(targetX >> 4, targetZ >> 4)) {
            return;
        }

        int offsetX = targetX - center.getX();
        int offsetZ = targetZ - center.getZ();
        int referenceX = referenceCenter.getX() + offsetX;
        int referenceZ = referenceCenter.getZ() + offsetZ;

        if (!world.hasChunk(referenceX >> 4, referenceZ >> 4)) {
            return;
        }

        LevelChunk targetChunk = world.getChunk(targetX >> 4, targetZ >> 4);
        int originalSurfaceY = targetChunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, targetX & 15, targetZ & 15);

        ReferenceTerrainInfo referenceInfo = getReferenceTerrainInfo(referenceX, referenceZ);
        if (referenceInfo == null) {
            return;
        }

        int referenceSurfaceYAtTarget = referenceInfo.surfaceY + center.getY() - this.referenceCenter.getY();

        if (shouldSkipProcessing(referenceSurfaceYAtTarget, originalSurfaceY)) {
            return;
        }

        if (currentRadius <= 8) {
            destroyAtPositionWithPreserve(targetX, targetZ, originalSurfaceY);
            copyTerrainStructureTopDownWithPreserve(targetX, targetZ, referenceInfo);
        } else {
            destroyAtPosition(targetX, targetZ, originalSurfaceY);
            copyTerrainStructureTopDown(targetX, targetZ, referenceInfo);
        }
    }

    @Override
    protected ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ) {
        if (!world.hasChunk(referenceX >> 4, referenceZ >> 4)) {
            return null;
        }

        LevelChunk referenceChunk = world.getChunk(referenceX >> 4, referenceZ >> 4);
        int referenceSurfaceY = referenceChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, referenceX & 15, referenceZ & 15);

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
                world.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
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
                world.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
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
                if (currentState.isAir() || currentState.canBeReplaced()) {
                    world.setBlock(targetPos, referenceState, 3);
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
                        world.setBlock(targetPos, referenceState, 3);
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
                if (currentState.isAir() || currentState.canBeReplaced()) {
                    world.setBlock(targetPos, referenceState, 3);
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
                        world.setBlock(targetPos, referenceState, 3);
                    }
                }
            }
        }
    }
}
