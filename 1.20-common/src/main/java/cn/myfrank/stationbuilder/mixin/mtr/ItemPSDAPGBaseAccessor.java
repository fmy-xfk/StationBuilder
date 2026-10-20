package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mapping.holder.BlockState;
import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemPSDAPGBase.class)
public interface ItemPSDAPGBaseAccessor {
	@Accessor("item")
	ItemPSDAPGBase.EnumPSDAPGItem item_();

	@Accessor("type")
	ItemPSDAPGBase.EnumPSDAPGType type_();

	@Invoker("getBlockStateFromItem")
	BlockState getBlockStateFromItem_();
}