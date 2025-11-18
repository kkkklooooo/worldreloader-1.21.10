package com.worldreloader;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;
import me.shedaniel.autoconfig.annotation.ConfigEntry.Gui.CollapsibleObject;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.world.biome.BiomeKeys;

import java.util.HashMap;
import java.util.List;

@Config(name = "worldreloader")
public class ModConfig implements ConfigData {

    // ============ 主配置 ============
    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    public int targetYmin = 64;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    boolean UseSpecificPos = false;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    int Posx;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    int Posy;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    int Posz;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    boolean UseSurface = false;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    int maxRadius = 76;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    int itemCleanupInterval = 20;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    boolean isChangeBiome = false;

    @ConfigEntry.Category("Main")
    @ConfigEntry.Gui.Tooltip
    boolean Debug = false;

    // ============ 线段改造配置 ============
    @ConfigEntry.Category("Line Transformation")
    @ConfigEntry.Gui.Tooltip
    public boolean useLineTransformation = false;

    @ConfigEntry.Category("Line Transformation")
    @ConfigEntry.Gui.Tooltip
    public int lineStartX = 0;

    @ConfigEntry.Category("Line Transformation")
    @ConfigEntry.Gui.Tooltip
    public int lineStartZ = 0;

    @ConfigEntry.Category("Line Transformation")
    @ConfigEntry.Gui.Tooltip
    public int lineEndX = 100;

    @ConfigEntry.Category("Line Transformation")
    @ConfigEntry.Gui.Tooltip
    public int lineEndZ = 100;

    @ConfigEntry.Category("Line Transformation")
    @ConfigEntry.Gui.Tooltip
    public int lineWidth = 3;

    @ConfigEntry.Category("Line Transformation")
    @ConfigEntry.Gui.Tooltip
    public int maxLineLength = 1000;

    // ============ 表面改造配置 ============
    @ConfigEntry.Category("Surface Transformation")
    @ConfigEntry.Gui.Tooltip(count = 1)
    int totalSteps = 10;

    @ConfigEntry.Category("Surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int DESTROY_DEPTH = 15;

    @ConfigEntry.Category("Surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int DESTROY_HEIGHT = 15;

    @ConfigEntry.Category("Surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int COPY_DEPTH = 15;

    @ConfigEntry.Category("Surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int COPY_HEIGHT = 15;

    // ============ 非表面改造配置 ============
    @ConfigEntry.Category("Non-surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int paddingCount = 12;

    @ConfigEntry.Category("Non-surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int totalSteps2 = 3;

    @ConfigEntry.Category("Non-surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int yMin = 40;

    @ConfigEntry.Category("Non-surface Transformation")
    @ConfigEntry.Gui.Tooltip
    int yMaxThanSurface = 30;

    // ============ 结构映射配置 ============
    @ConfigEntry.Category("Structure Mappings")
    @ConfigEntry.Gui.Tooltip
    public List<StructureMapping> structureMappings = List.of(
            new StructureMapping(Registries.BLOCK.getId(Blocks.TARGET).getPath(), "village_plains"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.COBBLESTONE).getPath(), "minecraft:pillager_outpost"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.MOSSY_COBBLESTONE).getPath(), "minecraft:jungle_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.SMOOTH_SANDSTONE).getPath(), "minecraft:desert_pyramid"),
            new StructureMapping(Registries.BLOCK.getId(Blocks.BOOKSHELF).getPath(), "minecraft:mansion")
    );

    // ============ 生物群系映射配置 ============
    @ConfigEntry.Category("Biome Mappings")
    @ConfigEntry.Gui.Tooltip
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

    // ============ 结构映射类 ============
    public static class StructureMapping {
        @ConfigEntry.Gui.Tooltip
        public String itemId;

        @ConfigEntry.Gui.Tooltip
        public String structureId;

        @ConfigEntry.Gui.Tooltip
        public boolean enabled = true;

        public StructureMapping() {}

        public StructureMapping(String itemId, String structureId) {
            this.itemId = itemId;
            this.structureId = structureId;
        }
    }

    // ============ 生物群系映射类 ============
    public static class BiomeMapping {
        @ConfigEntry.Gui.Tooltip
        public String itemId;

        @ConfigEntry.Gui.Tooltip
        public String BiomeId;

        @ConfigEntry.Gui.Tooltip
        public boolean enabled = true;

        public BiomeMapping() {}

        public BiomeMapping(String itemId, String BiomeId) {
            this.itemId = itemId;
            this.BiomeId = BiomeId;
        }
    }

    // ============ 辅助方法 ============
    public HashMap<String, String> ToHash() {
        HashMap<String, String> map = new HashMap<>();
        for (StructureMapping i : this.structureMappings) {
            if (i.enabled) {
                map.put(i.itemId, i.structureId);
            }
        }
        return map;
    }

    /**
     * 验证线段配置的有效性
     */
    public boolean validateLineConfig() {
        double length = Math.sqrt(
                Math.pow(lineEndX - lineStartX, 2) +
                        Math.pow(lineEndZ - lineStartZ, 2)
        );

        if (length > maxLineLength) {
            return false;
        }

        if (lineWidth < 1 || lineWidth > 20) {
            return false;
        }

        return true;
    }

    /**
     * 获取线段长度
     */
    public double getLineLength() {
        return Math.sqrt(
                Math.pow(lineEndX - lineStartX, 2) +
                        Math.pow(lineEndZ - lineStartZ, 2)
        );
    }

    /**
     * 设置线段起点
     */
    public void setLineStart(int x, int z) {
        this.lineStartX = x;
        this.lineStartZ = z;
    }

    /**
     * 设置线段终点
     */
    public void setLineEnd(int x, int z) {
        this.lineEndX = x;
        this.lineEndZ = z;
    }

    /**
     * 设置特定位置
     */
    public void setSpecificPos(int x, int y, int z) {
        this.Posx = x;
        this.Posy = y;
        this.Posz = z;
        this.UseSpecificPos = true;
    }
}