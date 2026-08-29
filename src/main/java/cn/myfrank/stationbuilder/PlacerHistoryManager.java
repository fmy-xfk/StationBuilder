package cn.myfrank.stationbuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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

    // 压入一次放置前的环境备份
    public static void push(ServerPlayer player, ServerLevel world, Map<BlockPos, SavedBlockState> savedBlocks) {
        LinkedList<UndoStep> steps = HISTORIES.computeIfAbsent(player.getUUID(), k -> new LinkedList<>());
        steps.addFirst(new UndoStep(world.dimension(), savedBlocks));
        if (steps.size() > MAX_STEPS) {
            steps.removeLast(); // 保持最大10步限制
        }
    }

    // 执行回滚还原操作
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

            // 还原 BlockState
            world.setBlock(pos, saved.state, 3);
            // 还原 BlockEntity 的 NBT 状态
            if (saved.nbt != null) {
                var be = world.getBlockEntity(pos);
                if (be != null) {
                    be.loadWithComponents(saved.nbt, world.registryAccess());
                    be.setChanged();
                }
            }
        }
        return true;
    }
}
