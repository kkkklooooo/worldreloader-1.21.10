package com.worldreloader;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SurfaceTransformationTask {
    private final ServerWorld world;
    private final BlockPos center;
    private final BlockPos referenceCenter;
    private final net.minecraft.entity.player.PlayerEntity player;
    private Set<ChunkPos> forcedChunks = new HashSet<>();

    private int currentRadius = 0;
    private final int maxRadius = 512;
    private boolean isActive = false;
    private boolean isinit = false;

    // 控制改造速度的间隔变量
    private final int interval = 10;
    private int radiusCounter = 0;

    // 处理范围
    private static final int DESTROY_DEPTH = 10;
    private static final int DESTROY_HEIGHT = 10;
    private static final int COPY_DEPTH = 10;
    private static final int COPY_HEIGHT = 10;
    private final int minY;

    private static final int HEIGHT_DIFFERENCE_THRESHOLD = 10;

    public SurfaceTransformationTask(ServerWorld world, BlockPos center, BlockPos referenceCenter, net.minecraft.entity.player.PlayerEntity player) {
        this.world = world;
        this.center = center;
        this.referenceCenter = referenceCenter;
        this.player = player;
        this.minY = world.getBottomY();
        RegisterToTick();
    }

    public void start() {
        this.isActive = true;
    }

    public void stop() {
        this.isActive = false;
    }

    private void RegisterToTick() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (this.isActive) {
                processNextStep();
                if (!isinit) {
                    int chunkRadius = (maxRadius + 15) >> 4;
                    for (int x = -chunkRadius; x <= chunkRadius; x++) {
                        for (int z = -chunkRadius; z <= chunkRadius; z++) {
                            ChunkPos chunkPos = new ChunkPos((referenceCenter.getX() >> 4) + x, (referenceCenter.getZ() >> 4) + z);
                            if (!forcedChunks.contains(chunkPos)) {
                                world.setChunkForced(chunkPos.x, chunkPos.z, true);
                                forcedChunks.add(chunkPos);
                            }
                        }
                    }
                    isinit = true;
                }
            } else if (isinit) {
                for (ChunkPos chunkPos : forcedChunks) {
                    world.setChunkForced(chunkPos.x, chunkPos.z, false);
                }
                forcedChunks.clear();
                isinit = false;
            }
        });
    }

    private void processNextStep() {
        if (currentRadius > maxRadius) {
            player.sendMessage(net.minecraft.text.Text.literal("§6地形改造完成！"), false);
            stop();
            return;
        }

        // 间隔控制逻辑
        radiusCounter++;
        if (radiusCounter < interval) {
            player.sendMessage(net.minecraft.text.Text.literal("§7准备改造半径: " + currentRadius + "格 (等待中 " + radiusCounter + "/" + interval + ")"), false);
            return;
        }

        radiusCounter = 0;

        // 生成当前半径的所有位置
        List<BlockPos> circlePositions = generateCirclePositions(currentRadius);

        // 处理当前圆环的所有位置
        for (BlockPos circlePos : circlePositions) {
            processPosition(circlePos);
        }

        player.sendMessage(net.minecraft.text.Text.literal("§e改造半径: " + currentRadius + "格"), false);
        currentRadius++;
    }

    /**
     * 生成圆形区域内的所有位置
     */
    private List<BlockPos> generateCirclePositions(int radius) {
        List<BlockPos> positions = new ArrayList<>();

        if (radius == 0) {
            positions.add(new BlockPos(center.getX(), 0, center.getZ()));
            return positions;
        }

        int radiusSquared = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int distanceSquared = dx * dx + dz * dz;
                if (distanceSquared <= radiusSquared && distanceSquared > (radius - 1) * (radius - 1)) {
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    positions.add(new BlockPos(x, 0, z));
                }
            }
        }

        return positions;
    }

    /**
     * 处理单个位置：先检查高度条件，再决定是否破坏和重建
     */
    private void processPosition(BlockPos circlePos) {
        int targetX = circlePos.getX();
        int targetZ = circlePos.getZ();

        if (!world.isChunkLoaded(targetX >> 4, targetZ >> 4)) {
            return;
        }

        // 计算偏移量
        int offsetX = targetX - center.getX();
        int offsetZ = targetZ - center.getZ();
        int referenceX = referenceCenter.getX() + offsetX;
        int referenceZ = referenceCenter.getZ() + offsetZ;

        if (!world.isChunkLoaded(referenceX >> 4, referenceZ >> 4)) {
            return;
        }

        // 获取当前地形高度
        int originalSurfaceY = world.getChunk(targetX >> 4, targetZ >> 4)
                .getHeightmap(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES)
                .get(targetX & 15, targetZ & 15);

        // 获取参考地形信息
        ReferenceTerrainInfo referenceInfo = getReferenceTerrainInfo(referenceX, referenceZ);
        if (referenceInfo == null) {
            return;
        }

        // 计算参考地形在目标位置的高度（考虑中心点高度差）
        int referenceSurfaceYAtTarget = referenceInfo.surfaceY + center.getY() - this.referenceCenter.getY();

        // 检查是否需要跳过处理：参考地形高度过低
        if (shouldSkipProcessing(referenceSurfaceYAtTarget, originalSurfaceY)) {
            // 跳过这个位置的处理
            return;
        }

        // 先破坏目标位置
        destroyAtPosition(targetX, targetZ, originalSurfaceY);

        // 然后从参考位置复制到目标位置
        copyFromReference(targetX, targetZ, referenceInfo);
    }

    /**
     * 检查是否应该跳过处理这个位置
     * 当参考地形高度低于当前地形高度-15时跳过
     */
    private boolean shouldSkipProcessing(int referenceSurfaceYAtTarget, int originalSurfaceY) {
        return referenceSurfaceYAtTarget < originalSurfaceY - HEIGHT_DIFFERENCE_THRESHOLD;
    }

    /**
     * 破坏目标位置
     */
    private void destroyAtPosition(int targetX, int targetZ, int surfaceY) {
        if (surfaceY < 18) {
            return;
        }

        // 清除范围
        int startY = Math.max(minY, surfaceY - DESTROY_DEPTH);
        int endY =surfaceY + DESTROY_HEIGHT;

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

    /**
     * 检查是否应该保留中心区域
     */
    private boolean shouldPreserveCenterArea(BlockPos pos) {
        if (pos.getX() == center.getX() && pos.getY() == center.getY() && pos.getZ() == center.getZ()) {
            return true;
        }
        if (pos.getY() == center.getY() - 1 &&
                Math.abs(pos.getX() - center.getX()) <= 1 &&
                Math.abs(pos.getZ() - center.getZ()) <= 1) {
            return true;
        }
        return false;
    }

    /**
     * 从参考位置复制到目标位置
     */
    private void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo) {
        copyTerrainStructure(targetX, targetZ, referenceInfo);
    }

    /**
     * 获取参考位置的完整地形信息
     */
    private ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ) {
        if (!world.isChunkLoaded(referenceX >> 4, referenceZ >> 4)) {
            return null;
        }

        // 使用 MOTION_BLOCKING 获取更准确的地形高度
        int referenceSurfaceY = world.getChunk(referenceX >> 4, referenceZ >> 4)
                .getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                .get(referenceX & 15, referenceZ & 15);

        if (referenceSurfaceY < 19) {
            return null;
        }

        // 进行高度验证
        referenceSurfaceY = validateAndAdjustHeight(referenceX, referenceZ, referenceSurfaceY);
        return analyzeTerrain(referenceX, referenceZ, referenceSurfaceY);
    }

    /**
     * 验证并调整高度
     */
    private int validateAndAdjustHeight(int x, int z, int initialHeight) {
        int currentY = initialHeight;
        int solidGroundCount = 0;

        // 向下搜索，找到连续的固体地面
        while (currentY > minY + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            BlockState state = world.getBlockState(pos);

            if (isSolidGroundBlock(state)) {
                solidGroundCount++;
                if (solidGroundCount >= 3) { // 连续3个固体方块认为是真实地面
                    return currentY + 2; // 返回到地表高度
                }
            } else {
                solidGroundCount = 0;
            }
            currentY--;
        }

        return initialHeight;
    }

    /**
     * 判断方块是否为真正的固体地面方块
     */
    private boolean isSolidGroundBlock(BlockState state) {
        Block block = state.getBlock();

        // 明确的非地面方块
        if (block == Blocks.AIR ||
                block == Blocks.LAVA ||
                block == Blocks.LEVER ||
                block == Blocks.OAK_LEAVES ||
                block == Blocks.SPRUCE_LEAVES ||
                block == Blocks.BIRCH_LEAVES ||
                block == Blocks.JUNGLE_LEAVES ||
                block == Blocks.ACACIA_LEAVES ||
                block == Blocks.DARK_OAK_LEAVES ||
                block == Blocks.MANGROVE_LEAVES ||
                block == Blocks.CHERRY_LEAVES ||
                block == Blocks.AZALEA_LEAVES ||
                block == Blocks.FLOWERING_AZALEA_LEAVES ||
                block == Blocks.PALE_OAK_LEAVES||
                block == Blocks.TALL_GRASS ||
                block == Blocks.FERN ||
                block == Blocks.LARGE_FERN ||
                block == Blocks.DEAD_BUSH ||
                block == Blocks.VINE) {
            return false;
        }

        // 检查是否为固体方块
        return state.isSolidBlock(world, new BlockPos(0, 0, 0)) ||
                block == Blocks.GRASS_BLOCK ||
                block == Blocks.DIRT ||
                block == Blocks.COARSE_DIRT ||
                block == Blocks.PODZOL ||
                block == Blocks.MYCELIUM ||
                block == Blocks.SAND ||
                block == Blocks.RED_SAND ||
                block == Blocks.GRAVEL ||
                block == Blocks.STONE ||
                block == Blocks.COBBLESTONE ||
                block == Blocks.MOSS_BLOCK ||
                block == Blocks.MUD ||
                block == Blocks.CLAY ||
                block == Blocks.SNOW_BLOCK ||
                block == Blocks.ICE ||
                block == Blocks.PACKED_ICE||
                block == Blocks.WATER;
    }

    /**
     * 分析地形的完整结构
     */
    private ReferenceTerrainInfo analyzeTerrain(int x, int z, int surfaceY) {
        ReferenceTerrainInfo info = new ReferenceTerrainInfo();
        info.surfaceY = surfaceY;

        // 分析范围
        int startY = Math.max(minY, surfaceY - COPY_DEPTH);
        int endY = surfaceY + COPY_HEIGHT;

        List<BlockState> blocks = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();

        // 分析主要地形结构
        for (int y = startY; y <= endY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);

            // 记录所有非空气方块，以及地表以下的空气（洞穴）
            if (!state.isAir() || y <= surfaceY) {
                blocks.add(state);
                heights.add(y);
            }
        }

        info.blocks = blocks.toArray(new BlockState[0]);
        info.heights = heights.stream().mapToInt(Integer::intValue).toArray();

        // 记录地表以上的装饰方块
        List<BlockState> aboveBlocks = new ArrayList<>();
        List<Integer> aboveHeights = new ArrayList<>();

        // 扩展到地表以上15格，以捕捉高树和结构
        for (int y = surfaceY + 1; y <= surfaceY + 15; y++) {
            BlockPos abovePos = new BlockPos(x, y, z);
            BlockState aboveState = world.getBlockState(abovePos);
            if (!aboveState.isAir()) {
                aboveBlocks.add(aboveState);
                aboveHeights.add(y);
            }
        }

        if (!aboveBlocks.isEmpty()) {
            info.aboveSurfaceBlocks = aboveBlocks.toArray(new BlockState[0]);
            info.aboveSurfaceHeights = aboveHeights.stream().mapToInt(Integer::intValue).toArray();
        }

        return info;
    }

    /**
     * 复制地形结构到目标位置
     */
    private void copyTerrainStructure(int targetX, int targetZ, ReferenceTerrainInfo reference) {
        // 复制主要地形方块
        if (reference.blocks != null && reference.heights.length != 0) {
            if (currentRadius <= 3) {
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
                            world.setBlockState(targetPos, referenceState, 3);
                        }
                    }
                }
            } else {
                for (int i = 0; i < reference.blocks.length; i++) {
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

        // 复制地表以上的装饰方块
        if (reference.aboveSurfaceBlocks != null && reference.aboveSurfaceHeights != null) {
            for (int i = 0; i < reference.aboveSurfaceBlocks.length; i++) {
                int targetY = reference.aboveSurfaceHeights[i] + center.getY() - this.referenceCenter.getY();
                BlockPos targetPos = new BlockPos(targetX, targetY, targetZ);
                BlockState referenceState = reference.aboveSurfaceBlocks[i];

                BlockState currentState = world.getBlockState(targetPos);
                if (currentState.isAir() || currentState.isReplaceable()) {
                    world.setBlockState(targetPos, referenceState, 3);
                }
            }
        }
    }



    /**
     * 参考地形信息类
     */
    private static class ReferenceTerrainInfo {
        int surfaceY;
        BlockState[] blocks;
        int[] heights;
        BlockState[] aboveSurfaceBlocks;
        int[] aboveSurfaceHeights;
    }
}