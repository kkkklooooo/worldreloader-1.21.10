package com.worldreloader.client;

import com.worldreloader.ModConfig;
import com.worldreloader.WorldReloader;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.gui.registry.GuiRegistry;
import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.gui.entries.IntegerListEntry;
import me.shedaniel.clothconfig2.gui.entries.TooltipListEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registers Cloth Config UI helpers for capturing the player's current position into the
 * whitelist min/max coordinate fields.
 */
public final class WhitelistConfigGui {
    private static final Map<String, WeakReference<IntegerListEntry>> BOUND_ENTRIES = new HashMap<>();
    private static boolean registered = false;

    private WhitelistConfigGui() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        GuiRegistry registry = AutoConfig.getGuiRegistry(ModConfig.class);

        registry.registerPredicateTransformer((entries, i13n, field, config, defaults, guiProvider) -> {
            if (!entries.isEmpty() && entries.get(0) instanceof IntegerListEntry intEntry) {
                BOUND_ENTRIES.put(field.getName(), new WeakReference<>(intEntry));
            }
            return entries;
        }, field -> field.getDeclaringClass() == ModConfig.class && isWhitelistCoordField(field.getName()));

        registry.registerPredicateProvider((i13n, field, config, defaults, guiProvider) -> {
            boolean isMin = "captureWhitelistMin".equals(field.getName());
            Text label = Text.translatable(i13n);
            Optional<Text[]> tooltip = Optional.of(new Text[]{
                    Text.translatable(i13n + ".@Tooltip")
            });
            return List.of(new CapturePositionButtonEntry(label, isMin, () -> tooltip));
        }, field -> field.getDeclaringClass() == ModConfig.class
                && ("captureWhitelistMin".equals(field.getName()) || "captureWhitelistMax".equals(field.getName())));
    }

    private static boolean isWhitelistCoordField(String name) {
        return "whitelistMinX".equals(name) || "whitelistMinY".equals(name) || "whitelistMinZ".equals(name)
                || "whitelistMaxX".equals(name) || "whitelistMaxY".equals(name) || "whitelistMaxZ".equals(name);
    }

    private static void setIntEntryValue(String fieldName, int value) {
        WeakReference<IntegerListEntry> ref = BOUND_ENTRIES.get(fieldName);
        if (ref != null) {
            IntegerListEntry entry = ref.get();
            if (entry != null) {
                entry.setValue(String.valueOf(value));
            }
        }
    }

    private static final class CapturePositionButtonEntry extends TooltipListEntry<Boolean> {
        private final boolean isMin;
        private final ButtonWidget buttonWidget;
        private final List<ButtonWidget> widgets;

        @SuppressWarnings("deprecation")
        CapturePositionButtonEntry(Text fieldName, boolean isMin,
                                   java.util.function.Supplier<Optional<Text[]>> tooltipSupplier) {
            super(fieldName, tooltipSupplier, false);
            this.isMin = isMin;
            Text initialText = isMin
                    ? Text.translatable("text.worldreloader.capture_min.button")
                    : Text.translatable("text.worldreloader.capture_max.button");
            this.buttonWidget = ButtonWidget.builder(initialText, btn -> onCaptureClicked())
                    .dimensions(0, 0, 150, 20)
                    .build();
            this.widgets = List.of(this.buttonWidget);
        }

        private void onCaptureClicked() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                this.buttonWidget.setMessage(Text.translatable("text.worldreloader.capture_ingame_only"));
                return;
            }

            BlockPos pos = client.player.getBlockPos();
            if (isMin) {
                setIntEntryValue("whitelistMinX", pos.getX());
                setIntEntryValue("whitelistMinY", pos.getY());
                setIntEntryValue("whitelistMinZ", pos.getZ());
                if (WorldReloader.config != null) {
                    WorldReloader.config.whitelistMinX = pos.getX();
                    WorldReloader.config.whitelistMinY = pos.getY();
                    WorldReloader.config.whitelistMinZ = pos.getZ();
                }
                this.buttonWidget.setMessage(Text.literal(
                        String.format("Min: (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())));
            } else {
                setIntEntryValue("whitelistMaxX", pos.getX());
                setIntEntryValue("whitelistMaxY", pos.getY());
                setIntEntryValue("whitelistMaxZ", pos.getZ());
                if (WorldReloader.config != null) {
                    WorldReloader.config.whitelistMaxX = pos.getX();
                    WorldReloader.config.whitelistMaxY = pos.getY();
                    WorldReloader.config.whitelistMaxZ = pos.getZ();
                }
                this.buttonWidget.setMessage(Text.literal(
                        String.format("Max: (%d, %d, %d)", pos.getX(), pos.getY(), pos.getZ())));
            }
        }

        @Override
        public void render(DrawContext graphics, int index, int y, int x, int entryWidth, int entryHeight,
                           int mouseX, int mouseY, boolean isHovered, float delta) {
            super.render(graphics, index, y, x, entryWidth, entryHeight, mouseX, mouseY, isHovered, delta);
            MinecraftClient client = MinecraftClient.getInstance();
            graphics.drawTextWithShadow(client.textRenderer, getDisplayedFieldName(), x, y + 6, getPreferredTextColor());

            this.buttonWidget.setX(x + entryWidth - 150);
            this.buttonWidget.setY(y);
            this.buttonWidget.setWidth(150);
            this.buttonWidget.render(graphics, mouseX, mouseY, delta);
        }

        @Override
        public Boolean getValue() {
            return false;
        }

        @Override
        public Optional<Boolean> getDefaultValue() {
            return Optional.empty();
        }

        @Override
        public boolean isEdited() {
            return false;
        }

        @Override
        public void save() {
            // Coordinates are stored in whitelistMin*/whitelistMax* fields.
        }

        @Override
        public List<? extends Element> children() {
            return this.widgets;
        }

        @Override
        public List<? extends Selectable> narratables() {
            return this.widgets;
        }
    }
}
