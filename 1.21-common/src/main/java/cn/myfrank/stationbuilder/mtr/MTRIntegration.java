package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGItemAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.EnumPSDAPGTypeAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemPSDAPGBaseAccessor;
import cn.myfrank.stationbuilder.mixin.mtr.ItemRailModifierAccessor;
import cn.myfrank.stationbuilder.utils.*;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;

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
    public static Identifier getDefaultRailType() {
        return Identifier.of("mtr", "rail_connector_160");
    }

    public static boolean isRailNode(World world, BlockPos pos) {
        var block = world.getBlockState(pos).getBlock();
        return block == Blocks.RAIL_NODE.get();
    }

    public static boolean isRailNode(BlockState state) {
        return state.getBlock() == Blocks.RAIL_NODE.get();
    }
    
    public static void placeRailNode(ServerWorld world, BlockPos pos, Direction facing) {
        var mtrNodeState = Blocks.RAIL_NODE.get().getDefaultState()
                .with(BlockNode.FACING, facing == Direction.EAST || facing == Direction.WEST)
                .with(BlockNode.IS_45, false)
                .with(BlockNode.IS_22_5, false)
                .with(BlockNode.IS_CONNECTED, false);
        world.setBlockState(pos, mtrNodeState);
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, float angle) {
        var quadrant = Angle.getQuadrant(angle, true);
        var mtrNodeState = Blocks.RAIL_NODE.get().getDefaultState()
                .with(BlockNode.FACING, quadrant % 8 >= 4)
                .with(BlockNode.IS_45, quadrant % 4 >= 2)
                .with(BlockNode.IS_22_5, quadrant % 2 == 1)
                .with(BlockNode.IS_CONNECTED, false);
        world.setBlockState(pos, mtrNodeState);
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
        var pids = Registries.BLOCK.get(blockId);
        world.setBlockState(
                pos,
                pids.getDefaultState().with(HorizontalFacingBlock.FACING, facing),
                3
        );
        world.setBlockState(
                pos.offset(facing),
                pids.getDefaultState().with(HorizontalFacingBlock.FACING, facing.getOpposite()),
                3
        );
        world.updateNeighbors(pos, net.minecraft.block.Blocks.AIR);
        return true;
    }

        public static boolean placePsdItem(ServerWorld world, BlockPos pos, Direction facing, Identifier blockId) {
        Item itemRaw = CommonUtil.getItem(blockId);
        BlockState baseState = null;
        boolean isDoor = false;
        boolean isOdd = false;
        boolean isPSD = false;

        // 路径 1: MTR 原生物品 (ItemPSDAPGBase)
        if (itemRaw instanceof ItemPSDAPGBase item) {
            ItemPSDAPGBaseAccessor accessor = (ItemPSDAPGBaseAccessor) item;
            baseState = accessor.getBlockStateFromItem_();
            var psdItem = (EnumPSDAPGItemAccessor) (Object) accessor.item_();
            var psdType = (EnumPSDAPGTypeAccessor) (Object) accessor.type_();
            isDoor = psdItem != null && psdItem.isDoor();
            isOdd = psdType != null && psdType.isOdd();
            isPSD = psdType != null && psdType.isPSD();
        } 
        // 路径 2: 第三方 MTR 模组 (以 BlockItem 或常规 Block 形式注册)
        else {
            Block block = null;
            if (itemRaw instanceof BlockItem bi) {
                block = bi.getBlock();
            } else if (CommonUtil.hasBlock(blockId)) {
                block = CommonUtil.getBlock(blockId);
            }

            if (block != null && block != net.minecraft.block.Blocks.AIR) {
                baseState = block.getDefaultState();
                // 门体判定：包含 SIDE 属性或者 ID 包含 "door"
                isDoor = baseState.contains(SIDE) || blockId.getPath().contains("door");
                // 三格门判定：包含 CENTER 属性
                isOdd = baseState.contains(TripleHorizontalBlock.CENTER);
                // 全高 PSD 判定：ID 或类名中包含 "psd"（半高 APG 则不包含 psd）
                isPSD = blockId.getPath().contains("psd") || block.getClass().getSimpleName().toLowerCase().contains("psd");
            }
        }

        if (baseState == null) {
            return false;
        }

        int horizontalBlocks = isDoor ? (isOdd ? 3 : 2) : 1;

        for (int x = 0; x < horizontalBlocks; ++x) {
            // 沿站台边缘横向推移
            BlockPos newPos = pos.offset(facing.rotateYClockwise(), x);

            for (int y = 0; y < 2; ++y) {
                BlockState state = baseState;
                if (state.contains(Properties.HORIZONTAL_FACING)) {
                    state = state.with(Properties.HORIZONTAL_FACING, facing);
                }
                if (state.contains(HALF)) {
                    state = state.with(HALF, y == 1 ? IBlock.DoubleBlockHalf.UPPER : IBlock.DoubleBlockHalf.LOWER);
                }

                if (isDoor) {
                    if (state.contains(SIDE)) {
                        state = state.with(SIDE, x == 0 ? IBlock.EnumSide.LEFT : IBlock.EnumSide.RIGHT);
                    }
                    if (isOdd && state.contains(TripleHorizontalBlock.CENTER)) {
                        state = state.with(TripleHorizontalBlock.CENTER, x > 0 && x < horizontalBlocks - 1);
                    }
                } else {
                    if (state.contains(SIDE_EXTENDED)) {
                        state = state.with(SIDE_EXTENDED, IBlock.EnumSide.SINGLE);
                    } else if (state.contains(SIDE)) {
                        state = state.with(SIDE, IBlock.EnumSide.SINGLE);
                    }
                }

                world.setBlockState(newPos.up(y), state, Block.NOTIFY_ALL);
            }

            // 如果是全高 PSD，在上方补齐顶盖
            if (isPSD) {
                BlockPos newPos2 = newPos.up(2);
                world.setBlockState(
                        newPos2,
                        BlockPSDTop.getActualState(world, newPos2),
                        Block.NOTIFY_ALL
                );
            }
        }
        return true;
    }

    public static Rail connectRailNodes(
            java.util.UUID uuid, ServerWorld world, BlockPos a, BlockPos b, int speed
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

    public static boolean isValidRailType(Identifier railType) {
        var itemRaw = Registries.ITEM.get(railType);
        return itemRaw instanceof ItemRailModifier;
    }
    
    public static MTRCurveData connectRailNodes(
            UUID uuid, ServerWorld world, BlockPos a, BlockPos b, Identifier railType
    ) {
        var itemRaw = Registries.ITEM.get(railType);
        if (itemRaw instanceof ItemRailModifier modifier) {
            Rail r = connectRailNodes(uuid, world, a, b, modifier);
            if (r == null) {
                return null;
            }
            return new MTRCurveData(r.railMath);
        } else {
            System.out.println("Invalid rail type: " + railType);
        }
        return null;
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
            world.setBlockState(a, sa.with(BlockNode.IS_CONNECTED, true), 3);
            world.setBlockState(b, sb.with(BlockNode.IS_CONNECTED, true), 3);
            PacketUpdateData.sendDirectlyToServerRail(world, rail);
            return rail;
        } else {
            System.out.println("Failed to create rail between " + a + "(" + facingStart + ") and " + b + "(" + facingEnd + ") with modifier " + modifier);
        }
        return null;
    }

    private static Vec3d toVec3d(Vector v) {
        return new Vec3d(v.x(), v.y(), v.z());
    }
}
