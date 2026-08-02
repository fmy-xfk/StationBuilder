package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.gui.*;
import cn.myfrank.stationbuilder.gui.GuiPanel.CrossAlignMode;
import cn.myfrank.stationbuilder.gui.GuiPanel.PanelDirection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RailBuilderScreen extends GuiScreen {
    private static final int BUTTON_WIDTH = 70;
    private static final int BUTTON_WIDTH_S = 60;
    private static final int INPUT_WIDTH_S = 30;
    private static final int INPUT_HEIGHT = 18;
    private int catenaryState = 1; // 0=Disabled, 1=Vanilla, 2=MSD

    private String initialRailCount;
    private final GuiLabelTextField railCountInput = new GuiLabelTextField(getText("rail_count"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("2"));
    private final GuiLabelTextField railSpacingInput = new GuiLabelTextField(getText("rail_spacing"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("5.0"));
    private final GuiLabelSlot ballastBlockInput = new GuiLabelSlot(getText("ballast_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "andesite"), false);
    private final GuiLabelSlot railTypeInput = new GuiLabelSlot(getText("rail_type"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "rail"), false);
    private final GuiLabelTextField ballastTopWidthInput = new GuiLabelTextField(getText("ballast_top_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("5.0"));
    private final GuiLabelTextField ballastBottomWidthInput = new GuiLabelTextField(getText("ballast_bottom_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("11.0"));
    private final GuiLabelTextField ballastMaxThicknessInput = new GuiLabelTextField(getText("ballast_max_thickness"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("4"));
    private final GuiLabelTextField bridgeClearSpanInput = new GuiLabelTextField(getText("bridge_clear_span"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("50"));
    private final GuiLabelSlot bridgeGuardRailBlockInput = new GuiLabelSlot(getText("bridge_guardrail_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone_brick_wall"), false);
    private final GuiLabelSlot bridgeBlockInput = new GuiLabelSlot(getText("bridge_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "smooth_stone"), false);
    private final GuiLabelSlot bridgePillarBlockInput = new GuiLabelSlot(getText("bridge_pillar_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "light_gray_concrete"), false);
    private final GuiLabelTextField bridgeWidthInput = new GuiLabelTextField(getText("bridge_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("7.0"));
    private final GuiLabelTextField tunnelHeightInput = new GuiLabelTextField(getText("tunnel_height"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("7"));
    private final GuiLabelSlot tunnelWallBlockInput = new GuiLabelSlot(getText("tunnel_wall_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone"), false);
    private final GuiLabelSlot tunnelCeilingBlockInput = new GuiLabelSlot(getText("tunnel_ceiling_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "light_gray_concrete"), false);
    private final GuiLabelSlot tunnelFloorBlockInput = new GuiLabelSlot(getText("tunnel_floor_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "andesite"), false);
    private final GuiLabelTextField tunnelWidthInput = new GuiLabelTextField(getText("tunnel_width"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("7.0"));
    private boolean clearFullHeight = false;
    private final GuiLabelButton clearModeButton = new GuiLabelButton(getText("clear_mode_v"), (button) -> {
        clearFullHeight = !clearFullHeight;
        syncClearMode();
    }, BUTTON_WIDTH_S, INPUT_HEIGHT, getText("clear_mode"));

    private final GuiButton catenaryStateButton = new GuiButton(getText("catenary_vanilla"), (button) -> {
        catenaryState = (catenaryState + 1) % (StationBuilder.isMsdLoaded() ? 3 : 2);
        syncCatenaryState();
    }, BUTTON_WIDTH, INPUT_HEIGHT);
    private final GuiLabelSlot catenaryLineBlockInput = new GuiLabelSlot(getText("catenary_line_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "cobweb"), false);
    private final GuiLabelTextField catenarySpacingInput = new GuiLabelTextField(getText("catenary_spacing"), INPUT_WIDTH_S, INPUT_HEIGHT, Text.literal("50"));
    private int catenaryModeIndex = 0;
    private final GuiButton catenaryModeButton = new GuiButton(getText("catenary"), (button) -> {
        catenaryModeIndex = (catenaryModeIndex + 1) % CatenaryTypeMapping.values().length;
        syncCatenaryMode(button);
        syncCatenaryState();
    }, BUTTON_WIDTH, INPUT_HEIGHT);
    private final GuiLabelSlot catenaryBridgePillarInput = new GuiLabelSlot(getText("catenary_bridge_pillar"), INPUT_WIDTH_S, INPUT_HEIGHT, StationBuilder.isMsdLoaded() ? new Identifier("msd", "catenary_with_long") : new Identifier("minecraft", "stone_brick_wall"), false);
    private final GuiLabelSlot catenaryTunnelPillarInput = new GuiLabelSlot(getText("catenary_tunnel_pillar"), INPUT_WIDTH_S, INPUT_HEIGHT, StationBuilder.isMsdLoaded() ? new Identifier("msd", "catenary_with_long_top") : new Identifier("minecraft", "stone_brick_wall"), false);
    private final GhostInventory ghostInventory = new GhostInventory();

    private void syncCatenaryMode(GuiButton button) {
        var type = CatenaryTypeMapping.values()[catenaryModeIndex];
        button.setMessage(Text.translatable("gui.stationbuilder." + type.getName()));
    }
    private void syncClearMode() {
        clearModeButton.setMessage(getText(clearFullHeight ? "clear_mode_v" : "clear_mode_tunnel"));
    }
    private void syncCatenaryState() {
        if (catenaryState == 0) {
            catenaryStateButton.setMessage(getText("catenary_disabled"));
            catenarySpacingInput.setVisible(false);
            catenaryModeButton.setVisible(false);
            catenaryLineBlockInput.setVisible(false);
            catenaryBridgePillarInput.setVisible(false);
            catenaryTunnelPillarInput.setVisible(false);
        } else if (catenaryState == 1) {
            catenaryStateButton.setMessage(getText("catenary_vanilla"));
            catenarySpacingInput.setVisible(true);
            catenaryModeButton.setVisible(false);
            catenaryLineBlockInput.setVisible(true);
            catenaryBridgePillarInput.setVisible(true);
            catenaryTunnelPillarInput.setVisible(true);
        } else {
            catenaryStateButton.setMessage(getText("catenary_msd"));
            catenarySpacingInput.setVisible(true);
            catenaryModeButton.setVisible(true);
            catenaryLineBlockInput.setVisible(false);
            catenaryBridgePillarInput.setVisible(true);
            catenaryTunnelPillarInput.setVisible(true);
            boolean isAuto = catenaryModeIndex == 0;
            catenaryBridgePillarInput.setVisible(!isAuto);
            catenaryTunnelPillarInput.setVisible(!isAuto);
        }
    }
    private static Text getText(String key) {
        return Text.translatable("gui.stationbuilder." + key);
    }

    public RailBuilderScreen(NbtCompound nbt) {
        super(getText("rail_builder"));
        this.railCountInput.getTextField().setNumberOnly(true);
        if (nbt.contains("railCount", NbtElement.INT_TYPE)) {
            this.railCountInput.setText(String.valueOf(nbt.getInt("railCount")));
        }
        if (nbt.contains("railSpacing", NbtElement.DOUBLE_TYPE)) {
            this.railSpacingInput.setText(String.valueOf(nbt.getDouble("railSpacing")));
        }
        if (nbt.contains("ballastBlock", NbtElement.STRING_TYPE)) {
            this.ballastBlockInput.setBlockId(new Identifier(nbt.getString("ballastBlock")));
        }
        if (nbt.contains("railType", NbtElement.STRING_TYPE)) {
            this.railTypeInput.setBlockId(new Identifier(nbt.getString("railType")));
        }
        this.ballastTopWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("ballastTopWidth", NbtElement.DOUBLE_TYPE)) {
            this.ballastTopWidthInput.setText(String.valueOf(nbt.getDouble("ballastTopWidth")));
        }
        this.ballastBottomWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("ballastBottomWidth", NbtElement.DOUBLE_TYPE)) {
            this.ballastBottomWidthInput.setText(String.valueOf(nbt.getDouble("ballastBottomWidth")));
        }
        this.ballastMaxThicknessInput.getTextField().setNumberOnly(true);
        if (nbt.contains("ballastMaxThickness", NbtElement.INT_TYPE)) {
            this.ballastMaxThicknessInput.setText(String.valueOf(nbt.getInt("ballastMaxThickness")));
        }
        this.bridgeClearSpanInput.getTextField().setNumberOnly(true);
        if (nbt.contains("bridgeClearSpan", NbtElement.INT_TYPE)) {
            this.bridgeClearSpanInput.setText(String.valueOf(nbt.getInt("bridgeClearSpan")));
        }
        if (nbt.contains("bridgeGuardRailBlock", NbtElement.STRING_TYPE)) {
            this.bridgeGuardRailBlockInput.setBlockId(new Identifier(nbt.getString("bridgeGuardRailBlock")));
        }
        if (nbt.contains("bridgeBlock", NbtElement.STRING_TYPE)) {
            this.bridgeBlockInput.setBlockId(new Identifier(nbt.getString("bridgeBlock")));
        }
        if (nbt.contains("bridgePillarBlock", NbtElement.STRING_TYPE)) {
            this.bridgePillarBlockInput.setBlockId(new Identifier(nbt.getString("bridgePillarBlock")));
        }
        this.bridgeWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("bridgeWidth", NbtElement.DOUBLE_TYPE)) {
            this.bridgeWidthInput.setText(String.valueOf(nbt.getDouble("bridgeWidth")));
        }
        this.tunnelHeightInput.getTextField().setNumberOnly(true);
        if (nbt.contains("tunnelHeight", NbtElement.INT_TYPE)) {
            this.tunnelHeightInput.setText(String.valueOf(nbt.getInt("tunnelHeight")));
        }
        if (nbt.contains("tunnelWallBlock", NbtElement.STRING_TYPE)) {
            this.tunnelWallBlockInput.setBlockId(new Identifier(nbt.getString("tunnelWallBlock")));
        }
        if (nbt.contains("tunnelCeilingBlock", NbtElement.STRING_TYPE)) {
            this.tunnelCeilingBlockInput.setBlockId(new Identifier(nbt.getString("tunnelCeilingBlock")));
        }
        if (nbt.contains("tunnelFloorBlock", NbtElement.STRING_TYPE)) {
            this.tunnelFloorBlockInput.setBlockId(new Identifier(nbt.getString("tunnelFloorBlock")));
        }
        this.tunnelWidthInput.getTextField().setNumberOnly(true);
        if (nbt.contains("tunnelWidth", NbtElement.DOUBLE_TYPE)) {
            this.tunnelWidthInput.setText(String.valueOf(nbt.getDouble("tunnelWidth")));
        }
        if (nbt.contains("clearFullHeight")) {
            this.clearFullHeight = nbt.getBoolean("clearFullHeight");
        } else {
            this.clearFullHeight = true;
        }
        syncClearMode();

        boolean useCat = !nbt.contains("useCatenary") || nbt.getBoolean("useCatenary");
        boolean isVan = nbt.contains("isVanillaCatenary") ? nbt.getBoolean("isVanillaCatenary") : !StationBuilder.isMsdLoaded();
        if (!useCat) {
            this.catenaryState = 0;
        } else if (isVan) {
            this.catenaryState = 1;
        } else {
            this.catenaryState = StationBuilder.isMsdLoaded() ? 2 : 1;
        }

        if (nbt.contains("catenaryModeIndex", NbtElement.INT_TYPE)) {
            this.catenaryModeIndex = nbt.getInt("catenaryModeIndex");
        } else {
            this.catenaryModeIndex = 0;
        }
        syncCatenaryMode(catenaryModeButton);
        syncCatenaryState();

        if (nbt.contains("catenaryBlock", NbtElement.STRING_TYPE)) {
            this.catenaryLineBlockInput.setBlockId(new Identifier(nbt.getString("catenaryBlock")));
        }
        this.catenarySpacingInput.getTextField().setNumberOnly(true);
        if (nbt.contains("catenarySpacing", NbtElement.INT_TYPE)) {
            this.catenarySpacingInput.setText(String.valueOf(nbt.getInt("catenarySpacing")));
        }
        if (nbt.contains("catenaryBridgePillar", NbtElement.STRING_TYPE)) {
            this.catenaryBridgePillarInput.setBlockId(new Identifier(nbt.getString("catenaryBridgePillar")));
        }
        if (nbt.contains("catenaryTunnelPillar", NbtElement.STRING_TYPE)) {
            this.catenaryTunnelPillarInput.setBlockId(new Identifier(nbt.getString("catenaryTunnelPillar")));
        }
        this.initialRailCount = nbt.contains("railCount", NbtElement.INT_TYPE)
                ? String.valueOf(nbt.getInt("railCount"))
                : "2"; // 默认值应与 railCountInput 的默认文本一致
    }

    public NbtCompound getNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("railCount", Integer.parseInt(railCountInput.getText()));
        nbt.putDouble("railSpacing", Double.parseDouble(railSpacingInput.getText()));
        nbt.putString("ballastBlock", ballastBlockInput.getBlockId().toString());
        nbt.putString("railType", railTypeInput.getBlockId().toString());
        nbt.putDouble("ballastTopWidth", Double.parseDouble(ballastTopWidthInput.getText()));
        nbt.putDouble("ballastBottomWidth", Double.parseDouble(ballastBottomWidthInput.getText()));
        nbt.putInt("ballastMaxThickness", Integer.parseInt(ballastMaxThicknessInput.getText()));
        nbt.putInt("bridgeClearSpan", Integer.parseInt(bridgeClearSpanInput.getText()));
        nbt.putString("bridgeGuardRailBlock", bridgeGuardRailBlockInput.getBlockId().toString());
        nbt.putString("bridgeBlock", bridgeBlockInput.getBlockId().toString());
        nbt.putString("bridgePillarBlock", bridgePillarBlockInput.getBlockId().toString());
        nbt.putDouble("bridgeWidth", Double.parseDouble(bridgeWidthInput.getText()));
        nbt.putInt("tunnelHeight", Integer.parseInt(tunnelHeightInput.getText()));
        nbt.putString("tunnelWallBlock", tunnelWallBlockInput.getBlockId().toString());
        nbt.putString("tunnelCeilingBlock", tunnelCeilingBlockInput.getBlockId().toString());
        nbt.putString("tunnelFloorBlock", tunnelFloorBlockInput.getBlockId().toString());
        nbt.putDouble("tunnelWidth", Double.parseDouble(tunnelWidthInput.getText()));
        nbt.putBoolean("clearFullHeight", clearFullHeight);
        nbt.putBoolean("useCatenary", catenaryState != 0);
        nbt.putBoolean("isVanillaCatenary", catenaryState == 1);
        nbt.putString("catenaryBlock", catenaryLineBlockInput.getBlockId().toString());
        nbt.putInt("catenarySpacing", Integer.parseInt(catenarySpacingInput.getText()));
        nbt.putInt("catenaryModeIndex", catenaryModeIndex);
        nbt.putString("catenaryBridgePillar", catenaryBridgePillarInput.getBlockId().toString());
        nbt.putString("catenaryTunnelPillar", catenaryTunnelPillarInput.getBlockId().toString());
        return nbt;
    }
    
    @Override
    protected void initControls() {
        int w = (width - 15) / 4, h = 110;
        var topPanel = new GuiPanel(width - 15, h);
        topPanel.setGap(2);
        topPanel.setDirection(PanelDirection.HORIZONTAL);

        var bottomPanel = new GuiPanel(width - 15, h);
        bottomPanel.setGap(2);
        bottomPanel.setDirection(PanelDirection.HORIZONTAL);

        var railPanel = new GuiPanel(w, h);
        railPanel
                .addControl(new GuiLabel(getText("rail")))
                .addControl(railCountInput)
                .addControl(railSpacingInput)
                .addControl(railTypeInput)
                .addControl(clearModeButton)
                .setDirection(PanelDirection.VERTICAL);
        railPanel.setCrossAlign(CrossAlignMode.START);
        railPanel.setGap(2);

        var ballastPanel = new GuiPanel(w, h);
        ballastPanel.addControl(new GuiLabel(getText("ballast")))
            .addControl(ballastBlockInput)
            .addControl(ballastTopWidthInput)
            .addControl(ballastBottomWidthInput)
            .addControl(ballastMaxThicknessInput)
            .setDirection(PanelDirection.VERTICAL);
        ballastPanel.setCrossAlign(CrossAlignMode.START);
        ballastPanel.setGap(2);

        var bridgePanel = new GuiPanel(w + 5, h);
        bridgePanel
            .addControl(new GuiLabel(getText("bridge")))
            .addControl(bridgeClearSpanInput)
            .addControl(bridgeGuardRailBlockInput)
            .addControl(bridgeBlockInput)
            .addControl(bridgePillarBlockInput)
            .addControl(bridgeWidthInput)
            .setDirection(PanelDirection.VERTICAL);
        bridgePanel.setCrossAlign(CrossAlignMode.START);
        bridgePanel.setGap(2);

        var tunnelPanel = new GuiPanel(w, h);
        tunnelPanel
            .addControl(new GuiLabel(getText("tunnel")))
            .addControl(tunnelHeightInput)
            .addControl(tunnelWallBlockInput)
            .addControl(tunnelCeilingBlockInput)
            .addControl(tunnelFloorBlockInput)
            .addControl(tunnelWidthInput)
            .setDirection(PanelDirection.VERTICAL);
        tunnelPanel.setCrossAlign(CrossAlignMode.START);
        tunnelPanel.setGap(2);

        var catenaryPanel = new GuiPanel(w * 2, h);
        catenaryPanel
                .addControl(new GuiLabel(getText("catenary")))
                .addControl(catenaryStateButton)
                .addControl(catenarySpacingInput)
                .addControl(catenaryModeButton)
                .addControl(catenaryLineBlockInput)
                .addControl(catenaryBridgePillarInput)
                .addControl(catenaryTunnelPillarInput)
                .setDirection(PanelDirection.VERTICAL);
        catenaryPanel.setCrossAlign(CrossAlignMode.START);
        catenaryPanel.setGap(2);

        topPanel.addControl(railPanel).addControl(ballastPanel).addControl(catenaryPanel);
        addControl(topPanel);

        bottomPanel.addControl(bridgePanel).addControl(tunnelPanel).addControl(ghostInventory);
        addControl(bottomPanel);
    }

    @Override
    public void close() {
        String currentRailCount = railCountInput.getText();
        // 如果 railCount 发生了变化，先清除状态
        if (!currentRailCount.equals(initialRailCount)) {
            ClientPlayNetworking.send(StationBuilder.CLEAR_RAIL_PACKET, PacketByteBufs.create());
        }
        // 关闭时自动发送保存包
        sendSyncPacket(StationBuilder.SAVE_DATA_PACKET_RAIL);
        super.close();
    }

    private void sendSyncPacket(Identifier packetId) {
        try {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeNbt(getNbt());
            ClientPlayNetworking.send(packetId, buf);
        } catch (Exception ignored) {}
    }
}
