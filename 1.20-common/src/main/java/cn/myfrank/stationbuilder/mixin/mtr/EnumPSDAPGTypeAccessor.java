package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemPSDAPGBase.EnumPSDAPGType.class)
public interface EnumPSDAPGTypeAccessor {
    @Accessor("isPSD")
    boolean isPSD();

    @Accessor("isOdd")
    boolean isOdd();

    @Accessor("isLift")
    boolean isLift();
}