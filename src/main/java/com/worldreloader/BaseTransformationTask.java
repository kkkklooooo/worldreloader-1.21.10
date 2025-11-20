package com.worldreloader;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.FillBiomeCommand;
import net.minecraft.server.command.FillCommand;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSupplier;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static net.minecraft.server.command.FillBiomeCommand.UNLOADED_EXCEPTION;

public abstract class BaseTransformationTask {
    protected final ServerWorld world;
    protected final BlockPos center;
    protected final BlockPos referenceCenter;
    protected final net.minecraft.entity.player.PlayerEntity player;
    protected Set<ChunkPos> forcedChunks = new HashSet<>();

    protected int currentRadius = 0;
    protected final int maxRadius;
    protected boolean isActive = false;
    protected boolean isinit = false;

    protected final int totalSteps;
    protected int currentStep = 0;

    protected final int minY;
    protected final int itemCleanupInterval;
    protected int lastCleanupRadius = -1;

    protected int width = WorldReloader.config.width;
    protected int len = 10;
    protected int currentLen = 0;

    // 修改：当前处理的宽度（从中心向两侧扩展）
    protected int currentWidth = 0;
    List<BlockPos> positions=new ArrayList<>();

    protected List<BlockPos> currentRadiusPositions = new ArrayList<>();

    public BaseTransformationTask(ServerWorld world, BlockPos center, BlockPos referenceCenter,
                                  net.minecraft.entity.player.PlayerEntity player,
                                  int maxRadius, int totalSteps, int itemCleanupInterval) {
        this.world = world;
        this.center = center;
        this.referenceCenter = referenceCenter;
        this.player = player;
        this.minY = world.getBottomY();
        this.maxRadius = maxRadius;
        this.totalSteps = totalSteps;
        this.itemCleanupInterval = itemCleanupInterval;
        registerToTick();
    }

    // 公共方法
    public void start() {

        this.isActive = true;
    }

    public void stop() {
        cleanupItemEntities();
        if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§a最终物品清理完成！"), false);
        this.isActive = false;
        currentRadiusPositions.clear();
        currentStep = 0;
        lastCleanupRadius = -1;
        currentWidth = 0;
        currentLen = 0;
    }

    protected abstract void processPosition(BlockPos circlePos);
    protected abstract ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ);
    protected abstract void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo);
    protected abstract boolean shouldSkipProcessing(int referenceSurfaceYAtTarget, int originalSurfaceY);

    protected void registerToTick() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (this.isActive) {
                processNextStep2();
                handleChunkForcing2();
            } else if (isinit) {
                cleanupChunkForcing();
            }
        });
    }

    protected void handleChunkForcing() {
        if (!isinit) {
            int chunkRadius = (maxRadius + 15) >> 4;
            for (int x = -chunkRadius; x <= chunkRadius; x++) {
                for (int z = -chunkRadius; z <= chunkRadius; z++) {
                    ChunkPos chunkPos = new ChunkPos((referenceCenter.getX() >> 4) + x, (referenceCenter.getZ() >> 4) + z);
                    if (!forcedChunks.contains(chunkPos)) {
                        world.setChunkForced(chunkPos.x, chunkPos.z, true);
                        if(WorldReloader.config.isChangeBiome) {
                            RegistryEntry<Biome> bb = getBiomeAtChunkCenter(world, chunkPos);
                            setBiome(center.add(16*x, 0, 16*z), bb, world);
                        }
                        forcedChunks.add(chunkPos);
                    }
                }
            }
            isinit = true;
        }
    }

    protected void handleChunkForcing2() {
        if (!isinit) {
            int chunkLen = len >> 4;
            int chunkWidth = width >> 4;

            for (int x = 0; x <= chunkLen; x++) {
                for (int z = -chunkWidth; z <= chunkWidth; z++) {
                    ChunkPos chunkPos = new ChunkPos((referenceCenter.getX() >> 4) + x, (referenceCenter.getZ() >> 4) + z);
                    if (!forcedChunks.contains(chunkPos)) {
                        world.setChunkForced(chunkPos.x, chunkPos.z, true);
                        if(WorldReloader.config.isChangeBiome) {
                            RegistryEntry<Biome> bb = getBiomeAtChunkCenter(world, chunkPos);
                            setBiome(center.add(16*x, 0, 16*z), bb, world);
                        }
                        forcedChunks.add(chunkPos);
                    }
                }
            }
            isinit = true;
        }
    }

    protected void cleanupChunkForcing() {
        for (ChunkPos chunkPos : forcedChunks) {
            world.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        forcedChunks.clear();
        isinit = false;
    }

    public static RegistryEntry<Biome> getBiomeAtChunkCenter(World world, ChunkPos chunkPos) {
        int centerX = chunkPos.getStartX() + 8;
        int centerZ = chunkPos.getStartZ() + 8;
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, centerX, centerZ);
        BlockPos topPos = new BlockPos(centerX, topY, centerZ);
        return world.getBiome(topPos);
    }

    protected void processNextStep() {
        if (currentRadius > maxRadius) {
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§6地形改造完成！"), false);
            stop();
            return;
        }

        if (currentStep == 0) {
            currentRadiusPositions = generateCirclePositions(currentRadius);
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§7开始改造半径: " + currentRadius + "格 (共 " + currentRadiusPositions.size() + " 个位置)"), false);
        }

        if (shouldCleanupItems()) {
            cleanupItemEntities();
            lastCleanupRadius = currentRadius;
        }

        if (!processCurrentStepPositions()) {
            return;
        }

        if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§e完成步骤: 半径 " + currentRadius + "格 (" + (currentStep + 1) + "/" + totalSteps + ")"), false);
        currentStep++;

        if (currentStep >= totalSteps) {
            currentRadius++;
            currentStep = 0;
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§a完成半径: " + (currentRadius - 1) + "格"), false);
        }
    }

    // 修改：从中心向两侧逐渐变宽的生成方式
    protected void processNextStep2() {
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
        processCurrentStepPositionsLine();

        if(WorldReloader.config.Debug) {
            player.sendMessage(net.minecraft.text.Text.literal("§e完成宽度: " + currentWidth), false);
        }

        // 直接移动到下一个宽度
        currentWidth++;
        currentStep = 0;
    }

    protected boolean shouldCleanupItems() {
        return currentWidth % itemCleanupInterval == 0 && currentWidth != lastCleanupRadius;
    }

    protected boolean processCurrentStepPositions() {
        int totalPositions = currentRadiusPositions.size();
        int positionsPerStep = (totalPositions + totalSteps - 1) / totalSteps;
        int startIndex = currentStep * positionsPerStep;
        int endIndex = Math.min(startIndex + positionsPerStep, totalPositions);

        if (startIndex >= totalPositions) {
            currentRadius++;
            currentStep = 0;
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§a完成半径: " + (currentRadius - 1) + "格"), false);
            return false;
        }

        List<BlockPos> currentStepPositions = currentRadiusPositions.subList(startIndex, endIndex);
        for (BlockPos pos : currentStepPositions) {
            processPosition(pos);
        }
        return true;
    }

    protected boolean processCurrentStepPositionsLine() {
        for (BlockPos pos : currentRadiusPositions) {
            world.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4,true);
            //WorldReloader.LOGGER.info(pos.toShortString());
            processPosition(pos);
        }
        return true;
    }

    protected List<BlockPos> generateCirclePositions(int radius) {
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

    // 修改：生成当前宽度的所有位置（从中心向两侧扩展）
    protected List<BlockPos> generateExpandingWidthPositions(int currentWidth) {
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
//    protected List<BlockPos> generateExpandingWidthSave(int currentWidth) {
//        List<ModConfig.SavedPosition> savepositions =WorldReloader.config.savedPositions;
//        List<BlockPos> positions1=new ArrayList<>();
//
//        // 如果是宽度0，生成中心线的所有长度位置
//        if (currentWidth == 0) {
//            positions.clear();
//            for (ModConfig.SavedPosition saveposition : savepositions) {
//                positions.add(new BlockPos(saveposition.x, 0, saveposition.z));
//            }
//            return positions;
//        }
//        // 对于其他宽度，生成两侧的所有长度位置
//        for (BlockPos position : positions) {
//            // 生成两侧位置
//            positions1.add(new BlockPos(position.getX(), 0, position.getZ() + currentWidth));
//            positions1.add(new BlockPos(position.getX(), 0, position.getZ() - currentWidth));
//        }
//
//        return positions1;
//    }

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

//    protected List<BlockPos> generateExpandingWidthSave(int currentWidth) {
//        // 1. 获取保存点列表并创建一个副本以进行排序，避免修改原始配置列表
//        List<ModConfig.SavedPosition> savePositionsSorted = new ArrayList<>(WorldReloader.config.savedPositions);
//
//        // 2. 按照 X 坐标从小到大排序
//        savePositionsSorted.sort((p1, p2) -> Integer.compare(p1.x, p2.x));
//
//        List<BlockPos> expandingPositions = new ArrayList<>();
//
//        // 如果是宽度0，生成中心线路径（Center -> 第一个点 -> 第二个点...）
//        if (currentWidth == 0) {
//            this.positions.clear(); // 清空类成员变量 positions
//
//            // 起点从 center 开始
//            BlockPos startPos = new BlockPos(center.getX(), 0, center.getZ());
//
//            // 遍历所有排序后的保存点，将它们连成线
//            for (ModConfig.SavedPosition target : savePositionsSorted) {
//                BlockPos endPos = new BlockPos(target.x, 0, target.z);
//
//                // 计算两点之间的距离
//                int dx = endPos.getX() - startPos.getX();
//                int dz = endPos.getZ() - startPos.getZ();
//
//                // 步长取两个轴距离的最大值，保证方块连续
//                int steps = Math.max(Math.abs(dx), Math.abs(dz));
//
//                // 插值生成路径上的所有点
//                for (int i = 0; i <= steps; i++) {
//                    float t = (steps == 0) ? 0 : (float) i / steps;
//                    int currentX = startPos.getX() + Math.round(dx * t);
//                    int currentZ = startPos.getZ() + Math.round(dz * t);
//
//                    BlockPos newPos = new BlockPos(currentX, 0, currentZ);
//
//                    // 防止重复添加点（例如上一段的终点和这一段的起点）
//                    if (this.positions.isEmpty() || !this.positions.get(this.positions.size() - 1).equals(newPos)) {
//                        this.positions.add(newPos);
//                    }
//                }
//
//                // 将当前目标点设为下一段的起点
//                startPos = endPos;
//            }
//            return this.positions;
//        }
//
//        // 对于其他宽度，基于之前生成的中心路径(this.positions)向两侧扩展
//        // 只有当 positions 有数据时才执行（防止 width=0 步骤未执行的情况）
//        if (!this.positions.isEmpty()) {
//            for (BlockPos basePos : this.positions) {
//                // 生成两侧位置
//                expandingPositions.add(new BlockPos(basePos.getX(), 0, basePos.getZ() + currentWidth));
//                expandingPositions.add(new BlockPos(basePos.getX(), 0, basePos.getZ() - currentWidth));
//            }
//        }
//
//        return expandingPositions;
//    }

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

    protected void cleanupItemEntities() {
        int itemsCleared = 0;
        int cleanupRadius = Math.min(currentWidth + 10, width + 20);

        List<ItemEntity> itemsToRemove = new ArrayList<>(world.getEntitiesByClass(
                ItemEntity.class,
                getCleanupBoundingBox(cleanupRadius),
                entity -> true));

        for (ItemEntity item : itemsToRemove) {
            item.discard();
            itemsCleared++;
        }

        if (itemsCleared > 0 && WorldReloader.config.Debug) {
            player.sendMessage(net.minecraft.text.Text.literal("§b清理了 " + itemsCleared + " 个掉落物（宽度 " + currentWidth + "）"), false);
        }
    }

    protected net.minecraft.util.math.Box getCleanupBoundingBox(int radius) {
        int minX = center.getX();
        int minY = this.minY;
        int minZ = center.getZ() - width;
        int maxX = center.getX() + len;
        int maxY = 2000;
        int maxZ = center.getZ() + width;
        return new net.minecraft.util.math.Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    protected boolean shouldPreserveCenterArea(BlockPos pos) {
        for(int i=0;i<=4;i++)
        {
            if(pos.getY() == center.getY() - i &&
                    Math.abs(pos.getX() - center.getX()) <= i &&
                    Math.abs(pos.getZ() - center.getZ()) <= i)
            {
                return true;
            }
        }
        return false;
    }

    public int validateAndAdjustHeight(World world, int x, int z, int initialHeight, int minY) {
        int currentY = initialHeight;
        int solidGroundCount = 0;

        while (currentY > minY + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            BlockState state = world.getBlockState(pos);

            if (isSolidBlock(world,state)) {
                solidGroundCount++;
                if (solidGroundCount >= 3) {
                    return currentY + 2;
                }
            } else {
                solidGroundCount = 0;
            }
            currentY--;
        }
        return initialHeight;
    }

    public static ReferenceTerrainInfo analyzeTerrain(World world, int x, int z, int surfaceY, int minY, int copyDepth, int copyHeight) {
        ReferenceTerrainInfo info = new ReferenceTerrainInfo();
        info.surfaceY = surfaceY;

        int startY = Math.max(minY, surfaceY - copyDepth);
        int endY = surfaceY + copyHeight;

        List<BlockState> blocks = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();

        for (int y = startY; y <= endY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(pos);
            if (!state.isAir() || y <= surfaceY) {
                blocks.add(state);
                heights.add(y);
            }
        }

        info.blocks = blocks.toArray(new BlockState[0]);
        info.heights = heights.stream().mapToInt(Integer::intValue).toArray();

        List<BlockState> aboveBlocks = new ArrayList<>();
        List<Integer> aboveHeights = new ArrayList<>();

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

    public static void setBiome(BlockPos pos, RegistryEntry<Biome> biome, ServerWorld serverWorld) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        Chunk chunk = serverWorld.getChunk(chunkX, chunkZ);

        BlockBox chunkBox = BlockBox.create(
                new Vec3i(chunkX << 4, serverWorld.getBottomY(), chunkZ << 4),
                new Vec3i((chunkX << 4) + 15, serverWorld.getTopY(), (chunkZ << 4) + 15)
        );

        MutableInt modifiedCount = new MutableInt(0);

        BiomeSupplier biomeSupplier = createBiomeSupplier(modifiedCount, chunk, chunkBox, biome, b -> true);

        chunk.populateBiomes(biomeSupplier, serverWorld.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
        chunk.setNeedsSaving(true);

        serverWorld.getChunkManager().threadedAnvilChunkStorage.sendChunkBiomePackets(List.of(chunk));

        WorldReloader.LOGGER.info("成功设置区块 [{}, {}] 的生物群系，修改了 {} 个方块", chunkX, chunkZ, modifiedCount.getValue());
    }

    private static BlockPos convertPos(BlockPos pos) {
        return new BlockPos(convertCoordinate(pos.getX()), convertCoordinate(pos.getY()), convertCoordinate(pos.getZ()));
    }

    private static int convertCoordinate(int coordinate) {
        return BiomeCoords.toBlock(BiomeCoords.fromBlock(coordinate));
    }

    private static BiomeSupplier createBiomeSupplier(MutableInt counter, Chunk chunk, BlockBox box, RegistryEntry<Biome> biome, Predicate<RegistryEntry<Biome>> filter) {
        return (x, y, z, noise) -> {
            int i = BiomeCoords.toBlock(x);
            int j = BiomeCoords.toBlock(y);
            int k = BiomeCoords.toBlock(z);
            RegistryEntry<Biome> registryEntry2 = chunk.getBiomeForNoiseGen(x, y, z);
            if (box.contains(i, j, k) && filter.test(registryEntry2)) {
                counter.increment();
                return biome;
            } else {
                return registryEntry2;
            }
        };
    }

    protected static class ReferenceTerrainInfo {
        public int surfaceY;
        public BlockState[] blocks;
        public int[] heights;
        public BlockState[] aboveSurfaceBlocks;
        public int[] aboveSurfaceHeights;
    }

    protected static boolean isSolidBlock(World world,BlockState state) {
        return state.isSolidBlock(world, BlockPos.ORIGIN) &&
                !isWaterOrPlant(state);
    }

    protected static boolean isWaterOrPlant(BlockState state) {
        Block block = state.getBlock();

        if (state.getFluidState().isStill() || block == Blocks.BUBBLE_COLUMN || block == Blocks.CONDUIT) {
            return true;
        }
        String blockName = block.getTranslationKey().toLowerCase();
        if(blockName.contains("coral") &&
                (blockName.contains("fan") || blockName.contains("block") || blockName.contains("wall")))
        {
            return true;
        }

        return state.isIn(BlockTags.REPLACEABLE) ||
                state.isIn(BlockTags.LEAVES) ||
                state.isIn(BlockTags.FLOWERS) ||
                state.isIn(BlockTags.CROPS) ||
                state.isIn(BlockTags.LOGS)||
                state.isIn(BlockTags.MUSHROOM_GROW_BLOCK);
    }
}