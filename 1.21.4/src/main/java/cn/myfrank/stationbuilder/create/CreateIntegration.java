package cn.myfrank.stationbuilder.create;

import cn.myfrank.stationbuilder.utils.CurveData;
import cn.myfrank.stationbuilder.utils.TestConnectResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CreateIntegration {
    public static Vec3 getTrackDirectionFromAngle(float angle) {
        int sector = Math.floorMod((int) Math.floor(angle / 45.0F + 0.5F), 8);
        return switch (sector) {
            case 0 -> new Vec3(0, 0, 1);
            case 1 -> new Vec3(-0.70710678, 0, 0.70710678);
            case 2 -> new Vec3(-1, 0, 0);
            case 3 -> new Vec3(-0.70710678, 0, -0.70710678);
            case 4 -> new Vec3(0, 0, -1);
            case 5 -> new Vec3(0.70710678, 0, -0.70710678);
            case 6 -> new Vec3(1, 0, 0);
            case 7 -> new Vec3(0.70710678, 0, 0.70710678);
            default -> new Vec3(0, 0, 1);
        };
    }

    public static BlockState getTrackBlockState(Direction facing) {
        return null;
    }

    public static boolean isRailNode(Level world, BlockPos pos) {
        return false;
    }

    public static BlockState getTrackBlockState(float angle) {
        return null;
    }

    public static void placeRailNode(ServerLevel world, BlockPos pos, float angle) {

    }

    public static Vec3 getRailCenter(BlockPos pos) {
        return Vec3.atBottomCenterOf(pos).add(0, 0.125, 0);
    }

    public static void carveTunnelAndBuildBridge(ServerLevel world, CurveData curveData, BlockPos startPos, BlockPos endPos, int tunnelRadius) {

    }

    public static CurveData connectRailNodes(Player player, ServerLevel world, BlockPos s, BlockPos e) {
        return null;
    }

    public static TestConnectResult testConnectRailNodes(
            BlockPos startPos, float startAngle,
            BlockPos endPos, float endAngle) {
        return TestConnectResult.fail();
    }

}