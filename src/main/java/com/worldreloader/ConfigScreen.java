package com.worldreloader;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.AbstractInput;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    private Category currentCategory = Category.MAIN;

    private final List<TextFieldWidget> textFields = new ArrayList<>();

    public ConfigScreen(Screen parent) {
        super(Text.translatable("worldreloader.config.title"));
        this.parent = parent;
        this.config = WorldReloader.config;
    }

    enum Category {
        MAIN("text.autoconfig.worldreloader.category.Main"),
        GENERATION("text.autoconfig.worldreloader.category.Non-surface"),
        SURFACE("text.autoconfig.worldreloader.category.surface"),
        LINE("text.autoconfig.worldreloader.category.Line"),
        MAPPINGS("text.autoconfig.worldreloader.category.Structure Mappings"); // Use existing key

        final String translationKey;
        Category(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    @Override
    protected void init() {
        textFields.clear();

        int centerX = this.width / 2;
        int tabWidth = 70; // Slightly smaller to fit more tabs
        int totalTabsWidth = Category.values().length * (tabWidth + 5);
        int startX = centerX - totalTabsWidth / 2;

        // Category Tabs
        for (int i = 0; i < Category.values().length; i++) {
            Category cat = Category.values()[i];
            ButtonWidget button = new ButtonWidget.Builder(Text.translatable(cat.translationKey), b -> {
                saveCurrentTab();
                currentCategory = cat;
                this.clearAndInit();
            }).dimensions(startX + i * (tabWidth + 5), 30, tabWidth, 20).build();
            
            if (cat == currentCategory) button.active = false;
            this.addDrawableChild(button);
        }

        int startY = 65;
        int spacing = 25;

        switch (currentCategory) {
            case MAIN -> initMainTab(centerX, startY, spacing);
            case GENERATION -> initGenerationTab(centerX, startY, spacing);
            case SURFACE -> initSurfaceTab(centerX, startY, spacing);
            case LINE -> initLineTab(centerX, startY, spacing);
            case MAPPINGS -> initMappingsTab(centerX, startY, spacing);
        }
// ... rest of the buttons ...

        // Bottom Buttons
        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable("gui.done"), button -> {
            saveCurrentTab();
            config.save();
            this.client.setScreen(this.parent);
        }).dimensions(centerX - 155, this.height - 30, 150, 20).build());

        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable("gui.cancel"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(centerX + 5, this.height - 30, 150, 20).build());
    }

    private void initMainTab(int centerX, int startY, int spacing) {
        addIntField("text.autoconfig.worldreloader.option.maxRadius", config.maxRadius, centerX, startY, val -> config.maxRadius = val);
        addIntField("text.autoconfig.worldreloader.option.itemCleanupInterval", config.itemCleanupInterval, centerX, startY + spacing, val -> config.itemCleanupInterval = val);
        
        addToggle("text.autoconfig.worldreloader.option.Debug", config.Debug, centerX, startY + spacing * 2, val -> config.Debug = val);
        addToggle("text.autoconfig.worldreloader.option.preserveBeacon", config.preserveBeacon, centerX, startY + spacing * 3, val -> config.preserveBeacon = val);
        addToggle("text.autoconfig.worldreloader.option.UseSpecificPos", config.UseSpecificPos, centerX, startY + spacing * 4, val -> config.UseSpecificPos = val);

        addStringField("text.autoconfig.worldreloader.option.targetBlock", config.targetBlock, centerX, startY + spacing * 5, val -> config.targetBlock = val);
        addStringField("text.autoconfig.worldreloader.option.dimension", config.dimension, centerX, startY + spacing * 6, val -> config.dimension = val);
    }

    private void initGenerationTab(int centerX, int startY, int spacing) {
        addIntField("text.autoconfig.worldreloader.option.paddingCount", config.paddingCount, centerX, startY, val -> config.paddingCount = val);
        addIntField("text.autoconfig.worldreloader.option.totalSteps2", config.totalSteps2, centerX, startY + spacing, val -> config.totalSteps2 = val);
        addIntField("text.autoconfig.worldreloader.option.yMin", config.yMin, centerX, startY + spacing * 2, val -> config.yMin = val);
        addIntField("text.autoconfig.worldreloader.option.yMaxThanSurface", config.yMaxThanSurface, centerX, startY + spacing * 3, val -> config.yMaxThanSurface = val);
    }

    private void initSurfaceTab(int centerX, int startY, int spacing) {
        addToggle("text.autoconfig.worldreloader.option.UseSurface", config.UseSurface, centerX, startY, val -> config.UseSurface = val);
        addIntField("text.autoconfig.worldreloader.option.totalSteps", config.totalSteps, centerX, startY + spacing, val -> config.totalSteps = val);
        addIntField("text.autoconfig.worldreloader.option.HEIGHT", config.HEIGHT, centerX, startY + spacing * 2, val -> config.HEIGHT = val);
        addIntField("text.autoconfig.worldreloader.option.DEPTH", config.DEPTH, centerX, startY + spacing * 3, val -> config.DEPTH = val);
    }

    private void initLineTab(int centerX, int startY, int spacing) {
        addToggle("text.autoconfig.worldreloader.option.UseLine", config.UseLine, centerX, startY, val -> config.UseLine = val);
        addStringField("text.autoconfig.worldreloader.option.tool", config.tool, centerX, startY + spacing, val -> config.tool = val);
        addIntField("text.autoconfig.worldreloader.option.width", config.width, centerX, startY + spacing * 2, val -> config.width = val);
    }

    private void initMappingsTab(int centerX, int startY, int spacing) {
        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable("text.autoconfig.worldreloader.category.Structure Mappings"), button -> {
            this.client.setScreen(new MappingEditScreen<>(this, Text.translatable("text.autoconfig.worldreloader.category.Structure Mappings"), config.structureMappings, 
                () -> new ModConfig.StructureMapping("", ""), 
                (m, k) -> m.itemId = k, (m, v) -> m.structureId = v, m -> m.itemId, m -> m.structureId, (m, e) -> m.enabled = e, m -> m.enabled));
        }).dimensions(centerX - 100, startY, 200, 20).build());

        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable("text.autoconfig.worldreloader.category.Biome Mappings"), button -> {
            this.client.setScreen(new MappingEditScreen<>(this, Text.translatable("text.autoconfig.worldreloader.category.Biome Mappings"), config.biomeMappings, 
                () -> new ModConfig.BiomeMapping("", ""), 
                (m, k) -> m.itemId = k, (m, v) -> m.BiomeId = v, m -> m.itemId, m -> m.BiomeId, (m, e) -> m.enabled = e, m -> m.enabled));
        }).dimensions(centerX - 100, startY + spacing, 200, 20).build());

        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable("text.autoconfig.worldreloader.option.targetBlockDict"), button -> {
            this.client.setScreen(new MappingEditScreen<>(this, Text.translatable("text.autoconfig.worldreloader.option.targetBlockDict"), config.targetBlockDict, 
                () -> new ModConfig.ItemRequirement("", 1), 
                (m, k) -> m.itemId = k, (m, v) -> { try { m.count = Integer.parseInt(v); } catch (Exception ignored) {} }, m -> m.itemId, m -> String.valueOf(m.count), (m, e) -> m.enabled = e, m -> m.enabled));
        }).dimensions(centerX - 100, startY + spacing * 2, 200, 20).build());
    }

    private void addIntField(String labelKey, int value, int centerX, int y, java.util.function.Consumer<Integer> setter) {
        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable(labelKey), button -> {}).dimensions(centerX - 155, y, 150, 20).build()).active = false;
        TextFieldWidget widget = new TextFieldWidget(this.textRenderer, centerX + 5, y, 150, 20, Text.translatable(labelKey));
        widget.setText(String.valueOf(value));
        widget.setChangedListener(s -> {
            try {
                setter.accept(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {}
        });
        this.addDrawableChild(widget);
        textFields.add(widget);
    }

    private void addStringField(String labelKey, String value, int centerX, int y, java.util.function.Consumer<String> setter) {
        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable(labelKey), button -> {}).dimensions(centerX - 155, y, 150, 20).build()).active = false;
        TextFieldWidget widget = new TextFieldWidget(this.textRenderer, centerX + 5, y, 150, 20, Text.translatable(labelKey));
        widget.setText(value);
        widget.setChangedListener(setter);
        this.addDrawableChild(widget);
        textFields.add(widget);
    }

    private void addToggle(String labelKey, boolean value, int centerX, int y, java.util.function.Consumer<Boolean> setter) {
        // Custom button for toggle to avoid complex state management
        this.addDrawableChild(new ToggleButton(centerX - 155, y, 310, 20, labelKey, value, setter));
    }

    private Text getToggleText(String labelKey, boolean value) {
        return Text.translatable(labelKey).append(": ").append(value ? Text.literal("§aON") : Text.literal("§cOFF"));
    }

    private void saveCurrentTab() {
        // Values are already saved via listeners in this implementation
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    private static class ToggleButton extends ButtonWidget {
        private final String labelKey;
        private boolean value;
        private final java.util.function.Consumer<Boolean> setter;

        public ToggleButton(int x, int y, int width, int height, String labelKey, boolean initialValue, java.util.function.Consumer<Boolean> setter) {
            super(x, y, width, height, Text.empty(), b -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.labelKey = labelKey;
            this.value = initialValue;
            this.setter = setter;
            this.updateMessage();
        }

        @Override
        public void onPress(AbstractInput input) {
            this.value = !this.value;
            this.setter.accept(this.value);
            this.updateMessage();
        }






        private void updateMessage() {
            this.setMessage(Text.translatable(labelKey).append(": ").append(value ? Text.literal("§aON") : Text.literal("§cOFF")));
        }
    }
}
