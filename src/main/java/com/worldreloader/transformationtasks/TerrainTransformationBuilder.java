package com.worldreloader.transformationtasks;

import com.mojang.datafixers.util.Pair;
import com.worldreloader.WorldReloader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.structure.Structure;

import java.util.function.Predicate;

import static com.mojang.text2speech.Narrator.LOGGER;
import static com.worldreloader.transformationtasks.BaseTransformationTask.isSolidBlock;

public class TerrainTransformationBuilder {


    ServerWorld world;
    BlockPos targetPos=null;
    BlockPos changePos=null;
    int radius=64;
    int padding=12;
    boolean isChangeBiome=true;
    int yMin=20;
    int yMax=30;
    int steps=3;
    int itemCleanupInterval=20;
    PlayerEntity player;
    public TerrainTransformationBuilder(ServerWorld world,PlayerEntity player)
    {
        this.world=world;
        this.player=player;
    }

    public TerrainTransformationBuilder changeBiome(boolean isChangeBiome)
    {
        this.isChangeBiome=isChangeBiome;
        return this;
    }
    public TerrainTransformationBuilder setItemCleanupInterval(int itemCleanupInterval)
    {
        this.itemCleanupInterval=itemCleanupInterval;
        return this;
    }
    public TerrainTransformationBuilder setRadius(int r)
    {
        this.radius=r;
        return this;
    }
    public TerrainTransformationBuilder setPadding(int padding)
    {
        this.padding=padding;
        return this;
    }
    public TerrainTransformationBuilder setRandomPos(BlockPos center,int randomRadius)
    {
        LOGGER.info("开始随机查找参考位置 - 中心: {}, 随机半径: {}", center, randomRadius);

        for (int i = 0; i < 20; i++) {
            double angle = world.random.nextDouble() * 2 * Math.PI;
            int distance = randomRadius + world.random.nextInt(500);

            int refX = center.getX() + (int) (Math.cos(angle) * distance);
            int refZ = center.getZ() + (int) (Math.sin(angle) * distance);
            BlockPos testPos = new BlockPos(refX, 0, refZ);

            BlockPos surfacePos = getValidSurfacePosition(testPos);
            if (surfacePos != null) {
                LOGGER.info("成功找到随机位置: {}", surfacePos);
                this.targetPos=surfacePos;
                return this;
            }
        }

        LOGGER.info("随机查找失败");
        return this;
    }
    public TerrainTransformationBuilder setBiomePos(BlockPos center,
                               Predicate<RegistryEntry<Biome>> targetBiome, int searchRadius)
    {
        try {
            Pair<BlockPos, RegistryEntry<Biome>> p = world.locateBiome(targetBiome, center, searchRadius, 32, 64);
            if (p == null) {
                player.sendMessage(Text.literal("§c无法找到目标生物群系，请尝试扩大搜索范围或检查生物群系名称"), false);
                return this;
            }

            BlockPos biomePos = p.getFirst();
            BlockPos surfacePos = getValidSurfacePosition(biomePos);
            if (surfacePos != null) {
                double distance = Math.sqrt(center.getSquaredDistance(surfacePos));
                player.sendMessage(Text.literal("§a成功找到目标生物群系，距离: " + String.format("%.1f", distance) + " 格"), false);
                this.targetPos=surfacePos;
                return this;
            }

            // 如果表面位置无效，尝试在生物群系内寻找替代位置
            surfacePos=findAlternativeBiomePosition(biomePos, targetBiome);
            if (surfacePos != null) {
                double distance = Math.sqrt(center.getSquaredDistance(surfacePos));
                player.sendMessage(Text.literal("§a成功找到目标生物群系，距离: " + String.format("%.1f", distance) + " 格"), false);
                this.targetPos=surfacePos;
                return this;
            }
        } catch (Exception e) {
            LOGGER.error("查找生物群系时发生错误", e);
            player.sendMessage(Text.literal("§c查找生物群系时发生错误: " + e.getMessage()), false);
        }
        return this;
    }
    public TerrainTransformationBuilder setStructurePos(BlockPos center, String structureId,
                                   int searchRadius)
    {
        //设置目标结构的坐标
        try {
            var structureRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            var structure = structureRegistry.get(Identifier.of(structureId));

            BlockPos structurePos = null;

            if (structure != null) {
                // 通过ID直接查找结构
                Pair<BlockPos, RegistryEntry<Structure>> pair = world.getChunkManager()
                        .getChunkGenerator()
                        .locateStructure(world, RegistryEntryList.of(RegistryEntry.of(structure)), center, searchRadius, false);
                if (pair != null) {
                    structurePos = pair.getFirst();
                }
            } else {
                // 通过标签查找结构
                structurePos = world.locateStructure(
                        net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE,
                                net.minecraft.util.Identifier.of(structureId)),
                        center, searchRadius, false
                );
            }

            if (structurePos != null) {
                BlockPos surfacePos = getValidSurfacePosition(structurePos);
                if (surfacePos != null) {
                    double distance = Math.sqrt(center.getSquaredDistance(surfacePos));
                    player.sendMessage(Text.literal("§a成功找到目标结构，距离: " + String.format("%.1f", distance) + " 格"), false);
                    this.targetPos=surfacePos;
                    return this;
                }
            } else {
                player.sendMessage(Text.literal("§c无法找到目标结构，请尝试使用locate命令测试或检查拼写错误"), false);
            }
        } catch (Exception e) {
            LOGGER.error("查找结构时发生错误", e);
            player.sendMessage(Text.literal("§c查找结构时发生错误: " + e.getMessage()), false);
        }
        return this;
    }
    public TerrainTransformationBuilder setTargetPos(int posX,int posY,int posZ)
    {
        this.targetPos=new BlockPos(posX,posY,posZ);
        //直接设置目标地形的改造中心位置
        return this;
    }
    public TerrainTransformationBuilder setTargetPos(BlockPos pos)
    {
        this.targetPos=pos;
        //直接设置目标地形的改造中心位置
        return this;
    }
    public TerrainTransformationBuilder setChangePos(int posX,int posY,int posZ)
    {
        this.changePos=new BlockPos(posX,posY,posZ);
        //设置要改造的位置
        return this;
    }
    public TerrainTransformationBuilder setChangePos(BlockPos pos)
    {
        this.changePos=pos;
        //设置要改造的位置
        return this;
    }
    public TerrainTransformationBuilder setSteps(int steps)
    {
        this.steps=steps;
        //设置改造每个半径需要多少帧
        return this;
    }
    public TerrainTransformationBuilder setYMin(int yMin)
    {
        this.yMin=yMin;
        //设置最低改造高度
        //在TerrainTask中，是绝对高度
        //在Surface中，为了节约性能，是相对高度
        return this;
    }
    public TerrainTransformationBuilder setYMax(int yMax)
    {
        this.yMax=yMax;
        //设置最高相对改造高度
        return this;
    }
    public TerrainTransformationTask buildStandard()
    {
        if(isValidated())
        {
            return new TerrainTransformationTask(this);
        }
        LOGGER.error("构建失败！");
        return null;
    }
    public SurfaceTransformationTask buildSurface()
    {
        if(isValidated())
        {
            return new SurfaceTransformationTask(this);
        }
        LOGGER.error("构建失败！");
        return null;
    }
    private boolean isValidated()
    {

        boolean res=radius>0&&targetPos!=null&&changePos!=null;
        if(!this.targetPos.isWithinDistance(this.changePos,radius*2)){
            WorldReloader.LOGGER.error("Within!");
            player.sendMessage(Text.literal("失败:目标点和改造点距离过近"),false);
            return false;
        }
        return res;
    }
    private BlockPos getValidSurfacePosition(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);

        if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
            world.setChunkForced(chunkPos.x, chunkPos.z, true);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        try {
            int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());
            surfaceY = validateAndAdjustSurfaceHeight(pos.getX(), pos.getZ(), surfaceY);

            BlockPos surfacePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
            BlockState surfaceBlock = world.getBlockState(surfacePos);

            if (isSolidBlock(world, surfaceBlock) && surfacePos.getY() >= 64) {
                return surfacePos;
            }

        } finally {
            world.setChunkForced(chunkPos.x, chunkPos.z, false);
        }

        return null;
    }
    private int validateAndAdjustSurfaceHeight(int x, int z, int initialHeight) {
        int currentY = initialHeight;

        while (currentY > world.getBottomY() + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            BlockState state = world.getBlockState(pos);

            if (isSolidBlock(world, state)) {
                return currentY;
            }

            currentY--;
        }

        return initialHeight;
    }
    private BlockPos findAlternativeBiomePosition(BlockPos center,
                                                  Predicate<RegistryEntry<Biome>> targetBiome) {
        for (int i = 0; i < 10; i++) {
            int offsetX = world.random.nextInt(400) - 200;
            int offsetZ = world.random.nextInt(400) - 200;
            BlockPos testPos = center.add(offsetX, 0, offsetZ);

            if (targetBiome.test(world.getBiome(testPos))) {
                BlockPos surfacePos = getValidSurfacePosition(testPos);
                if (surfacePos != null) return surfacePos;
            }
        }
        return null;
    }
}
