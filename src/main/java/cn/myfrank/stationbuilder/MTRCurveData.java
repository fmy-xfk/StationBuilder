package cn.myfrank.stationbuilder;

import net.minecraft.world.phys.Vec3;
import org.mtr.core.data.RailMath;

public class MTRCurveData implements CurveData{
    private RailMath math;
    public MTRCurveData(RailMath math) {
        this.math = math;
    }

    @Override
    public double getLength() {
        return math.getLength();
    }

    @Override
    public Vec3 getPosition(double s) {
        var ret = math.getPosition(s, false);
        return new Vec3(ret.x(), ret.y(), ret.z());
    }
}
