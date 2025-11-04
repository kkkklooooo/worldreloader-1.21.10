package com.worldreloader;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "worldreloader")
class ModConfig implements ConfigData {

    @ConfigEntry.Category("Main")
    boolean UseSurface = true;
    @ConfigEntry.Category("Main")
    int maxRadius = 88;
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
    int paddingCount = 24;

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
