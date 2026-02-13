package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.elements.BuildingElement;
import cn.myfrank.stationbuilder.elements.PlatformElement;
import cn.myfrank.stationbuilder.elements.StationElement;
import cn.myfrank.stationbuilder.elements.TrackElement;
import cn.myfrank.stationbuilder.gui.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.NotNull;

import static cn.myfrank.stationbuilder.StationBuilder.SAVE_DATA_PACKET;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StationEditorScreen extends GuiScreen {
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
            new Identifier("minecraft", "andesite"), false
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
            new Identifier("minecraft", "yellow_concrete"), false
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
            new Identifier("minecraft", "stone_slab"), false);
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
            INPUT_HEIGHT, new Identifier("minecraft", "stone_brick_wall"), false);
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
            INPUT_HEIGHT, new Identifier("minecraft", "sea_lantern"), false);
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
            new Identifier("mtr", "apg_glass_end"), false);
    private final GuiLabelSlot psdGlassSlot = new GuiLabelSlot(getText("psd_glass"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "apg_glass"), false);
    private final GuiLabelSlot psdDoorSlot = new GuiLabelSlot(getText("psd_door"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "apg_door"), false);
    private final GuiLabelButton pidsBtn = new GuiLabelButton(Text.empty(), b -> {
        int index = canvas.getSelectedIndex();
        if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
            pe.hasPids = !pe.hasPids;
            b.setMessage(pe.hasPids ? getText("pids_on") : getText("pids_off"));
        }
    }, BTN_WIDTH_XL, BTN_HEIGHT, getText("pids"));

    private final GuiLabelSlot pidBlockSlot = new GuiLabelSlot(getText("pids_block"), INPUT_WIDTH, INPUT_HEIGHT,
            new Identifier("mtr", "pids_1"), false);

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
            } else if(e instanceof BuildingElement b) {
                buildingProperties.setVisible(true);
                buildingPresetField.setText(b.presetName);
            } else {
                emptyProperties.setVisible(true);
            }
        }
    }
    @Override
    protected void initControls() {
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
        addControl(canvas);

        int elemOpPanelHeight = 24;
        GuiPanel elemOpPanel = new GuiPanel(width, elemOpPanelHeight)
        .addControl(new GuiButton(getText("add_track"), b -> {
            var e = new TrackElement(); elements.add(e);
            canvas.addRect(e.getWidth() * 3, "T");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT))
        .addControl(new GuiButton(getText("add_platform"), b -> {
            var e = new PlatformElement(); elements.add(e);
            canvas.addRect(e.getWidth() * 3, "P");
            refreshPropertyArea();
        }, BTN_WIDTH, BTN_HEIGHT))
        .addControl(new GuiButton(getText("add_building"), b -> {
            var e = new BuildingElement("matchbox"); elements.add(e);
            canvas.addRect(e.getWidth() * 3, "B");
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
            sendBuildPacket(StationBuilder.BUILD_PACKET_ID); this.close();
        }, BTN_WIDTH_L, BTN_HEIGHT));

        bottomPanel.setMajorAlign(GuiPanel.MajorAlignMode.END);
        addControl(bottomPanel);
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

        psdEndSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdEndId = e.newId;
            }
        });

        psdGlassSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdGlassId = e.newId;
            }
        });

        psdDoorSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.psdDoorId = e.newId;
            }
        });

        // PIDs 开关
        p.addControl(shieldDoorBtn).addControl(doorOffsetField).addControl(doorSpacingField);
        p.addControl(psdEndSlot).addControl(psdGlassSlot).addControl(psdDoorSlot);
        p.addControl(pidsBtn).addControl(pidBlockSlot);
        return p;
    }

    private @NotNull GuiPanel getPlatformBase(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel base = new GuiScrollablePanel(propertyPanelWidth, middlePanelHeight);
        initProperties(base);
        var topPanel = new GuiPanel(propertyPanelWidth, INPUT_HEIGHT);
        platformLengthField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement p)
                try { p.width = Integer.parseInt(e.newText); } catch (Exception ex) {}
        });
        platformSafetyField.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement p) {
                p.safetyBlock = e.newId;
                autoRotate.setVisible(net.minecraft.registry.Registries.BLOCK.get(p.safetyBlock).
                        getDefaultState().contains(Properties.HORIZONTAL_FACING));
            }
        });

        autoRotate.setForeColor(0xFF00FF00);
        topPanel.addControl(platformLengthField).addControl(platformSafetyField).addControl(autoRotate);
        base.addControl(topPanel);
        for (int i = 0; i < 5; i++) {
            final int fieldIndex = i;
            weightFields[i] = new GuiLabelSlotInput(
                    Text.translatable("gui.stationbuilder.platform_blocks", i + 1),
                    INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone"),
                    false, Text.empty()
            );
            weightFields[i].getTextField().textChanged.addHandler((sender, e) -> {
                int selectedCanvasIndex = canvas.getSelectedIndex();
                if (selectedCanvasIndex >= 0 && elements.get(selectedCanvasIndex) instanceof PlatformElement p) {
                    try {
                        if (fieldIndex < p.mixSlots.length) {
                            p.mixSlots[fieldIndex].weight = Double.parseDouble(e.newText);
                        }
                    } catch (Exception ignored) {
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

        pillarBlockSlot.slotChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                pe.pillarBlockId = e.newId;
            }
        });

        pillarSpacingField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.pillarSpacing = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

        pillarOffsetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.firstPillarOffset = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

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

        canopyHeightField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof PlatformElement pe) {
                try { pe.canopyHeight = Integer.parseInt(e.newText); } catch (Exception ignored) {}
            }
        });

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
        buildingPresetField.setText("matchbox");
        buildingPresetField.getTextField().textChanged.addHandler((sender, e) -> {
            int index = canvas.getSelectedIndex();
            if (index >= 0 && elements.get(index) instanceof BuildingElement b)
                b.presetName = e.newText;
        });
        buildingProperties.addControl(buildingPresetField);
        return buildingProperties;
    }

    private @NotNull GuiPanel getEmptyProperties(int propertyPanelWidth, int middlePanelHeight) {
        GuiPanel panel = new  GuiPanel(propertyPanelWidth, middlePanelHeight);
        initProperties(panel);
        panel.setMajorAlign(GuiPanel.MajorAlignMode.CENTER);
        panel.setCrossAlign(GuiPanel.CrossAlignMode.CENTER);
        panel.addControl(new GuiLabel(getText("please_select")));
        return panel;
    }

    private void SyncCanvasWithElements() {
        canvas.clear();
        for (var element : elements) {
            if (element instanceof PlatformElement p) {
                canvas.addRect(p.getWidth() * 3, "P");
            } else if (element instanceof BuildingElement b) {
                canvas.addRect(b.getWidth() * 3, "B");
            } else if (element instanceof TrackElement t) {
                canvas.addRect(t.getWidth() * 3, "T");
            }
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

    public int getActiveSlotIndex() {
        if (platformSafetyField.isActive() || trackBallastField.isActive()) {
            return 0;
        }
        if (canopySlabSlot != null && canopySlabSlot.isActive()) return 100;
        if (pillarBlockSlot != null && pillarBlockSlot.isActive()) return 101;
        if (lightBlockSlot != null && lightBlockSlot.isActive()) return 102;
        if (pidBlockSlot != null && pidBlockSlot.isActive()) return 200;
        if (psdEndSlot != null && psdEndSlot.isActive()) return 201;
        if (psdGlassSlot != null && psdGlassSlot.isActive()) return 202;
        if (psdDoorSlot != null && psdDoorSlot.isActive()) return 203;
        for (int i = 0; i < 5; i++) {
            if (weightFields[i].isActive()) {
                return i + 1;
            }
        }
        return -1;
    }

    private void updateSelectedElementBlock(Identifier newId) {
        final int index = canvas.getSelectedIndex();
        if (index < 0) return;
        StationElement e = elements.get(index);
        final int slot = getActiveSlotIndex();
        if (e instanceof TrackElement t) {
            if (slot == 0) t.ballastBlock = newId;
        }else if (e instanceof PlatformElement p) {
            if (slot == 0) p.safetyBlock = newId;
            else if (slot == 100) p.canopySlabId = newId;
            else if (slot == 101) p.pillarBlockId = newId;
            else if (slot == 102) p.lightBlockId = newId;
            else if (slot == 200) p.pidBlockId = newId;
            else if (slot == 201) p.psdEndId = newId;
            else if (slot == 202) p.psdGlassId = newId;
            else if (slot == 203) p.psdDoorId = newId;
            else if (slot > 0) p.mixSlots[slot - 1].blockId = newId;
        }
        refreshPropertyArea();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean ret = false;
        if(ghostInventory.isMouseOver(mouseX, mouseY)) {
            var stack = ghostInventory.getSelectedItemStack();
            if (stack.isEmpty()) {
                updateSelectedElementBlock(new Identifier("minecraft", "air"));
            }
            else if (stack.getItem() instanceof net.minecraft.item.BlockItem bi) {
                updateSelectedElementBlock(
                        net.minecraft.registry.Registries.BLOCK.getId(bi.getBlock())
                );
            } else if (getActiveSlotIndex() >= 200){
                updateSelectedElementBlock(
                        net.minecraft.registry.Registries.ITEM.getId(stack.getItem())
                );
            }
            ret = true;
        }
        ret = ret || super.mouseClicked(mouseX, mouseY, button);
        if (canvas.isMouseOver(mouseX, mouseY)) {
            ret = true;
            refreshPropertyArea();
        }
        return ret;
    }

    @Override
    public void close() {
        // 关闭时自动发送保存包
        sendSyncPacket(SAVE_DATA_PACKET);
        super.close();
    }

    private void sendSyncPacket(Identifier packetId) {
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeBlockPos(pos);
            buf.writeInt(facing.getHorizontal());
            buf.writeInt(Integer.parseInt(lengthField.getText()));
            buf.writeInt(elements.size());
            for (StationElement e : elements) e.write(buf);
            ClientPlayNetworking.send(packetId, buf);
        } catch (Exception ignored) {}
    }

    private void sendBuildPacket(Identifier packetId) {
        sendSyncPacket(packetId);
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
