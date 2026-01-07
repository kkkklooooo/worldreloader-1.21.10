package com.worldreloader.transformationtasks;

import com.worldreloader.ModConfig;
import com.worldreloader.WorldReloader;
import com.worldreloader.transformationtasks.BaseTransformationTask;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.List;

public class LineTransformationTask extends BaseTransformationTask {
    protected LineTransformationTask(TerrainTransformationBuilder builder) {
        super(builder.world, builder.changePos, builder.targetPos, builder.player,
                builder.targetDimensionWorld,
                builder.radius,
                builder.steps,
                builder.itemCleanupInterval,
                builder.isChangeBiome,
                builder.preserveBeacon);
        this.paddingCount = builder.padding;
        this.yMin = builder.yMin;
        this.yMax = builder.yMax;
    }

    protected int width = WorldReloader.config.width;
    protected int len = 10;
    protected int currentLen = 0;
    protected int currentWidth = 0;
    List<BlockPos> positions=new ArrayList<>();
    private final int paddingCount;
    private final int yMin;
    private final  int yMax;
    @Override
    protected boolean processCurrentStepPositions() {
        // 使用目标维度的生物群系
        RegistryEntry<Biome> bb = getBiomeAtChunkCenter(targetDimensionWorld,
                new ChunkPos(referenceCenter.getX()>>4, referenceCenter.getZ()>>4));

        for (BlockPos pos : currentRadiusPositions) {
            world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            if (isChangeBiome) {
                setBiome(pos, bb, world);
            }
            processPosition(pos);
        }
        return true;
    }

    @Override
    protected List<BlockPos> generateCirclePositions(int radius) {
        List<BlockPos> positions = new ArrayList<>();

        // 如果是宽度0，生成中心线的所有长度位置

        if (currentWidth == 0) {
            for (int x = 0; x <= len; x++) {
                int targetX = center.getX() + x;
                positions.add(new BlockPos(targetX, 0, center.getZ()));
            }
            return positions;
        }

        // 对于其他宽度，生成两侧的所有长度位置
        for (int x = 0; x <= len; x++) {
            int targetX = center.getX() + x;
            // 生成两侧位置
            positions.add(new BlockPos(targetX, 0, center.getZ() + currentWidth));
            positions.add(new BlockPos(targetX, 0, center.getZ() - currentWidth));
        }

        return positions;
    }

    protected List<BlockPos> generateExpandingWidthSave(int currentWidth) {
        List<ModConfig.SavedPosition> savePositions = WorldReloader.config.savedPositions;
        List<BlockPos> positions1 = new ArrayList<>();

        // 如果是宽度0，生成所有保存点之间的连接路径
        if (currentWidth == 0) {
            positions.clear();

            if (!savePositions.isEmpty()) {
                // 按x坐标对保存点进行排序
                List<ModConfig.SavedPosition> sortedSaves = new ArrayList<>(savePositions);
                sortedSaves.sort((a, b) -> Integer.compare(a.x, b.x));

                // 生成保存点之间的路径
                for (int i = 0; i < sortedSaves.size() - 1; i++) {
                    ModConfig.SavedPosition currentSave = sortedSaves.get(i);
                    ModConfig.SavedPosition nextSave = sortedSaves.get(i + 1);

                    BlockPos currentPos = new BlockPos(currentSave.x, 0, currentSave.z);
                    BlockPos nextPos = new BlockPos(nextSave.x, 0, nextSave.z);

                    List<BlockPos> segmentPath = generateLinePositions(currentPos, nextPos);
                    positions.addAll(segmentPath);
                }
            }

            return positions;
        }

        // 对于其他宽度，生成两侧的所有位置
        for (BlockPos pos : positions) {
            // 生成两侧位置
            positions1.add(new BlockPos(pos.getX(), 0, pos.getZ() + currentWidth));
            positions1.add(new BlockPos(pos.getX(), 0, pos.getZ() - currentWidth));
        }

        return positions1;
    }
    protected List<BlockPos> generateLinePositions(BlockPos start, BlockPos end) {
        List<BlockPos> positions = new ArrayList<>();

        int x0 = start.getX();
        int y0 = start.getZ(); // 注意：我们把 z 当作 y
        int x1 = end.getX();
        int y1 = end.getZ();

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0, y = y0;

        while (true) {
            positions.add(new BlockPos(x, 0, y)); // x = x, z = y
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
        return positions;
    }
    @Override
    protected void processNextStep() {
        // 检查是否完成所有宽度
        if (currentWidth > width) {
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§6地形改造完成！"), false);
            stop();
            return;
        }

        // 生成当前宽度的所有位置（从中心向两侧）
        currentRadiusPositions = generateExpandingWidthSave(currentWidth);

        if (currentRadiusPositions.isEmpty()) {
            currentWidth++;
            return;
        }

        if(WorldReloader.config.Debug && currentStep == 0) {
            player.sendMessage(net.minecraft.text.Text.literal("§7开始改造宽度: " + currentWidth +
                    " (共 " + currentRadiusPositions.size() + " 个位置)"), false);
        }

        // 处理物品清理
        if (shouldCleanupItems()) {
            cleanupItemEntities();
            lastCleanupRadius = currentWidth;
        }

        // 处理当前位置的所有点
        processCurrentStepPositions();

        if(WorldReloader.config.Debug) {
            player.sendMessage(net.minecraft.text.Text.literal("§e完成宽度: " + currentWidth), false);
        }

        // 直接移动到下一个宽度
        currentWidth++;
        currentStep = 0;
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

        destroyAtPosition(targetX, targetZ);
        ReferenceTerrainInfo referenceInfo = getReferenceTerrainInfo(referenceX, referenceZ);
        if (referenceInfo != null) {
            copyFromReference(targetX, targetZ, referenceInfo, originalSurfaceY);
        }
    }

    @Override
    protected ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ) {
        if (!targetDimensionWorld.isChunkLoaded(referenceX >> 4, referenceZ >> 4)) {
            return null;
        }

        // 使用目标维度世界获取表面高度
        int referenceSurfaceY = targetDimensionWorld.getChunk(referenceX >> 4, referenceZ >> 4)
                .getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                .get(referenceX & 15, referenceZ & 15);

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
        int surfaceY = world.getChunk(targetX >> 4, targetZ >> 4)
                .getHeightmap(Heightmap.Type.WORLD_SURFACE)
                .get(targetX & 15, targetZ & 15);

        if (surfaceY < yMin -1) {
            return;
        }

        for (int y = yMin; y <= surfaceY + yMax; y++) {
            BlockPos targetPos = new BlockPos(targetX, y, targetZ);
            if (currentRadius <= 8 && shouldPreserveCenterArea(targetPos)) {
                continue;
            }
            if(y>referenceCenter.getY()+yMax){
                continue;
            }
            BlockState currentState = world.getBlockState(targetPos);
            if (!currentState.isAir()) {
                world.setBlockState(targetPos, Blocks.AIR.getDefaultState(), 3);
            }
        }
    }

    private int validateAndAdjustReferenceHeight(int x, int z, int initialHeight) {
        int currentY = initialHeight;
        while (currentY > minY + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            // 使用目标维度世界检查方块
            BlockState state = targetDimensionWorld.getBlockState(pos);
            if (isSolidBlock(targetDimensionWorld, state)) {
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
            // 使用目标维度世界获取方块状态
            BlockState state = targetDimensionWorld.getBlockState(pos);
            if (!state.isAir() || y <= surfaceY) {
                blocks.add(state);
                heights.add(y);
            }
        }

        info.blocks = blocks.toArray(new BlockState[0]);
        info.heights = heights.stream().mapToInt(Integer::intValue).toArray();

        BlockPos abovePos = new BlockPos(x, surfaceY + 1, z);
        BlockState aboveState = targetDimensionWorld.getBlockState(abovePos);
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
            if(targetY>referenceCenter.getY()+yMax){
                continue;
            }
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

    private void copyWithoutPreservation(int targetX, int targetZ, ReferenceTerrainInfo reference) {
        for (int i = 0; i < reference.blocks.length; i++) {
            int targetY = reference.heights[i] + center.getY() - this.referenceCenter.getY();
            if(targetY>referenceCenter.getY()+yMax){
                continue;
            }
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



    private void applyPaddingTransition(int targetX, int targetZ, ReferenceTerrainInfo reference, int originalSurfaceY) {

        float progress = 1.0f - (float)(maxRadius - currentRadius) / paddingCount;
        int referenceTargetY = reference.surfaceY + center.getY() - this.referenceCenter.getY();
        int transitionSurfaceY = (int)(referenceTargetY + (originalSurfaceY - referenceTargetY) * progress);

        for (int i = 0; i < reference.blocks.length; i++) {
            int referenceY = reference.heights[i];
            int targetY = referenceY + center.getY() - this.referenceCenter.getY();
            int transitionY = calculateTransitionHeight(referenceY, reference.surfaceY, targetY, transitionSurfaceY, progress);

            BlockPos targetPos = new BlockPos(targetX, transitionY, targetZ);
            if (isSolidBlock(world,reference.blocks[i])) {
                BlockState referenceState = reference.blocks[i];
                if (!referenceState.isAir()) {
                    BlockState currentState = world.getBlockState(targetPos);
                    if (!currentState.equals(referenceState)) {
                        world.setBlockState(targetPos, referenceState, 3);
                    }
                }
            }
            else if(reference.blocks[i].getFluidState().isStill())
            {
                world.setBlockState(new BlockPos(targetX, targetY, targetZ),reference.blocks[i],3);
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
            if (!state.isAir() && isSolidBlock(world,state) && y > transitionSurfaceY + 2) {
                boolean hasSupport = false;
                for (int checkY = y - 1; checkY >= transitionSurfaceY; checkY--) {
                    BlockPos belowPos = new BlockPos(targetX, checkY, targetZ);
                    BlockState belowState = world.getBlockState(belowPos);
                    if (isSolidBlock(world,belowState)) {
                        hasSupport = true;
                        break;
                    }
                }
                if (!hasSupport) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
                }
            }
        }
    }
}