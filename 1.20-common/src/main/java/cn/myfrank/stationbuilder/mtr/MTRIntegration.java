package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.utils.TestConnectResult;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGItemAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGTypeAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemPSDAPGBaseAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemRailModifierAccessor;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Property;
import org.mtr.mapping.mapper.BlockExtension;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.block.*;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemPSDAPGBase;
import org.mtr.mod.item.ItemRailModifier;
import org.mtr.mod.packet.PacketUpdateData;

import java.util.ArrayList;
import java.util.UUID;

import static org.mtr.mod.block.IBlock.*;

public class MTRIntegration {
    public static Identifier getDefaultRailType() {
        return new Identifier("mtr", "rail_connector_160");
    }

    public static boolean isRailNode(ServerWorld world, BlockPos pos) {
        var block = world.getBlockState(pos).getBlock();
        return block == org.mtr.mod.Blocks.RAIL_NODE.get().data;
    }

    public static boolean isRailNode(BlockState state) {
        return state.getBlock() == org.mtr.mod.Blocks.RAIL_NODE.get().data;
    }
    
    public static void placeRailNode(ServerWorld world, BlockPos pos, Direction facing) {
        var mtrNodeState = org.mtr.mod.Blocks.RAIL_NODE.get().getDefaultState().data
                .with(BlockNode.FACING.data, facing == Direction.EAST || facing == Direction.WEST)
                .with(BlockNode.IS_45.data, false)
                .with(BlockNode.IS_22_5.data, false)
                .with(BlockNode.IS_CONNECTED.data, false);
        world.setBlockState(pos, mtrNodeState);
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, float angle) {
        var quadrant = Angle.getQuadrant(angle, true);
        var mtrNodeState = org.mtr.mod.Blocks.RAIL_NODE.get().getDefaultState().data
                .with(BlockNode.FACING.data, quadrant % 8 >= 4)
                .with(BlockNode.IS_45.data, quadrant % 4 >= 2)
                .with(BlockNode.IS_22_5.data, quadrant % 2 == 1)
                .with(BlockNode.IS_CONNECTED.data, false);
        world.setBlockState(pos, mtrNodeState);
    }

    public static float getRailNodeAngle(ServerWorld world, BlockPos pos) {
        var state = new org.mtr.mapping.holder.BlockState(world.getBlockState(pos));
        return BlockNode.getAngle(state);
    }

    public static double getAngleFromVec3d(Vec3d v) {
        if (Math.abs(v.x) < 1e-8 && Math.abs(v.z) < 1e-8) {
            return 0.0; // 无水平方向，返回默认值
        }
        return Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    public static void placePIDSPole(ServerWorld world, BlockPos pos, Direction facing, Identifier poleId) {
        var state = Registries.BLOCK.get(poleId).getDefaultState();
        if (state.contains(net.minecraft.state.property.Properties.HORIZONTAL_FACING)) {
                state = state.with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, facing);
            } else if (state.contains(net.minecraft.state.property.Properties.FACING)) {
                state = state.with(net.minecraft.state.property.Properties.FACING, facing);
            }
        world.setBlockState(pos, state, 3);
    }

    public static boolean placePIDS(ServerWorld world, BlockPos pos, Direction facing, Identifier blockId) {
        var block = Registries.BLOCK.get(blockId);
        if (block instanceof BlockExtension pids) {
            world.setBlockState(
                    pos,
                    pids.getDefaultState2().with(new Property<>(HorizontalFacingBlock.FACING), facing).data,
                    3
            );
            world.setBlockState(
                    pos.offset(facing),
                    pids.getDefaultState2().with(new Property<>(HorizontalFacingBlock.FACING), facing.getOpposite()).data,
                    3
            );
            world.updateNeighbors(pos, org.mtr.mapping.holder.Blocks.getAirMapped().data);
            return true;
        } else {
            return false;
        }
    }

    public static boolean placePsdItem(ServerWorld world, BlockPos pos, Direction facing, Identifier blockId) {
        var itemRaw = Registries.ITEM.get(blockId);
        if (itemRaw instanceof ItemPSDAPGBase item) {
            ItemPSDAPGBaseAccessor accessor = (ItemPSDAPGBaseAccessor) item;
            var psdItem = accessor.item_();
            var thisItem = (EnumPSDAPGItemAccessor) (Object) psdItem;
            assert thisItem != null;
            var psdType = accessor.type_();
            var thisType = (EnumPSDAPGTypeAccessor) (Object) psdType;

            int horizontalBlocks = thisItem.isDoor() ? (thisType.isOdd() ? 3 : 2) : 1;

            for (int x = 0; x < horizontalBlocks; ++x) {
                // 计算横向偏移位置：沿站台边缘延伸
                var newPos = pos.offset(facing.rotateYClockwise(), x);

                for (int y = 0; y < 2; ++y) {
                    // 获取基础 State 并手动设置属性
                    var state = accessor.getBlockStateFromItem_()
                            .with(new Property<>(BlockPSDAPGBase.FACING.data), facing)
                            .with(new Property<>(HALF.data), y == 1 ? IBlock.DoubleBlockHalf.UPPER : IBlock.DoubleBlockHalf.LOWER);

                    if (thisItem.isDoor()) {
                        var neighborState = state
                                .with(new Property<>(SIDE.data), x == 0 ? IBlock.EnumSide.LEFT : IBlock.EnumSide.RIGHT);
                        if (thisType.isOdd()) {
                            neighborState = neighborState.with(new Property<>(TripleHorizontalBlock.CENTER.data),
                                x > 0 && x < horizontalBlocks - 1);
                        }

                        world.setBlockState(newPos.up(y), neighborState.data);
                    } else {
                        world.setBlockState(newPos.up(y), state
                                .with(new Property<>(SIDE_EXTENDED.data), IBlock.EnumSide.SINGLE).data);
                    }
                }

                if (thisType.isPSD()) {
                    var newPos2 = new org.mtr.mapping.holder.BlockPos(newPos.up(2));
                    world.setBlockState(
                        newPos.up(2),
                        BlockPSDTop.getActualState(
                            org.mtr.mapping.holder.WorldAccess.cast(new org.mtr.mapping.holder.ServerWorld(world)),
                            newPos2
                        ).data
                    );
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static MTRCurveData connectRailNodes(
            java.util.UUID uuid, ServerWorld world, BlockPos a, BlockPos b, int speed
    ) {
        ItemRailModifier modifier;
        if (speed <= 0) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_PLATFORM.get().data;
        } else if (speed <= 20) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_20.get().data;
        } else if (speed <= 40) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_40.get().data;
        } else if (speed <= 60) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_60.get().data;
        } else if (speed <= 80) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_80.get().data;
        } else if (speed <= 100) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_100.get().data;
        } else if (speed <= 120) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_120.get().data;
        } else if (speed <= 140) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_140.get().data;
        } else if (speed <= 160) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_160.get().data;
        } else if (speed <= 200) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_200.get().data;
        } else {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_300.get().data;
        }

        Rail rail = connectRailNodesRaw(uuid, world, a, b, modifier);
        return rail == null ? null : new MTRCurveData(rail.railMath);
    }

    public static boolean isValidRailType(Identifier railType) {
        var itemRaw = Registries.ITEM.get(railType);
        return itemRaw instanceof ItemRailModifier;
    }
    
    public static MTRCurveData connectRailNodes(
            UUID uuid, ServerWorld world, BlockPos a, BlockPos b, Identifier railType
    ) {
        var itemRaw = Registries.ITEM.get(railType);
        if (itemRaw instanceof ItemRailModifier modifier) {
            Rail rail = connectRailNodesRaw(uuid, world, a, b, modifier);
            return rail == null ? null : new MTRCurveData(rail.railMath);
        } else {
            System.out.println("Invalid rail type: " + railType);
        }
        return null;
    }

    public static Pair<Float, Float> getRailNodeAngles(
            ServerWorld world, BlockPos a, BlockPos b
    ) {
        var sa = new org.mtr.mapping.holder.BlockState(world.getBlockState(a));
        var sb = new org.mtr.mapping.holder.BlockState(world.getBlockState(b));
        if (!isRailNode(world, a) || !isRailNode(world, b)) return null;
        float facingStart = BlockNode.getAngle(sa);
        float facingEnd   = BlockNode.getAngle(sb);
        return Pair.of(facingStart, facingEnd);
    }

    public static float parseAngle(float playerYaw) {
        int quadrant = Angle.getQuadrant(playerYaw, true);
        var facing = quadrant % 8 >= 4;
        var is_45 = quadrant % 4 >= 2;
        var is_22_5 = quadrant % 2 == 1;
        return (facing ? 0 : 90) + (is_22_5 ? 22.5F : 0.0F) + (is_45 ? 45 : 0);
    }

    @NotNull
    public static TestConnectResult testConnectRailNodes(
            float angleStart, float angleEnd, BlockPos a, BlockPos b
    ) {
        // Truncate angle
        var angles = Rail.getAngles(
                new Position(a.getX(), a.getY(), a.getZ()), parseAngle(angleStart),
                new Position(b.getX(), b.getY(), b.getZ()), parseAngle(angleEnd)
        );
        Angle facingStart = angles.left();
        Angle facingEnd   = angles.right();
        var posStart = new org.mtr.mapping.holder.BlockPos(a);
        var posEnd = new org.mtr.mapping.holder.BlockPos(b);
        var transportMode = TransportMode.TRAIN;
        ItemRailModifier modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_160.get().data;
        var railType = ((ItemRailModifierAccessor)modifier).railType_();
        if (railType != null) {
            Position positionStart = Init.blockPosToPosition(posStart);
            Position positionEnd = Init.blockPosToPosition(posEnd);
            Rail rail;
            switch (railType) {
                case PLATFORM -> rail = Rail.newPlatformRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0, new ObjectArrayList<>(), transportMode);
                case SIDING -> rail = Rail.newSidingRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0, new ObjectArrayList<>(), transportMode);
                case TURN_BACK -> rail = Rail.newTurnBackRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0, new ObjectArrayList<>(), transportMode);
                default -> rail = Rail.newRail(positionStart, facingStart, positionEnd, facingEnd, railType.railShape, 0.0, new ObjectArrayList<>(), railType.speedLimit, railType.speedLimit, false, false, railType.canAccelerate, railType == RailType.RUNWAY, railType.hasSignal, transportMode);
            }
            if (rail.isValid()) {
                var radii = rail.railMath.getHorizontalRadii();
                double radius;
                if (radii.leftDouble() > 0) {
                    if (radii.rightDouble() > 0) {
                        radius = Math.min(radii.leftDouble(), radii.rightDouble());
                    } else {
                        radius = radii.leftDouble();
                    }
                } else {
                    radius = radii.rightDouble();
                }
                ArrayList<Vec3d> points = new ArrayList<>();
                for(double s = 0; s <= rail.railMath.getLength(); s += 0.1) {
                    points.add(toVec3d(rail.railMath.getPosition(s, false)));
                }
                return new TestConnectResult(true, radius, rail.railMath.getLength(), points);
            }
        }
        return new TestConnectResult(false, 0, 0, new ArrayList<>());
    }

    @Nullable
    private static Rail connectRailNodesRaw(
            java.util.UUID uuid, ServerWorld world, BlockPos a, BlockPos b,
            ItemRailModifier modifier
    ) {
        var sa = new org.mtr.mapping.holder.BlockState(world.getBlockState(a));
        var sb = new org.mtr.mapping.holder.BlockState(world.getBlockState(b));
        if (!isRailNode(world, a) || !isRailNode(world, b)) {
            return null;
        }
        var angles = Rail.getAngles(
            new Position(a.getX(), a.getY(), a.getZ()), BlockNode.getAngle(sa),
            new Position(b.getX(), b.getY(), b.getZ()), BlockNode.getAngle(sb)
        );
        Angle facingStart = angles.left();
        Angle facingEnd   = angles.right();

        Rail rail = modifier.createRail(uuid, TransportMode.TRAIN, sa, sb,
                new org.mtr.mapping.holder.BlockPos(a), new org.mtr.mapping.holder.BlockPos(b),
                facingStart, facingEnd);

        if (rail != null) {
            world.setBlockState(a, sa.data.with(BlockNode.IS_CONNECTED.data, true), 3);
            world.setBlockState(b, sb.data.with(BlockNode.IS_CONNECTED.data, true), 3);
            PacketUpdateData.sendDirectlyToServerRail(new org.mtr.mapping.holder.ServerWorld(world), rail);
            return rail;
        } else {
            System.out.println("Failed to create rail between " + a + "(" + facingStart + ") and " + b + "(" + facingEnd + ") with modifier " + modifier);
        }
        return null;
    }


    private static Vec3d toVec3d(Vector v) {
        return new Vec3d(v.x, v.y, v.z);
    }
}
