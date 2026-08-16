package cn.myfrank.stationbuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public class RailBuilderConfig {
    public int railCount = 2;
    public double railSpacing = 4.5;
    public double ballastTopWidth = 5.0;
    public double ballastBottomWidth = 11.0;
    public int ballastMaxThickness = 4;
    public ResourceLocation ballastBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "andesite");
    public ResourceLocation railType = StationBuilder.isMtrLoaded() ? ResourceLocation.fromNamespaceAndPath("mtr", "rail_connector_160") : ResourceLocation.fromNamespaceAndPath("minecraft", "rail");
    public int bridgeClearSpan = 50;
    public ResourceLocation bridgeGuardRailBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "stone_brick_wall");
    public ResourceLocation bridgeBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "smooth_stone");
    public ResourceLocation bridgePillarBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "light_gray_concrete");
    public double bridgeWidth = 7.0;
    public int tunnelHeight = 7;
    public ResourceLocation tunnelWallBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "stone");
    public ResourceLocation tunnelCeilingBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "light_gray_concrete");
    public ResourceLocation tunnelFloorBlock = ResourceLocation.fromNamespaceAndPath("minecraft", "andesite");
    public double tunnelWidth = 7.0;
    public boolean clearFullHeight = false;
    public boolean useCatenary = true;
    public boolean isVanillaCatenary = !StationBuilder.isMsdLoaded();
    public int catenaryModeIndex = 0;
    public int catenarySpacing = 50;
    public ResourceLocation catenaryBlock = StationBuilder.isMsdLoaded() ? ResourceLocation.fromNamespaceAndPath("msd", "catenary_connector") : ResourceLocation.fromNamespaceAndPath("minecraft", "cobweb");
    public ResourceLocation catenaryBridgePillar = StationBuilder.isMsdLoaded() ? ResourceLocation.fromNamespaceAndPath("msd", "catenary_with_long") : ResourceLocation.fromNamespaceAndPath("minecraft", "stone_brick_wall");
    public ResourceLocation catenaryTunnelPillar = StationBuilder.isMsdLoaded() ? ResourceLocation.fromNamespaceAndPath("msd", "catenary_with_long_top") : ResourceLocation.fromNamespaceAndPath("minecraft", "stone_brick_wall");

    public static RailBuilderConfig fromItem(ItemStack stack) {
        RailBuilderConfig cfg = new RailBuilderConfig();
        if (stack.hasTag() && stack.getTag().contains("railBuilderConfig")) cfg.fromNbt(stack.getTag().getCompound("railBuilderConfig"));
        return cfg;
    }
    public void saveToItem(ItemStack stack) { stack.getOrCreateTag().put("railBuilderConfig", toNbt()); }
    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putInt("railCount", railCount); nbt.putDouble("railSpacing", railSpacing);
        nbt.putDouble("ballastTopWidth", ballastTopWidth); nbt.putDouble("ballastBottomWidth", ballastBottomWidth); nbt.putInt("ballastMaxThickness", ballastMaxThickness);
        nbt.putString("ballastBlock", ballastBlock.toString()); nbt.putString("railType", railType.toString());
        nbt.putInt("bridgeClearSpan", bridgeClearSpan); nbt.putString("bridgeGuardRailBlock", bridgeGuardRailBlock.toString());
        nbt.putString("bridgeBlock", bridgeBlock.toString()); nbt.putString("bridgePillarBlock", bridgePillarBlock.toString()); nbt.putDouble("bridgeWidth", bridgeWidth);
        nbt.putInt("tunnelHeight", tunnelHeight); nbt.putString("tunnelWallBlock", tunnelWallBlock.toString()); nbt.putString("tunnelCeilingBlock", tunnelCeilingBlock.toString()); nbt.putString("tunnelFloorBlock", tunnelFloorBlock.toString()); nbt.putDouble("tunnelWidth", tunnelWidth);
        nbt.putBoolean("clearFullHeight", clearFullHeight); nbt.putBoolean("useCatenary", useCatenary); nbt.putBoolean("isVanillaCatenary", isVanillaCatenary);
        nbt.putInt("catenaryModeIndex", catenaryModeIndex); nbt.putInt("catenarySpacing", catenarySpacing); nbt.putString("catenaryBlock", catenaryBlock.toString());
        nbt.putString("catenaryBridgePillar", catenaryBridgePillar.toString()); nbt.putString("catenaryTunnelPillar", catenaryTunnelPillar.toString());
        return nbt;
    }
    public void fromNbt(CompoundTag nbt) {
        if (nbt.contains("railCount", Tag.TAG_INT)) railCount = nbt.getInt("railCount");
        if (nbt.contains("railSpacing", Tag.TAG_DOUBLE)) railSpacing = nbt.getDouble("railSpacing");
        if (nbt.contains("railType", Tag.TAG_STRING)) railType = ResourceLocation.parse(nbt.getString("railType"));
        if (nbt.contains("ballastTopWidth", Tag.TAG_DOUBLE)) ballastTopWidth = nbt.getDouble("ballastTopWidth");
        if (nbt.contains("ballastBottomWidth", Tag.TAG_DOUBLE)) ballastBottomWidth = nbt.getDouble("ballastBottomWidth");
        if (nbt.contains("ballastMaxThickness", Tag.TAG_INT)) ballastMaxThickness = nbt.getInt("ballastMaxThickness");
        if (nbt.contains("ballastBlock", Tag.TAG_STRING)) ballastBlock = ResourceLocation.parse(nbt.getString("ballastBlock"));
        if (nbt.contains("bridgeClearSpan", Tag.TAG_INT)) bridgeClearSpan = nbt.getInt("bridgeClearSpan");
        if (nbt.contains("bridgeGuardRailBlock", Tag.TAG_STRING)) bridgeGuardRailBlock = ResourceLocation.parse(nbt.getString("bridgeGuardRailBlock"));
        if (nbt.contains("bridgeBlock", Tag.TAG_STRING)) bridgeBlock = ResourceLocation.parse(nbt.getString("bridgeBlock"));
        if (nbt.contains("bridgePillarBlock", Tag.TAG_STRING)) bridgePillarBlock = ResourceLocation.parse(nbt.getString("bridgePillarBlock"));
        if (nbt.contains("bridgeWidth", Tag.TAG_DOUBLE)) bridgeWidth = nbt.getDouble("bridgeWidth");
        if (nbt.contains("tunnelHeight", Tag.TAG_INT)) tunnelHeight = nbt.getInt("tunnelHeight");
        if (nbt.contains("tunnelWallBlock", Tag.TAG_STRING)) tunnelWallBlock = ResourceLocation.parse(nbt.getString("tunnelWallBlock"));
        if (nbt.contains("tunnelCeilingBlock", Tag.TAG_STRING)) tunnelCeilingBlock = ResourceLocation.parse(nbt.getString("tunnelCeilingBlock"));
        if (nbt.contains("tunnelFloorBlock", Tag.TAG_STRING)) tunnelFloorBlock = ResourceLocation.parse(nbt.getString("tunnelFloorBlock"));
        if (nbt.contains("tunnelWidth", Tag.TAG_DOUBLE)) tunnelWidth = nbt.getDouble("tunnelWidth");
        if (nbt.contains("clearFullHeight")) clearFullHeight = nbt.getBoolean("clearFullHeight");
        if (nbt.contains("useCatenary")) useCatenary = nbt.getBoolean("useCatenary");
        if (nbt.contains("isVanillaCatenary")) isVanillaCatenary = nbt.getBoolean("isVanillaCatenary");
        if (nbt.contains("catenaryModeIndex", Tag.TAG_INT)) catenaryModeIndex = nbt.getInt("catenaryModeIndex");
        if (nbt.contains("catenarySpacing", Tag.TAG_INT)) catenarySpacing = nbt.getInt("catenarySpacing");
        if (nbt.contains("catenaryBlock", Tag.TAG_STRING)) catenaryBlock = ResourceLocation.parse(nbt.getString("catenaryBlock"));
        if (nbt.contains("catenaryBridgePillar", Tag.TAG_STRING)) catenaryBridgePillar = ResourceLocation.parse(nbt.getString("catenaryBridgePillar"));
        if (nbt.contains("catenaryTunnelPillar", Tag.TAG_STRING)) catenaryTunnelPillar = ResourceLocation.parse(nbt.getString("catenaryTunnelPillar"));
    }
}
