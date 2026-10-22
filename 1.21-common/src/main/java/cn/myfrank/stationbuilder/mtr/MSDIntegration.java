package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.utils.CatenaryTypeMapping;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class MSDIntegration {
    public static boolean isCatenaryNode(ServerWorld world, BlockPos pos) {
        return false;
    }

    public static void clearCatenary(ServerWorld world, BlockPos pos) {

    }

    public static boolean placeCatenaryNode(ServerWorld world, BlockPos pos, Direction direction, double dirAngle, Identifier block) {
        return false;
    }

    public static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, int cType) {
        return false;
    }

    public static boolean connectCatenary(ServerWorld world, BlockPos a, BlockPos b, CatenaryTypeMapping type) {
        return false;
    }
}
