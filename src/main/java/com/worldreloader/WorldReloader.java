package com.worldreloader;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.worldreloader.transformationtasks.LineTransformationTask;
import com.worldreloader.transformationtasks.SurfaceTransformationTask;
import com.worldreloader.transformationtasks.TerrainTransformationBuilder;
import com.worldreloader.transformationtasks.TerrainTransformationTask;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.mixin.registry.sync.RegistriesAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class WorldReloader implements ModInitializer {
	public static final String MOD_ID = "worldreloader";

	public static ModConfig config;
	public static ConfigHolder<ModConfig> ch = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public String minPermission="op";
	private static boolean isLock=false;
	// 改为 Map 存储，方便查找
	private final Map<Item, Integer> itemRequirements = new HashMap<>();
	private Block targetBlock;

	public static void SetLocker(boolean isLock1){
		isLock=isLock1;
	}

	@Override
	public void onInitialize() {
		LOGGER.info("World Reloader Initialized!");
		// 注册指令
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// 设置权限指令
			dispatcher.register(literal("worldreloader")
					.then(literal("refresh")
							.executes(context -> {
								updateFromConfig();
								return 1;
							})
					)
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
			if(isLock)
			{
				player.sendMessage(Text.literal("§c有改造任务正在进行，请结束后再开启新改造任务"), false);
				return ActionResult.FAIL;
			}
			if(itemRequirements.isEmpty())
			{
				updateFromConfig();
			}

			if (itemRequirements.isEmpty()) {
				player.sendMessage(Text.literal("§c未配置有效的物品需求！请检查配置。"), false);
				return ActionResult.FAIL;
			}

			ItemStack itemStack = player.getStackInHand(hand);
			BlockPos pos = hitResult.getBlockPos();

			// 检查目标方块
			BlockState clickedBlock = world.getBlockState(pos);
			if (clickedBlock.getBlock() != targetBlock) {
				return ActionResult.PASS;
			}

			// 检查物品需求
			Item heldItem = itemStack.getItem();
			if (!itemRequirements.containsKey(heldItem)) {
				return ActionResult.PASS;
			}

			// 权限检查
			if (!checkPermission(player)) {
				player.sendMessage(Text.literal("§c你没有权限使用地形改造功能！"), false);
				return ActionResult.FAIL;
			}

			// 消耗物品
			int requiredCount = itemRequirements.get(heldItem);
			if (!player.isCreative()) {
				if (itemStack.getCount() > requiredCount) {
					itemStack.decrement(requiredCount);
					itemRequirements.remove(heldItem);
				} else if (itemStack.getCount() == requiredCount) {
					player.getInventory().removeStack(player.getInventory().getSlotWithStack(itemStack));
					itemRequirements.remove(heldItem);
				} else {
					int remaining = requiredCount - itemStack.getCount();
					itemRequirements.put(heldItem, remaining);
					player.getInventory().removeStack(player.getInventory().getSlotWithStack(itemStack));
				}
			} else {
				// 创造模式直接移除需求
				itemRequirements.remove(heldItem);
			}

			// 检查是否所有需求都已满足
			if (itemRequirements.isEmpty()) {
				LOGGER.info("激活地形改造");
				Objects.requireNonNull(world.getServer()).execute(() -> {
					startTerrainTransformation((ServerWorld) world, pos, player);
				});
			}

			return ActionResult.SUCCESS;
		});
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClient()) return ActionResult.PASS;

			Item handitem = player.getStackInHand(hand).getItem();
			Identifier blockId = Identifier.of(config.tool);
			Item item = Registries.ITEM.get(blockId);

			if (handitem==item) {
				removeSavedPosition(world, pos, player);
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
	 * 从配置更新物品需求和目标方块
	 */
	private void updateFromConfig() {
		// 清空现有需求
		itemRequirements.clear();

		// 转换物品需求
		for (ModConfig.ItemRequirement requirement : config.targetBlockDict) {
			if (!requirement.enabled) continue;

			try {
				Identifier itemId = Identifier.of(requirement.itemId);
				Item item = Registries.ITEM.get(itemId);

				if (item != Items.AIR) {
					itemRequirements.put(item, requirement.count);
				} else {
					LOGGER.warn("无效的物品ID: {}", requirement.itemId);
				}
			} catch (Exception e) {
				LOGGER.error("解析物品ID失败: {}", requirement.itemId, e);
			}
		}

		// 转换目标方块
		try {
			Identifier blockId = Identifier.of(config.targetBlock);
			Block block = Registries.BLOCK.get(blockId);

			if (block != Blocks.AIR) {
				targetBlock = block;
			} else {
				LOGGER.warn("无效的方块ID: {}，使用默认值beacon", config.targetBlock);
				targetBlock = Blocks.BEACON;
			}
		} catch (Exception e) {
			LOGGER.error("解析方块ID失败: {}，使用默认值beacon", config.targetBlock, e);
			targetBlock = Blocks.BEACON;
		}
	}


	/**
	 * 添加坐标到保存列表
	 */
	private void addSavedPosition(World world, BlockPos pos, net.minecraft.entity.player.PlayerEntity player) {
		ModConfig.SavedPosition newPos = new ModConfig.SavedPosition(pos.getX(), pos.getY(), pos.getZ());

		// 检查是否已存在相同坐标
		if (config.savedPositions.contains(newPos)) {
			player.sendMessage(Text.literal("§7该坐标已存在: " + newPos), false);
			return;
		}

		config.savedPositions.add(newPos);

		// 保存配置
		AutoConfig.getConfigHolder(ModConfig.class).save();

		player.sendMessage(Text.literal("§a已添加坐标: " + newPos), false);
		LOGGER.info("添加坐标: {}", newPos);

		// 显示当前坐标总数
		player.sendMessage(Text.literal("§7当前保存的坐标数: " + config.savedPositions.size()), false);
	}

	/**
	 * 从保存列表中移除坐标
	 */
	private void removeSavedPosition(World world, BlockPos pos, net.minecraft.entity.player.PlayerEntity player) {
		ModConfig.SavedPosition targetPos = new ModConfig.SavedPosition(pos.getX(), pos.getY(), pos.getZ());
		boolean removed = config.savedPositions.remove(targetPos);

		if (removed) {
			AutoConfig.getConfigHolder(ModConfig.class).save();
			player.sendMessage(Text.literal("§c已移除坐标: " + targetPos), false);
			LOGGER.info("移除坐标: {}", targetPos);
			player.sendMessage(Text.literal("§7剩余坐标数: " + config.savedPositions.size()), false);
		} else {
			player.sendMessage(Text.literal("§7未找到该位置的坐标"), false);
		}
	}

	/**
	 * 获取所有保存的坐标
	 */
	public static List<ModConfig.SavedPosition> getSavedPositions() {
		return config.savedPositions;
	}

	/**
	 * 清空所有保存的坐标
	 */
	public static void clearSavedPositions(net.minecraft.entity.player.PlayerEntity player) {
		int count = config.savedPositions.size();
		config.savedPositions.clear();
		AutoConfig.getConfigHolder(ModConfig.class).save();
		player.sendMessage(Text.literal("§c已清空所有保存的坐标 (" + count + " 个)"), false);
	}

	/**
	 * 显示所有保存的坐标
	 */
	public static void listSavedPositions(net.minecraft.entity.player.PlayerEntity player) {
		List<ModConfig.SavedPosition> positions = getSavedPositions();
		if (positions.isEmpty()) {
			player.sendMessage(Text.literal("§7没有保存的坐标"), false);
		} else {
			player.sendMessage(Text.literal("§6保存的坐标列表 (" + positions.size() + " 个):"), false);
			for (int i = 0; i < positions.size(); i++) {
				ModConfig.SavedPosition pos = positions.get(i);
				player.sendMessage(Text.literal("§a" + (i + 1) + ". §f" + pos), false);
			}
		}
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
	 * 在指定位置开始地形改造（指令版本）- 使用Builder模式
	 */
	private void startTerrainTransformationAt(ServerWorld world, BlockPos centerPos,
											  net.minecraft.entity.player.PlayerEntity player,
											  String mode, String target) {



		LOGGER.info("指令启动地形改造 - 位置: {}, 模式: {}, 目标: {}", centerPos, mode, target);
		RegistryKey<World> worldRegistryKey=RegistryKey.of(RegistryKeys.WORLD,Identifier.of(config.dimension));
		MinecraftServer server=world.getServer();
		ServerWorld targetWorld=server.getWorld(worldRegistryKey);
		TerrainTransformationBuilder builder = new TerrainTransformationBuilder(world, player)
				.setChangePos(centerPos)
				.setRadius(config.maxRadius)
				.setPadding(config.paddingCount)
				.setSteps(config.totalSteps2)
				.setItemCleanupInterval(config.itemCleanupInterval)
				.changeBiome(true)
				.preserveBeacon(config.preserveBeacon)
				.setTargetDimension(targetWorld);

		// 设置高度参数
		if (config.UseSurface) {
			builder.setYMin(config.DEPTH)
					.setYMax(config.HEIGHT);
		} else {
			builder.setYMin(config.yMin)
					.setYMax(config.yMaxThanSurface);
		}


		switch (mode) {
			case "biome":
				if (target != null) {
					Predicate<RegistryEntry<Biome>> targetBiome;
					if (target.startsWith("#")) {
						Identifier tagId = Identifier.of(target.substring(1));
						TagKey<Biome> biomeTag = TagKey.of(RegistryKeys.BIOME, tagId);
						targetBiome = (entry) -> entry.isIn(biomeTag);
					} else {
						RegistryKey<Biome> k = RegistryKey.of(RegistryKeys.BIOME, Identifier.of(target));
						targetBiome = (entry) -> entry.matchesKey(k);
					}
					player.sendMessage(Text.literal("§6目标生物群系: " + target), false);
					builder.setBiomePos(centerPos, targetBiome, 6400);
				}
				break;
			case "structure":
				if (target != null) {
					player.sendMessage(Text.literal("§6目标结构: " + target), false);
					builder.setStructurePos(centerPos, target, 6400);
				}
				break;
			case "random":
				player.sendMessage(Text.literal("§6随机模式"), false);
				builder.setRandomPos(centerPos, 6400);
				break;
		}

			if (config.UseSurface) {
				builder.setSteps(config.totalSteps);
				SurfaceTransformationTask task = builder.buildSurface();
				if (task != null) {
					task.start();
					player.sendMessage(Text.literal("§a地表地形改造已启动！"), false);
					LOGGER.info("地表地形改造任务已启动 - 中心位置: {}", centerPos);
				} else {
					player.sendMessage(Text.literal("§c无法启动地表地形改造任务！"), false);
				}
			} else {
				TerrainTransformationTask task = builder.buildStandard();
				if (task != null) {
					task.start();
					player.sendMessage(Text.literal("§a完整地形改造已启动！"), false);
					LOGGER.info("完整地形改造任务已启动 - 中心位置: {}", centerPos);
				} else {
					player.sendMessage(Text.literal("§c无法启动完整地形改造任务！"), false);
				}
			}

	}

	/**
	 * 信标激活的地形改造 - 使用Builder模式
	 */
	private void startTerrainTransformation(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		// 权限检查
		if (!checkPermission(player)) {
			player.sendMessage(Text.literal("§c你没有权限使用地形改造功能！"), false);
			return;
		}

		LOGGER.info("开始地形改造过程 - 信标位置: {}", beaconPos);

		TerrainTransformationBuilder builder = new TerrainTransformationBuilder(world, player)
				.setChangePos(beaconPos)
				.setRadius(config.maxRadius)
				.setPadding(config.paddingCount)
				.setSteps(config.totalSteps2)
				.setItemCleanupInterval(config.itemCleanupInterval)
				.changeBiome(true);

		// 设置高度参数
		if (config.UseSurface) {
			builder.setYMin(config.DEPTH)
					.setYMax(config.HEIGHT);
		} else {
			builder.setYMin(config.yMin)
					.setYMax(config.yMaxThanSurface);
		}

        if (config.UseSpecificPos) {
			BlockPos specificPos = new BlockPos(config.Posx, config.Posy, config.Posz);
			builder.setTargetPos(specificPos);
            player.sendMessage(Text.literal("§6使用特定位置: " + specificPos), false);
		} else {
			Predicate<RegistryEntry<Biome>> targetBiome = detectTargetBiome(world, beaconPos, player);
			String targetStructure = detectTargetStructure(world, beaconPos, player);

			if (targetBiome != null) {
				builder.setBiomePos(beaconPos, targetBiome, 6400);
            } else if (targetStructure != null) {
				builder.setStructurePos(beaconPos, targetStructure, 6400);
            } else {
				builder.setRandomPos(beaconPos,6400);
                player.sendMessage(Text.literal("§6使用随机位置"), false);
			}
		}

			if (config.UseSurface) {
				SurfaceTransformationTask task = builder.buildSurface();
				if (task != null) {
					task.start();
					player.sendMessage(Text.literal("§a地表地形改造已启动！"), false);
					LOGGER.info("地表地形改造任务已启动 - 信标位置: {}", beaconPos);
				} else {
					player.sendMessage(Text.literal("§c无法启动地表地形改造任务！"), false);
				}
			}else if(config.UseLine){
				LineTransformationTask task = builder.buildLine();
				if (task != null) {
					task.start();
					player.sendMessage(Text.literal("§a完整地形改造已启动！"), false);
					LOGGER.info("完整地形改造任务已启动 - 信标位置: {}", beaconPos);
				} else {
					player.sendMessage(Text.literal("§c无法启动完整地形改造任务！"), false);
				}
			}
			else {
				TerrainTransformationTask task = builder.buildStandard();
				if (task != null) {
					task.start();
					player.sendMessage(Text.literal("§a完整地形改造已启动！"), false);
					LOGGER.info("完整地形改造任务已启动 - 信标位置: {}", beaconPos);
				} else {
					player.sendMessage(Text.literal("§c无法启动完整地形改造任务！"), false);
				}
			}
	}

	// 其余现有方法保持不变...
	private Predicate<RegistryEntry<Biome>> detectTargetBiome(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		BlockPos sidePos = beaconPos.east();
		Block sideBlock = world.getBlockState(sidePos).getBlock();

		for (var i:config.biomeMappings) {
			if (Registries.BLOCK.get(Identifier.of(i.itemId)) == sideBlock&&i.enabled) {
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
			if (Registries.BLOCK.get(Identifier.of(i.itemId)) == sideBlock&&i.enabled) {
				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.structureId + "结构"), false);
				return i.structureId;
			}
		}
		return null;
	}
}