package cn.myfrank.stationbuilder;

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
    public Identifier ballastBlock = new Identifier("minecraft", "andesite");
    public Identifier railType = StationBuilder.isMtrLoaded() ?
            new Identifier("mtr", "rail_connector_160") :
            new Identifier("minecraft", "rail");

    public int bridgeClearSpan = 50;
    public Identifier bridgeGuardRailBlock = new Identifier("minecraft", "stone_brick_wall");
    public Identifier bridgeBlock = new Identifier("minecraft", "smooth_stone");
    public Identifier bridgePillarBlock = new Identifier("minecraft", "light_gray_concrete");

    public int tunnelHeight = 7;
    public Identifier tunnelWallBlock = new Identifier("minecraft", "stone");
    public Identifier tunnelCeilingBlock = new Identifier("minecraft", "light_gray_concrete");
    public Identifier tunnelFloorBlock = new Identifier("minecraft", "andesite");

    public boolean useCatenary = StationBuilder.isMsdLoaded();
    public int catenarySpacing = 50;

    // ===== NBT =====
    public static RailBuilderConfig fromItem(ItemStack stack) {
        RailBuilderConfig cfg = new RailBuilderConfig();
        if (stack.hasNbt() && stack.getNbt().contains("railBuilderConfig")) {
            cfg.fromNbt(stack.getNbt().getCompound("railBuilderConfig"));
        }
        return cfg;
    }

    public void saveToItem(ItemStack stack) {
        stack.getOrCreateNbt().put("railBuilderConfig", toNbt());
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

        nbt.putInt("tunnelHeight", tunnelHeight);
        nbt.putString("tunnelWallBlock", tunnelWallBlock.toString());
        nbt.putString("tunnelCeilingBlock", tunnelCeilingBlock.toString());
        nbt.putString("tunnelFloorBlock", tunnelFloorBlock.toString());

        nbt.putBoolean("useCatenary", useCatenary);
        nbt.putInt("catenarySpacing", catenarySpacing);

        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("railCount", NbtElement.INT_TYPE))
            railCount = nbt.getInt("railCount");

        if (nbt.contains("railSpacing", NbtElement.DOUBLE_TYPE))
            railSpacing = nbt.getDouble("railSpacing");

        if (nbt.contains("ballastTopWidth", NbtElement.DOUBLE_TYPE))
            ballastTopWidth = nbt.getDouble("ballastTopWidth");

        if (nbt.contains("ballastBottomWidth", NbtElement.DOUBLE_TYPE))
            ballastTopWidth = nbt.getDouble("ballastBottomWidth");

        if (nbt.contains("ballastMaxThickness", NbtElement.INT_TYPE))
            ballastTopWidth = nbt.getInt("ballastMaxThickness");

        if (nbt.contains("ballastBlock", NbtElement.STRING_TYPE))
            ballastBlock = new Identifier(nbt.getString("ballastBlock"));

        if (nbt.contains("railType", NbtElement.STRING_TYPE))
            railType = new Identifier(nbt.getString("railType"));

        if (nbt.contains("bridgeClearSpan", NbtElement.INT_TYPE))
            bridgeClearSpan = nbt.getInt("bridgeClearSpan");

        if (nbt.contains("bridgeGuardRailBlock", NbtElement.STRING_TYPE))
            bridgeGuardRailBlock = new Identifier(nbt.getString("bridgeGuardRailBlock"));

        if (nbt.contains("bridgeBlock", NbtElement.STRING_TYPE))
            bridgeBlock = new Identifier(nbt.getString("bridgeBlock"));

        if (nbt.contains("bridgePillarBlock", NbtElement.STRING_TYPE))
            bridgePillarBlock = new Identifier(nbt.getString("bridgePillarBlock"));

        if (nbt.contains("tunnelHeight", NbtElement.INT_TYPE))
            tunnelHeight = nbt.getInt("tunnelHeight");

        if (nbt.contains("tunnelWallBlock", NbtElement.STRING_TYPE))
            tunnelWallBlock = new Identifier(nbt.getString("tunnelWallBlock"));

        if (nbt.contains("tunnelCeilingBlock", NbtElement.STRING_TYPE))
            tunnelCeilingBlock = new Identifier(nbt.getString("tunnelCeilingBlock"));

        if (nbt.contains("tunnelFloorBlock", NbtElement.STRING_TYPE))
            tunnelFloorBlock = new Identifier(nbt.getString("tunnelFloorBlock"));

        useCatenary = nbt.getBoolean("useCatenary");

        if (nbt.contains("catenarySpacing", NbtElement.INT_TYPE))
            catenarySpacing = nbt.getInt("catenarySpacing");
    }
}
