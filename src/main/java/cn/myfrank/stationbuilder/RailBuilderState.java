package cn.myfrank.stationbuilder;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;
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
        NbtComponent component = stack.get(ModComponents.RAIL_BUILDER_DATA);
        if (component == null) return null;

        NbtCompound root = component.copyNbt();
        if (!root.contains(KEY, NbtElement.COMPOUND_TYPE)) return null;

        NbtCompound state = root.getCompound(KEY);
        if (!state.contains(LAST_NODES, NbtElement.LIST_TYPE)) return null;

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

        float angle = state.getFloat(LAST_NODES_ANGLE);
        return Pair.of(result, angle);
    }

    public static void setLastNodesAndAngle(ItemStack stack,
                                            @Nullable ArrayList<BlockPos> nodes,
                                            float angle) {
        NbtComponent component = stack.get(ModComponents.RAIL_BUILDER_DATA);
        NbtCompound root = component == null ? new NbtCompound() : component.copyNbt();

        if (nodes == null || nodes.isEmpty()) {
            root.remove(KEY);
        } else {
            NbtCompound state = new NbtCompound();

            NbtList list = new NbtList();
            for (BlockPos p : nodes) {
                NbtCompound pos = new NbtCompound();
                pos.putInt("x", p.getX());
                pos.putInt("y", p.getY());
                pos.putInt("z", p.getZ());
                list.add(pos);
            }

            state.put(LAST_NODES, list);
            state.putFloat(LAST_NODES_ANGLE, angle);
            root.put(KEY, state);
        }

        if (root.isEmpty()) {
            stack.remove(ModComponents.RAIL_BUILDER_DATA);
        } else {
            stack.set(ModComponents.RAIL_BUILDER_DATA, NbtComponent.of(root));
        }
    }

    public static void clear(ItemStack stack) {
        NbtComponent component = stack.get(ModComponents.RAIL_BUILDER_DATA);
        if (component == null) return;

        NbtCompound root = component.copyNbt();
        root.remove(KEY);

        if (root.isEmpty()) {
            stack.remove(ModComponents.RAIL_BUILDER_DATA);
        } else {
            stack.set(ModComponents.RAIL_BUILDER_DATA, NbtComponent.of(root));
        }
    }
}