package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;

import java.io.File;

public class BuildingPlacerScreen extends GuiScreen {
    private final GuiLabelTextField presetField = new GuiLabelTextField(Text.translatable("gui.stationbuilder.building_placer_template"), 120, 18, Text.empty());
    private BlockRotation rotation = BlockRotation.NONE;
    private boolean placeAir = false;

    private final GuiButton rotBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(BlockRotation.NONE)), b -> {
        BlockRotation[] rots = BlockRotation.values();
        rotation = rots[(rotation.ordinal() + 1) % rots.length];
        b.setMessage(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(rotation)));
    }, 150, 18);

    private final GuiButton airBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_air_off"), b -> {
        placeAir = !placeAir;
        b.setMessage(Text.translatable("gui.stationbuilder.building_air_" + (placeAir ? "on" : "off")));
    }, 150, 18);

    public BuildingPlacerScreen(NbtCompound nbt) {
        super(Text.translatable("gui.stationbuilder.building_placer_tool"));
        if (nbt.contains("presetName")) presetField.setText(nbt.getString("presetName"));
        if (nbt.contains("rotation")) rotation = BlockRotation.valueOf(nbt.getString("rotation"));
        if (nbt.contains("placeAir")) placeAir = nbt.getBoolean("placeAir");
        rotBtn.setMessage(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(rotation)));
        airBtn.setMessage(Text.translatable("gui.stationbuilder.building_air_" + (placeAir ? "on" : "off")));
    }

    @Override
    protected void initControls() {
        rootPanel.setGap(5);
        rootPanel.setPadding(20);

        rootPanel.addControl(new GuiLabel(Text.translatable("gui.stationbuilder.building_placer_tool").formatted(net.minecraft.util.Formatting.GOLD)));

        int widthVal = this.width - (rootPanel.getMarginLeft() + rootPanel.getPaddingLeft() + rootPanel.getMarginRight() + rootPanel.getPaddingRight());
        GuiPanel templatePanel = new GuiPanel(widthVal, 18);
        templatePanel.setDirection(GuiPanel.PanelDirection.HORIZONTAL);
        templatePanel.setGap(5);
        templatePanel.setMajorAlign(GuiPanel.MajorAlignMode.START);
        templatePanel.setCrossAlign(GuiPanel.CrossAlignMode.CENTER);

        GuiButton browseButton = new GuiButton(
                Text.translatable("gui.stationbuilder.browse"),
                b -> {
                    if (client != null) {
                        client.setScreen(new BuildingSelectionScreen(this, presetField.getTextField()));
                    }
                },
                50, 18
        );

        GuiButton importButton = new GuiButton(
                Text.translatable("gui.stationbuilder.import_file"),
                b -> openFileChooser(),
                70, 18
        );

        templatePanel.addControl(presetField);
        templatePanel.addControl(browseButton);
        templatePanel.addControl(importButton);

        rootPanel.addControl(templatePanel);
        rootPanel.addControl(rotBtn);
        rootPanel.addControl(airBtn);

        rootPanel.addControl(new GuiButton(Text.translatable("gui.stationbuilder.building_placer_save"), b -> {
            this.close();
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
                    Text.translatable("control.stationbuilder.select_struct_file").getString(),
                    null,
                    filters,
                    Text.translatable("control.stationbuilder.struct_file_filter").getString(),
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

                            if (client != null && client.player != null) {
                                client.player.sendMessage(Text.translatable("gui.stationbuilder.import_success", name), false);
                            }
                        } else {
                            if (client != null && client.player != null) {
                                client.player.sendMessage(Text.translatable("gui.stationbuilder.import_fail"), true);
                            }
                        }
                    }, client::execute);
        }
    }

    private StructureTemplate loadStructureFromFile(File file) {
        String name = file.getName().toLowerCase();
        try {
            if (name.endsWith(".nbt")) {
                NbtCompound nbt = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                StructureTemplate template = new StructureTemplate();
                template.readNbt(Registries.BLOCK.getReadOnlyWrapper(), nbt);
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
    public void close() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("presetName", presetField.getText().trim());
        nbt.putString("rotation", rotation.name());
        nbt.putBoolean("placeAir", placeAir);

        // 1.20.4 网络包写法：构建 PacketByteBuf 并按顺序写入 NBT 数据
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeNbt(nbt);

        ClientPlayNetworking.send(StationBuilder.SAVE_PLACER_PACKET, buf);
        super.close();
    }
}