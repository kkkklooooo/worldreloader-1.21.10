package com.worldreloader;

import me.shedaniel.autoconfig.gui.registry.api.GuiProvider;
import me.shedaniel.autoconfig.gui.registry.api.GuiRegistryAccess;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.DropdownMenuBuilder;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class StructureMappingGuiProvider implements GuiProvider {

    private static final List<String> COMMON_ITEMS = Arrays.asList(
            "minecraft:diamond",
            "minecraft:emerald",
            "minecraft:netherite_scrap",
            "minecraft:gold_ingot",
            "minecraft:iron_ingot",
            "minecraft:coal",
            "minecraft:redstone",
            "minecraft:lapis_lazuli",
            "minecraft:quartz",
            "minecraft:amethyst_shard"
    );

    private static final List<String> VANILLA_STRUCTURES = Arrays.asList(
            "minecraft:village",
            "minecraft:pillager_outpost",
            "minecraft:mansion",
            "minecraft:stronghold",
            "minecraft:fortress",
            "minecraft:monument",
            "minecraft:ruined_portal",
            "minecraft:ancient_city",
            "minecraft:end_city"
    );

//    @Override
//    public List<AbstractConfigListEntry> get(String i18n, Field field, Object config, Object defaults, ConfigRegistry registry) {
//        if (field.getType() == List.class && "structureMappings".equals(field.getName())) {
//            return createStructureMappingGUI(i18n, field, config, defaults);
//        }
//        return null;
//    }
//
//    @SuppressWarnings("unchecked")
//    private List<AbstractConfigListEntry> createStructureMappingGUI(String i18n, Field field, Object config, Object defaults) {
//        try {
//            List<ModConfig.StructureMapping> mappings = (List<ModConfig.StructureMapping>) field.get(config);
//            List<ModConfig.StructureMapping> defaultMappings = (List<ModConfig.StructureMapping>) field.get(defaults);
//
//            ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();
//            List<AbstractConfigListEntry> entries = new ArrayList<>();
//
//            // 说明文本
//            entries.add(entryBuilder.startTextDescription(
//                            Text.translatable("text.your_mod.structure_mapping_description"))
//                    .build());
//
//            // 添加新映射按钮
//            entries.add(entryBuilder.startButton(
//                            Text.translatable("button.your_mod.add_mapping"),
//                            Text.translatable("button.your_mod.add"))
//                    .setButtonConsumer(btn -> addNewMapping(mappings))
//                    .build());
//
//            // 映射列表
//            for (int i = 0; i < mappings.size(); i++) {
//                entries.add(createSingleMappingEntry(entryBuilder, mappings, i, defaultMappings));
//            }
//
//            return entries;
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return Collections.singletonList(
//                    ConfigEntryBuilder.create().startTextDescription(Text.literal("Error loading structure mappings")).build()
//            );
//        }
//    }

    private AbstractConfigListEntry createSingleMappingEntry(ConfigEntryBuilder entryBuilder,
                                                             List<ModConfig.StructureMapping> mappings,
                                                             int index,
                                                             List<ModConfig.StructureMapping> defaultMappings) {
        ModConfig.StructureMapping mapping = mappings.get(index);

        List<AbstractConfigListEntry> mappingEntries = new ArrayList<>();

        // 物品选择器
        mappingEntries.add(entryBuilder.startDropdownMenu(
                        Text.translatable("option.your_mod.item"),
                DropdownMenuBuilder.TopCellElementBuilder.of(mapping.itemId,(s)->s))
                .setDefaultValue(getDefaultValue(defaultMappings, index, "itemId", "minecraft:diamond"))
                .setSelections(COMMON_ITEMS)
                .setSaveConsumer(newValue -> mapping.itemId = newValue)
                .build());

        // 结构选择器
        mappingEntries.add(entryBuilder.startDropdownMenu(
                        Text.translatable("option.your_mod.structure"),
                DropdownMenuBuilder.TopCellElementBuilder.of(mapping.structureId,s->s))
                .setDefaultValue(getDefaultValue(defaultMappings, index, "structureId", "minecraft:village"))
                .setSelections(VANILLA_STRUCTURES)
                .setSaveConsumer(newValue -> mapping.structureId = newValue)
                .build());

        // 启用开关
        mappingEntries.add(entryBuilder.startBooleanToggle(
                        Text.translatable("option.your_mod.enabled"),
                        mapping.enabled)
                .setDefaultValue(getDefaultBoolean(defaultMappings, index, true))
                .setSaveConsumer(newValue -> mapping.enabled = newValue)
                .build());

        // 操作按钮
//        List<AbstractConfigListEntry> buttonEntries = new ArrayList<>();
//        buttonEntries.add(entryBuilder.start(
//                        Text.translatable("button.your_mod.remove"),
//                        Text.translatable("button.your_mod.remove_confirm"))
//                .setButtonConsumer(btn -> removeMapping(mappings, index))
//                .build());

//        mappingEntries.add(entryBuilder.startSubCategory(
//                        Text.translatable("category.your_mod.actions"),
//                        buttonEntries)
//                .setExpanded(false)
//                .build());

        return entryBuilder.startSubCategory(
                        Text.translatable("category.your_mod.mapping_entry", index + 1,
                                getSimpleItemName(mapping.itemId),
                                getSimpleStructureName(mapping.structureId)),
                        mappingEntries)
                .setExpanded(true)
                .build();
    }

    private String getDefaultValue(List<ModConfig.StructureMapping> defaultMappings, int index, String field, String fallback) {
        if (index < defaultMappings.size()) {
            try {
                Field f = ModConfig.StructureMapping.class.getField(field);
                return (String) f.get(defaultMappings.get(index));
            } catch (Exception e) {
                return fallback;
            }
        }
        return fallback;
    }

    private boolean getDefaultBoolean(List<ModConfig.StructureMapping> defaultMappings, int index, boolean fallback) {
        return index < defaultMappings.size() ? defaultMappings.get(index).enabled : fallback;
    }

    private String getSimpleItemName(String itemId) {
        return itemId.replace("minecraft:", "");
    }

    private String getSimpleStructureName(String structureId) {
        return structureId.replace("minecraft:", "");
    }

    private void addNewMapping(List<ModConfig.StructureMapping> mappings) {
        mappings.add(new ModConfig.StructureMapping("minecraft:diamond", "minecraft:village"));
    }

    private void removeMapping(List<ModConfig.StructureMapping> mappings, int index) {
        if (index >= 0 && index < mappings.size()) {
            mappings.remove(index);
        }
    }

    @Override
    public List<AbstractConfigListEntry> get(String i18n, Field field, Object config, Object defaults, GuiRegistryAccess registry) {
        if (field.getType() == List.class && "structureMappings".equals(field.getName())) {
            return createStructureMappingGUI(i18n, field, config, defaults, registry);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<AbstractConfigListEntry> createStructureMappingGUI(String i18n, Field field, Object config, Object defaults, GuiRegistryAccess registry) {
        try {
            List<ModConfig.StructureMapping> mappings = (List<ModConfig.StructureMapping>) field.get(config);
            List<ModConfig.StructureMapping> defaultMappings = (List<ModConfig.StructureMapping>) field.get(defaults);

            ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();
            List<AbstractConfigListEntry> entries = new ArrayList<>();

            // 说明文本
            entries.add(entryBuilder.startTextDescription(
                            Text.translatable("text.your_mod.structure_mapping_description"))
                    .build());

//            // 添加新映射按钮
//            entries.add(entryBuilder.startButton(
//                            Text.translatable("button.your_mod.add_mapping"),
//                            Text.translatable("button.your_mod.add"))
//                    .setButtonConsumer(btn -> addNewMapping(mappings))
//                    .build());

            // 映射列表
            for (int i = 0; i < mappings.size(); i++) {
                entries.add(createSingleMappingEntry(entryBuilder, mappings, i, defaultMappings));
            }

            return entries;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.singletonList(
                    ConfigEntryBuilder.create().startTextDescription(Text.literal("Error loading structure mappings")).build()
            );
        }
    }
}


//public class StructureMappingGuiProvider implements GuiProvider {
//
//    @Override
//    public List<AbstractConfigListEntry> get(String i18n, Field field, Object config, Object defaults, ConfigRegistry registry) {
//        if (field.getType() == List.class && field.getGenericType() instanceof ParameterizedType) {
//            ParameterizedType type = (ParameterizedType) field.getGenericType();
//            if (type.getActualTypeArguments()[0] == ModConfig.StructureMapping.class) {
//                return createStructureMappingEntry(i18n, field, config, defaults);
//            }
//        }
//        return null;
//    }
//
//    private List<AbstractConfigListEntry> createStructureMappingEntry(String i18n, Field field, Object config, Object defaults) {
//        try {
//            @SuppressWarnings("unchecked")
//            List<ModConfig.StructureMapping> mappings = (List<ModConfig.StructureMapping>) field.get(config);
//            @SuppressWarnings("unchecked")
//            List<ModConfig.StructureMapping> defaultMappings = (List<ModConfig.StructureMapping>) field.get(defaults);
//
//            ConfigEntryBuilder entryBuilder = ConfigEntryBuilder.create();
//
//            // 创建映射列表条目
//            return Arrays.asList(
//                    entryBuilder.startTextDescription(Text.translatable("text.my_mod.structure_mapping_help"))
//                            .build(),
//
//                    entryBuilder.startButton(Text.translatable("button.my_mod.add_mapping"), Text.translatable("button.my_mod.add"))
//                            .setButtonConsumer(btn -> addNewMapping(mappings))
//                            .build(),
//
//                    createMappingListEntry(entryBuilder, i18n, mappings, defaultMappings, config, field)
//            );
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//    }
//
//    private AbstractConfigListEntry createMappingListEntry(ConfigEntryBuilder entryBuilder, String i18n,
//                                                           List<ModConfig.StructureMapping> mappings,
//                                                           List<ModConfig.StructureMapping> defaultMappings,
//                                                           Object config, Field field) {
//        return entryBuilder.startSubCategory(Text.translatable(i18n), createMappingEntries(entryBuilder, mappings, defaultMappings, config, field))
//                .setExpanded(true)
//                .build();
//    }
//
//    private List<AbstractConfigListEntry> createMappingEntries(ConfigEntryBuilder entryBuilder,
//                                                               List<ModConfig.StructureMapping> mappings,
//                                                               List<ModConfig.StructureMapping> defaultMappings,
//                                                               Object config, Field field) {
//        List<AbstractConfigListEntry> entries = new ArrayList<>();
//
//        for (int i = 0; i < mappings.size(); i++) {
//            final int index = i;
//            ModConfig.StructureMapping mapping = mappings.get(i);
//
//            // 每个映射创建一个子类别
//            List<AbstractConfigListEntry> mappingEntries = new ArrayList<>();
//
//            // 物品选择器
//            mappingEntries.add(
//                    entryBuilder.startTextField(Text.translatable("option.my_mod.item_id"), mapping.itemId)
//                            .setDefaultValue(getDefaultItemId(defaultMappings, index))
//                            .setSaveConsumer(newValue -> mapping.itemId = newValue)
//                            .setTooltip(Text.translatable("tooltip.my_mod.item_id"))
//                            .build()
//            );
//
//            // 结构选择器
//            mappingEntries.add(
//                    entryBuilder.startTextField(Text.translatable("option.my_mod.structure_id"), mapping.structureId)
//                            .setDefaultValue(getDefaultStructureId(defaultMappings, index))
//                            .setSaveConsumer(newValue -> mapping.structureId = newValue)
//                            .setTooltip(Text.translatable("tooltip.my_mod.structure_id"))
//                            .build()
//            );
//
//            // 启用开关
//            mappingEntries.add(
//                    entryBuilder.startBooleanToggle(Text.translatable("option.my_mod.enabled"), mapping.enabled)
//                            .setDefaultValue(getDefaultEnabled(defaultMappings, index))
//                            .setSaveConsumer(newValue -> mapping.enabled = newValue)
//                            .build()
//            );
//
//            // 删除按钮
//            mappingEntries.add(
//                    entryBuilder.startButton(Text.translatable("button.my_mod.remove"), Text.translatable("button.my_mod.remove_confirm"))
//                            .setButtonConsumer(btn -> removeMapping(mappings, index))
//                            .build()
//            );
//
//            entries.add(
//                    entryBuilder.startSubCategory(Text.translatable("category.my_mod.mapping", i + 1), mappingEntries)
//                            .setExpanded(true)
//                            .build()
//            );
//        }
//
//        return entries;
//    }
//
//    private String getDefaultItemId(List<ModConfig.StructureMapping> defaultMappings, int index) {
//        return index < defaultMappings.size() ? defaultMappings.get(index).itemId : "";
//    }
//
//    private String getDefaultStructureId(List<ModConfig.StructureMapping> defaultMappings, int index) {
//        return index < defaultMappings.size() ? defaultMappings.get(index).structureId : "";
//    }
//
//    private boolean getDefaultEnabled(List<ModConfig.StructureMapping> defaultMappings, int index) {
//        return index < defaultMappings.size() ? defaultMappings.get(index).enabled : true;
//    }
//
//    private void addNewMapping(List<ModConfig.StructureMapping> mappings) {
//        mappings.add(new ModConfig.StructureMapping("minecraft:stone", "minecraft:village"));
//    }
//
//    private void removeMapping(List<ModConfig.StructureMapping> mappings, int index) {
//        if (index >= 0 && index < mappings.size()) {
//            mappings.remove(index);
//        }
//    }
//
//    @Override
//    public List<AbstractConfigListEntry> get(String s, Field field, Object o, Object o1, GuiRegistryAccess guiRegistryAccess) {
//        return List.of();
//    }
//}
