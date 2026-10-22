package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.utils.CatenaryTypeMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

public class MSDIntegration {
    public static boolean isCatenaryNode(ServerLevel world, BlockPos pos) {
        return false;
    }

    public static void clearCatenary(ServerLevel world, BlockPos pos) {
        
    }

    public static boolean placeCatenaryNode(ServerLevel world, BlockPos pos, Direction direction, double dirAngle, ResourceLocation blockId) {
        return false;
    }

    public static boolean connectCatenary(ServerLevel world, BlockPos a, BlockPos b, int cType) {
        return false;
    }

    public static boolean connectCatenary(ServerLevel world, BlockPos a, BlockPos b, CatenaryTypeMapping type) {
        return false;
    }
}