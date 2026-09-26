package cn.myfrank.stationbuilder;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RailGenerator {
    public enum RailNodeType {
        VANILLA,
        CREATE,
        MTR,
        UNSUPPORTED
    }

    public static boolean isRailNode(ServerLevel world, BlockPos pos, RailNodeType railNodeType) {
        if (railNodeType == RailNodeType.MTR && StationBuilder.isMtrLoaded()) {
            return MTRIntegration.isRailNode(world, pos);
        } else if (railNodeType == RailNodeType.CREATE && StationBuilder.isCreateLoaded()) {
            return CreateIntegration.isRailNode(world, pos);
        } else if (railNodeType == RailNodeType.VANILLA) {
            BlockState state = world.getBlockState(pos);
            return state.is(Blocks.RAIL) || state.is(Blocks.POWERED_RAIL) || state.is(Blocks.DETECTOR_RAIL) || state.is(Blocks.ACTIVATOR_RAIL);
        } else {
            return false;
        }
    }

    public static RailNodeType getRailNodeType(RailBuilderConfig config) {
        switch (config.railType.getNamespace()) {
            case "mtr" -> {
                if (StationBuilder.isMtrLoaded()) {
                    if (MTRIntegration.isValidRailType(config.railType)) {
                        return RailNodeType.MTR;
                    } else {
                        return RailNodeType.UNSUPPORTED;
                    }
                } else {
                    return RailNodeType.UNSUPPORTED;
                }           
            }
            case "create" -> {
                if (config.railType.getPath().equals("track")) {
                    return RailNodeType.CREATE;
                } else {
                    return RailNodeType.UNSUPPORTED;
                }            
            }
            default -> {
                if (config.railType.getNamespace().equals("minecraft") && config.railType.getPath().contains("rail")) {
                    return RailNodeType.VANILLA;
                } else {
                    return RailNodeType.UNSUPPORTED;
                }
            }
        }
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, Player player, RailNodeType railNodeType, boolean showInfo) {
        if (railNodeType == RailNodeType.MTR && StationBuilder.isMtrLoaded()){
            if (!MTRIntegration.isRailNode(world, pos)) {
                MTRIntegration.placeRailNode(world, pos, player.getYRot());
            } else {
                if(showInfo) System.out.println("Position is already a rail node: " + pos);
            }
        } else if (railNodeType == RailNodeType.CREATE && StationBuilder.isCreateLoaded()) {
            CreateIntegration.placeRailNode(world, pos, player.getYRot());
        } else if (railNodeType == RailNodeType.VANILLA) {
            BlockState state = Blocks.RAIL.defaultBlockState();
            state.setValue(BlockStateProperties.RAIL_SHAPE, (int)(player.getYRot() / 90.0f + 0.5f) % 2 == 0 ? RailShape.NORTH_SOUTH : RailShape.EAST_WEST);
        } else {
            if(showInfo) System.out.println("Rail node type not supported or mod not loaded: " + railNodeType);
        }
    }

    public static void placeFirstRailNode(ServerLevel world, BlockPos pos, Player player, RailNodeType railNodeType) {
        placeRailNode(world, pos, player, railNodeType, true);
    }

    public static ArrayList<BlockPos> calcRailNodes(BlockPos pos, float yaw, RailBuilderConfig config) {
        final ArrayList<BlockPos> placedPositions = new ArrayList<>();
        if (StationBuilder.isMtrLoaded()){
            Vec3 normal = MTRRailMath.normalFromYaw(yaw);

            int count = config.railCount;
            double spacing = config.railSpacing;

            for (int i = 0; i < count; i++) {
                double offsetIndex = i - (count - 1) / 2.0;
                double t = offsetIndex * spacing;
                Vec3 offset = normal.multiply(t, t, t);
                BlockPos s = MTRRailMath.offsetPos(pos, offset);
                placedPositions.add(s);
            }
            return placedPositions;
        }
        return null;
    }

    public static ArrayList<BlockPos> placeFirstRailNodes(ServerLevel world, BlockPos pos, Player player, RailBuilderConfig config) {
        ArrayList<BlockPos> nodes = calcRailNodes(pos, player.getYRot(), config);
        RailNodeType railNodeType = getRailNodeType(config);
        if (nodes != null) {
            for (BlockPos p : nodes) {
                placeFirstRailNode(world, p, player, railNodeType);
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
        RailNodeType railNodeType = getRailNodeType(config);
        if (railNodeType == RailNodeType.UNSUPPORTED) {
            player.displayClientMessage(Component.translatable("message.stationbuilder.rail_builder.invalid_rail",
                    config.railType.toString()), true);
            return null;
        }

        float yaw = player.getYRot();
        Vec3 normal = MTRRailMath.normalFromYaw(yaw); // 右侧法向量
        int count = config.railCount;
        double spacing = config.railSpacing;

        // Calculate end positions
        ArrayList<BlockPos> endPositions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double offsetIndex = i - (count - 1) / 2.0;
            double t = offsetIndex * spacing;
            Vec3 offset = normal.multiply(t, t, t);
            BlockPos e = MTRRailMath.offsetPos(endPos, offset);
            endPositions.add(e);
        }

        // Adjust positions
        MTRRailMath.adjustPointSequence(startPositions, endPositions);

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
            if(isRailNode(world, s, railNodeType)) {
                boolean success = true;
                if (s.equals(e)) continue;
                placeRailNode(world, e, player, railNodeType, false);
                anySuccess |= success;
            } else {
                System.out.println("Start position is not a valid rail node: " + s);
            }
        }

        // Build rails
        TickScheduler.schedule(1, () -> {
            var failToPlaceCatenaryNode = false;
            if (railNodeType == RailNodeType.MTR) {
                failToPlaceCatenaryNode = MTRIntegration.buildRails(startPositions, endPositions, player.getUUID(), world, config);
            } else if (railNodeType == RailNodeType.CREATE) {
                failToPlaceCatenaryNode = CreateIntegration.buildRails(startPositions, endPositions, player, world, config);
            } else if (railNodeType == RailNodeType.VANILLA) {
                // Build rails for Vanilla
                // failToPlaceCatenaryNode = VanillaIntegration.buildRails(startPositions, endPositions, player.getUUID(), world, config);
            }
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
