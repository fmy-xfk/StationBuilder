package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mod.data.RailType;
import org.mtr.mod.item.ItemRailModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemRailModifier.class)
public interface ItemRailModifierAccessor {
    @Accessor("railType")
    RailType railType_();
}
