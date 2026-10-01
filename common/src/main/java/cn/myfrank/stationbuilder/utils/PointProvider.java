package cn.myfrank.stationbuilder.utils;

import net.minecraft.util.math.Vec3d;

import java.util.List;

public class PointProvider {
    private static final double EPS = 0.001;
    private final CurveData math;
    private final int segment;
    private final double step;
    private final boolean reversed;
    private int i;
    public PointProvider(CurveData math, int segment, boolean reversed) {
        this.math = math;
        this.segment = segment;
        this.step = math.getLength() / segment;
        this.reversed = reversed;
        if (reversed) {
            i = segment;
        } else {
            i = 0;
        }
    }
    public boolean notExhausted() {
        if (reversed) {
            return i >= 0;
        } else {
            return i <= segment;
        }
    }
    public void next() {
        if (reversed) {
            if (notExhausted()) i--;
        } else {
            if (notExhausted()) i++;
        }
    }
    private List<Vec3d> _get(int i) {
        final double length = math.getLength();
        double s = i * step;
        if (s > length) s = length;
        Vec3d center = math.getPosition(s);
        Vec3d pNext, tangent;
        if (reversed) {
            if (s - EPS < 0) {
                pNext = math.getPosition(Math.max(s + EPS, 0));
                tangent = center.subtract(pNext).normalize();
            } else {
                pNext = math.getPosition(Math.max(s - EPS, 0));
                tangent = pNext.subtract(center).normalize();
            }
        } else {
            if (s + EPS > length) {
                pNext = math.getPosition(Math.min(s - EPS, length));
                tangent = center.subtract(pNext).normalize();
            } else {
                pNext = math.getPosition(Math.min(s + EPS, length));
                tangent = pNext.subtract(center).normalize();
            }
        }
        Vec3d normal = new Vec3d(-tangent.z, 0, tangent.x).normalize();
        return List.of(center, tangent, normal);
    }
    public List<Vec3d> get() {
        return _get(i);
    }
    public List<Vec3d> get(int i) {
        if(reversed) {
            return _get(segment - i);
        } else {
            return _get(i);
        }
    }
}