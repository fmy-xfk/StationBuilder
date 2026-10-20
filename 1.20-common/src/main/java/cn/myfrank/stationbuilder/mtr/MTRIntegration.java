package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.utils.*;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGItemAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGTypeAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemPSDAPGBaseAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemRailModifierAccessor;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.NotNull;

import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.ServerWorld;
import org.mtr.mod.block.*;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemPSDAPGBase;
import org.mtr.mod.item.ItemRailModifier;
import org.mtr.mod.Items;
import org.mtr.mod.Blocks;
import org.mtr.mod.packet.PacketUpdateData;

import static org.mtr.mod.block.IBlock.*;

import org.jetbrains.annotations.Nullable;


import java.util.*;

public class MTRIntegration {
    public static ResourceLocation getDefaultRailType() {
        return ResourceLocation.fromNamespaceAndPath("mtr", "rail_connector_160");
    }

    public static boolean isRailNode(Level world, BlockPos pos) {
        var block = world.getBlockState(pos).getBlock();
        return block == Blocks.RAIL_NODE.get().data;
    }

    public static boolean isRailNode(BlockState state) {
        return state.getBlock() == Blocks.RAIL_NODE.get().data;
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, Direction facing) {
        var mtrNodeState = Blocks.RAIL_NODE.get().data.defaultBlockState()
                .setValue(BlockNode.FACING.data, facing == Direction.EAST || facing == Direction.WEST)
                .setValue(BlockNode.IS_45.data, false)
                .setValue(BlockNode.IS_22_5.data, false)
                .setValue(BlockNode.IS_CONNECTED.data, false);
        world.setBlock(pos, mtrNodeState, 3);
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, float angle) {
        var quadrant = Angle.getQuadrant(angle, true);
        var mtrNodeState = Blocks.RAIL_NODE.get().data.defaultBlockState()
                .setValue(BlockNode.FACING.data, quadrant % 8 >= 4)
                .setValue(BlockNode.IS_45.data, quadrant % 4 >= 2)
                .setValue(BlockNode.IS_22_5.data, quadrant % 2 == 1)
                .setValue(BlockNode.IS_CONNECTED.data, false);
        world.setBlock(pos, mtrNodeState, 3);
    }

    public static float getRailNodeAngle(ServerLevel world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(state));
    }

    public static double getAngleFromVec3d(Vec3 v) {
        if (Math.abs(v.x) < 1e-8 && Math.abs(v.z) < 1e-8) {
            return 0.0; // 无水平方向，返回默认值
        }
        return Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    public static void placePIDSPole(ServerLevel world, BlockPos pos, Direction facing, ResourceLocation poleId) {
        var state = BuiltInRegistries.BLOCK.get(poleId).defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
        } else if (state.hasProperty(BlockStateProperties.FACING)) {
            state = state.setValue(BlockStateProperties.FACING, facing);
        }
        world.setBlock(pos, state, 3);
    }

    public static boolean placePIDS(ServerLevel world, BlockPos pos, Direction facing, ResourceLocation blockId) {
        var pids = BuiltInRegistries.BLOCK.get(blockId);
        world.setBlock(
                pos,
                pids.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing),
                3
        );
        world.setBlock(
                pos.relative(facing),
                pids.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing.getOpposite()),
                3
        );
        world.updateNeighborsAt(pos, net.minecraft.world.level.block.Blocks.AIR);
        return true;
    }

    public static boolean placePsdItem(ServerLevel world, BlockPos pos, Direction facing, ResourceLocation blockId) {
        var itemRaw = BuiltInRegistries.ITEM.get(blockId);
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
                var newPos = pos.relative(facing.getClockWise(), x);

                for (int y = 0; y < 2; ++y) {
                    // 获取基础 State 并手动设置属性
                    var state = accessor.getBlockStateFromItem_().data
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                            .setValue(HALF.data, y == 1 ? IBlock.DoubleBlockHalf.UPPER : IBlock.DoubleBlockHalf.LOWER);

                    if (thisItem.isDoor()) {
                        var neighborState = state
                                .setValue(SIDE.data, x == 0 ? IBlock.EnumSide.LEFT : IBlock.EnumSide.RIGHT);
                        if (thisType.isOdd()) {
                            neighborState = neighborState.setValue(TripleHorizontalBlock.CENTER.data,
                                    x > 0 && x < horizontalBlocks - 1);
                        }

                        world.setBlock(newPos.above(y), neighborState, 3);
                    } else {
                        world.setBlock(newPos.above(y), state.setValue(SIDE_EXTENDED.data, IBlock.EnumSide.SINGLE), 3);
                    }
                }

                if (thisType.isPSD()) {
                    var newPos2 = newPos.above(2);
                    var mappedWorldAccess = new org.mtr.mapping.holder.WorldAccess(world);
                    var mappedPos = new org.mtr.mapping.holder.BlockPos(newPos2);
                    var mappedState = BlockPSDTop.getActualState(mappedWorldAccess, mappedPos);
                    world.setBlock(
                            newPos.above(2),
                            mappedState.data,
                            3
                    );
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static Rail connectRailNodes(
            java.util.UUID uuid, ServerLevel world, BlockPos a, BlockPos b, int speed
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

        return connectRailNodes(uuid, world, a, b, modifier);
    }

    public static boolean isValidRailType(ResourceLocation railType) {
        var itemRaw = BuiltInRegistries.ITEM.get(railType);
        return itemRaw instanceof ItemRailModifier;
    }

    public static CurveData connectRailNodes(
            UUID uuid, ServerLevel world, BlockPos a, BlockPos b, ResourceLocation railType
    ) {
        var itemRaw = BuiltInRegistries.ITEM.get(railType);
        if (itemRaw instanceof ItemRailModifier modifier) {
            Rail r = connectRailNodes(uuid, world, a, b, modifier);
            if (r != null) {
                return new MTRCurveData(r.railMath);
            }
        } else {
            System.out.println("Invalid rail type: " + railType);
        }
        return null;
    }

    public static Pair<Float, Float> getRailNodeAngles(
            ServerLevel world, BlockPos a, BlockPos b
    ) {
        var sa = world.getBlockState(a);
        var sb = world.getBlockState(b);
        if (!isRailNode(world, a) || !isRailNode(world, b)) return null;
        float facingStart = BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(sa));
        float facingEnd = BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(sb));
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
        var angles = Rail.getAngles(
                new Position(a.getX(), a.getY(), a.getZ()), parseAngle(angleStart),
                new Position(b.getX(), b.getY(), b.getZ()), parseAngle(angleEnd)
        );
        Angle facingStart = angles.left();
        Angle facingEnd = angles.right();
        var posStart = new BlockPos(a);
        var posEnd = new BlockPos(b);
        var transportMode = TransportMode.TRAIN;
        ItemRailModifier modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_160.get().data;
        var railType = ((ItemRailModifierAccessor) modifier).railType_();
        if (railType != null) {
            Position positionStart = new Position(posStart.getX(), posStart.getY(), posStart.getZ());
            Position positionEnd = new Position(posEnd.getX(), posEnd.getY(), posEnd.getZ());
            Rail rail;
            switch (railType) {
                case PLATFORM ->
                        rail = Rail.newPlatformRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0F, new ObjectArrayList<>(), transportMode);
                case SIDING ->
                        rail = Rail.newSidingRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0F, new ObjectArrayList<>(), transportMode);
                case TURN_BACK ->
                        rail = Rail.newTurnBackRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0F, new ObjectArrayList<>(), transportMode);
                default ->
                        rail = Rail.newRail(positionStart, facingStart, positionEnd, facingEnd, railType.railShape, 0.0F, new ObjectArrayList<>(), (long) railType.speedLimit, (long) railType.speedLimit, false, false, railType.canAccelerate, railType == RailType.RUNWAY, railType.hasSignal, transportMode);
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
                ArrayList<Vec3> points = new ArrayList<>();
                for (double s = 0; s <= rail.railMath.getLength(); s += 0.1) {
                    points.add(toVec3d(rail.railMath.getPosition(s, false)));
                }
                return new TestConnectResult(true, radius, rail.railMath.getLength(), points);
            }
        }
        return new TestConnectResult(false, 0, 0, new ArrayList<>());
    }

    @Nullable
    protected static Rail connectRailNodes(
            java.util.UUID uuid, ServerLevel world, BlockPos a, BlockPos b,
            ItemRailModifier modifier
    ) {
        var sa = world.getBlockState(a);
        var sb = world.getBlockState(b);
        if (!isRailNode(world, a) || !isRailNode(world, b)) {
            return null;
        }
        var angles = Rail.getAngles(
                new Position(a.getX(), a.getY(), a.getZ()), BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(sa)),
                new Position(b.getX(), b.getY(), b.getZ()), BlockNode.getAngle(new org.mtr.mapping.holder.BlockState(sb))
        );
        Angle facingStart = angles.left();
        Angle facingEnd = angles.right();

        Rail rail = modifier.createRail(uuid, TransportMode.TRAIN,
                new org.mtr.mapping.holder.BlockState(sa),
                new org.mtr.mapping.holder.BlockState(sb),
                new org.mtr.mapping.holder.BlockPos(a),
                new org.mtr.mapping.holder.BlockPos(b), facingStart, facingEnd);

        if (rail != null) {
            world.setBlock(a, sa.setValue(BlockNode.IS_CONNECTED.data, true), 3);
            world.setBlock(b, sb.setValue(BlockNode.IS_CONNECTED.data, true), 3);
            PacketUpdateData.sendDirectlyToServerRail(new ServerWorld(world), rail);
            return rail;
        } else {
            System.out.println("Failed to create rail between " + a + "(" + facingStart + ") and " + b + "(" + facingEnd + ") with modifier " + modifier);
        }
        return null;
    }

    private static Vec3 toVec3d(Vector v) {
        return new Vec3(v.x, v.y, v.z);
    }

    public static Direction horizontalDirectionFromVec(Vec3 v) {
        double x = v.x, z = v.z;
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    public static BlockPos addCatenaryNode(
            ServerLevel world, Vec3 center, Vec3 tangent, boolean isLeftest, boolean isRightest,
            @Nullable BlockPos lastCatenaryNode, ResourceLocation block, CatenaryTypeMapping type, int height
    ) {
        Direction dir = horizontalDirectionFromVec(tangent);
        double dirAngle = getAngleFromVec3d(tangent);
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));
        if (isLeftest || isRightest) {
            if (isLeftest) {
                dir = dir.getClockWise();
            } else {
                dir = dir.getCounterClockWise();
            }
            if (!MSDIntegration.placeCatenaryNode(world, catenaryPos, dir, dirAngle, block)) {
                return null;
            }
            if (lastCatenaryNode != null) {
                MSDIntegration.connectCatenary(world, lastCatenaryNode, catenaryPos, type);
            }
            return catenaryPos;
        }
        return null;
    }

}