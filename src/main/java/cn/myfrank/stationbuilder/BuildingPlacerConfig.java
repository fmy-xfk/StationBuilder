package cn.myfrank.stationbuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Rotation;

public class BuildingPlacerConfig {
    public String presetName = "matchbox";
    public Rotation rotation = Rotation.NONE;
    public boolean placeAir = false;

    public static BuildingPlacerConfig fromItem(ItemStack stack) {
        BuildingPlacerConfig cfg = new BuildingPlacerConfig();
        CustomData component = stack.get(ModComponents.PLACER_DATA.get());
        if (component != null) {
            cfg.fromNbt(component.copyTag());
        }
        return cfg;
    }

    public void saveToItem(ItemStack stack) {
        stack.set(ModComponents.PLACER_DATA.get(), CustomData.of(toNbt()));
    }

    public CompoundTag toNbt() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("presetName", presetName);
        nbt.putString("rotation", rotation.name());
        nbt.putBoolean("placeAir", placeAir);
        return nbt;
    }

    public void fromNbt(CompoundTag nbt) {
        if (nbt.contains("presetName")) {
            presetName = nbt.getString("presetName");
        }
        if (nbt.contains("rotation")) {
            rotation = Rotation.valueOf(nbt.getString("rotation"));
        }
        if (nbt.contains("placeAir")) {
            placeAir = nbt.getBoolean("placeAir");
        }
    }
}
