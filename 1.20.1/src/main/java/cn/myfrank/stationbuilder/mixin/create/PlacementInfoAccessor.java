package cn.myfrank.stationbuilder.mixin.create;

import com.simibubi.create.content.trains.track.BezierConnection;
import com.simibubi.create.content.trains.track.TrackPlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// 使用字符串全限定名，避免在没有 Create 的环境下直接类加载崩溃
@Mixin(TrackPlacement.PlacementInfo.class)
public interface PlacementInfoAccessor {
    @Accessor("curve")
    BezierConnection getCurve();

    @Accessor("valid")
    boolean isValid();
}