package cn.myfrank.stationbuilder.screens;

import cn.myfrank.stationbuilder.elements.PassageConfig;
import cn.myfrank.stationbuilder.gui.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

public class PassageConfigScreen extends GuiScreen {
    private static final int INPUT_WIDTH = 50;
    private static final int INPUT_HEIGHT = 18;
    private static final int PANEL_HEIGHT = 24;

    private final StationEditorScreen parent;
    private final PassageConfig passage;
    private final GhostInventory ghostInventory = new GhostInventory();

    public PassageConfigScreen(StationEditorScreen parent, PassageConfig passage) {
        super(getText("passage_config"));
        this.parent = parent;
        this.passage = passage;
    }

    private static Text getText(String key) {
        return Text.translatable("gui.stationbuilder." + key);
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

        GuiPanel titlePanel = new GuiPanel(width, INPUT_HEIGHT);
        titlePanel.addControl(new GuiLabel(getText("passage_config")));
        addControl(titlePanel);

        int tabHeight = Math.max(60, height - INPUT_HEIGHT - PANEL_HEIGHT - rootPanel.getGap() * 2);
        int tabsWidth = Math.max(200, width - ghostInventory.getWidth() - 2);
        GuiTab tabs = new GuiTab(tabsWidth, tabHeight);
        tabs.setTabButtonWidth(90);
        tabs.addTab(getText("passage_underground"),
                getUndergroundTab(tabs.getContentWidth(), tabs.getContentHeight()));
        tabs.addTab(getText("passage_skybridge"),
                getSkybridgeTab(tabs.getContentWidth(), tabs.getContentHeight()));

        GuiPanel middlePanel = new GuiPanel(width, tabHeight);
        middlePanel.setGap(2);
        middlePanel.setCrossAlign(GuiPanel.CrossAlignMode.START);
        middlePanel.addControl(tabs).addControl(ghostInventory);
        addControl(middlePanel);

        GuiPanel bottomPanel = new GuiPanel(width, PANEL_HEIGHT);
        bottomPanel.setMajorAlign(GuiPanel.MajorAlignMode.END);
        bottomPanel.addControl(new GuiButton(getText("done"), b -> close(), 60, INPUT_HEIGHT));
        addControl(bottomPanel);
    }

    private GuiPanel createTabPanel(int width, int height) {
        GuiPanel panel = new GuiScrollablePanel(width, height);
        panel.setPadding(10);
        panel.setGap(2);
        panel.setDirection(GuiPanel.PanelDirection.VERTICAL);
        panel.setCrossAlign(GuiPanel.CrossAlignMode.START);
        return panel;
    }

    private void addSlot(GuiPanel panel, String key,
                         Identifier current, Consumer<Identifier> setter) {
        GuiLabelSlot slot = new GuiLabelSlot(getText(key), INPUT_WIDTH, INPUT_HEIGHT, current, false);
        slot.slotChanged.addHandler((sender, e) -> setter.accept(e.newId));
        panel.addControl(slot);
    }

    private GuiPanel getUndergroundTab(int width, int height) {
        GuiPanel panel = createTabPanel(width, height);
        addSlot(panel, "floor", passage.undergroundFloorBlock,
                id -> passage.undergroundFloorBlock = id);
        addSlot(panel, "ceiling", passage.undergroundCeilingBlock,
                id -> passage.undergroundCeilingBlock = id);
        addSlot(panel, "light", passage.undergroundLightBlock,
                id -> passage.undergroundLightBlock = id);
        addSlot(panel, "wall", passage.undergroundWallBlock,
                id -> passage.undergroundWallBlock = id);
        addSlot(panel, "stairs", passage.undergroundStairBlock,
                id -> passage.undergroundStairBlock = id);
        addSlot(panel, "stair_wall", passage.undergroundStairWallBlock,
                id -> passage.undergroundStairWallBlock = id);
        addSlot(panel, "stair_rail", passage.undergroundStairRailBlock,
                id -> passage.undergroundStairRailBlock = id);
        return panel;
    }

    private GuiPanel getSkybridgeTab(int width, int height) {
        GuiPanel panel = createTabPanel(width, height);
        addSlot(panel, "floor", passage.skybridgeFloorBlock,
                id -> passage.skybridgeFloorBlock = id);
        addSlot(panel, "ceiling", passage.skybridgeCeilingBlock,
                id -> passage.skybridgeCeilingBlock = id);
        addSlot(panel, "light", passage.skybridgeLightBlock,
                id -> passage.skybridgeLightBlock = id);
        addSlot(panel, "wall", passage.skybridgeWallBlock,
                id -> passage.skybridgeWallBlock = id);
        addSlot(panel, "stairs", passage.skybridgeStairBlock,
                id -> passage.skybridgeStairBlock = id);
        addSlot(panel, "stair_wall", passage.skybridgeStairWallBlock,
                id -> passage.skybridgeStairWallBlock = id);
        return panel;
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }
}
