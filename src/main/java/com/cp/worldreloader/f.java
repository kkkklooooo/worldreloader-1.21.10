package com.cp.worldreloader;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.serializer.JanksonConfigSerializer;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Config(name = "worldreloader")
public class ModConfig implements ConfigData {
    @ConfigEntry.Category("Main")
    public int targetYmin = 64;

    @ConfigEntry.Category("Main")
    boolean UseSpecificPos = false;

    @ConfigEntry.Category("Main")
    int Posx;

    @ConfigEntry.Category("Main")
    int Posy;

    @ConfigEntry.Category("Main")
    int Posz;

    @ConfigEntry.Category("Main")
    boolean UseSurface = false;

    @ConfigEntry.Category("Main")
    int maxRadius = 76;

    @ConfigEntry.Category("Main")
    int itemCleanupInterval = 20;

    @ConfigEntry.Category("Main")
    boolean isChangeBiome = false;

    @ConfigEntry.Category("Main")
    boolean Debug = false;

    @ConfigEntry.Category("surface")
    @ConfigEntry.Gui.Tooltip(count = 1)
    int totalSteps = 10;

    @ConfigEntry.Category("surface")
    int DESTROY_DEPTH = 15;

    @ConfigEntry.Category("surface")
    int DESTROY_HEIGHT = 15;

    @ConfigEntry.Category("surface")
    int COPY_DEPTH = 15;

    @ConfigEntry.Category("surface")
    int COPY_HEIGHT = 15;

    @ConfigEntry.Category("Non-surface")
    int paddingCount = 12;

    @ConfigEntry.Category("Non-surface")
    int totalSteps2 = 3;

    @ConfigEntry.Category("Non-surface")
    int yMin = 40;

    @ConfigEntry.Category("Non-surface")
    int yMaxThanSurface = 30;

    @ConfigEntry.Category("Structure Mappings")
    public List<StructureMapping> structureMappings = new ArrayList<>();

    @ConfigEntry.Category("Biome Mappings")
    public List<BiomeMapping> biomeMappings = new ArrayList<>();

    public ModConfig() {
        // 初始化默认映射
        structureMappings = createDefaultStructureMappings();
        biomeMappings = createDefaultBiomeMappings();
    }

    private List<StructureMapping> createDefaultStructureMappings() {
        List<StructureMapping> mappings = new ArrayList<>();
        mappings.add(new StructureMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.TARGET).getPath(),
                "village_plains"
        ));
        mappings.add(new StructureMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.COBBLESTONE).getPath(),
                "minecraft:pillager_outpost"
        ));
        mappings.add(new StructureMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.MOSSY_COBBLESTONE).getPath(),
                "minecraft:jungle_pyramid"
        ));
        mappings.add(new StructureMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.SMOOTH_SANDSTONE).getPath(),
                "minecraft:desert_pyramid"
        ));
        mappings.add(new StructureMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.BOOKSHELF).getPath(),
                "minecraft:mansion"
        ));
        return mappings;
    }

    private List<BiomeMapping> createDefaultBiomeMappings() {
        List<BiomeMapping> mappings = new ArrayList<>();
        mappings.add(new BiomeMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.GRASS_BLOCK).getPath(),
                Biomes.PLAINS.location().getPath()
        ));
        mappings.add(new BiomeMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.JUNGLE_LOG).getPath(),
                Biomes.JUNGLE.location().getPath()
        ));
        mappings.add(new BiomeMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.SAND).getPath(),
                Biomes.DESERT.location().getPath()
        ));
        mappings.add(new BiomeMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.SNOW_BLOCK).getPath(),
                Biomes.SNOWY_PLAINS.location().getPath()
        ));
        mappings.add(new BiomeMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.DARK_OAK_LOG).getPath(),
                Biomes.DARK_FOREST.location().getPath()
        ));
        mappings.add(new BiomeMapping(
                BuiltInRegistries.BLOCK.getKey(Blocks.MYCELIUM).getPath(),
                Biomes.MUSHROOM_FIELDS.location().getPath()
        ));
        // ... 添加其他生物群系映射
        return mappings;
    }

    public static class StructureMapping {
        public String itemId;
        public String structureId;
        public boolean enabled = true;

        public StructureMapping() {}

        public StructureMapping(String itemId, String structureId) {
            this.itemId = itemId;
            this.structureId = structureId;
        }
    }

    public static class BiomeMapping {
        public String itemId;
        public String BiomeId;
        public boolean enabled = true;

        public BiomeMapping() {}

        public BiomeMapping(String itemId, String BiomeId) {
            this.itemId = itemId;
            this.BiomeId = BiomeId;
        }
    }

    public HashMap<String, String> ToHash() {
        HashMap<String, String> map = new HashMap<>();
        for (StructureMapping i : this.structureMappings) {
            map.put(i.itemId, i.structureId);
        }
        return map;
    }
}