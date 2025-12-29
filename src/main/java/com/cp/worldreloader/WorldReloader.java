package com.cp.worldreloader;

import com.mojang.datafixers.util.Pair;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.Predicate;

import static com.cp.worldreloader.BaseTransformationTask.isSolidBlock;


@Mod(WorldReloader.MOD_ID)
public class WorldReloader {
    public static final String MOD_ID = "worldreloader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static ModConfig config;
    public static ConfigHolder<ModConfig> ch;

    public WorldReloader() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // 注册配置
        ch = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
        config = ch.getConfig();

        // 注册事件监听器
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);

        // 注册Forge事件总线
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("World Reloader Initialized!");
    }
    private void onClientSetup(final FMLClientSetupEvent event) {
        // 客户端设置，不需要再注册tick事件
        // 按键事件现在在ClientEvents类中处理
        LOGGER.info("World Reloader Client Setup Complete!");
    }


    @SubscribeEvent
    public void onBlockUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;

        Level world = event.getLevel();
        Player player = event.getEntity();
        ItemStack itemStack = event.getItemStack();
        BlockPos pos = event.getPos();

        if (world.getBlockState(pos).getBlock() == Blocks.BEACON &&
                itemStack.getItem() == Items.NETHER_STAR) {

            if (!player.isCreative()) {
                itemStack.shrink(1);
            }

            LOGGER.info("激活地形改造");
            world.getServer().execute(() -> {
                startTerrainTransformation((ServerLevel) world, pos, player);
            });

            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }



    private void startTerrainTransformation(ServerLevel world, BlockPos beaconPos, Player player) {
        LOGGER.info("开始地形改造过程 - 信标位置: {}", beaconPos);

        Predicate<Holder<Biome>> targetBiome = detectTargetBiome(world, beaconPos, player);
        String targetStructure = detectTargetStructure(world, beaconPos, player);

        BlockPos referencePos;
        if (config.UseSpecificPos) {
            referencePos = new BlockPos(config.Posx, config.Posy, config.Posz);
            if (referencePos == null) {
                player.sendSystemMessage(Component.literal("你丫没设置目标点,用setPos命令设置!!!!"));
                return;
            }
            referencePos = referencePos.atY(0); // 将Y坐标设为0
        } else {
            referencePos = findReferencePosition(world, beaconPos, targetBiome, targetStructure, player);
        }

        if (referencePos != null) {
            // 两种改造模式
            if (config.UseSurface) {
                new SurfaceTransformationTask(world, beaconPos, referencePos, player).start();
            } else {
                new TerrainTransformationTask(world, beaconPos, referencePos, player).start();
            }

            player.sendSystemMessage(Component.literal("§a地形改造已启动！"));
            LOGGER.info("地形改造任务已启动 - 参考位置: {}", referencePos);
        } else {
            sendErrorMessage(player, targetBiome, targetStructure);
        }
    }

    private Predicate<Holder<Biome>> detectTargetBiome(ServerLevel world, BlockPos beaconPos, Player player) {
        BlockPos sidePos = beaconPos.east();
        Block sideBlock = world.getBlockState(sidePos).getBlock();

        for (var i : config.biomeMappings) {
            if (world.registryAccess().registryOrThrow(Registries.BLOCK)
                    .get(new ResourceLocation(i.itemId)) == sideBlock) {
                player.sendSystemMessage(Component.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.BiomeId + "生物群系"));

                if (i.BiomeId.startsWith("#")) {
                    ResourceLocation tagId = new ResourceLocation(i.BiomeId.substring(1));
                    TagKey<Biome> biomeTag = TagKey.create(Registries.BIOME, tagId);

                    return (holder) -> holder.is(biomeTag);
                } else {
                    ResourceKey<Biome> biomeKey = ResourceKey.create(Registries.BIOME,
                            new ResourceLocation(i.BiomeId));

                    return (holder) -> holder.is(biomeKey);
                }
            }
        }
        return null;
    }

    private String detectTargetStructure(ServerLevel world, BlockPos beaconPos, Player player) {
        BlockPos sidePos = beaconPos.east();
        Block sideBlock = world.getBlockState(sidePos).getBlock();

        for (var i : config.structureMappings) {
            if (world.registryAccess().registryOrThrow(Registries.BLOCK)
                    .get(new ResourceLocation(i.itemId)) == sideBlock) {
                player.sendSystemMessage(Component.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.structureId + "结构"));
                return i.structureId;
            }
        }
        return null;
    }

    private BlockPos findReferencePosition(ServerLevel world, BlockPos center,
                                           Predicate<Holder<Biome>> targetBiome,
                                           String targetStructure, Player player) {
        if (targetStructure != null) {
            return findStructurePosition(world, center, targetStructure, player);
        } else if (targetBiome != null) {
            return findBiomePosition(world, center, targetBiome, player);
        } else {
            return findRandomPosition(world, center);
        }
    }

    private BlockPos findBiomePosition(ServerLevel world, BlockPos center,
                                       Predicate<Holder<Biome>> targetBiome, Player player) {
        try {
            Pair<BlockPos, Holder<Biome>> p = world.findClosestBiome3d(
                    targetBiome, center, 6400, 32, 64);

            if (p == null) {
                player.sendSystemMessage(Component.literal("无法找到结构,请尝试使用locate命令测试或检查拼写错误"));
                return null;
            }

            BlockPos biomePos = p.getFirst();
            if (biomePos != null) {
                BlockPos surfacePos = getValidSurfacePosition(world, biomePos);
                if (surfacePos != null) {
                    double distance = Math.sqrt(center.distSqr(surfacePos));
                    player.sendSystemMessage(Component.literal("§a成功找到目标生物群系，距离: " + String.format("%.1f", distance) + " 格"));
                    return surfacePos;
                }
                BlockPos res = findAlternativeBiomePosition(world, biomePos, targetBiome);
                if (res == null) {
                    player.sendSystemMessage(Component.literal("无法找到目标群落,请考虑降低目标最低高度"));
                }
                return res;
            }
        } catch (Exception e) {
            LOGGER.error("查找生物群系时发生错误: {}", e.getMessage());
            player.sendSystemMessage(Component.literal("§c查找生物群系时发生错误: " + e.getMessage()));
        }
        return null;
    }

    private BlockPos findStructurePosition(ServerLevel world, BlockPos center,
                                           String structureId, Player player) {
        try {
            String[] strings = structureId.split(":");
            String namespace = strings.length == 1 ? "minecraft" : strings[0];
            String path = strings.length == 1 ? strings[0] : strings[1];

            ResourceLocation structureLoc = new ResourceLocation(namespace, path);
            ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, structureLoc);

            Pair<BlockPos, Holder<Structure>> pair = world.getChunkSource().getGenerator()
                    .findNearestMapStructure(world,
                            world.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(structureKey).get(),
                            center, 6400, false);

            BlockPos structurePos;
            if (pair == null) {
                // 尝试使用标签查找（适用于村庄等）
                TagKey<Structure> structureTag = TagKey.create(Registries.STRUCTURE, structureLoc);
                structurePos = world.findNearestMapStructure(structureTag, center, 6400, false);
            } else {
                structurePos = pair.getFirst();
            }

            if (structurePos != null) {
                BlockPos surfacePos = getValidSurfacePosition(world, structurePos);
                if (surfacePos != null) {
                    double distance = Math.sqrt(center.distSqr(surfacePos));
                    player.sendSystemMessage(Component.literal("§a成功找到目标结构，距离: " + String.format("%.1f", distance) + " 格"));
                    return surfacePos;
                }
            }
        } catch (Exception e) {
            LOGGER.error("查找结构时发生错误", e);
            player.sendSystemMessage(Component.literal("§c查找结构时发生错误: " + e.getMessage()));
        }
        return null;
    }

    private BlockPos findRandomPosition(ServerLevel world, BlockPos center) {
        LOGGER.info("开始随机查找参考位置 - 中心: {}", center);

        for (int i = 0; i < 20; i++) {
            double angle = world.random.nextDouble() * 2 * Math.PI;
            int distance = 2000 + world.random.nextInt(500);

            int refX = center.getX() + (int)(Math.cos(angle) * distance);
            int refZ = center.getZ() + (int)(Math.sin(angle) * distance);
            BlockPos testPos = new BlockPos(refX, 0, refZ);

            BlockPos surfacePos = getValidSurfacePosition(world, testPos);
            if (surfacePos != null) {
                LOGGER.info("成功找到随机位置: {}", surfacePos);
                return surfacePos;
            }
        }

        LOGGER.info("随机查找失败");
        return null;
    }

    private BlockPos getValidSurfacePosition(ServerLevel world, BlockPos pos) {
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        if (!world.hasChunk(chunkX, chunkZ)) {
            world.setChunkForced(chunkX, chunkZ, true);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }

        try {
            // 使用MOTION_BLOCKING而不是WORLD_SURFACE
            int surfaceY = world.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());

            // 进一步验证找到的高度确实是固体地面
            surfaceY = validateAndAdjustSurfaceHeight(world, pos.getX(), pos.getZ(), surfaceY);

            BlockPos surfacePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
            BlockState surfaceBlock = world.getBlockState(surfacePos);

            // 检查是否为真正的固体方块
            if (isSolidBlock(world, surfaceBlock) && (surfacePos.getY() >= config.targetYmin)) {
                return surfacePos;
            }
        } finally {
            // 取消强制加载
            world.setChunkForced(chunkX, chunkZ, false);
        }

        return null;
    }

    private int validateAndAdjustSurfaceHeight(ServerLevel world, int x, int z, int initialHeight) {
        int currentY = initialHeight;

        // 向下搜索，直到找到真正的固体地面
        while (currentY > world.getMinBuildHeight() + 10) {
            BlockPos pos = new BlockPos(x, currentY, z);
            BlockState state = world.getBlockState(pos);

            // 如果是真正的固体地面方块
            if (isSolidBlock(world, state)) {
                return currentY;
            }

            // 如果是树叶、草等非固体方块，继续向下搜索
            currentY--;
        }

        return initialHeight;
    }

    private BlockPos findAlternativeBiomePosition(ServerLevel world, BlockPos center,
                                                  Predicate<Holder<Biome>> targetBiome) {
        for (int i = 0; i < 10; i++) {
            int offsetX = world.random.nextInt(400) - 200;
            int offsetZ = world.random.nextInt(400) - 200;
            BlockPos testPos = center.offset(offsetX, 0, offsetZ);

            if (targetBiome.test(world.getBiome(testPos))) {
                BlockPos surfacePos = getValidSurfacePosition(world, testPos);
                if (surfacePos != null) return surfacePos;
            }
        }
        return null;
    }

    private void sendErrorMessage(Player player, Predicate<Holder<Biome>> targetBiome,
                                  String targetStructure) {
        if (targetBiome != null) {
            player.sendSystemMessage(Component.literal("§c群系查找出现问题！"));
        } else if (targetStructure != null) {
            player.sendSystemMessage(Component.literal("§c结构查找出现问题！"));
        } else {
            player.sendSystemMessage(Component.literal("§c随机查找出现问题！"));
        }
    }
}