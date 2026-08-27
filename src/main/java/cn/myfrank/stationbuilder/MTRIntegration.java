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
import org.mtr.mapping.mapper.BlockExtension;
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
            @Nullable BlockPos lastCatenaryNode, Identifier block, CatenaryTypeMapping type, int height
    ) {
        Direction dir = horizontalDirectionFromVec(tangent);
        double dirAngle = getAngleFromVec3d(tangent);
        int blockY = (int) Math.floor(center.getY());
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));
        if (isLeftest || isRightest) {
            if (isLeftest) {
                dir = dir.rotateYClockwise();
            } else {
                dir = dir.rotateYCounterclockwise();
            }
            if (!MSDIntegration.placeCatenaryNode(world, catenaryPos, dir, dirAngle, block)){
                return null;
            }
            if (lastCatenaryNode != null) {
                MSDIntegration.connectCatenary(world, lastCatenaryNode, catenaryPos, type);
            }
            return catenaryPos;
        }
        return null;
    }

    private static void drawLine(ServerWorld world, BlockPos a, BlockPos b, Identifier lineBlock) {
        var state = Registries.BLOCK.get(lineBlock).getDefaultState();
        int x1 = a.getX(), y1 = a.getY(), z1 = a.getZ();
        int x2 = b.getX(), y2 = b.getY(), z2 = b.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = x1 < x2 ? 1 : -1;
        int ys = y1 < y2 ? 1 : -1;
        int zs = z1 < z2 ? 1 : -1;

        int x = x1;
        int y = y1;
        int z = z1;

        setBlockIfEmpty(world, new BlockPos(x, y, z), state);

        int max = Math.max(dx, Math.max(dy, dz));
        if (max == 0) return;

        for (int i = 1; i <= max; i++) {
            int curX = x1 + Math.round((float)(i * (x2 - x1)) / max);
            int curY = y1 + Math.round((float)(i * (y2 - y1)) / max);
            int curZ = z1 + Math.round((float)(i * (z2 - z1)) / max);
                        // 强制采取 6向（曼哈顿）步进移动，杜绝对角线产生不连接的孤立围栏
            while(x != curX || y != curY || z != curZ) {
                if (x != curX) x += xs;
                else if (z != curZ) z += zs;
                else if (y != curY) y += ys;
                setBlockIfEmpty(world, new BlockPos(x, y, z), state);
            }
        }
    }
    private static void buildPillarDown(ServerWorld world, BlockPos topPos, Identifier pillarBlock) {
        var state = Registries.BLOCK.get(pillarBlock).getDefaultState();
        BlockPos pos = topPos;
        while(world.isInBuildLimit(pos) && StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
            pos = pos.offset(Direction.DOWN);
        }
    }

    private static void setBlockIfEmpty(ServerWorld world, BlockPos pos, net.minecraft.block.BlockState state) {
        if (world.getBlockState(pos).isAir() || StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlockState(pos, state, 3);
        }
    }

    private static BlockPos addVanillaCatenaryNode(
            ServerWorld world, Vec3d center, Vec3d tangent, boolean isRightest, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height, Identifier pillarBlock, Identifier lineBlock
    ) {
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));

        if (isRightest) {
            var trussPos = catenaryPos.up();
            Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();

            if (trackCount == 1) {
                // 单条轨道只在最右侧放置 L 型支架
                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * 3.0),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * 3.0)
                );
                drawLine(world, trussPos, rightEnd, pillarBlock);
                buildPillarDown(world, rightEnd.down(), pillarBlock);
            } else {
                // 多条轨道时，跨越所有轨道总宽画一道拱门
                double leftSpan = -((trackCount - 1) * railSpacing + 3.0) + EPS;
                double rightSpan = 3.0 - EPS;

                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * rightSpan),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * rightSpan)
                );
                BlockPos leftEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * leftSpan),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * leftSpan)
                );

                drawLine(world, rightEnd, leftEnd, pillarBlock);
                buildPillarDown(world, rightEnd.down(), pillarBlock);
                buildPillarDown(world, leftEnd.down(), pillarBlock);
            }
        }

        // 每条轨道沿着轨道延伸接触线（蜘蛛网或铁栏杆）
        if (lastCatenaryNode != null) {
            drawLine(world, lastCatenaryNode, catenaryPos, lineBlock);
        }

        return catenaryPos;
    }

    private static void clearHeights(Vec3d center, Vec3d normal, ServerWorld world, RailBuilderConfig config,
             boolean isLeftest, boolean isRightest, boolean clearCatenary) {
        double halfWidth = config.ballastTopWidth / 2.0 + EPS;
        int baseY = (int) Math.floor(center.y);
        if (config.clearFullHeight) {
            var XZs = RailMath.getPositions(center, normal, config.tunnelHeight + 3);
            int height = 0;
            for (var xz : XZs) {
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
                for (var block : blockXZs) {
                    var pos = new BlockPos(block.x(), blockY, block.z());
                    if (!isRailNode(world, pos)) {
                        clearBlock(world, pos, clearCatenary);
                    }
                }
            }
        } else {
            double clearHalfWidth = config.tunnelWidth / 2.0 + EPS;
            for (int y = 0; y <= config.tunnelHeight; ++y) {
                Vec3d layerCenter = center.add(0, y, 0);
                int blockY = (int) Math.floor(layerCenter.y);
                var blockXZs = RailMath.getPositions(center, normal,
                        isLeftest ? clearHalfWidth : halfWidth,
                        isRightest ? clearHalfWidth : halfWidth);
                for(var block: blockXZs) {
                    var pos = new BlockPos(block.x(), blockY, block.z());
                    if (!isRailNode(world, pos)) {
                        clearBlock(world, pos, clearCatenary);
                    }
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
        ArrayList<BlockPos> overpass_walls = new ArrayList<>();
        if (dbm == BuildingMode.Down.Bridge) {
            var bridgeXZs = RailMath.getPositions(center, normal, halfBridgeWidth);
            for (int i = 0; i < bridgeXZs.size(); i++) {
                var block = bridgeXZs.get(i);
                int x = block.x(), z = block.z();
                var bridgeBlockState = Registries.BLOCK.get(config.bridgeBlock).getDefaultState();
                if ((isLeftest && i == 0) || (isRightest && i == bridgeXZs.size() - 1)) {
                    var pos = new BlockPos(x, blockY - 1, z);
                    if (!isRailNode(world, pos)) {
                        world.setBlockState(pos, bridgeBlockState, 3);
                    }
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        world.setBlockState(posU, Registries.BLOCK.get(config.bridgeGuardRailBlock).getDefaultState(), 3);
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
                if (pillar) {
                    Vec3d p = new Vec3d(x + 0.5, blockY + 0.5, z + 0.5);
                    double dist = Math.abs((p.x - center.x) * normal.x + (p.z - center.z) * normal.z);
                    if (dist <= halfPillarWidth) {
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
            }
        }

        if (ubm == BuildingMode.Up.Tunnel) {
            var tunnelXZs = RailMath.getPositions(center, normal, halfTunnelWidth);
            for (int i = 0; i < tunnelXZs.size(); i++) {
                var block = tunnelXZs.get(i);
                int x = block.x(), z = block.z();
                var posTop = new BlockPos(x, blockY + config.tunnelHeight, z);
                if (!isRailNode(world, posTop)) {
                    world.setBlockState(posTop, Registries.BLOCK.get(config.tunnelWallBlock).getDefaultState(), 3);
                }
                var posBottom = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, posBottom)) {
                    world.setBlockState(posBottom, Registries.BLOCK.get(config.ballastBlock).getDefaultState(), 3);
                }
                if ((isLeftest && i == 0) || (isRightest && i == tunnelXZs.size() - 1)) {
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
        }
        if (ubm != BuildingMode.Up.Tunnel && dbm == BuildingMode.Down.Ballast) {
            var ballastXZs = RailMath.getPositions(center, normal, halfBallastWidth);
            for (var block : ballastXZs) {
                int x = block.x(), z = block.z();
                var pos = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, pos)) {
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

    public static boolean buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            UUID uuid, ServerWorld world, RailBuilderConfig config) {
        int count = startPositions.size();
        assert endPositions.size() == count;

        // Connect rails
        Rail[] rails = new Rail[count];
        double maxLength = -1.0;
        for (int i = 0; i < count; i++) {
            var pos1 = startPositions.get(i);
            var pos2 = endPositions.get(i);
            if (i < count / 2) {
                var temp = pos1;
                pos1 = pos2;
                pos2 = temp;
            }
            var rail = connectRailNodes(uuid, world, pos1, pos2, config.railType);
            rails[i] = rail;
            if (rail != null) {
                maxLength = Math.max(maxLength, rail.railMath.getLength());
            }
        }

        if (maxLength < 0) return false; // No rail created.
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

        // First pass: Clear heights according to building modes
        for (int j = 0; j <= segments; j++) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var tuple = pps[i].get(j);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                var center = tuple.get(0);
                var normal = tuple.get(2);
                if (thisUbm == BuildingMode.Up.Clear) {
                    clearHeights(center, normal, world, config, isLeftest[i], isRightest[i], j > 1);
                }
            }
        }

        // Second pass: Build structures
        for (int j = 0; j <= segments; j++) {
            for(int i = 0; i < count; i++) {
                if (rails[i] == null) continue;
                var tuple = pps[i].get(j);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                var thisDbm = BuildingMode.Down.fromValue(dbm[j]);
                var center = tuple.get(0);
                var normal = tuple.get(2);
                buildUpDown(center, normal, world, config, j > 1,
                        thisUbm, thisDbm, isLeftest[i], isRightest[i], pillar[j]);
                if (thisDbm == BuildingMode.Down.ThickBallast) {
                    buildThickerBallast(center, normal, world, config, isLeftest[i], isRightest[i]);
                }
            }
        }

        // Build catenary
        boolean failToPlaceCatenaryNode = false;
        for(int i = 0; i < count; i++) {
            BlockPos lastCatenaryNode = null;
            BuildingMode.Up lastUbm = null;
            var rail = rails[i];
            if (rail == null) continue;
            MTRPointProvider pp = new MTRPointProvider(rail.railMath, segments, reverse[i]);
            for (int k = 0; k <= catenarySegCount; k++) {
                int j = k < catenarySegCount ? (int)Math.floor(k * catenarySpacing) : segments;
                var tuple = pp.get(j);
                var center = tuple.get(0);
                var tangent = tuple.get(1);
                var thisUbm = BuildingMode.Up.fromValue(ubm[j]);
                if (config.useCatenary) {
                    if (config.isVanillaCatenary) {
                        lastCatenaryNode = addVanillaCatenaryNode(world, center, tangent, isRightest[i], count, config.railSpacing,
                                lastCatenaryNode, config.tunnelHeight - 1,
                                thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar,
                                config.catenaryBlock);
                    } else {
                        if (isLeftest[i] || isRightest[i]) {
                            CatenaryTypeMapping type = CatenaryTypeMapping.values()[config.catenaryModeIndex];
                            Identifier pillarBlock = thisUbm == BuildingMode.Up.Tunnel ? config.catenaryTunnelPillar : config.catenaryBridgePillar;

                            if (type == CatenaryTypeMapping.Auto) {
                                if (thisUbm == BuildingMode.Up.Tunnel) {
                                    pillarBlock = new Identifier("msd", "rigid_catenary_node");
                                } else {
                                    pillarBlock = new Identifier("msd", "catenary_with_long");
                                }

                                if (lastUbm != null) {
                                    if (lastUbm == BuildingMode.Up.Tunnel && thisUbm == BuildingMode.Up.Tunnel) {
                                        type = CatenaryTypeMapping.MSDRigidCatenary;
                                    } else if (lastUbm == BuildingMode.Up.Clear && thisUbm == BuildingMode.Up.Clear) {
                                        type = CatenaryTypeMapping.MSDCatenary;
                                    } else {
                                        type = CatenaryTypeMapping.MSDRigidSoftCatenary;
                                    }
                                }
                            }

                            lastCatenaryNode = addCatenaryNode(world, center, tangent, isLeftest[i], isRightest[i],
                                    lastCatenaryNode, pillarBlock, type, config.tunnelHeight - 1);
                            if (lastCatenaryNode == null) {
                                failToPlaceCatenaryNode = true;
                            }
                            lastUbm = thisUbm;
                        }
                    }
                }
            }
        }
        return failToPlaceCatenaryNode;
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
                        if (!state.isAir() && !isRailNode && !StationBuilder.isNotLiquidTransparent(state)) {
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
