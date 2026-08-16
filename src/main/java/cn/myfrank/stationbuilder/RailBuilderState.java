package cn.myfrank.stationbuilder;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.ListTag;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public class RailBuilderState {
    private static final String KEY = "railBuilderState";
    private static final String LAST_NODES = "lastNodes";
    private static final String LAST_NODES_ANGLE = "lastNodesAngle";

    public static boolean isBuilding(ItemStack stack) {
        return getLastNodesAndAngle(stack) != null;
    }

    @Nullable
    public static Pair<ArrayList<BlockPos>, Float> getLastNodesAndAngle(ItemStack stack) {
        if (!stack.hasTag()) return null;
        CompoundTag nbt = stack.getTag();
        if (nbt == null || !nbt.contains(KEY)) return null;
        CompoundTag state = nbt.getCompound(KEY);
        if (!state.contains(LAST_NODES)) return null;
        ListTag list = state.getList(LAST_NODES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return null;
        ArrayList<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pos = list.getCompound(i);
            result.add(new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z")));
        }
        return Pair.of(result, state.getFloat(LAST_NODES_ANGLE));
    }

    public static void setLastNodesAndAngle(ItemStack stack, @Nullable ArrayList<BlockPos> nodes, float angle) {
        CompoundTag nbt = stack.getOrCreateTag();
        CompoundTag state = new CompoundTag();
        ListTag list = new ListTag();
        if (nodes != null) {
            for (BlockPos p : nodes) {
                CompoundTag pos = new CompoundTag();
                pos.putInt("x", p.getX()); pos.putInt("y", p.getY()); pos.putInt("z", p.getZ());
                list.add(pos);
            }
        }
        state.put(LAST_NODES, list);
        state.putFloat(LAST_NODES_ANGLE, angle);
        nbt.put(KEY, state);
    }

    public static void clear(ItemStack stack) {
        if (!stack.hasTag()) return;
        CompoundTag nbt = stack.getTag();
        if (nbt != null) nbt.remove(KEY);
    }
}
