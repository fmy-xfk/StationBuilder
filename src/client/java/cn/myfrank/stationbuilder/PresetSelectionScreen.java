package cn.myfrank.stationbuilder;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.io.File;
import java.util.List;

public class PresetSelectionScreen extends Screen {
    private final StationEditorScreen parent;
    private List<String> presets;

    public PresetSelectionScreen(StationEditorScreen parent) {
        super(Text.translatable("gui.stationbuilder.presets"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (client == null) return;
        presets = PresetManager.getPresetList();
        int centerX = width / 2;

        // 渲染预设列表按钮
        for (int i = 0; i < presets.size(); i++) {
            String name = presets.get(i);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> {
                parent.applyPreset(PresetManager.loadPreset(name));
                client.setScreen(parent);
            }).dimensions(centerX - 100, 40 + (i * 25), 200, 20).build());
        }

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).dimensions(centerX - 50, height - 30, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.open_folder"), b -> {
            File dir = PresetManager.getPresetPath().toFile();
            // 如果文件夹不存在则创建，否则打开会失败
            if (!dir.exists()) dir.mkdirs();
            // 使用原版 Util 方法安全地打开系统文件夹
            Util.getOperatingSystem().open(dir);
        }).dimensions(width - 110, height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}