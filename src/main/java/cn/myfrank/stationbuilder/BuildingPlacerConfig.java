package cn.myfrank.stationbuilder;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.BlockRotation;

public class BuildingPlacerConfig {
    public String presetName = "matchbox";
    public BlockRotation rotation = BlockRotation.NONE;
    public boolean placeAir = false;

    public static BuildingPlacerConfig fromItem(ItemStack stack) {
        BuildingPlacerConfig cfg = new BuildingPlacerConfig();
        if (stack.hasNbt() && stack.getNbt().contains("placerConfig")) {
            cfg.fromNbt(stack.getNbt().getCompound("placerConfig"));
        }
        return cfg;
    }

    public void saveToItem(ItemStack stack) {
        stack.getOrCreateNbt().put("placerConfig", toNbt());
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("presetName", presetName);
        nbt.putString("rotation", rotation.name());
        nbt.putBoolean("placeAir", placeAir);
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("presetName")) {
            presetName = nbt.getString("presetName");
        }
        if (nbt.contains("rotation")) {
            rotation = BlockRotation.valueOf(nbt.getString("rotation"));
        }
        if (nbt.contains("placeAir")) {
            placeAir = nbt.getBoolean("placeAir");
        }
    }
}