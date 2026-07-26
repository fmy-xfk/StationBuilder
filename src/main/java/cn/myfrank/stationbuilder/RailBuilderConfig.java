package cn.myfrank.stationbuilder;

import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;

public class RailBuilderConfig {
    public int railCount = 2;
    public double railSpacing = 4.5;
    public double ballastTopWidth = 5.0;
    public double ballastBottomWidth = 11.0;
    public int ballastMaxThickness = 4;
    public Identifier ballastBlock = Identifier.of("minecraft", "andesite");
    public Identifier railType = StationBuilder.isMtrLoaded() ?
            Identifier.of("mtr", "rail_connector_160") :
            Identifier.of("minecraft", "rail");

    public int bridgeClearSpan = 50;
    public Identifier bridgeGuardRailBlock = Identifier.of("minecraft", "stone_brick_wall");
    public Identifier bridgeBlock = Identifier.of("minecraft", "smooth_stone");
    public Identifier bridgePillarBlock = Identifier.of("minecraft", "light_gray_concrete");
    public double bridgeWidth = 7.0;

    public int tunnelHeight = 7;
    public Identifier tunnelWallBlock = Identifier.of("minecraft", "stone");
    public Identifier tunnelCeilingBlock = Identifier.of("minecraft", "light_gray_concrete");
    public Identifier tunnelFloorBlock = Identifier.of("minecraft", "andesite");
    public double tunnelWidth = 7.0;
    public boolean clearFullHeight = false;

    public boolean useCatenary = true;
    public boolean isVanillaCatenary = !StationBuilder.isMsdLoaded();
    public int catenaryModeIndex = 0;
    public int catenarySpacing = 50;
    public Identifier catenaryBlock = StationBuilder.isMsdLoaded() ?
            Identifier.of("msd", "catenary_connector") :
            Identifier.of("minecraft", "cobweb");
    public Identifier catenaryBridgePillar = StationBuilder.isMsdLoaded() ?
            Identifier.of("msd", "catenary_with_long") :
            Identifier.of("minecraft", "stone_brick_wall");
    public Identifier catenaryTunnelPillar = StationBuilder.isMsdLoaded() ?
            Identifier.of("msd", "catenary_with_long_top") :
            Identifier.of("minecraft", "stone_brick_wall");

    public static RailBuilderConfig fromItem(ItemStack stack) {
        RailBuilderConfig cfg = new RailBuilderConfig();

        NbtCompound root = stack.getOrDefault(ModComponents.RAIL_BUILDER_DATA, NbtComponent.DEFAULT).copyNbt();
        if (root.contains("railBuilderConfig", NbtElement.COMPOUND_TYPE)) {
            cfg.fromNbt(root.getCompound("railBuilderConfig"));
        }

        return cfg;
    }

    public void saveToItem(ItemStack stack) {
        NbtCompound root = stack.getOrDefault(ModComponents.RAIL_BUILDER_DATA, NbtComponent.DEFAULT).copyNbt();
        root.put("railBuilderConfig", toNbt());
        stack.set(ModComponents.RAIL_BUILDER_DATA, NbtComponent.of(root));
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();

        nbt.putInt("railCount", railCount);
        nbt.putDouble("railSpacing", railSpacing);
        nbt.putDouble("ballastTopWidth", ballastTopWidth);
        nbt.putDouble("ballastBottomWidth", ballastBottomWidth);
        nbt.putInt("ballastMaxThickness", ballastMaxThickness);
        nbt.putString("ballastBlock", ballastBlock.toString());
        nbt.putString("railType", railType.toString());

        nbt.putInt("bridgeClearSpan", bridgeClearSpan);
        nbt.putString("bridgeGuardRailBlock", bridgeGuardRailBlock.toString());
        nbt.putString("bridgeBlock", bridgeBlock.toString());
        nbt.putString("bridgePillarBlock", bridgePillarBlock.toString());
        nbt.putDouble("bridgeWidth", bridgeWidth);

        nbt.putInt("tunnelHeight", tunnelHeight);
        nbt.putString("tunnelWallBlock", tunnelWallBlock.toString());
        nbt.putString("tunnelCeilingBlock", tunnelCeilingBlock.toString());
        nbt.putString("tunnelFloorBlock", tunnelFloorBlock.toString());
        nbt.putDouble("tunnelWidth", tunnelWidth);
        nbt.putBoolean("clearFullHeight", clearFullHeight);

        nbt.putBoolean("useCatenary", useCatenary);
        nbt.putBoolean("isVanillaCatenary", isVanillaCatenary);
        nbt.putInt("catenaryModeIndex", catenaryModeIndex);
        nbt.putInt("catenarySpacing", catenarySpacing);
        nbt.putString("catenaryBlock", catenaryBlock.toString());
        nbt.putString("catenaryBridgePillar", catenaryBridgePillar.toString());
        nbt.putString("catenaryTunnelPillar", catenaryTunnelPillar.toString());

        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("railCount", NbtElement.INT_TYPE))
            railCount = nbt.getInt("railCount");

        if (nbt.contains("railSpacing", NbtElement.DOUBLE_TYPE))
            railSpacing = nbt.getDouble("railSpacing");

        if (nbt.contains("railType", NbtElement.STRING_TYPE))
            railType = Identifier.of(nbt.getString("railType"));

        if (nbt.contains("ballastTopWidth", NbtElement.DOUBLE_TYPE))
            ballastTopWidth = nbt.getDouble("ballastTopWidth");

        if (nbt.contains("ballastBottomWidth", NbtElement.DOUBLE_TYPE))
            ballastBottomWidth = nbt.getDouble("ballastBottomWidth");

        if (nbt.contains("ballastMaxThickness", NbtElement.INT_TYPE))
            ballastMaxThickness = nbt.getInt("ballastMaxThickness");

        if (nbt.contains("ballastBlock", NbtElement.STRING_TYPE))
            ballastBlock = Identifier.of(nbt.getString("ballastBlock"));

        if (nbt.contains("bridgeClearSpan", NbtElement.INT_TYPE))
            bridgeClearSpan = nbt.getInt("bridgeClearSpan");

        if (nbt.contains("bridgeGuardRailBlock", NbtElement.STRING_TYPE))
            bridgeGuardRailBlock = Identifier.of(nbt.getString("bridgeGuardRailBlock"));

        if (nbt.contains("bridgeBlock", NbtElement.STRING_TYPE))
            bridgeBlock = Identifier.of(nbt.getString("bridgeBlock"));

        if (nbt.contains("bridgePillarBlock", NbtElement.STRING_TYPE))
            bridgePillarBlock = Identifier.of(nbt.getString("bridgePillarBlock"));

        if (nbt.contains("bridgeWidth", NbtElement.DOUBLE_TYPE))
            bridgeWidth = nbt.getDouble("bridgeWidth");

        if (nbt.contains("tunnelHeight", NbtElement.INT_TYPE))
            tunnelHeight = nbt.getInt("tunnelHeight");

        if (nbt.contains("tunnelWallBlock", NbtElement.STRING_TYPE))
            tunnelWallBlock = Identifier.of(nbt.getString("tunnelWallBlock"));

        if (nbt.contains("tunnelCeilingBlock", NbtElement.STRING_TYPE))
            tunnelCeilingBlock = Identifier.of(nbt.getString("tunnelCeilingBlock"));

        if (nbt.contains("tunnelFloorBlock", NbtElement.STRING_TYPE))
            tunnelFloorBlock = Identifier.of(nbt.getString("tunnelFloorBlock"));

        if (nbt.contains("tunnelWidth", NbtElement.DOUBLE_TYPE))
            tunnelWidth = nbt.getDouble("tunnelWidth");

        if (nbt.contains("clearFullHeight"))
            clearFullHeight = nbt.getBoolean("clearFullHeight");

        if (nbt.contains("useCatenary", NbtElement.BYTE_TYPE))
            useCatenary = nbt.getBoolean("useCatenary");

        if (nbt.contains("isVanillaCatenary", NbtElement.BYTE_TYPE))
            isVanillaCatenary = nbt.getBoolean("isVanillaCatenary");

        if (nbt.contains("catenaryModeIndex", NbtElement.INT_TYPE))
            catenaryModeIndex = nbt.getInt("catenaryModeIndex");

        if (nbt.contains("catenarySpacing", NbtElement.INT_TYPE))
            catenarySpacing = nbt.getInt("catenarySpacing");

        if (nbt.contains("catenaryBlock", NbtElement.STRING_TYPE))
            catenaryBlock = Identifier.of(nbt.getString("catenaryBlock"));

        if (nbt.contains("catenaryBridgePillar", NbtElement.STRING_TYPE))
            catenaryBridgePillar = Identifier.of(nbt.getString("catenaryBridgePillar"));

        if (nbt.contains("catenaryTunnelPillar", NbtElement.STRING_TYPE))
            catenaryTunnelPillar = Identifier.of(nbt.getString("catenaryTunnelPillar"));
    }
}