package com.cp.worldreloader;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.commands.FillBiomeCommand;
import net.minecraft.server.commands.FillCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.common.MinecraftForge;
import org.apache.commons.lang3.mutable.MutableInt;

import java.util.*;
import java.util.function.Predicate;

public abstract class BaseTransformationTask {
    protected final ServerLevel world;
    protected final BlockPos center;
    protected final BlockPos referenceCenter;
    protected final ServerPlayer player;
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

    protected List<BlockPos> currentRadiusPositions = new ArrayList<>();

    public BaseTransformationTask(ServerLevel world, BlockPos center, BlockPos referenceCenter,
                                  ServerPlayer player,
                                  int maxRadius, int totalSteps, int itemCleanupInterval) {
        this.world = world;
        this.center = center;
        this.referenceCenter = referenceCenter;
        this.player = player;
        this.minY = world.getMinBuildHeight();
        this.maxRadius = maxRadius;
        this.totalSteps = totalSteps;
        this.itemCleanupInterval = itemCleanupInterval;
        MinecraftForge.EVENT_BUS.register(this); // 注册到Forge事件总线
    }

    // 公共方法
    public void start() {
        this.isActive = true;
    }

    public void stop() {
        cleanupItemEntities();
        if(WorldReloader.config.Debug) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a最终物品清理完成！"));
        this.isActive = false;
        currentRadiusPositions.clear();
        currentStep = 0;
        lastCleanupRadius = -1;
        MinecraftForge.EVENT_BUS.unregister(this); // 从事件总线取消注册
    }

    protected abstract void processPosition(BlockPos circlePos);
    protected abstract ReferenceTerrainInfo getReferenceTerrainInfo(int referenceX, int referenceZ);
    protected abstract void copyFromReference(int targetX, int targetZ, ReferenceTerrainInfo referenceInfo);
    protected abstract boolean shouldSkipProcessing(int referenceSurfaceYAtTarget, int originalSurfaceY);

    // Forge版本的事件监听
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            if (this.isActive) {
                processNextStep();
                handleChunkForcing();
            } else if (isinit) {
                cleanupChunkForcing();
            }
        }
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
                            Holder<Biome> bb = getBiomeAtChunkCenter(world, chunkPos);
                            setBiome(center.offset(16*x, 0, 16*z), bb, world);
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

    public static Holder<Biome> getBiomeAtChunkCenter(Level world, ChunkPos chunkPos) {
        int centerX = chunkPos.getMinBlockX() + 8;
        int centerZ = chunkPos.getMinBlockZ() + 8;
        int topY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, centerX, centerZ);
        BlockPos topPos = new BlockPos(centerX, topY, centerZ);
        return world.getBiome(topPos);
    }

    protected void processNextStep() {
        if (currentRadius > maxRadius) {
            if(WorldReloader.config.Debug) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6地形改造完成！"));
            stop();
            return;
        }

        if (currentStep == 0) {
            currentRadiusPositions = generateCirclePositions(currentRadius);
            if(WorldReloader.config.Debug) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7开始改造半径: " + currentRadius + "格 (共 " + currentRadiusPositions.size() + " 个位置)"));
        }

        if (shouldCleanupItems()) {
            cleanupItemEntities();
            lastCleanupRadius = currentRadius;
        }

        if (!processCurrentStepPositions()) {
            return;
        }

        if(WorldReloader.config.Debug) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e完成步骤: 半径 " + currentRadius + "格 (" + (currentStep + 1) + "/" + totalSteps + ")"));
        currentStep++;

        if (currentStep >= totalSteps) {
            currentRadius++;
            currentStep = 0;
            if(WorldReloader.config.Debug) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a完成半径: " + (currentRadius - 1) + "格"));
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
            if(WorldReloader.config.Debug) player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a完成半径: " + (currentRadius - 1) + "格"));
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

        List<ItemEntity> itemsToRemove = new ArrayList<>(world.getEntitiesOfClass(
                ItemEntity.class,
                getCleanupBoundingBox(cleanupRadius),
                entity -> true));

        for (ItemEntity item : itemsToRemove) {
            item.discard();
            itemsCleared++;
        }

        if (itemsCleared > 0 && WorldReloader.config.Debug) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b清理了 " + itemsCleared + " 个掉落物（半径 " + currentRadius + "）"));
        }
    }

    protected AABB getCleanupBoundingBox(int radius) {
        int minX = center.getX() - radius;
        int minY = this.minY;
        int minZ = center.getZ() - radius;
        int maxX = center.getX() + radius;
        int maxY = 2000;
        int maxZ = center.getZ() + radius;
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
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

    public int validateAndAdjustHeight(Level world, int x, int z, int initialHeight, int minY) {
        int currentY = initialHeight;
        int solidGroundCount = 0;

        while (currentY > minY + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            BlockState state = world.getBlockState(pos);

            if (isSolidBlock(world, state)) {
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

    public static ReferenceTerrainInfo analyzeTerrain(Level world, int x, int z, int surfaceY, int minY, int copyDepth, int copyHeight) {
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

    public static void setBiome(BlockPos pos, Holder<Biome> biome, ServerLevel serverWorld) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        // 获取目标区块
        LevelChunk chunk = serverWorld.getChunk(chunkX, chunkZ);

        int minBiomeX = (chunk.getPos().getMinBlockX()) >> 2;
        int maxBiomeX = (chunk.getPos().getMaxBlockX()) >> 2;
        int minBiomeZ = (chunk.getPos().getMinBlockZ()) >> 2;
        int maxBiomeZ = (chunk.getPos().getMaxBlockZ()) >> 2;
        int minBiomeY = serverWorld.getMinSection() * 4;
        int maxBiomeY = (serverWorld.getMaxSection() * 4) - 1;

        MutableInt modifiedCount = new MutableInt(0);

        // 遍历区块内的所有生物群系位置
        for (int biomeX = minBiomeX; biomeX <= maxBiomeX; biomeX++) {
            for (int biomeZ = minBiomeZ; biomeZ <= maxBiomeZ; biomeZ++) {
                for (int biomeY = minBiomeY; biomeY <= maxBiomeY; biomeY++) {
                    // 获取当前生物群系
                    Holder<Biome> currentBiome = chunk.getNoiseBiome(biomeX, biomeY, biomeZ);

                    // 如果已经是目标生物群系，跳过
                    if (currentBiome.is(Objects.requireNonNull(biome.unwrapKey().orElse(null)))) {
                        continue;
                    }

                    // 设置新的生物群系
                    chunk.setBiome(biomeX, biomeY, biomeZ, biome);
                    modifiedCount.increment();
                }
            }
        }

        chunk.setUnsaved(true);

        // 发送更新包给客户端
        serverWorld.getChunkSource().chunkMap.getPlayers(chunk.getPos(), false)
                .forEach(p -> p.connection.send(
                        new ClientboundLevelChunkWithLightPacket(chunk, serverWorld.getChunkSource().getLightEngine(), null, null)));

        WorldReloader.LOGGER.info("成功设置区块 [{}, {}] 的生物群系，修改了 {} 个方块", chunkX, chunkZ, modifiedCount.getValue());
    }

    private static BlockPos convertPos(BlockPos pos) {
        return new BlockPos(convertCoordinate(pos.getX()), convertCoordinate(pos.getY()), convertCoordinate(pos.getZ()));
    }

    private static int convertCoordinate(int coordinate) {
        return coordinate & ~0b11; // Forge中不同的生物群系坐标转换
    }

    // 内部类
    protected static class ReferenceTerrainInfo {
        public int surfaceY;
        public BlockState[] blocks;
        public int[] heights;
        public BlockState[] aboveSurfaceBlocks;
        public int[] aboveSurfaceHeights;
    }

    protected static boolean isSolidBlock(Level world, BlockState state) {
        return state.isSolid() &&
                !isWaterOrPlant(state);
    }

    protected static boolean isWaterOrPlant(BlockState state) {
        Block block = state.getBlock();

        // 判断液体
        if (!state.getFluidState().isEmpty() || block == Blocks.BUBBLE_COLUMN || block == Blocks.CONDUIT) {
            return true;
        }
        String blockName = block.getDescriptionId().toLowerCase();
        if(blockName.contains("coral") &&
                (blockName.contains("fan") || blockName.contains("block") || blockName.contains("wall")))
        {
            return true;
        }

        // 使用标签系统判断植物
        return state.is(net.minecraft.tags.BlockTags.REPLACEABLE) ||
                state.is(net.minecraft.tags.BlockTags.LEAVES) ||
                state.is(net.minecraft.tags.BlockTags.FLOWERS) ||
                state.is(net.minecraft.tags.BlockTags.CROPS) ||
                state.is(net.minecraft.tags.BlockTags.LOGS) ||
                state.is(net.minecraft.tags.BlockTags.MUSHROOM_GROW_BLOCK);
    }
}
