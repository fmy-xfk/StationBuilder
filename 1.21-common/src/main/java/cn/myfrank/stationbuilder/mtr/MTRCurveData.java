package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.utils.CurveData;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.data.RailMath;

public class MTRCurveData implements CurveData {
    private final RailMath math;
    public MTRCurveData(RailMath math) {
        this.math = math;
    }

    @Override
    public double getLength() {
        return math.getLength();
    }

    @Override
    public Vec3d getPosition(double s) {
        var ret = math.getPosition(s, false);
        return new Vec3d(ret.x(), ret.y(), ret.z());
    }
}
