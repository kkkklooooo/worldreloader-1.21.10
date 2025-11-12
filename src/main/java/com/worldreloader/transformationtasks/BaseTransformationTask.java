package com.worldreloader.transformationtasks;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import com.worldreloader.WorldReloader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.FillBiomeCommand;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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
    protected  boolean isChangeBiome=true;

    protected boolean UseBreakLimitTopY=false;
    protected int LimitYFromBeacon=-10;
    protected List<BlockPos> currentRadiusPositions = new ArrayList<>();

    protected BaseTransformationTask(ServerWorld world, BlockPos center, BlockPos referenceCenter,
                                  net.minecraft.entity.player.PlayerEntity player,
                                  int maxRadius, int totalSteps, int itemCleanupInterval,boolean isChangeBiome,int LimitYFromBeacon,boolean UseBreakLimitTopY) {
        this.world = world;
        this.center = center;
        this.referenceCenter = referenceCenter;
        this.player = player;
        this.minY = world.getBottomY();
        this.maxRadius = maxRadius;
        this.totalSteps = totalSteps;
        this.itemCleanupInterval = itemCleanupInterval;
        registerToTick();
        this.isChangeBiome=isChangeBiome;
        this.LimitYFromBeacon=LimitYFromBeacon;
        this.UseBreakLimitTopY = UseBreakLimitTopY;
    }

    // 公共方法
    public void start() {
        WorldReloader.SetLocker(true);
        this.isActive = true;
    }

    public void stop() {
        //cleanupItemEntities();
        if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§a最终物品清理完成！"), false);
        this.isActive = false;
        WorldReloader.SetLocker(false);
        currentRadiusPositions.clear();
        currentStep = 0;
        lastCleanupRadius = -1;
    }

    protected abstract void processPosition(BlockPos circlePos);
    protected abstract ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ);
    protected abstract void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo);
    protected abstract boolean shouldSkipProcessing(int referenceSurfaceYAtTarget, int originalSurfaceY);


    protected void registerToTick() {
        ServerTickEvents.END_SERVER_TICK.register((MinecraftServer server) -> {
            if (this.isActive) {
                processNextStep();
                handleChunkForcing();
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
                        if(isChangeBiome) {
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

    protected boolean shouldCleanupItems() {
        return currentRadius % itemCleanupInterval == 0 && currentRadius != lastCleanupRadius;
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

    protected void cleanupItemEntities() {
        int itemsCleared = 0;
        int cleanupRadius = Math.min(currentRadius + 10, maxRadius + 20);

        List<ItemEntity> itemsToRemove = new ArrayList<>(world.getEntitiesByClass(
                ItemEntity.class,
                getCleanupBoundingBox(cleanupRadius),
                entity -> true));

        for (ItemEntity item : itemsToRemove) {
            item.discard();
            itemsCleared++;
        }

        if (itemsCleared > 0 && WorldReloader.config.Debug) {
            player.sendMessage(net.minecraft.text.Text.literal("§b清理了 " + itemsCleared + " 个掉落物（半径 " + currentRadius + "）"), false);
        }
    }

    protected net.minecraft.util.math.Box getCleanupBoundingBox(int radius) {
        int minX = center.getX() - radius;
        int minY = this.minY;
        int minZ = center.getZ() - radius;
        int maxX = center.getX() + radius;
        int maxY = 2000;
        int maxZ = center.getZ() + radius;
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
        // 获取坐标所属区块的起始坐标（区块坐标转换为世界坐标）
        int chunkX = pos.getX() >> 4; // 除以16得到区块坐标
        int chunkZ = pos.getZ() >> 4;
        BlockPos chunkStartPos = new BlockPos(chunkX << 4, serverWorld.getBottomY(), chunkZ << 4); // 乘以16得到世界坐标

        // 计算区块的结束坐标（一个区块是16x16，高度从世界底部到顶部）
        BlockPos chunkEndPos = new BlockPos(
                chunkStartPos.getX() + 15,
                serverWorld.getHeight(),
                chunkStartPos.getZ() + 15
        );

        WorldReloader.LOGGER.warn("区块范围: {} 到 {}", chunkStartPos.toShortString(), chunkEndPos.toShortString());

        int worldBottomY = serverWorld.getBottomY();
        int worldTopY = serverWorld.getHeight();
        int totalHeight = worldTopY - worldBottomY;

        // 计算每次处理的高度层（确保每次不超过3000个方块）
        // 每个水平面有 16 * 16 = 256 个方块
        int maxBlocksPerCall = 32768;
        int maxHeightPerCall = maxBlocksPerCall / 256; // 每次最多处理的高度层数

        WorldReloader.LOGGER.info("世界高度范围: {}-{}, 总高度: {}, 每次处理高度: {}",
                worldBottomY, worldTopY, totalHeight, maxHeightPerCall);

        // 按高度分层处理
        for (int startY = worldBottomY; startY < worldTopY; startY += maxHeightPerCall) {
            int endY = Math.min(startY + maxHeightPerCall - 1, worldTopY - 1);

            BlockPos layerStartPos = new BlockPos(chunkStartPos.getX(), startY, chunkStartPos.getZ());
            BlockPos layerEndPos = new BlockPos(chunkEndPos.getX(), endY, chunkEndPos.getZ());

            int layerHeight = endY - startY + 1;
            int blocksInThisLayer = 256 * layerHeight;

            WorldReloader.LOGGER.info("处理高度层: {}-{}, 方块数: {}", startY, endY, blocksInThisLayer);

            // 使用FillBiomeCommand设置当前高度层的生物群系
            Either<Integer, CommandSyntaxException> either = FillBiomeCommand.fillBiome(
                    serverWorld,
                    layerStartPos,
                    layerEndPos,
                    biome
            );

            if (either.right().isPresent()) {
                CommandSyntaxException error = either.right().get();
                WorldReloader.LOGGER.error("设置生物群系失败 (高度层 {}-{}): {}", startY, endY, error.getMessage());
                // 可以选择继续处理其他层，或者抛出异常
                // throw this.createError("test.error.set_biome: " + error.getMessage());
            } else {
                Integer modifiedCount = either.left().orElse(0);
                WorldReloader.LOGGER.info("成功设置高度层 {}-{} 的生物群系，修改计数: {}", startY, endY, modifiedCount);
            }
        }

        WorldReloader.LOGGER.info("生物群系设置完成");
    }

    // 内部类
    protected static class ReferenceTerrainInfo {
        public int surfaceY;
        public BlockState[] blocks;
        public int[] heights;
        public BlockState[] aboveSurfaceBlocks;
        public int[] aboveSurfaceHeights;
    }
    public static boolean isSolidBlock(World world, BlockState state) {
        return state.isSolidBlock(world, BlockPos.ORIGIN) &&
                !isWaterOrPlant(state);
    }
    protected static boolean isWaterOrPlant(BlockState state) {
        Block block = state.getBlock();

        // 判断液体
        if (state.getFluidState().isStill() || block == Blocks.BUBBLE_COLUMN || block == Blocks.CONDUIT) {
            return true;
        }
        String blockName = block.getTranslationKey().toLowerCase();
        if(blockName.contains("coral") &&
                (blockName.contains("fan") || blockName.contains("block") || blockName.contains("wall")))
        {
            return true;
        }

        // 使用标签系统判断植物
        return state.isIn(BlockTags.REPLACEABLE) ||
                state.isIn(BlockTags.LEAVES) ||
                state.isIn(BlockTags.FLOWERS) ||
                state.isIn(BlockTags.CROPS) ||
                state.isIn(BlockTags.AIR)||
                state.isIn(BlockTags.LOGS)||
                state.isIn(BlockTags.MUSHROOM_GROW_BLOCK);
    }


}
