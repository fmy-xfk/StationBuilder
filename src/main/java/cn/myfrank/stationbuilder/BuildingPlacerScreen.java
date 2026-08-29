package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Rotation;

import java.io.File;

public class BuildingPlacerScreen extends GuiScreen {
    private final GuiLabelTextField presetField = new GuiLabelTextField(Component.translatable("gui.stationbuilder.building_placer_template"), 120, 18, Component.empty());
    private Rotation rotation = Rotation.NONE;
    private boolean placeAir = false;

    private final GuiButton rotBtn = new GuiButton(Component.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(Rotation.NONE)), b -> {
        Rotation[] rots = Rotation.values();
        rotation = rots[(rotation.ordinal() + 1) % rots.length];
        b.setMessage(Component.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(rotation)));
    }, 150, 18);

    private final GuiButton airBtn = new GuiButton(Component.translatable("gui.stationbuilder.building_air_off"), b -> {
        placeAir = !placeAir;
        b.setMessage(Component.translatable("gui.stationbuilder.building_air_" + (placeAir ? "on" : "off")));
    }, 150, 18);

    public BuildingPlacerScreen(CompoundTag nbt) {
        super(Component.translatable("gui.stationbuilder.building_placer_tool"));
        if (nbt != null) {
            if (nbt.contains("presetName")) presetField.setText(nbt.getString("presetName"));
            if (nbt.contains("rotation")) rotation = Rotation.valueOf(nbt.getString("rotation"));
            if (nbt.contains("placeAir")) placeAir = nbt.getBoolean("placeAir");
        }
        rotBtn.setMessage(Component.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(rotation)));
        airBtn.setMessage(Component.translatable("gui.stationbuilder.building_air_" + (placeAir ? "on" : "off")));
    }

    @Override
    protected void initControls() {
        rootPanel.setGap(5);
        rootPanel.setPadding(20);

        rootPanel.addControl(new GuiLabel(Component.translatable("gui.stationbuilder.building_placer_tool").withStyle(net.minecraft.ChatFormatting.GOLD)));

        int widthVal = this.width - (rootPanel.getMarginLeft() + rootPanel.getPaddingLeft() + rootPanel.getMarginRight() + rootPanel.getPaddingRight());
        GuiPanel templatePanel = new GuiPanel(widthVal, 18);
        templatePanel.setDirection(GuiPanel.PanelDirection.HORIZONTAL);
        templatePanel.setGap(5);
        templatePanel.setMajorAlign(GuiPanel.MajorAlignMode.START);
        templatePanel.setCrossAlign(GuiPanel.CrossAlignMode.CENTER);

        GuiButton browseButton = new GuiButton(
                Component.translatable("gui.stationbuilder.browse"),
                b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new BuildingSelectionScreen(this, presetField.getTextField()));
                    }
                },
                50, 18
        );

        GuiButton importButton = new GuiButton(
                Component.translatable("gui.stationbuilder.import_file"),
                b -> openFileChooser(),
                70, 18
        );

        templatePanel.addControl(presetField);
        templatePanel.addControl(browseButton);
        templatePanel.addControl(importButton);

        rootPanel.addControl(templatePanel);
        rootPanel.addControl(rotBtn);
        rootPanel.addControl(airBtn);

        rootPanel.addControl(new GuiButton(Component.translatable("gui.stationbuilder.building_placer_save"), b -> {
            this.onClose();
        }, 100, 18));
    }

    public void openFileChooser() {
        String path;
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.nbt"));
            filters.put(stack.UTF8("*.schem"));
            filters.put(stack.UTF8("*.schematic"));
            filters.put(stack.UTF8("*.litematic"));
            filters.flip();

            path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                    Component.translatable("control.stationbuilder.select_struct_file").getString(),
                    null,
                    filters,
                    Component.translatable("control.stationbuilder.struct_file_filter").getString(),
                    false
            );
        }

        if (path != null) {
            java.io.File file = new java.io.File(path);
            java.util.concurrent.CompletableFuture.supplyAsync(() -> loadStructureFromFile(file))
                    .thenAcceptAsync(template -> {
                        if (template != null) {
                            String name = file.getName().replaceFirst("\\.[^.]+$", "");
                            BuildingTemplateManager.addTemplate(name, template);
                            presetField.setText(name);

                            if (minecraft != null && minecraft.player != null) {
                                minecraft.player.displayClientMessage(Component.translatable("gui.stationbuilder.import_success", name), false);
                            }
                        } else {
                            if (minecraft != null && minecraft.player != null) {
                                minecraft.player.displayClientMessage(Component.translatable("gui.stationbuilder.import_fail"), true);
                            }
                        }
                    }, minecraft::execute);
        }
    }

    private StructureTemplate loadStructureFromFile(File file) {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".nbt")) {
                CompoundTag nbt = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
                StructureTemplate template = new StructureTemplate();
                template.load(BuiltInRegistries.BLOCK, nbt);
                return template;
            } else if (name.endsWith(".schem") || name.endsWith(".schematic")) {
                return SchematicLoaderUtil.loadSchematic(file.toPath());
            } else if (name.endsWith(".litematic")) {
                return SchematicLoaderUtil.loadSchematic(file.toPath());
            } else {
                return null;
            }
        } catch (Exception e) {
            StationBuilder.LOGGER.error("Failed to load structure file: {}", file, e);
            return null;
        }
    }

    @Override
    public void onClose() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("presetName", presetField.getText().trim());
        nbt.putString("rotation", rotation.name());
        nbt.putBoolean("placeAir", placeAir);

        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new StationBuilder.SavePlacerPayload(nbt));
        super.onClose();
    }
}
