package cn.myfrank.stationbuilder.mtr;

import cn.myfrank.stationbuilder.utils.RailMath;

import cn.myfrank.stationbuilder.utils.CurveData;
import net.minecraft.util.math.Vec3d;
import org.mtr.core.tool.Vector;

public class MTRCurveData implements CurveData {
    private final org.mtr.core.data.RailMath math;

    public MTRCurveData(org.mtr.core.data.RailMath math) {
        this.math = math;
    }

    @Override
    public double getLength() {
        return math.getLength();
    }

    @Override
    public Vec3d getPosition(double s) {
        Vector ret = math.getPosition(s, false);
        return new Vec3d(ret.x, ret.y, ret.z);
    }
}
