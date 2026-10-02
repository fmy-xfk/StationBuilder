package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemPSDAPGBase.EnumPSDAPGType.class)
public interface EnumPSDAPGTypeAccessor {
    @Accessor(value = "isPSD", remap = false)
    boolean isPSD();

    @Accessor(value = "isOdd", remap = false)
    boolean isOdd();

    @Accessor(value = "isLift", remap = false)
    boolean isLift();
}