package cn.myfrank.stationbuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class PresetSaveScreen extends Screen {
    private final StationEditorScreen parent;
    private TextFieldWidget nameField;

    public PresetSaveScreen(StationEditorScreen parent) {
        super(Text.translatable("gui.stationbuilder.save_preset"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (client == null) return;
        int centerX = width / 2;
        int centerY = height / 2;

        this.nameField = new TextFieldWidget(textRenderer, centerX - 80, centerY - 10, 160, 20, Text.empty());
        this.addSelectableChild(nameField);

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.confirm"), b -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty()) {
                PresetManager.savePreset(name, parent.getStationLength(), parent.getElements());
                client.setScreen(parent);
            }
        }).dimensions(centerX - 82, centerY + 20, 80, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).dimensions(centerX + 2, centerY + 20, 80, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, height / 2 - 30, 0xFFFFFF);
        this.nameField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}