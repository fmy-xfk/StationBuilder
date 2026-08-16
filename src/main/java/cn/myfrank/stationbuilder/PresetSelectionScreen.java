package cn.myfrank.stationbuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.Util;

import java.io.File;
import java.util.List;

public class PresetSelectionScreen extends Screen {
    private final StationEditorScreen parent;

    public PresetSelectionScreen(StationEditorScreen parent) {
        super(Component.translatable("gui.stationbuilder.presets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        List<String> presets = PresetManager.getPresetList();
        int centerX = width / 2;

        // 渲染预设列表按钮
        for (int i = 0; i < presets.size(); i++) {
            String name = presets.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(name), b -> {
                parent.applyPreset(PresetManager.loadPreset(name));
                client.setScreen(parent);
            }).bounds(centerX - 100, 40 + (i * 25), 200, 20).build());
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).bounds(centerX - 50, height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.stationbuilder.open_folder"), b -> {
            File dir = PresetManager.getPresetPath().toFile();
            // 如果文件夹不存在则创建，否则打开会失败
            if (!dir.exists()) dir.mkdirs();
            Util.getPlatform().openFile(dir);
        }).bounds(width - 110, height - 30, 100, 20).build()); // 2. 顺手将这里的 .dimensions 修正为 .bounds
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, getTitle(), width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}