package cn.myfrank.stationbuilder.mixin.mtr;

import net.minecraft.block.BlockState;
import org.mtr.item.ItemPSDAPGBase;
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