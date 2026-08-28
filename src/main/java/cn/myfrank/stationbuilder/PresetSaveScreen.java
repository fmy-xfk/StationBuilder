package cn.myfrank.stationbuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public class PresetSaveScreen extends Screen {
    private final StationEditorScreen parent;
    private EditBox nameField;

    public PresetSaveScreen(StationEditorScreen parent) {
        super(Component.translatable("gui.stationbuilder.save_preset"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        int centerX = width / 2;
        int centerY = height / 2;

        // 1. 将 textRenderer 替换为 font
        this.nameField = new EditBox(font, centerX - 80, centerY - 10, 160, 20, Component.empty());
        // 2. 将 addSelectableChild 替换为 addWidget
        this.addWidget(nameField);

        // 3. 将 dimensions 替换为 bounds
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stationbuilder.confirm"), b -> {
            // 4. 将 getText 替换为 getValue
            String name = nameField.getValue().trim();
            if (!name.isEmpty()) {
                PresetManager.savePreset(name, parent.getStationLength(), parent.getElements());
                client.setScreen(parent);
            }
        }).bounds(centerX - 82, centerY + 20, 80, 20).build()); // 使用 bounds 替代 dimensions

        this.addRenderableWidget(Button.builder(Component.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).bounds(centerX + 2, centerY + 20, 80, 20).build()); // 使用 bounds 替代 dimensions
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        // 5. 将 textRenderer 替换为 font
        context.drawCenteredString(font, getTitle(), width / 2, height / 2 - 30, 0xFFFFFF);
        this.nameField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }
}