package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.GuiButton;
import cn.myfrank.stationbuilder.gui.GuiLabel;
import cn.myfrank.stationbuilder.gui.GuiLabelTextField;
import cn.myfrank.stationbuilder.gui.GuiScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class BuildingSelectorScreen extends GuiScreen {
    private final BlockPos pos1;
    private final BlockPos pos2;
    private final GuiLabelTextField nameField = new GuiLabelTextField(Text.translatable("gui.stationbuilder.building_selector_template"), 150, 18, Text.literal("my_building"));
    private boolean includeEntities = false;
    private final GuiButton entityToggleBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_selector_include_entities_off"), b -> {
        includeEntities = !includeEntities;
        if (includeEntities) {
            b.setMessage(Text.translatable("gui.stationbuilder.building_selector_include_entities_on"));
        } else {
            b.setMessage(Text.translatable("gui.stationbuilder.building_selector_include_entities_off"));
        }
    }, 150, 18);

    public BuildingSelectorScreen(BlockPos pos1, BlockPos pos2) {
        super(Text.literal("Selection Tool"));
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    @Override
    protected void initControls() {
        rootPanel.setGap(4);
        rootPanel.setPadding(15);

        rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_config").formatted(net.minecraft.util.Formatting.GOLD)));

        if (pos1 == null) {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos1", "N/A").formatted(net.minecraft.util.Formatting.RED)));
        } else {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos1", pos1.toShortString())));
        }

        if (pos2 == null) {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos2", "N/A").formatted(net.minecraft.util.Formatting.RED)));
        } else {
            rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_selector_pos2", pos2.toShortString())));
        }

        rootPanel.addControl(nameField);
        rootPanel.addControl(entityToggleBtn);

        rootPanel.addControl(new GuiButton(Text.translatable("gui.stationbuilder.building_selector_save"), b -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty() && pos1 != null && pos2 != null) {
                ClientPlayNetworking.send(new StationBuilder.SaveSelectionPayload(name, pos1, pos2, includeEntities));
                this.close();
            }
        }, 180, 18));
    }
}