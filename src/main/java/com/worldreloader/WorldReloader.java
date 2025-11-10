package com.worldreloader;

import com.mojang.datafixers.util.Pair;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.util.Objects;
import java.util.function.Predicate;

import static com.worldreloader.BaseTransformationTask.isSolidBlock;

public class WorldReloader implements ModInitializer {
	public static final String MOD_ID = "worldreloader";

	public static ModConfig config;
	public static ConfigHolder ch = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);



	@Override
	public void onInitialize() {




		LOGGER.info("World Reloader Initialized!");

		GuiRegistry registry = AutoConfig.getGuiRegistry(ModConfig.class);
//		registry.registerPredicateProvider(new StructureMappingGuiProvider(),
//				field -> "structureMappings".equals(field.getName()));
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient()) return ActionResult.PASS;

			ItemStack itemStack = player.getStackInHand(hand);
			BlockPos pos = hitResult.getBlockPos();

			if (world.getBlockState(pos).getBlock() == Blocks.BEACON &&
					itemStack.getItem() == Items.NETHER_STAR) {

				if (!player.isCreative()) {
					itemStack.decrement(1);
				}

				LOGGER.info("激活地形改造");
				Objects.requireNonNull(world.getServer()).execute(() -> {
					startTerrainTransformation((ServerWorld) world, pos, player);
				});

				return ActionResult.SUCCESS;
			}

			return ActionResult.PASS;
		});
		KeyBindings.register();

		config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KeyBindings.openConfigKey.wasPressed()) {
				if (client.player != null) {
					// 打开配置界面
					client.setScreen(AutoConfig.getConfigScreen(ModConfig.class, client.currentScreen).get());
				}
			}
		});

	}

//	private void openConfigScreen(MinecraftClient client) {
//		ConfigBuilder cb = ConfigBuilder.create().setParentScreen(client.currentScreen).setTitle(Text.of("CFG"));
//		client.setScreen();
//	}

	private void startTerrainTransformation(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		LOGGER.info("开始地形改造过程 - 信标位置: {}", beaconPos);

		Predicate<RegistryEntry<Biome>> targetBiome = detectTargetBiome(world, beaconPos, player);
		String targetStructure = detectTargetStructure(world, beaconPos, player);

		BlockPos referencePos;
		if (config.UseSpecificPos){

			 referencePos = new BlockPos(config.Posx,config.Posy,config.Posz);
			 if (referencePos==null){
				 player.sendMessage(Text.literal("你丫没设置目标点,用setPos命令设置!!!!"));
				 return;
			 }
			 referencePos.add(0,-referencePos.getY(),0);
		}else{
			 referencePos = findReferencePosition(world, beaconPos, targetBiome, targetStructure, player);
		}



		if (referencePos != null) {
			//两种改造模式
			if(config.UseSurface){
				new SurfaceTransformationTask(world,beaconPos,referencePos,player).start();
			}else {
				new TerrainTransformationTask(world, beaconPos, referencePos, player).start();
			}
			//new TerrainTransformationTask(world, beaconPos, referencePos, player).start();

			player.sendMessage(Text.literal("§a地形改造已启动！"), false);
			LOGGER.info("地形改造任务已启动 - 参考位置: {}", referencePos);
		} else {
			sendErrorMessage(player, targetBiome, targetStructure);
		}
	}

	private Predicate<RegistryEntry<Biome>> detectTargetBiome(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		BlockPos sidePos = beaconPos.east();
		Block sideBlock = world.getBlockState(sidePos).getBlock();

//		for (BlockToBiomeMapping mapping : BLOCK_TO_BIOME_MAPPINGS) {
//			if (mapping.block == sideBlock) {
//
//				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + mapping.biomeName + "生物群系"), false);
//				return mapping.biome;
//			}
//		}
		Predicate<RegistryEntry<Biome>> p;
		for (var i:config.biomeMappings){
			if(Registries.BLOCK.get(Identifier.of("minecraft",i.itemId))==sideBlock){
				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.BiomeId + "生物群系"), false);

				if (i.BiomeId.startsWith("#")) {
					String sp[]=i.BiomeId.substring(1).split(":");
					Identifier tagId = Identifier.of(sp[0]==""?"minecraft":sp[0],sp[1]); // "biome_tag_villagers:villager_jungle"
					TagKey<Biome> biomeTag = TagKey.of(RegistryKeys.BIOME, tagId);

					p = (entry) -> {
						return entry.isIn(biomeTag);
					};
				} else {
					String sp[]=i.BiomeId.substring(1).split(":");
					RegistryKey<Biome> k = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(sp[0]==""?"minecraft":sp[0],sp[1]));

					p = (entry) -> {
						return entry.matchesKey(k);
					};
				}
				return p;
				//return RegistryKey.of(RegistryKeys.BIOME,Identifier.of("minecraft",i.BiomeId));
			}
		}
		return null;
	}

	private String detectTargetStructure(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		BlockPos sidePos = beaconPos.east();
		Block sideBlock = world.getBlockState(sidePos).getBlock();

//		for (BlockToStructureMapping mapping : BLOCK_TO_STRUCTURE_MAPPINGS) {
//			if (mapping.block == sideBlock) {
//				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + mapping.structureName + "结构"), false);
//				return mapping.structureId;
//			}
//		}
		for (var i:config.structureMappings){
			if(Registries.BLOCK.get(Identifier.of("minecraft",i.itemId))==sideBlock){
				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.structureId + "结构"), false);
				return i.structureId;
			}
		}
		return null;
	}

	private BlockPos findReferencePosition(ServerWorld world, BlockPos center, Predicate<RegistryEntry<Biome>> targetBiome,
										   String targetStructure, net.minecraft.entity.player.PlayerEntity player) {
		if (targetStructure != null) {
			return findStructurePosition(world, center, targetStructure, player);
		} else if (targetBiome != null) {
			return findBiomePosition(world, center, targetBiome, player);
		} else {
			return findRandomPosition(world, center);
		}
	}

	private BlockPos findBiomePosition(ServerWorld world, BlockPos center, Predicate<RegistryEntry<Biome>> targetBiome,
									   net.minecraft.entity.player.PlayerEntity player) {
		try {
//			BlockPos biomePos = Objects.requireNonNull(world.locateBiome(
//					b -> b.matchesKey(targetBiome),
//					center, 6400, 8, 64
//			)).getFirst();
			Pair<BlockPos,RegistryEntry<Biome>>p = world.locateBiome(targetBiome,center,6400,32,64);
			if(p==null){
				player.sendMessage(Text.literal("无法找到结构,请尝试使用locate命令测试或检查拼写错误"),false);
			}
			BlockPos biomePos = p.getFirst();

			if (biomePos != null) {
				BlockPos surfacePos = getValidSurfacePosition(world, biomePos);
				if (surfacePos != null) {
					double distance = Math.sqrt(center.getSquaredDistance(surfacePos));
					player.sendMessage(Text.literal("§a成功找到目标生物群系，距离: " + String.format("%.1f", distance) + " 格"), false);
					return surfacePos;
				}
				return findAlternativeBiomePosition(world, biomePos, targetBiome);
			}
		} catch (Exception e) {
			LOGGER.error("查找生物群系时发生错误", e);
			player.sendMessage(Text.literal("§c查找生物群系时发生错误: " + e.getMessage()), false);
		}
		return null;
	}

	private BlockPos findStructurePosition(ServerWorld world, BlockPos center, String structureId,
										   net.minecraft.entity.player.PlayerEntity player) {
		try {
//			BlockPos structurePos = world.locateStructure(
//					net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE,
//							net.minecraft.util.Identifier.of(structureId)),
//					center, 10000, false
//			);
			String[] strings = structureId.split(":");
			String namespace;
			String path;
			if(strings.length==1)
			{
				namespace="minecraft";
				path=strings[0];
			}
			else
			{
				namespace=strings[0];
				path=strings[1];
			}
			var a = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(Identifier.of(namespace,path));
			Pair<BlockPos, RegistryEntry<Structure>> pair = world.getChunkManager().getChunkGenerator().locateStructure(world, RegistryEntryList.of(RegistryEntry.of(a)), center, 6400, false);
			//上面这个负责村庄外结构，村庄必须使用下面这个查询
			BlockPos structurePos;
			if(pair==null)
			{
				structurePos = world.locateStructure(
						net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE,
								net.minecraft.util.Identifier.of(namespace,path)),
						center, 6400, false
				);
			}
			else
			{
				structurePos=pair.getFirst();
			}
			if (structurePos != null) {
				BlockPos surfacePos = getValidSurfacePosition(world, structurePos);
				if (surfacePos != null) {
					double distance = Math.sqrt(center.getSquaredDistance(surfacePos));
					player.sendMessage(Text.literal("§a成功找到目标结构，距离: " + String.format("%.1f", distance) + " 格"), false);
					return surfacePos;
				}
			}
		} catch (Exception e) {
			LOGGER.error("查找结构时发生错误", e);
			player.sendMessage(Text.literal("§c查找结构时发生错误: " + e.getMessage()), false);
		}
		return null;
	}

	private BlockPos findRandomPosition(ServerWorld world, BlockPos center) {
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

	/**
	 * 获取有效的地面位置，确保找到的是真正的地面而不是树叶等非固体方块
	 */
	private BlockPos getValidSurfacePosition(ServerWorld world, BlockPos pos) {
		// 只在必要时强制加载单个区块
		ChunkPos chunkPos = new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4);

		if (!world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
			world.setChunkForced(chunkPos.x, chunkPos.z, true);
			try {
				Thread.sleep(10); // 短暂等待区块加载
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
		}

		try {
			// 使用 MOTION_BLOCKING 而不是 WORLD_SURFACE，这样可以避免把树叶当作地面
			int surfaceY = world.getTopY(Heightmap.Type.MOTION_BLOCKING, pos.getX(), pos.getZ());

			// 进一步验证找到的高度确实是固体地面
			surfaceY = validateAndAdjustSurfaceHeight(world, pos.getX(), pos.getZ(), surfaceY);

				BlockPos surfacePos = new BlockPos(pos.getX(), surfaceY, pos.getZ());
				BlockState surfaceBlock = world.getBlockState(surfacePos);

				// 检查是否为真正的固体方块
				if (isSolidBlock(world, surfaceBlock) &&
						surfacePos.getY()>=64) {
					return surfacePos;
				}

		} finally {
			// 取消强制加载
			world.setChunkForced(chunkPos.x, chunkPos.z, false);
		}

		return null;
	}

	/**
	 * 验证并调整表面高度，确保找到的是真正的地面
	 */
	private int validateAndAdjustSurfaceHeight(ServerWorld world, int x, int z, int initialHeight) {
		int currentY = initialHeight;

		// 向下搜索，直到找到真正的固体地面
		while (currentY > world.getBottomY() + 10) {
			BlockPos pos = new BlockPos(x, currentY, z);
			BlockState state = world.getBlockState(pos);

			// 如果是真正的固体地面方块
			if (isSolidBlock(world, state)) {
				return currentY;
			}

			// 如果是树叶、草等非固体方块，继续向下搜索
			currentY--;
		}

		return initialHeight; // 如果找不到更好的，返回原始高度
	}

	/**
	 * 判断方块是否为真正的固体地面方块
	 */


	private BlockPos findAlternativeBiomePosition(ServerWorld world, BlockPos center, Predicate<RegistryEntry<Biome>> targetBiome) {
		for (int i = 0; i < 10; i++) {
			int offsetX = world.random.nextInt(400) - 200;
			int offsetZ = world.random.nextInt(400) - 200;
			BlockPos testPos = center.add(offsetX, 0, offsetZ);

			if (targetBiome.test(world.getBiome(testPos))) {
				BlockPos surfacePos = getValidSurfacePosition(world, testPos);
				if (surfacePos != null) return surfacePos;
			}
		}
		return null;
	}

	private void sendErrorMessage(net.minecraft.entity.player.PlayerEntity player,
								  Predicate<RegistryEntry<Biome>> targetBiome, String targetStructure) {
		if (targetBiome != null) {
			player.sendMessage(Text.literal("§c群系查找出现问题！"), false);
		} else if (targetStructure != null) {
			player.sendMessage(Text.literal("§c结构查找出现问题！"), false);
		} else {
			player.sendMessage(Text.literal("§c随机查找出现问题！"), false);
		}
	}

	private record BlockToBiomeMapping(Block block, RegistryKey<Biome> biome, String biomeName) {}
	private record BlockToStructureMapping(Block block, String structureId, String structureName) {}

}