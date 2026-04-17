package com.worldreloader;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.Consumer;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    private ConfigList list;
    private boolean advancedExpanded = false;

    // Unicode Color Constants to avoid encoding issues
    private static final String COLOR_AQUA = "\u00A7b";
    private static final String COLOR_YELLOW = "\u00A7e";
    private static final String COLOR_GREEN = "\u00A7a";
    private static final String COLOR_RED = "\u00A7c";
    private static final String COLOR_GOLD = "\u00A76";
    private static final String COLOR_BOLD = "\u00A7l";
    private static final String ICON_GEAR = "\u2699 ";

    public ConfigScreen(Screen parent) {
        super(Text.translatable("worldreloader.config.title"));
        this.parent = parent;
        this.config = WorldReloader.config;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        
        // --- 1. Top Bar: Mode Selector ---
        int tabWidth = 85;
        addModeButton("Standard", ModConfig.OperationMode.STANDARD, centerX - 135, 30, tabWidth);
        addModeButton("Surface", ModConfig.OperationMode.SURFACE, centerX - 42, 30, tabWidth);
        addModeButton("Line", ModConfig.OperationMode.LINE, centerX + 50, 30, tabWidth);

        // --- 2. Middle: Scrolling List ---
        this.list = new ConfigList();
        this.addSelectableChild(this.list);

        // --- 3. Bottom: Footer ---
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> {
            config.save();
            this.client.setScreen(this.parent);
        }).dimensions(centerX - 155, this.height - 28, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(centerX + 5, this.height - 28, 150, 20).build());
    }

    private void addModeButton(String name, ModConfig.OperationMode mode, int x, int y, int w) {
        boolean active = config.mode == mode;
        String title = (active ? COLOR_GOLD + COLOR_BOLD : "") + name;
        this.addDrawableChild(ButtonWidget.builder(Text.literal(title), b -> {
            config.mode = mode;
            clearAndInit();
        }).dimensions(x, y, w, 20).build()).active = !active;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.list.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.list.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private class ConfigList extends ElementListWidget<ConfigEntry> {
        public ConfigList() {
            super(ConfigScreen.this.client, ConfigScreen.this.width, ConfigScreen.this.height - 95, 55, 24);
            buildList();
        }

        private void buildList() {
            this.clearEntries();
            
            // --- SECTION: BASIC ---
            addSection("Basic Parameters");
            addSteppedInt("Max Operation Radius", config.maxRadius, 1, 512, val -> config.maxRadius = val);
            addInt("Item Cleanup Interval", config.itemCleanupInterval, val -> config.itemCleanupInterval = val);
            addToggle("Preserve Beacon Block", config.preserveBeacon, val -> config.preserveBeacon = val);
            addToggle("Debug Logging", config.Debug, val -> config.Debug = val);

            // --- SECTION: TARGET ---
            addSection("Target Configuration");
            addString("Target Block ID", config.targetBlock, val -> config.targetBlock = val);
            addMapping("Manage Item Requirements", "targetBlockDict");
            addString("Target Dimension", config.dimension, val -> config.dimension = val);

            // --- SECTION: POSITION ---
            addSection("Position Selection");
            addEntry(new PosModeEntry());
            if (config.posMode == ModConfig.PositionMode.FIXED) {
                addInt("Fixed X Coordinate", config.Posx, val -> config.Posx = val);
                addInt("Fixed Y Coordinate", config.Posy, val -> config.Posy = val);
                addInt("Fixed Z Coordinate", config.Posz, val -> config.Posz = val);
            } else if (config.posMode == ModConfig.PositionMode.BIOME) {
                addString("Target Biome ID", config.targetBiomeId, val -> config.targetBiomeId = val);
                addInt("Biome Search Range", config.searchRadius, val -> config.searchRadius = val);
            } else if (config.posMode == ModConfig.PositionMode.RANDOM) {
                addInt("Random Offset Radius", config.randomRadius, val -> config.randomRadius = val);
            }

            // --- SECTION: MODE SPECIFIC ---
            addSection(config.mode.name() + " Mode Settings");
            if (config.mode == ModConfig.OperationMode.SURFACE) {
                addInt("Surface Total Steps", config.totalSteps, val -> config.totalSteps = val);
                addInt("Fill Height (Above)", config.HEIGHT, val -> config.HEIGHT = val);
                addInt("Fill Depth (Below)", config.DEPTH, val -> config.DEPTH = val);
            } else if (config.mode == ModConfig.OperationMode.LINE) {
                addString("Line Tool Item ID", config.tool, val -> config.tool = val);
                addInt("Line Brush Width", config.width, val -> config.width = val);
                addMapping("Manage Saved Positions", "savedPositions");
            }

            // --- ADVANCED ---
            String advText = ICON_GEAR + (advancedExpanded ? "Hide Advanced Settings" : "Show Advanced Settings");
            addEntry(new SingleWidgetEntry(ButtonWidget.builder(Text.literal(advText), b -> { 
                advancedExpanded = !advancedExpanded; clearAndInit(); 
            }).dimensions(0, 0, 310, 20).build(), true));

            if (advancedExpanded) {
                addInt("Boundary Padding", config.paddingCount, val -> config.paddingCount = val);
                addInt("Minimum Y Level", config.yMin, val -> config.yMin = val);
                addMapping("Edit Biome Mappings", "biomeMappings");
                addMapping("Edit Structure Mappings", "structureMappings");
            }
        }

        private void addSection(String title) {
            String styledTitle = COLOR_AQUA + "--- " + title + " ---";
            ButtonWidget b = ButtonWidget.builder(Text.literal(styledTitle), btn -> {}).dimensions(0, 0, 310, 20).build();
            b.active = false;
            addEntry(new SingleWidgetEntry(b, false));
        }
        
        private void addInt(String label, int val, Consumer<Integer> setter) {
            ButtonWidget l = ButtonWidget.builder(Text.literal(COLOR_YELLOW + label), b -> {}).dimensions(0, 0, 150, 20).build();
            l.active = false;
            TextFieldWidget v = new TextFieldWidget(textRenderer, 0, 0, 150, 20, Text.empty());
            v.setText(String.valueOf(val));
            v.setChangedListener(s -> { try { setter.accept(Integer.parseInt(s)); } catch (Exception ignored) {} });
            addEntry(new DualWidgetEntry(l, v));
        }

        private void addSteppedInt(String label, int val, int min, int max, Consumer<Integer> setter) {
            ButtonWidget l = ButtonWidget.builder(Text.literal(COLOR_YELLOW + label), b -> {}).dimensions(0, 0, 150, 20).build();
            l.active = false;
            ButtonWidget minus = ButtonWidget.builder(Text.literal("-"), b -> { setter.accept(Math.max(min, val - 1)); clearAndInit(); }).dimensions(0, 0, 20, 20).build();
            ButtonWidget plus = ButtonWidget.builder(Text.literal("+"), b -> { setter.accept(Math.min(max, val + 1)); clearAndInit(); }).dimensions(0, 0, 20, 20).build();
            ButtonWidget display = ButtonWidget.builder(Text.literal(String.valueOf(val)), b -> {}).dimensions(0, 0, 100, 20).build();
            display.active = false;
            addEntry(new SteppedEntry(l, minus, display, plus));
        }

        private void addString(String label, String val, Consumer<String> setter) {
            ButtonWidget l = ButtonWidget.builder(Text.literal(COLOR_YELLOW + label), b -> {}).dimensions(0, 0, 150, 20).build();
            l.active = false;
            TextFieldWidget v = new TextFieldWidget(textRenderer, 0, 0, 150, 20, Text.empty());
            v.setText(val);
            v.setChangedListener(setter);
            addEntry(new DualWidgetEntry(l, v));
        }

        private void addToggle(String label, boolean val, Consumer<Boolean> setter) {
            String toggleText = COLOR_YELLOW + label + ": " + (val ? COLOR_GREEN + COLOR_BOLD + "ON" : COLOR_RED + COLOR_BOLD + "OFF");
            ButtonWidget b = ButtonWidget.builder(Text.literal(toggleText), btn -> { setter.accept(!val); clearAndInit(); }).dimensions(0, 0, 310, 20).build();
            addEntry(new SingleWidgetEntry(b, true));
        }

        private void addMapping(String label, String type) {
            ButtonWidget b = ButtonWidget.builder(Text.literal(label + "..."), btn -> openMappingScreen(type)).dimensions(0, 0, 310, 20).build();
            addEntry(new SingleWidgetEntry(b, true));
        }

        @Override public int getRowWidth() { return 310; }
        @Override protected int getScrollbarX() { return this.width / 2 + 160; }
    }

    private abstract class ConfigEntry extends ElementListWidget.Entry<ConfigEntry> {
        @Override public abstract List<? extends Element> children();
        @Override public abstract List<? extends Selectable> selectableChildren();
    }

    private class SingleWidgetEntry extends ConfigEntry {
        private final ClickableWidget widget;
        public SingleWidgetEntry(ClickableWidget widget, boolean active) { this.widget = widget; this.widget.active = active; }
        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            widget.setX(ConfigScreen.this.width / 2 - 155); widget.setY(this.getY()); widget.render(context, mouseX, mouseY, delta);
        }
        @Override public List<? extends Element> children() { return List.of(widget); }
        @Override public List<? extends Selectable> selectableChildren() { return List.of(widget); }
    }

    private class DualWidgetEntry extends ConfigEntry {
        private final ClickableWidget label, value;
        public DualWidgetEntry(ClickableWidget label, ClickableWidget value) { this.label = label; this.value = value; }
        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = ConfigScreen.this.width / 2 - 155; int y = this.getY();
            label.setX(x); label.setY(y); label.render(context, mouseX, mouseY, delta);
            value.setX(x + 160); value.setY(y); value.render(context, mouseX, mouseY, delta);
        }
        @Override public List<? extends Element> children() { return List.of(label, value); }
        @Override public List<? extends Selectable> selectableChildren() { return List.of(label, value); }
    }

    private class SteppedEntry extends ConfigEntry {
        private final ClickableWidget label, minus, display, plus;
        public SteppedEntry(ClickableWidget label, ClickableWidget minus, ClickableWidget display, ClickableWidget plus) {
            this.label = label; this.minus = minus; this.display = display; this.plus = plus;
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = ConfigScreen.this.width / 2 - 155; int y = this.getY();
            label.setX(x); label.setY(y); label.render(context, mouseX, mouseY, delta);
            minus.setX(x + 160); minus.setY(y); minus.render(context, mouseX, mouseY, delta);
            display.setX(x + 185); display.setY(y); display.render(context, mouseX, mouseY, delta);
            plus.setX(x + 290); plus.setY(y); plus.render(context, mouseX, mouseY, delta);
        }
        @Override public List<? extends Element> children() { return List.of(label, minus, display, plus); }
        @Override public List<? extends Selectable> selectableChildren() { return List.of(label, minus, display, plus); }
    }

    private class PosModeEntry extends ConfigEntry {
        private final ButtonWidget bFixed, bBiome, bRandom;
        public PosModeEntry() {
            bFixed = createBtn("Fixed", ModConfig.PositionMode.FIXED);
            bBiome = createBtn("Biome", ModConfig.PositionMode.BIOME);
            bRandom = createBtn("Random", ModConfig.PositionMode.RANDOM);
        }
        private ButtonWidget createBtn(String name, ModConfig.PositionMode m) {
            boolean active = config.posMode == m;
            return ButtonWidget.builder(Text.literal((active ? COLOR_GOLD : "") + name), b -> { config.posMode = m; clearAndInit(); }).dimensions(0, 0, 100, 20).build();
        }
        @Override public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float delta) {
            int x = ConfigScreen.this.width / 2 - 155; int y = this.getY();
            bFixed.active = (config.posMode != ModConfig.PositionMode.FIXED);
            bBiome.active = (config.posMode != ModConfig.PositionMode.BIOME);
            bRandom.active = (config.posMode != ModConfig.PositionMode.RANDOM);
            bFixed.setX(x); bFixed.setY(y); bFixed.render(context, mouseX, mouseY, delta);
            bBiome.setX(x + 105); bBiome.setY(y); bBiome.render(context, mouseX, mouseY, delta);
            bRandom.setX(x + 210); bRandom.setY(y); bRandom.render(context, mouseX, mouseY, delta);
        }
        @Override public List<? extends Element> children() { return List.of(bFixed, bBiome, bRandom); }
        @Override public List<? extends Selectable> selectableChildren() { return List.of(bFixed, bBiome, bRandom); }
    }

    private void openMappingScreen(String type) {
        switch (type) {
            case "targetBlockDict": this.client.setScreen(new MappingEditScreen<>(this, Text.literal("Item Requirements"), config.targetBlockDict, () -> new ModConfig.ItemRequirement("", 1), (m, k) -> m.itemId = k, (m, v) -> { try { m.count = Integer.parseInt(v); } catch (Exception ignored) {} }, m -> m.itemId, m -> String.valueOf(m.count), (m, e) -> m.enabled = e, m -> m.enabled)); break;
            case "savedPositions": this.client.setScreen(new MappingEditScreen<>(this, Text.literal("Saved Positions"), config.savedPositions, () -> new ModConfig.SavedPosition(0, 0, 0), (m, k) -> {}, (m, v) -> {}, m -> m.toString(), m -> "", (m, e) -> {}, m -> true)); break;
            case "biomeMappings": this.client.setScreen(new MappingEditScreen<>(this, Text.literal("Biome Mappings"), config.biomeMappings, () -> new ModConfig.BiomeMapping("", ""), (m, k) -> m.itemId = k, (m, v) -> m.BiomeId = v, m -> m.itemId, m -> m.BiomeId, (m, e) -> m.enabled = e, m -> m.enabled)); break;
            case "structureMappings": this.client.setScreen(new MappingEditScreen<>(this, Text.literal("Structure Mappings"), config.structureMappings, () -> new ModConfig.StructureMapping("", ""), (m, k) -> m.itemId = k, (m, v) -> m.structureId = v, m -> m.itemId, m -> m.structureId, (m, e) -> m.enabled = e, m -> m.enabled)); break;
        }
    }
}
