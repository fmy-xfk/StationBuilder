package cn.myfrank.stationbuilder;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.*;

public class PlacerHistoryManager {
    private static final Map<UUID, LinkedList<UndoStep>> HISTORIES = new HashMap<>();
    private static final int MAX_STEPS = 10;

    public static class SavedBlockState {
        public final BlockState state;
        public final CompoundTag nbt;

        public SavedBlockState(BlockState state, CompoundTag nbt) {
            this.state = state;
            this.nbt = nbt;
        }
    }

    public static class UndoStep {
        public final ResourceKey<Level> worldKey;
        public final Map<BlockPos, SavedBlockState> savedBlocks;

        public UndoStep(ResourceKey<Level> worldKey, Map<BlockPos, SavedBlockState> savedBlocks) {
            this.worldKey = worldKey;
            this.savedBlocks = savedBlocks;
        }
    }

    public static void push(ServerPlayer player, ServerLevel world, Map<BlockPos, SavedBlockState> savedBlocks) {
        LinkedList<UndoStep> steps = HISTORIES.computeIfAbsent(player.getUUID(), k -> new LinkedList<>());
        steps.addFirst(new UndoStep(world.dimension(), savedBlocks));
        if (steps.size() > MAX_STEPS) {
            steps.removeLast();
        }
    }

    public static boolean undo(ServerPlayer player) {
        LinkedList<UndoStep> steps = HISTORIES.get(player.getUUID());
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        UndoStep step = steps.removeFirst();
        ServerLevel world = player.getServer().getLevel(step.worldKey);
        if (world == null) return false;

        for (Map.Entry<BlockPos, SavedBlockState> entry : step.savedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            SavedBlockState saved = entry.getValue();

            world.setBlock(pos, saved.state, 3);
            if (saved.nbt != null) {
                var be = world.getBlockEntity(pos);
                if (be != null) {
                    be.load(saved.nbt);
                    be.setChanged();
                }
            }
        }
        return true;
    }
}
