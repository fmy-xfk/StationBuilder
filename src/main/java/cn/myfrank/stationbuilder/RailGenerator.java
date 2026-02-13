package cn.myfrank.stationbuilder;

import java.util.ArrayList;

import org.jetbrains.annotations.Nullable;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class RailGenerator {
    public static void placeFirstRailNode(ServerWorld world, BlockPos pos, PlayerEntity player) {
        if (StationBuilder.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYaw());
            } else {
                System.out.println("Position is already a rail node: " + pos);
            }
        }
    }

    public static ArrayList<BlockPos> calcRailNodes(BlockPos pos, float yaw, RailBuilderConfig config) {
        final ArrayList<BlockPos> placedPositions = new ArrayList<>();
        if (StationBuilder.isMtrLoaded()){
            Vec3d normal = RailMath.normalFromYaw(yaw);

            int count = config.railCount;
            double spacing = config.railSpacing;

            for (int i = 0; i < count; i++) {
                double offsetIndex = i - (count - 1) / 2.0;
                Vec3d offset = normal.multiply(offsetIndex * spacing);
                BlockPos s = RailMath.offsetPos(pos, offset);
                placedPositions.add(s);
            }
            return placedPositions;
        }
        return null;
    }

    public static ArrayList<BlockPos> placeFirstRailNodes(ServerWorld world, BlockPos pos, PlayerEntity player, RailBuilderConfig config) {
        ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYaw(), config);
        if (nodes != null) {
            for (BlockPos p : nodes) {
                placeFirstRailNode(world, p, player);
            }
        }
        return nodes;
    }

    @Nullable
    public static ArrayList<BlockPos> buildRails(
            ServerWorld world,
            ArrayList<BlockPos> startPositions,
            BlockPos endPos,
            RailBuilderConfig config,
            PlayerEntity player
    ) {
        if (!StationBuilder.isMtrLoaded()) return null;

        float yaw = player.getYaw();
        Vec3d normal = RailMath.normalFromYaw(yaw); // 右侧法向量
        int count = config.railCount;
        double spacing = config.railSpacing;

        // Calculate end positions
        ArrayList<BlockPos> endPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            Vec3d offset = normal.multiply(offsetIndex * spacing);
            BlockPos e = RailMath.offsetPos(endPos, offset);
            endPositions.add(e);
        }

        // Adjust positions
        RailMath.adjustPointSequence(startPositions, endPositions);

        // Place BlockNodes
        boolean anySuccess = false;
        for (int i = 0; i < count; i++) {
            BlockPos s = startPositions.get(i);
            BlockPos e = endPositions.get(i);
            if(MTRIntegration.isRailNode(world, s)) {
                boolean success = true;
                if (s.equals(e)) continue;
                if (!MTRIntegration.isRailNode(world, e)) {
                    MTRIntegration.placeRailNode(world, e, player.getYaw());
                }
                anySuccess |= success;
            } else {
                System.out.println("Start position is not a valid rail node: " + s);
            }
        }

        // Validate rail type
        if (!MTRIntegration.isValidRailType(config.railType)) {
            player.sendMessage(Text.translatable("message.stationbuilder.rail_builder.invalid_rail",
                    config.railType.toString()), true);
            config.railType = MTRIntegration.getDefaultRailType();
        }

        // Build rails
        TickScheduler.schedule(1, () -> {
            MTRIntegration.buildRails(startPositions, endPositions, player.getUuid(), world, config);
        });

        if(anySuccess) {
            return endPositions;
        } else {
            return null;
        }
    }
}
