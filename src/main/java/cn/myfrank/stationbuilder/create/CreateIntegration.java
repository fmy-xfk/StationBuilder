package cn.myfrank.stationbuilder.create;

import java.util.*;

import cn.myfrank.stationbuilder.utils.CurveData;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class CreateIntegration {
    public record CreatePreviewResult(boolean success, double radius, double length, List<Vec3d> positions) {}

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
        return null;
    }

    public static boolean isRailNode(World world, BlockPos pos) {
        return false;
    }

    public static BlockState getTrackBlockState(float angle) {
        return null;
    }

    public static void placeRailNode(ServerWorld world, BlockPos pos, float angle) {

    }

    public static Vec3d getRailCenter(BlockPos pos) {
        return Vec3d.ofCenter(pos).add(0, 0.125, 0);
    }

    public static void carveTunnelAndBuildBridge(ServerWorld world, CurveData curveData, BlockPos startPos, BlockPos endPos, int tunnelRadius) {

    }

    public static CurveData connectRailNodes(PlayerEntity player, ServerWorld world, BlockPos s, BlockPos e) {
        return null;
    }

    public static CreatePreviewResult testConnectRailNodes(
            BlockPos startPos, float startAngle,
            BlockPos endPos, float endAngle) {
        return new CreatePreviewResult(true, 0, 0, new ArrayList<>());
    }

}