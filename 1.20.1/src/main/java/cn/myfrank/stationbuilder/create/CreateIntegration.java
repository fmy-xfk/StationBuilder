package cn.myfrank.stationbuilder.create;

import java.util.*;

import cn.myfrank.stationbuilder.mixin.create.PlacementInfoAccessor;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.PointProvider;

import com.simibubi.create.content.trains.track.*;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CreateIntegration {
    public static final Block TRACK_BLOCK = Registries.BLOCK.get(new Identifier("create", "track"));

    public static Vec3d getTrackDirectionFromAngle(float angle) {
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        return switch (sector) {
            case 0 -> new Vec3d(0, 0, 1);
            case 1 -> new Vec3d(-0.70710678, 0, 0.70710678);
            case 2 -> new Vec3d(-1, 0, 0);
            case 3 -> new Vec3d(-0.70710678, 0, -0.70710678);
            case 4 -> new Vec3d(0, 0, -1);
            case 5 -> new Vec3d(0.70710678, 0, -0.70710678);
            case 6 -> new Vec3d(1, 0, 0);
            case 7 -> new Vec3d(0.70710678, 0, 0.70710678);
            default -> new Vec3d(0, 0, 1);
        };
    }

    public static BlockState getTrackBlockState(Direction facing) {
        BlockState state = TRACK_BLOCK.getDefaultState();
        TrackShape shape = switch (facing) {
            case NORTH, SOUTH -> TrackShape.ZO;
            case EAST, WEST -> TrackShape.XO;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE) {
            state = state.with(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static BlockState getTrackBlockState(Direction facing, Identifier railType) {
        Block block = CommonUtil.getBlock(railType);
        if (!(block instanceof ITrackBlock)) {
            block = TRACK_BLOCK; // 回退到默认铁轨
        }
        BlockState state = block.getDefaultState();
        TrackShape shape = switch (facing) {
            case NORTH, SOUTH -> TrackShape.ZO;
            case EAST, WEST -> TrackShape.XO;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE && state.contains(TrackBlock.SHAPE)) {
            state = state.with(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static boolean isRailNode(World world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof ITrackBlock;
    }

    public static boolean isValidRailType(Identifier railType) {
        if (railType == null) return false;

        // 1. 尝试直接获取方块，检查是否实现了 ITrackBlock 接口
        Block block = CommonUtil.getBlock(railType);
        if (block instanceof ITrackBlock) {
            return true;
        }

        // 2. 容错处理：若传入的是物品 ID（如 BlockItem），获取其绑定的方块进行判定
        Item item = CommonUtil.getItem(railType);
        if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof ITrackBlock) {
            return true;
        }

        return false;
    }

    public static BlockState getTrackBlockState(float angle) {
        BlockState state = TRACK_BLOCK.getDefaultState();
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        TrackShape shape = switch (sector) {
            case 0, 4 -> TrackShape.ZO;
            case 2, 6 -> TrackShape.XO;
            case 1, 5 -> TrackShape.ND;
            case 3, 7 -> TrackShape.PD;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE) {
            state = state.with(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static BlockState getTrackBlockState(float angle, Identifier railType) {
        Block block = CommonUtil.getBlock(railType);
        if (!(block instanceof ITrackBlock)) {
            block = TRACK_BLOCK;
        }
        BlockState state = block.getDefaultState();
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        TrackShape shape = switch (sector) {
            case 0, 4 -> TrackShape.ZO;
            case 2, 6 -> TrackShape.XO;
            case 1, 5 -> TrackShape.ND;
            case 3, 7 -> TrackShape.PD;
            default -> TrackShape.NONE;
        };
        if (shape != TrackShape.NONE && state.contains(TrackBlock.SHAPE)) {
            state = state.with(TrackBlock.SHAPE, shape);
        }
        return state;
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, float angle, Identifier railType) {
        var state = getTrackBlockState(angle, railType);
        world.setBlockState(pos, state, 3);
    }

    public static Vec3d getRailCenter(BlockPos pos) {
        return Vec3d.ofCenter(pos).add(0, 0.125, 0);
    }

    public static void carveTunnelAndBuildBridge(ServerWorld world, CurveData curveData, BlockPos startPos, BlockPos endPos, int tunnelRadius) {
        int segments = Math.max(10, (int) Math.ceil(curveData.getLength() * 5));
        PointProvider provider = new PointProvider(curveData, segments, false);
        Set<BlockPos> modified = new HashSet<>();

        while (provider.notExhausted()) {
            List<Vec3d> frame = provider.get();
            Vec3d center = frame.get(0);
            Vec3d normal = frame.get(2);

            for (int w = -tunnelRadius; w <= tunnelRadius; w++) {
                Vec3d slicePos = center.add(normal.multiply(w));
                BlockPos columnBase = BlockPos.ofFloored(slicePos.x, slicePos.y, slicePos.z);

                for (int dy = 0; dy <= 2; dy++) {
                    BlockPos airPos = columnBase.up(dy);
                    if (airPos.equals(startPos) || airPos.equals(endPos)) continue;

                    if (modified.add(airPos)) {
                        BlockState state = world.getBlockState(airPos);
                        if (!state.isAir() && !(state.getBlock() instanceof com.simibubi.create.content.trains.track.ITrackBlock)) {
                            world.setBlockState(airPos, Blocks.AIR.getDefaultState(), 3);
                        }
                    }
                }

                BlockPos floorPos = columnBase.down();
                if (modified.add(floorPos)) {
                    BlockState floorState = world.getBlockState(floorPos);
                    if (floorState.isAir() || !floorState.getFluidState().isEmpty()) {
                        world.setBlockState(floorPos, Blocks.STONE.getDefaultState(), 3);
                    }
                }
            }
            provider.next();
        }
    }

    /**
     * 单段标准 Create 原生连接（两点距离在限制内时调用）
     */
    public static CurveData connectRailNodes(PlayerEntity player, ServerWorld world, BlockPos s, BlockPos e, Identifier railType) {
        System.out.printf("Connect: %s %s\n", s, e);
        BlockState state1 = world.getBlockState(s);
        BlockState state2 = world.getBlockState(e);

        if (!(state1.getBlock() instanceof ITrackBlock) || !(state2.getBlock() instanceof ITrackBlock)) {
            return null;
        }

        ItemStack trackStack = CommonUtil.getItemStack(railType);
        if (trackStack.isEmpty() || !(trackStack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ITrackBlock)) {
            trackStack = new ItemStack(TRACK_BLOCK.asItem());
        }

        BlockHitResult hitStart = new BlockHitResult(Vec3d.ofCenter(s), Direction.UP, s, false);
        ItemUsageContext startContext = new ItemUsageContext(world, player, Hand.MAIN_HAND, trackStack, hitStart);
        ActionResult firstResult = trackStack.useOnBlock(startContext);

        if (!firstResult.isAccepted()) {
            return null;
        }

        TrackPlacement.PlacementInfo info;
        try {
            info = TrackPlacement.tryConnect(world, player, e, state2, trackStack, false, true);
        } catch (Throwable t) {
            return null;
        }

        if (info == null) return null;
        PlacementInfoAccessor accessor = (PlacementInfoAccessor) info;
        if (!accessor.isValid()) return null;

        CreateCurveData curveData = new CreateCurveData();
        Vec3d startCenter = getRailCenter(s);
        Vec3d endCenter = getRailCenter(e);
        BezierConnection curve = accessor.getCurve();

        if (curve != null) {
            Vec3d curveStart = curve.getPosition(0.0);
            Vec3d curveEnd = curve.getPosition(1.0);
            curveData.addLine(startCenter, curveStart);
            curveData.addBezier(curve);
            curveData.addLine(curveEnd, endCenter);
        } else {
            curveData.addLine(startCenter, endCenter);
        }

        carveTunnelAndBuildBridge(world, curveData, s, e, 1);

        try {
            BlockState updatedState2 = world.getBlockState(e);
            TrackPlacement.tryConnect(world, player, e, updatedState2, trackStack, false, true);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return curveData;
    }

    
}