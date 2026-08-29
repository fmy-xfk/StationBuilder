package cn.myfrank.stationbuilder;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RailGenerator {
    public static void placeFirstRailNode(ServerLevel world, BlockPos pos, Player player) {
        if (StationBuilder.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYRot());
            } else {
                System.out.println("Position is already a rail node: " + pos);
            }
        }
    }

    public static ArrayList<BlockPos> calcRailNodes(BlockPos pos, float yaw, RailBuilderConfig config) {
        final ArrayList<BlockPos> placedPositions = new ArrayList<>();
        if (StationBuilder.isMtrLoaded()){
            Vec3 normal = RailMath.normalFromYaw(yaw);

            int count = config.railCount;
            double spacing = config.railSpacing;

            for (int i = 0; i < count; i++) {
                double offsetIndex = i - (count - 1) / 2.0;
                double t = offsetIndex * spacing;
                Vec3 offset = normal.multiply(t, t, t);
                BlockPos s = RailMath.offsetPos(pos, offset);
                placedPositions.add(s);
            }
            return placedPositions;
        }
        return null;
    }

    public static ArrayList<BlockPos> placeFirstRailNodes(ServerLevel world, BlockPos pos, Player player, RailBuilderConfig config) {
        ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYRot(), config);
        if (nodes != null) {
            for (BlockPos p : nodes) {
                placeFirstRailNode(world, p, player);
            }
        }
        return nodes;
    }

    @Nullable
    public static ArrayList<BlockPos> buildRails(
            ServerLevel world,
            ArrayList<BlockPos> startPositions,
            BlockPos endPos,
            RailBuilderConfig config,
            Player player
    ) {
        if (!StationBuilder.isMtrLoaded()) return null;

        float yaw = player.getYRot();
        Vec3 normal = RailMath.normalFromYaw(yaw); // 右侧法向量
        int count = config.railCount;
        double spacing = config.railSpacing;

        // Calculate end positions
        ArrayList<BlockPos> endPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            double t = offsetIndex * spacing;
            Vec3 offset = normal.multiply(t, t, t);
            BlockPos e = RailMath.offsetPos(endPos, offset);
            endPositions.add(e);
        }

        // Adjust positions
        RailMath.adjustPointSequence(startPositions, endPositions);

        if (startPositions.size() != endPositions.size()) {
            // 清除该玩家的轨道建造状态（服务端清除）
            ItemStack stack = player.getMainHandItem();
            RailBuilderState.clear(stack);
            return null;
        }

        // Place BlockNodes
        boolean anySuccess = false;
        for (int i = 0; i < count; i++) {
            BlockPos s = startPositions.get(i);
            BlockPos e = endPositions.get(i);
            if(MTRIntegration.isRailNode(world, s)) {
                boolean success = true;
                if (s.equals(e)) continue;
                if (!MTRIntegration.isRailNode(world, e)) {
                    MTRIntegration.placeRailNode(world, e, player.getYRot());
                }
                anySuccess |= success;
            } else {
                System.out.println("Start position is not a valid rail node: " + s);
            }
        }

        // Validate rail type
        if (!MTRIntegration.isValidRailType(config.railType)) {
            player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.invalid_rail",
                    config.railType.toString()), true);
            config.railType = MTRIntegration.getDefaultRailType();
        }

        // Build rails
        TickScheduler.schedule(1, () -> {
            var failToPlaceCatenaryNode = MTRIntegration.buildRails(startPositions, endPositions, player.getUUID(), world, config);
            if (failToPlaceCatenaryNode) {
                player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.catenary_node_failed"), true);
            }
        });

        if(anySuccess) {
            return endPositions;
        } else {
            return null;
        }
    }
}
