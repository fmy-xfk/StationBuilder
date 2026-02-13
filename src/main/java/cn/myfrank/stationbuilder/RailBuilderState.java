package cn.myfrank.stationbuilder;

import java.util.ArrayList;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class RailBuilderState {

    private static final String KEY = "railBuilderState";
    private static final String LAST_NODES = "lastNodes";
    private static final String LAST_NODES_ANGLE = "lastNodesAngle";

    public static boolean isBuilding(ItemStack stack) {
        return getLastNodesAndAngle(stack) != null;
    }

    public static Pair<ArrayList<BlockPos>, Float> getLastNodesAndAngle(ItemStack stack) {
        if (!stack.hasNbt()) return null;

        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(KEY)) return null;

        NbtCompound state = nbt.getCompound(KEY);
        if (!state.contains(LAST_NODES)) return null;

        NbtList list = state.getList(LAST_NODES, NbtElement.COMPOUND_TYPE);
        if (list.isEmpty()) return null;

        ArrayList<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            NbtCompound pos = list.getCompound(i);
            result.add(new BlockPos(
                    pos.getInt("x"),
                    pos.getInt("y"),
                    pos.getInt("z")
            ));
        }
        var angle = state.getFloat(LAST_NODES_ANGLE);
        return Pair.of(result, angle);
    }


    public static void setLastNodesAndAngle(ItemStack stack, @Nullable ArrayList<BlockPos> nodes, float angle) {
        NbtCompound nbt = stack.getOrCreateNbt();
        NbtCompound state = nbt.getCompound(KEY);

        NbtList list = new NbtList();
        if (nodes != null) {
            for (BlockPos p : nodes) {
                NbtCompound pos = new NbtCompound();
                pos.putInt("x", p.getX());
                pos.putInt("y", p.getY());
                pos.putInt("z", p.getZ());
                list.add(pos);
            }
        }

        state.put(LAST_NODES, list);
        state.putFloat(LAST_NODES_ANGLE, angle);
        nbt.put(KEY, state);
    }

    public static void clear(ItemStack stack) {
        if (!stack.hasNbt()) return;

        NbtCompound nbt = stack.getNbt();
        if (nbt != null && nbt.contains(KEY)) {
            nbt.remove(KEY);
        }
    }
}
