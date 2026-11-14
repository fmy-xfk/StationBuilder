package cn.myfrank.stationbuilder.create;

import cn.myfrank.stationbuilder.utils.TestConnectResult;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public class CreateClientIntegration {
    public static TestConnectResult testConnectRailNodes(
            BlockPos startPos, float startAngle,
            BlockPos endPos, float endAngle, ResourceLocation railType) {
        return TestConnectResult.fail();
    }
}
