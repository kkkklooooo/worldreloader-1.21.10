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



    protected  int width=20;
    protected  int len=100;
    protected  int currentLen=0;

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
            int chunkLen = len >> 4;  // 将长度转换为chunk单位
            int chunkWidth = width >> 4;  // 将宽度转换为chunk单位

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

    protected void processNextStep2() {
        if (currentLen > len) {
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§6地形改造完成！"), false);
            stop();
            return;
        }

        if (true) {
            currentRadiusPositions = generateLinePositions(currentStep);
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§7开始改造半径: " + currentLen + "格 (共 " + currentRadiusPositions.size() + " 个位置)"), false);
        }

        if (false) {
            cleanupItemEntities();
            lastCleanupRadius = currentRadius;
        }

        if (!processCurrentStepPositionsLine()) {
            return;
        }

        if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§e完成步骤: 半径 " + currentLen + "格 (" + (currentStep + 1) + "/" + totalSteps + ")"), false);
        currentStep++;

        if (true) {
            currentLen++;
            currentStep = 0;
            if(WorldReloader.config.Debug) player.sendMessage(net.minecraft.text.Text.literal("§a完成半径: " + (currentLen - 1) + "格"), false);
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

    protected boolean processCurrentStepPositionsLine() {

        for (BlockPos pos : currentRadiusPositions) {
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

    protected List<BlockPos> generateLinePositions(int radius) {
        List<BlockPos> positions = new ArrayList<>();

        if (currentLen == 0) {
            for (int i=-width;i<=width;i++){
                positions.add(new BlockPos(center.getX(), 0, center.getZ()+i));
            }

            return positions;
        }

        // 生成当前长度的边界位置（最远端的水平线）
        int x = center.getX() + currentLen;
        for (int dz = -width; dz <= width; dz++) {
            int z = center.getZ() + dz;
            positions.add(new BlockPos(x, 0, z));
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
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        // 获取目标区块
        Chunk chunk = serverWorld.getChunk(chunkX, chunkZ);

        // 计算区块内需要修改生物群系的区域（这里是整个区块）
        BlockBox chunkBox = BlockBox.create(
                new Vec3i(chunkX << 4, serverWorld.getBottomY(), chunkZ << 4),
                new Vec3i((chunkX << 4) + 15, serverWorld.getTopY(), (chunkZ << 4) + 15)
        );

        // 使用一个MutableInt来计数修改的方块数
        MutableInt modifiedCount = new MutableInt(0);

        // 创建生物群系供应商，这里filter直接设置为 biome -> true，表示替换所有原有生物群系
        BiomeSupplier biomeSupplier = createBiomeSupplier(modifiedCount, chunk, chunkBox, biome, b -> true);

        // 为区块设置新的生物群系
        chunk.populateBiomes(biomeSupplier, serverWorld.getChunkManager().getNoiseConfig().getMultiNoiseSampler());
        chunk.setNeedsSaving(true); // 标记区块需要保存

        // 发送更新包给客户端
        serverWorld.getChunkManager().threadedAnvilChunkStorage.sendChunkBiomePackets(List.of(chunk));

        WorldReloader.LOGGER.info("成功设置区块 [{}, {}] 的生物群系，修改了 {} 个方块", chunkX, chunkZ, modifiedCount.getValue());
    }

    // 保持你原有的 createBiomeSupplier 方法不变



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

    // 内部类
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
                //state.isIn(BlockTags.AIR)||
                state.isIn(BlockTags.LOGS)||
                state.isIn(BlockTags.MUSHROOM_GROW_BLOCK);
    }


}
