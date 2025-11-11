package com.worldreloader;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.datafixers.util.Pair;
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
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
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

import static com.worldreloader.transformationtasks.BaseTransformationTask.isSolidBlock;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class WorldReloader implements ModInitializer {
	public static final String MOD_ID = "worldreloader";

	public static ModConfig config;
	public static ConfigHolder ch = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public String minPermission="op";
	private static boolean isLock=false;

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
					itemStack.getItem() == Items.NETHER_STAR) {
				if (!checkPermission(player)) {
					player.sendMessage(Text.literal("§c你没有权限使用地形改造功能！"), false);
					return ActionResult.FAIL;
				}

				if (!player.isCreative()) {
					itemStack.decrement(1);
				}

				LOGGER.info("激活地形改造");
				if(!isLock){
					Objects.requireNonNull(world.getServer()).execute(() -> {
						startTerrainTransformation((ServerWorld) world, pos, player);
						//isLock=false;
					});
				}else {
					player.sendMessage(Text.literal("另一个改造正在进行,请等待"),false);

				}

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
	 * 在指定位置开始地形改造（指令版本）- 使用Builder模式
	 */
	private void startTerrainTransformationAt(ServerWorld world, BlockPos centerPos,
											  net.minecraft.entity.player.PlayerEntity player,
											  String mode, String target) {
		LOGGER.info("指令启动地形改造 - 位置: {}, 模式: {}, 目标: {}", centerPos, mode, target);

		TerrainTransformationBuilder builder = new TerrainTransformationBuilder(world, player)
				.setChangePos(centerPos)
				.setRadius(config.maxRadius)
				.setPadding(config.paddingCount)
				.setSteps(config.totalSteps2)
				.setItemCleanupInterval(config.itemCleanupInterval)
				.changeBiome(config.isChangeBiome);

		// 设置高度参数
		if (config.UseSurface) {
			builder.setYMin(config.DEPTH)
					.setYMax(config.HEIGHT);
		} else {
			builder.setYMin(config.yMin)
					.setYMax(config.yMaxThanSurface);
		}

		boolean foundReference = false;

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
					builder.setBiomePos(centerPos, targetBiome, config.searchRadius);
					foundReference = true;
				}
				break;
			case "structure":
				if (target != null) {
					player.sendMessage(Text.literal("§6目标结构: " + target), false);
					builder.setStructurePos(centerPos, target, config.searchRadius);
					foundReference = true;
				}
				break;
			case "random":
				player.sendMessage(Text.literal("§6随机模式"), false);
				builder.setRandomPos(centerPos, config.randomRadius);
				foundReference = true;
				break;
		}

		if (foundReference) {
			if (config.UseSurface) {
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
		} else {
			player.sendMessage(Text.literal("§c无法找到合适的参考位置！"), false);
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
				.changeBiome(config.isChangeBiome);

		// 设置高度参数
		if (config.UseSurface) {
			builder.setYMin(config.DEPTH)
					.setYMax(config.HEIGHT);
		} else {
			builder.setYMin(config.yMin)
					.setYMax(config.yMaxThanSurface);
		}

		boolean foundReference = false;

		if (config.UseSpecificPos) {
			BlockPos specificPos = new BlockPos(config.Posx, config.Posy, config.Posz);
			builder.setTargetPos(specificPos);
			foundReference = true;
			player.sendMessage(Text.literal("§6使用特定位置: " + specificPos), false);
		} else {
			Predicate<RegistryEntry<Biome>> targetBiome = detectTargetBiome(world, beaconPos, player);
			String targetStructure = detectTargetStructure(world, beaconPos, player);

			if (targetBiome != null) {
				builder.setBiomePos(beaconPos, targetBiome, config.searchRadius);
				foundReference = true;
			} else if (targetStructure != null) {
				builder.setStructurePos(beaconPos, targetStructure, config.searchRadius);
				foundReference = true;
			} else {
				builder.setRandomPos(beaconPos, config.randomRadius);
				foundReference = true;
				player.sendMessage(Text.literal("§6使用随机位置"), false);
			}
		}

		if (foundReference) {
			if (config.UseSurface) {
				SurfaceTransformationTask task = builder.buildSurface();
				if (task != null) {
					task.start();
					player.sendMessage(Text.literal("§a地表地形改造已启动！"), false);
					LOGGER.info("地表地形改造任务已启动 - 信标位置: {}", beaconPos);
				} else {
					player.sendMessage(Text.literal("§c无法启动地表地形改造任务！"), false);
				}
			} else {
				TerrainTransformationTask task = builder.buildStandard();
				if (task != null) {
					task.start();
					player.sendMessage(Text.literal("§a完整地形改造已启动！"), false);
					LOGGER.info("完整地形改造任务已启动 - 信标位置: {}", beaconPos);
				} else {
					player.sendMessage(Text.literal("§c无法启动完整地形改造任务！"), false);
				}
			}
		} else {
			player.sendMessage(Text.literal("§c无法找到合适的参考位置！"), false);
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
}