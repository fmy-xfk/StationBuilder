package cn.myfrank.stationbuilder.utils;

import net.minecraft.util.math.Vec3d;

public interface CurveData {
    double getLength();
    Vec3d getPosition(double s);
}
