package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.BuildingElement;
import cn.myfrank.stationbuilder.elements.PlatformElement;
import cn.myfrank.stationbuilder.elements.StationElement;
import cn.myfrank.stationbuilder.elements.TrackElement;
import cn.myfrank.stationbuilder.gui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.registry.Registries;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.text.Text;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StationEditorScreen extends GuiScreen {
    private static final int BTN_WIDTH_XXL = 120;
    private static final int BTN_WIDTH_XL = 75;
    private static final int BTN_WIDTH_L = 65;
    private static final int BTN_WIDTH = 50;
    private static final int BTN_WIDTH_S = 20;
    private static final int BTN_HEIGHT = 18;
    private static final int INPUT_WIDTH_L = 70;
    private static final int INPUT_WIDTH = 50;
    private static final int INPUT_WIDTH_S = 30;
    private static final int INPUT_HEIGHT = 18;

    private static Text getText(String key) {
        return Text.translatable("gui.stationbuilder." + key);
    }

    private String initialLength = String.valueOf(StationBuilder.DEFAULT_STATION_LENGTH);
    private final BlockPos pos;
    private final Direction facing;
    private final List<StationElement> elements = new ArrayList<>();

    private GuiPanel trackProperties;
    private GuiPanel platformProperties;
    private GuiPanel buildingProperties;
    private GuiPanel emptyProperties;
    private GuiRectCanvas canvas;
    private final GuiLabelTextField lengthField = new GuiLabelTextField(getText("length"), INPUT_WIDTH_S,
            INPUT_HEIGHT, Text.empty());
    private final GuiLabelSlot trackBallastField = new GuiLabelSlot(
            getText("ballast"), INPUT_WIDTH, INPUT_HEIGHT,
            Identifier.of("minecraft", "andesite"), false
    );
    private final GuiLabel autoRotate = new GuiLabel(getText("auto_rotate"));
    private final GuiButton useMtrTrackButton = new GuiButton(getText("mtr_on"), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof TrackElement t) {
            t.isMtrTrack = !t.isMtrTrack;
            if (t.isMtrTrack) {
                b.setMessage(getText("mtr_on"));
            }else{
                b.setMessage(getText("mtr_off"));
            }
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT);
    private final GuiLabelTextField platformLengthField = new GuiLabelTextField(getText("width"), INPUT_WIDTH_S,
            INPUT_HEIGHT, Text.literal("9"));
    private final GuiLabelSlot platformSafetyField = new GuiLabelSlot(
            getText("safety_line"), INPUT_WIDTH_S, INPUT_HEIGHT,
            Identifier.of("minecraft", "yellow_concrete"), false
    );
    private final GuiLabelSlotInput[] weightFields = new GuiLabelSlotInput[5];
    private final GuiLabelTextField buildingPresetField = new GuiLabelTextField(getText("preset"), INPUT_WIDTH_L,
            INPUT_HEIGHT, Text.empty());
    private final GhostInventory ghostInventory = new GhostInventory();
    private final GuiButton delButton = new GuiButton(getText("del"), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && index < this.elements.size()) {
            elements.remove(index);
            canvas.removeSelectedRect();
            refreshPropertyArea();
        }
    }, BTN_WIDTH, BTN_HEIGHT);
    private final GuiButton moveLeftButton = new GuiButton(Text.literal("←"), b -> {
        int index = canvas.getSelectedIndex();
        if (index > 0 && index < this.elements.size()) {
            Collections.swap(elements, index, index - 1);
            canvas.swap(index, index - 1);
            refreshPropertyArea();
        }
    }, BTN_WIDTH_S, BTN_HEIGHT);
    private final GuiButton moveRightButton = new GuiButton(Text.literal("→"), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && index < this.elements.size() - 1) {
            Collections.swap(elements, index, index + 1);
            canvas.swap(index, index + 1);
            refreshPropertyArea();
        }
    }, BTN_WIDTH_S, BTN_HEIGHT);
    private final GuiButton hasCanopyBtn = new GuiButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasCanopy = !pe.hasCanopy;
            b.setMessage(pe.hasCanopy ? getText("canopy_on") : getText("canopy_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT);
    private final GuiLabelTextField canopyHeightField = new GuiLabelTextField(getText("canopy_height"),
            INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("4"));
    private final GuiLabelSlot canopySlabSlot = new GuiLabelSlot(getText("canopy_slab"), INPUT_WIDTH, INPUT_HEIGHT,
            Identifier.of("minecraft", "stone_slab"), false);
    private final GuiLabelButton canopyStyleBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            PlatformElement.CanopyStyle[] styles = PlatformElement.CanopyStyle.values();
            pe.canopyStyle = styles[(pe.canopyStyle.ordinal() + 1) % styles.length];
            b.setMessage(getText(pe.canopyStyle.name()));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("canopy_style"));
    private final GuiLabelButton pillarStyleBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            // 循环切换枚举
            PlatformElement.PillarStyle[] styles = PlatformElement.PillarStyle.values();
            pe.pillarStyle = styles[(pe.pillarStyle.ordinal() + 1) % styles.length];
            b.setMessage(getText(pe.pillarStyle.name()));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("pillar_style"));
    private final GuiLabelSlot pillarBlockSlot = new GuiLabelSlot(getText("pillar_block"), INPUT_WIDTH,
            INPUT_HEIGHT, Identifier.of("minecraft", "stone_brick_wall"), false);
    private final GuiLabelTextField pillarSpacingField = new GuiLabelTextField(getText("pillar_spacing"),
            INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("6"));
    private final GuiLabelTextField pillarOffsetField = new GuiLabelTextField(getText("pillar_offset"),
            INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("2"));
    private final GuiButton hasLightingBtn = new GuiButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasLighting = !pe.hasLighting;
            b.setMessage(pe.hasLighting ? getText("lighting_on") : getText("lighting_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT);
    private final GuiLabelSlot lightBlockSlot = new GuiLabelSlot(getText("lighting_block"), INPUT_WIDTH,
            INPUT_HEIGHT, Identifier.of("minecraft", "sea_lantern"), false);
    private final GuiLabelButton shieldDoorBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasShieldDoors = !pe.hasShieldDoors;
            b.setMessage(pe.hasShieldDoors ? getText("psd_on") : getText("psd_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("psd"));;
    private final GuiLabelTextField doorOffsetField = new GuiLabelTextField(getText("door_offset"),
            INPUT_WIDTH, INPUT_HEIGHT, Text.literal("2"));
    private final GuiLabelTextField doorSpacingField = new GuiLabelTextField(getText("door_spacing"),
            INPUT_WIDTH, INPUT_HEIGHT, Text.literal("3"));;
    private final GuiLabelSlot psdEndSlot = new GuiLabelSlot(getText("psd_end"), INPUT_WIDTH, INPUT_HEIGHT,
            Identifier.of("mtr", "apg_glass_end"), false);
    private final GuiLabelSlot psdGlassSlot = new GuiLabelSlot(getText("psd_glass"), INPUT_WIDTH, INPUT_HEIGHT,
            Identifier.of("mtr", "apg_glass"), false);
    private final GuiLabelSlot psdDoorSlot = new GuiLabelSlot(getText("psd_door"), INPUT_WIDTH, INPUT_HEIGHT,
            Identifier.of("mtr", "apg_door"), false);
    private final GuiLabelButton pidsBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasPids = !pe.hasPids;
            b.setMessage(pe.hasPids ? getText("pids_on") : getText("pids_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("pids"));

    private final GuiLabelSlot pidBlockSlot = new GuiLabelSlot(getText("pids_block"), INPUT_WIDTH, INPUT_HEIGHT,
            Identifier.of("mtr", "pids_1"), false);
    private final GuiLabelSlot pidPoleSlot = new GuiLabelSlot(getText("pids_pole"), INPUT_WIDTH, INPUT_HEIGHT,
            Identifier.of("mtr", "pids_pole"), false);
    private final GuiButton buildingRotBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(BlockRotation.NONE)), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof BuildingElement be) {
            BlockRotation[] rots = BlockRotation.values();
            be.rotation = rots[(be.rotation.ordinal() + 1) % rots.length];
            b.setMessage(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(be.rotation)));
        }
    }, BTN_WIDTH_XXL, BTN_HEIGHT);

    private final GuiButton buildingAirBtn = new GuiButton(Text.translatable("gui.stationbuilder.building_air_off"), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof BuildingElement be) {
            be.placeAir = !be.placeAir;
            b.setMessage(Text.translatable("gui.stationbuilder.building_air_" + (be.placeAir ? "on" : "off")));
        }
    }, BTN_WIDTH_XXL, BTN_HEIGHT);

    public int getSelectedIndex() {
        return canvas != null ? canvas.getSelectedIndex() : -1;
    }

    public StationEditorScreen(BlockPos pos, Direction facing, NbtCompound nbt) {
        super(getText("station_builder"));
        this.pos = pos;
        this.facing = facing;

        if (nbt != null) {
            if (nbt.contains("length")) this.initialLength = String.valueOf(nbt.getInt("length"));
            NbtList list = nbt.getList("elements", 10);
            for (int i = 0; i < list.size(); i++) {
                this.elements.add(StationElement.fromNbt(list.getCompound(i)));
            }
        }
    }

    protected void refreshPropertyArea() {
        int index = canvas.getSelectedIndex();
        trackProperties.setVisible(false);
        platformProperties.setVisible(false);
        buildingProperties.setVisible(false);
        emptyProperties.setVisible(false);

        boolean hasSelection = index != -1;
        delButton.setActive(hasSelection);
        moveLeftButton.setActive(hasSelection && index > 0);
        moveRightButton.setActive(hasSelection && index < elements.size() - 1);

        if (0 <= index && index < elements.size()) {
            StationElement e = this.elements.get(index);
            if (e instanceof TrackElement t) {
                trackProperties.setVisible(true);
                trackBallastField.setBlockId(t.ballastBlock);
                useMtrTrackButton.setMessage(t.isMtrTrack?getText("mtr_on"):getText("mtr_off"));
            } else if(e instanceof PlatformElement p) {
                platformProperties.setVisible(true);
                platformLengthField.setText(String.valueOf(p.width));
                platformSafetyField.setBlockId(p.safetyBlock);
                for(int i = 0; i < 5; i++) {
                    weightFields[i].setBlockId(p.mixSlots[i].blockId);
                    weightFields[i].setText(String.valueOf(p.mixSlots[i].weight));
                }
                hasCanopyBtn.setMessage(p.hasCanopy ? getText("canopy_on") : getText("canopy_off"));
                canopyHeightField.setText(String.valueOf(p.canopyHeight));
                canopySlabSlot.setBlockId(p.canopySlabId);
                canopyStyleBtn.setMessage(getText(p.canopyStyle.name()));

                hasLightingBtn.setMessage(p.hasLighting ? getText("lighting_on") : getText("lighting_off"));
                pillarStyleBtn.setMessage(getText(p.pillarStyle.name()));
                pillarBlockSlot.setBlockId(p.pillarBlockId);
                pillarSpacingField.setText(String.valueOf(p.pillarSpacing));
                pillarOffsetField.setText(String.valueOf(p.firstPillarOffset));

                shieldDoorBtn.setMessage(p.hasShieldDoors ? getText("psd_on") : getText("psd_off"));
                doorOffsetField.setText(String.valueOf(p.doorStartOffset));
                doorSpacingField.setText(String.valueOf(p.doorSpacing));
                psdEndSlot.setBlockId(p.psdEndId);
                psdGlassSlot.setBlockId(p.psdGlassId);
                psdDoorSlot.setBlockId(p.psdDoorId);

                pidsBtn.setMessage(p.hasPids ? getText("pids_on") : getText("pids_off"));
                pidBlockSlot.setBlockId(p.pidBlockId);
                pidPoleSlot.setBlockId(p.pidPoleId);
            } else if(e instanceof BuildingElement b) {
                buildingProperties.setVisible(true);
                buildingPresetField.setText(b.presetName);
                buildingRotBtn.setMessage(Text.translatable("gui.stationbuilder.building_rotation", StationBuilder.getRotName(b.rotation)));
                buildingAirBtn.setMessage(Text.translatable("gui.stationbuilder.building_air_" + (b.placeAir ? "on" : "off")));
            } else {
                emptyProperties.setVisible(true);
            }
        }
    }
    @Override
    protected void initControls() {
        int savedIndex = this.canvas != null ? this.canvas.getSelectedIndex() : -1;

        rootPanel.setGap(2);
        int width = this.width - (
                rootPanel.getMarginLeft() + rootPanel.getPaddingLeft() +
                rootPanel.getMarginRight() + rootPanel.getPaddingRight()
        );
        int height = this.height - (
                rootPanel.getMarginTop() + rootPanel.getMarginBottom() +
                rootPanel.getPaddingTop() + rootPanel.getPaddingBottom()
        );

        int topPanelHeight = 24;
        GuiPanel topPanel = new GuiPanel(width, topPanelHeight);
        lengthField.setText(initialLength);
        topPanel.addControl(lengthField);
        topPanel.addControl(new GuiButton(getText("presets"), b -> {
            MinecraftClient.getInstance().setScreen(new PresetSelectionScreen(this));
        }, BTN_WIDTH_XL, BTN_HEIGHT));
        topPanel.setMajorAlign(GuiPanel.MajorAlignMode.SPACE_BETWEEN);
        addControl(topPanel);

        this.canvas = new GuiRectCanvas(width, 34);
        SyncCanvasWithElements();
        if (savedIndex >= 0) {
            this.canvas.select(savedIndex);
        }
        addControl(canvas);

        int elemOpPanelHeight = 24;
        GuiPanel elemOpPanel = new GuiPanel(width, elemOpPanelHeight)
        .addControl(new GuiButton(getText("add_track"), b -> {
            var e = new TrackElement(); elements.add(e);
            canvas.addRect(Math.min(20, e.getWidth()) * 3, "T");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT))
        .addControl(new GuiButton(getText("add_platform"), b -> {
            var e = new PlatformElement(); elements.add(e);
            canvas.addRect(Math.min(20, e.getWidth()) * 3, "P");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT))
        .addControl(new GuiButton(getText("add_building"), b -> {
            var e = new BuildingElement("matchbox"); elements.add(e);
            canvas.addRect(Math.min(20, e.getWidth()) * 3, "B");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT));

        delButton.setActive(false);
        moveLeftButton.setActive(false);
        moveRightButton.setActive(false);
        elemOpPanel.addControl(delButton).addControl(moveLeftButton).addControl(moveRightButton);

        addControl(elemOpPanel);

        int middlePanelHeight = height - elemOpPanelHeight - topPanelHeight * 2 - canvas.getHeight() - 4 * rootPanel.getGap();
        GuiPanel middlePanel = new GuiPanel(width, middlePanelHeight);

        int propertyPanelWidth = width - ghostInventory.getWidth() - middlePanel.getGap();
        GuiPanel middleLeftBox = new GuiPanel(propertyPanelWidth, middlePanelHeight);
        middleLeftBox.setGap(0);

        trackProperties = getTrackProperties(propertyPanelWidth, middlePanelHeight);
        platformProperties = getPlatformProperties(propertyPanelWidth, middlePanelHeight);
        buildingProperties = getBuildingProperties(propertyPanelWidth, middlePanelHeight);
        emptyProperties = getEmptyProperties(propertyPanelWidth, middlePanelHeight);
        emptyProperties.setVisible(true);

        middleLeftBox.addControl(trackProperties)
                .addControl(platformProperties)
                .addControl(buildingProperties)
                .addControl(emptyProperties);

        GuiPanel middleRightBox = new GuiPanel(ghostInventory.getWidth(), middlePanelHeight);
        middleRightBox.setMajorAlign(GuiPanel.MajorAlignMode.END);
        middleRightBox.addControl(ghostInventory);

        middlePanel.addControl(middleLeftBox).addControl(middleRightBox);
        addControl(middlePanel);

        GuiPanel bottomPanel = new GuiPanel(width, topPanelHeight)
        .addControl(new GuiButton(getText("save_preset"), b -> {
            MinecraftClient.getInstance().setScreen(new PresetSaveScreen(this));
        }, BTN_WIDTH_L, BTN_HEIGHT))
        .addControl(new GuiButton(getText("construct"), b -> {
            sendBuildPacket(); this.close();
        }, BTN_WIDTH_L, BTN_HEIGHT));

        bottomPanel.setMajorAlign(GuiPanel.MajorAlignMode.END);
        addControl(bottomPanel);

        refreshPropertyArea();
    }

    private void initProperties(GuiPanel panel) {
        panel.setPadding(10);
        panel.setGap(2);
        panel.setDirection(GuiPanel.PanelDirection.VERTICAL);
        panel.setCrossAlign(GuiPanel.CrossAlignMode.START);
        panel.setBorderVisible(true);
        panel.setVisible(false);
    }

    private @NotNull GuiPanel getTrackProperties(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel trackProperties = new GuiPanel(propertyPanelWidth, middlePanelHeight);
        initProperties(trackProperties);

        trackBallastField.slotChanged.clear();
        trackBallastField.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof TrackElement t) {
                t.ballastBlock = e.newId;
            }
        });

        trackProperties.addControl(trackBallastField).addControl(useMtrTrackButton);
        return trackProperties;
    }

    private @NotNull GuiPanel getPlatformProperties(int w, int h) {
        GuiTab p = new GuiTab(w, h);
        int w0 = p.getContentWidth(), h0 = p.getContentHeight();
        p.addTab(getText("platform"), getPlatformBase(w0, h0));
        p.addTab(getText("canopy"), getPlatformCanopy(w0, h0));
        p.addTab(getText("pillar"), getPlatformPillar(w0, h0));
        if (StationBuilder.isMtrLoaded()) {
            p.addTab(Text.literal("MTR"), getPlatformMtr(w0, h0));
        }
        p.setVisible(false);
        return p;
    }

    private @NotNull GuiPanel getPlatformMtr(int w, int h) {
        GuiPanel p = new GuiScrollablePanel(w, h);
        initProperties(p);

        psdEndSlot.slotChanged.clear();
        psdEndSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdEndId = e.newId;
            }
        });

        psdGlassSlot.slotChanged.clear();
        psdGlassSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdGlassId = e.newId;
            }
        });

        psdDoorSlot.slotChanged.clear();
        psdDoorSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdDoorId = e.newId;
            }
        });

        doorOffsetField.getTextField().textChanged.clear();
        doorOffsetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.doorStartOffset = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        doorSpacingField.getTextField().textChanged.clear();
        doorSpacingField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.doorSpacing = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        pidBlockSlot.slotChanged.clear();
        pidBlockSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.pidBlockId = e.newId;
            }
        });

        pidPoleSlot.slotChanged.clear();
        pidPoleSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.pidPoleId = e.newId;
            }
        });

        // PIDs 开关
        p.addControl(shieldDoorBtn).addControl(doorOffsetField).addControl(doorSpacingField);
        p.addControl(psdEndSlot).addControl(psdGlassSlot).addControl(psdDoorSlot);
        p.addControl(pidsBtn).addControl(pidBlockSlot).addControl(pidPoleSlot);
        return p;
    }

    private @NotNull GuiPanel getPlatformBase(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel base = new GuiScrollablePanel(propertyPanelWidth, middlePanelHeight);
        initProperties(base);
        var topPanel = new GuiPanel(propertyPanelWidth, INPUT_HEIGHT);

        platformLengthField.getTextField().textChanged.clear();
        platformLengthField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement p)
                try { p.width = Integer.parseInt(e.newText); } catch (Exception ex) {}
        });

        platformSafetyField.slotChanged.clear();
        platformSafetyField.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement p) {
                p.safetyBlock = e.newId;
                autoRotate.setVisible(net.minecraft.registry.Registries.BLOCK.get(p.safetyBlock).
                        getDefaultState().contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING));
            }
        });

        autoRotate.setForeColor(0xFF00FF00);
        topPanel.addControl(platformLengthField).addControl(platformSafetyField).addControl(autoRotate);
        base.addControl(topPanel);
        for (int i = 0; i < 5; i++) {
            final int fieldIndex = i;
            weightFields[i] = new GuiLabelSlotInput(
                    net.minecraft.text.Text.translatable("gui.stationbuilder.platform_blocks", i + 1),
                    INPUT_WIDTH_S, INPUT_HEIGHT, Identifier.of("minecraft", "stone"),
                    false, net.minecraft.text.Text.empty()
            );
            weightFields[i].getTextField().textChanged.addHandler((sender, e) -> {
                int selectedCanvasIndex = canvas.getSelectedIndex();
                if (selectedCanvasIndex >= 0 && elements.get(selectedCanvasIndex) instanceof PlatformElement p) {
                    try {
                        if (fieldIndex < p.mixSlots.length) {
                            p.mixSlots[fieldIndex].weight = Double.parseDouble(e.newText);
                        }
                    } catch (Exception ignored) {}
                }
            });
            // 处理从指针放进去的新方块同步
            weightFields[i].slotChanged.addHandler((sender, e) -> {
                int selectedCanvasIndex = canvas.getSelectedIndex();
                if (selectedCanvasIndex >= 0 && elements.get(selectedCanvasIndex) instanceof PlatformElement p) {
                    if (fieldIndex < p.mixSlots.length) {
                        p.mixSlots[fieldIndex].blockId = e.newId;
                    }
                }
            });
            base.addControl(weightFields[i]);
        }
        return base;
    }


    private @NotNull GuiPanel getPlatformPillar(int w0, int h0) {
        GuiPanel p = new GuiScrollablePanel(w0, h0);
        initProperties(p);

        pillarBlockSlot.slotChanged.clear();
        pillarBlockSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.pillarBlockId = e.newId;
            }
        });

        pillarSpacingField.getTextField().textChanged.clear();
        pillarSpacingField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.pillarSpacing = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        pillarOffsetField.getTextField().textChanged.clear();
        pillarOffsetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.firstPillarOffset = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        lightBlockSlot.slotChanged.clear();
        lightBlockSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.lightBlockId = e.newId;
            }
        });

        p.addControl(pillarStyleBtn);
        p.addControl(pillarBlockSlot);
        p.addControl(pillarSpacingField);
        p.addControl(pillarOffsetField);
        p.addControl(hasLightingBtn);
        p.addControl(lightBlockSlot);
        return p;
    }

    private @NotNull GuiPanel getPlatformCanopy(int w0, int h0) {
        GuiPanel p = new GuiScrollablePanel(w0, h0);
        initProperties(p);

        canopyHeightField.getTextField().textChanged.clear();
        canopyHeightField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.canopyHeight = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        canopySlabSlot.slotChanged.clear();
        canopySlabSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.canopySlabId = e.newId;
            }
        });

        p.addControl(hasCanopyBtn);
        p.addControl(canopyHeightField);
        p.addControl(canopySlabSlot);
        p.addControl(canopyStyleBtn);

        return p;
    }

    private @NotNull GuiPanel getBuildingProperties(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel buildingProperties = new GuiPanel(propertyPanelWidth, middlePanelHeight);
        initProperties(buildingProperties);

        buildingPresetField.getTextField().textChanged.clear();
        buildingPresetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof BuildingElement b)
                b.presetName = e.newText;
        });

        GuiButton browseButton = new GuiButton(
                net.minecraft.text.Text.translatable("gui.stationbuilder.browse"),
                b -> {
                    if (client != null) {
                        client.setScreen(new BuildingSelectionScreen(this, buildingPresetField.getTextField()));
                    }
                },
                60, INPUT_HEIGHT
        );
        GuiButton importButton = new GuiButton(
                net.minecraft.text.Text.translatable("gui.stationbuilder.import_file"),
                b -> openFileChooser(),
                60, INPUT_HEIGHT
        );
        
        buildingProperties.addControl(buildingPresetField);
        buildingProperties.addControl(browseButton);
        buildingProperties.addControl(importButton);
        buildingProperties.addControl(buildingRotBtn);
        buildingProperties.addControl(buildingAirBtn);
        
        return buildingProperties;
    }

    public void openFileChooser() {
        String path;

        // 使用 LWJGL 的 MemoryStack 分配内存，避免内存泄漏
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            // 分配指针缓冲区来存放过滤规则
            org.lwjgl.PointerBuffer filters = stack.mallocPointer(4);
            filters.put(stack.UTF8("*.nbt"));
            filters.put(stack.UTF8("*.schem"));
            filters.put(stack.UTF8("*.schematic"));
            filters.put(stack.UTF8("*.litematic"));
            filters.flip();

            // 调用 TinyFileDialogs 打开原生文件选择器 (注意: 会阻塞当前线程直到对话框关闭)
            path = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                    "Select Structure File", // 对话框标题
                    null,                    // 默认路径
                    filters,                 // 过滤器
                    "Structure files (*.nbt, *.schem, *.schematic, *.litematic)", // 过滤器描述
                    false                    // 是否允许多选
            );
        }

        if (path != null) {
            java.io.File file = new java.io.File(path);

            // 使用异步去加载结构文件，防止卡死主线程太久
            java.util.concurrent.CompletableFuture.supplyAsync(() -> loadStructureFromFile(file))
                    .thenAcceptAsync(template -> {
                        if (template != null) {
                            String name = file.getName().replaceFirst("\\.[^.]+$", "");
                            BuildingTemplateManager.addTemplate(name, template);

                            // 更新文本框
                            buildingPresetField.setText(name);
                            // 更新当前选中的建筑元素
                            int index = canvas.getSelectedIndex();
                            if (index >= 0 && elements.get(index) instanceof BuildingElement b) {
                                b.presetName = name;
                                SyncCanvasWithElements();
                            }
                            if (client != null && client.player != null) {
                                client.player.sendMessage(net.minecraft.text.Text.translatable("gui.stationbuilder.import_success", name), false);
                            }
                        } else {
                            if (client != null && client.player != null) {
                                client.player.sendMessage(net.minecraft.text.Text.translatable("gui.stationbuilder.import_fail"), true);
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
                // Litematic 也可用 schematic4j 读取（SchematicReader 自动识别）
                return SchematicLoaderUtil.loadSchematic(file.toPath());
            } else {
                return null;
            }
        } catch (Exception e) {
            StationBuilder.LOGGER.error("Failed to load structure file: {}", file, e);
            return null;
        }
    }

    private @NotNull GuiPanel getEmptyProperties(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel panel = new GuiPanel(propertyPanelWidth, middlePanelHeight);
        initProperties(panel);
        panel.setMajorAlign(GuiPanel.MajorAlignMode.CENTER);
        panel.setCrossAlign(GuiPanel.CrossAlignMode.CENTER);
        panel.addControl(new GuiLabel(getText("please_select")));
        return panel;
    }

    private void SyncCanvasWithElements() {
        int oldIndex = this.canvas.getSelectedIndex();
        canvas.clear();
        for (var element : elements) {
            if (element instanceof PlatformElement p) {
                canvas.addRect(Math.min(p.getWidth(), 20) * 3, "P");
            } else if (element instanceof BuildingElement b) {
                canvas.addRect(Math.min(b.getWidth(), 20) * 3, "B");
            } else if (element instanceof TrackElement t) {
                canvas.addRect(Math.min(t.getWidth(), 20) * 3, "T");
            }
        }
        if (oldIndex >= 0) {
            canvas.select(oldIndex);
        }
    }

    public void applyPreset(PresetManager.PresetData data) {
        if (data == null) return;
        this.elements.clear();
        this.elements.addAll(data.elements());
        if (this.lengthField != null) {
            this.lengthField.setText(String.valueOf(data.length()));
        }
        SyncCanvasWithElements();
        refreshPropertyArea();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int oldIndex = canvas.getSelectedIndex();
        boolean ret = super.mouseClicked(mouseX, mouseY, button);
        
        // 当发生点击并在 Canvas 内选中新块或空白时，更新右侧的属性区域即可
        if (oldIndex != canvas.getSelectedIndex()) {
            refreshPropertyArea();
        }
        return ret;
    }

    @Override
    public void close() {
        // 关闭时自动发送保存包
        sendSyncPacket();
        super.close();
    }

    private void sendSyncPacket() {
        try {
            int length = Integer.parseInt(lengthField.getText());
            ClientPlayNetworking.send(
                    new StationBuilder.SaveStationPayload(
                            pos,
                            facing.getHorizontal(),
                            length,
                            new ArrayList<>(elements)
                    )
            );
        } catch (Exception ignored) {
        }
    }

    private void sendBuildPacket() {
        try {
            int length = Integer.parseInt(lengthField.getText());
            ClientPlayNetworking.send(
                    new StationBuilder.BuildStationPayload(
                            pos,
                            facing.getHorizontal(),
                            length,
                            new ArrayList<>(elements)
                    )
            );
        } catch (Exception ignored) {
        }
    }

    public int getStationLength() {
        try {
            return Integer.parseInt(this.lengthField.getText());
        } catch (NumberFormatException e) {
            return Integer.parseInt(initialLength); // 默认值
        }
    }

    public List<StationElement> getElements() {
        return this.elements;
    }
}
