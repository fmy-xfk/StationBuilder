package cn.myfrank.stationbuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class StationBuilderState {
    private StationBuilderState() {}

    public static boolean hasData(ItemStack stack) {
        return stack.has(ModComponents.STATION_BUILDER_DATA.get());
    }

    public static void saveFromBlockEntity(
            ItemStack stack,
            StationBuilderBlockEntity be,
            HolderLookup.Provider registries
    ) {
        CompoundTag nbt = be.saveWithoutMetadata(registries);

        // 保持和你旧版 BlockEntityTag 一样：不保存坐标/id
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");
        nbt.remove("id");

        stack.set(ModComponents.STATION_BUILDER_DATA.get(), CustomData.of(nbt));
    }

    public static void loadToBlockEntity(
            ItemStack stack,
            StationBuilderBlockEntity be,
            HolderLookup.Provider registries
    ) {
        CustomData component = stack.get(ModComponents.STATION_BUILDER_DATA.get());
        if (component == null) return;

        be.loadData(component.getUnsafe(), registries);
    }

    public static void clear(ItemStack stack) {
        stack.remove(ModComponents.STATION_BUILDER_DATA.get());
    }
}
