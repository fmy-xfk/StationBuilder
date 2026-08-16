package cn.myfrank.stationbuilder;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModBlocks {
    public static final ResourceLocation STATION_BUILDER_ID = ResourceLocation.fromNamespaceAndPath(StationBuilder.MOD_ID, "station_builder");

    // 1. 创建方块、物品、以及方块实体的延迟注册器
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, StationBuilder.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, StationBuilder.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StationBuilder.MOD_ID);

    // 2. 延迟注册方块
    public static final RegistryObject<StationBuilderBlock> STATION_BUILDER = BLOCKS.register("station_builder", () ->
            new StationBuilderBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).requiresCorrectToolForDrops().strength(3.0f, 6.0f))
    );

    // 3. 延迟注册对应的 BlockItem (注意：通过 STATION_BUILDER.get() 来安全提取方块实例)
    public static final RegistryObject<Item> STATION_BUILDER_ITEM = ITEMS.register("station_builder", () ->
            new BlockItem(STATION_BUILDER.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
                    tooltip.add(Component.translatable("tooltip.stationbuilder.station_builder"));
                    if (StationBuilderState.hasData(stack)) {
                        tooltip.add(Component.translatable("gui.stationbuilder.include_config").withStyle(ChatFormatting.GOLD));
                    }
                }
            }
    );

    // 4. 延迟注册方块实体类型
    public static final RegistryObject<BlockEntityType<StationBuilderBlockEntity>> STATION_BUILDER_ENTITY = BLOCK_ENTITIES.register("station_builder_be", () ->
            BlockEntityType.Builder.of(StationBuilderBlockEntity::new, STATION_BUILDER.get()).build(null)
    );

    // 5. 事件总线绑定方法，注册所有的延迟注册器
    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}