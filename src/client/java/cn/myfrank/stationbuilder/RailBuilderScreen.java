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
    private static final int INPUT_WIDTH = 50;
    private static final int INPUT_WIDTH_S = 30;
    private static final int INPUT_HEIGHT = 18;
    private boolean useCatenary = true;

    private final GuiLabelTextField railCountInput = new GuiLabelTextField(getText("rail_count"), INPUT_WIDTH, INPUT_HEIGHT, Text.literal("2"));
    private final GuiLabelTextField railSpacingInput = new GuiLabelTextField(getText("rail_spacing"), INPUT_WIDTH, INPUT_HEIGHT, Text.literal("5.0"));
    private final GuiLabelSlot ballastBlockInput = new GuiLabelSlot(getText("ballast_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "andesite"), false);
    private final GuiLabelSlot railTypeInput = new GuiLabelSlot(getText("rail_type"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "rail"), false);
    private final GuiLabelTextField bridgeClearSpanInput = new GuiLabelTextField(getText("bridge_clear_span"), INPUT_WIDTH, INPUT_HEIGHT, Text.literal("50"));
    private final GuiLabelSlot bridgeGuardRailBlockInput = new GuiLabelSlot(getText("bridge_guardrail_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone_brick_wall"), false);
    private final GuiLabelSlot bridgeBlockInput = new GuiLabelSlot(getText("bridge_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "smooth_stone"), false);
    private final GuiLabelSlot bridgePillarBlockInput = new GuiLabelSlot(getText("bridge_pillar_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "light_gray_concrete"), false);
    private final GuiLabelTextField tunnelHeightInput = new GuiLabelTextField(getText("tunnel_height"), INPUT_WIDTH, INPUT_HEIGHT, Text.literal("7"));
    private final GuiLabelSlot tunnelWallBlockInput = new GuiLabelSlot(getText("tunnel_wall_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "stone"), false);
    private final GuiLabelSlot tunnelCeilingBlockInput = new GuiLabelSlot(getText("tunnel_ceiling_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "light_gray_concrete"), false);
    private final GuiLabelSlot tunnelFloorBlockInput = new GuiLabelSlot(getText("tunnel_floor_block"), INPUT_WIDTH_S, INPUT_HEIGHT, new Identifier("minecraft", "andesite"), false);
    private final GuiButton catenaryEnableButton = new GuiButton(getText("catenary_enabled"), (button) -> {
        useCatenary = !useCatenary;
        syncCatenaryEnabled(button);
    }, BUTTON_WIDTH, INPUT_HEIGHT);
    private final GuiLabelTextField catenarySpacingInput = new GuiLabelTextField(getText("catenary_spacing"), INPUT_WIDTH, INPUT_HEIGHT, Text.literal("50"));
    private final GhostInventory ghostInventory = new GhostInventory();

    private void syncCatenaryEnabled(GuiButton button) {
        button.setMessage(useCatenary ? getText("catenary_enabled") : getText("catenary_disabled"));
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
        this.useCatenary = nbt.getBoolean("useCatenary");
        syncCatenaryEnabled(this.catenaryEnableButton);
        this.catenarySpacingInput.getTextField().setNumberOnly(true);
        if (nbt.contains("catenarySpacing", NbtElement.INT_TYPE)) {
            this.catenarySpacingInput.setText(String.valueOf(nbt.getInt("catenarySpacing")));
        }
    }

    public NbtCompound getNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("railCount", Integer.parseInt(railCountInput.getText()));
        nbt.putDouble("railSpacing", Double.parseDouble(railSpacingInput.getText()));
        nbt.putString("ballastBlock", ballastBlockInput.getBlockId().toString());
        nbt.putString("railType", railTypeInput.getBlockId().toString());
        nbt.putInt("bridgeClearSpan", Integer.parseInt(bridgeClearSpanInput.getText()));
        nbt.putString("bridgeGuardRailBlock", bridgeGuardRailBlockInput.getBlockId().toString());
        nbt.putString("bridgeBlock", bridgeBlockInput.getBlockId().toString());
        nbt.putString("bridgePillarBlock", bridgePillarBlockInput.getBlockId().toString());
        nbt.putInt("tunnelHeight", Integer.parseInt(tunnelHeightInput.getText()));
        nbt.putString("tunnelWallBlock", tunnelWallBlockInput.getBlockId().toString());
        nbt.putString("tunnelCeilingBlock", tunnelCeilingBlockInput.getBlockId().toString());
        nbt.putString("tunnelFloorBlock", tunnelFloorBlockInput.getBlockId().toString());
        nbt.putBoolean("useCatenary", useCatenary);
        nbt.putInt("catenarySpacing", Integer.parseInt(catenarySpacingInput.getText()));
        return nbt;
    }
    
    @Override
    protected void initControls() {
        int w = (width - 35) / 4, h = 120;
        var topPanel = new GuiPanel(width - 35, h);
        topPanel.setGap(5);
        topPanel.setDirection(PanelDirection.HORIZONTAL);
        var railPanel = new GuiPanel(w, h);
        railPanel
            .addControl(new GuiLabel(getText("rail")))
            .addControl(railCountInput)
            .addControl(railSpacingInput)
            .addControl(ballastBlockInput)
            .addControl(railTypeInput)
            .setDirection(PanelDirection.VERTICAL);
        railPanel.setCrossAlign(CrossAlignMode.START);
        var bridgePanel = new GuiPanel(w, h);
        bridgePanel
            .addControl(new GuiLabel(getText("bridge")))
            .addControl(bridgeClearSpanInput)
            .addControl(bridgeGuardRailBlockInput)
            .addControl(bridgeBlockInput)
            .addControl(bridgePillarBlockInput)
            .setDirection(PanelDirection.VERTICAL);
        bridgePanel.setCrossAlign(CrossAlignMode.START);
        var tunnelPanel = new GuiPanel(w, h);
        tunnelPanel
            .addControl(new GuiLabel(getText("tunnel")))
            .addControl(tunnelHeightInput)
            .addControl(tunnelWallBlockInput)
            .addControl(tunnelCeilingBlockInput)
            .addControl(tunnelFloorBlockInput)
            .setDirection(PanelDirection.VERTICAL);
        tunnelPanel.setCrossAlign(CrossAlignMode.START);
        var catenaryPanel = new GuiPanel(w, h);
        catenaryPanel
                .addControl(new GuiLabel(getText("catenary")))
                .addControl(catenaryEnableButton)
                .addControl(catenarySpacingInput)
                .setDirection(PanelDirection.VERTICAL);
        catenaryPanel.setCrossAlign(CrossAlignMode.START);
        topPanel.addControl(railPanel).addControl(bridgePanel).addControl(tunnelPanel).addControl(catenaryPanel);
        addControl(topPanel);
        addControl(ghostInventory);
        ghostInventory.addSlot(ballastBlockInput)
            .addSlot(railTypeInput)
            .addSlot(bridgeGuardRailBlockInput)
            .addSlot(bridgeBlockInput)
            .addSlot(bridgePillarBlockInput)
            .addSlot(tunnelWallBlockInput)
            .addSlot(tunnelCeilingBlockInput)
            .addSlot(tunnelFloorBlockInput);
    }

    @Override
    public void close() {
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
