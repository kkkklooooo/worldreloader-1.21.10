package com.worldreloader;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MappingEditScreen<T> extends Screen {
    private final Screen parent;
    private final List<T> list;
    private final Supplier<T> factory;
    private final BiConsumer<T, String> keySetter;
    private final BiConsumer<T, String> valueSetter;
    private final Function<T, String> keyGetter;
    private final Function<T, String> valueGetter;
    private final BiConsumer<T, Boolean> enabledSetter;
    private final Function<T, Boolean> enabledGetter;

    private MappingList<T> mappingList;

    public MappingEditScreen(Screen parent, Text title, List<T> list, Supplier<T> factory,
                             BiConsumer<T, String> keySetter, BiConsumer<T, String> valueSetter,
                             Function<T, String> keyGetter, Function<T, String> valueGetter,
                             BiConsumer<T, Boolean> enabledSetter, Function<T, Boolean> enabledGetter) {
        super(title);
        this.parent = parent;
        this.list = list;
        this.factory = factory;
        this.keySetter = keySetter;
        this.valueSetter = valueSetter;
        this.keyGetter = keyGetter;
        this.valueGetter = valueGetter;
        this.enabledSetter = enabledSetter;
        this.enabledGetter = enabledGetter;
    }

    @Override
    protected void init() {
        this.mappingList = new MappingList<>(this);
        this.addSelectableChild(this.mappingList);

        this.addDrawableChild(new ButtonWidget.Builder(Text.literal("+"), button -> {
            T newItem = factory.get();
            list.add(newItem);
            mappingList.publicAddEntry(new MappingEntry<>(this, newItem));
        }).dimensions(this.width / 2 - 155, this.height - 30, 20, 20).build());

        this.addDrawableChild(new ButtonWidget.Builder(Text.translatable("gui.done"), button -> {
            this.client.setScreen(this.parent);
        }).dimensions(this.width / 2 - 130, this.height - 30, 260, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.mappingList.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
    }

    public static class MappingList<T> extends ElementListWidget<MappingEntry<T>> {
        private final MappingEditScreen<T> screen;

        public MappingList(MappingEditScreen<T> screen) {
            super(screen.client, screen.width, screen.height - 70, 40, 25);
            this.screen = screen;
            for (T item : screen.list) {
                this.addEntry(new MappingEntry<>(screen, item));
            }
        }

        public void publicAddEntry(MappingEntry<T> entry) {
            this.addEntry(entry);
        }

        public void publicRemoveEntry(MappingEntry<T> entry) {
            this.removeEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return 300;
        }

        @Override
        protected int getScrollbarX() {
            return this.width / 2 + 155;
        }

    }

    public static class MappingEntry<T> extends ElementListWidget.Entry<MappingEntry<T>> {
        private final MappingEditScreen<T> screen;
        private final T item;
        private final TextFieldWidget keyField;
        private final TextFieldWidget valueField;
        private final ButtonWidget enabledButton;
        private final ButtonWidget deleteButton;

        public MappingEntry(MappingEditScreen<T> screen, T item) {
            this.screen = screen;
            this.item = item;
            this.keyField = new TextFieldWidget(screen.textRenderer, 0, 0, 100, 20, Text.empty());
            this.keyField.setText(screen.keyGetter.apply(item));
            this.keyField.setChangedListener(s -> screen.keySetter.accept(item, s));

            this.valueField = new TextFieldWidget(screen.textRenderer, 0, 0, 100, 20, Text.empty());
            this.valueField.setText(screen.valueGetter.apply(item));
            this.valueField.setChangedListener(s -> screen.valueSetter.accept(item, s));

            this.enabledButton = new ButtonWidget.Builder(getEnabledText(), b -> {
                boolean next = !screen.enabledGetter.apply(item);
                screen.enabledSetter.accept(item, next);
                b.setMessage(getEnabledText());
            }).dimensions(0, 0, 40, 20).build();

            this.deleteButton = new ButtonWidget.Builder(Text.literal("X"), b -> {
                screen.list.remove(item);
                screen.mappingList.publicRemoveEntry(this);
            }).dimensions(0, 0, 20, 20).build();
        }

        private Text getEnabledText() {
            return screen.enabledGetter.apply(item) ? Text.literal("§aV") : Text.literal("§cX");
        }

        @Override
        public List<? extends Element> children() {
            return List.of(keyField, valueField, enabledButton, deleteButton);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return List.of(keyField, valueField, enabledButton, deleteButton);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, boolean hovered, float deltaTicks) {
            int x = this.getX();
            int y = this.getY();

            keyField.setX(x);
            keyField.setY(y);
            keyField.render(context, mouseX, mouseY, deltaTicks);

            valueField.setX(x + 105);
            valueField.setY(y);
            valueField.render(context, mouseX, mouseY, deltaTicks);

            enabledButton.setX(x + 210);
            enabledButton.setY(y);
            enabledButton.render(context, mouseX, mouseY, deltaTicks);

            deleteButton.setX(x + 255);
            deleteButton.setY(y);
            deleteButton.render(context, mouseX, mouseY, deltaTicks);
        }

       
    }
}
