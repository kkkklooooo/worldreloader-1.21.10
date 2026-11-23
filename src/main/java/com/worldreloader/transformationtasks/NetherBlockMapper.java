package com.worldreloader.transformationtasks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps Overworld terrain and building blocks to Nether equivalents while preserving orientation
 * and structural properties (stairs, slabs, logs, doors, fences, walls, lanterns, etc.).
 */
public final class NetherBlockMapper {
    private static final Map<Block, Block> CRIMSON_MAP = new HashMap<>();
    private static final Map<Block, Block> WARPED_MAP = new HashMap<>();

    static {
        initCommonMappings(CRIMSON_MAP, false);
        initCommonMappings(WARPED_MAP, true);
    }

    private NetherBlockMapper() {
    }

    private static void initCommonMappings(Map<Block, Block> map, boolean warped) {
        // Surface & soil
        Block nylium = warped ? Blocks.WARPED_NYLIUM : Blocks.CRIMSON_NYLIUM;
        map.put(Blocks.GRASS_BLOCK, nylium);
        map.put(Blocks.PODZOL, nylium);
        map.put(Blocks.MYCELIUM, nylium);
        map.put(Blocks.MOSS_BLOCK, nylium);
        map.put(Blocks.MOSS_CARPET, warped ? Blocks.WARPED_ROOTS : Blocks.CRIMSON_ROOTS);

        map.put(Blocks.DIRT, Blocks.NETHERRACK);
        map.put(Blocks.COARSE_DIRT, Blocks.SOUL_SOIL);
        map.put(Blocks.ROOTED_DIRT, Blocks.SOUL_SOIL);
        map.put(Blocks.MUD, Blocks.SOUL_SOIL);
        map.put(Blocks.CLAY, Blocks.SOUL_SOIL);
        map.put(Blocks.DIRT_PATH, Blocks.POLISHED_BLACKSTONE_BRICKS);
        map.put(Blocks.SAND, Blocks.SOUL_SAND);
        map.put(Blocks.RED_SAND, Blocks.SOUL_SAND);
        map.put(Blocks.SNOW, Blocks.AIR);
        map.put(Blocks.SNOW_BLOCK, Blocks.BASALT);
        map.put(Blocks.ICE, Blocks.BASALT);
        map.put(Blocks.PACKED_ICE, Blocks.BLACKSTONE);
        map.put(Blocks.BLUE_ICE, Blocks.MAGMA_BLOCK);

        // Natural stone & ores
        map.put(Blocks.STONE, Blocks.NETHERRACK);
        map.put(Blocks.DEEPSLATE, Blocks.BLACKSTONE);
        map.put(Blocks.TUFF, Blocks.BASALT);
        map.put(Blocks.ANDESITE, Blocks.BASALT);
        map.put(Blocks.DIORITE, Blocks.BASALT);
        map.put(Blocks.GRANITE, Blocks.BLACKSTONE);
        map.put(Blocks.CALCITE, Blocks.SMOOTH_BASALT);
        map.put(Blocks.DRIPSTONE_BLOCK, Blocks.NETHERRACK);

        map.put(Blocks.COAL_ORE, Blocks.NETHER_QUARTZ_ORE);
        map.put(Blocks.DEEPSLATE_COAL_ORE, Blocks.NETHER_QUARTZ_ORE);
        map.put(Blocks.IRON_ORE, Blocks.NETHER_QUARTZ_ORE);
        map.put(Blocks.DEEPSLATE_IRON_ORE, Blocks.NETHER_QUARTZ_ORE);
        map.put(Blocks.COPPER_ORE, Blocks.NETHER_QUARTZ_ORE);
        map.put(Blocks.DEEPSLATE_COPPER_ORE, Blocks.NETHER_QUARTZ_ORE);
        map.put(Blocks.GOLD_ORE, Blocks.NETHER_GOLD_ORE);
        map.put(Blocks.DEEPSLATE_GOLD_ORE, Blocks.NETHER_GOLD_ORE);
        map.put(Blocks.EMERALD_ORE, Blocks.NETHER_GOLD_ORE);
        map.put(Blocks.DIAMOND_ORE, Blocks.NETHER_GOLD_ORE);

        // Cobblestone & Stone Bricks
        map.put(Blocks.COBBLESTONE, Blocks.BLACKSTONE);
        map.put(Blocks.MOSSY_COBBLESTONE, Blocks.GILDED_BLACKSTONE);
        map.put(Blocks.COBBLESTONE_STAIRS, Blocks.BLACKSTONE_STAIRS);
        map.put(Blocks.MOSSY_COBBLESTONE_STAIRS, Blocks.BLACKSTONE_STAIRS);
        map.put(Blocks.COBBLESTONE_SLAB, Blocks.BLACKSTONE_SLAB);
        map.put(Blocks.MOSSY_COBBLESTONE_SLAB, Blocks.BLACKSTONE_SLAB);
        map.put(Blocks.COBBLESTONE_WALL, Blocks.BLACKSTONE_WALL);
        map.put(Blocks.MOSSY_COBBLESTONE_WALL, Blocks.BLACKSTONE_WALL);

        map.put(Blocks.STONE_BRICKS, Blocks.NETHER_BRICKS);
        map.put(Blocks.MOSSY_STONE_BRICKS, Blocks.NETHER_BRICKS);
        map.put(Blocks.CRACKED_STONE_BRICKS, Blocks.CRACKED_NETHER_BRICKS);
        map.put(Blocks.CHISELED_STONE_BRICKS, Blocks.CHISELED_NETHER_BRICKS);
        map.put(Blocks.STONE_BRICK_STAIRS, Blocks.NETHER_BRICK_STAIRS);
        map.put(Blocks.MOSSY_STONE_BRICK_STAIRS, Blocks.NETHER_BRICK_STAIRS);
        map.put(Blocks.STONE_BRICK_SLAB, Blocks.NETHER_BRICK_SLAB);
        map.put(Blocks.MOSSY_STONE_BRICK_SLAB, Blocks.NETHER_BRICK_SLAB);
        map.put(Blocks.STONE_BRICK_WALL, Blocks.NETHER_BRICK_WALL);
        map.put(Blocks.MOSSY_STONE_BRICK_WALL, Blocks.NETHER_BRICK_WALL);

        map.put(Blocks.SMOOTH_STONE, Blocks.POLISHED_BLACKSTONE);
        map.put(Blocks.SMOOTH_STONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);

        // Sandstone & bricks (desert / savanna villages)
        map.put(Blocks.SANDSTONE, Blocks.POLISHED_BLACKSTONE);
        map.put(Blocks.SMOOTH_SANDSTONE, Blocks.POLISHED_BLACKSTONE);
        map.put(Blocks.CUT_SANDSTONE, Blocks.POLISHED_BLACKSTONE_BRICKS);
        map.put(Blocks.CHISELED_SANDSTONE, Blocks.CHISELED_POLISHED_BLACKSTONE);
        map.put(Blocks.SANDSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS);
        map.put(Blocks.SMOOTH_SANDSTONE_STAIRS, Blocks.POLISHED_BLACKSTONE_STAIRS);
        map.put(Blocks.SANDSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        map.put(Blocks.SMOOTH_SANDSTONE_SLAB, Blocks.POLISHED_BLACKSTONE_SLAB);
        map.put(Blocks.SANDSTONE_WALL, Blocks.POLISHED_BLACKSTONE_WALL);

        map.put(Blocks.RED_SANDSTONE, Blocks.RED_NETHER_BRICKS);
        map.put(Blocks.SMOOTH_RED_SANDSTONE, Blocks.RED_NETHER_BRICKS);
        map.put(Blocks.CUT_RED_SANDSTONE, Blocks.RED_NETHER_BRICKS);
        map.put(Blocks.CHISELED_RED_SANDSTONE, Blocks.CHISELED_NETHER_BRICKS);
        map.put(Blocks.RED_SANDSTONE_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS);
        map.put(Blocks.SMOOTH_RED_SANDSTONE_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS);
        map.put(Blocks.RED_SANDSTONE_SLAB, Blocks.RED_NETHER_BRICK_SLAB);
        map.put(Blocks.SMOOTH_RED_SANDSTONE_SLAB, Blocks.RED_NETHER_BRICK_SLAB);
        map.put(Blocks.RED_SANDSTONE_WALL, Blocks.RED_NETHER_BRICK_WALL);

        map.put(Blocks.BRICKS, Blocks.RED_NETHER_BRICKS);
        map.put(Blocks.BRICK_STAIRS, Blocks.RED_NETHER_BRICK_STAIRS);
        map.put(Blocks.BRICK_SLAB, Blocks.RED_NETHER_BRICK_SLAB);
        map.put(Blocks.BRICK_WALL, Blocks.RED_NETHER_BRICK_WALL);

        // Lighting & glass
        map.put(Blocks.TORCH, Blocks.SOUL_TORCH);
        map.put(Blocks.WALL_TORCH, Blocks.SOUL_WALL_TORCH);
        map.put(Blocks.LANTERN, Blocks.SOUL_LANTERN);
        map.put(Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE);
        map.put(Blocks.GLASS, Blocks.RED_STAINED_GLASS);
        map.put(Blocks.GLASS_PANE, Blocks.RED_STAINED_GLASS_PANE);

        // Small plants
        Block roots = warped ? Blocks.WARPED_ROOTS : Blocks.CRIMSON_ROOTS;
        map.put(Blocks.GRASS, roots);
        map.put(Blocks.FERN, roots);
        map.put(Blocks.DEAD_BUSH, roots);
        map.put(Blocks.VINE, warped ? Blocks.TWISTING_VINES_PLANT : Blocks.WEEPING_VINES_PLANT);
    }

    public static BlockState mapToNether(BlockPos pos, BlockState oldState, RegistryEntry<Biome> targetBiome) {
        if (oldState.isAir()) {
            return oldState;
        }

        boolean warped = targetBiome != null && targetBiome.matchesKey(BiomeKeys.WARPED_FOREST);
        boolean soulValley = targetBiome != null && targetBiome.matchesKey(BiomeKeys.SOUL_SAND_VALLEY);
        boolean basaltDeltas = targetBiome != null && targetBiome.matchesKey(BiomeKeys.BASALT_DELTAS);

        Block oldBlock = oldState.getBlock();

        // Keep existing Nether blocks untouched
        if (isAlreadyNetherBlock(oldBlock)) {
            return oldState;
        }

        // Tall grass / tall flowers -> single nether root on lower half, air on upper half
        if (oldBlock == Blocks.TALL_GRASS || oldBlock == Blocks.LARGE_FERN || oldState.isIn(BlockTags.TALL_FLOWERS)) {
            if (oldState.contains(Properties.DOUBLE_BLOCK_HALF)
                    && oldState.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
                return Blocks.AIR.getDefaultState();
            }
            return (warped ? Blocks.WARPED_ROOTS : Blocks.CRIMSON_ROOTS).getDefaultState();
        }

        if (oldState.isIn(BlockTags.SMALL_FLOWERS) || oldState.isIn(BlockTags.SAPLINGS)) {
            boolean useFungus = ((pos.getX() * 31 + pos.getZ() * 17) & 7) == 0;
            if (useFungus) {
                return (warped ? Blocks.WARPED_FUNGUS : Blocks.CRIMSON_FUNGUS).getDefaultState();
            }
            return (warped ? Blocks.WARPED_ROOTS : Blocks.CRIMSON_ROOTS).getDefaultState();
        }

        if (oldState.isIn(BlockTags.LEAVES)) {
            Block wart = warped ? Blocks.WARPED_WART_BLOCK : Blocks.NETHER_WART_BLOCK;
            return copyMatchingProperties(oldState, wart);
        }

        // Biome-specific ground overrides for soul sand valley & basalt deltas
        if (soulValley && (oldBlock == Blocks.GRASS_BLOCK || oldBlock == Blocks.DIRT || oldBlock == Blocks.STONE)) {
            return Blocks.SOUL_SOIL.getDefaultState();
        }
        if (basaltDeltas && (oldBlock == Blocks.GRASS_BLOCK || oldBlock == Blocks.STONE)) {
            return Blocks.BASALT.getDefaultState();
        }

        Map<Block, Block> directMap = warped ? WARPED_MAP : CRIMSON_MAP;
        Block mapped = directMap.get(oldBlock);
        if (mapped != null) {
            return copyMatchingProperties(oldState, mapped);
        }

        // Terracotta (savanna / desert / badlands villages)
        if (oldState.isIn(BlockTags.TERRACOTTA)) {
            Block terracottaTarget = (oldBlock == Blocks.WHITE_TERRACOTTA || oldBlock == Blocks.YELLOW_TERRACOTTA)
                    ? Blocks.POLISHED_BLACKSTONE
                    : Blocks.RED_NETHER_BRICKS;
            return copyMatchingProperties(oldState, terracottaTarget);
        }

        // Overworld woods (logs, planks, stairs, slabs, fences, gates, doors, trapdoors, plates, buttons)
        Block woodMapped = mapWoodBlock(oldBlock, oldState, warped);
        if (woodMapped != null) {
            return copyMatchingProperties(oldState, woodMapped);
        }

        return oldState;
    }

    private static Block mapWoodBlock(Block oldBlock, BlockState oldState, boolean warped) {
        String path = Registries.BLOCK.getId(oldBlock).getPath();
        if (path.startsWith("crimson_") || path.startsWith("warped_")) {
            return null;
        }
        if (oldState.isIn(BlockTags.LOGS)) {
            boolean stripped = path.startsWith("stripped_");
            boolean woodOrHyphae = path.endsWith("_wood");
            if (warped) {
                if (woodOrHyphae) return stripped ? Blocks.STRIPPED_WARPED_HYPHAE : Blocks.WARPED_HYPHAE;
                return stripped ? Blocks.STRIPPED_WARPED_STEM : Blocks.WARPED_STEM;
            } else {
                if (woodOrHyphae) return stripped ? Blocks.STRIPPED_CRIMSON_HYPHAE : Blocks.CRIMSON_HYPHAE;
                return stripped ? Blocks.STRIPPED_CRIMSON_STEM : Blocks.CRIMSON_STEM;
            }
        }
        if (oldState.isIn(BlockTags.PLANKS)) {
            return warped ? Blocks.WARPED_PLANKS : Blocks.CRIMSON_PLANKS;
        }
        if (oldState.isIn(BlockTags.WOODEN_STAIRS)) {
            return warped ? Blocks.WARPED_STAIRS : Blocks.CRIMSON_STAIRS;
        }
        if (oldState.isIn(BlockTags.WOODEN_SLABS)) {
            return warped ? Blocks.WARPED_SLAB : Blocks.CRIMSON_SLAB;
        }
        if (oldState.isIn(BlockTags.WOODEN_FENCES)) {
            return warped ? Blocks.WARPED_FENCE : Blocks.CRIMSON_FENCE;
        }
        if (oldState.isIn(BlockTags.FENCE_GATES)) {
            return warped ? Blocks.WARPED_FENCE_GATE : Blocks.CRIMSON_FENCE_GATE;
        }
        if (oldState.isIn(BlockTags.WOODEN_DOORS)) {
            return warped ? Blocks.WARPED_DOOR : Blocks.CRIMSON_DOOR;
        }
        if (oldState.isIn(BlockTags.WOODEN_TRAPDOORS)) {
            return warped ? Blocks.WARPED_TRAPDOOR : Blocks.CRIMSON_TRAPDOOR;
        }
        if (oldState.isIn(BlockTags.WOODEN_PRESSURE_PLATES)) {
            return warped ? Blocks.WARPED_PRESSURE_PLATE : Blocks.CRIMSON_PRESSURE_PLATE;
        }
        if (oldState.isIn(BlockTags.WOODEN_BUTTONS)) {
            return warped ? Blocks.WARPED_BUTTON : Blocks.CRIMSON_BUTTON;
        }
        return null;
    }

    private static boolean isAlreadyNetherBlock(Block block) {
        String path = Registries.BLOCK.getId(block).getPath();
        return path.contains("nether")
                || path.contains("blackstone")
                || path.contains("basalt")
                || path.contains("crimson")
                || path.contains("warped")
                || path.contains("soul_")
                || path.equals("shroomlight")
                || path.equals("magma_block")
                || path.equals("glowstone");
    }

    private static BlockState copyMatchingProperties(BlockState source, Block targetBlock) {
        BlockState target = targetBlock.getDefaultState();
        for (Property<?> property : source.getProperties()) {
            if (target.contains(property)) {
                target = copyProperty(source, target, property);
            }
        }
        return target;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(BlockState source, BlockState target, Property<T> property) {
        return target.with(property, source.get(property));
    }
}
