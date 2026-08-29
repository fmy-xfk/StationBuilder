package cn.myfrank.stationbuilder;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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
        CustomData component = stack.get(ModComponents.RAIL_BUILDER_DATA.get());
        if (component == null) return null;

        CompoundTag root = component.copyTag();
        if (!root.contains(KEY, Tag.TAG_COMPOUND)) return null;

        CompoundTag state = root.getCompound(KEY);
        if (!state.contains(LAST_NODES, Tag.TAG_LIST)) return null;

        ListTag list = state.getList(LAST_NODES, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return null;

        ArrayList<BlockPos> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pos = list.getCompound(i);
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
        CustomData component = stack.get(ModComponents.RAIL_BUILDER_DATA.get());
        CompoundTag root = component == null ? new CompoundTag() : component.copyTag();

        if (nodes == null || nodes.isEmpty()) {
            root.remove(KEY);
        } else {
            CompoundTag state = new CompoundTag();

            ListTag list = new ListTag();
            for (BlockPos p : nodes) {
                CompoundTag pos = new CompoundTag();
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
            stack.remove(ModComponents.RAIL_BUILDER_DATA.get());
        } else {
            stack.set(ModComponents.RAIL_BUILDER_DATA.get(), CustomData.of(root));
        }
    }

    public static void clear(ItemStack stack) {
        CustomData component = stack.get(ModComponents.RAIL_BUILDER_DATA.get());
        if (component == null) return;

        CompoundTag root = component.copyTag();
        root.remove(KEY);

        if (root.isEmpty()) {
            stack.remove(ModComponents.RAIL_BUILDER_DATA.get());
        } else {
            stack.set(ModComponents.RAIL_BUILDER_DATA.get(), CustomData.of(root));
        }
    }
}
