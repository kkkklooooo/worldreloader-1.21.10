package com.worldreloader;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Pair;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.ServerCommandSource;
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
import static net.minecraft.item.Items.register;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class WorldReloader implements ModInitializer {
	public static final String MOD_ID = "worldreloader";

	public static ModConfig config;
	public static ConfigHolder ch = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public String minPermission="op";
	private static boolean isLock=false;

	public static final Item CUSTOM_ITEM = new CrystalItem(new Item.Settings());

	public static void SetLocker(boolean isLock1){
		isLock=isLock1;
	}

	@Override
	public void onInitialize() {
//		final RegistryKey<Item> registryKey = RegistryKey.of(RegistryKeys.ITEM, Identifier.of(MOD_ID, "crystal"));
//		Registry.register(Registries.ITEM,registryKey,CUSTOM_ITEM);
		Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "crystal"), CUSTOM_ITEM);
		LOGGER.info("World Reloader Initialized!");
//        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(content -> {
//            content.add(CUSTOM_ITEM);
//        });
		// 注册指令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// 设置权限指令
			dispatcher.register(literal("worldreloader")
					.then(literal("setPermission")
							.requires(source -> source.hasPermissionLevel(3)) // 需要管理员权限
							.then(argument("permission", StringArgumentType.word())
									.suggests((context, builder) -> {
										builder.suggest("player");
										builder.suggest("op");
										builder.suggest("disabled");
										return builder.buildFuture();
									})
									.executes(context -> {
										String permission = StringArgumentType.getString(context, "permission");
										return setPermissionCommand(context.getSource(), permission);
									})
							)
					)
					.then(literal("transform")
							.then(argument("x", IntegerArgumentType.integer())
									.then(argument("y", IntegerArgumentType.integer())
											.then(argument("z", IntegerArgumentType.integer())
													.then(literal("biome")
															.then(argument("biomeName", StringArgumentType.greedyString())
																	.executes(context -> {
																		int x = IntegerArgumentType.getInteger(context, "x");
																		int y = IntegerArgumentType.getInteger(context, "y");
																		int z = IntegerArgumentType.getInteger(context, "z");
																		String biomeName = StringArgumentType.getString(context, "biomeName");
																		return transformAtCommand(context.getSource(), x, y, z, "biome", biomeName);
																	})
															)
													)
													.then(literal("structure")
															.then(argument("structureName", StringArgumentType.greedyString())
																	.executes(context -> {
																		int x = IntegerArgumentType.getInteger(context, "x");
																		int y = IntegerArgumentType.getInteger(context, "y");
																		int z = IntegerArgumentType.getInteger(context, "z");
																		String structureName = StringArgumentType.getString(context, "structureName");
																		return transformAtCommand(context.getSource(), x, y, z, "structure", structureName);
																	})
															)
													)
													.then(literal("random")
															.executes(context -> {
																int x = IntegerArgumentType.getInteger(context, "x");
																int y = IntegerArgumentType.getInteger(context, "y");
																int z = IntegerArgumentType.getInteger(context, "z");
																return transformAtCommand(context.getSource(), x, y, z, "random", null);
															})
													)
											)
									)
							)
					)
			);

			// 简化版指令，玩家可以使用
			dispatcher.register(literal("biometransform")
					.then(literal("biome")
							.then(argument("biomeName", StringArgumentType.greedyString())
									.executes(context -> {
										String biomeName = StringArgumentType.getString(context, "biomeName");
										return transformPlayerPositionCommand(context.getSource(), "biome", biomeName);
									})
							)
					)
					.then(literal("structure")
							.then(argument("structureName", StringArgumentType.greedyString())
									.executes(context -> {
										String structureName = StringArgumentType.getString(context, "structureName");
										return transformPlayerPositionCommand(context.getSource(), "structure", structureName);
									})
							)
					)
					.then(literal("random")
							.executes(context -> transformPlayerPositionCommand(context.getSource(), "random", null))
					)
			);
		});

		GuiRegistry registry = AutoConfig.getGuiRegistry(ModConfig.class);

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient()) return ActionResult.PASS;

			// 检查权限

			ItemStack itemStack = player.getStackInHand(hand);
			BlockPos pos = hitResult.getBlockPos();

			if (world.getBlockState(pos).getBlock() == Blocks.BEACON &&
					itemStack.getItem() == CUSTOM_ITEM) {
				if (!checkPermission(player)) {
					player.sendMessage(Text.literal("§c你没有权限使用地形改造功能！"), false);
					return ActionResult.FAIL;
				}

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
					client.setScreen(AutoConfig.getConfigScreen(ModConfig.class, client.currentScreen).get());
				}
			}
		});
	}

	/**
	 * 检查玩家权限
	 */
	private boolean checkPermission(net.minecraft.entity.player.PlayerEntity player) {
		String permission = minPermission;

		if ("disabled".equals(permission)) {
			return false;
		} else if ("op".equals(permission)) {
			return player.hasPermissionLevel(2); // OP权限
		} else { // "player"
			return true; // 所有玩家都可以使用
		}
	}

	/**
	 * 设置权限指令
	 */
	private int setPermissionCommand(ServerCommandSource source, String permission) {
		if (!permission.equals("player") && !permission.equals("op") && !permission.equals("disabled")) {
			source.sendError(Text.literal("§c无效的权限等级！可用选项: player, op, disabled"));
			return 0;
		}

		minPermission = permission;
		ch.save();

		source.sendMessage(Text.literal("§a地形改造权限已设置为: " + permission));
		LOGGER.info("地形改造权限已设置为: {}", permission);
		return 1;
	}

	/**
	 * 在指定坐标执行地形改造指令
	 */
	private int transformAtCommand(ServerCommandSource source, int x, int y, int z, String mode, String target) {
		if (!(source.getWorld() instanceof ServerWorld world)) {
			source.sendError(Text.literal("§c只能在服务器世界中执行此命令"));
			return 0;
		}



		BlockPos pos = new BlockPos(x, y, z);
		source.sendMessage(Text.literal("§6开始在地点 " + x + ", " + y + ", " + z + " 执行地形改造..."));

		world.getServer().execute(() -> {
			startTerrainTransformationAt(world, pos, source.getPlayer(), mode, target);
		});

		return 1;
	}



	/**
	 * 在玩家位置执行地形改造指令
	 */
	private int transformPlayerPositionCommand(ServerCommandSource source, String mode, String target) {
		if (source.getPlayer() == null) {
			source.sendError(Text.literal("§c只有玩家可以执行此命令"));
			return 0;
		}


		BlockPos pos = source.getPlayer().getBlockPos();
		source.sendMessage(Text.literal("§6开始在玩家位置执行地形改造..."));

		if (!(source.getWorld() instanceof ServerWorld world)) {
			source.sendError(Text.literal("§c只能在服务器世界中执行此命令"));
			return 0;
		}

		world.getServer().execute(() -> {
			startTerrainTransformationAt(world, pos, source.getPlayer(), mode, target);
		});

		return 1;
	}



	/**
	 * 在指定位置开始地形改造（指令版本）
	 */
	private void startTerrainTransformationAt(ServerWorld world, BlockPos centerPos,
											  net.minecraft.entity.player.PlayerEntity player,
											  String mode, String target) {
		LOGGER.info("指令启动地形改造 - 位置: {}, 模式: {}, 目标: {}", centerPos, mode, target);

		Predicate<RegistryEntry<Biome>> targetBiome=null;
		String targetStructure = null;

		switch (mode) {
			case "biome":
				if (target != null) {

					if(target.startsWith("#")){
						Identifier tagId = Identifier.of(target.substring(1)); // "biome_tag_villagers:villager_jungle"
						TagKey<Biome> biomeTag = TagKey.of(RegistryKeys.BIOME, tagId);

						targetBiome = (entry)-> entry.isIn(biomeTag);
					}else{
						RegistryKey<Biome> k =RegistryKey.of(RegistryKeys.BIOME,Identifier.of(target));
						targetBiome = (entry)-> entry.matchesKey(k);
					}
					player.sendMessage(Text.literal("§6目标生物群系: " + target), false);
				}
				break;
			case "structure":
				if (target != null) {
					targetStructure = target;
					player.sendMessage(Text.literal("§6目标结构: " + target), false);
				}
				break;
			case "random":
				player.sendMessage(Text.literal("§6随机模式"), false);
				break;
		}

		BlockPos referencePos = findReferencePosition(world, centerPos, targetBiome, targetStructure, player);

		if (referencePos != null) {
			if (config.UseSurface) {
				new SurfaceTransformationTask(world, centerPos, referencePos, player).start();
			} else {
				new TerrainTransformationTask(world, centerPos, referencePos, player).start();
			}
			player.sendMessage(Text.literal("§a地形改造已启动！"), false);
			LOGGER.info("地形改造任务已启动 - 中心位置: {}, 参考位置: {}", centerPos, referencePos);
		} else {
			sendErrorMessage(player, targetBiome, targetStructure);
		}
	}

	// 修改原有的startTerrainTransformation方法，添加权限检查
	private void startTerrainTransformation(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		// 权限检查
		if (!checkPermission(player)) {
			player.sendMessage(Text.literal("§c你没有权限使用地形改造功能！"), false);
			return;
		}

		LOGGER.info("开始地形改造过程 - 信标位置: {}", beaconPos);

		Predicate<RegistryEntry<Biome>> targetBiome = detectTargetBiome(world, beaconPos, player);
		String targetStructure = detectTargetStructure(world, beaconPos, player);

		BlockPos referencePos;
		if (config.UseSpecificPos) {
			referencePos = new BlockPos(config.Posx, config.Posy, config.Posz);
			referencePos.add(0, -referencePos.getY(), 0);
		} else {
			referencePos = findReferencePosition(world, beaconPos, targetBiome, targetStructure, player);
		}

		if (referencePos != null) {
			if (config.UseSurface) {
				new SurfaceTransformationTask(world, beaconPos, referencePos, player).start();
			} else {
				new TerrainTransformationTask(world, beaconPos, referencePos, player).start();
			}
			player.sendMessage(Text.literal("§a地形改造已启动！"), false);
			LOGGER.info("地形改造任务已启动 - 参考位置: {}", referencePos);
		} else {
			sendErrorMessage(player, targetBiome, targetStructure);
		}
	}

	// 修改findReferencePosition方法以支持指令调用
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

	// 其余现有方法保持不变...
	private Predicate<RegistryEntry<Biome>> detectTargetBiome(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		BlockPos sidePos = beaconPos.east();
		Block sideBlock = world.getBlockState(sidePos).getBlock();

		for (var i:config.biomeMappings) {
			if (Registries.BLOCK.get(Identifier.of(i.itemId)) == sideBlock) {
				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.BiomeId + "生物群系"), false);
				Predicate<RegistryEntry<Biome>> p;

				if (i.BiomeId.startsWith("#")) {
					Identifier tagId = Identifier.of(i.BiomeId.substring(1)); // "biome_tag_villagers:villager_jungle"
					TagKey<Biome> biomeTag = TagKey.of(RegistryKeys.BIOME, tagId);

					p = (entry) -> {
						return entry.isIn(biomeTag);
					};
				} else {
					RegistryKey<Biome> k = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(i.BiomeId));
					p = (entry) -> {
						return entry.matchesKey(k);
					};
				}
				//RegistryEntryPredicateArgumentType.EntryPredicate<Biome> a =

				return p;
			}
		}
		return null;
	}

	private String detectTargetStructure(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		BlockPos sidePos = beaconPos.east();
		Block sideBlock = world.getBlockState(sidePos).getBlock();

		for (var i : config.structureMappings) {
			if (Registries.BLOCK.get(Identifier.of(i.itemId)) == sideBlock) {
				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.structureId + "结构"), false);
				return i.structureId;
			}
		}
		return null;
	}

	private BlockPos findBiomePosition(ServerWorld world, BlockPos center, Predicate<RegistryEntry<Biome>> targetBiome,
									   net.minecraft.entity.player.PlayerEntity player) {
		try {
			Pair<BlockPos,RegistryEntry<Biome>> p = world.locateBiome(targetBiome,center,config.searchRadius,32,64);
			if(p==null){
				player.sendMessage(Text.literal("无法找到结构,请尝试使用locate命令测试或检查拼写错误"),false);
			}
			BlockPos biomePos = p.getFirst();
//			BlockPos biomePos = Objects.requireNonNull(world.locateBiome(
//					b -> b.matchesKey(targetBiome),
//					center, 6400, 32, 64
//			)).getFirst();

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
			var a = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(Identifier.of(structureId));
			Pair<BlockPos, RegistryEntry<Structure>> pair = world.getChunkManager().getChunkGenerator().locateStructure(world, RegistryEntryList.of(RegistryEntry.of(a)), center, 6400, false);
			//上面这个负责村庄外结构，村庄必须使用下面这个查询
			BlockPos structurePos;
			if(pair==null)
			{
				structurePos = world.locateStructure(
						net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE,
								net.minecraft.util.Identifier.of(structureId)),
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
			int distance = config.randomRadius + world.random.nextInt(500);

			int refX = center.getX() + (int) (Math.cos(angle) * distance);
			int refZ = center.getZ() + (int) (Math.sin(angle) * distance);
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

	private BlockPos getValidSurfacePosition(ServerWorld world, BlockPos pos) {
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
			surfaceY = validateAndAdjustSurfaceHeight(world, pos.getX(), pos.getZ(), surfaceY);

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

	private int validateAndAdjustSurfaceHeight(ServerWorld world, int x, int z, int initialHeight) {
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