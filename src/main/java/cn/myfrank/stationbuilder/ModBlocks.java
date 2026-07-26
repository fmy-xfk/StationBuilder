package cn.myfrank.stationbuilder;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

public class ModBlocks {
    public static final Identifier STATION_BUILDER_ID = Identifier.of("stationbuilder", "station_builder");
    public static final RegistryKey<net.minecraft.block.Block> STATION_BUILDER_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, STATION_BUILDER_ID);
    public static final RegistryKey<Item> STATION_BUILDER_ITEM_KEY =
            RegistryKey.of(RegistryKeys.ITEM, STATION_BUILDER_ID);

    public static final StationBuilderBlock STATION_BUILDER = new StationBuilderBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK)
                    .requiresTool()
                    .strength(3.0f, 6.0f)
    );

    public static final BlockItem STATION_BUILDER_ITEM = new BlockItem(
            STATION_BUILDER,
            new Item.Settings()
    ) {
        @Override
        public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
            tooltip.add(Text.translatable("tooltip.stationbuilder.station_builder"));
            if (stack.contains(ModComponents.STATION_BUILDER_DATA)) {
                tooltip.add(Text.translatable("gui.stationbuilder.include_config").formatted(Formatting.GOLD));
            }
        }
    };

    public static BlockEntityType<StationBuilderBlockEntity> STATION_BUILDER_ENTITY;

    public static void register() {
        Registry.register(Registries.BLOCK, STATION_BUILDER_ID, STATION_BUILDER);
        Registry.register(Registries.ITEM, STATION_BUILDER_ID, STATION_BUILDER_ITEM);
        STATION_BUILDER_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of("stationbuilder", "station_builder_be"),
                BlockEntityType.Builder.create(StationBuilderBlockEntity::new, STATION_BUILDER).build()
        );
    }
}