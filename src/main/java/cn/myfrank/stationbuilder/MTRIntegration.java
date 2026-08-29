package cn.myfrank.stationbuilder;

import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGItemAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGTypeAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemPSDAPGBaseAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemRailModifierAccessor;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import org.jetbrains.annotations.NotNull;

import org.mtr.MTR;
import org.mtr.core.data.Position;
import org.mtr.core.data.Rail;
import org.mtr.core.data.TransportMode;
import org.mtr.core.tool.Angle;
import org.mtr.core.tool.Vector;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.block.*;
import org.mtr.data.RailType;
import org.mtr.item.ItemPSDAPGBase;
import org.mtr.item.ItemRailModifier;
import org.mtr.registry.Items;
import org.mtr.registry.Blocks;
import org.mtr.packet.PacketUpdateData;
import static org.mtr.block.IBlock.*;

import org.jetbrains.annotations.Nullable;


import java.util.*;

public class MTRIntegration {
    private static final double EPS = 1e-6;

    public static ResourceLocation getDefaultRailType() {
        return ResourceLocation.fromNamespaceAndPath("mtr", "rail_connector_160");
    }

    public static boolean isRailNode(ServerLevel world, BlockPos pos) {
        var block = world.getBlockState(pos).getBlock();
        return block == Blocks.RAIL_NODE.get();
    }

    public static boolean isRailNode(BlockState state) {
        return state.getBlock() == Blocks.RAIL_NODE.get();
    }
    
    public static void placeRailNode(ServerLevel world, BlockPos pos, Direction facing) {
        var mtrNodeState = Blocks.RAIL_NODE.get().defaultBlockState()
                .setValue(BlockNode.FACING, facing == Direction.EAST || facing == Direction.WEST)
                .setValue(BlockNode.IS_45, false)
                .setValue(BlockNode.IS_22_5, false)
                .setValue(BlockNode.IS_CONNECTED, false);
        world.setBlock(pos, mtrNodeState, 3);
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, float angle) {
        var quadrant = Angle.getQuadrant(angle, true);
        var mtrNodeState = Blocks.RAIL_NODE.get().defaultBlockState()
                .setValue(BlockNode.FACING, quadrant % 8 >= 4)
                .setValue(BlockNode.IS_45, quadrant % 4 >= 2)
                .setValue(BlockNode.IS_22_5, quadrant % 2 == 1)
                .setValue(BlockNode.IS_CONNECTED, false);
        world.setBlock(pos, mtrNodeState, 3);
    }

    public static float getRailNodeAngle(ServerLevel world, BlockPos pos) {
        var state = world.getBlockState(pos);
        return BlockNode.getAngle(state);
    }

    public static double getAngleFromVec3d(Vec3 v) {
        if (Math.abs(v.x) < 1e-8 && Math.abs(v.z) < 1e-8) {
            return 0.0; // 无水平方向，返回默认值
        }
        return Math.toDegrees(Math.atan2(-v.x, v.z));
    }

    public static void placePIDSPole(ServerLevel world, BlockPos pos, Direction facing, ResourceLocation poleId) {
        var state = BuiltInRegistries.BLOCK.get(poleId).defaultBlockState();
        if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING)) {
                state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
            } else if (state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING)) {
                state = state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, facing);
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
                    var state = accessor.getBlockStateFromItem_()
                            .setValue(BlockStateProperties.HORIZONTAL_FACING, facing)
                            .setValue(HALF, y == 1 ? IBlock.DoubleBlockHalf.UPPER : IBlock.DoubleBlockHalf.LOWER);

                    if (thisItem.isDoor()) {
                        var neighborState = state
                                .setValue(SIDE, x == 0 ? IBlock.EnumSide.LEFT : IBlock.EnumSide.RIGHT);
                        if (thisType.isOdd()) {
                            neighborState = neighborState.setValue(TripleHorizontalBlock.CENTER,
                                x > 0 && x < horizontalBlocks - 1);
                        }

                        world.setBlock(newPos.above(y), neighborState, 3);
                    } else {
                        world.setBlock(newPos.above(y), state.setValue(SIDE_EXTENDED, IBlock.EnumSide.SINGLE), 3);
                    }
                }

                if (thisType.isPSD()) {
                    var newPos2 = newPos.above(2);
                    world.setBlock(
                        newPos.above(2),
                        BlockPSDTop.getActualState(world,newPos2),
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
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_PLATFORM.get();
        } else if (speed <= 20) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_20.get();
        } else if (speed <= 40) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_40.get();
        } else if (speed <= 60) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_60.get();
        } else if (speed <= 80) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_80.get();
        } else if (speed <= 100) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_100.get();
        } else if (speed <= 120) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_120.get();
        } else if (speed <= 140) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_140.get();
        } else if (speed <= 160) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_160.get();
        } else if (speed <= 200) {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_200.get();
        } else {
            modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_300.get();
        }

        return connectRailNodes(uuid, world, a, b, modifier);
    }

    public static boolean isValidRailType(ResourceLocation railType) {
        var itemRaw = BuiltInRegistries.ITEM.get(railType);
        return itemRaw instanceof ItemRailModifier;
    }
    
    public static Rail connectRailNodes(
            UUID uuid, ServerLevel world, BlockPos a, BlockPos b, ResourceLocation railType
    ) {
        var itemRaw = BuiltInRegistries.ITEM.get(railType);
        if (itemRaw instanceof ItemRailModifier modifier) {
            return connectRailNodes(uuid, world, a, b, modifier);
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
        var posStart = new BlockPos(a);
        var posEnd = new BlockPos(b);
        var transportMode = TransportMode.TRAIN;
        ItemRailModifier modifier = (ItemRailModifier) Items.RAIL_CONNECTOR_160.get();
        var railType = ((ItemRailModifierAccessor)modifier).railType_();
        if (railType != null) {
            Position positionStart = MTR.blockPosToPosition(posStart);
            Position positionEnd = MTR.blockPosToPosition(posEnd);
            Rail rail;
            switch (railType) {
                case PLATFORM -> rail = Rail.newPlatformRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0F, 0L, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, new ObjectArrayList<>(), transportMode);
                case SIDING -> rail = Rail.newSidingRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0F, 0L, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, new ObjectArrayList<>(), transportMode);
                case TURN_BACK -> rail = Rail.newTurnBackRail(positionStart, facingStart, positionEnd, facingEnd, Rail.Shape.QUADRATIC, 0.0F, 0L, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, new ObjectArrayList<>(), transportMode);
                default -> rail = Rail.newRail(positionStart, facingStart, positionEnd, facingEnd, railType.railShape, 0.0F, 0L, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, new ObjectArrayList<>(), (long)railType.speedLimit, (long)railType.speedLimit, false, false, railType.canAccelerate, railType == RailType.RUNWAY, railType.hasSignal, transportMode);
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
            java.util.UUID uuid, ServerLevel world, BlockPos a, BlockPos b,
            ItemRailModifier modifier
    ) {
        var sa = world.getBlockState(a);
        var sb = world.getBlockState(b);
        if (!isRailNode(world, a) || !isRailNode(world, b)) {
            return null;
        }
        var angles = Rail.getAngles(
            new Position(a.getX(), a.getY(), a.getZ()), BlockNode.getAngle(sa),
            new Position(b.getX(), b.getY(), b.getZ()), BlockNode.getAngle(sb)
        );
        Angle facingStart = angles.left();
        Angle facingEnd   = angles.right();

        Rail rail = modifier.createRail(uuid, TransportMode.TRAIN, sa, sb, a, b, facingStart, facingEnd);

        if (rail != null) {
            world.setBlock(a, sa.setValue(BlockNode.IS_CONNECTED, true), 3);
            world.setBlock(b, sb.setValue(BlockNode.IS_CONNECTED, true), 3);
            PacketUpdateData.sendDirectlyToServerRail(world, rail);
            return rail;
        } else {
            System.out.println("Failed to create rail between " + a + "(" + facingStart + ") and " + b + "(" + facingEnd + ") with modifier " + modifier);
        }
        return null;
    }

    private static void clearBlock(ServerLevel world, BlockPos pos, boolean includeCatenary) {
        world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static Vec3 toVec3d(Vector v) {
        return new Vec3(v.x(), v.y(), v.z());
    }

    private static Vector toVector(BlockPos v) {
        return new Vector(v.getX(), v.getY(), v.getZ());
    }

    public static Direction horizontalDirectionFromVec(Vec3 v) {
        double x = v.x, z = v.z;
        if (Math.abs(x) > Math.abs(z)) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private static void drawLine(ServerLevel world, BlockPos a, BlockPos b, ResourceLocation lineBlock) {
        var state = BuiltInRegistries.BLOCK.get(lineBlock).defaultBlockState();
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

    private static void buildPillarDown(ServerLevel world, BlockPos topPos, ResourceLocation pillarBlock) {
        var state = BuiltInRegistries.BLOCK.get(pillarBlock).defaultBlockState();
        BlockPos pos = topPos;
        while(!world.isOutsideBuildHeight(pos) && StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlock(pos, state, 3);
            pos = pos.relative(Direction.DOWN);
        }
    }

    private static void setBlockIfEmpty(ServerLevel world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (world.getBlockState(pos).isAir() || StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
            world.setBlock(pos, state, 3);
        }
    }

    private static BlockPos addVanillaCatenaryNode(
            ServerLevel world, Vec3 center, Vec3 tangent, boolean isRightest, int trackCount, double railSpacing,
            @Nullable BlockPos lastCatenaryNode, int height, ResourceLocation pillarBlock, ResourceLocation lineBlock
    ) {
        int blockY = (int) Math.floor(center.y);
        var catenaryPos = new BlockPos((int) Math.floor(center.x), blockY + height, (int) Math.floor(center.z));

        if (isRightest) {
            var trussPos = catenaryPos.above();
            Vec3 normal = new Vec3(-tangent.z, 0, tangent.x).normalize();

            if (trackCount == 1) {
                // 单条轨道只在最右侧放置 L 型支架
                BlockPos rightEnd = new BlockPos(
                        (int) Math.floor(center.x + normal.x * 3.0),
                        trussPos.getY(),
                        (int) Math.floor(center.z + normal.z * 3.0)
                );
                drawLine(world, trussPos, rightEnd, pillarBlock);
                buildPillarDown(world, rightEnd.below(), pillarBlock);
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
                buildPillarDown(world, rightEnd.below(), pillarBlock);
                buildPillarDown(world, leftEnd.below(), pillarBlock);
            }
        }

        // 每条轨道沿着轨道延伸接触线（蜘蛛网或铁栏杆）
        if (lastCatenaryNode != null) {
            drawLine(world, lastCatenaryNode, catenaryPos, lineBlock);
        }

        return catenaryPos;
    }

    private static void clearHeights(Vec3 center, Vec3 normal, ServerLevel world, RailBuilderConfig config,
             boolean isLeftest, boolean isRightest, boolean clearCatenary) {
        double halfWidth = config.ballastTopWidth / 2.0 + EPS;
        int baseY = (int) Math.floor(center.y);
        if (config.clearFullHeight) {
            var XZs = RailMath.getPositions(center, normal, config.tunnelHeight + 3);
            int height = 0;
            for (var xz : XZs) {
                BlockPos topPos = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING,
                        new BlockPos(xz.x(), baseY, xz.z()));
                height = Math.max(height, topPos.getY() - baseY);
            }
            double k = Math.max(1.0, (double) height / config.tunnelHeight);
            height = Math.max(height, config.tunnelHeight);
            for (int y = 0; y <= height; ++y) {
                Vec3 layerCenter = center.add(0, y, 0);
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
                Vec3 layerCenter = center.add(0, y, 0);
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
            Vec3 center, Vec3 normal, ServerLevel world, RailBuilderConfig config,
            boolean isLeftest, boolean isRightest
    ) {
        final int ballastHeight = config.ballastMaxThickness;
        double halfTopWidth = config.ballastTopWidth / 2.0 + EPS;
        double halfBottomWidth = config.ballastBottomWidth / 2.0 + EPS;
        for (int y = 0; y < ballastHeight; y++) {
            double halfWidth = lerp(halfTopWidth, halfBottomWidth, (double) y / ballastHeight);
            Vec3 layerCenter = center.add(0, -y - 1, 0);
            int blockY = (int) Math.floor(layerCenter.y);
            var blockXZs = RailMath.getPositions(center, normal,
                    isLeftest? halfWidth: halfTopWidth,
                    isRightest? halfWidth: halfTopWidth);
            for(var block: blockXZs) {
                var pos = new BlockPos(block.x(), blockY, block.z());
                world.setBlock(pos, BuiltInRegistries.BLOCK.get(config.ballastBlock).defaultBlockState(), 3);
            }
        }
    }

    private static void buildUpDown(
            Vec3 center, Vec3 normal, ServerLevel world, RailBuilderConfig config, boolean clearCatenary,
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
                var bridgeBlockState = BuiltInRegistries.BLOCK.get(config.bridgeBlock).defaultBlockState();
                if ((isLeftest && i == 0) || (isRightest && i == bridgeXZs.size() - 1)) {
                    var pos = new BlockPos(x, blockY - 1, z);
                    if (!isRailNode(world, pos)) {
                        world.setBlock(pos, bridgeBlockState, 3);
                    }
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        world.setBlock(posU, BuiltInRegistries.BLOCK.get(config.bridgeGuardRailBlock).defaultBlockState(), 3);
                        overpass_walls.add(posU);
                    }
                } else {
                    var posD = new BlockPos(x, blockY - 2, z);
                    world.setBlock(posD, bridgeBlockState, 3);
                    var pos = new BlockPos(x, blockY - 1, z);
                    world.setBlock(pos, BuiltInRegistries.BLOCK.get(config.ballastBlock).defaultBlockState(), 3);
                    var posU = new BlockPos(x, blockY, z);
                    if (!isRailNode(world, posU)) {
                        clearBlock(world, posU, clearCatenary);
                    }
                }
                if (pillar) {
                    Vec3 p = new Vec3(x + 0.5, blockY + 0.5, z + 0.5);
                    double dist = Math.abs((p.x - center.x) * normal.x + (p.z - center.z) * normal.z);
                    if (dist <= halfPillarWidth) {
                        var pos = new BlockPos(x, blockY - 2, z);
                        int solidCount = 0;
                        while(solidCount < 3 && !world.isOutsideBuildHeight(pos)) {
                            if (StationBuilder.isSoftTransparent(world.getBlockState(pos))) {
                                world.setBlock(pos, BuiltInRegistries.BLOCK.get(config.bridgePillarBlock).defaultBlockState(), 3);
                                solidCount = 0;
                            } else {
                                solidCount++;
                            }
                            pos = pos.relative(Direction.DOWN);
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
                    world.setBlock(posTop, BuiltInRegistries.BLOCK.get(config.tunnelWallBlock).defaultBlockState(), 3);
                }
                var posBottom = new BlockPos(x, blockY - 1, z);
                if (!isRailNode(world, posBottom)) {
                    world.setBlock(posBottom, BuiltInRegistries.BLOCK.get(config.ballastBlock).defaultBlockState(), 3);
                }
                if ((isLeftest && i == 0) || (isRightest && i == tunnelXZs.size() - 1)) {
                    for(int y = 0; y < config.tunnelHeight; y++) {
                        world.setBlock(new BlockPos(x, blockY + y, z),
                                BuiltInRegistries.BLOCK.get(config.tunnelWallBlock).defaultBlockState(), 3);
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
                    world.setBlock(pos, BuiltInRegistries.BLOCK.get(config.ballastBlock).defaultBlockState(), 3);
                }
            }
        }
        for(var pos: overpass_walls) {
            var state = world.getBlockState(pos);
            if (state.getBlock() instanceof WallBlock) {
                if (world.getBlockState(pos.relative(Direction.NORTH)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.NORTH_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.SOUTH)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.SOUTH_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.EAST)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.EAST_WALL, WallSide.LOW);
                }
                if (world.getBlockState(pos.relative(Direction.WEST)).getBlock() instanceof WallBlock) {
                    state = state.setValue(BlockStateProperties.WEST_WALL, WallSide.LOW);
                }
                world.setBlock(pos, state, 3 | 16);
            }
        }
    }
    // >0: R is on left side of vector AB, <0 R is on right side of vector AB
    public static double getSide(Vector a, Vector b, Vector r) {
        Vector AB = new Vector(b.x() - a.x(), b.y() - a.y(), b.z() - a.z());
        Vector AR = new Vector(r.x() - a.x(), r.y() - a.y(), r.z() - a.z());
        return AB.x() * AR.z() - AB.z() * AR.x();
    }

    public static boolean buildRails(
            ArrayList<BlockPos> startPositions, ArrayList<BlockPos> endPositions,
            UUID uuid, ServerLevel world, RailBuilderConfig config) {
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
                        // Cannot be MSD catenary, because MSD catenary is not implemented yet
                    }
                }
            }
        }
        return failToPlaceCatenaryNode;
    }

    public static void calcBuildingMode(
            Rail rail, ServerLevel world, RailBuilderConfig config, boolean reverseMath, int segments,
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
            Vec3 center = tuple.get(0);
            Vec3 normal = tuple.get(2);

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
                    if (!!world.isOutsideBuildHeight(pos)) continue;
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
