package cn.myfrank.stationbuilder.utils;

import net.minecraft.world.phys.Vec3;

public interface CurveData {
    double getLength();
    Vec3 getPosition(double s);
}
