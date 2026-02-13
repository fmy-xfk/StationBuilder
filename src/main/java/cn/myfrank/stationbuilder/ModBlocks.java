package cn.myfrank.stationbuilder;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;

public class ModBlocks {
    public static final StationBuilderBlock STATION_BUILDER = new StationBuilderBlock(
            AbstractBlock.Settings.copy(Blocks.IRON_BLOCK) // 拷贝铁块的基础属性
                    .requiresTool() // 必须使用对应等级的工具挖掘才会掉落
                    .strength(3.0f, 6.0f) // 设置硬度和爆炸抗性
    );
    public static final BlockItem STATION_BUILDER_ITEM = new BlockItem(STATION_BUILDER, new Item.Settings()){
        @Override
        public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
            if (stack.hasNbt() && stack.getNbt().contains("BlockEntityTag")) {
                tooltip.add(Text.translatable("gui.stationbuilder.include_config").formatted(Formatting.GOLD));
            }
        }
    };
    public static BlockEntityType<StationBuilderBlockEntity> STATION_BUILDER_ENTITY;

    public static void register() {
        Identifier id = new Identifier("stationbuilder", "station_builder");
        Registry.register(Registries.BLOCK, id, STATION_BUILDER);
        Registry.register(Registries.ITEM, id, STATION_BUILDER_ITEM);
        STATION_BUILDER_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                new Identifier("stationbuilder", "station_builder_be"),
                BlockEntityType.Builder.create(StationBuilderBlockEntity::new, STATION_BUILDER).build(null)
        );
    }
}