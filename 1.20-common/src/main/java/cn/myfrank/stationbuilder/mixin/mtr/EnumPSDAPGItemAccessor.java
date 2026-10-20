package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemPSDAPGBase.EnumPSDAPGItem.class)
public interface EnumPSDAPGItemAccessor {
    @Accessor("isDoor")
    boolean isDoor();
}