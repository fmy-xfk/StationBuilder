package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.GuiButton;
import cn.myfrank.stationbuilder.gui.GuiLabel;
import cn.myfrank.stationbuilder.gui.GuiLabelTextField;
import cn.myfrank.stationbuilder.gui.GuiScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;

public class BuildingSelectorScreen extends GuiScreen {
    private final BlockPos pos1;
    private final BlockPos pos2;
    private final GuiLabelTextField nameField = new GuiLabelTextField(Component.translatable("gui.stationbuilder.building_selector_template"), 150, 18, Component.literal("my_building"));
    private boolean includeEntities = false;
    private final GuiButton entityToggleBtn = new GuiButton(Component.translatable("gui.stationbuilder.building_selector_include_entities_off"), b -> {
        includeEntities = !includeEntities;
        if (includeEntities) {
            b.setMessage(Component.translatable("gui.stationbuilder.building_selector_include_entities_on"));
        } else {
            b.setMessage(Component.translatable("gui.stationbuilder.building_selector_include_entities_off"));
        }
    }, 150, 18);

    public BuildingSelectorScreen(BlockPos pos1, BlockPos pos2) {
        super(Component.translatable("gui.stationbuilder.building_selector_title"));
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    @Override
    protected void initControls() {
        rootPanel.setGap(4);
        rootPanel.setPadding(15);

        rootPanel.addControl(new GuiLabel(Component.translatable("gui.stationbuilder.building_selector_config").withStyle(net.minecraft.ChatFormatting.GOLD)));

        if (pos1 == null) {
            rootPanel.addControl(new GuiLabel(Component.translatable("gui.stationbuilder.building_selector_pos1", "N/A").withStyle(net.minecraft.ChatFormatting.RED)));
        } else {
            rootPanel.addControl(new GuiLabel(Component.translatable("gui.stationbuilder.building_selector_pos1", pos1.toShortString())));
        }

        if (pos2 == null) {
            rootPanel.addControl(new GuiLabel(Component.translatable("gui.stationbuilder.building_selector_pos2", "N/A").withStyle(net.minecraft.ChatFormatting.RED)));
        } else {
            rootPanel.addControl(new GuiLabel(Component.translatable("gui.stationbuilder.building_selector_pos2", pos2.toShortString())));
        }

        rootPanel.addControl(nameField);
        rootPanel.addControl(entityToggleBtn);

        rootPanel.addControl(new GuiButton(Component.translatable("gui.stationbuilder.building_selector_save"), b -> {
            String name = nameField.getText().trim();
            if (!name.isEmpty() && pos1 != null && pos2 != null) {
                PlatformServices.sendToServer(StationBuilder.PACKET_SAVE_SELECTION, StationBuilder.buf(buf -> {
                    buf.writeUtf(name);
                    buf.writeBlockPos(pos1);
                    buf.writeBlockPos(pos2);
                    buf.writeBoolean(includeEntities);
                }));
                this.onClose();
            }
        }, 180, 18));
    }
}
