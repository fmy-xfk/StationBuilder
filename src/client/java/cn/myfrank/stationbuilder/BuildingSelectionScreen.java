package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.BuildingElement;
import cn.myfrank.stationbuilder.gui.GuiTextField;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

@Environment(EnvType.CLIENT)
public class BuildingSelectionScreen extends Screen {
    private final Screen parent;
    private final GuiTextField buildingPresetField;
    private List<String> buildingNames;

    public BuildingSelectionScreen(Screen parent, GuiTextField buildingPresetField) {
        super(Text.translatable("gui.stationbuilder.select_building"));
        this.parent = parent;
        this.buildingPresetField = buildingPresetField;
        this.buildingNames = BuildingTemplateManager.getTemplateNames();
    }

    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;

        // 显示模板列表按钮
        for (int i = 0; i < buildingNames.size(); i++) {
            String name = buildingNames.get(i);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(name), b -> {
                buildingPresetField.setText(name);
                // 同步更新元素
                if (parent instanceof StationEditorScreen editor) {
                    int index = editor.getSelectedIndex();
                    if (index >= 0 && editor.getElements().get(index) instanceof BuildingElement be) {
                        be.presetName = name;
                    }
                }
                client.setScreen(parent);
            }).dimensions(centerX - 100, 40 + i * 25, 200, 20).build());
        }

        // 导入文件按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.import_file"), b -> {
            if (parent instanceof StationEditorScreen editor) {
                editor.openFileChooser();
            } else if (parent instanceof BuildingPlacerScreen placer) {
                placer.openFileChooser();
            }
            client.setScreen(parent);
        }).dimensions(centerX - 50, height - 60, 100, 20).build());

        // 取消按钮
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.stationbuilder.cancel"), b -> {
            client.setScreen(parent);
        }).dimensions(centerX - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(textRenderer, getTitle(), width / 2, 15, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }
}