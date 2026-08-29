package cn.myfrank.stationbuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(StationBuilder.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(StationBuilder.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, StationBuilder.MOD_ID);

    public static final DeferredBlock<StationBuilderBlock> STATION_BUILDER =
            BLOCKS.registerBlock("station_builder", StationBuilderBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .requiresCorrectToolForDrops()
                            .strength(3.0f, 6.0f)
            );

    public static final DeferredItem<BlockItem> STATION_BUILDER_ITEM =
            ITEMS.registerItem("station_builder", props -> new BlockItem(STATION_BUILDER.get(), props) {
                @Override
                public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable("tooltip.stationbuilder.station_builder"));
                    if (stack.has(ModComponents.STATION_BUILDER_DATA.get())) {
                        tooltip.add(Component.translatable("gui.stationbuilder.include_config").withStyle(net.minecraft.ChatFormatting.GOLD));
                    }
                }
            });

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StationBuilderBlockEntity>> STATION_BUILDER_ENTITY =
            BLOCK_ENTITY_TYPES.register("station_builder_be",
                    () -> new BlockEntityType<>(StationBuilderBlockEntity::new, STATION_BUILDER.get()));
}
