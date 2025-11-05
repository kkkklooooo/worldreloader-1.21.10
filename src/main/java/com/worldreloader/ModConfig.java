package com.worldreloader;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.world.biome.BiomeKeys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Config(name = "worldreloader")
class ModConfig implements ConfigData {

    @ConfigEntry.Category("Main")
    boolean UseSurface = false;
    @ConfigEntry.Category("Main")
    int maxRadius = 76;
    @ConfigEntry.Category("Main")
    int itemCleanupInterval = 20;
    @ConfigEntry.Category("Main")
    boolean isChangeBiome=false;
    @ConfigEntry.Category("Main")
    boolean Debug = false;
    //int lastCleanupRadius = -1;

    @ConfigEntry.Category("surface")
    // 控制改造速度的间隔变量 - 改为10刻完成一个半径
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
    // 控制改造速度的间隔变量
    int totalSteps2 = 3;


    // ============ 新增的物品-结构映射配置 ============
    @ConfigEntry.Category("Structure Mappings")
    //List<Float> f = new ArrayList<>();
    public List<StructureMapping> structureMappings = List.of(
            new StructureMapping(Registries.BLOCK.getId(Blocks.TARGET).getPath(), "village"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.COBBLESTONE).getPath(), "pillager_outpost"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.MOSSY_COBBLESTONE).getPath(), "jungle_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.SMOOTH_SANDSTONE).getPath(), "desert_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.BOOKSHELF).getPath(), "mansion")
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
