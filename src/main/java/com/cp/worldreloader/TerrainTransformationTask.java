package com.cp.worldreloader;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

public class TerrainTransformationTask extends BaseTransformationTask {
    private final int paddingCount;
    private final int yMin;
    private final int yMax;

    public TerrainTransformationTask(ServerLevel world, BlockPos center, BlockPos referenceCenter,
                                     ServerPlayer player) {
        super(world, center, referenceCenter, player,
                WorldReloader.config.maxRadius,
                WorldReloader.config.totalSteps2,
                WorldReloader.config.itemCleanupInterval);
        this.paddingCount = WorldReloader.config.paddingCount;
        this.yMax = WorldReloader.config.yMaxThanSurface;
        this.yMin = WorldReloader.config.yMin;
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

        destroyAtPosition(targetX, targetZ);
        ReferenceTerrainInfo referenceInfo = getReferenceTerrainInfo(referenceX, referenceZ);
        if (referenceInfo != null) {
            copyFromReference(targetX, targetZ, referenceInfo, originalSurfaceY);
        }
    }

    @Override
    protected ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ) {
        if (!world.hasChunk(referenceX >> 4, referenceZ >> 4)) {
            return null;
        }

        LevelChunk referenceChunk = world.getChunk(referenceX >> 4, referenceZ >> 4);
        int referenceSurfaceY = referenceChunk.getHeight(Heightmap.Types.MOTION_BLOCKING, referenceX & 15, referenceZ & 15);

        if (referenceSurfaceY < yMin - 1) {
            return null;
        }

        referenceSurfaceY = validateAndAdjustReferenceHeight(referenceX, referenceZ, referenceSurfaceY);
        return analyzeReferenceTerrain(referenceX, referenceZ, referenceSurfaceY);
    }

    @Override
    protected void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo) {
        copyFromReference(targetX, targetZ, referenceInfo, 0);
    }

    protected void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo, int originalSurfaceY) {
        copyTerrainStructure(targetX, targetZ, referenceInfo, originalSurfaceY);
    }

    @Override
    protected boolean shouldSkipProcessing(int referenceSurfaceYAtTarget, int originalSurfaceY) {
        return false;
    }

    private void destroyAtPosition(int targetX, int targetZ) {
        LevelChunk targetChunk = world.getChunk(targetX >> 4, targetZ >> 4);
        int surfaceY = targetChunk.getHeight(Heightmap.Types.WORLD_SURFACE, targetX & 15, targetZ & 15);

        if (surfaceY < yMin - 1) {
            return;
        }

        for (int y = yMin; y <= surfaceY + yMax; y++) {
            BlockPos targetPos = new BlockPos(targetX, y, targetZ);
            if (currentRadius <= 8 && shouldPreserveCenterArea(targetPos)) {
                continue;
            }
            BlockState currentState = world.getBlockState(targetPos);
            if (!currentState.isAir()) {
                world.setBlock(targetPos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private int validateAndAdjustReferenceHeight(int x, int z, int initialHeight) {
        int currentY = initialHeight;
        while (currentY > minY + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            BlockState state = world.getBlockState(pos);
            if (isSolidBlock(world, state)) {
                return currentY;
            }
            currentY--;
        }
        return initialHeight;
    }

    private ReferenceTerrainInfo analyzeReferenceTerrain(int x, int z, int surfaceY) {
        ReferenceTerrainInfo info = new ReferenceTerrainInfo();
        info.surfaceY = surfaceY;

        List<BlockState> blocks = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();

        for (int y = yMin; y <= surfaceY + yMax; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() || y <= surfaceY) {
                blocks.add(state);
                heights.add(y);
            }
        }

        info.blocks = blocks.toArray(new BlockState[0]);
        info.heights = heights.stream().mapToInt(Integer::intValue).toArray();

        BlockPos abovePos = new BlockPos(x, surfaceY + 1, z);
        BlockState aboveState = world.getBlockState(abovePos);
        if (!aboveState.isAir()) {
            info.aboveSurfaceBlocks = new BlockState[]{aboveState};
            info.aboveSurfaceHeights = new int[]{surfaceY + 1};
        }

        return info;
    }

    private void copyTerrainStructure(int targetX, int targetZ, ReferenceTerrainInfo reference, int originalSurfaceY) {
        if (reference.blocks != null && reference.heights.length != 0) {
            if (currentRadius <= 8) {
                copyWithCenterPreservation(targetX, targetZ, reference);
            } else if (currentRadius < maxRadius - paddingCount) {
                copyWithoutPreservation(targetX, targetZ, reference);
            } else {
                applyPaddingTransition(targetX, targetZ, reference, originalSurfaceY);
            }
        }
    }

    private void copyWithCenterPreservation(int targetX, int targetZ, ReferenceTerrainInfo reference) {
        for (int i = 0; i < reference.blocks.length; i++) {
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

    private void copyWithoutPreservation(int targetX, int targetZ, ReferenceTerrainInfo reference) {
        for (int i = 0; i < reference.blocks.length; i++) {
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

    private void applyPaddingTransition(int targetX, int targetZ, ReferenceTerrainInfo reference, int originalSurfaceY) {
        float progress = 1.0f - (float)(maxRadius - currentRadius) / paddingCount;
        int referenceTargetY = reference.surfaceY + center.getY() - this.referenceCenter.getY();
        int transitionSurfaceY = (int)(referenceTargetY + (originalSurfaceY - referenceTargetY) * progress);

        for (int i = 0; i < reference.blocks.length; i++) {
            int referenceY = reference.heights[i];
            int targetY = referenceY + center.getY() - this.referenceCenter.getY();
            int transitionY = calculateTransitionHeight(referenceY, reference.surfaceY, targetY, transitionSurfaceY, progress);

            BlockPos targetPos = new BlockPos(targetX, transitionY, targetZ);
            BlockState referenceState = reference.blocks[i];

            if (isSolidBlock(world, referenceState)) {
                if (!referenceState.isAir()) {
                    BlockState currentState = world.getBlockState(targetPos);
                    if (!currentState.equals(referenceState)) {
                        world.setBlock(targetPos, referenceState, 3);
                    }
                }
            } else if (!referenceState.getFluidState().isEmpty()) {
                world.setBlock(new BlockPos(targetX, targetY, targetZ), referenceState, 3);
            }
        }
        cleanFloatingBlocks(targetX, targetZ, transitionSurfaceY);
    }

    private int calculateTransitionHeight(int referenceY, int referenceSurfaceY, int targetY, int transitionSurfaceY, float progress) {
        if (referenceY > referenceSurfaceY) {
            int heightAboveSurface = referenceY - referenceSurfaceY;
            return transitionSurfaceY + heightAboveSurface;
        }
        int depthBelowSurface = referenceSurfaceY - referenceY;
        return transitionSurfaceY - depthBelowSurface;
    }

    private void cleanFloatingBlocks(int targetX, int targetZ, int transitionSurfaceY) {
        for (int y = transitionSurfaceY + 10; y > transitionSurfaceY; y--) {
            BlockPos pos = new BlockPos(targetX, y, targetZ);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() && isSolidBlock(world, state) && y > transitionSurfaceY + 2) {
                boolean hasSupport = false;
                for (int checkY = y - 1; checkY >= transitionSurfaceY; checkY--) {
                    BlockPos belowPos = new BlockPos(targetX, checkY, targetZ);
                    BlockState belowState = world.getBlockState(belowPos);
                    if (isSolidBlock(world, belowState)) {
                        hasSupport = true;
                        break;
                    }
                }
                if (!hasSupport) {
                    world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}