package com.worldreloader;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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
        addModeButton("worldreloader.config.mode.standard", ModConfig.OperationMode.STANDARD, centerX - 135, 30, tabWidth);
        addModeButton("worldreloader.config.mode.surface", ModConfig.OperationMode.SURFACE, centerX - 42, 30, tabWidth);
        addModeButton("worldreloader.config.mode.line", ModConfig.OperationMode.LINE, centerX + 50, 30, tabWidth);

        // --- 2. Middle: Scrolling List ---
        this.list = new ConfigList();
        this.addSelectableChild(this.list);

        // --- 3. Bottom: Footer ---
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> {
            config.save();
            syncConfigToServer();
            this.client.setScreen(this.parent);
        }).dimensions(centerX - 155, this.height - 28, 150, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.cancel"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(centerX + 5, this.height - 28, 150, 20).build());
    }

    private void addModeButton(String labelKey, ModConfig.OperationMode mode, int x, int y, int w) {
        boolean active = config.mode == mode;
        Text title = Text.literal(active ? COLOR_GOLD + COLOR_BOLD : "").append(Text.translatable(labelKey));
        this.addDrawableChild(ButtonWidget.builder(title, b -> {
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
            addSection("worldreloader.config.section.basic");
            addSteppedInt("worldreloader.config.option.max_radius", config.maxRadius, 1, 512, val -> config.maxRadius = val);
            addInt("worldreloader.config.option.item_cleanup_interval", config.itemCleanupInterval, val -> config.itemCleanupInterval = val);
            addToggle("worldreloader.config.option.preserve_beacon", config.preserveBeacon, val -> config.preserveBeacon = val);
            addToggle("worldreloader.config.option.debug_logging", config.Debug, val -> config.Debug = val);

            // --- SECTION: TARGET ---
            addSection("worldreloader.config.section.target");
            addString("worldreloader.config.option.target_block", config.targetBlock, val -> config.targetBlock = val);
            addMapping("worldreloader.config.option.manage_item_requirements", "targetBlockDict");
            addString("worldreloader.config.option.target_dimension", config.dimension, val -> config.dimension = val);

            // --- SECTION: POSITION ---
            addSection("worldreloader.config.section.position");
            addEntry(new PosModeEntry());
            if (config.posMode == ModConfig.PositionMode.FIXED) {
                addInt("worldreloader.config.option.fixed_x", config.Posx, val -> config.Posx = val);
                addInt("worldreloader.config.option.fixed_y", config.Posy, val -> config.Posy = val);
                addInt("worldreloader.config.option.fixed_z", config.Posz, val -> config.Posz = val);
            } else if (config.posMode == ModConfig.PositionMode.BIOME) {
                addString("worldreloader.config.option.target_biome", config.targetBiomeId, val -> config.targetBiomeId = val);
                addInt("worldreloader.config.option.biome_search_range", config.searchRadius, val -> config.searchRadius = val);
            } else if (config.posMode == ModConfig.PositionMode.RANDOM) {
                addInt("worldreloader.config.option.random_offset_radius", config.randomRadius, val -> config.randomRadius = val);
            }

            // --- SECTION: MODE SPECIFIC ---
            addSection(modeSettingsKey(config.mode));
            if (config.mode == ModConfig.OperationMode.SURFACE) {
                addInt("worldreloader.config.option.surface_total_steps", config.totalSteps, val -> config.totalSteps = val);
                addInt("worldreloader.config.option.fill_height", config.HEIGHT, val -> config.HEIGHT = val);
                addInt("worldreloader.config.option.fill_depth", config.DEPTH, val -> config.DEPTH = val);
            } else if (config.mode == ModConfig.OperationMode.LINE) {
                addString("worldreloader.config.option.line_tool", config.tool, val -> config.tool = val);
                addInt("worldreloader.config.option.line_width", config.width, val -> config.width = val);
                addMapping("worldreloader.config.option.manage_saved_positions", "savedPositions");
            }

            // --- ADVANCED ---
            Text advText = Text.literal(ICON_GEAR).append(Text.translatable(advancedExpanded ? "worldreloader.config.advanced.hide" : "worldreloader.config.advanced.show"));
            addEntry(new SingleWidgetEntry(ButtonWidget.builder(advText, b -> { 
                advancedExpanded = !advancedExpanded; clearAndInit(); 
            }).dimensions(0, 0, 310, 20).build(), true));

            if (advancedExpanded) {
                addInt("worldreloader.config.option.boundary_padding", config.paddingCount, val -> config.paddingCount = val);
                addInt("worldreloader.config.option.min_y", config.yMin, val -> config.yMin = val);
                addInt("worldreloader.config.option.max_y_above_surface", config.yMaxThanSurface, val -> config.yMaxThanSurface = val);
                addMapping("worldreloader.config.option.edit_biome_mappings", "biomeMappings");
                addMapping("worldreloader.config.option.edit_structure_mappings", "structureMappings");
            }
        }

        private String modeSettingsKey(ModConfig.OperationMode mode) {
            return switch (mode) {
                case SURFACE -> "worldreloader.config.section.surface_mode";
                case LINE -> "worldreloader.config.section.line_mode";
                default -> "worldreloader.config.section.standard_mode";
            };
        }

        private void addSection(String titleKey) {
            Text styledTitle = Text.literal(COLOR_AQUA + "--- ").append(Text.translatable(titleKey)).append(Text.literal(" ---"));
            ButtonWidget b = ButtonWidget.builder(styledTitle, btn -> {}).dimensions(0, 0, 310, 20).build();
            b.active = false;
            addEntry(new SingleWidgetEntry(b, false));
        }
        
        private void addInt(String labelKey, int val, Consumer<Integer> setter) {
            ButtonWidget l = ButtonWidget.builder(Text.literal(COLOR_YELLOW).append(Text.translatable(labelKey)), b -> {}).dimensions(0, 0, 150, 20).build();
            l.active = false;
            TextFieldWidget v = new TextFieldWidget(textRenderer, 0, 0, 150, 20, Text.empty());
            v.setText(String.valueOf(val));
            v.setChangedListener(s -> { try { setter.accept(Integer.parseInt(s)); } catch (Exception ignored) {} });
            addEntry(new DualWidgetEntry(l, v));
        }

        private void addSteppedInt(String labelKey, int val, int min, int max, Consumer<Integer> setter) {
            ButtonWidget l = ButtonWidget.builder(Text.literal(COLOR_YELLOW).append(Text.translatable(labelKey)), b -> {}).dimensions(0, 0, 150, 20).build();
            l.active = false;
            ButtonWidget minus = ButtonWidget.builder(Text.literal("-"), b -> { setter.accept(Math.max(min, val - 1)); clearAndInit(); }).dimensions(0, 0, 20, 20).build();
            ButtonWidget plus = ButtonWidget.builder(Text.literal("+"), b -> { setter.accept(Math.min(max, val + 1)); clearAndInit(); }).dimensions(0, 0, 20, 20).build();
            ButtonWidget display = ButtonWidget.builder(Text.literal(String.valueOf(val)), b -> {}).dimensions(0, 0, 100, 20).build();
            display.active = false;
            addEntry(new SteppedEntry(l, minus, display, plus));
        }

        private void addString(String labelKey, String val, Consumer<String> setter) {
            ButtonWidget l = ButtonWidget.builder(Text.literal(COLOR_YELLOW).append(Text.translatable(labelKey)), b -> {}).dimensions(0, 0, 150, 20).build();
            l.active = false;
            TextFieldWidget v = new TextFieldWidget(textRenderer, 0, 0, 150, 20, Text.empty());
            v.setText(val);
            v.setChangedListener(setter);
            addEntry(new DualWidgetEntry(l, v));
        }

        private void addToggle(String labelKey, boolean val, Consumer<Boolean> setter) {
            Text toggleText = Text.literal(COLOR_YELLOW)
                    .append(Text.translatable(labelKey))
                    .append(Text.literal(": " + (val ? COLOR_GREEN + COLOR_BOLD : COLOR_RED + COLOR_BOLD)))
                    .append(Text.translatable(val ? "worldreloader.config.value.on" : "worldreloader.config.value.off"));
            ButtonWidget b = ButtonWidget.builder(toggleText, btn -> { setter.accept(!val); clearAndInit(); }).dimensions(0, 0, 310, 20).build();
            addEntry(new SingleWidgetEntry(b, true));
        }

        private void addMapping(String labelKey, String type) {
            ButtonWidget b = ButtonWidget.builder(Text.translatable(labelKey).append("..."), btn -> openMappingScreen(type)).dimensions(0, 0, 310, 20).build();
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
            bFixed = createBtn("worldreloader.config.position.fixed", ModConfig.PositionMode.FIXED);
            bBiome = createBtn("worldreloader.config.position.biome", ModConfig.PositionMode.BIOME);
            bRandom = createBtn("worldreloader.config.position.random", ModConfig.PositionMode.RANDOM);
        }
        private ButtonWidget createBtn(String labelKey, ModConfig.PositionMode m) {
            boolean active = config.posMode == m;
            return ButtonWidget.builder(Text.literal(active ? COLOR_GOLD : "").append(Text.translatable(labelKey)), b -> { config.posMode = m; clearAndInit(); }).dimensions(0, 0, 100, 20).build();
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

    private void syncConfigToServer() {
        try {
            if (ClientPlayNetworking.canSend(ConfigSyncPayload.ID)) {
                ClientPlayNetworking.send(new ConfigSyncPayload(config.toJson()));
            } else if (this.client != null && this.client.player != null) {
                this.client.player.sendMessage(Text.literal("§e当前服务器不支持 World Reloader 配置同步，已仅保存客户端配置"), false);
            }
        } catch (Exception e) {
            WorldReloader.LOGGER.warn("无法同步 World Reloader 配置到服务器", e);
        }
    }

    private void openMappingScreen(String type) {
        switch (type) {
            case "targetBlockDict": this.client.setScreen(new MappingEditScreen<>(this, Text.translatable("worldreloader.config.title.item_requirements"), config.targetBlockDict, () -> new ModConfig.ItemRequirement("", 1), (m, k) -> m.itemId = k, (m, v) -> { try { m.count = Integer.parseInt(v); } catch (Exception ignored) {} }, m -> m.itemId, m -> String.valueOf(m.count), (m, e) -> m.enabled = e, m -> m.enabled)); break;
            case "savedPositions": this.client.setScreen(new MappingEditScreen<>(this, Text.translatable("worldreloader.config.title.saved_positions"), config.savedPositions, () -> new ModConfig.SavedPosition(0, 0, 0), (m, k) -> {}, (m, v) -> {}, m -> m.toString(), m -> "", (m, e) -> {}, m -> true)); break;
            case "biomeMappings": this.client.setScreen(new MappingEditScreen<>(this, Text.translatable("worldreloader.config.title.biome_mappings"), config.biomeMappings, () -> new ModConfig.BiomeMapping("", ""), (m, k) -> m.itemId = k, (m, v) -> m.BiomeId = v, m -> m.itemId, m -> m.BiomeId, (m, e) -> m.enabled = e, m -> m.enabled)); break;
            case "structureMappings": this.client.setScreen(new MappingEditScreen<>(this, Text.translatable("worldreloader.config.title.structure_mappings"), config.structureMappings, () -> new ModConfig.StructureMapping("", ""), (m, k) -> m.itemId = k, (m, v) -> m.structureId = v, m -> m.itemId, m -> m.structureId, (m, e) -> m.enabled = e, m -> m.enabled)); break;
        }
    }
}
