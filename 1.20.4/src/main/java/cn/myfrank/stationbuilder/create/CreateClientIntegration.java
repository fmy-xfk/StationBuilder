package cn.myfrank.stationbuilder.create;

import cn.myfrank.stationbuilder.utils.TestConnectResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class CreateClientIntegration {
    public static TestConnectResult testConnectRailNodes(BlockPos startPos, float startAngle, BlockPos endPos, float endAngle, Identifier railType) {
        return TestConnectResult.fail();
    }
}
