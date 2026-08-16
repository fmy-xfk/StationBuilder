package cn.myfrank.stationbuilder;

import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;

public final class StationBuilderState {
    private static final String KEY = "BlockEntityTag";
    private StationBuilderState() {}

    public static boolean hasData(ItemStack stack) {
        return stack.hasTag() && stack.getTag() != null && stack.getTag().contains(KEY);
    }

    public static void saveFromBlockEntity(ItemStack stack, StationBuilderBlockEntity be) {
        CompoundTag nbt = be.saveWithoutMetadata();
        stack.addTagElement(KEY, nbt);
    }

    public static void loadToBlockEntity(ItemStack stack, StationBuilderBlockEntity be) {
        if (!stack.hasTag()) return;

        CompoundTag nbt = stack.getTagElement(KEY);
        if (nbt == null) return;

        be.load(nbt);
        be.setChanged();
    }

    public static void clear(ItemStack stack) {
        // 替换了 removeSubNbt
        stack.removeTagKey(KEY);
    }
}