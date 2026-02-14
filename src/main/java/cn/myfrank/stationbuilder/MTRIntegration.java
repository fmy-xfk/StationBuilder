package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGItemAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGTypeAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemPSDAPGBaseAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemRailModifierAccessor;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.enums.WallShape;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.BlockState;

import net.minecraft.world.Heightmap;
import org.jetbrains.annotations.NotNull;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Property;
import org.mtr.mod.Init;
import org.mtr.mod.Items;
import org.mtr.mod.block.*;
import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemPSDAPGBase;
import org.mtr.mod.item.ItemRailModifier;
import org.mtr.mod.packet.PacketUpdateData;
import static org.mtr.mod.block.IBlock.*;

import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MTRIntegration {
    private static final double EPS = 1e-6;

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

    public static void placePIDSPole(ServerWorld world, BlockPos pos, Direction facing) {
        var pole = org.mtr.mod.Blocks.PIDS_POLE.get().getDefaultState();
        world.setBlockState(pos, pole.with(new Property<>(HorizontalFacingBlock.FACING), facing).data);
    }

    public static boolean placePIDS(ServerWorld world, BlockPos pos, Direction facing, Identifier blockId) {
        var block = Registries.BLOCK.get(blockId);
        if (block instanceof BlockPIDSHorizontalBase pids) {
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

    public static Rail connectRailNodes(
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

        return connectRailNodes(uuid, world, a, b, modifier);
    }

    public static boolean isValidRailType(Identifier railType) {
        var itemRaw = Registries.ITEM.get(railType);
        return itemRaw instanceof ItemRailModifier;
    }
    
    public static Rail connectRailNodes(
            UUID uuid, ServerWorld world, BlockPos a, BlockPos b, Identifier railType
    ) {
        var itemRaw = Registries.ITEM.get(railType);
        if (itemRaw instanceof ItemRailModifier modifier) {
            return connectRailNodes(uuid, world, a, b, modifier);
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
    protected static TestConnectResult testConnectRailNodes(
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
    protected static Rail connectRailNodes(
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

    private static void clearBlock(ServerWorld world, BlockPos pos, boolean includeCatenary) {
        if(StationBuilder.isMsdLoaded()) {
            if (MSDIntegration.isCatenaryNode(world, pos)) {
                if (includeCatenary) {
                    MSDIntegration.clearCatenary(world, pos);
                } else {
                    return;
                }
            }
        }
        world.setBlockState(pos, org.mtr.mapping.holder.Blocks.getAirMapped().data.getDefaultState());
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static Vec3d toVec3d(Vector v) {
        return new Vec3d(v.x, v.y, v.z);
    }

    private static Vector toVector(BlockPos v) {
        return new Vector(v.getX(), v.getY(), v.getZ());
    }

    public static Direction horizontalDirectionFromVec(Vec3d v) {
        double x = v.x, z = v.z;
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static BlockPos addCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, boolean isLeftest, boolean isRightest,
            @Nullable BlockPos lastCatenaryNode, boolean top
    ) {
        Direction dir = horizontalDirectionFromVec(tangent);
        int blockY = (int) Math.floor(center.getY());
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + 5, (int) Math.floor(center.z));
        if (isLeftest || isRightest) {
            if (isLeftest) {
                dir = dir.rotateYClockwise();
            } else {
                dir = dir.rotateYCounterclockwise();
            }
            MSDIntegration.placeCatenaryNode(world, catenaryPos, dir, top);
            if (lastCatenaryNode != null) {
                MSDIntegration.connectCatenary(world, lastCatenaryNode, catenaryPos, CatenaryTypeMapping.MSDCatenary);
            }
            return catenaryPos;
        }
        return null;
    }

    private static void clearHeights(Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config,
             boolean isLeftest, boolean isRightest, boolean clearCatenary) {
        double halfWidth = config.ballastTopWidth / 2.0 + EPS;
        int baseY = (int) Math.floor(center.y);
        var XZs = RailMath.getPositions(center, normal, config.tunnelHeight + 3);
        int height = 0;
        for(var xz: XZs) {
            BlockPos topPos = world.getTopPosition(Heightmap.Type.MOTION_BLOCKING,
                    new BlockPos(xz.x(), baseY, xz.z()));
            height = Math.max(height, topPos.getY() - baseY);
        }
        double k = Math.max(1.0, (double) height / config.tunnelHeight);
        height = Math.max(height, config.tunnelHeight);
        for (int y = 0; y <= height; ++y) {
            Vec3d layerCenter = center.add(0, y, 0);
            double halfWidth2 = halfWidth + y / k;
            int blockY = (int) Math.floor(layerCenter.y);
            var blockXZs = RailMath.getPositions(center, normal,
                    isLeftest ? halfWidth2 : halfWidth,
                    isRightest ? halfWidth2 : halfWidth);
            for(var block: blockXZs) {
                var pos = new BlockPos(block.x(), blockY, block.z());
                if (!isRailNode(world, pos)) {
                    clearBlock(world, pos, clearCatenary);
                }
            }
        }
    }

    private static void buildThickerBallast(
            Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config,
            boolean isLeftest, boolean isRightest
    ) {
        final int ballastHeight = config.ballastMaxThickness;
        double halfTopWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfBottomWidth = config.ballastBottomWidth / 2.0 + EPS;
        for (int y = 0; y < ballastHeight; y++) {
            double halfWidth = lerp(halfTopWidth, halfBottomWidth, (double) y / ballastHeight);
            Vec3d layerCenter = center.add(0, -y - 1, 0);
            int blockY = (int) Math.floor(layerCenter.y);
            var blockXZs = RailMath.getPositions(center, normal,
                    isLeftest? halfWidth: halfTopWidth,
                    isRightest? halfWidth: halfTopWidth);
            for(var block: blockXZs) {
                var pos = new BlockPos(block.x(), blockY, block.z());
                world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
            }
        }
    }

    private static void buildUpDown(
            Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config, boolean clearCatenary,
            BuildingMode.Up ubm, BuildingMode.Down dbm, boolean isLeftest, boolean isRightest, boolean pillar
    ) {
        double EPS = 1e-6;
        double halfBallastWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;
        double halfPillarWidth = 1.5;
        int blockY = (int) Math.floor(center.y);
        var blockXZs = RailMath.getPositions(center, normal, Math.max(halfTunnelWidth, halfBridgeWidth));
        ArrayList<BlockPos> overpass_walls = new ArrayList<>();
        int blockIndex = -1;
        for (var block: blockXZs) {
            blockIndex++;
            int x = block.x(), z = block.z();
            Vec3d p = new Vec3d(x + 0.5, blockY + 0.5, z + 0.5);
            double dist = Math.abs((p.x - center.x) * normal.x + (p.z - center.z) * normal.z);
            if (dbm == BuildingMode.Down.Bridge && dist <= halfBridgeWidth) {
                var bridgeBlockState = Registries.BLOCK.get(config.bridgeBlock).getDefaultState();
                if ((isLeftest && blockIndex == 0) || (isRightest && blockIndex == blockXZs.size() - 1)) {
                    var pos = new BlockPos(x, blockY - 1, z);
                    if (!isRailNode(world, pos)) {
                        world.setBlockState(pos, bridgeBlockState, 3);
                    }
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        world.setBlockState(posU, Registries.BLOCK.get(config.bridgeGuardRailBlock).getDefaultState(), 0);
                        overpass_walls.add(posU);
                    }
                } else {
                    var posD = new BlockPos(x, blockY - 2, z);
                    world.setBlockState(posD, bridgeBlockState, 3);
                    var pos = new BlockPos(x, blockY - 1, z);
                    world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        clearBlock(world, posU, clearCatenary);
                    }
                }
                if (pillar && dist <= halfPillarWidth) {
                    var pos = new BlockPos(x, blockY - 2, z);
                    int solidCount = 0;
                    while(solidCount < 3 && world.isInBuildLimit(pos)) {
                        if (StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
                            world.setBlockState(pos, Registries.BLOCK.get(config.bridgePillarBlock).getDefaultState(), 3);
                            solidCount = 0;
                        } else {
                            solidCount++;
                        }
                        pos = pos.offset(Direction.DOWN);
                    }
                }
            }
            if (ubm == BuildingMode.Up.Tunnel && dist <= halfTunnelWidth) {
                var posTop = new BlockPos(x, blockY + config.tunnelHeight, z);
                if (!isRailNode(world, posTop)) {
                    world.setBlockState(posTop, Registries.BLOCK.get(config.tunnelWallBlock).getDefaultState(), 3);
                }
                var posBottom = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, posBottom)) {
                    world.setBlockState(posBottom, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
                }
                if ((isLeftest && blockIndex == 0) || (isRightest && blockIndex == blockXZs.size() - 1)) {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        world.setBlockState(new BlockPos(x, blockY + y, z),
                                Registries.BLOCK.get(config.tunnelWallBlock).getDefaultState(), 3);
                    }
                } else {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        clearBlock(world, new BlockPos(x, blockY + y, z), clearCatenary);
                    }
                }
            }
            if (ubm != BuildingMode.Up.Tunnel && dbm == BuildingMode.Down.Ballast) {
                var pos = new BlockPos(x, blockY - 1, z);
                if (dist <= halfBallastWidth && !isRailNode(world, pos)) {
                    world.setBlockState(pos, Registries.BLOCK.get(config.ballastBlock).getDefaultState());
                }
            }
        }
        for(var pos: overpass_walls) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.offset(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.NORTH_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.SOUTH_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.EAST_SHAPE, WallShape.LOW);
                }
                if (world.getBlockState(pos.offset(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.with(WallBlock.WEST_SHAPE, WallShape.LOW);
                }
                world.setBlockState(pos, state, 3 | 16);
            }
        }
    }
    // >0: R is on left side of vector AB, <0 R is on right side of vector AB
    public static double getSide(Vector a, Vector b, Vector r) {
        Vector AB = new Vector(b.x - a.x, b.y - a.y, b.z - a.z);
        Vector AR = new Vector(r.x - a.x, r.y - a.y, r.z - a.z);
        return AB.x * AR.z - AB.z * AR.x;
    }

    public static void buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            UUID uuid, ServerWorld world, RailBuilderConfig config) {
        int count = startPositions.size();
        assert endPositions.size() == count;

        // Connect rails
        Rail[] rails = new Rail[count];
        double maxLength = -1.0;
        for (int i = 0; i < count; i++) {
            var rail = connectRailNodes(uuid, world, startPositions.get(i), endPositions.get(i), config.railType);
            rails[i] = rail;
            if (rail != null) {
                maxLength = Math.max(maxLength, rail.railMath.getLength());
            }
        }

        if (maxLength < 0) return; // No rail created.
        int segments = (int) Math.floor(maxLength / 0.5);

        // First round: building modes detection
        byte[][] ubm2 = new byte[count][segments + 1];
        byte[][] dbm2 = new byte[count][segments + 1];
        boolean[] reverse = new boolean[count];
        boolean[] pillar = new boolean[segments + 1];
        pillar[0] = pillar[segments] = true;

        int pillarSegCount = (int) Math.ceil(maxLength / config.bridgeClearSpan);
        double pillarSpacing = segments / (double) pillarSegCount;
        for (int i = 0; i < pillarSegCount; i++) {
            pillar[(int)Math.floor(i * pillarSpacing)] = true;
        }

        int catenarySegCount = (int) Math.ceil(maxLength / config.catenarySpacing);
        double catenarySpacing = segments / (double) catenarySegCount;

        for (int i = 0; i < count; i++) {
            var rail = rails[i];
            if (rail == null) continue;

            var pa = toVector(startPositions.get(i));
            var pb = toVector(endPositions.get(i));
            var p1 = rail.railMath.getPosition(0, false);
            reverse[i] = p1.distanceTo(pa) > p1.distanceTo(pb);
            MTRIntegration.calcBuildingMode(rail, world, config, reverse[i], segments, ubm2[i], dbm2[i]);
        }
        var ubm = BuildingMode.smoothModes(ubm2);
        var dbm = BuildingMode.smoothModes(dbm2);

        // Second round: Build tunnel, bridge, pillar, thick ballast, catenary

        // Create PointProviders, and check left and right side
        MTRPointProvider[] pps = new MTRPointProvider[count];
        boolean[] isLeftest = new boolean[count], isRightest = new boolean[count];
        for (int i = 0; i < count; i++) {
            var rail = rails[i];
            if (rail == null) continue;
            pps[i] = new MTRPointProvider(rail.railMath, segments, reverse[i]);
            if (count == 1) {
                isLeftest[i] = true; isRightest[i] = true;
            } else { // count > 1
                if (i == 0 || i == count - 1) {
                    var a = startPositions.get(i);
                    var b = endPositions.get(i);
                    BlockPos r = null;
                    if (i == 0) {
                        if (rails[i + 1] == null) {
                            isLeftest[i] = true; isRightest[i] = true;
                        } else {
                            r = startPositions.get(i + 1);
                        }
                    } else { // i == count - 1
                        if (rails[i - 1] == null) {
                            isLeftest[i] = true; isRightest[i] = true;
                        } else {
                            r = startPositions.get(i - 1);
                        }
                    }
                    if (r != null) {
                        isRightest[i] = getSide(toVector(a), toVector(b), toVector(r)) < 0;
                        isLeftest[i] = !isRightest[i];
                    }
                }
            }
        }

        // Build according to building modes
        for (int j = 0; j <= segments; j++) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var tuple = pps[i].get();
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                var thisDbm = BuildingMode.Down.fromValue(dbm[j]);
                var center = tuple.get(0);
                var normal = tuple.get(2);
                buildUpDown(center, normal, world, config, j > 1,
                        thisUbm, thisDbm, isLeftest[i], isRightest[i], pillar[j]);
                if (thisDbm == BuildingMode.Down.ThickBallast) {
                    buildThickerBallast(center, normal, world, config, isLeftest[i], isRightest[i]);
                }
                if (thisUbm == BuildingMode.Up.Clear) {
                    clearHeights(center, normal, world, config, isLeftest[i], isRightest[i], j > 1);
                }
                pps[i].next();
            }
        }

        // Build catenary
        for(int i = 0; i < count; i++) {
            BlockPos lastCatenaryNode = null;
            var rail = rails[i];
            if (rail == null) continue;
            MTRPointProvider pp = new MTRPointProvider(rail.railMath, segments, reverse[i]);
            for (int k = 0; k <= catenarySegCount; k++) {
                int j = k < catenarySegCount ? (int)Math.floor(k * catenarySpacing) : segments;
                var tuple = pp.get(j);
                var center = tuple.get(0);
                var tangent = tuple.get(1);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                lastCatenaryNode = addCatenaryNode(world, center, tangent, isLeftest[i], isRightest[i],
                        lastCatenaryNode, thisUbm == BuildingMode.Up.Tunnel);
            }
        }
    }

    public static void calcBuildingMode(
            Rail rail, ServerWorld world, RailBuilderConfig config, boolean reverseMath, int segments,
            byte[] upBuildingModes, byte[] downBuildingModes
    ) {
        final int thickBallastHeight = config.ballastMaxThickness;
        final double halfTunnelWidth = config.tunnelWidth / 2.0 + EPS;
        final double halfBridgeWidth = config.bridgeWidth / 2.0 + EPS;
        var math = rail.railMath;

        // Determine building modes for upper and lower attachments
        int i = 0;
        for (var pp = new MTRPointProvider(math, segments, reverseMath); pp.notExhausted(); pp.next(), i++) {
            var tuple = pp.get();
            Vec3d center = tuple.get(0);
            Vec3d normal = tuple.get(2);

            // Stretch left and right from the center point, and get a bundle of BlockPos
            int blockY = (int) Math.floor(center.y);
            var blockXZs = RailMath.getPositions(center, normal, Math.max(halfTunnelWidth, halfBridgeWidth));

            // Determine building modes
            BuildingMode.Up ubm = BuildingMode.Up.Clear;
            BuildingMode.Down dbm = BuildingMode.Down.Ballast;
            int roofBlocks = 0, roofSolidBlocks = 0;
            int lowBlocks = 0, lowSolidBlocks = 0;

            for (var block: blockXZs) {
                // Calculate distance to center
                int floorBlocks = 0, floorSolidBlocks = 0;
                for (int y = -thickBallastHeight - 1; y <= config.tunnelHeight + 3; y++) {
                    var pos = new BlockPos(block.x(), blockY + y, block.z());
                    if (!world.isInBuildLimit(pos)) continue;
                    var state = world.getBlockState(pos);
                    boolean isRailNode = isRailNode(world, pos);
                    boolean isSoftTransparent = StationBuilder.isSoftTransparent(state);
                    if (y < -1) {
                        // 检查是否需要更厚的路基
                        if (y == -thickBallastHeight - 1) {
                            lowBlocks += 1;
                            if (!isRailNode && !isSoftTransparent) lowSolidBlocks += 1;
                        } else {
                            floorBlocks += 1;
                            if (!isRailNode && !isSoftTransparent) floorSolidBlocks += 1;
                        }
                    } else if (y >= config.tunnelHeight) {
                        roofBlocks++;
                        if (!state.isAir() && !isRailNode && !isSoftTransparent) {
                            roofSolidBlocks++;
                        }
                    }
                }
                if ((double) floorSolidBlocks / floorBlocks <= 0.7) {
                    dbm = BuildingMode.Down.ThickBallast;
                }
            }
            if ((double) roofSolidBlocks / roofBlocks >= 0.6) {
                ubm = BuildingMode.Up.Tunnel;
            }
            if ((double) lowSolidBlocks / lowBlocks <= 0.6) {
                dbm = BuildingMode.Down.Bridge;
            }
            upBuildingModes[i] = ubm.getValue();
            downBuildingModes[i] = dbm.getValue();
        }
    }
}
