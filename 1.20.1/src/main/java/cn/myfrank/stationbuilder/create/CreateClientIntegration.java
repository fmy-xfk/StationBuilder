package cn.myfrank.stationbuilder.create;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackPlacement;

import cn.myfrank.stationbuilder.mixin.create.PlacementInfoAccessor;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.TestConnectResult;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class CreateClientIntegration {
    public static TestConnectResult testConnectRailNodes(BlockPos startPos, float startAngle, BlockPos endPos, float endAngle, Identifier railType) {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.world == null || client.player == null) {
            return TestConnectResult.fail();
        }

        ClientWorld level = client.world;
        ClientPlayerEntity player = client.player;

        BlockState stateStart = level.getBlockState(startPos);
        if (!(stateStart.getBlock() instanceof ITrackBlock)) {
            return TestConnectResult.fail();
        }

        BlockState stateEnd = level.getBlockState(endPos);
        if (!(stateEnd.getBlock() instanceof ITrackBlock)) {
            stateEnd = CreateIntegration.getTrackBlockState(endAngle);
        }

        ItemStack trackStack = CommonUtil.getItemStack(railType);
        if (trackStack.isEmpty() || !(trackStack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ITrackBlock)) {
            trackStack = new ItemStack(CreateIntegration.TRACK_BLOCK.asItem());
        }

        BlockHitResult hitStart = new BlockHitResult(
                Vec3d.ofCenter(startPos),
                Direction.UP,
                startPos,
                false
        );
        ItemUsageContext startContext = new ItemUsageContext(level, player, Hand.MAIN_HAND, trackStack, hitStart);
        ActionResult firstResult = trackStack.useOnBlock(startContext);
        if (firstResult != ActionResult.SUCCESS && firstResult != ActionResult.CONSUME) {
            return TestConnectResult.fail();
        }

        TrackPlacement.PlacementInfo info;
        try {
            info = TrackPlacement.tryConnect(level, player, endPos, stateEnd, trackStack, false, true);
        } catch (Throwable t) {
            return TestConnectResult.fail();
        }

        if (info == null) {
            return TestConnectResult.fail();
        }

        PlacementInfoAccessor accessor = (PlacementInfoAccessor) (Object) info;
        if (!accessor.isValid()) {
            return TestConnectResult.fail();
        }

        // 提取几何信息与连续中心线
        BezierConnection curve = accessor.getCurve();
        Vec3d startCenter = CreateIntegration.getRailCenter(startPos);
        Vec3d endCenter = CreateIntegration.getRailCenter(endPos);

        List<Vec3d> previewPoints = new ArrayList<>();
        double radius = 0.0;
        double length = 0.0;

        if (curve != null) {
            radius = curve.getRadius();
            length = curve.getLength();

            Vec3d curveStart = curve.getPosition(0.0);
            Vec3d curveEnd = curve.getPosition(1.0);
            double step = 0.25;

            // (1) 起点方块中心 -> 曲线切点 curveStart
            sampleSegment(previewPoints, startCenter, curveStart, step);

            // (2) 贝塞尔曲线段细分采样
            int curveSamples = Math.max(2, (int) Math.ceil(length / step));
            for (int i = 0; i <= curveSamples; i++) {
                double t = (double) i / (double) curveSamples;
                previewPoints.add(curve.getPosition(t));
            }

            // (3) 曲线切点 curveEnd -> 终点方块中心
            sampleSegment(previewPoints, curveEnd, endCenter, step);
            length += startCenter.distanceTo(curveStart) + curveEnd.distanceTo(endCenter);

            if (radius <= 0) {
                // 计算曲线实际切点之间的相对位移
                Vec3d delta = curveEnd.subtract(curveStart);
                Vec3d axis = CreateIntegration.getTrackDirectionFromAngle(startAngle).normalize();

                // 投影求得沿轨道轴向的纵向跨度 L 与横向偏距 u
                double L = Math.abs(delta.x * axis.x + delta.z * axis.z);
                // 横向偏距向量 = 相对向量 - 纵向投影向量
                Vec3d normalComp = new Vec3d(delta.x - L * axis.x, 0, delta.z - L * axis.z);
                double u = normalComp.length();

                // 当存在横向偏移时，通过对称双圆弧几何公式 R = (L^2 + u^2) / (4u)
                if (u > 0.05 && L > 0.1) {
                    radius = (L * L + u * u) / (4.0 * u);
                }
            }
        } else {
            length = startCenter.distanceTo(endCenter);
            radius = 0.0;
            sampleSegment(previewPoints, startCenter, endCenter, 0.5);
        }

        return new TestConnectResult(true, radius, length, previewPoints);
    }

    /**
     * 线性离散采样辅助方法
     */
    private static void sampleSegment(List<Vec3d> points, Vec3d from, Vec3d to, double step) {
        double dist = from.distanceTo(to);
        if (dist < 1e-4) {
            if (points.isEmpty() || points.get(points.size() - 1).distanceTo(from) > 1e-4) {
                points.add(from);
            }
            return;
        }
        int samples = Math.max(1, (int) Math.ceil(dist / step));
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3d p = from.lerp(to, t);
            if (points.isEmpty() || points.get(points.size() - 1).distanceTo(p) > 1e-4) {
                points.add(p);
            }
        }
    }
}
