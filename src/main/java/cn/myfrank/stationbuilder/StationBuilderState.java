package cn.myfrank.stationbuilder;

import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;

public final class StationBuilderState {
    private StationBuilderState() {}

    public static boolean hasData(ItemStack stack) {
        return stack.contains(ModComponents.STATION_BUILDER_DATA);
    }

    public static void saveFromBlockEntity(
            ItemStack stack,
            StationBuilderBlockEntity be,
            RegistryWrapper.WrapperLookup registries
    ) {
        NbtCompound nbt = new NbtCompound();
        be.writeNbt(nbt, registries);

        // 保持和你旧版 BlockEntityTag 一样：不保存坐标/id
        nbt.remove("x");
        nbt.remove("y");
        nbt.remove("z");
        nbt.remove("id");

        stack.set(ModComponents.STATION_BUILDER_DATA, NbtComponent.of(nbt));
    }

    public static void loadToBlockEntity(
            ItemStack stack,
            StationBuilderBlockEntity be,
            RegistryWrapper.WrapperLookup registries
    ) {
        NbtComponent component = stack.get(ModComponents.STATION_BUILDER_DATA);
        if (component == null) return;

        be.readNbt(component.copyNbt(), registries);
        be.markDirty();
    }

    public static void clear(ItemStack stack) {
        stack.remove(ModComponents.STATION_BUILDER_DATA);
    }
}