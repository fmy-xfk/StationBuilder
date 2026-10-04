package cn.myfrank.stationbuilder.create;

import java.util.ArrayList;
import java.util.List;

import cn.myfrank.stationbuilder.utils.CurveData;
import com.simibubi.create.content.trains.track.BezierConnection;
import net.minecraft.util.math.Vec3d;

public class CreateCurveData implements CurveData {

    /**
     * 子线段接口：支持按局部弧长求点
     */
    private interface SubSegment {
        double length();
        Vec3d getPoint(double localDist);
    }

    /**
     * 空间直线子段
     */
    private static class LineSubSegment implements SubSegment {
        private final Vec3d start;
        private final Vec3d end;
        private final double length;

        public LineSubSegment(Vec3d start, Vec3d end) {
            this.start = start;
            this.end = end;
            this.length = start.distanceTo(end);
        }

        @Override
        public double length() {
            return length;
        }

        @Override
        public Vec3d getPoint(double localDist) {
            if (length < 1e-6) return start;
            double t = Math.min(Math.max(localDist / length, 0.0), 1.0);
            return start.lerp(end, t);
        }
    }

    /**
     * Create 贝塞尔曲线子段（封装弧长参数化）
     */
    private static class BezierSubSegment implements SubSegment {
        private final BezierConnection bc;
        private final double totalLength;
        // 弧长参数化查找表 (Look-Up Table)，保证按距离均匀采样
        private final double[] arcLengths;
        private static final int LUT_SAMPLES = 100;

        public BezierSubSegment(BezierConnection bc) {
            this.bc = bc;
            this.arcLengths = new double[LUT_SAMPLES + 1];
            this.arcLengths[0] = 0.0;

            Vec3d prev = bc.getPosition(0.0);
            double accumulated = 0.0;
            for (int i = 1; i <= LUT_SAMPLES; i++) {
                double t = (double) i / LUT_SAMPLES;
                Vec3d current = bc.getPosition(t);
                accumulated += prev.distanceTo(current);
                this.arcLengths[i] = accumulated;
                prev = current;
            }
            this.totalLength = accumulated;
        }

        @Override
        public double length() {
            return totalLength;
        }

        @Override
        public Vec3d getPoint(double localDist) {
            if (totalLength < 1e-6) return bc.getPosition(0.0);
            double targetDist = Math.min(Math.max(localDist, 0.0), totalLength);

            // 二分查找对应的 t
            int low = 0;
            int high = LUT_SAMPLES;
            while (low < high - 1) {
                int mid = (low + high) >>> 1;
                if (arcLengths[mid] <= targetDist) {
                    low = mid;
                } else {
                    high = mid;
                }
            }

            double segmentDist = arcLengths[high] - arcLengths[low];
            double frac = (segmentDist > 1e-6) ? (targetDist - arcLengths[low]) / segmentDist : 0.0;
            double t = ((double) low + frac) / LUT_SAMPLES;

            return bc.getPosition(Math.min(Math.max(t, 0.0), 1.0));
        }
    }

    private final List<SubSegment> segments = new ArrayList<>();
    private final List<Double> cumulativeLengths = new ArrayList<>();
    private double totalLength = 0.0;

    public void addLine(Vec3d from, Vec3d to) {
        if (from.distanceTo(to) > 1e-4) {
            addSegment(new LineSubSegment(from, to));
        }
    }

    public void addBezier(BezierConnection bc) {
        if (bc != null && bc.getLength() > 1e-4) {
            addSegment(new BezierSubSegment(bc));
        }
    }

    private void addSegment(SubSegment segment) {
        segments.add(segment);
        totalLength += segment.length();
        cumulativeLengths.add(totalLength);
    }

    @Override
    public double getLength() {
        return totalLength;
    }

    @Override
    public Vec3d getPosition(double s) {
        if (segments.isEmpty()) {
            return Vec3d.ZERO;
        }
        if (s <= 0.0) {
            return segments.get(0).getPoint(0.0);
        }
        if (s >= totalLength) {
            return segments.get(segments.size() - 1).getPoint(segments.get(segments.size() - 1).length());
        }

        // 寻找 s 落在哪一个子段上
        double prevCumLength = 0.0;
        for (int i = 0; i < segments.size(); i++) {
            double cumLength = cumulativeLengths.get(i);
            if (s <= cumLength || i == segments.size() - 1) {
                double localDist = s - prevCumLength;
                return segments.get(i).getPoint(localDist);
            }
            prevCumLength = cumLength;
        }

        return segments.get(segments.size() - 1).getPoint(0.0);
    }

    public void append(CreateCurveData other) {
        for (SubSegment seg : other.segments) {
            this.addSegment(seg);
        }
    }
}