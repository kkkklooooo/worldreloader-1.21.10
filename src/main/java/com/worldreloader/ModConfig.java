package com.worldreloader;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@Config(name = "worldreloader")
public class ModConfig implements ConfigData {



    @ConfigEntry.Category("Main")
    boolean UseSpecificPos = false;
    @ConfigEntry.Category("Main")
    int Posx;
    @ConfigEntry.Category("Main")
    int Posy;
    @ConfigEntry.Category("Main")
    int Posz;
    @ConfigEntry.Category("Main")
    int maxRadius = 76;
    @ConfigEntry.Category("Main")
    int itemCleanupInterval = 20;
    @ConfigEntry.Category("Main")
    public boolean Debug = false;
    @ConfigEntry.Category("Main")
    public boolean preserveBeacon = true;
    @ConfigEntry.Category("Main")
    public List<ItemRequirement> targetBlockDict = List.of(
            new ItemRequirement("minecraft:nether_star", 1)
    );

    @ConfigEntry.Category("Main")
    String targetBlock = "minecraft:beacon";
    @ConfigEntry.Category("Main")
    String dimension = "minecraft:overworld";

    @ConfigEntry.Category("Non-surface")
    int paddingCount = 12;
    @ConfigEntry.Category("Non-surface")
    int totalSteps2 = 3;
    @ConfigEntry.Category("Non-surface")
    int yMin = 40;
    @ConfigEntry.Category("Non-surface")
    int yMaxThanSurface = 30;

    @ConfigEntry.Category("surface")
    boolean UseSurface = false;
    @ConfigEntry.Category("surface")
    @ConfigEntry.Gui.Tooltip(count = 1)
    int totalSteps = 10;
    @ConfigEntry.Category("surface")
    int HEIGHT = 15;
    @ConfigEntry.Category("surface")
    int DEPTH = 15;



    @ConfigEntry.Category("Line")
    boolean UseLine=false;
    @ConfigEntry.Category("Line")
    String tool = "minecraft:wooden_shovel";
    @ConfigEntry.Category("Line")
    public int width=5;
    @ConfigEntry.Category("Line")
    public List<SavedPosition> savedPositions = new ArrayList<>();

    public static class ItemRequirement {
        public String itemId;  // 改为 String 类型
        public int count;
        public boolean enabled = true;

        public ItemRequirement() {}

        public ItemRequirement(String itemId, int count) {
            this.itemId = itemId;
            this.count = count;
        }
    }
    // ... 现有的其他字段和方法 ...

    public static class SavedPosition {
        public int x;
        public int y;
        public int z;
        public long timestamp;

        public SavedPosition() {}

        public SavedPosition(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = System.currentTimeMillis();
        }

        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }

        @Override
        public String toString() {
            return String.format("(%d, %d, %d)", x, y, z);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            SavedPosition that = (SavedPosition) obj;
            return x == that.x && y == that.y && z == that.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }

    // ============ 新增的物品-结构映射配置 ============
    @ConfigEntry.Category("Structure Mappings")
    //List<Float> f = new ArrayList<>();
    public List<StructureMapping> structureMappings = List.of(
            new StructureMapping(Registries.BLOCK.getId(Blocks.TARGET).getPath(), "village_snowy"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.COBBLESTONE).getPath(), "minecraft:pillager_outpost"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.MOSSY_COBBLESTONE).getPath(), "minecraft:jungle_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.SMOOTH_SANDSTONE).getPath(), "minecraft:desert_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.BOOKSHELF).getPath(), "minecraft:mansion")
    );

    @ConfigEntry.Category("Biome Mappings")
    public List<BiomeMapping> biomeMappings = List.of(

            new BiomeMapping(Registries.BLOCK.getId(Blocks.GRASS_BLOCK).getPath(), BiomeKeys.PLAINS.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.JUNGLE_LOG).getPath(), BiomeKeys.JUNGLE.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.SAND).getPath(), BiomeKeys.DESERT.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.SNOW_BLOCK).getPath(), BiomeKeys.SNOWY_PLAINS.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.DARK_OAK_LOG).getPath(), BiomeKeys.DARK_FOREST.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.MYCELIUM).getPath(), BiomeKeys.MUSHROOM_FIELDS.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.OAK_LOG).getPath(), BiomeKeys.FOREST.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.AMETHYST_BLOCK).getPath(), BiomeKeys.FLOWER_FOREST.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.HAY_BLOCK).getPath(), BiomeKeys.SUNFLOWER_PLAINS.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.MOSS_BLOCK).getPath(), BiomeKeys.SWAMP.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.PODZOL).getPath(), BiomeKeys.OLD_GROWTH_PINE_TAIGA.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.MUD).getPath(), BiomeKeys.MANGROVE_SWAMP.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.SANDSTONE).getPath(), BiomeKeys.BADLANDS.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.RED_SANDSTONE).getPath(), BiomeKeys.ERODED_BADLANDS.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.ICE).getPath(), BiomeKeys.ICE_SPIKES.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.PACKED_ICE).getPath(), BiomeKeys.FROZEN_PEAKS.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.BIRCH_LOG).getPath(), BiomeKeys.BIRCH_FOREST.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.SPRUCE_LOG).getPath(), BiomeKeys.TAIGA.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.ACACIA_LOG).getPath(), BiomeKeys.SAVANNA.getValue().getPath()),
            new BiomeMapping(Registries.BLOCK.getId(Blocks.CHERRY_LOG).getPath(), BiomeKeys.CHERRY_GROVE.getValue().getPath())
    );



    public static class StructureMapping {
        public String itemId="";
        public String structureId="";
        public boolean enabled = true;

        public StructureMapping(String itemId, String structureId) {
            this.itemId = itemId;
            this.structureId = structureId;
        }
        public StructureMapping() {
        }

    }

    public static class BiomeMapping {
        public String itemId;
        public String BiomeId;
        public boolean enabled = true;


        public BiomeMapping(String itemId, String BiomeId) {
            this.itemId = itemId;
            this.BiomeId = BiomeId;

        }
        public BiomeMapping() {
        }

    }

    public HashMap<String,String> ToHash(){
        HashMap<String,String> map = new HashMap<>();
        for (StructureMapping i :this.structureMappings){
            map.put(i.itemId,i.structureId);
        }
        return map;
    }


//    @ConfigEntry.Gui.CollapsibleObject
//    InnerStuff stuff = new InnerStuff();
//
//    @ConfigEntry.Gui.Excluded
//    InnerStuff invisibleStuff = new InnerStuff();
//
//    static class InnerStuff {
//        int a = 0;
//        int b = 1;
//    }
}
