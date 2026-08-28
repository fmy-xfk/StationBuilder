package cn.myfrank.stationbuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

final class ForgeRegistry {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, StationBuilder.MOD_ID);

    private static final RegistryObject<CreativeModeTab> TAB = TABS.register("station_group", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.stationbuilder.group"))
            // 1. 获取图标物品时加上 .get()
            .icon(() -> new net.minecraft.world.item.ItemStack(ModBlocks.STATION_BUILDER_ITEM.get()))
            .displayItems((params, output) -> {
                // 2. 向创造模式物品栏添加物品时加上 .get()
                output.accept(ModBlocks.STATION_BUILDER_ITEM.get());
                output.accept(ModItems.RAIL_BUILDER_ITEM.get());
                output.accept(ModItems.BUILDING_SELECTOR_ITEM.get());
                output.accept(ModItems.BUILDING_PLACER_ITEM.get());
            })
            .build());

    static void register(IEventBus bus) {
        TABS.register(bus);
    }
}