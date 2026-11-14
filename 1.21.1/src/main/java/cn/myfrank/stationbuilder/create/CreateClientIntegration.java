package cn.myfrank.stationbuilder.create;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.ITrackBlock;
import com.simibubi.create.content.trains.track.TrackPlacement;

import cn.myfrank.stationbuilder.mixin.create.PlacementInfoAccessor;
import cn.myfrank.stationbuilder.utils.CommonUtil;
import cn.myfrank.stationbuilder.utils.TestConnectResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CreateClientIntegration {
    
    public static TestConnectResult testConnectRailNodes(
            BlockPos startPos, float startAngle,
            BlockPos endPos, float endAngle, ResourceLocation railType) {

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return TestConnectResult.fail();
        }

        ClientLevel level = client.level;
        LocalPlayer player = client.player;

        BlockState stateStart = level.getBlockState(startPos);
        if (!(stateStart.getBlock() instanceof ITrackBlock)) {
            return TestConnectResult.fail();
        }

        BlockState stateEnd = level.getBlockState(endPos);
        if (!(stateEnd.getBlock() instanceof ITrackBlock)) {
            stateEnd = CreateIntegration.getTrackBlockState(endAngle, railType);
        }
        
        ItemStack trackStack = CommonUtil.getItemStack(railType);
        if (trackStack.isEmpty() || !(trackStack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ITrackBlock)) {
            trackStack = new ItemStack(CreateIntegration.TRACK_BLOCK.asItem());
        }

        BlockHitResult hitStart = new BlockHitResult(
                Vec3.atCenterOf(startPos),
                Direction.UP,
                startPos,
                false
        );
        UseOnContext startContext = new UseOnContext(level, player, InteractionHand.MAIN_HAND, trackStack, hitStart);
        InteractionResult firstResult = trackStack.useOn(startContext);
        if (firstResult != InteractionResult.SUCCESS && firstResult != InteractionResult.CONSUME) {
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
        Vec3 startCenter = CreateIntegration.getRailCenter(startPos);
        Vec3 endCenter = CreateIntegration.getRailCenter(endPos);

        List<Vec3> previewPoints = new ArrayList<>();
        double radius = 0.0;
        double length = 0.0;

        if (curve != null) {
            radius = curve.getRadius();
            length = curve.getLength();

            Vec3 curveStart = curve.getPosition(0.0);
            Vec3 curveEnd = curve.getPosition(1.0);
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
                Vec3 delta = curveEnd.subtract(curveStart);
                Vec3 axis = CreateIntegration.getTrackDirectionFromAngle(startAngle).normalize();

                // 投影求得沿轨道轴向的纵向跨度 L 与横向偏距 u
                double L = Math.abs(delta.x * axis.x + delta.z * axis.z);
                // 横向偏距向量 = 相对向量 - 纵向投影向量
                Vec3 normalComp = new Vec3(delta.x - L * axis.x, 0, delta.z - L * axis.z);
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
    private static void sampleSegment(List<Vec3> points, Vec3 from, Vec3 to, double step) {
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
            Vec3 p = from.lerp(to, t);
            if (points.isEmpty() || points.get(points.size() - 1).distanceTo(p) > 1e-4) {
                points.add(p);
            }
        }
    }
}
