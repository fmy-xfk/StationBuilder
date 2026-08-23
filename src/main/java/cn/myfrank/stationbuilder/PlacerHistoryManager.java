package cn.myfrank.stationbuilder;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class PlacerHistoryManager {
    private static final Map<UUID, LinkedList<UndoStep>> HISTORIES = new HashMap<>();
    private static final int MAX_STEPS = 10;

    public static class SavedBlockState {
        public final BlockState state;
        public final NbtCompound nbt;

        public SavedBlockState(BlockState state, NbtCompound nbt) {
            this.state = state;
            this.nbt = nbt;
        }
    }

    public static class UndoStep {
        public final RegistryKey<World> worldKey;
        public final Map<BlockPos, SavedBlockState> savedBlocks;

        public UndoStep(RegistryKey<World> worldKey, Map<BlockPos, SavedBlockState> savedBlocks) {
            this.worldKey = worldKey;
            this.savedBlocks = savedBlocks;
        }
    }

    public static void push(ServerPlayerEntity player, ServerWorld world, Map<BlockPos, SavedBlockState> savedBlocks) {
        LinkedList<UndoStep> steps = HISTORIES.computeIfAbsent(player.getUuid(), k -> new LinkedList<>());
        steps.addFirst(new UndoStep(world.getRegistryKey(), savedBlocks));
        if (steps.size() > MAX_STEPS) {
            steps.removeLast();
        }
    }

    public static boolean undo(ServerPlayerEntity player) {
        LinkedList<UndoStep> steps = HISTORIES.get(player.getUuid());
        if (steps == null || steps.isEmpty()) {
            return false;
        }
        UndoStep step = steps.removeFirst();
        ServerWorld world = player.getServer().getWorld(step.worldKey);
        if (world == null) return false;

        for (Map.Entry<BlockPos, SavedBlockState> entry : step.savedBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            SavedBlockState saved = entry.getValue();

            world.setBlockState(pos, saved.state, 3);
            if (saved.nbt != null) {
                var be = world.getBlockEntity(pos);
                if (be != null) {
                    be.readNbt(saved.nbt);
                    be.markDirty();
                }
            }
        }
        return true;
    }
}