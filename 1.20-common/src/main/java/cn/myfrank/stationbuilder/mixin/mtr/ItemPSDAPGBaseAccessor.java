package cn.myfrank.stationbuilder.mixin.mtr;

import org.mtr.mapping.holder.BlockState; // 修正：导入 MTR 模组自有的包装类
import org.mtr.mod.item.ItemPSDAPGBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemPSDAPGBase.class)
public interface ItemPSDAPGBaseAccessor {
	@Accessor(value = "item", remap = false)
	ItemPSDAPGBase.EnumPSDAPGItem item_();

	@Accessor(value = "type", remap = false)
	ItemPSDAPGBase.EnumPSDAPGType type_();

	@Invoker(value = "getBlockStateFromItem", remap = false)
	BlockState getBlockStateFromItem_();
}