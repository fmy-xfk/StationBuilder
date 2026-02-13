package cn.myfrank.stationbuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class RailMath {

    /**
     * 轨道前进方向（备用，后面做自动 yaw 会用到）
     */
    public static Vec3d directionFromYaw(float yaw) {
        // Minecraft yaw: 0 = south, 正方向是顺时针
        double rad = Math.toRadians(-yaw);
        return new Vec3d(Math.sin(rad), 0, Math.cos(rad));
    }

    /**
     * 轨道法向量（左-右方向，用于平行轨道）
     */
    public static Vec3d normalFromYaw(float yaw) {
        double rad = Math.toRadians(-yaw);
        // direction = ( sin, 0, cos )
        // normal    = ( cos, 0, -sin )
        return new Vec3d(Math.cos(rad), 0, -Math.sin(rad));
    }

    /**
     * 向量偏移并对齐到 BlockPos（对称 & 稳定）
     */
    public static BlockPos offsetPos(BlockPos origin, Vec3d offset) {
        return new BlockPos(
                (int)Math.round(origin.getX() + offset.x),
                origin.getY(),
                (int)Math.round(origin.getZ() + offset.z)
        );
    }

    
    public record PairXZ(int x, int z) {
        double distTo(Vec3d p) {
            double tx = x + 0.5, tz = z + 0.5;
            return Math.hypot(tx - p.x, tz - p.z);
        }
    }

    public static ArrayList<PairXZ> getPositions(Vec3d center, Vec3d normal, double halfWidth) {
        return getPositions(center, normal, halfWidth, halfWidth);
    }

    public static ArrayList<PairXZ> getPositions(Vec3d center, Vec3d normal, double leftWidth, double rightWidth) {
        ArrayList<PairXZ> left = new ArrayList<>(), right = new ArrayList<>();
        double step = 0.5, dx = normal.x, dz = normal.z;
        for (double s = 0; s <= leftWidth; s += step) {
            var xzL = new PairXZ((int)Math.floor(center.x - s * dx), (int)Math.floor(center.z - s * dz));
            int leftSize = left.size();
            if (xzL.distTo(center) <= leftWidth &&
                    (leftSize == 0 || !Objects.equals(left.get(leftSize - 1), xzL))) {
                left.add(xzL);
            }
        }
        for (double s = 0; s <= rightWidth; s += step) {
            var xzR = new PairXZ((int)Math.floor(center.x + s * dx), (int)Math.floor(center.z + s * dz));
            int rightSize = right.size();
            if (xzR.distTo(center) <= rightWidth &&
                    (rightSize == 0 || !Objects.equals(right.get(rightSize - 1), xzR))) {
                right.add(xzR);
            }
        }
        Collections.reverse(left);
        int leftLastIndex = left.size() - 1;
        if (Objects.equals(left.get(leftLastIndex), right.get(0))) {
            left.remove(leftLastIndex);
        }
        left.addAll(right);
        return left;
    }

    /**
     * 判断两个点是否在直线的同一侧（忽略y坐标）
     * @param linePoint1 直线第一个点
     * @param linePoint2 直线第二个点
     * @param point1 要判断的第一个点
     * @param point2 要判断的第二个点
     * @return 1: 同侧, -1: 异侧, 0: 至少一个点在直线上
     */
    public static int getSideRelation(BlockPos linePoint1, BlockPos linePoint2,
                                      BlockPos point1, BlockPos point2) {
        long dx = linePoint2.getX() - linePoint1.getX();
        long dz = linePoint2.getZ() - linePoint1.getZ();

        long d1 = dx * (point1.getZ() - linePoint1.getZ())
                - dz * (point1.getX() - linePoint1.getX());

        long d2 = dx * (point2.getZ() - linePoint1.getZ())
                - dz * (point2.getX() - linePoint1.getX());

        if (d1 == 0 || d2 == 0) {
            return 0; // 点在直线上
        }

        if ((d1 > 0 && d2 > 0) || (d1 < 0 && d2 < 0)) {
            return 1; // 同侧
        } else {
            return -1; // 异侧
        }
    }

    public static boolean adjustPointSequence(ArrayList<BlockPos> fromNodes, ArrayList<BlockPos> toNodes) {
        int count = fromNodes.size();
        assert count == toNodes.size();
        if (count > 1) {
            if (RailMath.getSideRelation(
                    fromNodes.get(0), toNodes.get(0),
                    fromNodes.get(count - 1), toNodes.get(count - 1)
            ) < 0) {
                Collections.reverse(fromNodes);
                return true;
            }
        }
        return false;
    }
}
