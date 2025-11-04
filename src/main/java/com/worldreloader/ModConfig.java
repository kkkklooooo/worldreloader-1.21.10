package com.worldreloader;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

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


    // ============ 新增的物品-结构映射配置 ============
    @ConfigEntry.Category("Structure Mappings")
    @ConfigEntry.Gui.Tooltip(count = 2)
    //List<Float> f = new ArrayList<>();
    public List<StructureMapping> structureMappings = new ArrayList<>();



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
