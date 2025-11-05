package com.worldreloader;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Config(name = "worldreloader")
class ModConfig implements ConfigData {

    @ConfigEntry.Category("Main")
    boolean UseSurface = true;
    @ConfigEntry.Category("Main")
    int maxRadius = 88;
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
    int paddingCount = 24;
    @ConfigEntry.Category("Non-surface")
    // 控制改造速度的间隔变量
    int totalSteps2 = 1;


    // ============ 新增的物品-结构映射配置 ============
    @ConfigEntry.Category("Structure Mappings")
    @ConfigEntry.Gui.Tooltip(count = 2)
    //List<Float> f = new ArrayList<>();
    public List<StructureMapping> structureMappings = List.of(
            new StructureMapping(Registries.BLOCK.getId(Blocks.TARGET).getPath(), "village"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.COBBLESTONE).getPath(), "pillager_outpost"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.MOSSY_COBBLESTONE).getPath(), "jungle_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.SMOOTH_SANDSTONE).getPath(), "desert_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.BOOKSHELF).getPath(), "mansion")
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
