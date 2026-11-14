package cn.myfrank.stationbuilder.manager;

import cn.myfrank.stationbuilder.mtr.MSDIntegration;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Per-player, global undo history shared by every StationBuilder tool.
 *
 * <p>All placements (Building Placer, Station Builder construction and Rail Builder)
 * push their own {@link UndoStep} onto the same stack, so Ctrl+Z always rolls back
 * the most recent action regardless of which tool performed it.</p>
 *
 * <p>Block changes are captured by {@code WorldBlockEditMixin}, which calls
 * {@link #capture(World, BlockPos)} before every {@code World.setBlockState}.
 * Only changes performed while a {@link Recording} is active are recorded.</p>
 */
public class PlacerHistoryManager {
    private static final Map<UUID, LinkedList<UndoStep>> HISTORIES = new HashMap<>();
    private static final int MAX_STEPS = 10;
    private static Recording activeRecording;

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
        public final String tool;

        public UndoStep(RegistryKey<World> worldKey, Map<BlockPos, SavedBlockState> savedBlocks, String tool) {
            this.worldKey = worldKey;
            this.savedBlocks = savedBlocks;
            this.tool = tool;
        }
    }

    /**
     * An in-progress block edit. It can be suspended (for work deferred to a later
     * tick) and resumed later, but must always be finished exactly once.
     */
    public static final class Recording {
        private final UUID playerId;
        private final RegistryKey<World> worldKey;
        private final String tool;
        private final Map<BlockPos, SavedBlockState> savedBlocks = new LinkedHashMap<>();
        private boolean finished;

        private Recording(UUID playerId, RegistryKey<World> worldKey, String tool) {
            this.playerId = playerId;
            this.worldKey = worldKey;
            this.tool = tool;
        }
    }

    /** Starts recording all block edits in {@code world} for {@code player}. */
    public static Recording begin(ServerPlayerEntity player, ServerWorld world, String tool) {
        if (activeRecording != null) {
            // Never lose a recording that was not finished explicitly.
            finish(activeRecording);
        }
        Recording recording = new Recording(player.getUuid(), world.getRegistryKey(), tool);
        activeRecording = recording;
        return recording;
    }

    /** Makes a previously suspended recording active again (used from scheduled tasks). */
    public static void resume(Recording recording) {
        if (recording != null && !recording.finished) {
            activeRecording = recording;
        }
    }

    /** Stops capturing until {@link #resume(Recording)} is called. */
    public static void suspend(Recording recording) {
        if (activeRecording == recording) {
            activeRecording = null;
        }
    }

    /** Ends a recording and pushes its changes onto the player's history. */
    public static void finish(Recording recording) {
        if (recording == null || recording.finished) {
            return;
        }
        recording.finished = true;
        if (activeRecording == recording) {
            activeRecording = null;
        }
        if (recording.savedBlocks.isEmpty()) {
            return;
        }
        LinkedList<UndoStep> steps = HISTORIES.computeIfAbsent(recording.playerId, k -> new LinkedList<>());
        steps.addFirst(new UndoStep(recording.worldKey, recording.savedBlocks, recording.tool));
        if (steps.size() > MAX_STEPS) {
            steps.removeLast(); // keep at most 10 steps
        }
    }

    /**
     * Called from the block-edit mixin before a block is replaced.
     * Stores the original state and block entity data only once per position.
     */
    public static void capture(World world, BlockPos pos) {
        Recording recording = activeRecording;
        if (recording == null || recording.finished) {
            return;
        }
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }
        if (!serverWorld.getRegistryKey().equals(recording.worldKey)) {
            return;
        }
        if (recording.savedBlocks.containsKey(pos)) {
            return;
        }

        BlockState state = serverWorld.getBlockState(pos);
        NbtCompound nbt = null;
        var be = serverWorld.getBlockEntity(pos);
        if (be != null) {
            nbt = be.createNbt();
        }
        recording.savedBlocks.put(pos, new SavedBlockState(state, nbt));
    }

    // Pushes a pre-computed snapshot (used by the Building Placer, which knows its
    // footprint up front). The order on the global stack is the placement order.
    public static void push(ServerPlayerEntity player, ServerWorld world, Map<BlockPos, SavedBlockState> savedBlocks) {
        if (savedBlocks.isEmpty()) {
            return;
        }
        LinkedList<UndoStep> steps = HISTORIES.computeIfAbsent(player.getUuid(), k -> new LinkedList<>());
        steps.addFirst(new UndoStep(world.getRegistryKey(), savedBlocks, "placer"));
        if (steps.size() > MAX_STEPS) {
            steps.removeLast(); // keep at most 10 steps
        }
    }

    // Rolls back the most recent action of this player, whichever tool performed it.
    // Returns the tool that produced the step ("station"/"rail"/"placer"), or null
    // when there is no history left.
    @Nullable
    public static String undo(ServerPlayerEntity player) {
        LinkedList<UndoStep> steps = HISTORIES.get(player.getUuid());
        if (steps == null || steps.isEmpty()) {
            return null;
        }
        UndoStep step = steps.removeFirst();
        ServerWorld world = player.getServer().getWorld(step.worldKey);
        if (world == null) {
            return null;
        }

        // First pass: run mod break hooks while every placed block is still present.
        // Doing this before any restoration matters when the same position was
        // written more than once (e.g. a station block first, then an MTR node).
        for (Map.Entry<BlockPos, SavedBlockState> entry : step.savedBlocks.entrySet()) {
            cleanupPlacedModBlock(world, player, entry.getKey(), entry.getValue().state);
        }

        // Second pass: restore block states and block entity data.
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
        return step.tool;
    }

    /**
     * Invokes the same cleanup hooks as breaking the block with the left mouse
     * button, but only when the block at {@code pos} was placed by the operation
     * (its pre-operation block type differs). Existing rail nodes are kept.
     */
    private static void cleanupPlacedModBlock(
            ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState savedState
    ) {
        BlockState currentState = world.getBlockState(pos);
        if (currentState.getBlock() == savedState.getBlock()) {
            return;
        }

        Identifier id = Registries.BLOCK.getId(currentState.getBlock());
        if (id != null && id.getNamespace().equals("mtr") && id.getPath().contains("rail")) {
            // Mirrors a left-click break; also notifies MTR to delete the rail data.
            currentState.getBlock().onBreak(world, pos, currentState, player);
            return;
        }

        if (CommonUtil.isMsdLoaded() && MSDIntegration.isCatenaryNode(world, pos)) {
            MSDIntegration.clearCatenary(world, pos);
        }
    }
}
