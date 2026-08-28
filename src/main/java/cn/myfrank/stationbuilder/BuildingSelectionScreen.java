package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.BuildingElement;
import cn.myfrank.stationbuilder.gui.GuiTextField;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

public class BuildingSelectionScreen extends Screen {
    private final Screen parent;
    private final GuiTextField buildingPresetField;
    private List<String> buildingNames;

    public BuildingSelectionScreen(Screen parent, GuiTextField buildingPresetField) {
        super(Component.translatable("gui.stationbuilder.select_building"));
        this.parent = parent;
        this.buildingPresetField = buildingPresetField;
        this.buildingNames = BuildingTemplateManager.getTemplateNames();
    }

    @Override
    protected void init() {
        super.init();
        Minecraft client = Minecraft.getInstance();
        int centerX = width / 2;

        // 显示模板列表按钮
        for (int i = 0; i < buildingNames.size(); i++) {
            String name = buildingNames.get(i);
            this.addRenderableWidget(Button.builder(Component.literal(name), b -> {
                buildingPresetField.setText(name);
                // 同步更新元素（仅当父屏幕为站点编辑器时）
                if (parent instanceof StationEditorScreen stationParent) {
                    int index = stationParent.getSelectedIndex();
                    if (index >= 0 && stationParent.getElements().get(index) instanceof BuildingElement be) {
                        be.presetName = name;
                    }
                }
                client.setScreen(parent);
            }).bounds(centerX - 100, 40 + i * 25, 200, 20).build());
        }

        // 导入文件按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stationbuilder.import_file"), b -> {
            if (parent instanceof StationEditorScreen stationParent) {
                stationParent.openFileChooser();
            } else if (parent instanceof BuildingPlacerScreen placerScreen) {
                placerScreen.openFileChooser();
            }
            // 关闭当前屏幕，让父屏幕处理导入
            client.setScreen(parent);
        }).bounds(centerX - 50, height - 60, 100, 20).build());

        // 取消按钮
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).bounds(centerX - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        context.drawCenteredString(font, getTitle(), width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}
