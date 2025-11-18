package com.worldreloader;

import com.mojang.datafixers.util.Pair;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.ConfigHolder;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.registry.*;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;

import java.util.Objects;
import java.util.function.Predicate;

import static com.worldreloader.BaseTransformationTask.isSolidBlock;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.server.command.CommandManager.argument;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

public class WorldReloader implements ModInitializer {
	public static final String MOD_ID = "worldreloader";

	public static ModConfig config;
	public static ConfigHolder ch = AutoConfig.register(ModConfig.class, GsonConfigSerializer::new);
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("World Reloader Initialized!");

		GuiRegistry registry = AutoConfig.getGuiRegistry(ModConfig.class);

		// 注册方块使用事件
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

		// 注册按键绑定
		KeyBindings.register();

		// 获取配置
		config = AutoConfig.getConfigHolder(ModConfig.class).getConfig();

		// 注册客户端tick事件
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (KeyBindings.openConfigKey.wasPressed()) {
				if (client.player != null) {
					client.setScreen(AutoConfig.getConfigScreen(ModConfig.class, client.currentScreen).get());
				}
			}
		});

		// 注册命令
		registerCommands();
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			// 设置特定位置命令
			dispatcher.register(literal("setPos")
					.then(argument("x", integer())
							.then(argument("y", integer())
									.then(argument("z", integer())
											.executes(context -> {
												int x = context.getArgument("x", Integer.class);
												int y = context.getArgument("y", Integer.class);
												int z = context.getArgument("z", Integer.class);

												config.UseSpecificPos = true;
												config.Posx = x;
												config.Posy = y;
												config.Posz = z;
												ch.save();

												context.getSource().sendFeedback(() ->
														Text.literal("§a已设置参考位置: " + x + ", " + y + ", " + z), false);
												return 1;
											})))));

			// 设置线段起点命令
			dispatcher.register(literal("setLineStart")
					.then(argument("x", integer())
							.then(argument("z", integer())
									.executes(context -> {
										int x = context.getArgument("x", Integer.class);
										int z = context.getArgument("z", Integer.class);

										config.lineStartX = x;
										config.lineStartZ = z;
										ch.save();

										context.getSource().sendFeedback(() ->
												Text.literal("§a已设置线段起点: " + x + ", " + z), false);
										return 1;
									}))));

			// 设置线段终点命令
			dispatcher.register(literal("setLineEnd")
					.then(argument("x", integer())
							.then(argument("z", integer())
									.executes(context -> {
										int x = context.getArgument("x", Integer.class);
										int z = context.getArgument("z", Integer.class);

										config.lineEndX = x;
										config.lineEndZ = z;
										ch.save();

										context.getSource().sendFeedback(() ->
												Text.literal("§a已设置线段终点: " + x + ", " + z), false);
										return 1;
									}))));

			// 切换改造模式命令
			dispatcher.register(literal("toggleTransformationMode")
					.executes(context -> {
						config.useLineTransformation = !config.useLineTransformation;
						ch.save();

						String mode = config.useLineTransformation ? "线段改造" : "圆形改造";
						context.getSource().sendFeedback(() ->
								Text.literal("§a已切换到: " + mode + "模式"), false);
						return 1;
					}));

			// 显示当前设置命令
			dispatcher.register(literal("transformationInfo")
					.executes(context -> {
						ServerCommandSource source = context.getSource();
						String mode = config.useLineTransformation ? "线段改造" : "圆形改造";
						source.sendFeedback(() -> Text.literal("§6当前改造模式: " + mode), false);

						if (config.useLineTransformation) {
							source.sendFeedback(() -> Text.literal("§6线段起点: " + config.lineStartX + ", " + config.lineStartZ), false);
							source.sendFeedback(() -> Text.literal("§6线段终点: " + config.lineEndX + ", " + config.lineEndZ), false);
							source.sendFeedback(() -> Text.literal("§6线段宽度: " + config.lineWidth), false);
						}

						if (config.UseSpecificPos) {
							source.sendFeedback(() -> Text.literal("§6参考位置: " + config.Posx + ", " + config.Posy + ", " + config.Posz), false);
						}

						return 1;
					}));
		});
	}

	private void startTerrainTransformation(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		LOGGER.info("开始地形改造过程 - 信标位置: {}", beaconPos);

		// 检查是否启用线段改造
		if (config.useLineTransformation) {
			startLineTransformation(world, beaconPos, player);
			return;
		}

		// 原有的圆形改造逻辑
		Predicate<RegistryEntry<Biome>> targetBiome = detectTargetBiome(world, beaconPos, player);
		String targetStructure = detectTargetStructure(world, beaconPos, player);

		BlockPos referencePos;
		if (config.UseSpecificPos) {
			referencePos = new BlockPos(config.Posx, config.Posy, config.Posz);
			if (referencePos == null) {
				player.sendMessage(Text.literal("§c你丫没设置目标点,用setPos命令设置!!!!"));
				return;
			}
			referencePos = referencePos.add(0, -referencePos.getY(), 0);
		} else {
			referencePos = findReferencePosition(world, beaconPos, targetBiome, targetStructure, player);
		}

		if (referencePos != null) {
			if (config.UseSurface) {
				new SurfaceTransformationTask(world, beaconPos, referencePos, player).start();
			} else {
				new TerrainTransformationTask(world, beaconPos, referencePos, player).start();
			}
			player.sendMessage(Text.literal("§a圆形地形改造已启动！"), false);
			LOGGER.info("圆形地形改造任务已启动 - 参考位置: {}", referencePos);
		} else {
			sendErrorMessage(player, targetBiome, targetStructure);
		}
	}

	/**
	 * 启动线段改造
	 */
	private void startLineTransformation(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		LOGGER.info("开始线段改造过程");

		// 获取配置中的线段起点和终点
		BlockPos lineStart = new BlockPos(config.lineStartX, world.getSeaLevel(), config.lineStartZ);
		BlockPos lineEnd = new BlockPos(config.lineEndX, world.getSeaLevel(), config.lineEndZ);

		// 验证线段长度
		double lineLength = Math.sqrt(lineStart.getSquaredDistance(lineEnd));
		if (lineLength > config.maxLineLength) {
			player.sendMessage(Text.literal("§c线段长度超过最大限制: " + config.maxLineLength + " 格"), false);
			return;
		}

		if (lineLength < 10) {
			player.sendMessage(Text.literal("§c线段长度太短，至少需要10格"), false);
			return;
		}

		player.sendMessage(Text.literal("§6线段长度: " + String.format("%.1f", lineLength) + " 格"), false);
		player.sendMessage(Text.literal("§6线段宽度: " + config.lineWidth + " 格"), false);

		// 查找参考位置
		BlockPos referenceStart = findLineReferencePosition(world, lineStart, player);
		BlockPos referenceEnd = findLineReferencePosition(world, lineEnd, player);

		if (referenceStart == null || referenceEnd == null) {
			player.sendMessage(Text.literal("§c无法找到有效的参考位置"), false);
			return;
		}

		// 创建并启动线段改造任务
		LineTransformationTask task = new LineTransformationTask(
				world, lineStart, lineEnd, referenceStart, referenceEnd, player
		);
		task.start();

		player.sendMessage(Text.literal("§a线段改造已启动！"), false);
		LOGGER.info("线段改造任务已启动 - 起点: {}, 终点: {}", lineStart, lineEnd);
	}

	/**
	 * 为线段端点查找参考位置
	 */
	private BlockPos findLineReferencePosition(ServerWorld world, BlockPos point, net.minecraft.entity.player.PlayerEntity player) {
		// 如果启用了特定位置，使用配置的位置作为参考起点
		if (config.UseSpecificPos) {
			BlockPos specificPos = new BlockPos(config.Posx, config.Posy, config.Posz);
			if (specificPos != null) {
				// 保持与目标点相同的相对位置
				return new BlockPos(
						specificPos.getX() + (point.getX() - config.lineStartX),
						specificPos.getY(),
						specificPos.getZ() + (point.getZ() - config.lineStartZ)
				);
			}
		}

		// 否则在目标点周围寻找合适的参考位置
		return findRandomPositionAround(world, point, player);
	}

	/**
	 * 在指定点周围寻找随机位置
	 */
	private BlockPos findRandomPositionAround(ServerWorld world, BlockPos center, net.minecraft.entity.player.PlayerEntity player) {
		LOGGER.info("在线段端点周围查找参考位置 - 中心: {}", center);

		for (int i = 0; i < 10; i++) {
			double angle = world.random.nextDouble() * 2 * Math.PI;
			int distance = 500 + world.random.nextInt(500); // 500-1000格距离

			int refX = center.getX() + (int)(Math.cos(angle) * distance);
			int refZ = center.getZ() + (int)(Math.sin(angle) * distance);
			BlockPos testPos = new BlockPos(refX, 0, refZ);

			BlockPos surfacePos = getValidSurfacePosition(world, testPos);
			if (surfacePos != null) {
				LOGGER.info("成功找到线段参考位置: {}", surfacePos);
				return surfacePos;
			}
		}

		LOGGER.info("线段参考位置查找失败");
		return null;
	}

	private Predicate<RegistryEntry<Biome>> detectTargetBiome(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		BlockPos sidePos = beaconPos.east();
		Block sideBlock = world.getBlockState(sidePos).getBlock();

		for (var i : config.biomeMappings) {
			if (Registries.BLOCK.get(Identifier.of("minecraft", i.itemId)) == sideBlock) {
				player.sendMessage(Text.literal("§6检测到东侧方块: " + sideBlock.getName().getString() + "，将寻找" + i.BiomeId + "生物群系"), false);

				if (i.BiomeId.startsWith("#")) {
					Identifier tagId = Identifier.splitOn(i.BiomeId.substring(1), ':');
					TagKey<Biome> biomeTag = TagKey.of(RegistryKeys.BIOME, tagId);

					return (entry) -> entry.isIn(biomeTag);
				} else {
					RegistryKey<Biome> k = RegistryKey.of(RegistryKeys.BIOME, Identifier.splitOn(i.BiomeId, ':'));
					return (entry) -> entry.matchesKey(k);
				}
			}
		}
		return null;
	}

	private String detectTargetStructure(ServerWorld world, BlockPos beaconPos, net.minecraft.entity.player.PlayerEntity player) {
		BlockPos sidePos = beaconPos.east();
		Block sideBlock = world.getBlockState(sidePos).getBlock();

		for (var i : config.structureMappings) {
			if (Registries.BLOCK.get(Identifier.of("minecraft", i.itemId)) == sideBlock) {
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
			Pair<BlockPos, RegistryEntry<Biome>> p = world.locateBiome(targetBiome, center, 6400, 32, 64);
			if (p == null) {
				player.sendMessage(Text.literal("§c无法找到生物群系,请尝试使用locate命令测试或检查拼写错误"), false);
				return null;
			}
			BlockPos biomePos = p.getFirst();

			if (biomePos != null) {
				BlockPos surfacePos = getValidSurfacePosition(world, biomePos);
				if (surfacePos != null) {
					double distance = Math.sqrt(center.getSquaredDistance(surfacePos));
					player.sendMessage(Text.literal("§a成功找到目标生物群系，距离: " + String.format("%.1f", distance) + " 格"), false);
					return surfacePos;
				}
				BlockPos res = findAlternativeBiomePosition(world, biomePos, targetBiome);
				if (res == null) {
					player.sendMessage(Text.literal("§c无法找到目标群落,请考虑降低目标最低高度"), false);
				}
				return res;
			}
		} catch (Exception e) {
			LOGGER.error("查找生物群系时发生错误: {}", e.getMessage());
			player.sendMessage(Text.literal("§c查找生物群系时发生错误: " + e.getMessage()), false);
		}
		return null;
	}

	private BlockPos findStructurePosition(ServerWorld world, BlockPos center, String structureId,
										   net.minecraft.entity.player.PlayerEntity player) {
		try {
			String[] strings = structureId.split(":");
			String namespace;
			String path;
			if (strings.length == 1) {
				namespace = "minecraft";
				path = strings[0];
			} else {
				namespace = strings[0];
				path = strings[1];
			}

			var a = world.getRegistryManager().get(RegistryKeys.STRUCTURE).get(Identifier.of(namespace, path));
			Pair<BlockPos, RegistryEntry<Structure>> pair = world.getChunkManager().getChunkGenerator().locateStructure(world, RegistryEntryList.of(RegistryEntry.of(a)), center, 6400, false);

			BlockPos structurePos;
			if (pair == null) {
				structurePos = world.locateStructure(
						net.minecraft.registry.tag.TagKey.of(net.minecraft.registry.RegistryKeys.STRUCTURE,
								net.minecraft.util.Identifier.of(namespace, path)),
						center, 6400, false
				);
			} else {
				structurePos = pair.getFirst();
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

			if (isSolidBlock(world, surfaceBlock) && (surfacePos.getY() >= config.targetYmin)) {
				return surfacePos;
			}

		} finally {
			world.setChunkForced(chunkPos.x, chunkPos.z, false);
		}

		return null;
	}

	/**
	 * 验证并调整表面高度，确保找到的是真正的地面
	 */
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
			player.sendMessage(Text.literal("§c生物群系查找出现问题！"), false);
		} else if (targetStructure != null) {
			player.sendMessage(Text.literal("§c结构查找出现问题！"), false);
		} else {
			player.sendMessage(Text.literal("§c随机查找出现问题！"), false);
		}
	}
}