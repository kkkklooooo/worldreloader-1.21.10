package com.worldreloader.transformationtasks;

import com.worldreloader.WorldReloader;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.biome.source.BiomeSupplier;
import net.minecraft.world.chunk.Chunk;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;


public abstract class BaseTransformationTask {
    protected final ServerWorld world;
    protected final BlockPos center;
    protected final BlockPos referenceCenter;
    protected final net.minecraft.entity.player.PlayerEntity player;
    protected Set<ChunkPos> forcedTargetChunks = new HashSet<>();
    protected Set<ChunkPos> forcedReferenceChunks = new HashSet<>();
    protected final ServerWorld targetDimensionWorld;

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
    protected boolean preserveBeacon=true;

    protected List<BlockPos> currentRadiusPositions = new ArrayList<>();

    protected BaseTransformationTask(ServerWorld world, BlockPos center, BlockPos referenceCenter,
                                     net.minecraft.entity.player.PlayerEntity player, ServerWorld targetDimensionWorld,
                                     int maxRadius, int totalSteps, int itemCleanupInterval, boolean isChangeBiome, boolean preserveBeacon) {
        this.world = world;
        this.center = center;
        this.referenceCenter = referenceCenter;
        this.player = player;
        this.minY = world.getBottomY();
        this.targetDimensionWorld = targetDimensionWorld;
        this.maxRadius = maxRadius;
        this.totalSteps = totalSteps;
        this.itemCleanupInterval = itemCleanupInterval;
        registerToTick();
        this.isChangeBiome=isChangeBiome;
        this.preserveBeacon=preserveBeacon;
    }

    // 公共方法
    public void start() {

        this.isActive = true;
    }

    public void stop() {
        //cleanupItemEntities();
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
                handleChunkForcing();
                processNextStep();
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
                    ChunkPos targetChunkPos = new ChunkPos((center.getX() >> 4) + x, (center.getZ() >> 4) + z);
                    ChunkPos referenceChunkPos = new ChunkPos((referenceCenter.getX() >> 4) + x, (referenceCenter.getZ() >> 4) + z);

                    if (!forcedTargetChunks.contains(targetChunkPos)) {
                        forceChunk(world, targetChunkPos);
                        forcedTargetChunks.add(targetChunkPos);
                    }

                    if (targetDimensionWorld != null && !forcedReferenceChunks.contains(referenceChunkPos)) {
                        forceChunk(targetDimensionWorld, referenceChunkPos);
                        forcedReferenceChunks.add(referenceChunkPos);
                    }

                    if (isChangeBiome && targetDimensionWorld != null) {
                        RegistryEntry<Biome> bb = getBiomeAtChunkCenter(targetDimensionWorld, referenceChunkPos);
                        setBiome(center.add(16 * x, 0, 16 * z), bb, world);
                    }
                }
            }
            isinit = true;
        }
    }

    protected boolean ensureChunkLoaded(ServerWorld serverWorld, int chunkX, int chunkZ) {
        if (serverWorld == null) {
            return false;
        }

        serverWorld.getChunk(chunkX, chunkZ);
        return serverWorld.isChunkLoaded(chunkX, chunkZ);
    }

    private void forceChunk(ServerWorld serverWorld, ChunkPos chunkPos) {
        serverWorld.setChunkForced(chunkPos.x, chunkPos.z, true);
        // Synchronously load the chunk before heightmap/block reads.
        serverWorld.getChunk(chunkPos.x, chunkPos.z);
    }

    protected boolean ensureColumnChunksLoaded(int targetX, int targetZ, int referenceX, int referenceZ) {
        return ensureChunkLoaded(world, targetX >> 4, targetZ >> 4)
                && ensureChunkLoaded(targetDimensionWorld, referenceX >> 4, referenceZ >> 4);
    }

    protected void cleanupChunkForcing() {
        if(targetDimensionWorld!=null){
            for (ChunkPos chunkPos : forcedReferenceChunks) {
                targetDimensionWorld.setChunkForced(chunkPos.x, chunkPos.z, false);
            }
        }
        for (ChunkPos chunkPos : forcedTargetChunks) {
            world.setChunkForced(chunkPos.x, chunkPos.z, false);
        }
        forcedReferenceChunks.clear();
        forcedTargetChunks.clear();
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
        if(!preserveBeacon)
        {
            return false;
        }
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

    // 在 analyzeTerrain 方法中，需要传入目标维度世界
    public static ReferenceTerrainInfo analyzeTerrain(World world, int x, int z, int surfaceY, int minY, int copyDepth, int copyHeight) {
        // 这个方法现在应该使用 targetDimensionWorld
        // 注意：这个方法是静态的，需要在实例方法中调用时传入正确的世界
        // 保持现有实现，但调用时需要传入目标维度世界
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
    protected int getTargetWorldSurfaceY(int x, int z) {
        return targetDimensionWorld.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
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

    private static BiomeSupplier createBiomeSupplier(MutableInt counter, Chunk chunk, BlockBox box, RegistryEntry<Biome> biome, Predicate<RegistryEntry<Biome>> filter) {
        return (x, y, z, noise) -> {
            int i = BiomeCoords.toBlock(x);
            int j = BiomeCoords.toBlock(y);
            int k = BiomeCoords.toBlock(z);
            RegistryEntry<Biome> registryEntry2 = chunk.getBiomeForNoiseGen(x, y, z);
            if (box.contains(i, j, k) && filter.test(registryEntry2)) {
                counter.increment();
                return biome;
            }
            return registryEntry2;
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
                state.isAir() ||
                state.isIn(BlockTags.LOGS)||
                state.isIn(BlockTags.MUSHROOM_GROW_BLOCK);
    }


}
